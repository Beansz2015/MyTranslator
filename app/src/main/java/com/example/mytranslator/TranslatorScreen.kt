package com.example.mytranslator

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    translatorManager: TranslatorManager,
    onRequestPermission: () -> Unit,
    hasMicPermission: () -> Boolean
) {
    var langA           by remember { mutableStateOf(LANGUAGES[0]) }
    var langB           by remember { mutableStateOf(LANGUAGES[1]) }
    var appState        by remember { mutableStateOf(AppState.IDLE) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var expandedA       by remember { mutableStateOf(false) }
    var expandedB       by remember { mutableStateOf(false) }
    var autoMode        by remember { mutableStateOf(false) }
    var detectedLang    by remember { mutableStateOf<LangOption?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.35f,
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Auto Mode Toggle ──────────────────────────────────
            AutoModeToggle(
                autoMode = autoMode,
                enabled  = appState == AppState.IDLE,
                onToggle = {
                    autoMode     = it
                    detectedLang = null
                }
            )

            // ── Language Selectors ────────────────────────────────
            LanguageSelectors(
                langA      = langA,
                langB      = langB,
                appState   = appState,
                autoMode   = autoMode,
                detectedLang = detectedLang,
                onLangA    = { langA = it },
                onLangB    = { langB = it },
                expandedA  = expandedA,
                expandedB  = expandedB,
                onExpandA  = { expandedA = it },
                onExpandB  = { expandedB = it }
            )

            // ── Status Indicator ──────────────────────────────────
            StatusIndicator(appState = appState, pulseScale = pulseScale)

            // ── Error Message ─────────────────────────────────────
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            // ── Start / Stop Button ───────────────────────────────
            StartStopButton(
                appState            = appState,
                hasMicPermission    = hasMicPermission,
                onRequestPermission = onRequestPermission,
                autoMode            = autoMode,
                langA               = langA,
                langB               = langB,
                translatorManager   = translatorManager,
                onStateChange       = { appState = it },
                onDetectedLang      = { detectedLang = it },
                onError             = { errorMessage = it },
                onClearError        = { errorMessage = null }
            )
        }
    }
}

// ── Auto Mode Toggle ──────────────────────────────────────────────────────────

@Composable
private fun AutoModeToggle(
    autoMode: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (autoMode) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Wrap the Column in a weight modifier so it doesn't push the Switch off-screen
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "Auto-Detect Mode",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = if (autoMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // Shortened string here:
                if (autoMode) "Auto-translate guest language"
                else "Manual selection",
                fontSize = 12.sp,
                color    = if (autoMode) Color(0xFFB9F6CA) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Switch(
            checked  = autoMode,
            onCheckedChange = { if (enabled) onToggle(it) },
            enabled  = enabled
        )
    }
}


// ── Language Selectors ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectors(
    langA: LangOption, langB: LangOption,
    appState: AppState,
    autoMode: Boolean,
    detectedLang: LangOption?,
    onLangA: (LangOption) -> Unit, onLangB: (LangOption) -> Unit,
    expandedA: Boolean, expandedB: Boolean,
    onExpandA: (Boolean) -> Unit, onExpandB: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (autoMode) "Default Language (your staff's language)"
            else "Select Languages",
            fontWeight = FontWeight.SemiBold,
            fontSize   = 17.sp
        )

        // Person A — always active
        LangDropdown(
            label    = if (autoMode) "Default language" else "Person A speaks",
            selected = langA,
            expanded = expandedA,
            enabled  = appState == AppState.IDLE,
            onExpand = onExpandA,
            onSelect = onLangA
        )

        // Person B — disabled in auto mode, shows detected language instead
        if (autoMode) {
            OutlinedTextField(
                value         = detectedLang?.name ?: "Waiting for guest…",
                onValueChange = {},
                readOnly      = true,
                enabled       = false,
                label         = { Text("Guest language (auto-detected)") },
                modifier      = Modifier.fillMaxWidth()
            )
        } else {
            LangDropdown(
                label    = "Person B speaks",
                selected = langB,
                expanded = expandedB,
                enabled  = appState == AppState.IDLE,
                onExpand = onExpandB,
                onSelect = onLangB
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangDropdown(
    label: String, selected: LangOption, expanded: Boolean,
    enabled: Boolean, onExpand: (Boolean) -> Unit, onSelect: (LangOption) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) onExpand(it) }) {
        OutlinedTextField(
            value         = selected.name,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) {
            LANGUAGES.forEach { lang ->
                DropdownMenuItem(
                    text    = { Text(lang.name) },
                    onClick = { onSelect(lang); onExpand(false) }
                )
            }
        }
    }
}

// ── Status Indicator ──────────────────────────────────────────────────────────

@Composable
private fun StatusIndicator(appState: AppState, pulseScale: Float) {
    Box(
        modifier         = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        when (appState) {
            AppState.IDLE -> Text("Ready. Press Start.", color = Color.Gray, fontSize = 16.sp)
            AppState.LISTENING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.scale(pulseScale).size(80.dp)
                            .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Listening…", color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            AppState.SPEAKING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(80.dp)
                            .background(Color(0xFF2196F3), androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Speaking translation…", color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── Start / Stop Button ───────────────────────────────────────────────────────

@Composable
private fun StartStopButton(
    appState: AppState,
    hasMicPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    autoMode: Boolean,
    langA: LangOption,
    langB: LangOption,
    translatorManager: TranslatorManager,
    onStateChange: (AppState) -> Unit,
    onDetectedLang: (LangOption?) -> Unit,
    onError: (String) -> Unit,
    onClearError: () -> Unit
) {
    Button(
        onClick = {
            onClearError()
            when {
                appState != AppState.IDLE -> {
                    translatorManager.stop()
                    onDetectedLang(null)   // clear detected lang display on Stop
                    onStateChange(AppState.IDLE)
                }
                !hasMicPermission() -> onRequestPermission()
                !autoMode && langA == langB -> onError("Please select two different languages.")
                autoMode -> translatorManager.startAutoMode(
                    defaultLang    = langA,
                    onStateChange  = onStateChange,
                    onDetectedLang = onDetectedLang,
                    onError        = onError
                )
                else -> translatorManager.start(
                    langA         = langA,
                    langB         = langB,
                    onStateChange = onStateChange,
                    onError       = onError
                )
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (appState == AppState.IDLE)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(
            text     = if (appState == AppState.IDLE) "▶  Start Conversation" else "■  Stop",
            fontSize = 18.sp
        )
    }
}
