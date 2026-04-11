package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {
    val plan by viewModel.editingPlan.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val drives by viewModel.drives.collectAsStateWithLifecycle()
    val dirItems by viewModel.dirItems.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val isLoading by viewModel.browseLoading.collectAsStateWithLifecycle()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val cs = MaterialTheme.colorScheme

    var showAddStep by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var filePickerFilter by remember { mutableStateOf(PcFileFilter.ALL) }
    var filePickerTarget by remember { mutableStateOf("open_file") }
    var pickedOpenFilePath by remember { mutableStateOf("") }
    var pickedScriptPath by remember { mutableStateOf("") }
    var pickedLaunchFile by remember { mutableStateOf("") }

    if (plan == null) return
    val canSave = plan!!.planName.isNotBlank() && plan!!.steps.isNotEmpty()

    Scaffold(
        topBar = {
            if (isLandscape) {
                Surface(color = cs.surfaceVariant, tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.cancelEdit() }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Discard", Modifier.size(18.dp)) }
                        OutlinedTextField(value = plan!!.planName, onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                            placeholder = { Text("Plan Name…", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f).height(44.dp), singleLine = true, shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${plan!!.steps.size} steps", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Button(onClick = { viewModel.savePlan() }, enabled = canSave, shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Check, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { viewModel.cancelEdit() }) { Icon(Icons.Default.Close, "Discard") } },
                    title = { Text(plan!!.planName.ifBlank { "New Plan" }, fontWeight = FontWeight.Bold) },
                    actions = { Button(onClick = { viewModel.savePlan() }, enabled = canSave, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Save") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.loadInstalledApps(); viewModel.loadDrives(); showAddStep = true },
                containerColor = cs.primary, shape = RoundedCornerShape(16.dp),
                modifier = if (isLandscape) Modifier.size(48.dp) else Modifier) {
                Icon(Icons.Default.Add, "Add Step", modifier = if (isLandscape) Modifier.size(20.dp) else Modifier)
            }
        }
    ) { padding ->
        if (isLandscape) {
            if (plan!!.steps.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("➕", fontSize = 32.sp); Spacer(Modifier.height(8.dp)); Text("Tap + to add steps", color = cs.onSurfaceVariant) }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)) {
                    itemsIndexed(plan!!.steps) { idx, step ->
                        PcStepCard(idx, step, onMoveUp = if (idx > 0) {{ viewModel.reorderSteps(idx, idx-1) }} else null,
                            onMoveDown = if (idx < plan!!.steps.lastIndex) {{ viewModel.reorderSteps(idx, idx+1) }} else null,
                            onRemove = { viewModel.removeStep(idx) }, compact = true)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = plan!!.planName, onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                    label = { Text("Plan Name *") }, placeholder = { Text("e.g. Movie Night…") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = plan!!.planName.isBlank(), shape = RoundedCornerShape(10.dp))
                HorizontalDivider(color = cs.outline.copy(0.15f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("STEPS (${plan!!.steps.size})", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    if (plan!!.steps.isEmpty()) Text("Add at least 1 step", style = MaterialTheme.typography.labelSmall, color = cs.error)
                }
                if (plan!!.steps.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("➕", fontSize = 32.sp); Spacer(Modifier.height(8.dp)); Text("Tap + to add steps", color = cs.onSurfaceVariant) } }
                    }
                } else {
                    plan!!.steps.forEachIndexed { idx, step -> PcStepCard(idx, step, onMoveUp = if (idx > 0) {{ viewModel.reorderSteps(idx, idx-1) }} else null, onMoveDown = if (idx < plan!!.steps.lastIndex) {{ viewModel.reorderSteps(idx, idx+1) }} else null, onRemove = { viewModel.removeStep(idx) }) }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showAddStep) PcAddStepSheet(key = "addStep", installedApps = installedApps, isLoadingApps = isLoading, pickedOpenFile = pickedOpenFilePath, pickedScript = pickedScriptPath, pickedLaunchFile = pickedLaunchFile,
        onAdd = { step -> viewModel.addStep(step); pickedOpenFilePath = ""; pickedScriptPath = ""; pickedLaunchFile = ""; showAddStep = false },
        onDismiss = { pickedOpenFilePath = ""; pickedScriptPath = ""; pickedLaunchFile = ""; showAddStep = false },
        onPickFile = { filter, target -> filePickerFilter = filter; filePickerTarget = target; showFilePicker = true; showAddStep = false })

    if (showFilePicker) PcFilePickerDialog(drives = drives, dirItems = dirItems, currentPath = currentPath, filter = filePickerFilter,
        onBrowseDir = { path -> viewModel.browseDir(path, filePickerFilter) }, onNavigateUp = { viewModel.navigateUp() },
        onPick = { path -> when (filePickerTarget) { "open_file" -> pickedOpenFilePath = path; "run_script" -> pickedScriptPath = path; "launch_file" -> pickedLaunchFile = path }; showFilePicker = false; showAddStep = true },
        onDismiss = { showFilePicker = false; showAddStep = true })
}

@Composable
fun PcStepCard(index: Int, step: PcStep, onMoveUp: (()->Unit)?, onMoveDown: (()->Unit)?, onRemove: ()->Unit, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme; val type = PcStepType.entries.find { it.name == step.type }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = cs.primaryContainer, modifier = Modifier.size(if (compact) 28.dp else 36.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("${index+1}", fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 13.sp) }
            }
            Text(type?.icon ?: "•", fontSize = if (compact) 16.sp else 20.sp)
            Column(Modifier.weight(1f)) {
                Text(type?.display ?: step.type, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val detail = buildStepDetail(step)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onMoveUp != null) IconButton(onMoveUp, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(16.dp)) } else Spacer(Modifier.size(28.dp))
                    IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp), tint = cs.error) }
                    if (onMoveDown != null) IconButton(onMoveDown, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(16.dp)) } else Spacer(Modifier.size(28.dp))
                }
            } else { IconButton(onRemove, Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(12.dp), tint = cs.error) } }
        }
    }
}

fun buildStepDetail(step: PcStep): String = when (step.type) {
    "LAUNCH_APP" -> step.value.substringAfterLast("\\").substringAfterLast("/"); "KILL_APP" -> step.value; "KEY_PRESS" -> step.value
    "TYPE_TEXT" -> "\"${step.value.take(28)}${if (step.value.length > 28) "…" else ""}\""; "OPEN_FILE" -> step.value.substringAfterLast("\\").substringAfterLast("/").take(36)
    "MOUSE_CLICK" -> "(${step.x}, ${step.y})"; "FILE_OP" -> "${step.action}: ${step.from.substringAfterLast("\\")}"
    "SYSTEM_CMD" -> step.value + if (step.args.isNotEmpty()) " → ${step.args[0]}" else ""; "WAIT" -> "${step.ms}ms (${step.ms / 1000.0}s)"
    "RUN_SCRIPT" -> step.value.substringAfterLast("\\").substringAfterLast("/"); else -> step.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAddStepSheet(key: String = "sheet", installedApps: List<PcInstalledApp>, isLoadingApps: Boolean,
                   pickedOpenFile: String, pickedScript: String, pickedLaunchFile: String,
                   onAdd: (PcStep) -> Unit, onDismiss: () -> Unit, onPickFile: (PcFileFilter, String) -> Unit) {
    val cfg = LocalConfiguration.current; val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp; val cs = MaterialTheme.colorScheme
    var selectedType by remember(key) { mutableStateOf(PcStepType.LAUNCH_APP) }; var pickedApp by remember(key) { mutableStateOf<PcInstalledApp?>(null) }
    var manualPath by remember(key) { mutableStateOf("") }; var appSearch by remember(key) { mutableStateOf("") }
    var keyValue by remember(key) { mutableStateOf("ENTER") }; var textValue by remember(key) { mutableStateOf("") }
    var waitMs by remember(key) { mutableFloatStateOf(2000f) }; var sysCmd by remember(key) { mutableStateOf("LOCK") }
    var sysCmdArg by remember(key) { mutableStateOf("") }; var killName by remember(key) { mutableStateOf("") }
    val filteredApps = remember(installedApps, appSearch) { if (appSearch.isEmpty()) installedApps else installedApps.filter { it.name.contains(appSearch, ignoreCase = true) } }
    val canAdd = when (selectedType) { PcStepType.LAUNCH_APP -> pickedApp != null || manualPath.isNotBlank(); PcStepType.TYPE_TEXT -> textValue.isNotBlank(); PcStepType.RUN_SCRIPT -> pickedScript.isNotBlank(); PcStepType.OPEN_FILE -> pickedOpenFile.isNotBlank(); PcStepType.KILL_APP -> pickedApp != null || killName.isNotBlank(); else -> true }
    val chipCols = if (isLandscape) 4 else 3

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(if (isLandscape) 0.95f else 0.92f).verticalScroll(rememberScrollState())
            .padding(horizontal = if (isLandscape) 24.dp else 20.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add Step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            Text("STEP TYPE", style = MaterialTheme.typography.labelSmall, color = cs.primary, letterSpacing = 0.5.sp)
            PcStepType.entries.chunked(chipCols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { type -> FilterChip(selected = selectedType == type, onClick = { selectedType = type; pickedApp = null; manualPath = ""; appSearch = "" }, label = { Text("${type.icon} ${type.display}", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f)) }
                    repeat(chipCols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            HorizontalDivider(color = cs.outline.copy(0.15f))

            // Step type content
            when (selectedType) {
                PcStepType.LAUNCH_APP -> {
                    Text("APP", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    if (pickedApp != null) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(pickedApp!!.icon, fontSize = 22.sp); Column(Modifier.weight(1f)) { Text(pickedApp!!.name, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(pickedApp!!.exePath, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1) }
                                IconButton(onClick = { pickedApp = null }) { Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp)) }
                            }
                        }
                    } else {
                        OutlinedTextField(value = appSearch, onValueChange = { appSearch = it }, placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        if (isLoadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else if (filteredApps.isEmpty()) Text(if (appSearch.isEmpty()) "No apps." else "No match.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filteredApps.take(15).forEach { app -> Surface(onClick = { pickedApp = app; appSearch = "" }, shape = RoundedCornerShape(10.dp), color = if (app.isRunning) cs.tertiaryContainer else cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(app.icon, fontSize = 18.sp); Text(app.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall, maxLines = 1); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp)) } } }
                        }
                        OutlinedTextField(value = manualPath, onValueChange = { manualPath = it }, placeholder = { Text("C:/app.exe") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                    }
                    if (pickedLaunchFile.isNotEmpty()) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) { Row(Modifier.padding(10.dp)) { Text("📄 "); Text(pickedLaunchFile.substringAfterLast("\\").take(30), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1) } } }
                    else OutlinedButton(onClick = { onPickFile(PcFileFilter.MEDIA, "launch_file") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Folder, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Open File With App", style = MaterialTheme.typography.labelSmall) }
                }
                PcStepType.KILL_APP -> {
                    Text("RUNNING APPS", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    if (pickedApp != null) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(pickedApp!!.icon, fontSize = 22.sp); Text(pickedApp!!.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1); IconButton(onClick = { pickedApp = null }) { Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp)) } } } }
                    else { val running = installedApps.filter { it.isRunning }; if (running.isEmpty()) Text("No running apps.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) { running.take(10).forEach { app -> Surface(onClick = { pickedApp = app }, shape = RoundedCornerShape(10.dp), color = cs.tertiaryContainer, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(app.icon, fontSize = 18.sp); Text(app.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp)) } } } }
                        OutlinedTextField(value = killName, onValueChange = { killName = it }, label = { Text("Process name") }, placeholder = { Text("vlc.exe") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true) }
                }
                PcStepType.KEY_PRESS -> {
                    Surface(shape = RoundedCornerShape(10.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) { Text(keyValue, Modifier.padding(10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer) }
                    PC_COMMON_KEYS.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { k -> FilterChip(selected = keyValue == k, onClick = { keyValue = k }, label = { Text(k, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f)) }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } } }
                }
                PcStepType.TYPE_TEXT -> OutlinedTextField(value = textValue, onValueChange = { textValue = it }, label = { Text("Text to type *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), minLines = 2)
                PcStepType.OPEN_FILE -> {
                    if (pickedOpenFile.isNotEmpty()) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) { Row(Modifier.padding(10.dp)) { Text("📄 "); Text(pickedOpenFile.substringAfterLast("\\").take(30), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1) } } }
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(PcFileFilter.MEDIA to "Media", PcFileFilter.DOCS to "Docs", PcFileFilter.IMAGES to "Images", PcFileFilter.ALL to "All").forEach { (f, l) -> OutlinedButton(onClick = { onPickFile(f, "open_file") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text(l, style = MaterialTheme.typography.labelSmall) } } }
                }
                PcStepType.WAIT -> { Text("WAIT: ${waitMs.toInt()}ms = ${"%.1f".format(waitMs/1000)}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Slider(value = waitMs, onValueChange = { waitMs = it }, valueRange = 500f..15000f, steps = 28, modifier = Modifier.fillMaxWidth()) }
                PcStepType.SYSTEM_CMD -> {
                    PC_SYSTEM_COMMANDS.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { cmd -> FilterChip(selected = sysCmd == cmd, onClick = { sysCmd = cmd }, label = { Text(cmd, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f)) }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
                    if (sysCmd in listOf("VOLUME_SET","OPEN_URL","OPEN_FOLDER","SCREENSHOT","WIN_R")) OutlinedTextField(value = sysCmdArg, onValueChange = { sysCmdArg = it }, label = { Text(when(sysCmd) { "VOLUME_SET"->"Volume"; "OPEN_URL"->"URL"; "OPEN_FOLDER"->"Path"; "WIN_R"->"Cmd"; else->"Value" }) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                }
                PcStepType.RUN_SCRIPT -> {
                    if (pickedScript.isNotEmpty()) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) { Row(Modifier.padding(10.dp)) { Text("⚙️ "); Text(pickedScript.substringAfterLast("\\").take(30), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1) } } }
                    else Button(onClick = { onPickFile(PcFileFilter.SCRIPTS, "run_script") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Code, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Browse Scripts") }
                }
                else -> OutlinedTextField(value = manualPath, onValueChange = { manualPath = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
            }

            Button(onClick = { onAdd(buildStep(selectedType, pickedApp, manualPath, pickedLaunchFile, keyValue, textValue, waitMs, sysCmd, sysCmdArg, pickedOpenFile, pickedScript, killName)) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), enabled = canAdd) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Step", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AppListRow(app: PcInstalledApp, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onPick, shape = RoundedCornerShape(10.dp), color = if (app.isRunning) cs.tertiaryContainer else cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app.icon, fontSize = 18.sp); Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, maxLines = 1); Text(app.exePath, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); if (app.isRunning) Text("Running", style = MaterialTheme.typography.labelSmall, color = cs.tertiary) }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        }
    }
}

private fun buildStep(type: PcStepType, pickedApp: PcInstalledApp?, manualPath: String, pickedFile: String, keyValue: String, textValue: String, waitMs: Float, sysCmd: String, sysCmdArg: String, openFilePath: String, scriptPath: String, killName: String): PcStep = when (type) {
    PcStepType.LAUNCH_APP -> PcStep("LAUNCH_APP", pickedApp?.exePath ?: manualPath, args = if (pickedFile.isNotEmpty()) listOf(pickedFile) else emptyList())
    PcStepType.KILL_APP -> PcStep("KILL_APP", pickedApp?.exePath?.substringAfterLast("\\")?.substringAfterLast("/") ?: killName)
    PcStepType.KEY_PRESS -> PcStep("KEY_PRESS", keyValue); PcStepType.TYPE_TEXT -> PcStep("TYPE_TEXT", textValue)
    PcStepType.WAIT -> PcStep("WAIT", ms = waitMs.toInt()); PcStepType.OPEN_FILE -> PcStep("OPEN_FILE", openFilePath)
    PcStepType.SYSTEM_CMD -> PcStep("SYSTEM_CMD", sysCmd, args = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList())
    PcStepType.RUN_SCRIPT -> PcStep("RUN_SCRIPT", scriptPath); else -> PcStep(type.name, manualPath)
}