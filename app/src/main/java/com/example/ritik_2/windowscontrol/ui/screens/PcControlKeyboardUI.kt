package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class KeyColor { NORMAL, SPECIAL, WIN, ALT, DANGER, MEDIA }

// ─────────────────────────────────────────────────────────────
//  PcControlKeyboardUI v3
//  - Swipe left/right to switch between tabs
//  - Large buttons (56dp height)
//  - Win+L uses SYSTEM_CMD:LOCK (not key shortcut)
//  - Tabs: Navigation | Windows | Shortcuts | Alt | System | F-Keys
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PcControlKeyboardUI(viewModel: PcControlViewModel) {

    var typeText   by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    val tabs = listOf("Navigation", "Windows", "Shortcuts", "Alt", "System", "F-Keys")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val topBg = MaterialTheme.colorScheme.surface
    val tabSelected = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Type + send bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = typeText,
                        onValueChange = { typeText = it },
                        placeholder   = { Text("Type text to send…", style = MaterialTheme.typography.bodySmall) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = true
                    )
                    FilledTonalButton(
                        onClick = {
                            if (typeText.isNotEmpty()) {
                                viewModel.sendText(typeText)
                                lastAction = "Sent: ${typeText.take(20)}"
                                typeText = ""
                            }
                        },
                        enabled = typeText.isNotEmpty(),
                        shape   = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send")
                    }
                    IconButton(onClick = { viewModel.navigateTo(PcScreen.TOUCHPAD) }) {
                        Icon(Icons.Default.Mouse, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (lastAction.isNotEmpty()) {
                    Text(
                        "✓ $lastAction",
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.primary
                    )
                }

                // Scrollable tab row
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding      = 8.dp,
                    divider          = {},
                    indicator        = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = tabSelected
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick  = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text     = {
                                Text(
                                    tab,
                                    fontWeight = if (pagerState.currentPage == i)
                                        FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Swipeable pages
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (page) {
                    0 -> navigationPage(viewModel) { lastAction = it }
                    1 -> windowsPage(viewModel)    { lastAction = it }
                    2 -> shortcutsPage(viewModel)  { lastAction = it }
                    3 -> altPage(viewModel)         { lastAction = it }
                    4 -> systemPage(viewModel)      { lastAction = it }
                    5 -> fKeysPage(viewModel)       { lastAction = it }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PAGE BUILDERS
// ─────────────────────────────────────────────────────────────

private fun LazyListScope.navigationPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("Arrow Keys") }
    item {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KbBtn("↑", "UP", KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KbBtn("←", "LEFT",  KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
                KbBtn("↓", "DOWN",  KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
                KbBtn("→", "RIGHT", KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
            }
        }
    }
    item { KbSectionLabel("Navigation") }
        KbGrid(
            listOf(
                Triple("Home",     "HOME",      KeyColor.SPECIAL),
                Triple("End",      "END",       KeyColor.SPECIAL),
                Triple("PgUp",     "PAGE_UP",   KeyColor.SPECIAL),
                Triple("PgDn",     "PAGE_DOWN", KeyColor.SPECIAL),
                Triple("Insert",   "INSERT",    KeyColor.NORMAL),
                Triple("Delete",   "DELETE",    KeyColor.DANGER),
                Triple("⌫ Back",  "BACKSPACE", KeyColor.NORMAL),
                Triple("Tab →",   "TAB",       KeyColor.NORMAL),
            ), vm, onAction
        )

    item { KbSectionLabel("Common") }
        KbGrid(
            listOf(
                Triple("Enter ↵", "ENTER",     KeyColor.SPECIAL),
                Triple("Esc",     "ESC",       KeyColor.DANGER),
                Triple("Space",   "SPACE",     KeyColor.NORMAL),
                Triple("PrtSc",   "PRINTSCREEN", KeyColor.NORMAL),
            ), vm, onAction
        )

}

private fun LazyListScope.windowsPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("⊞ Windows Key") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // WIN+L uses SYSTEM_CMD:LOCK — more reliable than key combo
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KbBtn("⊞ Win", "WIN", KeyColor.WIN,
                    Modifier.weight(1f), vm, onAction)
                // LOCK via system command — NOT WIN+L key (more reliable)
                SysCmdBtn("🔒 Lock", "LOCK", "Lock PC",
                    KeyColor.DANGER, Modifier.weight(1f), vm, onAction)
            }
        }
    }
    item { KbSectionLabel("⊞ Win + Letter") }
        KbGrid(
            listOf(
                Triple("⊞+D",  "WIN+D",   KeyColor.WIN),
                Triple("⊞+E",  "WIN+E",   KeyColor.WIN),
                Triple("⊞+R",  "WIN+R",   KeyColor.WIN),
                Triple("⊞+I",  "WIN+I",   KeyColor.WIN),
                Triple("⊞+A",  "WIN+A",   KeyColor.WIN),
                Triple("⊞+S",  "WIN+S",   KeyColor.WIN),
                Triple("⊞+X",  "WIN+X",   KeyColor.WIN),
                Triple("⊞+Tab","WIN+TAB", KeyColor.WIN),
            ), vm, onAction
        )

    item { KbSectionLabel("⊞ Win + Arrow (Snap)") }
        KbGrid(
            listOf(
                Triple("⊞+↑", "WIN+UP",    KeyColor.WIN),
                Triple("⊞+↓", "WIN+DOWN",  KeyColor.WIN),
                Triple("⊞+←", "WIN+LEFT",  KeyColor.WIN),
                Triple("⊞+→", "WIN+RIGHT", KeyColor.WIN),
            ), vm, onAction
        )

    item { KbSectionLabel("Run Commands (⊞+R)") }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("notepad","calc","cmd","explorer","control","taskmgr","msconfig")) { cmd ->
                OutlinedButton(
                    onClick = {
                        vm.executeQuickStep(PcStep("SYSTEM_CMD","WIN_R", args = listOf(cmd)))
                        onAction("Run: $cmd")
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(cmd, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

private fun LazyListScope.shortcutsPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("Common Shortcuts") }
        KbGrid(
            listOf(
                Triple("Copy",   "CTRL+C", KeyColor.SPECIAL),
                Triple("Paste",  "CTRL+V", KeyColor.SPECIAL),
                Triple("Cut",    "CTRL+X", KeyColor.SPECIAL),
                Triple("Undo",   "CTRL+Z", KeyColor.SPECIAL),
                Triple("Redo",   "CTRL+Y", KeyColor.SPECIAL),
                Triple("Save",   "CTRL+S", KeyColor.SPECIAL),
                Triple("All",    "CTRL+A", KeyColor.SPECIAL),
                Triple("Find",   "CTRL+F", KeyColor.SPECIAL),
                Triple("New",    "CTRL+N", KeyColor.SPECIAL),
                Triple("Open",   "CTRL+O", KeyColor.SPECIAL),
                Triple("Print",  "CTRL+P", KeyColor.SPECIAL),
                Triple("Close",  "CTRL+W", KeyColor.SPECIAL),
                Triple("NewTab", "CTRL+T", KeyColor.SPECIAL),
                Triple("Refresh","CTRL+R", KeyColor.SPECIAL),
            ), vm, onAction
        )

}

private fun LazyListScope.altPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("Alt Combos") }
        KbGrid(
            listOf(
                Triple("Alt+Tab",  "ALT+TAB",   KeyColor.ALT),
                Triple("Alt+F4",   "ALT+F4",    KeyColor.DANGER),
                Triple("Alt+Enter","ALT+ENTER", KeyColor.ALT),
                Triple("Alt+Esc",  "ALT+ESC",   KeyColor.ALT),
            ), vm, onAction
        )

    item { KbSectionLabel("Ctrl+Alt") }
        KbGrid(
            listOf(
                Triple("Ctrl+Alt+Del","CTRL+ALT+DEL",    KeyColor.DANGER),
                Triple("Ctrl+Sh+Esc","CTRL+SHIFT+ESC",   KeyColor.DANGER),
            ), vm, onAction
        )

    item { KbSectionLabel("Modifier Keys") }
        KbGrid(
            listOf(
                Triple("Ctrl",  "CTRL",  KeyColor.SPECIAL),
                Triple("Shift", "SHIFT", KeyColor.SPECIAL),
                Triple("Alt",   "ALT",   KeyColor.ALT),
                Triple("⊞ Win", "WIN",   KeyColor.WIN),
            ), vm, onAction
        )

    item {
        KbBtn("Space Bar", "SPACE", KeyColor.NORMAL,
            Modifier.fillMaxWidth(), vm, onAction)
    }
}

private fun LazyListScope.systemPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("System Commands") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SysCmdBtn("🔒 Lock",       "LOCK",        "Lock PC",     KeyColor.DANGER,  Modifier.weight(1f), vm, onAction)
                SysCmdBtn("😴 Sleep",      "SLEEP",       "Sleep",       KeyColor.NORMAL,  Modifier.weight(1f), vm, onAction)
                SysCmdBtn("⏻ Shutdown",   "SHUTDOWN",    "Shutdown",    KeyColor.DANGER,  Modifier.weight(1f), vm, onAction)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SysCmdBtn("🔄 Restart",    "RESTART",     "Restart",     KeyColor.DANGER,  Modifier.weight(1f), vm, onAction)
                SysCmdBtn("📸 Snip",       "SCREENSHOT",  "Screenshot",  KeyColor.NORMAL,  Modifier.weight(1f), vm, onAction)
                SysCmdBtn("🖥 Desktop",    "OPEN_FOLDER", "Desktop",     KeyColor.NORMAL,  Modifier.weight(1f), vm, onAction)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SysCmdBtn("🔊 Vol+",       "VOLUME_UP",   "Vol Up",      KeyColor.MEDIA,   Modifier.weight(1f), vm, onAction)
                SysCmdBtn("🔇 Mute",       "MUTE",        "Mute",        KeyColor.MEDIA,   Modifier.weight(1f), vm, onAction)
                SysCmdBtn("🔉 Vol-",       "VOLUME_DOWN", "Vol Down",    KeyColor.MEDIA,   Modifier.weight(1f), vm, onAction)
            }
        }
    }
}

private fun LazyListScope.fKeysPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    item { KbSectionLabel("Function Keys") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { i ->
                    KbBtn("F$i", "F$i", KeyColor.SPECIAL, Modifier.weight(1f), vm, onAction)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (5..8).forEach { i ->
                    KbBtn("F$i", "F$i", KeyColor.SPECIAL, Modifier.weight(1f), vm, onAction)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (9..12).forEach { i ->
                    KbBtn("F$i", "F$i", KeyColor.SPECIAL, Modifier.weight(1f), vm, onAction)
                }
            }
        }
    }
    item { KbSectionLabel("ESC Row") }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KbBtn("Esc",   "ESC",   KeyColor.DANGER,  Modifier.weight(1.5f), vm, onAction)
            KbBtn("Enter", "ENTER", KeyColor.SPECIAL, Modifier.weight(1f),   vm, onAction)
            KbBtn("Tab",   "TAB",   KeyColor.NORMAL,  Modifier.weight(1f),   vm, onAction)
            KbBtn("⌫",    "BACKSPACE", KeyColor.NORMAL, Modifier.weight(1f), vm, onAction)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
fun KbSectionLabel(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier   = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

fun LazyListScope.KbGrid(
    keys    : List<Triple<String, String, KeyColor>>,
    vm      : PcControlViewModel,
    onAction: (String) -> Unit,
    cols    : Int = 4
) {
    val rows = keys.chunked(cols)
    rows.forEach { row ->
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { (label, key, color) ->
                    KbBtn(label, key, color, Modifier.weight(1f), vm, onAction)
                }
                // Fill empty slots
                repeat(cols - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun KbBtn(
    label   : String,
    keyValue: String,
    color   : KeyColor,
    modifier: Modifier = Modifier,
    vm      : PcControlViewModel,
    onAction: (String) -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val bg = when (color) {
        KeyColor.NORMAL  -> MaterialTheme.colorScheme.surfaceVariant
        KeyColor.SPECIAL -> MaterialTheme.colorScheme.secondaryContainer
        KeyColor.WIN     -> MaterialTheme.colorScheme.primaryContainer
        KeyColor.ALT     -> MaterialTheme.colorScheme.tertiaryContainer
        KeyColor.DANGER  -> MaterialTheme.colorScheme.errorContainer
        KeyColor.MEDIA   -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val fg = when (color) {
        KeyColor.NORMAL  -> MaterialTheme.colorScheme.onSurfaceVariant
        KeyColor.SPECIAL -> MaterialTheme.colorScheme.onSecondaryContainer
        KeyColor.WIN     -> MaterialTheme.colorScheme.onPrimaryContainer
        KeyColor.ALT     -> MaterialTheme.colorScheme.onTertiaryContainer
        KeyColor.DANGER  -> MaterialTheme.colorScheme.error
        KeyColor.MEDIA   -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(80); pressed = false }
            vm.sendKey(keyValue)
            onAction("⌨ $label")
        },
        modifier       = modifier.height(56.dp),  // Large buttons
        shape          = RoundedCornerShape(10.dp),
        color          = if (pressed) fg.copy(0.2f) else bg,
        border         = BorderStroke(
            if (pressed) 1.5.dp else 1.dp,
            if (pressed) fg else fg.copy(0.25f)
        ),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                label,
                fontSize   = if (label.length > 7) 9.sp else if (label.length > 4) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (pressed) fg else fg,
                textAlign  = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

// System command button (uses SYSTEM_CMD not key press)
@Composable
fun SysCmdBtn(
    label   : String,
    cmd     : String,
    hint    : String,
    color   : KeyColor,
    modifier: Modifier = Modifier,
    vm      : PcControlViewModel,
    onAction: (String) -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val bg = when (color) {
        KeyColor.DANGER -> MaterialTheme.colorScheme.errorContainer
        KeyColor.MEDIA  -> MaterialTheme.colorScheme.tertiaryContainer
        else            -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (color) {
        KeyColor.DANGER -> MaterialTheme.colorScheme.error
        KeyColor.MEDIA  -> MaterialTheme.colorScheme.onTertiaryContainer
        else            -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { pressed = true; delay(80); pressed = false }
            vm.executeQuickStep(PcStep("SYSTEM_CMD", cmd))
            onAction("⚡ $hint")
        },
        modifier       = modifier.height(56.dp),
        shape          = RoundedCornerShape(10.dp),
        color          = if (pressed) fg.copy(0.2f) else bg,
        border         = BorderStroke(
            if (pressed) 1.5.dp else 1.dp,
            if (pressed) fg else fg.copy(0.25f)
        ),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                label,
                fontSize   = if (label.length > 7) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color      = fg,
                textAlign  = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}