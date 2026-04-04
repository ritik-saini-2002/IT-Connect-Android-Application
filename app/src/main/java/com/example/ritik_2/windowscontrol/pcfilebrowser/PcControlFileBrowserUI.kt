package com.example.ritik_2.windowscontrol.pcfilebrowser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.FileBrowserMode
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus

// ── UI State ──────────────────────────────────────────────────────────────────

data class FileBrowserUiState(
    val drives           : List<PcDrive>       = emptyList(),
    val currentPath      : String              = "",
    val dirItems         : List<PcFileItem>    = emptyList(),
    val isLoading        : Boolean             = false,
    val recentPaths      : List<PcRecentPath>  = emptyList(),
    val specialFolders   : List<PcRecentPath>  = emptyList(),
    val browserMode      : FileBrowserMode     = FileBrowserMode.EXECUTE,
    val transferProgress : PcTransferProgress? = null,
    val connectionStatus : PcConnectionStatus  = PcConnectionStatus.UNKNOWN,
    val openingFileName  : String?             = null,
    val selectedFilter   : PcFileFilter        = PcFileFilter.ALL,
    val level            : BrowserLevel        = BrowserLevel.Root,
    val openWithDialog   : PcOpenWithDialog?   = null,
)

// ── Nav level ─────────────────────────────────────────────────────────────────

sealed class BrowserLevel {
    object Root                                               : BrowserLevel()
    data class Drive(val drive: PcDrive)                     : BrowserLevel()
    data class Directory(val path: String, val label: String) : BrowserLevel()
}

val BrowserLevel.depth: Int get() = when (this) {
    is BrowserLevel.Root      -> 0
    is BrowserLevel.Drive     -> 1
    is BrowserLevel.Directory -> 2
}

// ── Callbacks ─────────────────────────────────────────────────────────────────

data class FileBrowserCallbacks(
    val onDriveClick         : (PcDrive) -> Unit,
    val onFolderClick        : (PcFileItem) -> Unit,
    val onSpecialFolderClick : (path: String, name: String) -> Unit,
    val onRecentClick        : (PcRecentPath) -> Unit,
    val onFileOpen           : (PcFileItem) -> Unit,
    val onFileDownload       : (PcFileItem) -> Unit,
    val onFolderAction       : (item: PcFileItem, action: FolderAction) -> Unit,
    val onFilterChange       : (PcFileFilter) -> Unit,
    val onPing               : () -> Unit,
    val onUpload             : () -> Unit,
    val onRefresh            : () -> Unit,
    val onBreadcrumbNav      : (BrowserLevel) -> Unit,
    val onDismissTransfer    : () -> Unit,
    val onOpenWithSelect     : (PcOpenWithChoice) -> Unit,
    val onDismissOpenWith    : () -> Unit,
)

enum class FolderAction { DOWNLOAD, DELETE, PROPERTIES, MOVE }

// ── Root composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlFileBrowserUI(
    state    : FileBrowserUiState,
    callbacks: FileBrowserCallbacks
) {
    // FAB drag state
    var fabOffset by remember { mutableStateOf(Offset(16f, 200f)) }
    var screenW   by remember { mutableIntStateOf(0) }
    var screenH   by remember { mutableIntStateOf(0) }
    val density   = LocalDensity.current
    val fabSizePx = with(density) { 56.dp.toPx() }

    // Context menu
    var contextItem by remember { mutableStateOf<PcFileItem?>(null) }

    // Pull-to-refresh via scroll indicator
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                screenW = it.size.width
                screenH = it.size.height
            }
    ) {
        Scaffold(
            topBar = {
                FileBrowserTopBar(
                    level            = state.level,
                    currentPath      = state.currentPath,
                    isLoading        = state.isLoading || isRefreshing,
                    browserMode      = state.browserMode,
                    connectionStatus = state.connectionStatus,
                    onPing           = callbacks.onPing
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Transfer banner
                state.transferProgress?.let {
                    TransferProgressBanner(it, callbacks.onDismissTransfer)
                }

                // Opening indicator
                state.openingFileName?.let { name ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            Modifier.padding(5.dp, 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                            Text("Opening: $name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // Filter chips
                if (state.currentPath.isNotEmpty()) {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(PcFileFilter.entries) { f ->
                            FilterChip(
                                selected = state.selectedFilter == f,
                                onClick  = { callbacks.onFilterChange(f) },
                                label    = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Breadcrumb
                if (state.level !is BrowserLevel.Root) {
                    FileBreadcrumbBar(state.level, state.drives, callbacks.onBreadcrumbNav)
                }

                // Swipe-to-refresh wrapper using pointer input on a Box
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (isRefreshing) {
                                        callbacks.onRefresh()
                                        isRefreshing = false
                                    }
                                }
                            ) { _, dragAmount ->
                                // Only trigger refresh when at top and dragging down
                                if (dragAmount > 40f && !listState.canScrollBackward) {
                                    isRefreshing = true
                                }
                            }
                        }
                ) {
                    // Refresh indicator
                    if (isRefreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }

                    AnimatedContent(
                        targetState = state.level,
                        transitionSpec = {
                            val fwd = targetState.depth > initialState.depth
                            val enter = if (fwd) slideInHorizontally { it } + fadeIn()
                            else     slideInHorizontally { -it } + fadeIn()
                            val exit  = if (fwd) slideOutHorizontally { -it } + fadeOut()
                            else     slideOutHorizontally { it } + fadeOut()
                            enter togetherWith exit
                        },
                        label = "browser"
                    ) { lvl ->
                        when (lvl) {
                            is BrowserLevel.Root ->
                                RootView(
                                    drives               = state.drives,
                                    recentPaths          = state.recentPaths,
                                    specialFolders       = state.specialFolders,
                                    isLoading            = state.isLoading,
                                    listState            = listState,
                                    onDriveClick         = callbacks.onDriveClick,
                                    onRecentClick        = callbacks.onRecentClick,
                                    onSpecialFolderClick = callbacks.onSpecialFolderClick
                                )
                            is BrowserLevel.Drive,
                            is BrowserLevel.Directory ->
                                FileListView(
                                    dirItems          = state.dirItems,
                                    isLoading         = state.isLoading,
                                    browserMode       = state.browserMode,
                                    listState         = listState,
                                    onFolderClick     = callbacks.onFolderClick,
                                    onFolderLongPress = { item -> contextItem = item },
                                    onFileOpen        = callbacks.onFileOpen,
                                    onFileDownload    = callbacks.onFileDownload
                                )
                        }
                    }
                }
            }
        }

        // Draggable upload FAB
        Box(
            Modifier
                .offset { IntOffset(fabOffset.x.toInt(), fabOffset.y.toInt()) }
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap to nearest horizontal edge
                            val cx = fabOffset.x + fabSizePx / 2
                            fabOffset = if (cx < screenW / 2f)
                                fabOffset.copy(x = 16f)
                            else
                                fabOffset.copy(x = screenW - fabSizePx - 16f)
                        }
                    ) { _, drag ->
                        fabOffset = Offset(
                            x = (fabOffset.x + drag.x).coerceIn(0f, screenW - fabSizePx),
                            y = (fabOffset.y + drag.y).coerceIn(0f, screenH - fabSizePx)
                        )
                    }
                }
                .clickable { callbacks.onUpload() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Upload, "Upload", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        // Folder long-press context menu
        contextItem?.let { item ->
            FolderContextMenu(
                item      = item,
                onAction  = { action -> callbacks.onFolderAction(item, action); contextItem = null },
                onDismiss = { contextItem = null }
            )
        }
    }

    // Open-With dialog
    state.openWithDialog?.let { dlg ->
        PcOpenWithDialog(
            dialog    = dlg,
            onSelect  = callbacks.onOpenWithSelect,
            onDismiss = callbacks.onDismissOpenWith
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
fun FileBrowserTopBar(
    level           : BrowserLevel,
    currentPath     : String,
    isLoading       : Boolean,
    browserMode     : FileBrowserMode,
    connectionStatus: PcConnectionStatus,
    onPing          : () -> Unit
) {
    val title = when (level) {
        is BrowserLevel.Root      -> "File Browser"
        is BrowserLevel.Drive     -> "${level.drive.letter}:\\ ${level.drive.label}"
        is BrowserLevel.Directory -> level.label.ifBlank { "Files" }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF1E88E5))))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Level icon
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (level) {
                        is BrowserLevel.Root      -> Icons.Default.Computer
                        is BrowserLevel.Drive     -> Icons.Default.Storage
                        is BrowserLevel.Directory -> Icons.Default.Folder
                    },
                    null, tint = Color.White, modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))

            // Title + path
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        title,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(0.2f)
                    ) {
                        Text(
                            if (browserMode == FileBrowserMode.EXECUTE) "EXEC" else "TRANSFER",
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
                if (currentPath.isNotEmpty()) {
                    Text(
                        currentPath,
                        fontSize = 10.sp,
                        color    = Color.White.copy(0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Connection chip
            val (chipColor, chipLabel) = when (connectionStatus) {
                PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
                PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
                PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
                PcConnectionStatus.UNKNOWN  -> Color.White.copy(0.6f) to "Ping"
            }
            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = chipColor.copy(0.2f),
                border  = BorderStroke(1.dp, chipColor.copy(0.5f))
            ) {
                Text(
                    "● $chipLabel",
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            }

            if (isLoading) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(
                    Modifier.size(15.dp),
                    color       = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

// ── Breadcrumb ────────────────────────────────────────────────────────────────

@Composable
fun FileBreadcrumbBar(
    level     : BrowserLevel,
    drives    : List<PcDrive>,
    onNavigate: (BrowserLevel) -> Unit
) {
    val crumbs: List<Pair<String, BrowserLevel>> = buildList {
        add("This PC" to BrowserLevel.Root)
        when (level) {
            is BrowserLevel.Drive ->
                add("${level.drive.letter}:\\" to level)
            is BrowserLevel.Directory -> {
                val drive = drives.find { level.path.startsWith("${it.letter}:") }
                if (drive != null)
                    add("${drive.letter}:\\" to BrowserLevel.Drive(drive))
                val parts = level.path.trimEnd('/', '\\')
                    .split('/', '\\').filter { it.isNotBlank() }
                parts.drop(1).forEachIndexed { idx, seg ->
                    val partial = parts.take(idx + 2).joinToString("\\")
                        .let { if (!it.contains(':')) "${drive?.letter}:\\$it" else it }
                    val target  = if (idx == parts.size - 2) level
                    else BrowserLevel.Directory(partial, seg)
                    add(seg to target)
                }
            }
            else -> {}
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { i, (label, target) ->
            val isLast = i == crumbs.lastIndex
            Text(
                label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color      = if (isLast) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier   = Modifier.clickable(enabled = !isLast) { onNavigate(target) }
            )
            if (!isLast) Icon(
                Icons.Default.ChevronRight, null,
                Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
            )
        }
    }
}

// ── Root view ─────────────────────────────────────────────────────────────────

@Composable
fun RootView(
    drives              : List<PcDrive>,
    recentPaths         : List<PcRecentPath>,
    specialFolders      : List<PcRecentPath>,
    isLoading           : Boolean,
    listState           : LazyListState,
    onDriveClick        : (PcDrive) -> Unit,
    onRecentClick       : (PcRecentPath) -> Unit,
    onSpecialFolderClick: (path: String, name: String) -> Unit
) {
    if (isLoading && drives.isEmpty()) { BrowserLoadingView(); return }
    LazyColumn(
        state               = listState,
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        val recentFiles = recentPaths.filter { !it.isApp }
        if (recentFiles.isNotEmpty()) {
            item { BrowserSectionHeader("Recently Used", "${recentFiles.size}") }
            items(recentFiles, key = { "r_${it.path}" }) { r ->
                CompactFolderCard(
                    icon        = Icons.Default.History,
                    iconColor   = Color(0xFF7B1FA2),
                    title       = r.label,
                    onClick     = { onRecentClick(r) },
                    onLongPress = null
                )
            }
        }
        if (specialFolders.isNotEmpty()) {
            item { BrowserSectionHeader("Quick Access", "${specialFolders.size}") }
            items(specialFolders, key = { "sf_${it.path}" }) { f ->
                CompactFolderCard(
                    icon        = Icons.Default.BookmarkBorder,
                    iconColor   = Color(0xFF0288D1),
                    title       = f.label,
                    onClick     = { onSpecialFolderClick(f.path, f.label) },
                    onLongPress = null
                )
            }
        }
        if (drives.isEmpty() && !isLoading) {
            item { BrowserEmptyView(Icons.Default.Storage, "No drives found.\nEnsure agent is running.") }
        } else {
            item { BrowserSectionHeader("Drives", "${drives.size}") }
            items(drives, key = { it.letter }) { d ->
                CompactDriveCard(drive = d, onClick = { onDriveClick(d) })
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ── File list view ────────────────────────────────────────────────────────────

@Composable
fun FileListView(
    dirItems         : List<PcFileItem>,
    isLoading        : Boolean,
    browserMode      : FileBrowserMode,
    listState        : LazyListState,
    onFolderClick    : (PcFileItem) -> Unit,
    onFolderLongPress: (PcFileItem) -> Unit,
    onFileOpen       : (PcFileItem) -> Unit,
    onFileDownload   : (PcFileItem) -> Unit
) {
    val folders = dirItems.filter { it.isDir }
    val files   = dirItems.filter { !it.isDir }

    if (isLoading && dirItems.isEmpty()) { BrowserLoadingView(); return }
    if (!isLoading && folders.isEmpty() && files.isEmpty()) {
        BrowserEmptyView(Icons.Default.FolderOpen, "This folder is empty")
        return
    }

    LazyColumn(
        state               = listState,
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (folders.isNotEmpty()) {
            item { BrowserSectionHeader("Folders", "${folders.size}") }
            items(folders, key = { "d_${it.path}" }) { folder ->
                CompactFolderCard(
                    icon        = Icons.Default.Folder,
                    iconColor   = Color(0xFFF57C00),
                    title       = folder.name,
                    onClick     = { onFolderClick(folder) },
                    onLongPress = { onFolderLongPress(folder) }
                )
            }
        }
        if (files.isNotEmpty()) {
            item { BrowserSectionHeader("Files", "${files.size}") }
            items(files, key = { "f_${it.path}" }) { file ->
                CompactFileCard(
                    file        = file,
                    icon        = fileIcon(file.extension),
                    browserMode = browserMode,
                    onOpen      = { onFileOpen(file) },
                    onDownload  = { onFileDownload(file) }
                )
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ── Compact drive card ────────────────────────────────────────────────────────

@Composable
private fun CompactDriveCard(drive: PcDrive, onClick: () -> Unit) {
    val used      = if (drive.totalGb > 0) 1f - (drive.freeGb / drive.totalGb) else 0f
    val usedColor = when {
        used > 0.9f -> Color(0xFFD32F2F)
        used > 0.7f -> Color(0xFFF57C00)
        else        -> Color(0xFF1565C0)
    }
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(56.dp),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                    .background(usedColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Storage, null, tint = usedColor, modifier = Modifier.size(19.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    "${drive.letter}:\\ ${drive.label}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    maxLines   = 1
                )
                LinearProgressIndicator(
                    progress = { used },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp),
                    color    = usedColor
                )
            }
            Text(
                "${drive.freeGb.toInt()}GB free",
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.Default.ChevronRight, null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

// ── Compact folder card ───────────────────────────────────────────────────────

@Composable
fun CompactFolderCard(
    icon       : ImageVector,
    iconColor  : Color,
    title      : String,
    onClick    : () -> Unit,
    onLongPress: (() -> Unit)?
) {
    var pressed by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(
        targetValue    = if (pressed) 0.97f else 1f,
        animationSpec  = spring(stiffness = Spring.StiffnessMedium),
        label          = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress     = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap       = { onClick() },
                    onLongPress = { onLongPress?.invoke() }
                )
            },
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    .background(iconColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) }
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize   = 13.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

// ── Compact file card ─────────────────────────────────────────────────────────

@Composable
private fun CompactFileCard(
    file       : PcFileItem,
    icon       : String,
    browserMode: FileBrowserMode,
    onOpen     : () -> Unit,
    onDownload : () -> Unit
) {
    val action = if (browserMode == FileBrowserMode.EXECUTE) onOpen else onDownload
    Card(
        onClick   = action,
        modifier  = Modifier.fillMaxWidth().height(52.dp),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text(file.name,
                    fontSize   = 13.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium)
                if (file.sizeKb > 0)
                    Text(formatSize(file.sizeKb),
                        fontSize = 10.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick        = action,
                shape          = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier       = Modifier.height(30.dp)
            ) {
                Icon(
                    if (browserMode == FileBrowserMode.EXECUTE) Icons.AutoMirrored.Filled.OpenInNew
                    else Icons.Default.Download,
                    null, Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    if (browserMode == FileBrowserMode.EXECUTE) "Open" else "Save",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ── Folder context menu ───────────────────────────────────────────────────────

@Composable
fun FolderContextMenu(
    item     : PcFileItem,
    onAction : (FolderAction) -> Unit,
    onDismiss: () -> Unit
) {
    val menuItems = listOf(
        Triple(FolderAction.DOWNLOAD,   Icons.Default.Download,                    "Download Folder"),
        Triple(FolderAction.DELETE,     Icons.Default.Delete,                      "Delete Folder"),
        Triple(FolderAction.PROPERTIES, Icons.Default.Info,                        "Properties"),
        Triple(FolderAction.MOVE,       Icons.AutoMirrored.Filled.DriveFileMove,   "Move To…")
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Popup(
        onDismissRequest = onDismiss,
        properties       = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = scaleIn(
                initialScale    = 0.88f,
                transformOrigin = TransformOrigin(0f, 0f),
                animationSpec   = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(150)),
            exit    = scaleOut(transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(tween(100))
        ) {
            Surface(
                shape           = RoundedCornerShape(16.dp),
                tonalElevation  = 8.dp,
                shadowElevation = 12.dp,
                modifier        = Modifier.width(220.dp)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    // Header
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Folder, null,
                            tint     = Color(0xFFF57C00),
                            modifier = Modifier.size(17.dp))
                        Text(item.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(4.dp))
                    menuItems.forEach { (action, icon, label) ->
                        val isDestructive = action == FolderAction.DELETE
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAction(action) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, null,
                                tint     = if (isDestructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                            Text(label,
                                fontSize = 13.sp,
                                color    = if (isDestructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ── Transfer progress banner ──────────────────────────────────────────────────

@Composable
fun TransferProgressBanner(progress: PcTransferProgress, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        color          = if (progress.isUpload) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (progress.isUpload) Icons.Default.Upload else Icons.Default.Download,
                        null, Modifier.size(14.dp))
                    Text(progress.fileName.take(28),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (progress.isDone)
                    IconButton(onDismiss, Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(12.dp))
                    }
            }
            if (!progress.isDone) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator({ progress.progressFraction },
                    Modifier.fillMaxWidth().height(3.dp))
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress.progressFraction * 100).toInt()}%  ${progress.sizeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (progress.speedBps > 0)
                        Text("${progress.speedLabel}  ${progress.etaLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    if (progress.error != null) "❌ ${progress.error}"
                    else "✅ ${if (progress.isUpload) "Upload" else "Download"} complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.error != null) MaterialTheme.colorScheme.error
                    else Color(0xFF22C55E)
                )
            }
        }
    }
}

// ── Open With dialog ──────────────────────────────────────────────────────────

@Composable
fun PcOpenWithDialog(
    dialog   : PcOpenWithDialog,
    onSelect : (PcOpenWithChoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Text("🖥️", fontSize = 22.sp) },
        title = {
            Column {
                Text("Open With", fontWeight = FontWeight.Bold)
                Text(
                    dialog.filePath.substringAfterLast('/').substringAfterLast('\\'),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dialog.choices.forEach { c ->
                    Surface(
                        onClick  = { onSelect(c) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(11.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(c.icon, fontSize = 20.sp)
                            Text(c.appName,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
fun BrowserLoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF1565C0))
            Spacer(Modifier.height(10.dp))
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BrowserEmptyView(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
            Spacer(Modifier.height(10.dp))
            Text(message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BrowserSectionHeader(title: String, count: String) {
    Row(
        Modifier.padding(bottom = 4.dp, top = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("· $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun fileIcon(ext: String): String = when (ext.lowercase()) {
    "mp4", "mkv", "avi", "mov", "wmv"        -> "🎬"
    "mp3", "wav", "flac", "aac", "m4a"        -> "🎵"
    "jpg", "jpeg", "png", "gif", "bmp", "webp"-> "🖼️"
    "pdf"                                      -> "📕"
    "doc", "docx", "rtf"                       -> "📘"
    "xls", "xlsx", "csv"                       -> "📗"
    "ppt", "pptx"                              -> "📊"
    "txt", "log", "md"                         -> "📄"
    "zip", "rar", "7z", "tar", "gz"            -> "🗜️"
    "py", "bat", "ps1", "sh", "cmd"            -> "⚙️"
    "exe", "msi"                               -> "🖥️"
    else                                        -> "📄"
}

fun formatSize(kb: Long): String = when {
    kb > 1024 * 1024 -> "${kb / (1024 * 1024)} GB"
    kb > 1024        -> "${kb / 1024} MB"
    else             -> "$kb KB"
}