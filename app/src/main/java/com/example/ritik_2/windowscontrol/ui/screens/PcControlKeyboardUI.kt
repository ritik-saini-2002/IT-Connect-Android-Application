package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PcControlKeyboardUI(viewModel: PcControlViewModel) {
    var typeText   by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }
    val tabs       = listOf("Navigation", "Windows", "Shortcuts", "Alt", "System", "F-Keys")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope      = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
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
                                typeText   = ""
                            }
                        },
                        enabled = typeText.isNotEmpty(),
                        shape   = RoundedCornerShape(12.dp)
                    ) { Text("Send", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { viewModel.navigateTo(PcScreen.TOUCHPAD) }) {
                        Text("Pad", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (lastAction.isNotEmpty()) {
                    Text("✓ $lastAction",
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.primary)
                }
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding      = 8.dp,
                    divider          = {},
                    indicator        = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick  = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text     = {
                                Text(tab,
                                    fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                    fontSize   = 13.sp)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    when (page) {
                        0 -> NavigationPage(viewModel) { lastAction = it }
                        1 -> WindowsPage(viewModel)    { lastAction = it }
                        2 -> ShortcutsPage(viewModel)  { lastAction = it }
                        3 -> AltPage(viewModel)        { lastAction = it }
                        4 -> SystemPage(viewModel)     { lastAction = it }
                        5 -> FKeysPage(viewModel)      { lastAction = it }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable private fun NavigationPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("Arrow Keys")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KbBtn("Up",    "UP",    KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KbBtn("Left",  "LEFT",  KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
                KbBtn("Down",  "DOWN",  KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
                KbBtn("Right", "RIGHT", KeyColor.NORMAL, Modifier.width(72.dp), vm, onAction)
            }
        }
        KbSectionLabel("Navigation")
        KbGrid(listOf(
            Triple("Home",      "HOME",      KeyColor.SPECIAL),
            Triple("End",       "END",       KeyColor.SPECIAL),
            Triple("Pg Up",     "PAGE_UP",   KeyColor.SPECIAL),
            Triple("Pg Dn",     "PAGE_DOWN", KeyColor.SPECIAL),
            Triple("Insert",    "INSERT",    KeyColor.NORMAL),
            Triple("Delete",    "DELETE",    KeyColor.DANGER),
            Triple("Backspace", "BACKSPACE", KeyColor.NORMAL),
            Triple("Tab",       "TAB",       KeyColor.NORMAL),
        ), vm = vm, onAction = onAction)
        KbSectionLabel("Common")
        KbGrid(listOf(
            Triple("Enter",    "ENTER",       KeyColor.SPECIAL),
            Triple("Esc",      "ESC",         KeyColor.DANGER),
            Triple("Space",    "SPACE",       KeyColor.NORMAL),
            Triple("Print Scr","PRINTSCREEN", KeyColor.NORMAL),
        ), vm = vm, onAction = onAction)
    }
}

@Composable private fun WindowsPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("Windows Key")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KbBtn("Win",     "WIN",  KeyColor.WIN,   Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Lock PC","LOCK","Lock PC", KeyColor.DANGER, Modifier.weight(1f), vm, onAction)
        }
        KbSectionLabel("Win + Letter")
        KbGrid(listOf(
            Triple("Win+D","WIN+D",KeyColor.WIN), Triple("Win+E","WIN+E",KeyColor.WIN),
            Triple("Win+R","WIN+R",KeyColor.WIN), Triple("Win+I","WIN+I",KeyColor.WIN),
            Triple("Win+A","WIN+A",KeyColor.WIN), Triple("Win+S","WIN+S",KeyColor.WIN),
            Triple("Win+X","WIN+X",KeyColor.WIN), Triple("Win+Tab","WIN+TAB",KeyColor.WIN),
        ), vm = vm, onAction = onAction)
        KbSectionLabel("Win + Arrow (Snap)")
        KbGrid(listOf(
            Triple("Win+Up","WIN+UP",KeyColor.WIN),   Triple("Win+Down","WIN+DOWN",KeyColor.WIN),
            Triple("Win+Left","WIN+LEFT",KeyColor.WIN), Triple("Win+Right","WIN+RIGHT",KeyColor.WIN),
        ), vm = vm, onAction = onAction)
        KbSectionLabel("Run Dialog (Win+R)")
        listOf("notepad","calc","cmd","explorer","control","taskmgr","msconfig").chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cmd ->
                    OutlinedButton(onClick = { vm.executeQuickStep(PcStep("SYSTEM_CMD","WIN_R", args = listOf(cmd))); onAction("Run: $cmd") },
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)) { Text(cmd, style = MaterialTheme.typography.labelMedium) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun ShortcutsPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("Common Shortcuts")
        KbGrid(listOf(
            Triple("Copy",     "CTRL+C", KeyColor.SPECIAL), Triple("Paste",    "CTRL+V", KeyColor.SPECIAL),
            Triple("Cut",      "CTRL+X", KeyColor.SPECIAL), Triple("Undo",     "CTRL+Z", KeyColor.SPECIAL),
            Triple("Redo",     "CTRL+Y", KeyColor.SPECIAL), Triple("Save",     "CTRL+S", KeyColor.SPECIAL),
            Triple("Select All","CTRL+A",KeyColor.SPECIAL), Triple("Find",     "CTRL+F", KeyColor.SPECIAL),
            Triple("New",      "CTRL+N", KeyColor.SPECIAL), Triple("Open",     "CTRL+O", KeyColor.SPECIAL),
            Triple("Print",    "CTRL+P", KeyColor.SPECIAL), Triple("Close Tab","CTRL+W", KeyColor.SPECIAL),
            Triple("New Tab",  "CTRL+T", KeyColor.SPECIAL), Triple("Refresh",  "CTRL+R", KeyColor.SPECIAL),
        ), vm = vm, onAction = onAction)
    }
}

@Composable private fun AltPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("Alt Combos")
        KbGrid(listOf(
            Triple("Alt+Tab",   "ALT+TAB",   KeyColor.ALT),   Triple("Alt+F4",    "ALT+F4",    KeyColor.DANGER),
            Triple("Alt+Enter", "ALT+ENTER", KeyColor.ALT),   Triple("Alt+Esc",   "ALT+ESC",   KeyColor.ALT),
        ), vm = vm, onAction = onAction)
        KbSectionLabel("Ctrl+Alt")
        KbGrid(listOf(
            Triple("Ctrl+Alt+Del","CTRL+ALT+DEL",  KeyColor.DANGER),
            Triple("Ctrl+Sh+Esc", "CTRL+SHIFT+ESC",KeyColor.DANGER),
        ), vm = vm, onAction = onAction)
        KbSectionLabel("Modifier Keys")
        KbGrid(listOf(
            Triple("Ctrl","CTRL",KeyColor.SPECIAL), Triple("Shift","SHIFT",KeyColor.SPECIAL),
            Triple("Alt","ALT",  KeyColor.ALT),     Triple("Win","WIN",    KeyColor.WIN),
        ), vm = vm, onAction = onAction)
        KbBtn("Space Bar","SPACE",KeyColor.NORMAL,Modifier.fillMaxWidth(),vm,onAction)
    }
}

@Composable private fun SystemPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("System Commands")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysCmdBtn("Lock",     "LOCK",     "Lock PC",  KeyColor.DANGER, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Sleep",    "SLEEP",    "Sleep",    KeyColor.NORMAL, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Shutdown", "SHUTDOWN", "Shutdown", KeyColor.DANGER, Modifier.weight(1f), vm, onAction)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysCmdBtn("Restart",    "RESTART",    "Restart",    KeyColor.DANGER, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Screenshot", "SCREENSHOT", "Screenshot", KeyColor.NORMAL, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Desktop",    "OPEN_FOLDER","Desktop",    KeyColor.NORMAL, Modifier.weight(1f), vm, onAction)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysCmdBtn("Vol Up",  "VOLUME_UP",  "Vol Up",   KeyColor.MEDIA, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Mute",    "MUTE",       "Mute",     KeyColor.MEDIA, Modifier.weight(1f), vm, onAction)
            SysCmdBtn("Vol Down","VOLUME_DOWN","Vol Down", KeyColor.MEDIA, Modifier.weight(1f), vm, onAction)
        }
    }
}

@Composable private fun FKeysPage(vm: PcControlViewModel, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KbSectionLabel("Function Keys")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..4).forEach  { i -> KbBtn("F$i","F$i",KeyColor.SPECIAL,Modifier.weight(1f),vm,onAction) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (5..8).forEach  { i -> KbBtn("F$i","F$i",KeyColor.SPECIAL,Modifier.weight(1f),vm,onAction) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (9..12).forEach { i -> KbBtn("F$i","F$i",KeyColor.SPECIAL,Modifier.weight(1f),vm,onAction) } }
        KbSectionLabel("Action Keys")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KbBtn("Esc",       "ESC",       KeyColor.DANGER,  Modifier.weight(1.5f), vm, onAction)
            KbBtn("Enter",     "ENTER",     KeyColor.SPECIAL, Modifier.weight(1f),   vm, onAction)
            KbBtn("Tab",       "TAB",       KeyColor.NORMAL,  Modifier.weight(1f),   vm, onAction)
            KbBtn("Backspace", "BACKSPACE", KeyColor.NORMAL,  Modifier.weight(1f),   vm, onAction)
        }
    }
}

@Composable fun KbSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable fun KbGrid(keys: List<Triple<String,String,KeyColor>>, vm: PcControlViewModel, onAction: (String)->Unit, cols: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label,key,color) -> KbBtn(label,key,color,Modifier.weight(1f),vm,onAction) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable fun KbBtn(label: String, keyValue: String, color: KeyColor, modifier: Modifier = Modifier, vm: PcControlViewModel, onAction: (String)->Unit) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val bg = when(color) { KeyColor.NORMAL->MaterialTheme.colorScheme.surfaceVariant; KeyColor.SPECIAL->MaterialTheme.colorScheme.secondaryContainer; KeyColor.WIN->MaterialTheme.colorScheme.primaryContainer; KeyColor.ALT->MaterialTheme.colorScheme.tertiaryContainer; KeyColor.DANGER->MaterialTheme.colorScheme.errorContainer; KeyColor.MEDIA->MaterialTheme.colorScheme.tertiaryContainer }
    val fg = when(color) { KeyColor.NORMAL->MaterialTheme.colorScheme.onSurfaceVariant; KeyColor.SPECIAL->MaterialTheme.colorScheme.onSecondaryContainer; KeyColor.WIN->MaterialTheme.colorScheme.onPrimaryContainer; KeyColor.ALT->MaterialTheme.colorScheme.onTertiaryContainer; KeyColor.DANGER->MaterialTheme.colorScheme.error; KeyColor.MEDIA->MaterialTheme.colorScheme.onTertiaryContainer }
    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { pressed=true; delay(80); pressed=false }; vm.sendKey(keyValue); onAction(label) },
        modifier = modifier.height(56.dp), shape = RoundedCornerShape(10.dp),
        color = if(pressed) fg.copy(alpha=0.2f) else bg,
        border = BorderStroke(if(pressed) 1.5.dp else 1.dp, if(pressed) fg else fg.copy(alpha=0.25f)),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = when { label.length>9->9.sp; label.length>6->10.sp; label.length>3->11.sp; else->13.sp },
                fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}

@Composable fun SysCmdBtn(label: String, cmd: String, hint: String, color: KeyColor, modifier: Modifier = Modifier, vm: PcControlViewModel, onAction: (String)->Unit) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val bg = when(color) { KeyColor.DANGER->MaterialTheme.colorScheme.errorContainer; KeyColor.MEDIA->MaterialTheme.colorScheme.tertiaryContainer; else->MaterialTheme.colorScheme.surfaceVariant }
    val fg = when(color) { KeyColor.DANGER->MaterialTheme.colorScheme.error; KeyColor.MEDIA->MaterialTheme.colorScheme.onTertiaryContainer; else->MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { pressed=true; delay(80); pressed=false }; vm.executeQuickStep(PcStep("SYSTEM_CMD",cmd)); onAction(hint) },
        modifier = modifier.height(56.dp), shape = RoundedCornerShape(10.dp),
        color = if(pressed) fg.copy(alpha=0.2f) else bg,
        border = BorderStroke(if(pressed) 1.5.dp else 1.dp, if(pressed) fg else fg.copy(alpha=0.25f)),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = if(label.length>8) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}