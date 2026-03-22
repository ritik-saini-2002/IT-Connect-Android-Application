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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PC_COMMON_KEYS
import com.example.ritik_2.windowscontrol.data.PC_SYSTEM_COMMANDS
import com.example.ritik_2.windowscontrol.data.PcDrive
import com.example.ritik_2.windowscontrol.data.PcFileFilter
import com.example.ritik_2.windowscontrol.data.PcFileItem
import com.example.ritik_2.windowscontrol.data.PcInstalledApp
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.data.PcStepType
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PcControlPlanEditorUI — Smart plan builder with browse
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {

    val plan by viewModel.editingPlan.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val drives by viewModel.drives.collectAsState()
    val dirItems by viewModel.dirItems.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()

    var showAddStep by remember { mutableStateOf(false) }

    if (plan == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancelEdit() }) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                title = {
                    Text(
                        plan!!.planName.ifEmpty { "New Plan" },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.savePlan() },
                        enabled = plan!!.planName.isNotEmpty() && plan!!.steps.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.loadInstalledApps()
                viewModel.loadDrives()
                showAddStep = true
            }) {
                Icon(Icons.Default.Add, "Add Step")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan name
            OutlinedTextField(
                value = plan!!.planName,
                onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                label = { Text("Plan Name") },
                placeholder = { Text("e.g. Movie Night, Work Setup...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Icon picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Icon:", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("⚡","🎬","📊","💼","🎮","🔒","📁","🎵","🌐","📸","🖥️","⚙️")) { emoji ->
                        Surface(
                            onClick = { viewModel.updateEditingPlan(plan!!.copy(icon = emoji)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (plan!!.icon == emoji)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (plan!!.icon == emoji) BorderStroke(
                                2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            // Steps
            Text(
                "STEPS (${plan!!.steps.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            if (plan!!.steps.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕", fontSize = 32.sp)
                            Text("Tap + to add steps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                plan!!.steps.forEachIndexed { index, step ->
                    PcStepCard(
                        index = index, step = step,
                        onRemove = { viewModel.removeStep(index) }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // Add Step Bottom Sheet
    if (showAddStep) {
        PcAddStepSheet(
            installedApps = installedApps,
            drives = drives,
            dirItems = dirItems,
            currentPath = currentPath,
            onBrowseDir = { path, filter -> viewModel.browseDir(path, filter) },
            onNavigateUp = { viewModel.navigateUp() },
            onAdd = { step -> viewModel.addStep(step); showAddStep = false },
            onDismiss = { showAddStep = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun PcStepCard(index: Int, step: PcStep, onRemove: () -> Unit) {
    val type = PcStepType.values().find { it.name == step.type }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Text(type?.icon ?: "•", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(type?.display ?: step.type,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                val detail = buildPcStepDetail(step)
                if (detail.isNotEmpty()) {
                    Text(detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun buildPcStepDetail(step: PcStep): String = when (step.type) {
    "LAUNCH_APP"  -> step.value + if (step.args.isNotEmpty()) " ← ${step.args[0]}" else ""
    "KILL_APP"    -> step.value
    "KEY_PRESS"   -> step.value
    "TYPE_TEXT"   -> "\"${step.value}\""
    "MOUSE_CLICK" -> "(${step.x}, ${step.y})"
    "FILE_OP"     -> "${step.action}: ${step.from}"
    "SYSTEM_CMD"  -> step.value + if (step.args.isNotEmpty()) " → ${step.args[0]}" else ""
    "WAIT"        -> "${step.ms}ms"
    "RUN_SCRIPT"  -> step.value
    else          -> step.value
}

// ─────────────────────────────────────────────────────────────
//  ADD STEP BOTTOM SHEET — Smart browser-based step creator
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAddStepSheet(
    installedApps: List<PcInstalledApp>,
    drives: List<PcDrive>,
    dirItems: List<PcFileItem>,
    currentPath: String,
    onBrowseDir: (String, PcFileFilter) -> Unit,
    onNavigateUp: () -> Unit,
    onAdd: (PcStep) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(PcStepType.LAUNCH_APP) }

    // Step fields
    var pickedApp by remember { mutableStateOf<PcInstalledApp?>(null) }
    var pickedFilePath by remember { mutableStateOf("") }
    var keyValue by remember { mutableStateOf("F11") }
    var textValue by remember { mutableStateOf("") }
    var waitMs by remember { mutableFloatStateOf(2000f) }
    var sysCmd by remember { mutableStateOf("LOCK") }
    var sysCmdArg by remember { mutableStateOf("") }
    var scriptPath by remember { mutableStateOf("") }

    // Browse mode
    var showAppPicker by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var filePickerFilter by remember { mutableStateOf(PcFileFilter.ALL) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add Step", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            // Step type selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PcStepType.values()) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type; pickedApp = null; pickedFilePath = "" },
                        label = { Text("${type.icon} ${type.display}",
                            style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider()

            // ── Smart fields per step type ──
            when (selectedType) {

                PcStepType.LAUNCH_APP -> {
                    // App picker
                    Text("Select Application", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (pickedApp != null) {
                        PcPickedItem(icon = pickedApp!!.icon, label = pickedApp!!.name,
                            detail = pickedApp!!.exePath,
                            onClear = { pickedApp = null })
                    } else {
                        Button(onClick = { showAppPicker = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Apps, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Installed Apps on PC")
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Optional file to open with the app
                    Text("Open File With This App (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (pickedFilePath.isNotEmpty()) {
                        PcPickedItem(icon = "📄", label = pickedFilePath.substringAfterLast("/").substringAfterLast("\\"),
                            detail = pickedFilePath,
                            onClear = { pickedFilePath = "" })
                    } else {
                        OutlinedButton(onClick = {
                            filePickerFilter = PcFileFilter.MEDIA
                            showFilePicker = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Browse PC Files (optional)")
                        }
                    }
                }

                PcStepType.KEY_PRESS -> {
                    Text("Select Key", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PC_COMMON_KEYS) { key ->
                            FilterChip(
                                selected = keyValue == key,
                                onClick = { keyValue = key },
                                label = { Text(key, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                PcStepType.TYPE_TEXT -> {
                    OutlinedTextField(
                        value = textValue, onValueChange = { textValue = it },
                        label = { Text("Text to Type on PC") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                PcStepType.WAIT -> {
                    Text("Wait Duration: ${waitMs.toInt()}ms  (${waitMs.toInt()/1000.0}s)",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(value = waitMs, onValueChange = { waitMs = it },
                        valueRange = 500f..15000f, steps = 28,
                        modifier = Modifier.fillMaxWidth())
                }

                PcStepType.SYSTEM_CMD -> {
                    Text("System Command", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PC_SYSTEM_COMMANDS) { cmd ->
                            FilterChip(selected = sysCmd == cmd,
                                onClick = { sysCmd = cmd },
                                label = { Text(cmd, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    if (sysCmd in listOf("VOLUME_SET","OPEN_URL","OPEN_FOLDER","SCREENSHOT")) {
                        OutlinedTextField(
                            value = sysCmdArg, onValueChange = { sysCmdArg = it },
                            label = { Text(when(sysCmd) {
                                "VOLUME_SET" -> "Volume (0-100)"
                                "OPEN_URL"   -> "URL"
                                "OPEN_FOLDER"-> "Folder Path"
                                "SCREENSHOT" -> "Save Path"
                                else -> "Value"
                            })},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }

                PcStepType.RUN_SCRIPT -> {
                    Text("Select Script", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (scriptPath.isNotEmpty()) {
                        PcPickedItem(icon = "📜",
                            label = scriptPath.substringAfterLast("\\").substringAfterLast("/"),
                            detail = scriptPath, onClear = { scriptPath = "" })
                    } else {
                        Button(onClick = {
                            filePickerFilter = PcFileFilter.SCRIPTS
                            showFilePicker = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Code, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Scripts on PC (.py .bat .ps1)")
                        }
                    }
                }

                else -> {
                    Text("Step type '${selectedType.display}' — fill fields manually",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Add Button ──
            Button(
                onClick = {
                    val step = when (selectedType) {
                        PcStepType.LAUNCH_APP -> PcStep(
                            type = "LAUNCH_APP",
                            value = pickedApp?.exePath ?: "",
                            args = if (pickedFilePath.isNotEmpty()) listOf(pickedFilePath) else emptyList()
                        )
                        PcStepType.KEY_PRESS  -> PcStep("KEY_PRESS", keyValue)
                        PcStepType.TYPE_TEXT  -> PcStep("TYPE_TEXT", textValue)
                        PcStepType.WAIT       -> PcStep("WAIT", ms = waitMs.toInt())
                        PcStepType.SYSTEM_CMD -> PcStep("SYSTEM_CMD", sysCmd,
                            args = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList())
                        PcStepType.RUN_SCRIPT -> PcStep("RUN_SCRIPT", scriptPath)
                        else -> PcStep(selectedType.name)
                    }
                    onAdd(step)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = when (selectedType) {
                    PcStepType.LAUNCH_APP -> pickedApp != null
                    PcStepType.TYPE_TEXT  -> textValue.isNotEmpty()
                    PcStepType.RUN_SCRIPT -> scriptPath.isNotEmpty()
                    else -> true
                }
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add This Step", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── App Picker Dialog ──
    if (showAppPicker) {
        PcAppPickerDialog(
            apps = installedApps,
            onPick = { app -> pickedApp = app; showAppPicker = false },
            onDismiss = { showAppPicker = false }
        )
    }

    // ── File Picker Dialog ──
    if (showFilePicker) {
        PcFilePickerDialog(
            drives = drives,
            dirItems = dirItems,
            currentPath = currentPath,
            filter = filePickerFilter,
            onBrowseDir = { path -> onBrowseDir(path, filePickerFilter) },
            onNavigateUp = onNavigateUp,
            onPick = { path ->
                if (selectedType == PcStepType.RUN_SCRIPT) scriptPath = path
                else pickedFilePath = path
                showFilePicker = false
            },
            onDismiss = { showFilePicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  PICKED ITEM DISPLAY
// ─────────────────────────────────────────────────────────────

@Composable
fun PcPickedItem(icon: String, label: String, detail: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp))
            }
        }
    }
}