package com.example.ritik_2.windowscontrol.pcfilebrowser

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel.BrowserLevelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PcControlFileBrowserActivity : ComponentActivity() {
    private lateinit var viewModel: PcControlViewModel

    companion object {
        const val EXTRA_PICK_MODE    = "pick_mode"
        const val EXTRA_SELECTED_PATH = "selected_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyFullscreen()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        viewModel = ViewModelProvider(this)[PcControlViewModel::class.java]
        val isPickMode = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
        setContent {
            ITConnectTheme {
                FileBrowserScreen(
                    vm          = viewModel,
                    onFilePicked = if (isPickMode) { path ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_PATH, path))
                        finish()
                    } else null
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun PcFileBrowserCompat(viewModel: PcControlViewModel) = FileBrowserScreen(viewModel)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun FileBrowserScreen(vm: PcControlViewModel, onFilePicked: ((String) -> Unit)? = null) {

    val drives           by vm.drives.collectAsStateWithLifecycle()
    val currentPath      by vm.currentPath.collectAsStateWithLifecycle()
    val dirItems         by vm.dirItems.collectAsStateWithLifecycle()
    val isLoading        by vm.browseLoading.collectAsStateWithLifecycle()
    val recentPaths      by vm.recentPaths.collectAsStateWithLifecycle()
    val specialFolders   by vm.specialFolders.collectAsStateWithLifecycle()
    val browserMode      by vm.fileBrowserMode.collectAsStateWithLifecycle()
    val transferProg     by vm.transferProgress.collectAsStateWithLifecycle()
    val connectionStatus by vm.connectionStatus.collectAsStateWithLifecycle()
    val openWithDlg      by vm.openWithDialog.collectAsStateWithLifecycle()
    val plans            by vm.plans.observeAsState(emptyList())

    val vmLevel       by vm.browserLevel.collectAsStateWithLifecycle()
    val vmFilter      by vm.selectedFilter.collectAsStateWithLifecycle()
    val vmSearchQuery by vm.searchQuery.collectAsStateWithLifecycle()

    var addFileToPlanItem by remember { mutableStateOf<PcFileItem?>(null) }

    val level: BrowserLevel = when (val l = vmLevel) {
        is BrowserLevelState.Root      -> BrowserLevel.Root
        is BrowserLevelState.Drive     ->
            drives.find { it.letter == l.letter }
                ?.let { BrowserLevel.Drive(it) }
                ?: BrowserLevel.Root
        is BrowserLevelState.Directory -> BrowserLevel.Directory(l.path, l.label)
    }

    fun persistLevel(bl: BrowserLevel) {
        vm.setBrowserLevel(when (bl) {
            is BrowserLevel.Root      -> BrowserLevelState.Root
            is BrowserLevel.Drive     -> BrowserLevelState.Drive(bl.drive.letter, bl.drive.label)
            is BrowserLevel.Directory -> BrowserLevelState.Directory(bl.path, bl.label)
        })
    }

    // ── Server state ─────────────────────────────────────────────────────────
    val context = LocalContext.current
    var savedServers by remember {
        mutableStateOf(ServerCredentialStore.load(context))
    }
    var showServerDialog by remember { mutableStateOf(false) }
    var editingServer    by remember { mutableStateOf<SavedServerCredential?>(null) }

    // ── Clipboard for copy/move operations ───────────────────────────────────
    var clipboardItem    by remember { mutableStateOf<PcFileItem?>(null) }
    var clipboardAction  by remember { mutableStateOf("") }

    var openingFile  by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope   = rememberCoroutineScope()

    // ── One-time init ─────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (drives.isEmpty()) {
            vm.loadDrives()
        } else if (currentPath.isNotEmpty()) {
            vm.browseDir(currentPath, vmFilter, isRefresh = true)
        }
    }

    // ── Back handler — navigate folder-by-folder ─────────────────────────────
    BackHandler(enabled = level !is BrowserLevel.Root) {
        val parent = resolveParentLevel(level, drives)
        persistLevel(parent)
        when (parent) {
            is BrowserLevel.Root      -> vm.navigateUp()
            is BrowserLevel.Drive     -> vm.browseDir("${parent.drive.letter}:/")
            is BrowserLevel.Directory -> vm.browseDir(parent.path, vmFilter)
        }
    }

    // ── Map specialFolders ────────────────────────────────────────────────────
    val mappedSpecialFolders: List<PcRecentPath> = remember(specialFolders) {
        specialFolders.mapNotNull { folder ->
            runCatching {
                PcRecentPath(label = folder.name, path = folder.path,
                    icon = folder.icon, isApp = false)
            }.getOrNull()
        }
    }

    // ── Upload file launcher ──────────────────────────────────────────────────
    var uploadDestPath by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val cr   = context.contentResolver
                val name = cr.query(uri, null, null, null, null)?.use { cur ->
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cur.moveToFirst() && idx >= 0) cur.getString(idx) else null
                } ?: "upload_${System.currentTimeMillis()}"

                val fileSize = cr.query(
                    uri, arrayOf(OpenableColumns.SIZE), null, null, null
                )?.use { cur ->
                    val idx = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (cur.moveToFirst() && idx >= 0) cur.getLong(idx) else -1L
                } ?: -1L

                vm.uploadFileStream(
                    contentResolver = cr,
                    uri             = uri,
                    fileSize        = fileSize,
                    fileName        = name,
                    remotePath      = uploadDestPath.ifBlank { currentPath }
                )
                delay(600)
                vm.browseDir(currentPath, vmFilter, isRefresh = true)
            } catch (e: Exception) {
                Log.e("FileBrowser", "Upload error: ${e.message}")
            }
        }
    }

    val uploadFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        Log.d("FileBrowser", "Upload folder URI: $uri")
    }

    // ── Download launcher ─────────────────────────────────────────────────────
    var downloadItem by remember { mutableStateOf<PcFileItem?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val item = downloadItem ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                vm.downloadFile(item.path, uri, context.contentResolver)
                delay(600)
                vm.browseDir(currentPath, vmFilter, isRefresh = true)
                delay(500)
                val mimeType = getMimeType(item.extension) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            } catch (e: Exception) {
                Log.e("FileBrowser", "Download error: ${e.message}")
            } finally {
                downloadItem = null
            }
        }
    }

    // ── Execute file on PC ────────────────────────────────────────────────────
    fun executeFileOnPc(file: PcFileItem) {
        openingFile = file.name
        vm.executeQuickStep(PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path)))
        scope.launch { delay(3000); openingFile = null }
    }

    // ── Item action handler ───────────────────────────────────────────────────
    fun handleItemAction(item: PcFileItem, action: ItemAction) {
        scope.launch {
            when (action) {
                ItemAction.DOWNLOAD -> {
                    downloadItem = item
                    downloadLauncher.launch(item.name)
                }
                ItemAction.DELETE -> {
                    vm.executeQuickStep(PcStep("FILE_OP", action = "DELETE", from = item.path))
                    delay(400)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.RENAME -> {
                    val currentDir = item.path.substringBeforeLast('/')
                        .substringBeforeLast('\\')
                    val newPath = if (currentDir.isNotEmpty())
                        "$currentDir/${item.name}" else item.name
                    vm.executeQuickStep(
                        PcStep("FILE_OP", action = "RENAME",
                            from = item.path,
                            to   = newPath)
                    )
                    delay(400)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.COPY -> {
                    // FIX: Proper in-place copy with correct extension handling
                    val srcPath = item.path
                    val destPath = if (item.isDir) {
                        "${srcPath}_copy"
                    } else {
                        val dir  = srcPath.substringBeforeLast('/').substringBeforeLast('\\')
                        val name = item.name
                        val ext  = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
                        val base = if (ext.isNotEmpty()) name.substringBeforeLast('.') else name
                        val newName = "${base}_copy$ext"
                        if (dir.isNotEmpty()) "$dir/$newName" else newName
                    }
                    vm.executeQuickStep(
                        PcStep("FILE_OP", action = "COPY",
                            from = srcPath, to = destPath)
                    )
                    delay(500)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.MOVE -> {
                    // FIX: Set clipboard for move — user navigates to dest and pastes
                    clipboardItem   = item
                    clipboardAction = "MOVE"
                }
                ItemAction.PASTE -> {
                    val clip = clipboardItem ?: return@launch
                    val destDir = currentPath
                    if (destDir.isBlank()) return@launch

                    val destPath = "$destDir/${clip.name}"
                    when (clipboardAction) {
                        "COPY" -> vm.executeQuickStep(
                            PcStep("FILE_OP", action = "COPY",
                                from = clip.path, to = destPath))
                        "MOVE" -> vm.executeQuickStep(
                            PcStep("FILE_OP", action = "MOVE",
                                from = clip.path, to = destPath))
                    }
                    clipboardItem = null
                    clipboardAction = ""
                    delay(500)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.PROPERTIES -> { /* handled in UI */ }
            }
        }
    }

    // ── Navigate ──────────────────────────────────────────────────────────────
    fun navigateTo(target: BrowserLevel) {
        persistLevel(target)
        when (target) {
            is BrowserLevel.Root      -> vm.navigateUp()
            is BrowserLevel.Drive     -> vm.browseDir("${target.drive.letter}:/")
            is BrowserLevel.Directory -> vm.browseDir(target.path, vmFilter)
        }
    }

    fun doRefresh() {
        scope.launch {
            isRefreshing = true
            vm.browseDir(currentPath, vmFilter, isRefresh = true)
            delay(800)
            isRefreshing = false
        }
    }

    fun createFolder(folderName: String) {
        if (folderName.isBlank() || currentPath.isEmpty()) return
        scope.launch {
            vm.executeQuickStep(PcStep("FILE_OP", action = "MKDIR",
                from = "$currentPath/$folderName"))
            delay(500)
            vm.browseDir(currentPath, vmFilter, isRefresh = true)
        }
    }

    fun handleSearchChange(query: String) {
        vm.setSearchQuery(query)
        val trimmed = query.trim().trimStart('\u200B')
        when {
            trimmed.length >= 2 && currentPath.isNotEmpty() ->
                scope.launch { vm.searchFiles(currentPath, trimmed) }
            trimmed.isEmpty() && currentPath.isNotEmpty() ->
                vm.browseDir(currentPath, vmFilter, isRefresh = false)
        }
    }

    // ── Server dialogs ───────────────────────────────────────────────────────
    if (showServerDialog) {
        ServerCredentialDialog(
            existing  = editingServer,
            onSave    = { cred ->
                val updated = savedServers.filter { it.id != cred.id } + cred
                savedServers = updated
                ServerCredentialStore.save(context, updated)
                showServerDialog = false
                editingServer = null
            },
            onDismiss = { showServerDialog = false; editingServer = null }
        )
    }

    // ── Build UI state ────────────────────────────────────────────────────────
    val uiState = FileBrowserUiState(
        drives           = drives,
        currentPath      = currentPath,
        dirItems         = dirItems,
        isLoading        = isLoading,
        recentPaths      = recentPaths,
        specialFolders   = mappedSpecialFolders,
        browserMode      = browserMode,
        transferProgress = transferProg,
        connectionStatus = connectionStatus,
        openingFileName  = openingFile,
        selectedFilter   = vmFilter,
        level            = level,
        openWithDialog   = openWithDlg,
        searchQuery      = vmSearchQuery,
        isRefreshing     = isRefreshing,
        savedServers     = savedServers,
    )

    val callbacks = FileBrowserCallbacks(
        onDriveClick = { drive ->
            persistLevel(BrowserLevel.Drive(drive))
            vm.browseDir("${drive.letter}:/")
        },
        onFolderClick = { folder ->
            persistLevel(BrowserLevel.Directory(path = folder.path, label = folder.name))
            vm.browseDir(folder.path, vmFilter)
        },
        onSpecialFolderClick = { path, name ->
            persistLevel(BrowserLevel.Directory(path = path, label = name))
            vm.browseDir(path)
        },
        onRecentClick = { recent ->
            persistLevel(BrowserLevel.Directory(path = recent.path, label = recent.label))
            vm.browseDir(recent.path)
        },
        onFileOpen     = { file ->
            if (onFilePicked != null) onFilePicked(file.path) else executeFileOnPc(file)
        },
        onFileDownload = { file ->
            downloadItem = file
            downloadLauncher.launch(file.name)
        },
        onItemAction   = { item, action -> handleItemAction(item, action) },
        onFilterChange = { filter ->
            vm.setSelectedFilter(filter)
            vm.browseDir(currentPath, filter, isRefresh = true)
        },
        onPing            = { vm.pingPc() },
        onUpload          = { uploadDestPath = currentPath; uploadLauncher.launch("*/*") },
        onUploadFolder    = { uploadFolderLauncher.launch(null) },
        onCreateFolder    = { folderName -> createFolder(folderName) },
        onRefresh         = { doRefresh() },
        onBreadcrumbNav   = { target -> navigateTo(target) },
        onNavigateBack    = { navigateTo(resolveParentLevel(level, drives)) },
        onDismissTransfer = { vm.clearTransferProgress() },
        onOpenWithSelect  = { vm.resolveOpenWith(it.exePath) },
        onDismissOpenWith = { vm.dismissOpenWithDialog() },
        onSearchChange    = { query -> handleSearchChange(query) },
        onAddServer       = { showServerDialog = true },
        onEditServer      = { server -> editingServer = server; showServerDialog = true },
        onDeleteServer    = { server ->
            val updated = savedServers.filter { it.id != server.id }
            savedServers = updated
            ServerCredentialStore.save(context, updated)
        },
        onConnectServer   = { /* TODO: SMB server connect */ },
        onAddFileToPlan   = if (plans.isNotEmpty()) { item -> addFileToPlanItem = item } else null,
    )

    // ── Plan selector dialog (add file to plan) ───────────────────────────────
    addFileToPlanItem?.let { fileItem ->
        AlertDialog(
            onDismissRequest = { addFileToPlanItem = null },
            icon  = { Icon(Icons.Default.PlaylistAdd, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add to Plan") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Select a plan to add \"${fileItem.name}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier            = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(plans) { plan ->
                            Surface(
                                onClick = {
                                    val step        = PcStep("OPEN_FILE", fileItem.path)
                                    val updatedPlan = plan.copy(
                                        stepsJson = PcStepSerializer.toJson(plan.steps + step)
                                    )
                                    vm.savePlanDirectly(updatedPlan)
                                    addFileToPlanItem = null
                                },
                                shape    = RoundedCornerShape(10.dp),
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(plan.planName,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines   = 1,
                                            overflow   = TextOverflow.Ellipsis)
                                        Text("${plan.steps.size} steps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addFileToPlanItem = null }) { Text("Cancel") } }
        )
    }

    PcControlFileBrowserUI(state = uiState, callbacks = callbacks)
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

fun resolveParentLevel(level: BrowserLevel, drives: List<PcDrive>): BrowserLevel =
    when (level) {
        is BrowserLevel.Root      -> BrowserLevel.Root
        is BrowserLevel.Drive     -> BrowserLevel.Root
        is BrowserLevel.Directory -> {
            val parent = level.path.trimEnd('/', '\\')
                .substringBeforeLast('/').substringBeforeLast('\\')
            if (parent.length <= 3) {
                val d = drives.find { level.path.startsWith("${it.letter}:") }
                if (d != null) BrowserLevel.Drive(d) else BrowserLevel.Root
            } else {
                BrowserLevel.Directory(
                    path  = parent,
                    label = parent.substringAfterLast('/').substringAfterLast('\\')
                )
            }
        }
    }

fun buildOpenCommand(file: PcFileItem): PcStep =
    PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))

fun getMimeType(extension: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())

fun encodePath(path: String): String =
    URLEncoder.encode(path, StandardCharsets.UTF_8.name()).replace("+", "%20")