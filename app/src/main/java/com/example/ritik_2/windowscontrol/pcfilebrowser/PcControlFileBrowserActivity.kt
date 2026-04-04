package com.example.ritik_2.windowscontrol.pcfilebrowser

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.theme.Ritik_2Theme
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PcControlFileBrowserActivity : ComponentActivity() {

    private lateinit var viewModel: PcControlViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PcControlViewModel::class.java]
        setContent {
            Ritik_2Theme() {
                FileBrowserScreen(viewModel)
            }
        }
    }
}

/**
 * Drop-in replacement for the old PcControlFileBrowserUI(viewModel) call.
 * Use this in PcControlMainUI.kt:
 *
 *   PcScreen.FILE_BROWSER -> PcFileBrowserCompat(viewModel)
 */
@Composable
fun PcFileBrowserCompat(viewModel: PcControlViewModel) {
    FileBrowserScreen(viewModel)
}

// ── Screen composable — all logic lives here ──────────────────────────────────

@Composable
fun FileBrowserScreen(vm: PcControlViewModel) {

    // ── ViewModel state ───────────────────────────────────────────────────────
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

    // ── Local UI state ────────────────────────────────────────────────────────
    var level          by remember { mutableStateOf<BrowserLevel>(BrowserLevel.Root) }
    var selectedFilter by remember { mutableStateOf(vm.defaultFilter) }
    var openingFile    by remember { mutableStateOf<String?>(null) }

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Init ──────────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) { vm.loadDrives() }

    // Sync level when ViewModel resets path
    LaunchedEffect(currentPath) {
        if (currentPath.isEmpty() && level !is BrowserLevel.Root) level = BrowserLevel.Root
    }

    // ── Back handler ──────────────────────────────────────────────────────────
    BackHandler(enabled = level !is BrowserLevel.Root) {
        level = resolveParentLevel(level, drives)
        vm.navigateUp()
    }

    // ── Map specialFolders to PcRecentPath (safe, no reflection) ─────────────
    // ViewModel exposes specialFolders as List<SpecialFolder> — cast directly.
    // If your type is named differently, replace the cast below.
    val mappedSpecialFolders: List<PcRecentPath> = remember(specialFolders) {
        specialFolders.mapNotNull { folder ->
            runCatching {
                // SpecialFolder is an inner/data class in PcControlViewModel
                // with fields: name: String, path: String, icon: String
                val name = folder.name
                val path = folder.path
                val icon = folder.icon
                PcRecentPath(label = name, path = path, icon = icon, isApp = false)
            }.getOrNull()
        }
    }

    // ── File launchers ────────────────────────────────────────────────────────
    var uploadDestPath by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val cr    = context.contentResolver
                val bytes = cr.openInputStream(uri)?.readBytes() ?: return@launch
                val name  = cr.query(uri, null, null, null, null)?.use { cur ->
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cur.moveToFirst()
                    if (idx >= 0) cur.getString(idx) else null
                } ?: "upload_${System.currentTimeMillis()}"
                vm.uploadFile(bytes, name, uploadDestPath.ifBlank { currentPath })
            } catch (e: Exception) {
                Log.e("FileBrowser", "Upload error: ${e.message}")
            }
        }
    }

    var downloadPath by remember { mutableStateOf("") }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.downloadFile(downloadPath, uri, context.contentResolver)
    }

    // ── Folder action handler ─────────────────────────────────────────────────
    // Add downloadFolder / deleteFolder / showFolderProperties / startFolderMove
    // to your PcControlViewModel. Stubs shown below — replace with real impl.
    fun handleFolderAction(item: PcFileItem, action: FolderAction) {
        scope.launch {
            when (action) {
                FolderAction.DOWNLOAD    -> {
                    // Example: vm.downloadFolder(item.path)
                    Log.d("FileBrowser", "Download folder: ${item.path}")
                }
                FolderAction.DELETE      -> {
                    // Example: vm.deleteFolder(item.path)
                    Log.d("FileBrowser", "Delete folder: ${item.path}")
                    delay(300)
                    vm.browseDir(currentPath, selectedFilter)
                }
                FolderAction.PROPERTIES  -> {
                    // Example: vm.showFolderProperties(item.path)
                    Log.d("FileBrowser", "Properties: ${item.path}")
                }
                FolderAction.MOVE        -> {
                    // Example: vm.startFolderMove(item.path)
                    Log.d("FileBrowser", "Move: ${item.path}")
                }
            }
        }
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
        selectedFilter   = selectedFilter,
        level            = level,
        openWithDialog   = openWithDlg
    )

    // ── Build callbacks ───────────────────────────────────────────────────────
    val callbacks = FileBrowserCallbacks(
        onDriveClick = { drive ->
            level = BrowserLevel.Drive(drive)
            vm.browseDir("${drive.letter}:/")
        },
        onFolderClick = { folder ->
            level = BrowserLevel.Directory(path = folder.path, label = folder.name)
            vm.browseDir(folder.path, selectedFilter)
        },
        onSpecialFolderClick = { path, name ->
            level = BrowserLevel.Directory(path = path, label = name)
            vm.browseDir(path)
        },
        onRecentClick = { recent ->
            level = BrowserLevel.Directory(path = recent.path, label = recent.label)
            vm.browseDir(recent.path)
        },
        onFileOpen = { file ->
            openingFile = file.name
            vm.executeQuickStep(buildOpenCommand(file))
            vm.startOpenWithPolling()
            scope.launch { delay(3000); openingFile = null }
        },
        onFileDownload = { file ->
            downloadPath = file.path
            downloadLauncher.launch(file.name)
        },
        onFolderAction = { item, action -> handleFolderAction(item, action) },
        onFilterChange = { filter ->
            selectedFilter = filter
            vm.browseDir(currentPath, filter)
        },
        onPing    = { vm.pingPc() },
        onUpload  = {
            uploadDestPath = currentPath
            uploadLauncher.launch("*/*")
        },
        onRefresh = { vm.browseDir(currentPath, selectedFilter) },
        onBreadcrumbNav = { target ->
            level = target
            when (target) {
                is BrowserLevel.Root      -> vm.navigateUp()
                is BrowserLevel.Drive     -> vm.browseDir("${target.drive.letter}:/")
                is BrowserLevel.Directory -> vm.browseDir(target.path, selectedFilter)
            }
        },
        onDismissTransfer = { vm.clearTransferProgress() },
        onOpenWithSelect  = { vm.resolveOpenWith(it.exePath) },
        onDismissOpenWith = { vm.dismissOpenWithDialog() }
    )

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

fun buildOpenCommand(file: PcFileItem): PcStep {
    return when (file.extension.lowercase()) {
        "mp4", "mkv", "avi", "mov", "wmv",
        "flac", "mp3", "wav", "m4a", "aac"   -> PcStep("LAUNCH_APP", "vlc.exe",        args = listOf(file.path))
        "jpg", "jpeg", "png", "gif",
        "bmp", "webp", "tiff", "svg"          -> PcStep("SYSTEM_CMD", "OPEN_FILE",      args = listOf(file.path))
        "doc", "docx", "rtf", "odt"           -> PcStep("LAUNCH_APP", "WINWORD.EXE",    args = listOf(file.path))
        "xls", "xlsx", "csv", "ods"           -> PcStep("LAUNCH_APP", "EXCEL.EXE",      args = listOf(file.path))
        "ppt", "pptx", "odp"                  -> PcStep("LAUNCH_APP", "POWERPNT.EXE",   args = listOf(file.path))
        else                                   -> PcStep("SYSTEM_CMD", "OPEN_FILE",      args = listOf(file.path))
    }
}

// ── ViewModel extension property ──────────────────────────────────────────────
// Add a defaultFilter property to your PcControlViewModel:
//   val defaultFilter: PcFileFilter = PcFileFilter.ALL
// Or replace `vm.defaultFilter` with `PcFileFilter.ALL` directly if not present.
val PcControlViewModel.defaultFilter: PcFileFilter get() = PcFileFilter.ALL