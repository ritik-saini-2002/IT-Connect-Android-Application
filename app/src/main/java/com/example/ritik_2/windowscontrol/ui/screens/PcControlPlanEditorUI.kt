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
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PcControlPlanEditorUI — Rewritten from scratch
//  Issues fixed:
//  - Save button disabled even with steps
//  - Step picker types all visible
//  - WAIT slider works correctly
//  - App/file picker properly passes data
//  - Plan name saves correctly
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlPlanEditorUI(viewModel: PcControlViewModel) {

    val plan          by viewModel.editingPlan.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val drives        by viewModel.drives.collectAsState()
    val dirItems      by viewModel.dirItems.collectAsState()
    val currentPath   by viewModel.currentPath.collectAsState()
    val isLoading     by viewModel.browseLoading.collectAsState()

    var showAddStep      by remember { mutableStateOf(false) }
    var showAppPicker    by remember { mutableStateOf(false) }
    var showFilePicker   by remember { mutableStateOf(false) }
    var filePickerFilter by remember { mutableStateOf(PcFileFilter.ALL) }

    // Callback when file is picked — stored temporarily
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
                    Text(
                        plan!!.planName.ifBlank { "New Plan" },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Save button — enabled when name + at least 1 step
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadInstalledApps()
                    viewModel.loadDrives()
                    showAddStep = true
                }
            ) {
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

            // ── Plan Name ────────────────────────────────────
            OutlinedTextField(
                value       = plan!!.planName,
                onValueChange = { viewModel.updateEditingPlan(plan!!.copy(planName = it)) },
                label       = { Text("Plan Name *") },
                placeholder = { Text("e.g. Movie Night, Work Setup...") },
                modifier    = Modifier.fillMaxWidth(),
                singleLine  = true,
                isError     = plan!!.planName.isBlank(),
                supportingText = if (plan!!.planName.isBlank()) {
                    { Text("Required", color = MaterialTheme.colorScheme.error) }
                } else null,
                shape = RoundedCornerShape(14.dp)
            )

            // ── Icon Picker ───────────────────────────────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Icon:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("⚡","🎬","📊","💼","🎮","🔒","📁","🎵","🌐","📸","🖥️","⚙️","🚀","🎯")) { emoji ->
                        Surface(
                            onClick = { viewModel.updateEditingPlan(plan!!.copy(icon = emoji)) },
                            shape   = RoundedCornerShape(10.dp),
                            color   = if (plan!!.icon == emoji)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            border  = if (plan!!.icon == emoji)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Steps Section ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "STEPS (${plan!!.steps.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (plan!!.steps.isEmpty()) {
                    Text(
                        "Add at least 1 step to save",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("➕", fontSize = 32.sp)
                            Text(
                                "Tap + to add steps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                plan!!.steps.forEachIndexed { index, step ->
                    PcStepCard(
                        index    = index,
                        step     = step,
                        onMoveUp = if (index > 0) {
                            { viewModel.reorderSteps(index, index - 1) }
                        } else null,
                        onMoveDown = if (index < plan!!.steps.lastIndex) {
                            { viewModel.reorderSteps(index, index + 1) }
                        } else null,
                        onRemove = { viewModel.removeStep(index) }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ── Add Step Sheet ────────────────────────────────────────
    if (showAddStep) {
        PcAddStepSheet(
            installedApps = installedApps,
            isLoadingApps = isLoading,
            onAdd    = { step ->
                viewModel.addStep(step)
                showAddStep = false
            },
            onDismiss      = { showAddStep = false },
            onBrowseApps   = { /* already loaded */ },
            onPickFile     = { filter, callback ->
                filePickerFilter    = filter
                pendingFileCallback = callback
                showFilePicker      = true
                showAddStep         = false
            }
        )
    }

    // ── App Picker Dialog ─────────────────────────────────────
    if (showAppPicker) {
        PcAppPickerDialog(
            apps      = installedApps,
            onPick    = { app ->
                // handled inline in sheet
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }

    // ── File Picker Dialog ────────────────────────────────────
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
                showFilePicker      = false
                showAddStep         = true
            },
            onDismiss = {
                showFilePicker = false
                showAddStep    = true
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP CARD with reorder
// ─────────────────────────────────────────────────────────────

@Composable
fun PcStepCard(
    index: Int,
    step: PcStep,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit
) {
    val type = PcStepType.values().find { it.name == step.type }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Step number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Text(type?.icon ?: "•", fontSize = 20.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    type?.display ?: step.type,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val detail = buildPcStepDetail(step)
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            // Reorder + delete controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move Up", Modifier.size(16.dp))
                    }
                } else {
                    Spacer(Modifier.size(28.dp))
                }
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Remove",
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.error
                    )
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move Down", Modifier.size(16.dp))
                    }
                } else {
                    Spacer(Modifier.size(28.dp))
                }
            }
        }
    }
}

fun buildPcStepDetail(step: PcStep): String = when (step.type) {
    "LAUNCH_APP"  -> step.value.substringAfterLast("\\").substringAfterLast("/") +
            if (step.args.isNotEmpty()) "\n← ${step.args[0].substringAfterLast("\\").substringAfterLast("/")}" else ""
    "KILL_APP"    -> step.value
    "KEY_PRESS"   -> step.value
    "TYPE_TEXT"   -> "\"${step.value.take(30)}${if (step.value.length > 30) "..." else ""}\""
    "MOUSE_CLICK" -> "(${step.x}, ${step.y})"
    "FILE_OP"     -> "${step.action}: ${step.from.substringAfterLast("\\")}"
    "SYSTEM_CMD"  -> step.value + if (step.args.isNotEmpty()) " → ${step.args[0]}" else ""
    "WAIT"        -> "${step.ms}ms  (${step.ms / 1000.0}s)"
    "RUN_SCRIPT"  -> step.value.substringAfterLast("\\").substringAfterLast("/")
    else          -> step.value
}

// ─────────────────────────────────────────────────────────────
//  ADD STEP SHEET — Clean rewrite
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAddStepSheet(
    installedApps: List<PcInstalledApp>,
    isLoadingApps: Boolean,
    onAdd: (PcStep) -> Unit,
    onDismiss: () -> Unit,
    onBrowseApps: () -> Unit,
    onPickFile: (PcFileFilter, (String) -> Unit) -> Unit
) {
    var selectedType  by remember { mutableStateOf(PcStepType.LAUNCH_APP) }
    var pickedApp     by remember { mutableStateOf<PcInstalledApp?>(null) }
    var pickedFile    by remember { mutableStateOf("") }
    var keyValue      by remember { mutableStateOf("F11") }
    var textValue     by remember { mutableStateOf("") }
    var waitMs        by remember { mutableFloatStateOf(2000f) }
    var sysCmd        by remember { mutableStateOf("LOCK") }
    var sysCmdArg     by remember { mutableStateOf("") }
    var scriptPath    by remember { mutableStateOf("") }
    var manualPath    by remember { mutableStateOf("") }
    var appSearch     by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appSearch) {
        if (appSearch.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(appSearch, ignoreCase = true) }
    }

    // Whether the current step config is valid to add
    val canAdd = when (selectedType) {
        PcStepType.LAUNCH_APP  -> pickedApp != null || manualPath.isNotBlank()
        PcStepType.TYPE_TEXT   -> textValue.isNotBlank()
        PcStepType.RUN_SCRIPT  -> scriptPath.isNotBlank()
        PcStepType.FILE_OP     -> true
        else                   -> true
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add Step",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            // ── Step Type Picker ──────────────────────────────
            Text(
                "STEP TYPE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PcStepType.values()) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick  = {
                            selectedType = type
                            pickedApp    = null
                            pickedFile   = ""
                            manualPath   = ""
                        },
                        label = {
                            Text(
                                "${type.icon} ${type.display}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            HorizontalDivider()

            // ── Dynamic Fields per Step Type ──────────────────
            when (selectedType) {

                PcStepType.LAUNCH_APP -> {
                    Text("SELECT APP", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)

                    if (pickedApp != null) {
                        // Show picked app
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(pickedApp!!.icon, fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pickedApp!!.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        pickedApp!!.exePath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { pickedApp = null }) {
                                    Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        // App search
                        OutlinedTextField(
                            value = appSearch,
                            onValueChange = { appSearch = it },
                            placeholder = { Text("Search apps...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (isLoadingApps) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Loading apps from PC...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (filteredApps.isEmpty()) {
                            Text(
                                if (appSearch.isEmpty()) "No apps found — is agent_v3.py running?"
                                else "No apps match \"$appSearch\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            // App list (max 6 visible, scrollable)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filteredApps.forEach { app ->
                                    Surface(
                                        onClick = { pickedApp = app; appSearch = "" },
                                        shape   = RoundedCornerShape(10.dp),
                                        color   = if (app.isRunning)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(app.icon, fontSize = 20.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    app.name,
                                                    fontWeight = FontWeight.Medium,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1
                                                )
                                                if (app.isRunning) {
                                                    Text(
                                                        "● Running",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight, null,
                                                modifier = Modifier.size(18.dp),
                                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Manual path fallback
                        Text(
                            "OR enter path manually:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = manualPath,
                            onValueChange = { manualPath = it },
                            placeholder = { Text("C:/Program Files/app.exe") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Optional file argument
                    Text(
                        "OPEN FILE WITH THIS APP (optional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (pickedFile.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📄", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    pickedFile.substringAfterLast("\\").substringAfterLast("/"),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(onClick = { pickedFile = "" }) {
                                    Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onPickFile(PcFileFilter.MEDIA) { path -> pickedFile = path }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Browse PC Files (optional)")
                        }
                    }
                }

                PcStepType.KEY_PRESS -> {
                    Text("SELECT KEY", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)

                    // Selected key display
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            keyValue,
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PC_COMMON_KEYS) { key ->
                            FilterChip(
                                selected = keyValue == key,
                                onClick  = { keyValue = key },
                                label    = {
                                    Text(key, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }

                PcStepType.TYPE_TEXT -> {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Text to Type on PC *") },
                        placeholder = { Text("Hello World...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2
                    )
                }

                PcStepType.WAIT -> {
                    Text(
                        "WAIT DURATION: ${waitMs.toInt()}ms  =  ${String.format("%.1f", waitMs/1000)}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value          = waitMs,
                        onValueChange  = { waitMs = it },
                        valueRange     = 500f..15000f,
                        steps          = 28,
                        modifier       = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.5s", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("15s", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                PcStepType.SYSTEM_CMD -> {
                    Text("SYSTEM COMMAND", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PC_SYSTEM_COMMANDS) { cmd ->
                            FilterChip(
                                selected = sysCmd == cmd,
                                onClick  = { sysCmd = cmd },
                                label    = {
                                    Text(cmd, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                    if (sysCmd in listOf("VOLUME_SET","OPEN_URL","OPEN_FOLDER","SCREENSHOT","WIN_R")) {
                        OutlinedTextField(
                            value = sysCmdArg,
                            onValueChange = { sysCmdArg = it },
                            label = {
                                Text(when (sysCmd) {
                                    "VOLUME_SET" -> "Volume level (0–100)"
                                    "OPEN_URL"   -> "URL (https://...)"
                                    "OPEN_FOLDER" -> "Folder path"
                                    "SCREENSHOT" -> "Save path"
                                    "WIN_R"      -> "Command to run (e.g. notepad)"
                                    else         -> "Value"
                                })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }

                PcStepType.RUN_SCRIPT -> {
                    Text("SELECT SCRIPT", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (scriptPath.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📜", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    scriptPath.substringAfterLast("\\").substringAfterLast("/"),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(onClick = { scriptPath = "" }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                onPickFile(PcFileFilter.SCRIPTS) { path -> scriptPath = path }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Code, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Scripts (.py .bat .ps1)")
                        }
                    }
                }

                PcStepType.KILL_APP -> {
                    if (pickedApp != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(pickedApp!!.icon, fontSize = 22.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(pickedApp!!.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pickedApp = null }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            installedApps.filter { it.isRunning }.forEach { app ->
                                Surface(
                                    onClick = { pickedApp = app },
                                    shape   = RoundedCornerShape(10.dp),
                                    color   = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(app.icon, fontSize = 18.sp)
                                        Text(app.name, modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text("● Running",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = manualPath,
                            onValueChange = { manualPath = it },
                            label = { Text("Or type process name") },
                            placeholder = { Text("e.g. vlc.exe") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }

                else -> {
                    // MOUSE_CLICK, MOUSE_MOVE, MOUSE_SCROLL, FILE_OP
                    Text(
                        "${selectedType.display} — enter values:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // ── Add Button ────────────────────────────────────
            Button(
                onClick = {
                    val step = when (selectedType) {
                        PcStepType.LAUNCH_APP -> PcStep(
                            type  = "LAUNCH_APP",
                            value = pickedApp?.exePath ?: manualPath,
                            args  = if (pickedFile.isNotEmpty()) listOf(pickedFile) else emptyList()
                        )
                        PcStepType.KILL_APP   -> PcStep(
                            type  = "KILL_APP",
                            value = pickedApp?.name ?: manualPath
                        )
                        PcStepType.KEY_PRESS  -> PcStep("KEY_PRESS", keyValue)
                        PcStepType.TYPE_TEXT  -> PcStep("TYPE_TEXT", textValue)
                        PcStepType.WAIT       -> PcStep("WAIT", ms = waitMs.toInt())
                        PcStepType.SYSTEM_CMD -> PcStep(
                            type  = "SYSTEM_CMD",
                            value = sysCmd,
                            args  = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList()
                        )
                        PcStepType.RUN_SCRIPT -> PcStep("RUN_SCRIPT", scriptPath)
                        else                  -> PcStep(
                            type  = selectedType.name,
                            value = manualPath
                        )
                    }
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