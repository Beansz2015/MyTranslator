package com.example.mytranslator

import android.os.Handler
import android.os.Looper
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.translation.*
import java.util.concurrent.atomic.AtomicBoolean

class TranslatorManager {

    private val SPEECH_KEY    = BuildConfig.AZURE_SPEECH_KEY
    private val SPEECH_REGION = "southeastasia"

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Warm connection state ─────────────────────────────────────────────────
    private var recognizer:    TranslationRecognizer? = null
    private var warmLangA:     LangOption? = null
    private var warmLangB:     LangOption? = null
    private var warmAutoMode:  Boolean = false
    private var warmShortlist: List<LangOption> = emptyList()

    // ── Session state (active conversation) ──────────────────────────────────
    private val isActive   = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    // Callbacks forwarded to UI while active
    private var activeOnStateChange:  ((AppState) -> Unit)? = null
    private var activeOnDetectedLang: ((LangOption?) -> Unit)? = null
    private var activeOnError:        ((String) -> Unit)? = null

    // Segment buffering
    private val segmentBuffer  = StringBuilder()
    private var segmentLang:   LangOption? = null
    private var flushRunnable: Runnable?   = null
    private val FLUSH_DELAY_MS = 1000L

    // Auto-mode runtime state
    private var defaultLang:     LangOption? = null
    private var lastForeignLang: LangOption? = null

    // ─────────────────────────────────────────────────────────────────────────
    // PRE-WARM — call once on app launch with default settings
    // ─────────────────────────────────────────────────────────────────────────

    fun preWarm(
        langA: LangOption,
        langB: LangOption,
        shortlist: List<LangOption>
    ) {
        Thread {
            buildManualRecognizer(langA, langB)
        }.start()
        warmLangA     = langA
        warmLangB     = langB
        warmAutoMode  = false
        warmShortlist = shortlist
    }

    // Pre-warm auto mode in background when shortlist changes in Settings dialog
    fun preWarmAutoMode(
        defaultLang: LangOption,
        shortlist: List<LangOption>
    ) {
        Thread {
            recognizer?.stopContinuousRecognitionAsync()
            recognizer?.close()
            recognizer = null
            val candidates = (listOf(defaultLang) + shortlist.filter { it.locale != defaultLang.locale }).take(10)
            buildAutoRecognizer(defaultLang, candidates)
            warmLangA     = defaultLang
            warmAutoMode  = true
            warmShortlist = shortlist
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MANUAL MODE
    // ─────────────────────────────────────────────────────────────────────────

    fun start(
        langA: LangOption,
        langB: LangOption,
        onStateChange: (AppState) -> Unit,
        onError: (String) -> Unit
    ) {
        val settingsChanged = langA != warmLangA || langB != warmLangB || warmAutoMode
        if (settingsChanged) {
            recognizer?.stopContinuousRecognitionAsync()
            recognizer?.close()
            recognizer = null
            buildManualRecognizer(langA, langB)
            warmLangA    = langA
            warmLangB    = langB
            warmAutoMode = false
        }

        activeOnStateChange  = onStateChange
        activeOnDetectedLang = null
        activeOnError        = onError
        defaultLang          = null
        lastForeignLang      = null

        isActive.set(true)
        mainHandler.post { onStateChange(AppState.LISTENING) }
    }

    private fun buildManualRecognizer(langA: LangOption, langB: LangOption) {
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
            val codeA = langA.locale.split("-")[0]
            val codeB = langB.locale.split("-")[0]

            speechConfig.addTargetLanguage(codeA)
            speechConfig.addTargetLanguage(codeB)
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1000")

            val autoDetect  = AutoDetectSourceLanguageConfig.fromLanguages(listOf(langA.locale, langB.locale))
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val newRecognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            attachManualListeners(newRecognizer, langA, langB)
            newRecognizer.startContinuousRecognitionAsync()
            recognizer = newRecognizer

        } catch (e: Exception) {
            mainHandler.post { activeOnError?.invoke("Failed to initialise: ${e.message}") }
        }
    }

    private fun attachManualListeners(
        r: TranslationRecognizer,
        langA: LangOption,
        langB: LangOption
    ) {
        val codeA = langA.locale.split("-")[0]

        r.recognized.addEventListener { _, e ->
            if (!isActive.get() || isSpeaking.get()) return@addEventListener
            if (e.result.reason != ResultReason.TranslatedSpeech) return@addEventListener

            val detectedCode = AutoDetectSourceLanguageResult.fromResult(e.result).language.split("-")[0]
            val targetLang   = if (detectedCode == codeA) langB else langA
            val translated   = e.result.translations[targetLang.locale.split("-")[0]]

            if (!translated.isNullOrBlank()) {
                if (segmentLang != null && segmentLang != targetLang) flushBuffer()
                segmentLang = targetLang
                if (segmentBuffer.isNotEmpty()) segmentBuffer.append(" ")
                segmentBuffer.append(translated)
                flushRunnable?.let { mainHandler.removeCallbacks(it) }
                flushRunnable = Runnable { flushBuffer() }
                mainHandler.postDelayed(flushRunnable!!, FLUSH_DELAY_MS)
            }
        }

        r.canceled.addEventListener { _, e ->
            if (isActive.get()) {
                mainHandler.post {
                    activeOnError?.invoke("Recognition stopped: ${e.errorDetails}")
                    activeOnStateChange?.invoke(AppState.IDLE)
                }
                isActive.set(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO MODE
    // ─────────────────────────────────────────────────────────────────────────

    fun startAutoMode(
        defaultLangIn: LangOption,
        shortlist: List<LangOption>,
        onStateChange: (AppState) -> Unit,
        onDetectedLang: (LangOption?) -> Unit,
        onError: (String) -> Unit
    ) {
        val settingsChanged = !warmAutoMode || defaultLangIn != warmLangA || shortlist != warmShortlist
        if (settingsChanged) {
            recognizer?.stopContinuousRecognitionAsync()
            recognizer?.close()
            recognizer = null
            val candidates = (listOf(defaultLangIn) + shortlist.filter { it.locale != defaultLangIn.locale }).take(10)
            buildAutoRecognizer(defaultLangIn, candidates)
            warmLangA     = defaultLangIn
            warmAutoMode  = true
            warmShortlist = shortlist
        }

        activeOnStateChange  = onStateChange
        activeOnDetectedLang = onDetectedLang
        activeOnError        = onError
        defaultLang          = defaultLangIn
        lastForeignLang      = null
        onDetectedLang(null)

        isActive.set(true)
        mainHandler.post { onStateChange(AppState.LISTENING) }
    }

    private fun buildAutoRecognizer(
        defaultLangIn: LangOption,
        candidates: List<LangOption>
    ) {
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)

            candidates.forEach { speechConfig.addTargetLanguage(it.locale.split("-")[0]) }
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1000")
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_LanguageIdMode, "Continuous")

            val autoDetect  = AutoDetectSourceLanguageConfig.fromLanguages(candidates.map { it.locale })
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val newRecognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            attachAutoListeners(newRecognizer, defaultLangIn, defaultLangIn.locale.split("-")[0], candidates)
            newRecognizer.startContinuousRecognitionAsync()
            recognizer = newRecognizer

        } catch (e: Exception) {
            mainHandler.post { activeOnError?.invoke("Failed to initialise: ${e.message}") }
        }
    }

    private fun attachAutoListeners(
        r: TranslationRecognizer,
        defaultLangIn: LangOption,
        defaultCode: String,
        candidates: List<LangOption>
    ) {
        r.recognized.addEventListener { _, e ->
            if (!isActive.get() || isSpeaking.get()) return@addEventListener
            if (e.result.reason != ResultReason.TranslatedSpeech) return@addEventListener

            val detectedLocale = AutoDetectSourceLanguageResult.fromResult(e.result).language
            val detectedCode   = detectedLocale.split("-")[0]
            val isDefault      = detectedCode == defaultCode

            val targetLang: LangOption
            if (isDefault) {
                targetLang = lastForeignLang ?: return@addEventListener
            } else {
                val detected = candidates.firstOrNull { it.locale == detectedLocale }
                    ?: candidates.firstOrNull { it.locale.split("-")[0] == detectedCode }
                    ?: return@addEventListener

                if (lastForeignLang == null || lastForeignLang!!.locale != detected.locale) {
                    lastForeignLang = detected
                    mainHandler.post { activeOnDetectedLang?.invoke(detected) }

                    // Lock to 2-language pair for subsequent utterances
                    mainHandler.post {
                        if (isActive.get()) {
                            recognizer?.stopContinuousRecognitionAsync()
                            recognizer?.close()
                            buildAutoRecognizer(defaultLangIn, listOf(defaultLangIn, detected))
                        }
                    }
                }
                targetLang = defaultLangIn
            }

            val translated = e.result.translations[targetLang.locale.split("-")[0]]
            if (!translated.isNullOrBlank()) {
                if (segmentLang != null && segmentLang != targetLang) flushBuffer()
                segmentLang = targetLang
                if (segmentBuffer.isNotEmpty()) segmentBuffer.append(" ")
                segmentBuffer.append(translated)
                flushRunnable?.let { mainHandler.removeCallbacks(it) }
                flushRunnable = Runnable { flushBuffer() }
                mainHandler.postDelayed(flushRunnable!!, FLUSH_DELAY_MS)
            }
        }

        r.canceled.addEventListener { _, e ->
            if (isActive.get()) {
                mainHandler.post {
                    activeOnError?.invoke("Recognition stopped: ${e.errorDetails}")
                    activeOnStateChange?.invoke(AppState.IDLE)
                }
                isActive.set(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARED
    // ─────────────────────────────────────────────────────────────────────────

    private fun flushBuffer() {
        val text   = segmentBuffer.toString().trim()
        val target = segmentLang
        segmentBuffer.clear()
        segmentLang   = null
        flushRunnable = null
        if (text.isNotBlank() && target != null) speakTranslation(text, target)
    }

    private fun speakTranslation(text: String, targetLang: LangOption) {
        isSpeaking.set(true)
        recognizer?.stopContinuousRecognitionAsync()
        mainHandler.post { activeOnStateChange?.invoke(AppState.SPEAKING) }

        val ttsConfig = SpeechConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
        ttsConfig.speechSynthesisVoiceName = targetLang.voice
        val speakerAudioConfig = AudioConfig.fromDefaultSpeakerOutput()
        val synthesizer = SpeechSynthesizer(ttsConfig, speakerAudioConfig)

        val onDone = { _: Any, _: Any ->
            isSpeaking.set(false)
            recognizer?.startContinuousRecognitionAsync()
            mainHandler.post { activeOnStateChange?.invoke(AppState.LISTENING) }
            synthesizer.close()
            ttsConfig.close()
            speakerAudioConfig.close()
        }
        synthesizer.SynthesisCompleted.addEventListener(onDone)
        synthesizer.SynthesisCanceled.addEventListener(onDone)
        synthesizer.SpeakTextAsync(text)
    }

    // Stop conversation but keep WebSocket alive
    fun stop() {
        isActive.set(false)
        flushRunnable?.let { mainHandler.removeCallbacks(it) }
        segmentBuffer.clear()
        segmentLang          = null
        lastForeignLang      = null
        activeOnStateChange  = null
        activeOnDetectedLang = null
        activeOnError        = null
        isSpeaking.set(false)
        // Recognizer stays alive — WebSocket remains warm
    }

    // Full teardown — call from onDestroy() only
    fun destroy() {
        stop()
        recognizer?.stopContinuousRecognitionAsync()
        recognizer?.close()
        recognizer = null
    }
}
