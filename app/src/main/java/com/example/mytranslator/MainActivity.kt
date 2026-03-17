package com.example.mytranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mytranslator.ui.theme.MyTranslatorTheme
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.translation.*
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────
// LANGUAGE LIST
// ─────────────────────────────────────────────

data class Language(val name: String, val locale: String, val voice: String)

val LANGUAGES = listOf(
    Language("English",                    "en-US",  "en-US-JennyNeural"),
    Language("Malay",                      "ms-MY",  "ms-MY-YasminNeural"),
    Language("Chinese (Mandarin)",         "zh-CN",  "zh-CN-XiaoxiaoNeural"),
    Language("Bengali",                    "bn-IN",  "bn-IN-TanishaaNeural"),
    Language("Tamil",                      "ta-IN",  "ta-IN-PallaviNeural"),
    Language("Chinese (Cantonese)",        "zh-HK",  "zh-HK-HiuMaanNeural"),
    Language("Japanese",                   "ja-JP",  "ja-JP-NanamiNeural"),
    Language("Filipino",                   "fil-PH", "fil-PH-BlessicaNeural"),
    Language("Korean",                     "ko-KR",  "ko-KR-SunHiNeural"),
    Language("Thai",                       "th-TH",  "th-TH-PremwadeeNeural"),
    Language("French",                     "fr-FR",  "fr-FR-DeniseNeural"),
    Language("German",                     "de-DE",  "de-DE-KatjaNeural"),
    Language("Arabic",                     "ar-SA",  "ar-SA-ZariyahNeural"),
    Language("Russian",                    "ru-RU",  "ru-RU-SvetlanaNeural"),
    Language("Spanish",                    "es-ES",  "es-ES-ElviraNeural"),
    Language("Vietnamese",                 "vi-VN",  "vi-VN-HoaiMyNeural"),
    Language("Burmese",                    "my-MM",  "my-MM-NilarNeural"),
    Language("Lao",                        "lo-LA",  "lo-LA-KeomanyNeural"),
    Language("Nepali",                     "ne-NP",  "ne-NP-HemkalaNeural"),
    Language("Khmer",                      "km-KH",  "km-KH-SreymomNeural"),
    Language("Ukrainian",                  "uk-UA",  "uk-UA-PolinaNeural")
)

// ─────────────────────────────────────────────
// APP STATE
// ─────────────────────────────────────────────

enum class AppState { IDLE, LISTENING, SPEAKING }

// ─────────────────────────────────────────────
// TRANSLATION MANAGER
// ─────────────────────────────────────────────

class TranslatorManager {

    private val SPEECH_KEY    = "YOUR_KEY_1_HERE"   // ← Paste your Azure Key 1 here
    private val SPEECH_REGION = "southeastasia"      // ← Your Azure region

    private var recognizer: TranslationRecognizer? = null
    private val isSpeaking = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(
        langA: Language,
        langB: Language,
        onStateChange: (AppState) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val speechConfig = SpeechTranslationConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)

            // Extract base language codes (e.g. "en" from "en-US")
            val codeA = langA.locale.split("-")[0]
            val codeB = langB.locale.split("-")[0]

            // Tell Azure to translate into both languages simultaneously
            speechConfig.addTargetLanguage(codeA)
            speechConfig.addTargetLanguage(codeB)

            // Auto-detect which of the two languages is being spoken
            val autoDetect = AutoDetectSourceLanguageConfig.fromLanguages(
                listOf(langA.locale, langB.locale)
            )

            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            recognizer = TranslationRecognizer(speechConfig, autoDetect, audioConfig)

            // Fires each time a full sentence is recognised and translated
            recognizer!!.recognized.addEventListener { _, e ->
                if (e.result.reason == ResultReason.TranslatedSpeech && !isSpeaking.get()) {

                    val detectedCode = e.result.language.split("-")[0] // e.g. "en"

                    // If speaker used Language A → speak translation in Language B, and vice versa
                    val targetLang = if (detectedCode == codeA) langB else langA
                    val translated = e.result.translations[targetLang.locale.split("-")[0]]

                    if (!translated.isNullOrBlank()) {
                        speakTranslation(translated, targetLang, onStateChange)
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

    private fun speakTranslation(
        text: String,
        targetLang: Language,
        onStateChange: (AppState) -> Unit
    ) {
        isSpeaking.set(true)
        mainHandler.post { onStateChange(AppState.SPEAKING) }

        val ttsConfig = SpeechConfig.fromSubscription(SPEECH_KEY, SPEECH_REGION)
        ttsConfig.speechSynthesisVoiceName = targetLang.voice

        // null AudioConfig = plays through device speaker automatically
        val synthesizer = SpeechSynthesizer(ttsConfig, null)

        synthesizer.SynthesisCompleted.addEventListener { _, _ ->
            isSpeaking.set(false)
            mainHandler.post { onStateChange(AppState.LISTENING) }
            synthesizer.close()
            ttsConfig.close()
        }

        synthesizer.SynthesisCanceled.addEventListener { _, _ ->
            isSpeaking.set(false)
            mainHandler.post { onStateChange(AppState.LISTENING) }
            synthesizer.close()
            ttsConfig.close()
        }

        synthesizer.SpeakTextAsync(text)
    }

    fun stop() {
        recognizer?.stopContinuousRecognitionAsync()
        recognizer?.close()
        recognizer = null
        isSpeaking.set(false)
    }
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val translatorManager = TranslatorManager()

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result handled reactively in the UI via hasMicPermission() */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTranslatorTheme {
                TranslatorScreen(
                    translatorManager   = translatorManager,
                    onRequestPermission = { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    hasMicPermission    = {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translatorManager.stop()
    }
}

// ─────────────────────────────────────────────
// COMPOSE UI
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    translatorManager: TranslatorManager,
    onRequestPermission: () -> Unit,
    hasMicPermission: () -> Boolean
) {
    var langA        by remember { mutableStateOf(LANGUAGES[0]) }  // Default: English
    var langB        by remember { mutableStateOf(LANGUAGES[1]) }  // Default: Malay
    var appState     by remember { mutableStateOf(AppState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedA    by remember { mutableStateOf(false) }
    var expandedB    by remember { mutableStateOf(false) }

    // Pulsing animation for the LISTENING indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.35f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Translator") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            // ── Language Selectors ──────────────────────────────

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Select Languages", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

                // Language A
                ExposedDropdownMenuBox(
                    expanded        = expandedA,
                    onExpandedChange = { if (appState == AppState.IDLE) expandedA = it }
                ) {
                    OutlinedTextField(
                        value         = langA.name,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Person A speaks") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedA) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded        = expandedA,
                        onDismissRequest = { expandedA = false }
                    ) {
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang.name) },
                                onClick = { langA = lang; expandedA = false }
                            )
                        }
                    }
                }

                // Language B
                ExposedDropdownMenuBox(
                    expanded        = expandedB,
                    onExpandedChange = { if (appState == AppState.IDLE) expandedB = it }
                ) {
                    OutlinedTextField(
                        value         = langB.name,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Person B speaks") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedB) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded        = expandedB,
                        onDismissRequest = { expandedB = false }
                    ) {
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang.name) },
                                onClick = { langB = lang; expandedB = false }
                            )
                        }
                    }
                }
            }

            // ── Status Indicator ────────────────────────────────

            Box(
                modifier           = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment   = Alignment.Center
            ) {
                when (appState) {
                    AppState.IDLE -> {
                        Text("Ready. Press Start.", color = Color.Gray, fontSize = 16.sp)
                    }
                    AppState.LISTENING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .scale(pulseScale)
                                    .size(80.dp)
                                    .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Listening…", color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    AppState.SPEAKING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFF2196F3), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Speaking translation…", color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // ── Error Message ───────────────────────────────────

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            // ── Start / Stop Button ─────────────────────────────

            Button(
                onClick = {
                    errorMessage = null
                    when {
                        appState != AppState.IDLE -> {
                            translatorManager.stop()
                            appState = AppState.IDLE
                        }
                        !hasMicPermission() -> {
                            onRequestPermission()
                        }
                        langA == langB -> {
                            errorMessage = "Please select two different languages."
                        }
                        else -> {
                            translatorManager.start(
                                langA         = langA,
                                langB         = langB,
                                onStateChange = { appState = it },
                                onError       = { errorMessage = it }
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (appState == AppState.IDLE)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text     = if (appState == AppState.IDLE) "▶  Start Conversation" else "■  Stop",
                    fontSize = 18.sp
                )
            }
        }
    }
}