package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  PcControlKeyboardUI v2
//  - Win key section added
//  - WIN+R, WIN+L, WIN+D, WIN+E, WIN+Tab
//  - Multi-key combos: Ctrl+Alt+Del simulation
//  - Alt key section (Alt+Tab, Alt+F4)
//  - Long press on key = hold key (for combos)
//  - Landscape layout
// ─────────────────────────────────────────────────────────────

// Key data class
data class PcKeyData(
    val label: String,
    val keyValue: String,
    val subtitle: String = "",
    val isSpecial: Boolean = false,
    val isWide: Boolean = false,
    val widthWeight: Float = 1f,
    val color: KeyColor = KeyColor.NORMAL
)

enum class KeyColor { NORMAL, SPECIAL, WIN, ALT, DANGER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlKeyboardUI(viewModel: PcControlViewModel) {

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp
    var typeText      by remember { mutableStateOf("") }
    var lastAction    by remember { mutableStateOf("") }
    var activeTab     by remember { mutableIntStateOf(0) }

    val tabs = listOf("F-Keys", "Shortcuts", "Win", "Alt", "Nav", "System")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⌨️ Keyboard", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.navigateTo(PcScreen.TOUCHPAD) }) {
                        Icon(Icons.Default.Mouse, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pad", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Left: type bar + tab list
                Column(
                    modifier = Modifier.width(140.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = typeText,
                        onValueChange = { typeText = it },
                        placeholder = { Text("Type...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (typeText.isNotEmpty()) {
                                viewModel.sendText(typeText)
                                lastAction = "Sent: \"${typeText.take(20)}\""
                                typeText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = typeText.isNotEmpty(),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Send ↵", style = MaterialTheme.typography.labelSmall) }

                    HorizontalDivider()

                    tabs.forEachIndexed { i, tab ->
                        Surface(
                            onClick = { activeTab = i },
                            shape = RoundedCornerShape(8.dp),
                            color = if (activeTab == i) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                tab,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (activeTab == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (lastAction.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "✓ $lastAction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Right: key grid
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                    KeyTabContent(
                        activeTab = activeTab,
                        viewModel = viewModel,
                        onAction = { lastAction = it }
                    )
                }
            }
        } else {
            // Portrait
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // Type bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typeText,
                        onValueChange = { typeText = it },
                        placeholder = { Text("Type text to send to PC...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (typeText.isNotEmpty()) {
                                viewModel.sendText(typeText)
                                lastAction = "Sent: \"${typeText.take(20)}\""
                                typeText = ""
                            }
                        },
                        enabled = typeText.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) { Icon(Icons.Default.Send, null) }
                }

                if (lastAction.isNotEmpty()) {
                    Text(
                        "✓ $lastAction",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = activeTab == i,
                            onClick  = { activeTab = i },
                            text     = { Text(tab, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    KeyTabContent(
                        activeTab = activeTab,
                        viewModel = viewModel,
                        onAction  = { lastAction = it }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  KEY TAB CONTENT — All key sections
// ─────────────────────────────────────────────────────────────

@Composable
fun KeyTabContent(
    activeTab: Int,
    viewModel: PcControlViewModel,
    onAction: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
    ) {
        when (activeTab) {
            0 -> FunctionKeysSection(viewModel, onAction)
            1 -> ShortcutsSection(viewModel, onAction)
            2 -> WindowsKeySection(viewModel, onAction)
            3 -> AltKeySection(viewModel, onAction)
            4 -> NavigationSection(viewModel, onAction)
            5 -> SystemCommandsSection(viewModel, onAction)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: FUNCTION KEYS
// ─────────────────────────────────────────────────────────────

@Composable
fun FunctionKeysSection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("Function Keys")

    // ESC row
    KeyRow {
        PcKeyButton("ESC", "ESC", viewModel, onAction, color = KeyColor.DANGER, widthMod = Modifier.weight(1.5f))
        Spacer(Modifier.width(12.dp))
        listOf("F1","F2","F3","F4").forEach {
            PcKeyButton(it, it, viewModel, onAction, color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf("F5","F6","F7","F8","F9","F10","F11","F12").forEach {
            PcKeyButton(it, it, viewModel, onAction, color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }

    KeySectionLabel("Action Keys")
    KeyRow {
        listOf(
            Triple("⌫", "BACKSPACE", ""),
            Triple("↵", "ENTER", ""),
            Triple("⇥", "TAB", ""),
            Triple("DEL", "DELETE", ""),
            Triple("INS", "INSERT", ""),
        ).forEach { (label, key, _) ->
            PcKeyButton(label, key, viewModel, onAction, widthMod = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: SHORTCUTS
// ─────────────────────────────────────────────────────────────

@Composable
fun ShortcutsSection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("Common Shortcuts")
    KeyRow {
        listOf(
            Triple("Copy", "CTRL+C", "Ctrl+C"),
            Triple("Paste", "CTRL+V", "Ctrl+V"),
            Triple("Cut", "CTRL+X", "Ctrl+X"),
            Triple("Undo", "CTRL+Z", "Ctrl+Z"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf(
            Triple("Redo", "CTRL+Y", "Ctrl+Y"),
            Triple("Save", "CTRL+S", "Ctrl+S"),
            Triple("All", "CTRL+A", "Ctrl+A"),
            Triple("Find", "CTRL+F", "Ctrl+F"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf(
            Triple("New", "CTRL+N", "Ctrl+N"),
            Triple("Open", "CTRL+O", "Ctrl+O"),
            Triple("Print", "CTRL+P", "Ctrl+P"),
            Triple("Close", "CTRL+W", "Ctrl+W"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }
    KeySectionLabel("Close / Switch")
    KeyRow {
        PcKeyButton("Alt+F4", "ALT+F4", viewModel, onAction, subtitle = "Close App",
            color = KeyColor.DANGER, widthMod = Modifier.weight(1f))
        PcKeyButton("Prt Sc", "PRINTSCREEN", viewModel, onAction, subtitle = "Screenshot",
            widthMod = Modifier.weight(1f))
        PcKeyButton("Ctrl+Z", "CTRL+Z", viewModel, onAction, subtitle = "Undo",
            color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        PcKeyButton("Space", "SPACE", viewModel, onAction, widthMod = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: WINDOWS KEY  ← NEW
// ─────────────────────────────────────────────────────────────

@Composable
fun WindowsKeySection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("⊞ Windows Key Alone")
    KeyRow {
        PcKeyButton("⊞ Win", "WIN", viewModel, onAction, subtitle = "Start Menu",
            color = KeyColor.WIN, widthMod = Modifier.weight(2f))
        PcKeyButton("⊞+Tab", "WIN+TAB", viewModel, onAction, subtitle = "Task View",
            color = KeyColor.WIN, widthMod = Modifier.weight(2f))
    }

    KeySectionLabel("⊞ Windows + Letter")
    KeyRow {
        listOf(
            Triple("⊞+D", "WIN+D", "Desktop"),
            Triple("⊞+E", "WIN+E", "Explorer"),
            Triple("⊞+L", "WIN+L", "Lock PC"),
            Triple("⊞+R", "WIN+R", "Run..."),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.WIN, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf(
            Triple("⊞+I", "WIN+I", "Settings"),
            Triple("⊞+A", "WIN+A", "Action Ctr"),
            Triple("⊞+S", "WIN+S", "Search"),
            Triple("⊞+X", "WIN+X", "Power Menu"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.WIN, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf(
            Triple("⊞+↑", "WIN+UP", "Maximize"),
            Triple("⊞+↓", "WIN+DOWN", "Minimize"),
            Triple("⊞+←", "WIN+LEFT", "Snap Left"),
            Triple("⊞+→", "WIN+RIGHT", "Snap Right"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.WIN, widthMod = Modifier.weight(1f))
        }
    }

    KeySectionLabel("Run Commands (WIN+R then type)")
    val runCommands = listOf(
        "notepad" to "Notepad",
        "calc" to "Calculator",
        "cmd" to "Command Prompt",
        "explorer" to "File Explorer",
        "control" to "Control Panel",
        "taskmgr" to "Task Manager",
        "msconfig" to "System Config",
        "regedit" to "Registry Editor",
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(runCommands) { (cmd, label) ->
            OutlinedButton(
                onClick = {
                    viewModel.executeQuickStep(
                        PcStep("SYSTEM_CMD", "WIN_R", args = listOf(cmd))
                    )
                    onAction("⊞+R: $cmd")
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cmd, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: ALT KEY  ← NEW
// ─────────────────────────────────────────────────────────────

@Composable
fun AltKeySection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("Alt Key Combos")
    KeyRow {
        PcKeyButton("Alt", "ALT", viewModel, onAction, subtitle = "Hold Alt",
            color = KeyColor.ALT, widthMod = Modifier.weight(1f))
        PcKeyButton("Alt+Tab", "ALT+TAB", viewModel, onAction, subtitle = "Switch App",
            color = KeyColor.ALT, widthMod = Modifier.weight(1.5f))
        PcKeyButton("Alt+F4", "ALT+F4", viewModel, onAction, subtitle = "Close",
            color = KeyColor.DANGER, widthMod = Modifier.weight(1.5f))
    }
    KeyRow {
        listOf(
            Triple("Alt+F1", "ALT+F1", ""),
            Triple("Alt+F2", "ALT+F2", ""),
            Triple("Alt+Enter", "ALT+ENTER", "Properties"),
            Triple("Alt+Esc", "ALT+ESC", "Minimize"),
        ).forEach { (label, key, hint) ->
            PcKeyButton(label, key, viewModel, onAction, subtitle = hint,
                color = KeyColor.ALT, widthMod = Modifier.weight(1f))
        }
    }

    KeySectionLabel("Ctrl+Alt Combos")
    KeyRow {
        PcKeyButton("Ctrl+Alt+Del", "CTRL+ALT+DEL", viewModel, onAction,
            subtitle = "Task Mgr", color = KeyColor.DANGER, widthMod = Modifier.weight(2f))
        PcKeyButton("Ctrl+Shift+Esc", "CTRL+SHIFT+ESC", viewModel, onAction,
            subtitle = "Task Mgr", color = KeyColor.SPECIAL, widthMod = Modifier.weight(2f))
    }

    KeySectionLabel("Modifier Keys")
    KeyRow {
        listOf(
            Triple("Ctrl", "CTRL", ""),
            Triple("Shift", "SHIFT", ""),
            Triple("Alt", "ALT", ""),
            Triple("⊞ Win", "WIN", ""),
        ).forEach { (label, key, _) ->
            PcKeyButton(label, key, viewModel, onAction,
                color = KeyColor.SPECIAL, widthMod = Modifier.weight(1f))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PcKeyButton("SPACE", "SPACE", viewModel, onAction, widthMod = Modifier.weight(3f))
        PcKeyButton("↵", "ENTER", viewModel, onAction, widthMod = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: NAVIGATION
// ─────────────────────────────────────────────────────────────

@Composable
fun NavigationSection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("Arrow Keys")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            PcKeyButton("↑", "UP", viewModel, onAction, widthMod = Modifier.size(52.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PcKeyButton("←", "LEFT", viewModel, onAction, widthMod = Modifier.size(52.dp))
            PcKeyButton("↓", "DOWN", viewModel, onAction, widthMod = Modifier.size(52.dp))
            PcKeyButton("→", "RIGHT", viewModel, onAction, widthMod = Modifier.size(52.dp))
        }
    }

    KeySectionLabel("Navigation")
    KeyRow {
        listOf(
            Triple("Home", "HOME", ""),
            Triple("End", "END", ""),
            Triple("PgUp", "PAGE_UP", ""),
            Triple("PgDn", "PAGE_DOWN", ""),
        ).forEach { (label, key, _) ->
            PcKeyButton(label, key, viewModel, onAction, widthMod = Modifier.weight(1f))
        }
    }
    KeyRow {
        listOf(
            Triple("Del", "DELETE", ""),
            Triple("Ins", "INSERT", ""),
            Triple("⌫ Back", "BACKSPACE", ""),
            Triple("Tab →", "TAB", ""),
        ).forEach { (label, key, _) ->
            PcKeyButton(label, key, viewModel, onAction, widthMod = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SECTION: SYSTEM COMMANDS
// ─────────────────────────────────────────────────────────────

@Composable
fun SystemCommandsSection(viewModel: PcControlViewModel, onAction: (String) -> Unit) {
    KeySectionLabel("System Actions")
    val sysActions: List<Triple<String, String, String>> = listOf(
        Triple("🔒", "LOCK", "Lock PC"),
        Triple("😴", "SLEEP", "Sleep"),
        Triple("🔇", "MUTE", "Mute"),
        Triple("🔊", "VOLUME_UP", "Vol Up"),
        Triple("🔉", "VOLUME_DOWN", "Vol Dn"),
        Triple("📸", "SCREENSHOT", "Snip"),
        Triple("📁", "OPEN_FOLDER", "Explorer"),
        Triple("⚙", "SETTINGS", "Settings"),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(sysActions) { (icon, cmd, label) ->
            Surface(
                onClick = {
                    viewModel.executeQuickStep(PcStep("SYSTEM_CMD", cmd))
                    onAction("$icon $label")
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp, 10.dp)
                ) {
                    Text(icon, fontSize = 22.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
fun KeySectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
fun PcKeyButton(
    label: String,
    keyValue: String,
    viewModel: PcControlViewModel,
    onAction: (String) -> Unit,
    subtitle: String = "",
    color: KeyColor = KeyColor.NORMAL,
    widthMod: Modifier = Modifier
) {
    val haptic    = LocalHapticFeedback.current
    val scope     = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val containerColor = when (color) {
        KeyColor.NORMAL  -> MaterialTheme.colorScheme.surfaceVariant
        KeyColor.SPECIAL -> MaterialTheme.colorScheme.secondaryContainer
        KeyColor.WIN     -> MaterialTheme.colorScheme.primaryContainer
        KeyColor.ALT     -> MaterialTheme.colorScheme.tertiaryContainer
        KeyColor.DANGER  -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (color) {
        KeyColor.NORMAL  -> MaterialTheme.colorScheme.onSurfaceVariant
        KeyColor.SPECIAL -> MaterialTheme.colorScheme.onSecondaryContainer
        KeyColor.WIN     -> MaterialTheme.colorScheme.onPrimaryContainer
        KeyColor.ALT     -> MaterialTheme.colorScheme.onTertiaryContainer
        KeyColor.DANGER  -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                isPressed = true
                delay(80)
                isPressed = false
            }
            viewModel.sendKey(keyValue)
            val display = if (subtitle.isNotEmpty()) "$label ($subtitle)" else label
            onAction("⌨ $display")
        },
        modifier = widthMod,
        shape = RoundedCornerShape(9.dp),
        color = if (isPressed) MaterialTheme.colorScheme.primary else containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isPressed) 0.dp else 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = if (label.length > 5) 9.sp else 11.sp,
                color = if (isPressed) MaterialTheme.colorScheme.onPrimary else contentColor
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = if (isPressed) MaterialTheme.colorScheme.onPrimary.copy(0.8f)
                    else contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}