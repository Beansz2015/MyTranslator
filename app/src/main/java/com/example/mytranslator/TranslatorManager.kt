package com.example.mytranslator

import android.os.Handler
import android.os.Looper
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.translation.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class TranslatorManager {

    private val SPEECH_KEY    = BuildConfig.AZURE_SPEECH_KEY
    private val SPEECH_REGION = "southeastasia"

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Thread safety ───────────────────────────────────────────────────────
    private val recognizerLock = ReentrantLock()

    // ── Warm connection state ────────────────────────────────────────────────
    private var recognizer:    TranslationRecognizer? = null
    private var isWarmReady:   Boolean = false
    private var warmLangA:     LangOption? = null
    private var warmLangB:     LangOption? = null
    private var warmAutoMode:  Boolean = false
    private var warmShortlist: List<LangOption> = emptyList()

    // ── Session state ─────────────────────────────────────────────────────────
    private val isActive   = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    private var activeOnStateChange:  ((AppState) -> Unit)? = null
    private var activeOnDetectedLang: ((LangOption?) -> Unit)? = null
    private var activeOnError:        ((String) -> Unit)? = null

    // ── Segment buffering ───────────────────────────────────────────────────
    private val segmentBuffer  = StringBuilder()
    private var segmentLang:   LangOption? = null
    private var flushRunnable: Runnable?   = null
    private val FLUSH_DELAY_MS = 1000L

    // ── Auto-mode runtime ─────────────────────────────────────────────────────
    private var lastForeignLang: LangOption? = null

    // ── Detection streak guard ────────────────────────────────────────────────
    // Only meaningful with 3+ candidate languages (broad auto-mode scan).
    // With 2 languages, alternation is always expected — guard is disabled.
    private val detectionHistory = ArrayDeque<String>(5)
    private val HISTORY_MAX = 5

    private fun recordDetection(code: String) {
        if (detectionHistory.size >= HISTORY_MAX) detectionHistory.removeFirst()
        detectionHistory.addLast(code)
    }

    private fun isLikelyMisdetection(newCode: String, candidateCount: Int): Boolean {
        if (candidateCount <= 2) return false
        if (detectionHistory.size < 3) return false
        val recentMajority = detectionHistory.takeLast(3)
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        return recentMajority != newCode && detectionHistory.last() != newCode
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAFE TEARDOWN
    // ─────────────────────────────────────────────────────────────────────────

    private fun teardownRecognizer() {
        recognizerLock.lock()
        try {
            isWarmReady = false
            recognizer?.stopContinuousRecognitionAsync()
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            // Swallow — teardown is best-effort
        } finally {
            recognizerLock.unlock()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRE-WARM
    // ─────────────────────────────────────────────────────────────────────────

    fun preWarm(
        langA: LangOption,
        langB: LangOption,
        shortlist: List<LangOption>
    ) {
        warmLangA     = langA
        warmLangB     = langB
        warmAutoMode  = false
        warmShortlist = shortlist
        Thread { buildManualRecognizer(langA, langB) }.start()
    }

    fun preWarmAutoMode(
        defaultLang: LangOption,
        shortlist: List<LangOption>
    ) {
        warmLangA     = defaultLang
        warmAutoMode  = true
        warmShortlist = shortlist
        Thread {
            teardownRecognizer()
            val candidates = (listOf(defaultLang) +
                shortlist.filter { it.locale != defaultLang.locale }).take(10)
            buildAutoRecognizer(defaultLang, candidates)
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
        if (settingsChanged || !isWarmReady) {
            activeOnStateChange  = onStateChange
            activeOnDetectedLang = null
            activeOnError        = onError
            Thread {
                if (settingsChanged) {
                    teardownRecognizer()
                    warmLangA    = langA
                    warmLangB    = langB
                    warmAutoMode = false
                }
                buildManualRecognizer(langA, langB)
                activateSession(onStateChange)
            }.start()
        } else {
            activeOnStateChange  = onStateChange
            activeOnDetectedLang = null
            activeOnError        = onError
            lastForeignLang      = null
            isActive.set(true)
            mainHandler.post { onStateChange(AppState.LISTENING) }
        }
    }

    private fun buildManualRecognizer(langA: LangOption, langB: LangOption) {
        recognizerLock.lock()
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
            val codeA = langA.locale.split("-")[0]
            val codeB = langB.locale.split("-")[0]

            speechConfig.addTargetLanguage(codeA)
            speechConfig.addTargetLanguage(codeB)

            // ── Silence + segmentation tuning ───────────────────────────────────
            speechConfig.setProperty(
                PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "900"
            )
            speechConfig.setProperty("speech.segmentation.mode", "custom")
            speechConfig.setProperty("speech.segmentation.sentenceTimeoutMs", "750")

            val autoDetect  = AutoDetectSourceLanguageConfig.fromLanguages(
                listOf(langA.locale, langB.locale)
            )
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val newRecognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            attachManualListeners(newRecognizer, langA, langB)
            newRecognizer.startContinuousRecognitionAsync()
            recognizer  = newRecognizer
            isWarmReady = true

        } catch (e: Exception) {
            mainHandler.post { activeOnError?.invoke("Failed to initialise: ${e.message}") }
        } finally {
            recognizerLock.unlock()
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

            val detectedCode = AutoDetectSourceLanguageResult.fromResult(e.result)
                .language.split("-")[0]

            if (isLikelyMisdetection(detectedCode, 2)) return@addEventListener
            recordDetection(detectedCode)

            val targetLang = if (detectedCode == codeA) langB else langA
            val targetCode = targetLang.locale.split("-")[0]

            if (detectedCode == targetCode) return@addEventListener

            val sourceText = e.result.text
            val translated = e.result.translations[targetCode]

            if (!translated.isNullOrBlank()) {
                if (translated.trim().equals(sourceText.trim(), ignoreCase = true)) return@addEventListener

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
        val settingsChanged = !warmAutoMode ||
            defaultLangIn != warmLangA ||
            shortlist != warmShortlist

        if (settingsChanged || !isWarmReady) {
            activeOnStateChange  = onStateChange
            activeOnDetectedLang = onDetectedLang
            activeOnError        = onError
            onDetectedLang(null)
            Thread {
                if (settingsChanged) {
                    teardownRecognizer()
                    warmLangA     = defaultLangIn
                    warmAutoMode  = true
                    warmShortlist = shortlist
                }
                val candidates = (listOf(defaultLangIn) +
                    shortlist.filter { it.locale != defaultLangIn.locale }).take(10)
                buildAutoRecognizer(defaultLangIn, candidates)
                activateSession(onStateChange)
            }.start()
        } else {
            activeOnStateChange  = onStateChange
            activeOnDetectedLang = onDetectedLang
            activeOnError        = onError
            lastForeignLang      = null
            onDetectedLang(null)
            isActive.set(true)
            mainHandler.post { onStateChange(AppState.LISTENING) }
        }
    }

    private fun buildAutoRecognizer(
        defaultLangIn: LangOption,
        candidates: List<LangOption>
    ) {
        recognizerLock.lock()
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)

            candidates.forEach { speechConfig.addTargetLanguage(it.locale.split("-")[0]) }

            // ── Silence + segmentation tuning ───────────────────────────────────
            speechConfig.setProperty(
                PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "900"
            )
            speechConfig.setProperty("speech.segmentation.mode", "custom")
            speechConfig.setProperty("speech.segmentation.sentenceTimeoutMs", "750")
            speechConfig.setProperty(
                PropertyId.SpeechServiceConnection_LanguageIdMode, "Continuous"
            )

            val autoDetect  = AutoDetectSourceLanguageConfig.fromLanguages(
                candidates.map { it.locale }
            )
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val newRecognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            attachAutoListeners(
                newRecognizer,
                defaultLangIn,
                defaultLangIn.locale.split("-")[0],
                candidates
            )
            newRecognizer.startContinuousRecognitionAsync()
            recognizer  = newRecognizer
            isWarmReady = true

        } catch (e: Exception) {
            mainHandler.post { activeOnError?.invoke("Failed to initialise: ${e.message}") }
        } finally {
            recognizerLock.unlock()
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

            if (isLikelyMisdetection(detectedCode, candidates.size)) return@addEventListener
            recordDetection(detectedCode)

            val isDefault = detectedCode == defaultCode

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

                    Thread {
                        if (isActive.get()) {
                            teardownRecognizer()
                            detectionHistory.clear()
                            buildAutoRecognizer(defaultLangIn, listOf(defaultLangIn, detected))
                        }
                    }.start()
                }
                targetLang = defaultLangIn
            }

            val targetCode = targetLang.locale.split("-")[0]

            if (detectedCode == targetCode) return@addEventListener

            val translated = e.result.translations[targetCode]
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
    // SHARED HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun activateSession(onStateChange: (AppState) -> Unit) {
        lastForeignLang = null
        detectionHistory.clear()
        isActive.set(true)
        mainHandler.post { onStateChange(AppState.LISTENING) }
    }

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

        // SSML with rate=fast (~20-25% faster than default)
        // To adjust: x-slow | slow | medium | fast | x-fast | or "+20%" etc.
        val ssml = """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="${targetLang.locale}"><voice name="${targetLang.voice}"><prosody rate="fast">${text}</prosody></voice></speak>"""

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
        synthesizer.SpeakSsmlAsync(ssml)
    }

    fun stop() {
        isActive.set(false)
        flushRunnable?.let { mainHandler.removeCallbacks(it) }
        segmentBuffer.clear()
        segmentLang          = null
        lastForeignLang      = null
        detectionHistory.clear()
        activeOnStateChange  = null
        activeOnDetectedLang = null
        activeOnError        = null
        isSpeaking.set(false)
    }

    fun destroy() {
        stop()
        teardownRecognizer()
    }
}
