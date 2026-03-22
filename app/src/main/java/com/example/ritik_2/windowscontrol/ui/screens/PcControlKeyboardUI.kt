package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen

// ─────────────────────────────────────────────────────────────
//  PcControlKeyboardUI — Full keyboard control screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlKeyboardUI(viewModel: PcControlViewModel) {

    var typeText by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⌨️ Keyboard", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.navigateTo(PcScreen.TOUCHPAD) }) {
                        Icon(Icons.Default.Mouse, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Touchpad")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Type & Send ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = typeText,
                    onValueChange = { typeText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type text to send to PC...") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    trailingIcon = {
                        if (typeText.isNotEmpty()) {
                            IconButton(onClick = { typeText = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    }
                )
                Button(
                    onClick = {
                        if (typeText.isNotEmpty()) {
                            viewModel.sendText(typeText)
                            lastAction = "Typed: \"$typeText\""
                            typeText = ""
                        }
                    },
                    enabled = typeText.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Send, null)
                }
            }

            if (lastAction.isNotEmpty()) {
                Text(
                    "✓ $lastAction",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── Function Keys ──
            PcKeySection("Function Keys") {
                PcKeyRow {
                    listOf("ESC","F1","F2","F3","F4","F5","F6",
                        "F7","F8","F9","F10","F11","F12").forEach { key ->
                        PcKey(
                            label = key,
                            isSpecial = true,
                            onClick = { viewModel.sendKey(key); lastAction = "Key: $key" }
                        )
                    }
                }
            }

            // ── Shortcuts ──
            PcKeySection("Shortcuts") {
                PcKeyRow {
                    listOf(
                        "CTRL+C" to "Copy",
                        "CTRL+V" to "Paste",
                        "CTRL+Z" to "Undo",
                        "CTRL+A" to "All",
                        "CTRL+S" to "Save",
                        "ALT+F4" to "Close",
                        "WIN+D"  to "Desktop",
                        "WIN+L"  to "Lock"
                    ).forEach { (key, hint) ->
                        PcKey(
                            label = key,
                            subtitle = hint,
                            isSpecial = true,
                            onClick = { viewModel.sendKey(key); lastAction = "Shortcut: $key" }
                        )
                    }
                }
            }

            // ── Navigation Keys ──
            PcKeySection("Navigation") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PcKeyRow {
                        listOf("HOME","END","PAGE_UP","PAGE_DOWN",
                            "DELETE","BACKSPACE","TAB","ENTER").forEach { key ->
                            PcKey(label = key, onClick = {
                                viewModel.sendKey(key); lastAction = "Key: $key"
                            })
                        }
                    }
                    // Arrow keys
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.size(48.dp))
                        PcKey(label = "↑", modifier = Modifier.size(48.dp),
                            onClick = { viewModel.sendKey("UP"); lastAction = "↑ UP" })
                        Spacer(Modifier.size(48.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PcKey(label = "←", modifier = Modifier.size(48.dp),
                            onClick = { viewModel.sendKey("LEFT"); lastAction = "← LEFT" })
                        PcKey(label = "↓", modifier = Modifier.size(48.dp),
                            onClick = { viewModel.sendKey("DOWN"); lastAction = "↓ DOWN" })
                        PcKey(label = "→", modifier = Modifier.size(48.dp),
                            onClick = { viewModel.sendKey("RIGHT"); lastAction = "→ RIGHT" })
                    }
                }
            }

            // ── Modifiers ──
            PcKeySection("Modifiers + Space") {
                PcKeyRow {
                    PcKey(label = "CTRL", isSpecial = true, modifier = Modifier.weight(1f),
                        onClick = { viewModel.sendKey("CTRL"); lastAction = "CTRL" })
                    PcKey(label = "ALT", isSpecial = true, modifier = Modifier.weight(1f),
                        onClick = { viewModel.sendKey("ALT"); lastAction = "ALT" })
                    PcKey(label = "SHIFT", isSpecial = true, modifier = Modifier.weight(1f),
                        onClick = { viewModel.sendKey("SHIFT"); lastAction = "SHIFT" })
                    PcKey(label = "SPACE", modifier = Modifier.weight(2f),
                        onClick = { viewModel.sendKey("SPACE"); lastAction = "SPACE" })
                }
            }

            // ── System Quick Commands ──
            PcKeySection("System Commands") {
                PcKeyRow {
                    listOf(
                        "LOCK" to "🔒",
                        "SLEEP" to "😴",
                        "MUTE" to "🔇",
                        "VOLUME_UP" to "🔊",
                        "VOLUME_DOWN" to "🔉",
                        "SCREENSHOT" to "📸"
                    ).forEach { (cmd, icon) ->
                        PcKey(
                            label = icon,
                            subtitle = cmd.lowercase().replace("_", " "),
                            isSpecial = true,
                            onClick = {
                                viewModel.executeQuickStep(
                                    com.example.ritik_2.windowscontrol.data.PcStep("SYSTEM_CMD", cmd)
                                )
                                lastAction = "$icon $cmd"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  KEY COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
fun PcKeySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f,
                androidx.compose.ui.unit.TextUnitType.Sp)
        )
        content()
    }
}

@Composable
fun PcKeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
fun PcKey(
    label: String,
    subtitle: String = "",
    isSpecial: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Surface(
        onClick = { pressed = true; onClick(); pressed = false },
        modifier = modifier.then(
            if (modifier == Modifier) Modifier else Modifier
        ),
        shape = RoundedCornerShape(9.dp),
        color = if (pressed) MaterialTheme.colorScheme.primary
        else if (isSpecial) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isSpecial) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (pressed) 0.dp else 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = if (label.length > 4) 8.dp else 10.dp,
                vertical = 10.dp
            )
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = if (label.length > 6) 10.sp else 11.sp,
                color = if (pressed) MaterialTheme.colorScheme.onPrimary
                else if (isSpecial) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}