package com.example.ritik_2.windowscontrol.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.theme.Ritik_2Theme
import com.example.ritik_2.windowscontrol.data.*

class PcAddStepActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STEP_JSON = "extra_step_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Ritik_2Theme() {
                AddStepScreen(onStepCreated = { step ->
                    val json = PcStepSerializer.stepToJson(step)
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_STEP_JSON, json))
                    finish()
                }, onCancel = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStepScreen(onStepCreated: (PcStep) -> Unit, onCancel: () -> Unit) {
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val cs = MaterialTheme.colorScheme
    val chipCols = if (isLandscape) 4 else 3

    var selectedType by remember { mutableStateOf(PcStepType.KEY_PRESS) }
    var manualPath by remember { mutableStateOf("") }
    var keyValue by remember { mutableStateOf("ENTER") }
    var textValue by remember { mutableStateOf("") }
    var waitMs by remember { mutableFloatStateOf(2000f) }
    var sysCmd by remember { mutableStateOf("LOCK") }
    var sysCmdArg by remember { mutableStateOf("") }
    var killName by remember { mutableStateOf("") }
    var mouseX by remember { mutableStateOf("") }
    var mouseY by remember { mutableStateOf("") }
    var mouseButton by remember { mutableStateOf("left") }
    var mouseDouble by remember { mutableStateOf(false) }
    var scrollAmount by remember { mutableStateOf(3) }
    var scrollDir by remember { mutableStateOf("down") }
    var keyTab by remember { mutableIntStateOf(0) }

    val canAdd = when (selectedType) {
        PcStepType.LAUNCH_APP -> manualPath.isNotBlank()
        PcStepType.TYPE_TEXT -> textValue.isNotBlank()
        PcStepType.RUN_SCRIPT -> manualPath.isNotBlank()
        PcStepType.OPEN_FILE -> manualPath.isNotBlank()
        PcStepType.KILL_APP -> killName.isNotBlank()
        else -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") } },
                title = { Text("Add Step", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("STEP TYPE", style = MaterialTheme.typography.labelSmall, color = cs.primary, letterSpacing = 0.5.sp)
            PcStepType.entries.chunked(chipCols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type; manualPath = ""; killName = "" },
                            label = { Text(type.display, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(chipCols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            HorizontalDivider(color = cs.outline.copy(0.15f))

            when (selectedType) {
                PcStepType.LAUNCH_APP -> {
                    Text("APP PATH", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    OutlinedTextField(
                        value = manualPath, onValueChange = { manualPath = it },
                        label = { Text("Executable path *") },
                        placeholder = { Text("C:\\Program Files\\app.exe") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                    )
                }
                PcStepType.KILL_APP -> {
                    Text("PROCESS NAME", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    OutlinedTextField(
                        value = killName, onValueChange = { killName = it },
                        label = { Text("Process name *") }, placeholder = { Text("vlc.exe") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                    )
                }
                PcStepType.KEY_PRESS -> {
                    Surface(shape = RoundedCornerShape(10.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⌨", fontSize = 18.sp)
                            Text(keyValue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer, modifier = Modifier.weight(1f))
                            Text("selected", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(0.6f))
                        }
                    }
                    val keyCategories = listOf(
                        "Navigate" to listOf("ENTER","ESC","SPACE","TAB","BACKSPACE","DELETE","UP","DOWN","LEFT","RIGHT","HOME","END","PAGE_UP","PAGE_DOWN"),
                        "F-Keys"   to listOf("F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"),
                        "Ctrl"     to listOf("CTRL+C","CTRL+V","CTRL+Z","CTRL+S","CTRL+A","CTRL+X","CTRL+N","CTRL+W","CTRL+T","CTRL+F","CTRL+P","CTRL+SHIFT+ESC"),
                        "Win"      to listOf("WIN+D","WIN+L","WIN+R","WIN+E","WIN+TAB","WIN+I","WIN+A","WIN+S","WIN+X","WIN+PAUSE","WIN+PRINT"),
                        "Alt"      to listOf("ALT+F4","ALT+TAB","ALT+F","ALT+E","ALT+SPACE","ALT+ENTER"),
                    )
                    ScrollableTabRow(selectedTabIndex = keyTab, edgePadding = 0.dp, divider = {}) {
                        keyCategories.forEachIndexed { idx, (label, _) ->
                            Tab(selected = keyTab == idx, onClick = { keyTab = idx },
                                text = { Text(label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    val catKeys = keyCategories[keyTab].second
                    val cols = if (isLandscape) 4 else 3
                    catKeys.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k ->
                                FilterChip(selected = keyValue == k, onClick = { keyValue = k },
                                    label = { Text(k, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                PcStepType.TYPE_TEXT -> {
                    OutlinedTextField(
                        value = textValue, onValueChange = { textValue = it },
                        label = { Text("Text to type *") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), minLines = 2
                    )
                }
                PcStepType.MOUSE_CLICK -> {
                    Text("CLICK TYPE", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("left" to "Left Click", "right" to "Right Click", "middle" to "Middle").forEach { (btn, lbl) ->
                            FilterChip(selected = mouseButton == btn && !mouseDouble, onClick = { mouseButton = btn; mouseDouble = false },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                        }
                    }
                    FilterChip(selected = mouseDouble, onClick = { mouseDouble = !mouseDouble; mouseButton = "left" },
                        label = { Text("Double Click", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.fillMaxWidth())
                    Text("POSITION (leave blank = current cursor)", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(0.7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = mouseX, onValueChange = { mouseX = it }, label = { Text("X px") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                        OutlinedTextField(value = mouseY, onValueChange = { mouseY = it }, label = { Text("Y px") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                    }
                }
                PcStepType.MOUSE_MOVE -> {
                    Text("MOVE CURSOR TO", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = mouseX, onValueChange = { mouseX = it }, label = { Text("X px") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                        OutlinedTextField(value = mouseY, onValueChange = { mouseY = it }, label = { Text("Y px") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                    }
                }
                PcStepType.MOUSE_SCROLL -> {
                    Text("SCROLL DIRECTION", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("up" to "Scroll Up", "down" to "Scroll Down").forEach { (dir, lbl) ->
                            FilterChip(selected = scrollDir == dir, onClick = { scrollDir = dir },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Text("AMOUNT: $scrollAmount notches", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(value = scrollAmount.toFloat(), onValueChange = { scrollAmount = it.toInt() }, valueRange = 1f..15f, steps = 13, modifier = Modifier.fillMaxWidth())
                }
                PcStepType.WAIT -> {
                    Text("WAIT: ${waitMs.toInt()}ms = ${"%.1f".format(waitMs / 1000)}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(value = waitMs, onValueChange = { waitMs = it }, valueRange = 500f..15000f, steps = 28, modifier = Modifier.fillMaxWidth())
                }
                PcStepType.SYSTEM_CMD -> {
                    PC_SYSTEM_COMMANDS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { cmd ->
                                FilterChip(selected = sysCmd == cmd, onClick = { sysCmd = cmd },
                                    label = { Text(cmd, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (sysCmd in listOf("VOLUME_SET", "OPEN_URL", "OPEN_FOLDER", "SCREENSHOT", "WIN_R")) {
                        OutlinedTextField(
                            value = sysCmdArg, onValueChange = { sysCmdArg = it },
                            label = { Text(when (sysCmd) { "VOLUME_SET" -> "Volume"; "OPEN_URL" -> "URL"; "OPEN_FOLDER" -> "Path"; "WIN_R" -> "Cmd"; else -> "Value" }) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                        )
                    }
                }
                PcStepType.OPEN_FILE -> {
                    Text("FILE PATH", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    OutlinedTextField(
                        value = manualPath, onValueChange = { manualPath = it },
                        label = { Text("File path *") }, placeholder = { Text("C:\\Users\\file.pdf") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                    )
                }
                PcStepType.RUN_SCRIPT -> {
                    Text("SCRIPT PATH", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    OutlinedTextField(
                        value = manualPath, onValueChange = { manualPath = it },
                        label = { Text("Script path *") }, placeholder = { Text("C:\\scripts\\run.bat") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val step = when (selectedType) {
                        PcStepType.LAUNCH_APP   -> PcStep("LAUNCH_APP", manualPath)
                        PcStepType.KILL_APP     -> PcStep("KILL_APP", killName)
                        PcStepType.KEY_PRESS    -> PcStep("KEY_PRESS", keyValue)
                        PcStepType.TYPE_TEXT     -> PcStep("TYPE_TEXT", textValue)
                        PcStepType.WAIT         -> PcStep("WAIT", ms = waitMs.toInt())
                        PcStepType.OPEN_FILE    -> PcStep("OPEN_FILE", manualPath)
                        PcStepType.SYSTEM_CMD   -> PcStep("SYSTEM_CMD", sysCmd, args = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList())
                        PcStepType.RUN_SCRIPT   -> PcStep("RUN_SCRIPT", manualPath)
                        PcStepType.MOUSE_CLICK  -> PcStep("MOUSE_CLICK", x = mouseX.toIntOrNull() ?: 0, y = mouseY.toIntOrNull() ?: 0, button = mouseButton, double = mouseDouble)
                        PcStepType.MOUSE_MOVE   -> PcStep("MOUSE_MOVE", x = mouseX.toIntOrNull() ?: 0, y = mouseY.toIntOrNull() ?: 0)
                        PcStepType.MOUSE_SCROLL -> PcStep("MOUSE_SCROLL", value = scrollDir, amount = scrollAmount)
                    }
                    onStepCreated(step)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = canAdd
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Step", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
