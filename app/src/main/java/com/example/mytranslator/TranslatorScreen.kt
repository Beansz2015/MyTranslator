package com.example.mytranslator

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    // Default to Chinese (Mandarin) for Person B
    var langB           by remember { mutableStateOf(LANGUAGES.first { it.name == "Chinese (Mandarin)" }) }
    var appState        by remember { mutableStateOf(AppState.IDLE) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var expandedA       by remember { mutableStateOf(false) }
    var expandedB       by remember { mutableStateOf(false) }
    var autoMode        by remember { mutableStateOf(false) }
    var detectedLang    by remember { mutableStateOf<LangOption?>(null) }
    var showSettings    by remember { mutableStateOf(false) }

    // Your requested initial 8 languages
    var autoCandidates by remember {
        mutableStateOf(
            LANGUAGES.filter {
                it.name in listOf("English", "Malay", "Chinese (Mandarin)", "Hindi", "Thai", "Bengali", "Filipino", "Japanese")
            }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f, targetValue   = 1.35f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
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
            AutoModeToggle(
                autoMode = autoMode,
                enabled  = appState == AppState.IDLE,
                onToggle = { autoMode = it; detectedLang = null },
                onOpenSettings = { showSettings = true }
            )

            LanguageSelectors(
                langA = langA, langB = langB, appState = appState, autoMode = autoMode,
                detectedLang = detectedLang, onLangA = { langA = it }, onLangB = { langB = it },
                expandedA = expandedA, expandedB = expandedB, onExpandA = { expandedA = it }, onExpandB = { expandedB = it }
            )

            StatusIndicator(appState = appState, pulseScale = pulseScale)

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }

            StartStopButton(
                appState = appState, hasMicPermission = hasMicPermission, onRequestPermission = onRequestPermission,
                autoMode = autoMode, autoCandidates = autoCandidates, langA = langA, langB = langB,
                translatorManager = translatorManager, onStateChange = { appState = it },
                onDetectedLang = { detectedLang = it }, onError = { errorMessage = it }, onClearError = { errorMessage = null }
            )
        }

        // Settings Dialog for picking up to 10 candidates
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = {
                    Column {
                        Text("Auto-Detect Shortlist")
                        Text("Select up to 10 languages", fontSize = 13.sp, color = Color.Gray)
                    }
                },
                text = {
                    LazyColumn {
                        items(LANGUAGES) { lang ->
                            val isSelected = autoCandidates.contains(lang)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            if (autoCandidates.size > 1) autoCandidates = autoCandidates - lang
                                        } else {
                                            if (autoCandidates.size < 10) autoCandidates = autoCandidates + lang
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(lang.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Done") }
                }
            )
        }
    }
}

@Composable
private fun AutoModeToggle(
    autoMode: Boolean, enabled: Boolean,
    onToggle: (Boolean) -> Unit, onOpenSettings: () -> Unit
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
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Auto-Detect Mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (autoMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (autoMode) "Auto-translate guest language" else "Manual selection", fontSize = 12.sp, color = if (autoMode) Color(0xFFB9F6CA) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (autoMode) {
            IconButton(onClick = onOpenSettings, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Switch(checked = autoMode, onCheckedChange = { if (enabled) onToggle(it) }, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectors(
    langA: LangOption, langB: LangOption, appState: AppState, autoMode: Boolean,
    detectedLang: LangOption?, onLangA: (LangOption) -> Unit, onLangB: (LangOption) -> Unit,
    expandedA: Boolean, expandedB: Boolean, onExpandA: (Boolean) -> Unit, onExpandB: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (autoMode) "Default Language (your staff's language)" else "Select Languages", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

        LangDropdown(label = if (autoMode) "Default language" else "Person A speaks", selected = langA, expanded = expandedA, enabled = appState == AppState.IDLE, onExpand = onExpandA, onSelect = onLangA)

        if (autoMode) {
            if (detectedLang == null) {
                OutlinedTextField(
                    value = "Waiting for guest...", onValueChange = {},
                    readOnly = true, enabled = false,
                    label = { Text("Guest language (auto-detected)") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Detected Guest Language", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        Spacer(Modifier.height(2.dp))
                        Text(detectedLang.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LangDropdown(label = "Person B speaks", selected = langB, expanded = expandedB, enabled = appState == AppState.IDLE, onExpand = onExpandB, onSelect = onLangB)
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
            value = selected.name, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) {
            LANGUAGES.forEach { lang -> DropdownMenuItem(text = { Text(lang.name) }, onClick = { onSelect(lang); onExpand(false) }) }
        }
    }
}

@Composable
private fun StatusIndicator(appState: AppState, pulseScale: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        when (appState) {
            AppState.IDLE -> Text("Ready. Press Start.", color = Color.Gray, fontSize = 16.sp)
            AppState.LISTENING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.scale(pulseScale).size(80.dp).background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.height(16.dp))
                    Text("Listening…", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            AppState.SPEAKING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).background(Color(0xFF2196F3), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.height(16.dp))
                    Text("Speaking translation…", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StartStopButton(
    appState: AppState, hasMicPermission: () -> Boolean, onRequestPermission: () -> Unit,
    autoMode: Boolean, autoCandidates: List<LangOption>,
    langA: LangOption, langB: LangOption, translatorManager: TranslatorManager,
    onStateChange: (AppState) -> Unit, onDetectedLang: (LangOption?) -> Unit,
    onError: (String) -> Unit, onClearError: () -> Unit
) {
    Button(
        onClick = {
            onClearError()
            when {
                appState != AppState.IDLE -> { translatorManager.stop(); onDetectedLang(null); onStateChange(AppState.IDLE) }
                !hasMicPermission() -> onRequestPermission()
                !autoMode && langA == langB -> onError("Please select two different languages.")
                autoMode -> translatorManager.startAutoMode(
                    defaultLang = langA, shortlist = autoCandidates,
                    onStateChange = onStateChange, onDetectedLang = onDetectedLang, onError = onError
                )
                else -> translatorManager.start(langA = langA, langB = langB, onStateChange = onStateChange, onError = onError)
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = if (appState == AppState.IDLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text = if (appState == AppState.IDLE) "▶  Start Conversation" else "■  Stop", fontSize = 18.sp)
    }
}
