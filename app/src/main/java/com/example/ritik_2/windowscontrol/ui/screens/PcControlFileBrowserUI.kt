package com.example.ritik_2.windowscontrol.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        else ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
    }
}

private fun getFileIcon(ext: String): String = when (ext.lowercase()) {
    in listOf("mp4","mkv","avi","mov","wmv")         -> "🎬"
    in listOf("mp3","wav","flac","aac","m4a")         -> "🎵"
    in listOf("jpg","jpeg","png","gif","bmp","webp")  -> "🖼️"
    in listOf("pdf")                                  -> "📕"
    in listOf("doc","docx","rtf")                     -> "📘"
    in listOf("xls","xlsx","csv")                     -> "📗"
    in listOf("ppt","pptx")                           -> "📊"
    in listOf("txt","log","md")                       -> "📄"
    in listOf("zip","rar","7z","tar","gz")            -> "🗜️"
    in listOf("py","bat","ps1","sh","cmd")            -> "⚙️"
    in listOf("exe","msi")                            -> "🖥️"
    else                                              -> "📄"
}

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
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(PcFileFilter.ALL) }
    var openingFile    by remember { mutableStateOf<String?>(null) }
    val listState      = rememberLazyListState()
    val scope          = rememberCoroutineScope()
    val context        = LocalContext.current

    // Save/restore scroll position
    LaunchedEffect(currentPath) {
        val saved = viewModel.getScrollPosition(currentPath)
        if (saved > 0) {
            try { listState.scrollToItem(saved) } catch (_: Exception) {}
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        viewModel.saveScrollPosition(currentPath, listState.firstVisibleItemIndex)
    }

    LaunchedEffect(Unit) { viewModel.loadDrives() }

    BackHandler(enabled = currentPath.isNotEmpty()) { viewModel.navigateUp() }

    // Upload picker
    var uploadDestPath by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val cr    = context.contentResolver
                val bytes = cr.openInputStream(uri)?.readBytes() ?: return@launch
                val name  = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    if (idx >= 0) cursor.getString(idx) else null
                } ?: "upload_${System.currentTimeMillis()}"
                val dest = uploadDestPath.ifBlank { currentPath }
                viewModel.uploadFile(bytes, name, dest)
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "Upload read error: ${e.message}")
            }
        }
    }

    // Download save picker
    var downloadPath by remember { mutableStateOf("") }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.downloadFile(downloadPath, uri, context.contentResolver)
    }

    // Open With dialog
    val openWithDlg by viewModel.openWithDialog.collectAsStateWithLifecycle()
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
                            Icon(Icons.Default.ArrowBack, "Back",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                title = {
                    Column {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (currentPath.isEmpty()) "Files"
                                else currentPath.substringAfterLast('/').substringAfterLast('\\').take(20).ifEmpty { "Files" },
                                fontWeight = FontWeight.Bold,
                                style      = MaterialTheme.typography.titleMedium,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (browserMode == FileBrowserMode.EXECUTE)
                                    Color.White.copy(0.25f)
                                else Color.White.copy(0.15f)
                            ) {
                                Text(
                                    if (browserMode == FileBrowserMode.EXECUTE) "EXEC" else "TRANSFER",
                                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        if (currentPath.isNotEmpty()) {
                            Text(
                                currentPath,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onPrimary.copy(0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Connection chip
                    val (chipColor, chipLabel) = when (connectionStatus) {
                        com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
                        com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
                        com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "Checking"
                        com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus.UNKNOWN  -> Color.White.copy(0.6f) to "Ping"
                    }
                    Surface(
                        onClick  = { viewModel.pingPc() },
                        shape    = RoundedCornerShape(20.dp),
                        color    = chipColor.copy(0.18f),
                        border   = BorderStroke(1.dp, chipColor.copy(0.5f))
                    ) {
                        Text("● $chipLabel",
                            modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = chipColor)
                    }
                    if (currentPath.isNotEmpty() && browserMode == FileBrowserMode.TRANSFER) {
                        IconButton(onClick = {
                            uploadDestPath = currentPath
                            uploadLauncher.launch("*/*")
                        }) {
                            Icon(Icons.Default.Upload, "Upload",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.browseDir(currentPath, selectedFilter) }) {
                            Icon(Icons.Default.Refresh, "Refresh",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(2.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Transfer progress
            transferProg?.let { prog ->
                TransferProgressBanner(progress = prog, onDismiss = { viewModel.clearTransferProgress() })
            }

            // Opening indicator
            openingFile?.let { name ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(10.dp, 7.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Text("Opening: $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Filter chips — only when inside directory
            if (currentPath.isNotEmpty()) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PcFileFilter.entries) { f ->
                        FilterChip(
                            selected = selectedFilter == f,
                            onClick  = {
                                selectedFilter = f
                                viewModel.browseDir(currentPath, f)
                            },
                            label = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(
                state               = listState,
                contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier            = Modifier.fillMaxSize()
            ) {
                if (currentPath.isEmpty()) {
                    // Root view
                    val recentFiles = recentPaths.filter { !it.isApp }
                    if (recentFiles.isNotEmpty()) {
                        item { FileSectionLabel("🕐 RECENTLY USED") }
                        items(recentFiles, key = { "r_${it.path}" }) { recent ->
                            PcRecentPathRow(recent = recent,
                                onClick = { viewModel.browseDir(recent.path) })
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                    if (specialFolders.isNotEmpty()) {
                        item { FileSectionLabel("📌 QUICK ACCESS") }
                        items(specialFolders, key = { "sf_${it.path}" }) { folder ->
                            SimpleNavRow(icon = folder.icon, name = folder.name) {
                                viewModel.browseDir(folder.path)
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                    item { FileSectionLabel("💾 DRIVES") }
                    if (drives.isEmpty() && !isLoading) {
                        item { PcEmptyBrowse("No drives found.\nMake sure agent is running.") }
                    } else {
                        items(drives, key = { it.letter }) { drive ->
                            PcDriveCard(drive = drive,
                                onClick = { viewModel.browseDir("${drive.letter}:/") })
                        }
                    }
                } else {
                    // Directory contents — computed outside items() to avoid @Composable context error
                    val folders = dirItems.filter { it.isDir }
                    val files   = dirItems.filter { !it.isDir }

                    if (folders.isEmpty() && files.isEmpty() && !isLoading) {
                        item { PcEmptyBrowse("This folder is empty") }
                    }
                    if (folders.isNotEmpty()) {
                        item { FileSectionLabel("📂 FOLDERS (${folders.size})") }
                        items(folders, key = { "d_${it.path}" }) { folder ->
                            FolderRow(folder = folder,
                                onClick = { viewModel.browseDir(folder.path, selectedFilter) })
                        }
                    }
                    if (files.isNotEmpty()) {
                        item { FileSectionLabel("📄 FILES (${files.size})") }
                        items(files, key = { "f_${it.path}" }) { file ->
                            FileRow(
                                file        = file,
                                icon        = getFileIcon(file.extension),
                                browserMode = browserMode,
                                onOpen      = {
                                    openingFile = file.name
                                    viewModel.executeQuickStep(getOpenCommand(file))
                                    viewModel.startOpenWithPolling()
                                    scope.launch { delay(3000); openingFile = null }
                                },
                                onDownload  = {
                                    downloadPath = file.path
                                    downloadLauncher.launch(file.name)
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TRANSFER PROGRESS BANNER
// ─────────────────────────────────────────────────────────────

@Composable
fun TransferProgressBanner(
    progress : PcTransferProgress,
    onDismiss: () -> Unit
) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        color          = if (progress.isUpload)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (progress.isUpload) Icons.Default.Upload else Icons.Default.Download,
                        null, modifier = Modifier.size(15.dp))
                    Text(progress.fileName.take(26),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (progress.isDone) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(13.dp))
                    }
                }
            }
            if (!progress.isDone) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Spacer(Modifier.height(3.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress.progressFraction * 100).toInt()}%  ${progress.sizeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (progress.speedBps > 0) {
                        Text("${progress.speedLabel}  ${progress.etaLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(
                    if (progress.error != null) "❌ ${progress.error}"
                    else "✅ ${if (progress.isUpload) "Upload" else "Download"} complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.error != null)
                        MaterialTheme.colorScheme.error else Color(0xFF22C55E)
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
    dialog   : PcOpenWithDialog,
    onSelect : (PcOpenWithChoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Text("🖥️", fontSize = 26.sp) },
        title = {
            Column {
                Text("Open With", fontWeight = FontWeight.Bold)
                Text(
                    dialog.filePath.substringAfterLast('/').substringAfterLast('\\'),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dialog.choices.forEach { choice ->
                    Surface(
                        onClick  = { onSelect(choice) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier.padding(11.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(choice.icon, fontSize = 20.sp)
                            Text(choice.appName, fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(17.dp))
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
//  SMALL COMPONENTS
// ─────────────────────────────────────────────────────────────

@Composable
private fun FileSectionLabel(text: String) {
    Text(text,
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun SimpleNavRow(icon: String, name: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(11.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 18.sp)
            Text(name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
fun PcDriveCard(drive: PcDrive, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("💾", fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text("${drive.letter}:\\ — ${drive.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                val used = if (drive.totalGb > 0) 1f - (drive.freeGb / drive.totalGb) else 0f
                LinearProgressIndicator(
                    progress = { used },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = when {
                        used > 0.9f -> MaterialTheme.colorScheme.error
                        used > 0.7f -> Color(0xFFF59E0B)
                        else        -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(3.dp))
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
private fun FolderRow(folder: PcFileItem, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("📂", fontSize = 20.sp)
            Text(folder.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FileRow(
    file       : PcFileItem,
    icon       : String,
    browserMode: FileBrowserMode,
    onOpen     : () -> Unit,
    onDownload : () -> Unit
) {
    Surface(
        // Clicking anywhere on a file row triggers the mode action
        onClick  = { if (browserMode == FileBrowserMode.EXECUTE) onOpen() else onDownload() },
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (file.sizeKb > 0) {
                    val sz = when {
                        file.sizeKb > 1024 * 1024 -> "${file.sizeKb / (1024 * 1024)} GB"
                        file.sizeKb > 1024         -> "${file.sizeKb / 1024} MB"
                        else                        -> "${file.sizeKb} KB"
                    }
                    Text(sz, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Action button
            FilledTonalButton(
                onClick        = { if (browserMode == FileBrowserMode.EXECUTE) onOpen() else onDownload() },
                shape          = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (browserMode == FileBrowserMode.EXECUTE) Icons.Default.OpenInNew
                    else Icons.Default.Download,
                    null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (browserMode == FileBrowserMode.EXECUTE) "Open" else "Save",
                    style = MaterialTheme.typography.labelSmall)
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
            modifier              = Modifier.padding(11.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(recent.icon, fontSize = 17.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(recent.label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(recent.path, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.size(17.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PcEmptyBrowse(message: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}