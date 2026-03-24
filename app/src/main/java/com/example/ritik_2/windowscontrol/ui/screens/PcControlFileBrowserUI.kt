package com.example.ritik_2.windowscontrol.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  PcControlFileBrowserUI v3
//  Fixes applied:
//  - LaunchedEffect loads drives/special folders/recent on entry
//  - rememberCoroutineScope() replaces MainScope() leak
//  - PcFileFilter.entries replaces deprecated .values()
//  - specialFolders Quick Access section added to root view
//  - File type smart routing unchanged
// ─────────────────────────────────────────────────────────────

// Map file extensions → how to open them on PC
private fun getOpenCommand(file: PcFileItem): PcStep {
    val ext = file.extension.lowercase()
    return when {
        // ── Open media files with VLC (or default player) ──
        ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flac", "mp3", "wav", "m4a", "aac") ->
            PcStep(
                "LAUNCH_APP",
                value = "vlc.exe",
                args  = listOf(file.path)
            )

        // ── Open images with default viewer ──
        ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "ico", "svg") ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))

        // ── Open Word docs with Word ──
        ext in listOf("doc", "docx", "rtf", "odt") ->
            PcStep(
                "LAUNCH_APP",
                value = "WINWORD.EXE",
                args  = listOf(file.path)
            )

        // ── Open Excel ──
        ext in listOf("xls", "xlsx", "csv", "ods") ->
            PcStep(
                "LAUNCH_APP",
                value = "EXCEL.EXE",
                args  = listOf(file.path)
            )

        // ── Open PowerPoint ──
        ext in listOf("ppt", "pptx", "odp") ->
            PcStep(
                "LAUNCH_APP",
                value = "POWERPNT.EXE",
                args  = listOf(file.path)
            )

        // ── Open PDF with default PDF reader ──
        ext == "pdf" ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))

        // ── Open text/code files with Notepad ──
        ext in listOf("txt", "log", "ini", "cfg", "xml", "json", "yaml", "yml", "md") ->
            PcStep(
                "LAUNCH_APP",
                value = "notepad.exe",
                args  = listOf(file.path)
            )

        // ── Open scripts with Notepad ──
        ext in listOf("py", "bat", "ps1", "sh", "cmd") ->
            PcStep(
                "LAUNCH_APP",
                value = "notepad.exe",
                args  = listOf(file.path)
            )

        // ── Default: open with whatever Windows associates ──
        else ->
            PcStep("SYSTEM_CMD", "OPEN_FILE", args = listOf(file.path))
    }
}

private fun getFileIcon(ext: String): String = when (ext.lowercase()) {
    in listOf("mp4", "mkv", "avi", "mov", "wmv")        -> "🎬"
    in listOf("mp3", "wav", "flac", "aac", "m4a")        -> "🎵"
    in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> "🖼️"
    in listOf("pdf")                                      -> "📕"
    in listOf("doc", "docx", "rtf")                       -> "📘"
    in listOf("xls", "xlsx", "csv")                       -> "📗"
    in listOf("ppt", "pptx")                              -> "📊"
    in listOf("txt", "log", "md")                         -> "📄"
    in listOf("zip", "rar", "7z", "tar", "gz")            -> "🗜️"
    in listOf("py", "bat", "ps1", "sh", "cmd")            -> "⚙️"
    in listOf("exe", "msi")                               -> "🖥️"
    else                                                  -> "📄"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlFileBrowserUI(viewModel: PcControlViewModel) {

    val drives         by viewModel.drives.collectAsStateWithLifecycle()
    val currentPath    by viewModel.currentPath.collectAsStateWithLifecycle()
    val dirItems       by viewModel.dirItems.collectAsStateWithLifecycle()
    val isLoading      by viewModel.browseLoading.collectAsStateWithLifecycle()
    val recentPaths    by viewModel.recentPaths.collectAsStateWithLifecycle()
    val specialFolders by viewModel.specialFolders.collectAsStateWithLifecycle() // FIX 1: added
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(PcFileFilter.ALL) }
    var openingFile    by remember { mutableStateOf<String?>(null) }

    // FIX 2: managed scope — replaces MainScope() leak
    val scope = rememberCoroutineScope()

    // FIX 3: load data on first entry — screen was blank without this
    LaunchedEffect(Unit) {
        viewModel.loadDrives() // loads drives + special folders + recent paths
    }

    // Back navigation
    BackHandler(enabled = currentPath.isNotEmpty()) {
        viewModel.navigateUp()
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
                        Text(
                            "📁 File Browser",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
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
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.browseDir(currentPath, selectedFilter)
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            Column(modifier = Modifier.fillMaxSize()) {

                // Error banner from uiState
                // (shows inline instead of crashing)

                // Filter chips (only when inside a directory)
                if (currentPath.isNotEmpty()) {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // FIX 4: .entries replaces deprecated .values()
                        items(PcFileFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick  = {
                                    selectedFilter = filter
                                    viewModel.browseDir(currentPath, filter)
                                },
                                label = {
                                    Text(
                                        filter.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                // File opening indicator
                if (openingFile != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier              = Modifier.padding(12.dp, 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Opening: ${openingFile
                                    ?.substringAfterLast("/")
                                    ?.substringAfterLast("\\")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Main content
                LazyColumn(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxSize()
                ) {

                    if (currentPath.isEmpty()) {
                        // ── ROOT VIEW ─────────────────────────────────

                        // Recent paths
                        if (recentPaths.isNotEmpty()) {
                            item {
                                Text(
                                    "🕐 RECENTLY USED",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(recentPaths.filter { !it.isApp }) { recent ->
                                PcRecentPathRow(
                                    recent  = recent,
                                    onClick = { viewModel.browseDir(recent.path) }
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        // FIX 5: Quick Access / Special Folders section
                        if (specialFolders.isNotEmpty()) {
                            item {
                                Text(
                                    "📌 QUICK ACCESS",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(specialFolders, key = { "sf_${it.path}" }) { folder ->
                                Surface(
                                    onClick  = { viewModel.browseDir(folder.path) },
                                    shape    = RoundedCornerShape(10.dp),
                                    color    = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier              = Modifier.padding(12.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(folder.icon, fontSize = 20.sp)
                                        Text(
                                            folder.name,
                                            style      = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier   = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.ChevronRight, null,
                                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        // Drives
                        item {
                            Text(
                                "💾 DRIVES",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (drives.isEmpty() && !isLoading) {
                            item {
                                PcEmptyBrowse("No drives found.\nMake sure agent_v3.py is running.")
                            }
                        } else {
                            items(drives, key = { it.letter }) { drive ->
                                PcDriveCard(
                                    drive   = drive,
                                    onClick = { viewModel.browseDir("${drive.letter}:/") }
                                )
                            }
                        }

                    } else {
                        // ── DIRECTORY CONTENTS ────────────────────────

                        val folders = dirItems.filter { it.isDir }
                        val files   = dirItems.filter { !it.isDir }

                        if (folders.isEmpty() && files.isEmpty() && !isLoading) {
                            item { PcEmptyBrowse("This folder is empty") }
                        }

                        if (folders.isNotEmpty()) {
                            item {
                                Text(
                                    "📂 FOLDERS (${folders.size})",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(folders, key = { "d_${it.path}" }) { folder ->
                                PcFileRow(
                                    item    = folder,
                                    icon    = "📂",
                                    onClick = { viewModel.browseDir(folder.path, selectedFilter) },
                                    onOpen  = null
                                )
                            }
                        }

                        if (files.isNotEmpty()) {
                            item {
                                Text(
                                    "📄 FILES (${files.size})",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(files, key = { "f_${it.path}" }) { file ->
                                PcFileRow(
                                    item    = file,
                                    icon    = getFileIcon(file.extension),
                                    onClick = null,
                                    // FIX 6: scope.launch instead of MainScope().launch
                                    onOpen  = {
                                        openingFile = file.path
                                        viewModel.executeQuickStep(getOpenCommand(file))
                                        scope.launch {
                                            delay(2000)
                                            openingFile = null
                                        }
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
}

// ─────────────────────────────────────────────────────────────
//  DRIVE CARD
// ─────────────────────────────────────────────────────────────

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
                Text(
                    "${drive.letter}:\\ — ${drive.label}",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                val usedFraction = if (drive.totalGb > 0)
                    1f - (drive.freeGb / drive.totalGb) else 0f
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = when {
                        usedFraction > 0.9f -> MaterialTheme.colorScheme.error
                        usedFraction > 0.7f -> Color(0xFFF59E0B)
                        else                -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${drive.freeGb.toInt()} GB free of ${drive.totalGb.toInt()} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  FILE / FOLDER ROW
// ─────────────────────────────────────────────────────────────

@Composable
fun PcFileRow(
    item   : PcFileItem,
    icon   : String,
    onClick: (() -> Unit)?,
    onOpen : (() -> Unit)?
) {
    Card(
        onClick  = { onClick?.invoke() ?: onOpen?.invoke() },
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
                Text(
                    item.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isDir) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (!item.isDir && item.sizeKb > 0) {
                    val size = when {
                        item.sizeKb > 1024 * 1024 -> "${item.sizeKb / (1024 * 1024)} GB"
                        item.sizeKb > 1024         -> "${item.sizeKb / 1024} MB"
                        else                        -> "${item.sizeKb} KB"
                    }
                    Text(
                        size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.isDir) {
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else if (onOpen != null) {
                FilledTonalButton(
                    onClick        = onOpen,
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  RECENT PATH ROW
// ─────────────────────────────────────────────────────────────

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
                Text(
                    recent.label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    recent.path,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.size(18.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  EMPTY STATE
// ─────────────────────────────────────────────────────────────

@Composable
fun PcEmptyBrowse(message: String) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}