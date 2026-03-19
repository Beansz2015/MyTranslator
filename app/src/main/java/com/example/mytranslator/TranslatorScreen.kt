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
    var langA        by remember { mutableStateOf(LANGUAGES[0]) }
    var langB        by remember { mutableStateOf(LANGUAGES[1]) }
    var appState     by remember { mutableStateOf(AppState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedA    by remember { mutableStateOf(false) }
    var expandedB    by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            LanguageSelectors(
                langA = langA, langB = langB, appState = appState,
                onLangA = { langA = it }, onLangB = { langB = it },
                expandedA = expandedA, expandedB = expandedB,
                onExpandA = { expandedA = it }, onExpandB = { expandedB = it }
            )
            StatusIndicator(appState = appState, pulseScale = pulseScale)
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            StartStopButton(
                appState = appState, hasMicPermission = hasMicPermission,
                onRequestPermission = onRequestPermission,
                langA = langA, langB = langB,
                translatorManager = translatorManager,
                onStateChange = { appState = it },
                onError = { errorMessage = it },
                onClearError = { errorMessage = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectors(
    langA: LangOption, langB: LangOption, appState: AppState,
    onLangA: (LangOption) -> Unit, onLangB: (LangOption) -> Unit,
    expandedA: Boolean, expandedB: Boolean,
    onExpandA: (Boolean) -> Unit, onExpandB: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select Languages", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        LangDropdown(
            label = "Person A speaks", selected = langA, expanded = expandedA,
            enabled = appState == AppState.IDLE, onExpand = onExpandA, onSelect = onLangA
        )
        LangDropdown(
            label = "Person B speaks", selected = langB, expanded = expandedB,
            enabled = appState == AppState.IDLE, onExpand = onExpandB, onSelect = onLangB
        )
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
            value = selected.name, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) {
            LANGUAGES.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.name) },
                    onClick = { onSelect(lang); onExpand(false) }
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(appState: AppState, pulseScale: Float) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
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

@Composable
private fun StartStopButton(
    appState: AppState, hasMicPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    langA: LangOption, langB: LangOption,
    translatorManager: TranslatorManager,
    onStateChange: (AppState) -> Unit,
    onError: (String) -> Unit,
    onClearError: () -> Unit
) {
    Button(
        onClick = {
            onClearError()
            when {
                appState != AppState.IDLE -> { translatorManager.stop(); onStateChange(AppState.IDLE) }
                !hasMicPermission() -> onRequestPermission()
                langA == langB -> onError("Please select two different languages.")
                else -> translatorManager.start(
                    langA = langA, langB = langB,
                    onStateChange = onStateChange, onError = onError
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
            text = if (appState == AppState.IDLE) "▶  Start Conversation" else "■  Stop",
            fontSize = 18.sp
        )
    }
}
