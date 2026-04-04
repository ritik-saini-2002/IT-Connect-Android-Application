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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
// Popup kept for FAB menu only
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.viewmodel.FileBrowserMode
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    val searchQuery      : String              = "",
    val isRefreshing     : Boolean             = false,
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

// ── Item actions ──────────────────────────────────────────────────────────────

enum class ItemAction { DOWNLOAD, DELETE, PROPERTIES, MOVE, RENAME, COPY, PASTE }

// ── Callbacks ─────────────────────────────────────────────────────────────────

data class FileBrowserCallbacks(
    val onDriveClick         : (PcDrive) -> Unit,
    val onFolderClick        : (PcFileItem) -> Unit,
    val onSpecialFolderClick : (path: String, name: String) -> Unit,
    val onRecentClick        : (PcRecentPath) -> Unit,
    val onFileOpen           : (PcFileItem) -> Unit,
    val onFileDownload       : (PcFileItem) -> Unit,
    val onItemAction         : (item: PcFileItem, action: ItemAction) -> Unit,
    val onFilterChange       : (PcFileFilter) -> Unit,
    val onPing               : () -> Unit,
    val onUpload             : () -> Unit,
    val onUploadFolder       : () -> Unit,
    val onRefresh            : () -> Unit,
    val onBreadcrumbNav      : (BrowserLevel) -> Unit,
    val onDismissTransfer    : () -> Unit,
    val onOpenWithSelect     : (PcOpenWithChoice) -> Unit,
    val onDismissOpenWith    : () -> Unit,
    val onSearchChange       : (String) -> Unit,
    val onNavigateBack       : () -> Unit,
)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun PcControlFileBrowserUI(
    state    : FileBrowserUiState,
    callbacks: FileBrowserCallbacks
) {
    val scope = rememberCoroutineScope()

    // FAB — starts bottom-right
    var screenW   by remember { mutableIntStateOf(1080) }
    var screenH   by remember { mutableIntStateOf(1920) }
    val density   = LocalDensity.current
    val fabSizePx = with(density) { 52.dp.toPx() }
    val fabPadPx  = with(density) { 16.dp.toPx() }
    var showFabMenu by remember { mutableStateOf(false) }

    // Context menu
    var contextItem       by remember { mutableStateOf<PcFileItem?>(null) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    // Dialogs
    var moveSourceItem by remember { mutableStateOf<PcFileItem?>(null) }
    var renameItem     by remember { mutableStateOf<PcFileItem?>(null) }
    var renameValue    by remember { mutableStateOf("") }
    var deleteItem     by remember { mutableStateOf<PcFileItem?>(null) }
    var propertiesItem by remember { mutableStateOf<PcFileItem?>(null) }

    // Swipe-to-refresh drag
    val listState       = rememberLazyListState()
    var refreshDrag     by remember { mutableStateOf(0f) }
    val refreshThresh   = with(density) { 72.dp.toPx() }
    val refreshProgress = (refreshDrag / refreshThresh).coerceIn(0f, 1f)

    val isSearching  = state.searchQuery.isNotBlank()
    val searchQuery  = state.searchQuery

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
                    isLoading        = state.isLoading || state.isRefreshing,
                    browserMode      = state.browserMode,
                    connectionStatus = state.connectionStatus,
                    searchQuery      = state.searchQuery,
                    onPing           = callbacks.onPing,
                    onSearchChange   = callbacks.onSearchChange,
                    onNavigateBack   = callbacks.onNavigateBack,
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {

                // Transfer banner
                state.transferProgress?.let {
                    TransferProgressBanner(it, callbacks.onDismissTransfer)
                }

                // Opening indicator
                state.openingFileName?.let { name ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            Modifier.padding(8.dp, 5.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 2.dp)
                            Text("Opening: $name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // Filter chips + search icon toggle
                if (state.currentPath.isNotEmpty() && !isSearching) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            contentPadding        = PaddingValues(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier              = Modifier.weight(1f)
                        ) {
                            items(PcFileFilter.entries) { f ->
                                FilterChip(
                                    selected = state.selectedFilter == f,
                                    onClick  = { callbacks.onFilterChange(f) },
                                    label    = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        IconButton(onClick = { callbacks.onSearchChange(" ") }) {
                            Icon(Icons.Default.Search, "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Search bar — shown below filters in place of the path area
                AnimatedVisibility(
                    visible = isSearching,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value         = searchQuery.trim(),
                        onValueChange = callbacks.onSearchChange,
                        placeholder   = { Text("Search files & folders…") },
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = {
                            IconButton(onClick = { callbacks.onSearchChange("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        },
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        shape      = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                        )
                    )
                }

                // Breadcrumb
                if (state.level !is BrowserLevel.Root && !isSearching) {
                    FileBreadcrumbBar(state.level, state.drives, callbacks.onBreadcrumbNav)
                }

                // Pull-to-refresh bar
                AnimatedVisibility(
                    visible = refreshProgress > 0f || state.isRefreshing,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    LinearProgressIndicator(
                        progress  = { if (state.isRefreshing) 1f else refreshProgress },
                        modifier  = Modifier.fillMaxWidth().height(3.dp),
                        color     = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // Main content with swipe-to-refresh
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(listState) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (refreshDrag >= refreshThresh && !state.isRefreshing) {
                                        callbacks.onRefresh()
                                    }
                                    scope.launch {
                                        animate(refreshDrag, 0f) { v, _ -> refreshDrag = v }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        animate(refreshDrag, 0f) { v, _ -> refreshDrag = v }
                                    }
                                }
                            ) { _, dragAmount ->
                                if (dragAmount > 0f && !listState.canScrollBackward && !state.isRefreshing) {
                                    refreshDrag = (refreshDrag + dragAmount * 0.5f)
                                        .coerceIn(0f, refreshThresh * 1.2f)
                                }
                            }
                        }
                ) {
                    AnimatedContent(
                        targetState   = state.level,
                        transitionSpec = {
                            // RIGHT→LEFT entering folder, LEFT→RIGHT going back
                            val fwd = targetState.depth > initialState.depth
                            val enter = if (fwd)
                                slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } +
                                        fadeIn(tween(180))
                            else
                                slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it } +
                                        fadeIn(tween(180))
                            val exit = if (fwd)
                                slideOutHorizontally(tween(200)) { -(it / 3) } + fadeOut(tween(180))
                            else
                                slideOutHorizontally(tween(200)) { (it / 3) } + fadeOut(tween(180))
                            enter togetherWith exit
                        },
                        label = "browser"
                    ) { lvl ->
                        when {
                            isSearching -> SearchResultsView(
                                query            = state.searchQuery,
                                allItems         = state.dirItems,
                                browserMode      = state.browserMode,
                                listState        = listState,
                                onFolderClick    = callbacks.onFolderClick,
                                onFolderLongPress = { item, offset ->
                                    contextItem       = item
                                    contextMenuOffset = offset
                                },
                                onFileOpen       = callbacks.onFileOpen,
                                onFileDownload   = callbacks.onFileDownload,
                                onFileLongPress  = { item, offset ->
                                    contextItem       = item
                                    contextMenuOffset = offset
                                }
                            )
                            lvl is BrowserLevel.Root -> RootView(
                                drives               = state.drives,
                                recentPaths          = state.recentPaths,
                                specialFolders       = state.specialFolders,
                                isLoading            = state.isLoading,
                                listState            = listState,
                                onDriveClick         = callbacks.onDriveClick,
                                onRecentClick        = callbacks.onRecentClick,
                                onSpecialFolderClick = callbacks.onSpecialFolderClick
                            )
                            else -> FileListView(
                                dirItems          = state.dirItems,
                                isLoading         = state.isLoading,
                                browserMode       = state.browserMode,
                                listState         = listState,
                                onFolderClick     = callbacks.onFolderClick,
                                onFolderLongPress = { item, offset ->
                                    contextItem       = item
                                    contextMenuOffset = offset
                                },
                                onFileOpen        = callbacks.onFileOpen,
                                onFileDownload    = callbacks.onFileDownload,
                                onFileLongPress   = { item, offset ->
                                    contextItem       = item
                                    contextMenuOffset = offset
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Draggable Upload FAB (bottom-right default, smooth drag) ─────────
        val fabAnim = remember { Animatable(Offset(screenW - fabSizePx - fabPadPx, screenH - fabSizePx - fabPadPx - with(density) { 80.dp.toPx() }), Offset.VectorConverter) }

        LaunchedEffect(screenW, screenH) {
            if (fabAnim.value == Offset.Zero) {
                fabAnim.snapTo(
                    Offset(
                        screenW - fabSizePx - fabPadPx,
                        screenH - fabSizePx - fabPadPx - with(density) { 80.dp.toPx() }
                    )
                )
            }
        }

        Box(
            Modifier
                .offset {
                    IntOffset(
                        fabAnim.value.x.toInt(),
                        fabAnim.value.y.toInt()
                    )
                }
                .size(52.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    coroutineScope {
                        detectDragGestures(
                            onDragEnd = {
                                // Snap to nearest horizontal edge with spring
                                val cx = fabAnim.value.x + fabSizePx / 2
                                val targetX = if (cx < screenW / 2f)
                                    fabPadPx
                                else
                                    screenW - fabSizePx - fabPadPx
                                launch {
                                    fabAnim.animateTo(
                                        targetValue    = fabAnim.value.copy(x = targetX),
                                        animationSpec  = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness    = Spring.StiffnessLow
                                        )
                                    )
                                }
                                showFabMenu = false
                            }
                        ) { _, drag ->
                            launch {
                                fabAnim.snapTo(
                                    Offset(
                                        x = (fabAnim.value.x + drag.x).coerceIn(0f, screenW - fabSizePx),
                                        y = (fabAnim.value.y + drag.y).coerceIn(0f, screenH - fabSizePx)
                                    )
                                )
                            }
                            showFabMenu = false
                        }
                    }
                }
                .clickable { showFabMenu = !showFabMenu },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Upload, "Upload", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        // FAB upload menu (pops above FAB)
        if (showFabMenu) {
            Popup(
                onDismissRequest = { showFabMenu = false },
                properties       = PopupProperties(focusable = true),
                offset           = IntOffset(
                    fabAnim.value.x.toInt(),
                    (fabAnim.value.y - with(density) { 110.dp.toPx() }).toInt()
                )
            ) {
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter   = scaleIn(
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec   = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(),
                    exit    = scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(100))
                ) {
                    Surface(
                        shape           = RoundedCornerShape(14.dp),
                        tonalElevation  = 6.dp,
                        shadowElevation = 10.dp
                    ) {
                        Column(Modifier.width(160.dp).padding(vertical = 6.dp)) {
                            FabMenuItem(Icons.Default.InsertDriveFile, "Upload File") {
                                showFabMenu = false; callbacks.onUpload()
                            }
                            FabMenuItem(Icons.Default.Folder, "Upload Folder") {
                                showFabMenu = false; callbacks.onUploadFolder()
                            }
                        }
                    }
                }
            }
        }

        // ── Item context menu ─────────────────────────────────────────────────
        contextItem?.let { item ->
            ItemContextMenu(
                item      = item,
                anchor    = contextMenuOffset,
                onAction  = { action ->
                    contextItem = null
                    when (action) {
                        ItemAction.DELETE     -> deleteItem    = item
                        ItemAction.RENAME     -> { renameItem = item; renameValue = item.name }
                        ItemAction.PROPERTIES -> propertiesItem = item
                        ItemAction.MOVE       -> moveSourceItem = item
                        else                  -> callbacks.onItemAction(item, action)
                    }
                },
                onDismiss = { contextItem = null }
            )
        }
    }

    // ── Delete confirm ────────────────────────────────────────────────────────
    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete ${if (item.isDir) "Folder" else "File"}?") },
            text    = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\"${item.name}\" will be permanently deleted.")
                    if (item.isDir)
                        Text("All contents will be lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = { callbacks.onItemAction(item, ItemAction.DELETE); deleteItem = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteItem = null }) { Text("Cancel") } }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Rename") },
            text  = {
                OutlinedTextField(
                    value         = renameValue,
                    onValueChange = { renameValue = it },
                    label         = { Text("New name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick  = {
                        callbacks.onItemAction(item.copy(name = renameValue), ItemAction.RENAME)
                        renameItem = null
                    },
                    enabled = renameValue.isNotBlank() && renameValue != item.name
                ) { Text("Rename") }
            },
            dismissButton = { OutlinedButton(onClick = { renameItem = null }) { Text("Cancel") } }
        )
    }

    // ── Properties dialog ─────────────────────────────────────────────────────
    propertiesItem?.let { item ->
        AlertDialog(
            onDismissRequest = { propertiesItem = null },
            icon  = { Text(if (item.isDir) "📁" else fileIcon(item.extension), fontSize = 28.sp) },
            title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PropRow("Type",     if (item.isDir) "Folder" else "${item.extension.uppercase()} File")
                    PropRow("Path",     item.path)
                    PropRow("Size",     if (item.sizeKb > 0) formatSize(item.sizeKb) else "—")
                    PropRow("Modified", if (item.modTime > 0)
                        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm",
                            java.util.Locale.getDefault())
                            .format(java.util.Date(item.modTime * 1000L))
                    else "—")
                }
            },
            confirmButton = { TextButton(onClick = { propertiesItem = null }) { Text("Close") } }
        )
    }

    // ── Move-to picker ────────────────────────────────────────────────────────
    moveSourceItem?.let { source ->
        MoveToPickerDialog(
            drives     = state.drives,
            dirItems   = state.dirItems,
            onNavigate = { path -> callbacks.onItemAction(source.copy(path = path), ItemAction.MOVE) },
            onConfirm  = { destPath ->
                callbacks.onItemAction(source.copy(path = "$destPath/${source.name}"), ItemAction.MOVE)
                moveSourceItem = null
            },
            onDismiss  = { moveSourceItem = null }
        )
    }

    // ── Open-With dialog ──────────────────────────────────────────────────────
    state.openWithDialog?.let { dlg ->
        PcOpenWithDialog(
            dialog    = dlg,
            onSelect  = callbacks.onOpenWithSelect,
            onDismiss = callbacks.onDismissOpenWith
        )
    }
}

// ── Top bar — compact, theme color, no back button ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserTopBar(
    level           : BrowserLevel,
    currentPath     : String,
    isLoading       : Boolean,
    browserMode     : FileBrowserMode,
    connectionStatus: PcConnectionStatus,
    searchQuery     : String,
    onPing          : () -> Unit,
    onSearchChange  : (String) -> Unit,
    onNavigateBack  : () -> Unit,
) {
    val title = when (level) {
        is BrowserLevel.Root      -> "File Browser"
        is BrowserLevel.Drive     -> "${level.drive.letter}:\\ ${level.drive.label}"
        is BrowserLevel.Directory -> level.label.ifBlank { "Files" }
    }

    val (chipColor, chipLabel) = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onPrimary.copy(0.6f) to "Ping"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Level icon — small, no button
            Icon(
                when (level) {
                    is BrowserLevel.Root      -> Icons.Default.Computer
                    is BrowserLevel.Drive     -> Icons.Default.Storage
                    is BrowserLevel.Directory -> Icons.Default.Folder
                },
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )

            // Title + badge + path
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    // EXEC / TRANSFER badge
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(0.18f)
                    ) {
                        Text(
                            if (browserMode == FileBrowserMode.EXECUTE) "EXEC" else "TRANSFER",
                            modifier   = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize   = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                // Path subtitle
                if (currentPath.isNotEmpty()) {
                    Text(
                        currentPath,
                        fontSize = 9.sp,
                        color    = MaterialTheme.colorScheme.onPrimary.copy(0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Connection chip
            Surface(
                onClick = onPing,
                shape   = RoundedCornerShape(20.dp),
                color   = chipColor.copy(0.18f),
                border  = BorderStroke(1.dp, chipColor.copy(0.5f))
            ) {
                Text(
                    "● $chipLabel",
                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = chipColor
                )
            }

            // Loading spinner
            if (isLoading) {
                CircularProgressIndicator(
                    Modifier.size(13.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
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
                    add(seg to if (idx == parts.size - 2) level
                    else BrowserLevel.Directory(partial, seg))
                }
            }
            else -> {}
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
            if (!isLast) Icon(Icons.Default.ChevronRight, null,
                Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
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
        contentPadding      = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val recentFiles = recentPaths.filter { !it.isApp }
        if (recentFiles.isNotEmpty()) {
            item { BrowserSectionHeader("Recently Used", "${recentFiles.size}") }
            items(recentFiles, key = { "r_${it.path}" }) { r ->
                CompactFolderCard(
                    icon        = Icons.Default.History,
                    iconColor   = MaterialTheme.colorScheme.tertiary,
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
                    iconColor   = MaterialTheme.colorScheme.secondary,
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
    onFolderLongPress: (PcFileItem, Offset) -> Unit,
    onFileOpen       : (PcFileItem) -> Unit,
    onFileDownload   : (PcFileItem) -> Unit,
    onFileLongPress  : (PcFileItem, Offset) -> Unit,
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
        contentPadding      = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (folders.isNotEmpty()) {
            item { BrowserSectionHeader("Folders", "${folders.size}") }
            items(folders, key = { "d_${it.path}" }) { folder ->
                CompactFolderCard(
                    icon        = Icons.Default.Folder,
                    iconColor   = Color(0xFFF57C00),
                    title       = folder.name,
                    onClick     = { onFolderClick(folder) },
                    onLongPress = { offset -> onFolderLongPress(folder, offset) }
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
                    onDownload  = { onFileDownload(file) },
                    onLongPress = { offset -> onFileLongPress(file, offset) }
                )
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ── Search results view ───────────────────────────────────────────────────────

@Composable
fun SearchResultsView(
    query            : String,
    allItems         : List<PcFileItem>,
    browserMode      : FileBrowserMode,
    listState        : LazyListState,
    onFolderClick    : (PcFileItem) -> Unit,
    onFolderLongPress: (PcFileItem, Offset) -> Unit,
    onFileOpen       : (PcFileItem) -> Unit,
    onFileDownload   : (PcFileItem) -> Unit,
    onFileLongPress  : (PcFileItem, Offset) -> Unit,
) {
    val q       = query.trim()
    val results = allItems.filter { it.name.contains(q, ignoreCase = true) }
    val folders = results.filter { it.isDir }
    val files   = results.filter { !it.isDir }

    if (results.isEmpty()) {
        BrowserEmptyView(Icons.Default.SearchOff, "No results for \"$q\"")
        return
    }
    LazyColumn(
        state               = listState,
        contentPadding      = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { BrowserSectionHeader("Results", "${results.size}") }
        if (folders.isNotEmpty()) {
            item { BrowserSectionHeader("Folders", "${folders.size}") }
            items(folders, key = { "sr_d_${it.path}" }) { f ->
                CompactFolderCard(
                    icon        = Icons.Default.Folder,
                    iconColor   = Color(0xFFF57C00),
                    title       = f.name,
                    onClick     = { onFolderClick(f) },
                    onLongPress = { offset -> onFolderLongPress(f, offset) }
                )
            }
        }
        if (files.isNotEmpty()) {
            item { BrowserSectionHeader("Files", "${files.size}") }
            items(files, key = { "sr_f_${it.path}" }) { f ->
                CompactFileCard(
                    file        = f,
                    icon        = fileIcon(f.extension),
                    browserMode = browserMode,
                    onOpen      = { onFileOpen(f) },
                    onDownload  = { onFileDownload(f) },
                    onLongPress = { offset -> onFileLongPress(f, offset) }
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
        used > 0.9f -> MaterialTheme.colorScheme.error
        used > 0.7f -> Color(0xFFF57C00)
        else        -> MaterialTheme.colorScheme.primary
    }
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(54.dp),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    .background(usedColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Storage, null, tint = usedColor, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text("${drive.letter}:\\ ${drive.label}",
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                LinearProgressIndicator(
                    progress  = { used },
                    modifier  = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp),
                    color     = usedColor
                )
            }
            Text("${drive.freeGb.toInt()}GB",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp),
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
    onLongPress: ((Offset) -> Unit)?
) {
    var pressed      by remember { mutableStateOf(false) }
    val scale        by animateFloatAsState(
        targetValue   = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "cardScale"
    )
    var cardPos by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { cardPos = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress     = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap       = { onClick() },
                    onLongPress = { tap ->
                        onLongPress?.invoke(Offset(cardPos.x + tap.x, cardPos.y + tap.y))
                    }
                )
            },
        shape     = RoundedCornerShape(11.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(17.dp)) }
            Text(title,
                fontWeight = FontWeight.Medium,
                fontSize   = 13.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
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
    onDownload : () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    val action   = if (browserMode == FileBrowserMode.EXECUTE) onOpen else onDownload
    var cardPos by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .onGloballyPositioned { cardPos = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap       = { action() },
                    onLongPress = { tap ->
                        onLongPress(Offset(cardPos.x + tap.x, cardPos.y + tap.y))
                    }
                )
            },
        shape     = RoundedCornerShape(11.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(icon, fontSize = 18.sp)
            Column(Modifier.weight(1f)) {
                Text(file.name, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (file.sizeKb > 0)
                    Text(formatSize(file.sizeKb), fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick        = action,
                shape          = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier       = Modifier.height(28.dp)
            ) {
                Icon(
                    if (browserMode == FileBrowserMode.EXECUTE) Icons.AutoMirrored.Filled.OpenInNew
                    else Icons.Default.Download,
                    null, Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (browserMode == FileBrowserMode.EXECUTE) "Open" else "Save",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Item context menu — same AlertDialog style as Plans UI long-press ─────────

@Composable
fun ItemContextMenu(
    item     : PcFileItem,
    anchor   : Offset,
    onAction : (ItemAction) -> Unit,
    onDismiss: () -> Unit
) {
    data class ActionItem(
        val action     : ItemAction,
        val icon       : ImageVector,
        val label      : String,
        val containerColor: Color? = null,   // null = surfaceVariant
        val contentColor  : Color? = null,   // null = onSurface
    )

    @Composable
    fun actionColor(item: ActionItem) = item.containerColor
        ?: MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun labelColor(item: ActionItem) = item.contentColor
        ?: MaterialTheme.colorScheme.onSurface

    @Composable
    fun iconColor(item: ActionItem) = item.contentColor
        ?: MaterialTheme.colorScheme.primary

    val actions = listOf(
        ActionItem(ItemAction.DOWNLOAD,
            Icons.Default.Download,
            "Download",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor   = MaterialTheme.colorScheme.primary),
        ActionItem(ItemAction.MOVE,
            Icons.AutoMirrored.Filled.DriveFileMove,
            "Move To…",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor   = MaterialTheme.colorScheme.secondary),
        ActionItem(ItemAction.COPY,
            Icons.Default.ContentCopy,
            "Copy",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor   = MaterialTheme.colorScheme.tertiary),
        ActionItem(ItemAction.RENAME,
            Icons.Default.DriveFileRenameOutline,
            "Rename"),
        ActionItem(ItemAction.PROPERTIES,
            Icons.Default.Info,
            "Properties"),
        ActionItem(ItemAction.DELETE,
            Icons.Default.Delete,
            "Delete",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor   = MaterialTheme.colorScheme.error),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text(
                if (item.isDir) "📁" else fileIcon(item.extension),
                fontSize = 26.sp
            )
        },
        title = {
            Text(
                item.name,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (item.isDir) "Folder" else "${item.extension.uppercase()} · ${formatSize(item.sizeKb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                actions.forEach { a ->
                    Surface(
                        onClick  = { onAction(a.action) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = actionColor(a),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                a.icon, null,
                                tint     = iconColor(a),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                a.label,
                                fontWeight = FontWeight.SemiBold,
                                color      = labelColor(a),
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Move-to picker ────────────────────────────────────────────────────────────

@Composable
fun MoveToPickerDialog(
    drives    : List<PcDrive>,
    dirItems  : List<PcFileItem>,
    onNavigate: (String) -> Unit,
    onConfirm : (destPath: String) -> Unit,
    onDismiss : () -> Unit
) {
    var currentPickPath by remember { mutableStateOf("") }
    val folders = dirItems.filter { it.isDir }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move To…") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (currentPickPath.isEmpty()) "Select destination" else currentPickPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                if (currentPickPath.isEmpty()) {
                    drives.forEach { drive ->
                        Surface(
                            onClick  = { currentPickPath = "${drive.letter}:/"; onNavigate(currentPickPath) },
                            shape    = RoundedCornerShape(8.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("💾", fontSize = 16.sp)
                                Text("${drive.letter}:\\ ${drive.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp))
                            }
                        }
                    }
                } else {
                    if (currentPickPath.length > 3) {
                        TextButton(onClick = {
                            currentPickPath = currentPickPath.trimEnd('/', '\\')
                                .substringBeforeLast('/').substringBeforeLast('\\')
                            onNavigate(currentPickPath)
                        }) {
                            Icon(Icons.Default.ArrowBack, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Back")
                        }
                    }
                    Column(
                        Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (folders.isEmpty()) {
                            Text("No subfolders", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        folders.forEach { folder ->
                            Surface(
                                onClick  = { currentPickPath = folder.path; onNavigate(folder.path) },
                                shape    = RoundedCornerShape(8.dp),
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("📁", fontSize = 15.sp)
                                    Text(folder.name, style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f), maxLines = 1)
                                    Icon(Icons.Default.ChevronRight, null, Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentPickPath) },
                enabled = currentPickPath.isNotEmpty()) { Text("Move Here") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (progress.isUpload) Icons.Default.Upload else Icons.Default.Download,
                        null, Modifier.size(13.dp))
                    Text(progress.fileName.take(28),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (progress.isDone)
                    IconButton(onDismiss, Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(11.dp))
                    }
            }
            if (!progress.isDone) {
                Spacer(Modifier.height(3.dp))
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
                Text(dialog.filePath.substringAfterLast('/').substringAfterLast('\\'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                dialog.choices.forEach { c ->
                    Surface(onClick = { onSelect(c) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(c.icon, fontSize = 18.sp)
                            Text(c.appName, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp))
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
            Spacer(Modifier.height(8.dp))
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BrowserEmptyView(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.28f))
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BrowserSectionHeader(title: String, count: String) {
    Row(
        Modifier.padding(bottom = 3.dp, top = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("· $count", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FabMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.65f))
    }
}

fun fileIcon(ext: String): String = when (ext.lowercase()) {
    "mp4","mkv","avi","mov","wmv"         -> "🎬"
    "mp3","wav","flac","aac","m4a"         -> "🎵"
    "jpg","jpeg","png","gif","bmp","webp"  -> "🖼️"
    "pdf"                                  -> "📕"
    "doc","docx","rtf"                     -> "📘"
    "xls","xlsx","csv"                     -> "📗"
    "ppt","pptx"                           -> "📊"
    "txt","log","md"                       -> "📄"
    "zip","rar","7z","tar","gz"            -> "🗜️"
    "py","bat","ps1","sh","cmd"            -> "⚙️"
    "exe","msi"                            -> "🖥️"
    else                                    -> "📄"
}

fun formatSize(kb: Long): String = when {
    kb > 1024 * 1024 -> "${kb / (1024 * 1024)} GB"
    kb > 1024        -> "${kb / 1024} MB"
    else             -> "$kb KB"
}