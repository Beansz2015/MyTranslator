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

    private var recognizer: TranslationRecognizer? = null
    private val isSpeaking  = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val segmentBuffer  = StringBuilder()
    private var segmentLang:   LangOption? = null
    private var flushRunnable: Runnable?   = null
    private val FLUSH_DELAY_MS = 2000L

    fun start(
        langA: LangOption,
        langB: LangOption,
        onStateChange: (AppState) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
            val codeA = langA.locale.split("-")[0]
            val codeB = langB.locale.split("-")[0]

            speechConfig.addTargetLanguage(codeA)
            speechConfig.addTargetLanguage(codeB)
            speechConfig.setProperty(
                PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1500"
            )

            val autoDetect = AutoDetectSourceLanguageConfig.fromLanguages(
                listOf(langA.locale, langB.locale)
            )
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            recognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            recognizer!!.recognized.addEventListener { _, e ->
                if (e.result.reason == ResultReason.TranslatedSpeech && !isSpeaking.get()) {
                    val detectedCode = AutoDetectSourceLanguageResult
                        .fromResult(e.result).language.split("-")[0]
                    val targetLang = if (detectedCode == codeA) langB else langA
                    val translated = e.result.translations[targetLang.locale.split("-")[0]]

                    if (!translated.isNullOrBlank()) {
                        if (segmentLang != null && segmentLang != targetLang) {
                            flushBuffer(onStateChange)
                        }
                        segmentLang = targetLang
                        if (segmentBuffer.isNotEmpty()) segmentBuffer.append(" ")
                        segmentBuffer.append(translated)

                        flushRunnable?.let { mainHandler.removeCallbacks(it) }
                        flushRunnable = Runnable { flushBuffer(onStateChange) }
                        mainHandler.postDelayed(flushRunnable!!, FLUSH_DELAY_MS)
                    }
                }
            }

            recognizer!!.canceled.addEventListener { _, e ->
                mainHandler.post {
                    onError("Recognition stopped: ${e.errorDetails}")
                    onStateChange(AppState.IDLE)
                }
            }

            recognizer!!.startContinuousRecognitionAsync()
            mainHandler.post { onStateChange(AppState.LISTENING) }

        } catch (e: Exception) {
            mainHandler.post { onError("Failed to start: ${e.message}") }
        }
    }

    private fun flushBuffer(onStateChange: (AppState) -> Unit) {
        val text   = segmentBuffer.toString().trim()
        val target = segmentLang
        segmentBuffer.clear()
        segmentLang   = null
        flushRunnable = null
        if (text.isNotBlank() && target != null) {
            speakTranslation(text, target, onStateChange)
        }
    }

    private fun speakTranslation(
        text: String,
        targetLang: LangOption,
        onStateChange: (AppState) -> Unit
    ) {
        isSpeaking.set(true)
        recognizer?.stopContinuousRecognitionAsync()
        mainHandler.post { onStateChange(AppState.SPEAKING) }

        val ttsConfig = SpeechConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
        ttsConfig.speechSynthesisVoiceName = targetLang.voice
        val speakerAudioConfig = AudioConfig.fromDefaultSpeakerOutput()
        val synthesizer = SpeechSynthesizer(ttsConfig, speakerAudioConfig)

        val onDone = { _: Any, _: Any ->
            isSpeaking.set(false)
            recognizer?.startContinuousRecognitionAsync()
            mainHandler.post { onStateChange(AppState.LISTENING) }
            synthesizer.close()
            ttsConfig.close()
            speakerAudioConfig.close()
        }
        synthesizer.SynthesisCompleted.addEventListener(onDone)
        synthesizer.SynthesisCanceled.addEventListener(onDone)
        synthesizer.SpeakTextAsync(text)
    }

    fun stop() {
        flushRunnable?.let { mainHandler.removeCallbacks(it) }
        segmentBuffer.clear()
        segmentLang = null
        recognizer?.stopContinuousRecognitionAsync()
        recognizer?.close()
        recognizer = null
        isSpeaking.set(false)
    }
}
