package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {
    val plan          by viewModel.editingPlan.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val drives        by viewModel.drives.collectAsStateWithLifecycle()
    val dirItems      by viewModel.dirItems.collectAsStateWithLifecycle()
    val currentPath   by viewModel.currentPath.collectAsStateWithLifecycle()
    val isLoading     by viewModel.browseLoading.collectAsStateWithLifecycle()

    var showAddStep    by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }

    var filePickerFilter     by remember { mutableStateOf(PcFileFilter.ALL) }
    var filePickerTarget     by remember { mutableStateOf("open_file") }
    var pickedOpenFilePath   by remember { mutableStateOf("") }
    var pickedScriptPath     by remember { mutableStateOf("") }
    var pickedLaunchFile     by remember { mutableStateOf("") }

    if (plan == null) return
    val canSave = plan!!.planName.isNotBlank() && plan!!.steps.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { viewModel.cancelEdit() }) { Icon(Icons.Default.Close, "Discard") } },
                title = { Text(plan!!.planName.ifBlank { "New Plan" }, fontWeight = FontWeight.Bold) },
                actions = {
                    Button(onClick = { viewModel.savePlan() }, enabled = canSave,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.loadInstalledApps(); viewModel.loadDrives(); showAddStep = true }) {
                Icon(Icons.Default.Add, "Add Step")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = plan!!.planName,
                onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                label = { Text("Plan Name *") }, placeholder = { Text("e.g. Movie Night…") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = plan!!.planName.isBlank(), shape = RoundedCornerShape(14.dp)
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STEPS (${plan!!.steps.size})", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (plan!!.steps.isEmpty())
                    Text("Add at least 1 step to save", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
            }
            if (plan!!.steps.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕", fontSize = 32.sp)
                            Text("Tap + to add steps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                plan!!.steps.forEachIndexed { idx, step ->
                    PcStepCard(
                        index      = idx,
                        step       = step,
                        onMoveUp   = if (idx > 0) {{ viewModel.reorderSteps(idx, idx-1) }} else null,
                        onMoveDown = if (idx < plan!!.steps.lastIndex) {{ viewModel.reorderSteps(idx, idx+1) }} else null,
                        onRemove   = { viewModel.removeStep(idx) }
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showAddStep) {
        PcAddStepSheet(
            key              = "addStep",
            installedApps    = installedApps,
            isLoadingApps    = isLoading,
            pickedOpenFile   = pickedOpenFilePath,
            pickedScript     = pickedScriptPath,
            pickedLaunchFile = pickedLaunchFile,
            onAdd            = { step ->
                viewModel.addStep(step)
                pickedOpenFilePath = ""; pickedScriptPath = ""; pickedLaunchFile = ""
                showAddStep = false
            },
            onDismiss        = { pickedOpenFilePath = ""; pickedScriptPath = ""; pickedLaunchFile = ""; showAddStep = false },
            onPickFile       = { filter, target -> filePickerFilter = filter; filePickerTarget = target; showFilePicker = true; showAddStep = false }
        )
    }

    if (showFilePicker) {
        PcFilePickerDialog(
            drives       = drives,
            dirItems     = dirItems,
            currentPath  = currentPath,
            filter       = filePickerFilter,
            onBrowseDir  = { path -> viewModel.browseDir(path, filePickerFilter) },
            onNavigateUp = { viewModel.navigateUp() },
            onPick       = { path ->
                when (filePickerTarget) {
                    "open_file"   -> pickedOpenFilePath = path
                    "run_script"  -> pickedScriptPath   = path
                    "launch_file" -> pickedLaunchFile   = path
                }
                showFilePicker = false; showAddStep = true
            },
            onDismiss    = { showFilePicker = false; showAddStep = true }
        )
    }
}

@Composable
fun PcStepCard(index: Int, step: PcStep, onMoveUp: (()->Unit)?, onMoveDown: (()->Unit)?, onRemove: ()->Unit) {
    val type = PcStepType.entries.find { it.name == step.type }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("${index+1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Text(type?.icon ?: "•", fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text(type?.display ?: step.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val detail = buildStepDetail(step)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (step.hasFilePath()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                        Text(step.fileIcon(), fontSize = 12.sp)
                        Text(step.filePathArg().substringAfterLast('/').substringAfterLast('\\').take(30),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onMoveUp != null) IconButton(onMoveUp, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp,"Up",Modifier.size(16.dp)) }
                else Spacer(Modifier.size(28.dp))
                IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Default.Close,"Remove",Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                if (onMoveDown != null) IconButton(onMoveDown, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown,"Down",Modifier.size(16.dp)) }
                else Spacer(Modifier.size(28.dp))
            }
        }
    }
}

fun buildStepDetail(step: PcStep): String = when (step.type) {
    "LAUNCH_APP"  -> step.value.substringAfterLast("\\").substringAfterLast("/")
    "KILL_APP"    -> step.value
    "KEY_PRESS"   -> step.value
    "TYPE_TEXT"   -> "\"${step.value.take(28)}${if (step.value.length > 28) "…" else ""}\""
    "OPEN_FILE"   -> step.value.substringAfterLast("\\").substringAfterLast("/").take(36)
    "MOUSE_CLICK" -> "(${step.x}, ${step.y})"
    "FILE_OP"     -> "${step.action}: ${step.from.substringAfterLast("\\")}"
    "SYSTEM_CMD"  -> step.value + if (step.args.isNotEmpty()) " → ${step.args[0]}" else ""
    "WAIT"        -> "${step.ms}ms (${step.ms / 1000.0}s)"
    "RUN_SCRIPT"  -> step.value.substringAfterLast("\\").substringAfterLast("/")
    else          -> step.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAddStepSheet(
    key              : String = "sheet",
    installedApps    : List<PcInstalledApp>,
    isLoadingApps    : Boolean,
    pickedOpenFile   : String,
    pickedScript     : String,
    pickedLaunchFile : String,
    onAdd            : (PcStep) -> Unit,
    onDismiss        : () -> Unit,
    onPickFile       : (PcFileFilter, String) -> Unit
) {
    var selectedType by remember(key) { mutableStateOf(PcStepType.LAUNCH_APP) }
    var pickedApp    by remember(key) { mutableStateOf<PcInstalledApp?>(null) }
    var manualPath   by remember(key) { mutableStateOf("") }
    var appSearch    by remember(key) { mutableStateOf("") }
    var keyValue     by remember(key) { mutableStateOf("ENTER") }
    var textValue    by remember(key) { mutableStateOf("") }
    var waitMs       by remember(key) { mutableFloatStateOf(2000f) }
    var sysCmd       by remember(key) { mutableStateOf("LOCK") }
    var sysCmdArg    by remember(key) { mutableStateOf("") }
    var killName     by remember(key) { mutableStateOf("") }

    val filteredApps = remember(installedApps, appSearch) {
        if (appSearch.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(appSearch, ignoreCase = true) }
    }

    val canAdd = when (selectedType) {
        PcStepType.LAUNCH_APP -> pickedApp != null || manualPath.isNotBlank()
        PcStepType.TYPE_TEXT  -> textValue.isNotBlank()
        PcStepType.RUN_SCRIPT -> pickedScript.isNotBlank()
        PcStepType.OPEN_FILE  -> pickedOpenFile.isNotBlank()
        PcStepType.KILL_APP   -> pickedApp != null || killName.isNotBlank()
        else                  -> true
    }

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add Step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }

            Text("STEP TYPE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            PcStepType.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type; pickedApp = null; manualPath = ""; appSearch = "" },
                            label = { Text("${type.icon} ${type.display}", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            HorizontalDivider()

            when (selectedType) {
                PcStepType.LAUNCH_APP -> {
                    Text("APP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (pickedApp != null) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(pickedApp!!.icon, fontSize = 24.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(pickedApp!!.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(pickedApp!!.exePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                IconButton(onClick = { pickedApp = null }) { Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp)) }
                            }
                        }
                    } else {
                        OutlinedTextField(value = appSearch, onValueChange = { appSearch = it },
                            placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                        if (isLoadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else if (filteredApps.isEmpty()) Text(if (appSearch.isEmpty()) "No apps found." else "No match for \"$appSearch\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredApps.take(20).forEach { app ->
                                Surface(onClick = { pickedApp = app; appSearch = "" }, shape = RoundedCornerShape(10.dp),
                                    color = if (app.isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(app.icon, fontSize = 20.sp)
                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(app.exePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = manualPath, onValueChange = { manualPath = it },
                            placeholder = { Text("C:/Program Files/app.exe") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    }
                    HorizontalDivider()
                    Text("OPEN FILE (optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (pickedLaunchFile.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📄", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                                Text(pickedLaunchFile.substringAfterLast("\\").substringAfterLast("/").take(36), Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { onPickFile(PcFileFilter.MEDIA, "launch_file") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Browse PC Files")
                        }
                    }
                }
                PcStepType.KILL_APP -> {
                    Text("RUNNING APPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (pickedApp != null) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(pickedApp!!.icon, fontSize = 24.sp)
                                Column(Modifier.weight(1f)) { Text(pickedApp!!.name, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                                IconButton(onClick = { pickedApp = null }) { Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp)) }
                            }
                        }
                    } else {
                        val running = installedApps.filter { it.isRunning }
                        if (running.isEmpty()) Text("No running apps detected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            running.take(15).forEach { app ->
                                Surface(onClick = { pickedApp = app }, shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(app.icon, fontSize = 20.sp)
                                        Text(app.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = killName, onValueChange = { killName = it },
                            label = { Text("Or type process name") }, placeholder = { Text("e.g. vlc.exe") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    }
                }
                PcStepType.KEY_PRESS -> {
                    Text("SELECTED KEY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(keyValue, Modifier.padding(12.dp), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    PC_COMMON_KEYS.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { k -> FilterChip(selected = keyValue == k, onClick = { keyValue = k },
                                label = { Text(k, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f)) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                PcStepType.TYPE_TEXT ->
                    OutlinedTextField(value = textValue, onValueChange = { textValue = it },
                        label = { Text("Text to type on PC *") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), minLines = 2)
                PcStepType.OPEN_FILE -> {
                    Text("FILE TO OPEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (pickedOpenFile.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📄", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                                Text(pickedOpenFile.substringAfterLast("\\").substringAfterLast("/").take(36), Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PcFileFilter.MEDIA to "Media", PcFileFilter.DOCS to "Docs",
                                PcFileFilter.IMAGES to "Images", PcFileFilter.ALL to "All").forEach { (f, l) ->
                                OutlinedButton(onClick = { onPickFile(f, "open_file") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                    Text(l, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                PcStepType.WAIT -> {
                    Text("WAIT: ${waitMs.toInt()}ms = ${"%.1f".format(waitMs/1000)}s",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(value = waitMs, onValueChange = { waitMs = it }, valueRange = 500f..15000f, steps = 28, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.5s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("15s",  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PcStepType.SYSTEM_CMD -> {
                    Text("COMMAND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    PC_SYSTEM_COMMANDS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { cmd -> FilterChip(selected = sysCmd == cmd, onClick = { sysCmd = cmd },
                                label = { Text(cmd, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (sysCmd in listOf("VOLUME_SET","OPEN_URL","OPEN_FOLDER","SCREENSHOT","WIN_R"))
                        OutlinedTextField(value = sysCmdArg, onValueChange = { sysCmdArg = it },
                            label = { Text(when(sysCmd) { "VOLUME_SET"->"Volume (0-100)"; "OPEN_URL"->"URL"; "OPEN_FOLDER"->"Folder path"; "SCREENSHOT"->"Save path"; "WIN_R"->"Command"; else->"Value" }) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                }
                PcStepType.RUN_SCRIPT -> {
                    Text("SCRIPT FILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (pickedScript.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙️", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                                Text(pickedScript.substringAfterLast("\\").substringAfterLast("/").take(36), Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Button(onClick = { onPickFile(PcFileFilter.SCRIPTS, "run_script") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Code, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Browse Scripts (.py .bat .ps1)")
                        }
                    }
                }
                else -> OutlinedTextField(value = manualPath, onValueChange = { manualPath = it },
                    label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            }

            Button(
                onClick = {
                    onAdd(buildStep(selectedType, pickedApp, manualPath, pickedLaunchFile,
                        keyValue, textValue, waitMs, sysCmd, sysCmdArg, pickedOpenFile, pickedScript, killName))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = canAdd
            ) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
                Text("Add Step", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AppListRow(app: PcInstalledApp, onPick: () -> Unit) {
    Surface(onClick = onPick, shape = RoundedCornerShape(10.dp),
        color = if (app.isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(app.icon, fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(app.exePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (app.isRunning) Text("Running", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildStep(type: PcStepType, pickedApp: PcInstalledApp?, manualPath: String, pickedFile: String,
                      keyValue: String, textValue: String, waitMs: Float, sysCmd: String, sysCmdArg: String,
                      openFilePath: String, scriptPath: String, killName: String): PcStep = when (type) {
    PcStepType.LAUNCH_APP -> PcStep("LAUNCH_APP", pickedApp?.exePath ?: manualPath,
        args = if (pickedFile.isNotEmpty()) listOf(pickedFile) else emptyList())
    PcStepType.KILL_APP   -> PcStep("KILL_APP", pickedApp?.exePath?.substringAfterLast("\\")?.substringAfterLast("/") ?: killName)
    PcStepType.KEY_PRESS  -> PcStep("KEY_PRESS", keyValue)
    PcStepType.TYPE_TEXT  -> PcStep("TYPE_TEXT", textValue)
    PcStepType.WAIT       -> PcStep("WAIT", ms = waitMs.toInt())
    PcStepType.OPEN_FILE  -> PcStep("OPEN_FILE", openFilePath)
    PcStepType.SYSTEM_CMD -> PcStep("SYSTEM_CMD", sysCmd, args = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList())
    PcStepType.RUN_SCRIPT -> PcStep("RUN_SCRIPT", scriptPath)
    else                  -> PcStep(type.name, manualPath)
}