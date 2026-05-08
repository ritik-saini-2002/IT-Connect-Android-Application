package com.example.ritik_2.windowscontrol.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.theme.Ritik_2Theme
import com.example.ritik_2.windowscontrol.PcControlMain
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.pcfilebrowser.PcControlFileBrowserActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  ACTIVITY SHELL
// ─────────────────────────────────────────────────────────────

@AndroidEntryPoint
class PcAddStepActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    companion object {
        const val EXTRA_STEP_JSON = "extra_step_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessWindowsControlSub(Permissions.PERM_WINDOWS_CONTROL_ADD_STEP, role, perms, dba)
                },
                deniedMessage = "Add Step — access not granted")) return
        if (!PcControlMain.isInitialized) PcControlMain.init(this)
        setContent {
            Ritik_2Theme {
                AddStepScreen(
                    onStepCreated = { step ->
                        val json = PcStepSerializer.stepToJson(step)
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_STEP_JSON, json))
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStepScreen(
    onStepCreated: (PcStep) -> Unit,
    onCancel     : () -> Unit
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val cfg         = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val cs          = MaterialTheme.colorScheme
    val chipCols    = if (isLandscape) 4 else 3

    // ── Step type ─────────────────────────────────────────────
    var selectedType by remember { mutableStateOf(PcStepType.KEY_PRESS) }

    // ── App state ─────────────────────────────────────────────
    var installedApps  by remember { mutableStateOf<List<PcInstalledApp>>(emptyList()) }
    var isLoadingApps  by remember { mutableStateOf(false) }
    var pickedApp      by remember { mutableStateOf<PcInstalledApp?>(null) }
    var appSearch      by remember { mutableStateOf("") }

    // ── Path / file state ────────────────────────────────────
    var manualPath      by remember { mutableStateOf("") }
    var pickedOpenFile  by remember { mutableStateOf("") }
    var pickedScript    by remember { mutableStateOf("") }
    var pickedLaunchFile by remember { mutableStateOf("") } // file to open WITH the app
    var fileOpFrom      by remember { mutableStateOf("") }
    var fileOpTo        by remember { mutableStateOf("") }

    // ── Key ───────────────────────────────────────────────────
    var keyValue by remember { mutableStateOf("ENTER") }
    var keyTab   by remember { mutableIntStateOf(0) }

    // ── Text ─────────────────────────────────────────────────
    var textValue by remember { mutableStateOf("") }

    // ── Wait ─────────────────────────────────────────────────
    var waitMs by remember { mutableFloatStateOf(2000f) }

    // ── System command ────────────────────────────────────────
    var sysCmd    by remember { mutableStateOf("LOCK") }
    var sysCmdArg by remember { mutableStateOf("") }

    // ── Kill app ─────────────────────────────────────────────
    var killName by remember { mutableStateOf("") }

    // ── Mouse ─────────────────────────────────────────────────
    var mouseX      by remember { mutableStateOf("") }
    var mouseY      by remember { mutableStateOf("") }
    var mouseButton by remember { mutableStateOf("left") }
    var mouseDouble by remember { mutableStateOf(false) }

    // ── Scroll ────────────────────────────────────────────────
    var scrollAmount by remember { mutableStateOf(3) }
    var scrollDir    by remember { mutableStateOf("down") }

    // ── File Op ───────────────────────────────────────────────
    var fileAction by remember { mutableStateOf("COPY") }

    // ── Load apps on first render ─────────────────────────────
    LaunchedEffect(Unit) {
        isLoadingApps = true
        withContext(Dispatchers.IO) {
            try {
                val browse = PcControlMain.browseClient
                if (browse != null) {
                    val r = browse.getInstalledApps()
                    if (r.success) {
                        val apps = r.data ?: emptyList()
                        // Overlay running status from process list
                        val api = PcControlMain.apiClient
                        val runningSet = if (api != null) {
                            val pr = api.getProcesses()
                            if (pr.success) pr.data?.map { it.lowercase() }?.toSet() else null
                        } else null
                        installedApps = if (runningSet != null)
                            apps.map { app -> app.copy(isRunning = runningSet.any { it in app.exePath.lowercase() }) }
                        else apps
                    }
                }
            } catch (_: Exception) {}
        }
        isLoadingApps = false
    }

    // ── File picker launchers ─────────────────────────────────
    fun pickerIntent() = Intent(context, PcControlFileBrowserActivity::class.java)
        .putExtra(PcControlFileBrowserActivity.EXTRA_PICK_MODE, true)

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            pickedOpenFile = r.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH) ?: ""
    }
    val scriptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            pickedScript = r.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH) ?: ""
    }
    val launchFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            pickedLaunchFile = r.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH) ?: ""
    }
    val fileOpFromLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            fileOpFrom = r.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH) ?: ""
    }
    val fileOpToLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            fileOpTo = r.data?.getStringExtra(PcControlFileBrowserActivity.EXTRA_SELECTED_PATH) ?: ""
    }

    // ── Filtered app list ────────────────────────────────────
    val filteredApps = remember(installedApps, appSearch) {
        if (appSearch.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(appSearch, ignoreCase = true) }
    }

    // ── Can-add guard ─────────────────────────────────────────
    val canAdd = when (selectedType) {
        PcStepType.LAUNCH_APP  -> pickedApp != null || manualPath.isNotBlank()
        PcStepType.KILL_APP    -> pickedApp != null || killName.isNotBlank()
        PcStepType.TYPE_TEXT   -> textValue.isNotBlank()
        PcStepType.RUN_SCRIPT  -> pickedScript.isNotBlank() || manualPath.isNotBlank()
        PcStepType.OPEN_FILE   -> pickedOpenFile.isNotBlank() || manualPath.isNotBlank()
        PcStepType.FILE_OP     -> fileOpFrom.isNotBlank()
        else                   -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                },
                title = {
                    Column {
                        Text("Add Step", fontWeight = FontWeight.Bold)
                        Text(selectedType.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurface.copy(0.55f))
                    }
                },
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

            // ── Step type chips ───────────────────────────────
            Text("STEP TYPE",
                style       = MaterialTheme.typography.labelSmall,
                color       = cs.primary,
                letterSpacing = 0.5.sp)

            PcStepType.entries.chunked(chipCols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick  = {
                                selectedType  = type
                                pickedApp     = null
                                manualPath    = ""
                                appSearch     = ""
                                killName      = ""
                            },
                            label    = {
                                Text(type.display,
                                    style   = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(chipCols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            HorizontalDivider(color = cs.outline.copy(0.15f))

            // ── Step-type content ─────────────────────────────
            when (selectedType) {

                // ── LAUNCH APP ────────────────────────────────
                PcStepType.LAUNCH_APP -> {
                    Text("APP", style = MaterialTheme.typography.labelSmall, color = cs.primary)

                    if (pickedApp != null) {
                        PickedAppCard(pickedApp!!, tint = cs.primaryContainer) {
                            pickedApp = null; appSearch = ""
                        }
                    } else {
                        AppSearchField(appSearch) { appSearch = it }
                        AppsList(
                            apps      = filteredApps.take(12),
                            loading   = isLoadingApps,
                            emptyHint = if (appSearch.isEmpty()) "No apps loaded" else "No match",
                            onPick    = { pickedApp = it; appSearch = "" }
                        )
                        OutlinedTextField(
                            value         = manualPath,
                            onValueChange = { manualPath = it },
                            label         = { Text("Or type path manually") },
                            placeholder   = { Text("C:\\Program Files\\app.exe") },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            singleLine    = true
                        )
                    }

                    // Optional: open a file WITH this app
                    HorizontalDivider(color = cs.outline.copy(0.1f))
                    Text("OPEN FILE WITH APP (optional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(0.7f))
                    if (pickedLaunchFile.isNotEmpty()) {
                        PickedFileChip(pickedLaunchFile) { pickedLaunchFile = "" }
                    } else {
                        OutlinedButton(
                            onClick  = { launchFileLauncher.launch(pickerIntent()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse file to open with app",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // ── KILL APP ──────────────────────────────────
                PcStepType.KILL_APP -> {
                    val running = installedApps.filter { it.isRunning }
                    Text("RUNNING APPS",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)

                    if (pickedApp != null) {
                        PickedAppCard(pickedApp!!, tint = cs.errorContainer) {
                            pickedApp = null
                        }
                    } else {
                        if (isLoadingApps) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else if (running.isEmpty()) {
                            Text("No running apps detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant)
                        } else {
                            AppsList(
                                apps      = running.take(10),
                                loading   = false,
                                emptyHint = "No running apps",
                                onPick    = { pickedApp = it }
                            )
                        }
                        OutlinedTextField(
                            value         = killName,
                            onValueChange = { killName = it },
                            label         = { Text("Or type process name") },
                            placeholder   = { Text("vlc.exe") },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            singleLine    = true
                        )
                    }
                }

                // ── KEY PRESS ─────────────────────────────────
                PcStepType.KEY_PRESS -> {
                    // Selected key badge
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = cs.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⌨", fontSize = 18.sp)
                            Text(keyValue,
                                fontWeight = FontWeight.Bold,
                                style      = MaterialTheme.typography.titleMedium,
                                color      = cs.onPrimaryContainer,
                                modifier   = Modifier.weight(1f))
                            Text("selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onPrimaryContainer.copy(0.6f))
                        }
                    }
                    // Tabbed key categories
                    val keyCategories = listOf(
                        "Navigate" to listOf("ENTER","ESC","SPACE","TAB","BACKSPACE","DELETE",
                            "UP","DOWN","LEFT","RIGHT","HOME","END","PAGE_UP","PAGE_DOWN"),
                        "F-Keys"   to listOf("F1","F2","F3","F4","F5","F6",
                            "F7","F8","F9","F10","F11","F12"),
                        "Ctrl"     to listOf("CTRL+C","CTRL+V","CTRL+Z","CTRL+S","CTRL+A",
                            "CTRL+X","CTRL+N","CTRL+W","CTRL+T","CTRL+F","CTRL+P","CTRL+L",
                            "CTRL+R","CTRL+SHIFT+ESC"),
                        "Win"      to listOf("WIN+D","WIN+L","WIN+R","WIN+E","WIN+TAB",
                            "WIN+I","WIN+A","WIN+S","WIN+X","WIN+PAUSE","WIN+PRINT"),
                        "Alt"      to listOf("ALT+F4","ALT+TAB","ALT+F","ALT+E",
                            "ALT+SPACE","ALT+ENTER")
                    )
                    ScrollableTabRow(
                        selectedTabIndex = keyTab,
                        edgePadding      = 0.dp,
                        divider          = {}
                    ) {
                        keyCategories.forEachIndexed { idx, (label, _) ->
                            Tab(
                                selected = keyTab == idx,
                                onClick  = { keyTab = idx },
                                text     = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    val cols = if (isLandscape) 4 else 3
                    keyCategories[keyTab].second.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k ->
                                FilterChip(
                                    selected = keyValue == k,
                                    onClick  = { keyValue = k },
                                    label    = { Text(k, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // ── TYPE TEXT ─────────────────────────────────
                PcStepType.TYPE_TEXT -> {
                    OutlinedTextField(
                        value         = textValue,
                        onValueChange = { textValue = it },
                        label         = { Text("Text to type *") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        minLines      = 2
                    )
                }

                // ── MOUSE CLICK ───────────────────────────────
                PcStepType.MOUSE_CLICK -> {
                    Text("CLICK TYPE",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("left" to "Left", "right" to "Right", "middle" to "Middle").forEach { (btn, lbl) ->
                            FilterChip(
                                selected = mouseButton == btn && !mouseDouble,
                                onClick  = { mouseButton = btn; mouseDouble = false },
                                label    = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    FilterChip(
                        selected = mouseDouble,
                        onClick  = { mouseDouble = !mouseDouble; mouseButton = "left" },
                        label    = { Text("Double Click", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("POSITION (leave blank = current cursor)",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(0.7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(mouseX, { mouseX = it }, Modifier.weight(1f),
                            label = { Text("X px") }, shape = RoundedCornerShape(10.dp), singleLine = true)
                        OutlinedTextField(mouseY, { mouseY = it }, Modifier.weight(1f),
                            label = { Text("Y px") }, shape = RoundedCornerShape(10.dp), singleLine = true)
                    }
                }

                // ── MOUSE MOVE ────────────────────────────────
                PcStepType.MOUSE_MOVE -> {
                    Text("MOVE CURSOR TO",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(mouseX, { mouseX = it }, Modifier.weight(1f),
                            label = { Text("X px") }, shape = RoundedCornerShape(10.dp), singleLine = true)
                        OutlinedTextField(mouseY, { mouseY = it }, Modifier.weight(1f),
                            label = { Text("Y px") }, shape = RoundedCornerShape(10.dp), singleLine = true)
                    }
                    Text("Tip: 0,0 = top-left corner",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant.copy(0.5f))
                }

                // ── MOUSE SCROLL ──────────────────────────────
                PcStepType.MOUSE_SCROLL -> {
                    Text("SCROLL DIRECTION",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("up" to "▲ Scroll Up", "down" to "▼ Scroll Down").forEach { (dir, lbl) ->
                            FilterChip(
                                selected = scrollDir == dir,
                                onClick  = { scrollDir = dir },
                                label    = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text("AMOUNT: $scrollAmount notches",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Slider(value = scrollAmount.toFloat(), onValueChange = { scrollAmount = it.toInt() },
                        valueRange = 1f..15f, steps = 13, modifier = Modifier.fillMaxWidth())
                }

                // ── OPEN FILE ─────────────────────────────────
                PcStepType.OPEN_FILE -> {
                    Text("FILE PATH",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    if (pickedOpenFile.isNotEmpty()) {
                        PickedFileChip(pickedOpenFile) { pickedOpenFile = "" }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                PcFileFilter.MEDIA  to "Media",
                                PcFileFilter.DOCS   to "Docs",
                                PcFileFilter.IMAGES to "Images",
                                PcFileFilter.ALL    to "All"
                            ).forEach { (_, label) ->
                                OutlinedButton(
                                    onClick          = { openFileLauncher.launch(pickerIntent()) },
                                    contentPadding   = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape            = RoundedCornerShape(10.dp),
                                    modifier         = Modifier.weight(1f)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        OutlinedTextField(
                            value         = manualPath,
                            onValueChange = { manualPath = it },
                            label         = { Text("Or type path manually") },
                            placeholder   = { Text("C:\\Users\\file.pdf") },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            singleLine    = true
                        )
                    }
                }

                // ── FILE OP ───────────────────────────────────
                PcStepType.FILE_OP -> {
                    Text("ACTION",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    PC_FILE_ACTIONS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { action ->
                                FilterChip(
                                    selected = fileAction == action,
                                    onClick  = { fileAction = action },
                                    label    = { Text(action, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    HorizontalDivider(color = cs.outline.copy(0.1f))

                    // FROM path
                    Text("SOURCE PATH *",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    if (fileOpFrom.isNotEmpty()) {
                        PickedFileChip(fileOpFrom, label = "From") { fileOpFrom = "" }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick  = { fileOpFromLauncher.launch(pickerIntent()) },
                                shape    = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Folder, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Browse Source", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedTextField(
                            value         = fileOpFrom,
                            onValueChange = { fileOpFrom = it },
                            label         = { Text("Or enter path") },
                            placeholder   = { Text("C:\\source\\file.txt") },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            singleLine    = true
                        )
                    }

                    // TO path (not needed for DELETE)
                    if (fileAction != "DELETE") {
                        HorizontalDivider(color = cs.outline.copy(0.1f))
                        Text(
                            if (fileAction == "RENAME") "NEW NAME *" else "DESTINATION PATH",
                            style = MaterialTheme.typography.labelSmall, color = cs.primary
                        )
                        if (fileOpTo.isNotEmpty() && fileAction != "RENAME") {
                            PickedFileChip(fileOpTo, label = "To") { fileOpTo = "" }
                        } else {
                            if (fileAction != "RENAME") {
                                OutlinedButton(
                                    onClick  = { fileOpToLauncher.launch(pickerIntent()) },
                                    shape    = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse Destination", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            OutlinedTextField(
                                value         = fileOpTo,
                                onValueChange = { fileOpTo = it },
                                label         = {
                                    Text(if (fileAction == "RENAME") "New name/path" else "Or enter path")
                                },
                                placeholder   = {
                                    Text(if (fileAction == "RENAME") "newname.txt" else "C:\\dest\\")
                                },
                                modifier   = Modifier.fillMaxWidth(),
                                shape      = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                // ── RUN SCRIPT ────────────────────────────────
                PcStepType.RUN_SCRIPT -> {
                    Text("SCRIPT PATH",
                        style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    if (pickedScript.isNotEmpty()) {
                        PickedFileChip(pickedScript) { pickedScript = "" }
                    } else {
                        Button(
                            onClick  = { scriptLauncher.launch(pickerIntent()) },
                            shape    = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Code, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse Scripts (.py / .bat / .ps1)")
                        }
                        OutlinedTextField(
                            value         = manualPath,
                            onValueChange = { manualPath = it },
                            label         = { Text("Or type path manually") },
                            placeholder   = { Text("C:\\scripts\\run.bat") },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            singleLine    = true
                        )
                    }
                }

                // ── WAIT ─────────────────────────────────────
                PcStepType.WAIT -> {
                    Text("WAIT: ${waitMs.toInt()}ms = ${"%.1f".format(waitMs / 1000)}s",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Slider(value = waitMs, onValueChange = { waitMs = it },
                        valueRange = 500f..15000f, steps = 28,
                        modifier = Modifier.fillMaxWidth())
                }

                // ── SYSTEM CMD ────────────────────────────────
                PcStepType.SYSTEM_CMD -> {
                    PC_SYSTEM_COMMANDS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { cmd ->
                                FilterChip(
                                    selected = sysCmd == cmd,
                                    onClick  = { sysCmd = cmd },
                                    label    = { Text(cmd, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (sysCmd in listOf("VOLUME_SET", "OPEN_URL", "OPEN_FOLDER", "WIN_R")) {
                        OutlinedTextField(
                            value         = sysCmdArg,
                            onValueChange = { sysCmdArg = it },
                            label         = {
                                Text(when (sysCmd) {
                                    "VOLUME_SET"  -> "Volume (0–100)"
                                    "OPEN_URL"    -> "URL"
                                    "OPEN_FOLDER" -> "Folder path"
                                    "WIN_R"       -> "Run command"
                                    else          -> "Value"
                                })
                            },
                            modifier   = Modifier.fillMaxWidth(),
                            shape      = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Add Step Button ───────────────────────────────
            Button(
                onClick = {
                    onStepCreated(buildStep(
                        type         = selectedType,
                        pickedApp    = pickedApp,
                        manualPath   = manualPath,
                        pickedFile   = pickedLaunchFile,
                        keyValue     = keyValue,
                        textValue    = textValue,
                        waitMs       = waitMs,
                        sysCmd       = sysCmd,
                        sysCmdArg    = sysCmdArg,
                        openFilePath = pickedOpenFile.ifEmpty { manualPath },
                        scriptPath   = pickedScript.ifEmpty { manualPath },
                        killName     = pickedApp?.exePath?.substringAfterLast("\\")
                                           ?.substringAfterLast("/") ?: killName,
                        mouseX       = mouseX.toIntOrNull() ?: 0,
                        mouseY       = mouseY.toIntOrNull() ?: 0,
                        mouseButton  = mouseButton,
                        mouseDouble  = mouseDouble,
                        scrollAmount = scrollAmount,
                        scrollDir    = scrollDir,
                        fileAction   = fileAction,
                        fileFrom     = fileOpFrom,
                        fileTo       = fileOpTo
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(10.dp),
                enabled  = canAdd
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Step", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED SMALL COMPOSABLES
// ─────────────────────────────────────────────────────────────

@Composable
private fun PickedAppCard(
    app    : PcInstalledApp,
    tint   : Color,
    onClear: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = tint)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(app.icon.ifEmpty { "🖥" }, fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.exePath,
                    style   = MaterialTheme.typography.bodySmall,
                    color   = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AppSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        placeholder   = { Text("Search apps…") },
        leadingIcon   = { Icon(Icons.Default.Search, null) },
        trailingIcon  = {
            if (query.isNotEmpty())
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp))
                }
        },
        modifier   = Modifier.fillMaxWidth(),
        shape      = RoundedCornerShape(10.dp),
        singleLine = true
    )
}

@Composable
private fun AppsList(
    apps     : List<PcInstalledApp>,
    loading  : Boolean,
    emptyHint: String,
    onPick   : (PcInstalledApp) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    when {
        loading      -> LinearProgressIndicator(Modifier.fillMaxWidth())
        apps.isEmpty() -> Text(emptyHint,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant)
        else -> Column(Modifier.fillMaxWidth().heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.forEach { app ->
                Surface(
                    onClick  = { onPick(app) },
                    shape    = RoundedCornerShape(10.dp),
                    color    = if (app.isRunning) cs.tertiaryContainer
                               else cs.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(app.icon.ifEmpty { "🖥" }, fontSize = 18.sp)
                        Text(app.name,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            style      = MaterialTheme.typography.bodySmall,
                            maxLines   = 1, overflow = TextOverflow.Ellipsis)
                        if (app.isRunning)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = cs.tertiary.copy(0.15f)
                            ) {
                                Text("Running",
                                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.tertiary)
                            }
                        Icon(Icons.Default.ChevronRight, null,
                            Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickedFileChip(
    path   : String,
    label  : String = "File",
    onClear: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.InsertDriveFile, null,
                tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
                Text(path.substringAfterLast('\\').substringAfterLast('/').take(40),
                    style    = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP BUILDER
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
    killName    : String,
    mouseX      : Int       = 0,
    mouseY      : Int       = 0,
    mouseButton : String    = "left",
    mouseDouble : Boolean   = false,
    scrollAmount: Int       = 3,
    scrollDir   : String    = "down",
    fileAction  : String    = "COPY",
    fileFrom    : String    = "",
    fileTo      : String    = ""
): PcStep = when (type) {
    PcStepType.LAUNCH_APP   -> PcStep("LAUNCH_APP",
        pickedApp?.exePath ?: manualPath,
        args = if (pickedFile.isNotEmpty()) listOf(pickedFile) else emptyList())
    PcStepType.KILL_APP     -> PcStep("KILL_APP", killName)
    PcStepType.KEY_PRESS    -> PcStep("KEY_PRESS", keyValue)
    PcStepType.TYPE_TEXT    -> PcStep("TYPE_TEXT", textValue)
    PcStepType.WAIT         -> PcStep("WAIT", ms = waitMs.toInt())
    PcStepType.OPEN_FILE    -> PcStep("OPEN_FILE", openFilePath)
    PcStepType.FILE_OP      -> PcStep("FILE_OP", action = fileAction, from = fileFrom, to = fileTo)
    PcStepType.SYSTEM_CMD   -> PcStep("SYSTEM_CMD", sysCmd,
        args = if (sysCmdArg.isNotEmpty()) listOf(sysCmdArg) else emptyList())
    PcStepType.RUN_SCRIPT   -> PcStep("RUN_SCRIPT", scriptPath)
    PcStepType.MOUSE_CLICK  -> PcStep("MOUSE_CLICK",
        x = mouseX, y = mouseY, button = mouseButton, double = mouseDouble)
    PcStepType.MOUSE_MOVE   -> PcStep("MOUSE_MOVE", x = mouseX, y = mouseY)
    PcStepType.MOUSE_SCROLL -> PcStep("MOUSE_SCROLL",
        value = scrollDir, amount = scrollAmount)
}
