package com.example.ritik_2.windowscontrol.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.FileBrowserMode
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  OPEN COMMAND (execute mode)
// ─────────────────────────────────────────────────────────────

private fun getOpenCommand(file: PcFileItem): PcStep {
    val ext = file.extension.lowercase()
    return when {
        ext in listOf("mp4","mkv","avi","mov","wmv","flac","mp3","wav","m4a","aac") ->
            PcStep("LAUNCH_APP", "vlc.exe", args = listOf(file.path))
        ext in listOf("jpg","jpeg","png","gif","bmp","webp","tiff","svg") ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
        ext in listOf("doc","docx","rtf","odt") ->
            PcStep("LAUNCH_APP", "WINWORD.EXE", args = listOf(file.path))
        ext in listOf("xls","xlsx","csv","ods") ->
            PcStep("LAUNCH_APP", "EXCEL.EXE", args = listOf(file.path))
        ext in listOf("ppt","pptx","odp") ->
            PcStep("LAUNCH_APP", "POWERPNT.EXE", args = listOf(file.path))
        ext == "pdf" ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
        ext in listOf("txt","log","ini","cfg","xml","json","yaml","yml","md") ->
            PcStep("LAUNCH_APP", "notepad.exe", args = listOf(file.path))
        ext in listOf("py","bat","ps1","sh","cmd") ->
            PcStep("LAUNCH_APP", "notepad.exe", args = listOf(file.path))
        else ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
    }
}

private fun getFileIcon(ext: String): String = when (ext.lowercase()) {
    in listOf("mp4","mkv","avi","mov","wmv")          -> "🎬"
    in listOf("mp3","wav","flac","aac","m4a")          -> "🎵"
    in listOf("jpg","jpeg","png","gif","bmp","webp")   -> "🖼️"
    in listOf("pdf")                                   -> "📕"
    in listOf("doc","docx","rtf")                      -> "📘"
    in listOf("xls","xlsx","csv")                      -> "📗"
    in listOf("ppt","pptx")                            -> "📊"
    in listOf("txt","log","md")                        -> "📄"
    in listOf("zip","rar","7z","tar","gz")             -> "🗜️"
    in listOf("py","bat","ps1","sh","cmd")             -> "⚙️"
    in listOf("exe","msi")                             -> "🖥️"
    else                                               -> "📄"
}

// ─────────────────────────────────────────────────────────────
//  MAIN FILE BROWSER UI
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlFileBrowserUI(viewModel: PcControlViewModel) {

    val drives         by viewModel.drives.collectAsStateWithLifecycle()
    val currentPath    by viewModel.currentPath.collectAsStateWithLifecycle()
    val dirItems       by viewModel.dirItems.collectAsStateWithLifecycle()
    val isLoading      by viewModel.browseLoading.collectAsStateWithLifecycle()
    val recentPaths    by viewModel.recentPaths.collectAsStateWithLifecycle()
    val specialFolders by viewModel.specialFolders.collectAsStateWithLifecycle()
    val browserMode    by viewModel.fileBrowserMode.collectAsStateWithLifecycle()
    val transferProg   by viewModel.transferProgress.collectAsStateWithLifecycle()
    val openWithDlg    by viewModel.openWithDialog.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    var selectedFilter  by remember { mutableStateOf(PcFileFilter.ALL) }
    var openingFile     by remember { mutableStateOf<String?>(null) }
    val listState       = rememberLazyListState()
    val scope           = rememberCoroutineScope()
    val context         = LocalContext.current

    // Save/restore scroll position when path changes
    LaunchedEffect(currentPath) {
        val saved = viewModel.getScrollPosition(currentPath)
        if (saved > 0) listState.scrollToItem(saved)
    }
    // Remember position on scroll
    LaunchedEffect(listState.firstVisibleItemIndex) {
        viewModel.saveScrollPosition(currentPath, listState.firstVisibleItemIndex)
    }

    // Load drives on entry
    LaunchedEffect(Unit) { viewModel.loadDrives() }

    BackHandler(enabled = currentPath.isNotEmpty()) { viewModel.navigateUp() }

    // File picker for upload
    var uploadDestPath by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val cr    = context.contentResolver
            val bytes = cr.openInputStream(uri)?.readBytes() ?: return@launch
            // Extract filename from URI
            val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "upload_${System.currentTimeMillis()}"

            // Upload to currentPath (the folder currently being browsed)
            val destFolder = uploadDestPath.ifBlank { currentPath }
            android.util.Log.d("PcControl", "Uploading $name to $destFolder (${bytes.size} bytes)")
            viewModel.uploadFile(bytes, name, destFolder)
        }
    }

    // Save-as picker for download
    var downloadFileName by remember { mutableStateOf("") }
    var downloadPath     by remember { mutableStateOf("") }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.downloadFile(downloadPath, uri, context.contentResolver)
    }

    // ── Open With dialog ──────────────────────────────────
    openWithDlg?.let { dlg ->
        PcOpenWithDialog(
            dialog    = dlg,
            onSelect  = { viewModel.resolveOpenWith(it.exePath) },
            onDismiss = { viewModel.dismissOpenWithDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                title = {
                    Column {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "📁 Files",
                                fontWeight = FontWeight.Bold,
                                style      = MaterialTheme.typography.titleMedium
                            )
                            // Mode badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (browserMode == FileBrowserMode.EXECUTE)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    if (browserMode == FileBrowserMode.EXECUTE) "EXECUTE" else "TRANSFER",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (browserMode == FileBrowserMode.EXECUTE)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (currentPath.isNotEmpty()) {
                            Text(
                                currentPath,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    PcConnectionChip(
                        status  = connectionStatus,
                        onClick = { viewModel.pingPc() }
                    )
                    Spacer(Modifier.width(4.dp))
                    if (currentPath.isNotEmpty()) {
                        // Upload button (transfer mode only)
                        if (browserMode == FileBrowserMode.TRANSFER) {
                            IconButton(onClick = {
                                uploadDestPath = currentPath
                                uploadLauncher.launch("*/*")
                            }) {
                                Icon(Icons.Default.Upload, "Upload")
                            }
                        }
                        IconButton(onClick = { viewModel.browseDir(currentPath, selectedFilter) }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Transfer progress banner ──────────────
                transferProg?.let { prog ->
                    TransferProgressBanner(
                        progress  = prog,
                        onDismiss = { viewModel.clearTransferProgress() }
                    )
                }

                // ── Open file indicator ───────────────────
                openingFile?.let { name ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier              = Modifier.padding(12.dp, 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Opening: $name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // ── Filter chips ──────────────────────────
                if (currentPath.isNotEmpty()) {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(PcFileFilter.entries) { f ->
                            FilterChip(
                                selected = selectedFilter == f,
                                onClick  = { selectedFilter = f; viewModel.browseDir(currentPath, f) },
                                label    = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                // ── Main list ─────────────────────────────
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier            = Modifier.fillMaxSize()
                ) {
                    if (currentPath.isEmpty()) {
                        // Root view
                        if (recentPaths.filter { !it.isApp }.isNotEmpty()) {
                            item {
                                SectionLabel("🕐 RECENTLY USED")
                            }
                            items(recentPaths.filter { !it.isApp }) { recent ->
                                PcRecentPathRow(recent = recent,
                                    onClick = { viewModel.browseDir(recent.path) })
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        if (specialFolders.isNotEmpty()) {
                            item { SectionLabel("📌 QUICK ACCESS") }
                            items(specialFolders, key = { "sf_${it.path}" }) { folder ->
                                QuickAccessRow(folder.icon, folder.name) {
                                    viewModel.browseDir(folder.path)
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        item { SectionLabel("💾 DRIVES") }
                        if (drives.isEmpty() && !isLoading) {
                            item { PcEmptyBrowse("No drives found.\nMake sure agent is running.") }
                        } else {
                            items(drives, key = { it.letter }) { drive ->
                                PcDriveCard(drive = drive,
                                    onClick = { viewModel.browseDir("${drive.letter}:/") })
                            }
                        }
                    } else {
                        // Directory contents
                        val folders = dirItems.filter { it.isDir }
                        val files   = dirItems.filter { !it.isDir }

                        if (folders.isEmpty() && files.isEmpty() && !isLoading) {
                            item { PcEmptyBrowse("This folder is empty") }
                        }
                        if (folders.isNotEmpty()) {
                            item { SectionLabel("📂 FOLDERS (${folders.size})") }
                            items(folders, key = { "d_${it.path}" }) { folder ->
                                PcFileRow(
                                    item    = folder,
                                    icon    = "📂",
                                    onClick = { viewModel.browseDir(folder.path, selectedFilter) },
                                    onOpen  = null,
                                    onDownload = null
                                )
                            }
                        }
                        if (files.isNotEmpty()) {
                            item { SectionLabel("📄 FILES (${files.size})") }
                            items(files, key = { "f_${it.path}" }) { file ->
                                PcFileRow(
                                    item       = file,
                                    icon       = getFileIcon(file.extension),
                                    onClick    = null,
                                    onOpen     = if (browserMode == FileBrowserMode.EXECUTE) {
                                        {
                                            val n = file.name
                                            openingFile = n
                                            viewModel.executeQuickStep(getOpenCommand(file))
                                            // Start polling for Open With dialog
                                            viewModel.startOpenWithPolling()
                                            scope.launch { delay(3000); openingFile = null }
                                        }
                                    } else null,
                                    onDownload = if (browserMode == FileBrowserMode.TRANSFER) {
                                        {
                                            downloadPath     = file.path
                                            downloadFileName = file.name
                                            downloadLauncher.launch(file.name)
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TRANSFER PROGRESS BANNER
// ─────────────────────────────────────────────────────────────

@Composable
fun TransferProgressBanner(
    progress  : com.example.ritik_2.windowscontrol.data.PcTransferProgress,
    onDismiss : () -> Unit
) {
    val bg = if (progress.isUpload)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.tertiaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = bg,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (progress.isUpload) Icons.Default.Upload else Icons.Default.Download,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        progress.fileName.take(28),
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1
                    )
                }
                if (progress.isDone) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (!progress.isDone) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color    = if (progress.isUpload)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(progress.progressFraction * 100).toInt()}%  ${progress.sizeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        buildString {
                            if (progress.speedBps > 0) append(progress.speedLabel)
                            if (progress.etaLabel.isNotEmpty()) append("  ETA ${progress.etaLabel}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    if (progress.error != null) "❌ ${progress.error}"
                    else "✅ ${if (progress.isUpload) "Upload" else "Download"} complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.error != null)
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFF22C55E)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  OPEN WITH DIALOG
// ─────────────────────────────────────────────────────────────

@Composable
fun PcOpenWithDialog(
    dialog    : com.example.ritik_2.windowscontrol.data.PcOpenWithDialog,
    onSelect  : (com.example.ritik_2.windowscontrol.data.PcOpenWithChoice) -> Unit,
    onDismiss : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Text("🖥️", fontSize = 28.sp) },
        title = {
            Column {
                Text("Open With", fontWeight = FontWeight.Bold)
                Text(
                    dialog.filePath.substringAfterLast('/').substringAfterLast('\\'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "PC is asking which app to use. Select one:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                dialog.choices.forEach { choice ->
                    Surface(
                        onClick = { onSelect(choice) },
                        shape   = RoundedCornerShape(10.dp),
                        color   = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(choice.icon, fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(choice.appName, fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(choice.exePath.substringAfterLast('\\').substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────
//  SMALL SHARED COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
    )
}

@Composable
private fun QuickAccessRow(icon: String, name: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PcDriveCard(drive: PcDrive, onClick: () -> Unit) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("💾", fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text("${drive.letter}:\\ — ${drive.label}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val used = if (drive.totalGb > 0) 1f - (drive.freeGb / drive.totalGb) else 0f
                LinearProgressIndicator(
                    progress = { used },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = when {
                        used > 0.9f -> MaterialTheme.colorScheme.error
                        used > 0.7f -> Color(0xFFF59E0B)
                        else        -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text("${drive.freeGb.toInt()} GB free of ${drive.totalGb.toInt()} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PcFileRow(
    item       : PcFileItem,
    icon       : String,
    onClick    : (() -> Unit)?,
    onOpen     : (() -> Unit)?,       // execute mode
    onDownload : (() -> Unit)?        // transfer mode
) {
    Card(
        onClick  = { onClick?.invoke() ?: onOpen?.invoke() ?: onDownload?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (item.isDir)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isDir) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!item.isDir && item.sizeKb > 0) {
                    val sz = when {
                        item.sizeKb > 1024 * 1024 -> "${item.sizeKb / (1024 * 1024)} GB"
                        item.sizeKb > 1024         -> "${item.sizeKb / 1024} MB"
                        else                        -> "${item.sizeKb} KB"
                    }
                    Text(sz, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                item.isDir -> Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
                onOpen != null -> FilledTonalButton(
                    onClick        = onOpen,
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
                onDownload != null -> OutlinedButton(
                    onClick        = onDownload,
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun PcRecentPathRow(recent: PcRecentPath, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(recent.icon, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(recent.label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(recent.path, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(18.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PcEmptyBrowse(message: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}