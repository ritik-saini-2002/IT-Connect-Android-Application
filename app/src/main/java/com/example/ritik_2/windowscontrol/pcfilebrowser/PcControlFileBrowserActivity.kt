package com.example.ritik_2.windowscontrol.pcfilebrowser

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
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
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel.BrowserLevelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import androidx.core.view.WindowCompat
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

class PcControlFileBrowserActivity : ComponentActivity() {
    private lateinit var viewModel: PcControlViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel = ViewModelProvider(this)[PcControlViewModel::class.java]
        setContent { Ritik_2Theme { FileBrowserScreen(viewModel) } }
    }
}

@Composable
fun PcFileBrowserCompat(viewModel: PcControlViewModel) = FileBrowserScreen(viewModel)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun FileBrowserScreen(vm: PcControlViewModel) {

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

    // Persistent state from ViewModel (survives tab switches)
    val vmLevel       by vm.browserLevel.collectAsStateWithLifecycle()
    val vmFilter      by vm.selectedFilter.collectAsStateWithLifecycle()
    val vmSearchQuery by vm.searchQuery.collectAsStateWithLifecycle()

    // Convert ViewModel level state → UI BrowserLevel
    val level: BrowserLevel = when (val l = vmLevel) {
        is BrowserLevelState.Root      -> BrowserLevel.Root
        is BrowserLevelState.Drive     ->
            drives.find { it.letter == l.letter }
                ?.let { BrowserLevel.Drive(it) }
                ?: BrowserLevel.Root
        is BrowserLevelState.Directory -> BrowserLevel.Directory(l.path, l.label)
    }

    // Persist level back to ViewModel
    fun persistLevel(bl: BrowserLevel) {
        vm.setBrowserLevel(when (bl) {
            is BrowserLevel.Root      -> BrowserLevelState.Root
            is BrowserLevel.Drive     -> BrowserLevelState.Drive(bl.drive.letter, bl.drive.label)
            is BrowserLevel.Directory -> BrowserLevelState.Directory(bl.path, bl.label)
        })
    }

    var openingFile  by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // ── One-time init: only load drives when not already loaded ───────────────
    // If we already have a path (returning from another tab), refresh contents
    // without touching the nav stack or level.
    LaunchedEffect(Unit) {
        if (drives.isEmpty()) {
            vm.loadDrives()
        } else if (currentPath.isNotEmpty()) {
            vm.browseDir(currentPath, vmFilter, isRefresh = true)
        }
    }

    // ── Back handler ──────────────────────────────────────────────────────────
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
                PcRecentPath(
                    label = folder.name,
                    path  = folder.path,
                    icon  = folder.icon,
                    isApp = false
                )
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
                val cr    = context.contentResolver
                val bytes = cr.openInputStream(uri)?.readBytes() ?: return@launch
                val name  = cr.query(uri, null, null, null, null)?.use { cur ->
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cur.moveToFirst(); if (idx >= 0) cur.getString(idx) else null
                } ?: "upload_${System.currentTimeMillis()}"
                vm.uploadFile(bytes, name, uploadDestPath.ifBlank { currentPath })
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
        vm.executeQuickStep(
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
        )
        vm.startOpenWithPolling()
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
                    val newPath = item.path.substringBeforeLast('/') + "/" + item.name
                    vm.executeQuickStep(
                        PcStep(
                            "FILE_OP", action = "RENAME",
                            from = item.path.substringBeforeLast('/') +
                                    "/" + item.path.substringAfterLast('/'),
                            to = newPath
                        )
                    )
                    delay(400)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.MOVE -> {
                    val srcPath = item.path.substringBeforeLast('/')
                    vm.executeQuickStep(
                        PcStep("FILE_OP", action = "MOVE", from = srcPath, to = item.path)
                    )
                    delay(400)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.COPY -> {
                    val destPath = "$currentPath/${item.name}_copy"
                    vm.executeQuickStep(
                        PcStep("FILE_OP", action = "COPY", from = item.path, to = destPath)
                    )
                    delay(400)
                    vm.browseDir(currentPath, vmFilter, isRefresh = true)
                }
                ItemAction.PASTE, ItemAction.PROPERTIES -> { /* handled in UI */ }
            }
        }
    }

    // ── Navigate to a level ───────────────────────────────────────────────────
    fun navigateTo(target: BrowserLevel) {
        persistLevel(target)
        when (target) {
            is BrowserLevel.Root      -> vm.navigateUp()
            is BrowserLevel.Drive     -> vm.browseDir("${target.drive.letter}:/")
            is BrowserLevel.Directory -> vm.browseDir(target.path, vmFilter)
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────
    fun doRefresh() {
        scope.launch {
            isRefreshing = true
            vm.browseDir(currentPath, vmFilter, isRefresh = true)
            delay(800)
            isRefreshing = false
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
        selectedFilter   = vmFilter,
        level            = level,
        openWithDialog   = openWithDlg,
        searchQuery      = vmSearchQuery,
        isRefreshing     = isRefreshing,
    )

    // ── Build callbacks ───────────────────────────────────────────────────────
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
        onFileOpen     = { file -> executeFileOnPc(file) },
        onFileDownload = { file ->
            downloadItem = file
            downloadLauncher.launch(file.name)
        },
        onItemAction   = { item, action -> handleItemAction(item, action) },
        onFilterChange = { filter ->
            vm.setSelectedFilter(filter)
            vm.browseDir(currentPath, filter, isRefresh = true)
        },
        onPing         = { vm.pingPc() },
        onUpload       = { uploadDestPath = currentPath; uploadLauncher.launch("*/*") },
        onUploadFolder = { uploadFolderLauncher.launch(null) },
        onRefresh      = { doRefresh() },
        onBreadcrumbNav = { target -> navigateTo(target) },
        onNavigateBack  = {
            val parent = resolveParentLevel(level, drives)
            navigateTo(parent)
        },
        onDismissTransfer = { vm.clearTransferProgress() },
        onOpenWithSelect  = { vm.resolveOpenWith(it.exePath) },
        onDismissOpenWith = { vm.dismissOpenWithDialog() },
        onSearchChange    = { vm.setSearchQuery(it) },
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

fun buildOpenCommand(file: PcFileItem): PcStep =
    PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))

fun getMimeType(extension: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())

fun encodePath(path: String): String =
    URLEncoder.encode(path, StandardCharsets.UTF_8.name())
        .replace("+", "%20")