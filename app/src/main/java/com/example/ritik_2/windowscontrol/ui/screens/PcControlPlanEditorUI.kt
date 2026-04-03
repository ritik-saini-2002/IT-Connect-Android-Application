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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PLAN EDITOR
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {

    val plan          by viewModel.editingPlan.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val drives        by viewModel.drives.collectAsStateWithLifecycle()
    val dirItems      by viewModel.dirItems.collectAsStateWithLifecycle()
    val currentPath   by viewModel.currentPath.collectAsStateWithLifecycle()
    val isLoading     by viewModel.browseLoading.collectAsStateWithLifecycle()

    var showAddStep      by remember { mutableStateOf(false) }
    var showFilePicker   by remember { mutableStateOf(false) }
    var filePickerFilter by remember { mutableStateOf(PcFileFilter.ALL) }
    var pendingFileCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    if (plan == null) return
    val canSave = plan!!.planName.isNotBlank() && plan!!.steps.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancelEdit() }) {
                        Icon(Icons.Default.Close, "Discard")
                    }
                },
                title = {
                    Text(plan!!.planName.ifBlank { "New Plan" }, fontWeight = FontWeight.Bold)
                },
                actions = {
                    Button(
                        onClick  = { viewModel.savePlan() },
                        enabled  = canSave,
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // ── Plan Name ────────────────────────────────
            OutlinedTextField(
                value         = plan!!.planName,
                onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                label         = { Text("Plan Name *") },
                placeholder   = { Text("e.g. Movie Night, Work Setup…") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                isError       = plan!!.planName.isBlank(),
                shape         = RoundedCornerShape(14.dp)
            )

            HorizontalDivider()

            // ── Steps ────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("STEPS (${plan!!.steps.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
                if (plan!!.steps.isEmpty()) {
                    Text("Add at least 1 step to save",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            if (plan!!.steps.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("➕", fontSize = 32.sp)
                            Text("Tap + to add steps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                plan!!.steps.forEachIndexed { idx, step ->
                    PcStepCard(
                        index      = idx,
                        step       = step,
                        onMoveUp   = if (idx > 0) { { viewModel.reorderSteps(idx, idx - 1) } } else null,
                        onMoveDown = if (idx < plan!!.steps.lastIndex) { { viewModel.reorderSteps(idx, idx + 1) } } else null,
                        onRemove   = { viewModel.removeStep(idx) }
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // ── Add Step Sheet ────────────────────────────────────
    if (showAddStep) {
        PcAddStepSheet(
            installedApps = installedApps,
            isLoadingApps = isLoading,
            onAdd         = { step -> viewModel.addStep(step); showAddStep = false },
            onDismiss     = { showAddStep = false },
            onPickFile    = { filter, cb ->
                filePickerFilter    = filter
                pendingFileCallback = cb
                showFilePicker      = true
                showAddStep         = false
            }
        )
    }

    // ── File Picker ────────────────────────────────────────
    if (showFilePicker) {
        PcFilePickerDialog(
            drives       = drives,
            dirItems     = dirItems,
            currentPath  = currentPath,
            filter       = filePickerFilter,
            onBrowseDir  = { path -> viewModel.browseDir(path, filePickerFilter) },
            onNavigateUp = { viewModel.navigateUp() },
            onPick       = { path ->
                pendingFileCallback?.invoke(path)
                pendingFileCallback = null
                showFilePicker = false
                showAddStep    = true
            },
            onDismiss    = { showFilePicker = false; showAddStep = true }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun PcStepCard(
    index     : Int,
    step      : PcStep,
    onMoveUp  : (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove  : () -> Unit
) {
    val type = PcStepType.values().find { it.name == step.type }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(8.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Text(type?.icon ?: "•", fontSize = 20.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(type?.display ?: step.type,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                val detail = buildStepDetail(step)
                if (detail.isNotEmpty()) {
                    Text(detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
                // File path preview with open-button hint
                if (step.hasFilePath()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(step.fileIcon(), fontSize = 12.sp)
                        Text(
                            step.filePathArg().substringAfterLast('/').substringAfterLast('\\').take(30),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(16.dp))
                    }
                } else Spacer(Modifier.size(28.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove",
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.error)
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(16.dp))
                    }
                } else Spacer(Modifier.size(28.dp))
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

// ─────────────────────────────────────────────────────────────
//  ADD STEP SHEET
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAddStepSheet(
    installedApps : List<PcInstalledApp>,
    isLoadingApps : Boolean,
    onAdd         : (PcStep) -> Unit,
    onDismiss     : () -> Unit,
    onPickFile    : (PcFileFilter, (String) -> Unit) -> Unit
) {
    var selectedType by remember { mutableStateOf(PcStepType.LAUNCH_APP) }
    // LAUNCH_APP
    var pickedApp    by remember { mutableStateOf<PcInstalledApp?>(null) }
    var manualPath   by remember { mutableStateOf("") }
    var pickedFile   by remember { mutableStateOf("") }
    var appSearch    by remember { mutableStateOf("") }
    // KEY_PRESS
    var keyValue     by remember { mutableStateOf("ENTER") }
    // TYPE_TEXT
    var textValue    by remember { mutableStateOf("") }
    // WAIT
    var waitMs       by remember { mutableFloatStateOf(2000f) }
    // SYSTEM_CMD
    var sysCmd       by remember { mutableStateOf("LOCK") }
    var sysCmdArg    by remember { mutableStateOf("") }
    // OPEN_FILE
    var openFilePath by remember { mutableStateOf("") }
    // RUN_SCRIPT
    var scriptPath   by remember { mutableStateOf("") }
    // KILL_APP
    var killName     by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appSearch) {
        if (appSearch.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(appSearch, ignoreCase = true) }
    }

    val canAdd = when (selectedType) {
        PcStepType.LAUNCH_APP -> pickedApp != null || manualPath.isNotBlank()
        PcStepType.TYPE_TEXT  -> textValue.isNotBlank()
        PcStepType.RUN_SCRIPT -> scriptPath.isNotBlank()
        PcStepType.OPEN_FILE  -> openFilePath.isNotBlank()
        PcStepType.KILL_APP   -> pickedApp != null || killName.isNotBlank()
        else                  -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Add Step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }

            // Step type picker
            Text("STEP TYPE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PcStepType.values()) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick  = {
                            selectedType  = type
                            pickedApp     = null
                            manualPath    = ""
                            openFilePath  = ""
                            scriptPath    = ""
                        },
                        label = {
                            Text("${type.icon} ${type.display}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }

            HorizontalDivider()

            // ── Fields per step type ──────────────────────
            when (selectedType) {

                PcStepType.LAUNCH_APP -> LaunchAppFields(
                    pickedApp    = pickedApp,
                    manualPath   = manualPath,
                    pickedFile   = pickedFile,
                    appSearch    = appSearch,
                    filteredApps = filteredApps,
                    isLoading    = isLoadingApps,
                    onPickApp    = { pickedApp = it; appSearch = "" },
                    onManualPath = { manualPath = it },
                    onPickFile   = { onPickFile(PcFileFilter.MEDIA) { path -> pickedFile = path } },
                    onClearApp   = { pickedApp = null },
                    onClearFile  = { pickedFile = "" },
                    onSearchChange = { appSearch = it }
                )

                PcStepType.KILL_APP -> KillAppFields(
                    pickedApp    = pickedApp,
                    killName     = killName,
                    runningApps  = installedApps.filter { it.isRunning },
                    onPickApp    = { pickedApp = it },
                    onClearApp   = { pickedApp = null },
                    onNameChange = { killName = it }
                )

                PcStepType.KEY_PRESS -> KeyPickerFields(
                    keyValue  = keyValue,
                    onKeyPick = { keyValue = it }
                )

                PcStepType.TYPE_TEXT -> OutlinedTextField(
                    value         = textValue,
                    onValueChange = { textValue = it },
                    label         = { Text("Text to type on PC *") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    minLines      = 2
                )

                PcStepType.OPEN_FILE -> OpenFileFields(
                    filePath    = openFilePath,
                    onPickFile  = { filter ->
                        onPickFile(filter) { path -> openFilePath = path }
                    },
                    onClearFile = { openFilePath = "" },
                    onManual    = { openFilePath = it }
                )

                PcStepType.WAIT -> WaitFields(
                    waitMs         = waitMs,
                    onValueChange  = { waitMs = it }
                )

                PcStepType.SYSTEM_CMD -> SystemCmdFields(
                    sysCmd    = sysCmd,
                    sysCmdArg = sysCmdArg,
                    onCmdPick = { sysCmd = it },
                    onArgChange = { sysCmdArg = it }
                )

                PcStepType.RUN_SCRIPT -> RunScriptFields(
                    scriptPath  = scriptPath,
                    onPickFile  = { onPickFile(PcFileFilter.SCRIPTS) { p -> scriptPath = p } },
                    onClearFile = { scriptPath = "" }
                )

                else -> OutlinedTextField(
                    value         = manualPath,
                    onValueChange = { manualPath = it },
                    label         = { Text("Value") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true
                )
            }

            // ── Add button ────────────────────────────────
            Button(
                onClick = {
                    val step = buildStep(
                        type        = selectedType,
                        pickedApp   = pickedApp,
                        manualPath  = manualPath,
                        pickedFile  = pickedFile,
                        keyValue    = keyValue,
                        textValue   = textValue,
                        waitMs      = waitMs,
                        sysCmd      = sysCmd,
                        sysCmdArg   = sysCmdArg,
                        openFilePath = openFilePath,
                        scriptPath  = scriptPath,
                        killName    = killName
                    )
                    onAdd(step)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = canAdd
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Step", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  FIELD COMPOSABLES (extracted to avoid state loss + crashes)
// ─────────────────────────────────────────────────────────────

@Composable
private fun LaunchAppFields(
    pickedApp      : PcInstalledApp?,
    manualPath     : String,
    pickedFile     : String,
    appSearch      : String,
    filteredApps   : List<PcInstalledApp>,
    isLoading      : Boolean,
    onPickApp      : (PcInstalledApp) -> Unit,
    onManualPath   : (String) -> Unit,
    onPickFile     : () -> Unit,
    onClearApp     : () -> Unit,
    onClearFile    : () -> Unit,
    onSearchChange : (String) -> Unit
) {
    Text("APP", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)

    if (pickedApp != null) {
        AppPickedCard(app = pickedApp, onClear = onClearApp)
    } else {
        OutlinedTextField(
            value         = appSearch,
            onValueChange = onSearchChange,
            placeholder   = { Text("Search apps…") },
            leadingIcon   = { Icon(Icons.Default.Search, null) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            singleLine    = true
        )
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (filteredApps.isEmpty()) {
            Text(
                if (appSearch.isEmpty()) "No apps found. Is agent running?"
                else "No apps match \"$appSearch\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredApps.forEach { app ->
                    AppListRow(app = app, onPick = { onPickApp(app) })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("OR enter path manually:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value         = manualPath,
            onValueChange = onManualPath,
            placeholder   = { Text("C:/Program Files/app.exe") },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            singleLine    = true
        )
    }

    HorizontalDivider()
    Text("OPEN FILE WITH THIS APP (optional)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    if (pickedFile.isNotEmpty()) {
        FilePickedCard(path = pickedFile, onClear = onClearFile)
    } else {
        OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Browse PC Files (optional)")
        }
    }
}

@Composable
private fun KillAppFields(
    pickedApp   : PcInstalledApp?,
    killName    : String,
    runningApps : List<PcInstalledApp>,
    onPickApp   : (PcInstalledApp) -> Unit,
    onClearApp  : () -> Unit,
    onNameChange: (String) -> Unit
) {
    Text("RUNNING APPS", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    if (pickedApp != null) {
        AppPickedCard(app = pickedApp, onClear = onClearApp,
            containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        if (runningApps.isEmpty()) {
            Text("No running apps detected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                runningApps.forEach { app ->
                    AppListRow(app = app, onPick = { onPickApp(app) })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = killName,
            onValueChange = onNameChange,
            label         = { Text("Or type process name") },
            placeholder   = { Text("e.g. vlc.exe") },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            singleLine    = true
        )
    }
}

@Composable
private fun KeyPickerFields(keyValue: String, onKeyPick: (String) -> Unit) {
    Text("SELECTED KEY", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(keyValue, modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Bold,
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(PC_COMMON_KEYS) { key ->
            FilterChip(
                selected = keyValue == key,
                onClick  = { onKeyPick(key) },
                label    = { Text(key, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun OpenFileFields(
    filePath   : String,
    onPickFile : (PcFileFilter) -> Unit,
    onClearFile: () -> Unit,
    onManual   : (String) -> Unit
) {
    Text("FILE TO OPEN", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    Text(
        "File will be opened with its default Windows application.\nPick from PC or enter path manually.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (filePath.isNotEmpty()) {
        FilePickedCard(path = filePath, onClear = onClearFile)
    } else {
        // Quick filter buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PcFileFilter.MEDIA  to "🎬 Media",
                PcFileFilter.DOCS   to "📄 Docs",
                PcFileFilter.IMAGES to "🖼 Images",
                PcFileFilter.ALL    to "📁 All"
            ).forEach { (filter, label) ->
                OutlinedButton(
                    onClick        = { onPickFile(filter) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape          = RoundedCornerShape(8.dp),
                    modifier       = Modifier.weight(1f)
                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = filePath,
            onValueChange = onManual,
            label         = { Text("Or type full path") },
            placeholder   = { Text("C:/Videos/movie.mp4") },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            singleLine    = true
        )
    }
}

@Composable
private fun WaitFields(waitMs: Float, onValueChange: (Float) -> Unit) {
    Text(
        "WAIT: ${waitMs.toInt()}ms = ${"%.1f".format(waitMs / 1000)}s",
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Slider(value = waitMs, onValueChange = onValueChange,
        valueRange = 500f..15000f, steps = 28, modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0.5s", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("15s", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SystemCmdFields(
    sysCmd     : String,
    sysCmdArg  : String,
    onCmdPick  : (String) -> Unit,
    onArgChange: (String) -> Unit
) {
    Text("COMMAND", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(PC_SYSTEM_COMMANDS) { cmd ->
            FilterChip(
                selected = sysCmd == cmd,
                onClick  = { onCmdPick(cmd) },
                label    = { Text(cmd, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
    if (sysCmd in listOf("VOLUME_SET","OPEN_URL","OPEN_FOLDER","SCREENSHOT","WIN_R")) {
        OutlinedTextField(
            value         = sysCmdArg,
            onValueChange = onArgChange,
            label = {
                Text(when (sysCmd) {
                    "VOLUME_SET"  -> "Volume (0–100)"
                    "OPEN_URL"    -> "URL (https://…)"
                    "OPEN_FOLDER" -> "Folder path"
                    "SCREENSHOT"  -> "Save path"
                    "WIN_R"       -> "Command (e.g. notepad)"
                    else          -> "Value"
                })
            },
            modifier   = Modifier.fillMaxWidth(),
            shape      = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun RunScriptFields(
    scriptPath  : String,
    onPickFile  : () -> Unit,
    onClearFile : () -> Unit
) {
    Text("SCRIPT FILE", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    if (scriptPath.isNotEmpty()) {
        FilePickedCard(path = scriptPath, onClear = onClearFile)
    } else {
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Code, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Browse Scripts (.py .bat .ps1)")
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SMALL REUSABLE CARDS
// ─────────────────────────────────────────────────────────────

@Composable
private fun AppPickedCard(
    app            : PcInstalledApp,
    onClear        : () -> Unit,
    containerColor : androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(app.icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(app.exePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AppListRow(app: PcInstalledApp, onPick: () -> Unit) {
    Surface(
        onClick  = onPick,
        shape    = RoundedCornerShape(10.dp),
        color    = if (app.isRunning)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(app.icon, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                // Always show exePath so user knows what will be stored
                Text(app.exePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (app.isRunning) {
                    Text("● Running", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(18.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilePickedCard(path: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📄", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                path.substringAfterLast("\\").substringAfterLast("/").take(36),
                modifier  = Modifier.weight(1f),
                maxLines  = 1,
                style     = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP BUILDER — single source of truth
// ─────────────────────────────────────────────────────────────

private fun buildStep(
    type        : PcStepType,
    pickedApp   : PcInstalledApp?,
    manualPath  : String,
    pickedFile  : String,
    keyValue    : String,
    textValue   : String,
    waitMs      : Float,
    sysCmd      : String,
    sysCmdArg   : String,
    openFilePath: String,
    scriptPath  : String,
    killName    : String
): PcStep = when (type) {
    PcStepType.LAUNCH_APP -> PcStep(
        type  = "LAUNCH_APP",
        value = pickedApp?.exePath ?: manualPath,
        args  = if (pickedFile.isNotEmpty()) listOf(pickedFile) else emptyList()
    )
    PcStepType.KILL_APP -> PcStep(
        type  = "KILL_APP",
        // Use exePath as kill target (process name) — this is what was crashing before
        value = pickedApp?.exePath?.substringAfterLast("\\")?.substringAfterLast("/")
            ?: killName
    )
    PcStepType.KEY_PRESS  -> PcStep("KEY_PRESS", keyValue)
    PcStepType.TYPE_TEXT  -> PcStep("TYPE_TEXT", textValue)
    PcStepType.WAIT       -> PcStep("WAIT", ms = waitMs.toInt())
    PcStepType.OPEN_FILE  -> PcStep("OPEN_FILE", openFilePath)
    PcStepType.SYSTEM_CMD -> PcStep(
        type  = "SYSTEM_CMD",
        value = sysCmd,
        args  = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList()
    )
    PcStepType.RUN_SCRIPT -> PcStep("RUN_SCRIPT", scriptPath)
    else                  -> PcStep(type.name, manualPath)
}