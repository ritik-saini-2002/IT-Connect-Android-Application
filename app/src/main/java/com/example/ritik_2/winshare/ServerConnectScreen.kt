package com.example.ritik_2.winshare

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Theme-aware colors ──────────────────────────────────────────────────────

data class AppColors(
    val bg: Color,
    val bgGradientEnd: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val surface: Color,
    val accent: Color,
    val accentSecondary: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

@Composable
private fun appColors(): AppColors {
    val dark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    return AppColors(
        bg = cs.background, bgGradientEnd = cs.background,
        glassBg = cs.surfaceVariant.copy(alpha = if (dark) 0.5f else 0.8f),
        glassBorder = cs.outline.copy(alpha = 0.3f),
        glassHighlight = cs.surfaceVariant.copy(alpha = if (dark) 0.15f else 0.5f),
        surface = cs.surface,
        accent = cs.primary, accentSecondary = cs.secondary,
        danger = cs.error,
        textPrimary = cs.onSurface, textSecondary = cs.onSurfaceVariant,
        textTertiary = cs.onSurfaceVariant.copy(0.5f)
    )
}

// ─── Glass Card helpers ──────────────────────────────────────────────────────

@Composable
private fun GlassCard(c: AppColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.border(1.dp, c.glassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.glassBg),
        elevation = CardDefaults.cardElevation(0.dp)) { content() }
}

@Composable
private fun SmallGlassCard(c: AppColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.border(0.5.dp, c.glassBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.glassBg),
        elevation = CardDefaults.cardElevation(0.dp)) { content() }
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectScreen(viewModel: ServerConnectModule = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = appColors()

    LaunchedEffect(Unit) { viewModel.loadSavedServers(context) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { scope.launch { viewModel.uploadFile(it, context) } } }

    LaunchedEffect(Unit) { viewModel.errorMessages.collect { msg -> snackbarHostState.showSnackbar(msg, actionLabel = "OK", duration = SnackbarDuration.Long) } }
    LaunchedEffect(Unit) { viewModel.successMessages.collect { msg -> snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short) } }

    LaunchedEffect(uiState.autoConnectServerId) {
        uiState.autoConnectServerId?.let { id ->
            uiState.savedServers.find { it.id == id }?.let { viewModel.autoConnectSavedServer(it, context) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bg, c.bgGradientEnd)))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Action bar + search (when connected)
            if (uiState.isConnected) {
                ConnectedActionBar(c = c, uiState = uiState,
                    onRefresh = { viewModel.refreshFiles() },
                    onNavigateUp = { viewModel.navigateUp() },
                    onDeleteSelected = { viewModel.deleteSelectedFiles() },
                    onDisconnect = { viewModel.disconnect() },
                    onToggleSort = { viewModel.handleEvent(ServerConnectEvent.ToggleSortOrder) },
                    onToggleSearch = { viewModel.handleEvent(ServerConnectEvent.ToggleSearch) },
                    onSearchQueryChange = { viewModel.handleEvent(ServerConnectEvent.UpdateSearchQuery(it)) },
                    onExitMultiSelect = { viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode) }
                )
            }

            AnimatedVisibility(visible = uiState.isConnected) {
                ConnectionStatusSection(c = c, uiState = uiState, onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) })
            }

            AnimatedVisibility(visible = uiState.isTransferring,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) { TransferPanel(c = c, stats = uiState.transferStats, onCancel = { viewModel.handleEvent(ServerConnectEvent.CancelTransfer) }) }

            when {
                uiState.isLoading && !uiState.isTransferring -> GlassLoadingScreen(c)
                !uiState.isConnected -> GlassDisconnectedScreen(c = c,
                    onConnect = { viewModel.handleEvent(ServerConnectEvent.ShowConnectionDialog) },
                    savedServers = uiState.savedServers,
                    onAutoConnect = { viewModel.autoConnectSavedServer(it, context) },
                    onEditServer = { viewModel.handleEvent(ServerConnectEvent.ShowEditServerDialog(it)) },
                    onDeleteServer = { viewModel.handleEvent(ServerConnectEvent.DeleteSavedServer(it)) })
                uiState.fileList.isEmpty() -> GlassEmptyDirectoryScreen(c)
                else -> FileListSection(c = c, fileList = uiState.fileList, selectedFiles = uiState.selectedFiles, isMultiSelectMode = uiState.isMultiSelectMode,
                    onFileClick = { fileItem ->
                        if (uiState.isMultiSelectMode) viewModel.handleEvent(ServerConnectEvent.ToggleFileSelection(fileItem.name))
                        else if (fileItem.isDirectory) viewModel.navigateToDirectory(fileItem.name)
                        else scope.launch { viewModel.downloadFile(fileItem, context) }
                    },
                    onFileLongClick = { fileItem -> viewModel.handleEvent(ServerConnectEvent.LongPressFile(fileItem.name)) },
                    onFileContextMenu = { fileItem -> viewModel.handleEvent(ServerConnectEvent.ShowFileContextMenu(fileItem)) }
                )
            }
        }

        // ── Draggable FAB ──
        if (uiState.isConnected && !uiState.isMultiSelectMode && !uiState.isTransferring) {
            DraggableFab(c = c,
                onUpload = { filePickerLauncher.launch("*/*") },
                onNewFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) })
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())

        // ── Dialogs ──
        if (uiState.showConnectionDialog) GlassConnectionDialog(c, uiState, viewModel::handleEvent) { scope.launch { viewModel.connectToServer(context) } }
        if (uiState.showCreateFolderDialog) GlassCreateFolderDialog(c, uiState.newFolderName, { viewModel.handleEvent(ServerConnectEvent.UpdateNewFolderName(it)) }, { viewModel.handleEvent(ServerConnectEvent.HideCreateFolderDialog) }, { viewModel.createFolder() })
        if (uiState.showFileContextMenu && uiState.contextMenuFile != null) {
            FileContextMenuDialog(c, uiState.contextMenuFile!!,
                onDismiss = { viewModel.handleEvent(ServerConnectEvent.HideFileContextMenu) },
                onRename = { viewModel.handleEvent(ServerConnectEvent.ShowRenameDialog(it)) },
                onMove = { viewModel.handleEvent(ServerConnectEvent.ShowMoveDialog(it)) },
                onDelete = { viewModel.deleteSingleFile(it) },
                onProperties = { viewModel.handleEvent(ServerConnectEvent.ShowPropertiesDialog(it)) },
                onDownload = { f -> viewModel.handleEvent(ServerConnectEvent.HideFileContextMenu); scope.launch { viewModel.downloadFile(f, context) } })
        }
        if (uiState.showRenameDialog && uiState.renameTarget != null) RenameDialog(c, uiState.renameTarget!!.name, uiState.renameNewName, { viewModel.handleEvent(ServerConnectEvent.UpdateRenameName(it)) }, { viewModel.handleEvent(ServerConnectEvent.HideRenameDialog) }, { viewModel.renameFile() })
        if (uiState.showMoveDialog && uiState.moveTarget != null) MoveDialog(c, uiState.moveTarget!!.name, uiState.moveDestination, { viewModel.handleEvent(ServerConnectEvent.UpdateMoveDestination(it)) }, { viewModel.handleEvent(ServerConnectEvent.HideMoveDialog) }, { viewModel.moveFile() })
        if (uiState.showPropertiesDialog && uiState.propertiesTarget != null) PropertiesDialog(c, uiState.propertiesTarget!!, uiState.currentPath, uiState.currentServer, uiState.currentShare) { viewModel.handleEvent(ServerConnectEvent.HidePropertiesDialog) }
        if (uiState.showEditServerDialog && uiState.editingServer != null) EditServerDialog(c, uiState.editingServer!!, { viewModel.handleEvent(ServerConnectEvent.HideEditServerDialog) }, { viewModel.updateSavedServer(context, it) }, { viewModel.updateSavedServer(context, it); viewModel.autoConnectSavedServer(it, context) })
    }
}

// ─── Draggable FAB (Upload + Folder) ─────────────────────────────────────────

@Composable
private fun BoxScope.DraggableFab(c: AppColors, onUpload: () -> Unit, onNewFolder: () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(16.dp)
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        AnimatedVisibility(visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                MiniFabItem(c, Icons.Default.CreateNewFolder, "New Folder") { onNewFolder(); expanded = false }
                MiniFabItem(c, Icons.Default.Upload, "Upload File") { onUpload(); expanded = false }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = c.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape
        ) {
            Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Actions")
        }
    }
}

@Composable
private fun MiniFabItem(c: AppColors, icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = c.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
            Text(label, color = c.textSecondary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = c.accentSecondary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Connected Action Bar ────────────────────────────────────────────────────

@Composable
private fun ConnectedActionBar(
    c: AppColors, uiState: ServerConnectionState,
    onRefresh: () -> Unit, onNavigateUp: () -> Unit,
    onDeleteSelected: () -> Unit, onDisconnect: () -> Unit,
    onToggleSort: () -> Unit, onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit, onExitMultiSelect: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Search bar — stays open until user closes it
        AnimatedVisibility(visible = uiState.isSearchActive) {
            SmallGlassCard(c, Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery, onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search files…", color = c.textTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = c.accentSecondary) },
                    trailingIcon = { IconButton(onClick = { onSearchQueryChange(""); onToggleSearch(); focusManager.clearFocus() }) { Icon(Icons.Default.Close, "Close", tint = c.textSecondary) } },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accentSecondary, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }
        }

        if (uiState.isMultiSelectMode) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                Text("${uiState.selectedFiles.size} selected", color = c.accentSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                if (uiState.selectedFiles.isNotEmpty()) ActionBox(c, "Delete", Icons.Default.Delete, c.danger, onDeleteSelected)
                ActionBox(c, "Cancel", Icons.Default.Close, c.textSecondary, onExitMultiSelect)
            }
        } else {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                if (uiState.breadcrumbs.size > 1) ActionBox(c, "Back", Icons.Default.ArrowBack, c.textSecondary, onNavigateUp)
                ActionBox(c, "Search", Icons.Default.Search, c.accentSecondary, onToggleSearch)
                ActionBox(c, "Sort", if (uiState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, c.textSecondary, onToggleSort)
                ActionBox(c, "Refresh", Icons.Default.Refresh, c.textSecondary, onRefresh)
                Spacer(Modifier.weight(1f))
                ActionBox(c, "Exit", Icons.Default.CloudOff, c.danger, onDisconnect)
            }
        }
    }
}

@Composable
private fun ActionBox(c: AppColors, label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(0.5.dp, c.glassBorder, RoundedCornerShape(10.dp)).background(c.glassBg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 9.sp, color = tint, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

// ─── Connection Status + Breadcrumbs ─────────────────────────────────────────

@Composable
private fun ConnectionStatusSection(c: AppColors, uiState: ServerConnectionState, onBreadcrumbClick: (Int) -> Unit) {
    GlassCard(c, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accentSecondary))
                Spacer(Modifier.width(8.dp))
                Text("\\\\${uiState.currentServer}\\${uiState.currentShare}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (uiState.fileList.isNotEmpty()) Text("${uiState.fileList.size} items", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            if (uiState.breadcrumbs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(uiState.breadcrumbs) { index, crumb ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 0) Icon(Icons.Default.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                            val isLast = index == uiState.breadcrumbs.lastIndex
                            Surface(onClick = { onBreadcrumbClick(index) }, shape = RoundedCornerShape(6.dp), color = if (isLast) c.accent.copy(0.25f) else c.glassBg, modifier = Modifier.heightIn(max = 26.dp)) {
                                Text(crumb, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = if (isLast) c.accentSecondary else c.textSecondary, fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Transfer Panel ──────────────────────────────────────────────────────────

@Composable
private fun TransferPanel(c: AppColors, stats: TransferStats, onCancel: () -> Unit) {
    val pulse = rememberInfiniteTransition("pulse")
    val alpha by pulse.animateFloat(0.7f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), "a")
    GlassCard(c, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (stats.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload, null, tint = c.accentSecondary.copy(alpha), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (stats.isUpload) "Uploading" else "Downloading → Downloads/WinShare", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Text(stats.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCancel, Modifier.size(32.dp)) { Icon(Icons.Default.Cancel, "Cancel", tint = c.danger, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { stats.progress }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = c.accentSecondary, trackColor = c.glassHighlight)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(stats.formattedTransferred, style = MaterialTheme.typography.bodySmall, color = c.textSecondary); Text("${(stats.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = c.accentSecondary) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedChip(c, "Now", stats.formattedCurrentSpeed); SpeedChip(c, "Avg", stats.formattedAvgSpeed); SpeedChip(c, "Peak", stats.formattedPeakSpeed)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, Modifier.size(11.dp), c.textTertiary); Spacer(Modifier.width(3.dp)); Text(stats.formattedElapsed, style = MaterialTheme.typography.labelSmall, color = c.textTertiary) }
                    if (stats.etaSeconds >= 0) Text("ETA ${stats.formattedEta}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(c: AppColors, label: String, value: String) {
    Surface(color = c.accent.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) { Text("$label ", style = MaterialTheme.typography.labelSmall, color = c.textTertiary); Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = c.accentSecondary) }
    }
}

// ─── Loading / Disconnected / Empty ──────────────────────────────────────────

@Composable
private fun GlassLoadingScreen(c: AppColors) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = c.accentSecondary, strokeWidth = 2.dp); Spacer(Modifier.height(16.dp)); Text("Loading…", color = c.textSecondary, fontSize = 14.sp) } }
}

@Composable
private fun GlassDisconnectedScreen(c: AppColors, onConnect: () -> Unit, savedServers: List<SavedServer>, onAutoConnect: (SavedServer) -> Unit, onEditServer: (SavedServer) -> Unit, onDeleteServer: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(40.dp))
            GlassCard(c, Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent.copy(0.15f)), Alignment.Center) { Icon(Icons.Default.CloudOff, null, Modifier.size(36.dp), c.accent) }
                    Spacer(Modifier.height(16.dp)); Text("WinShare", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp)); Text("Connect to SMB server for fast file sharing", fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onConnect, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(c.accent, MaterialTheme.colorScheme.onPrimary), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("New Connection", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        if (savedServers.isNotEmpty()) {
            item { Text("Saved Servers", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
            items(savedServers, key = { it.id }) { server ->
                GlassCard(c, Modifier.fillMaxWidth().clickable { onAutoConnect(server) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(c.accentSecondary.copy(0.12f)), Alignment.Center) { Icon(Icons.Default.Storage, null, tint = c.accentSecondary, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(server.label.ifBlank { server.serverAddress }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                            Text("\\\\${server.serverAddress}${if (server.shareName.isNotEmpty()) "\\${server.shareName}" else ""}", fontSize = 11.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(server.username, fontSize = 10.sp, color = c.textTertiary)
                        }
                        IconButton(onClick = { onEditServer(server) }, Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "Edit", tint = c.accent, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { onDeleteServer(server.id) }, Modifier.size(34.dp)) { Icon(Icons.Outlined.DeleteOutline, "Delete", tint = c.danger, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun GlassEmptyDirectoryScreen(c: AppColors) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        GlassCard(c, Modifier.padding(32.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), c.textTertiary); Spacer(Modifier.height(12.dp)); Text("Empty Directory", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary); Spacer(Modifier.height(6.dp)); Text("Use the + button to upload or create folders", fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center) } }
    }
}

// ─── File List ───────────────────────────────────────────────────────────────

@Composable
private fun FileListSection(c: AppColors, fileList: List<SMBFileItem>, selectedFiles: Set<String>, isMultiSelectMode: Boolean, onFileClick: (SMBFileItem) -> Unit, onFileLongClick: (SMBFileItem) -> Unit, onFileContextMenu: (SMBFileItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(fileList, key = { it.name }) { fileItem ->
            FileListItem(c, fileItem, selectedFiles.contains(fileItem.name), isMultiSelectMode,
                onClick = { onFileClick(fileItem) },
                onLongClick = { if (isMultiSelectMode) onFileLongClick(fileItem) else onFileContextMenu(fileItem) },
                onSelectToggle = { onFileLongClick(fileItem) })
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(c: AppColors, fileItem: SMBFileItem, isSelected: Boolean, isMultiSelectMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onSelectToggle: () -> Unit) {
    val bgColor by animateColorAsState(if (isSelected) c.accent.copy(0.15f) else Color.Transparent, label = "bg")
    SmallGlassCard(c, Modifier.fillMaxWidth().combinedClickable(onClick = { if (isMultiSelectMode) onSelectToggle() else onClick() }, onLongClick = onLongClick)) {
        Row(Modifier.background(bgColor).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isMultiSelectMode) { Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (isSelected) c.accentSecondary else c.textTertiary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
            FileIcon(c, fileItem, Modifier.size(38.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fileItem.name, fontSize = 14.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) { if (!fileItem.isDirectory) { Text(fileItem.formattedSize, fontSize = 11.sp, color = c.textTertiary); Text(" · ", fontSize = 11.sp, color = c.textTertiary) }; Text(fileItem.formattedDate, fontSize = 11.sp, color = c.textTertiary) }
            }
            if (!isMultiSelectMode) { if (fileItem.isDirectory) Icon(Icons.Default.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(20.dp)) else Icon(Icons.Default.Download, "Download", tint = c.accent, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun FileIcon(c: AppColors, fileItem: SMBFileItem, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        fileItem.isDirectory -> Icons.Default.Folder to c.accent
        else -> when (fileItem.fileExtension.lowercase()) {
            "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFFF5252); "doc","docx" -> Icons.Default.Description to Color(0xFF448AFF); "xls","xlsx" -> Icons.Default.TableChart to Color(0xFF66BB6A); "ppt","pptx" -> Icons.Default.Slideshow to Color(0xFFFF7043)
            "jpg","jpeg","png","gif","bmp","webp" -> Icons.Default.Image to Color(0xFFCE93D8); "mp4","avi","mkv","mov","wmv" -> Icons.Default.Movie to Color(0xFF4DD0E1); "mp3","wav","flac","aac" -> Icons.Default.AudioFile to Color(0xFFFFB74D)
            "zip","rar","7z","tar","gz" -> Icons.Default.Archive to Color(0xFF90A4AE); "txt","md","log" -> Icons.Default.TextSnippet to c.textSecondary; "apk","exe","msi" -> Icons.Default.Apps to c.accentSecondary
            else -> Icons.Default.InsertDriveFile to c.textSecondary
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(0.12f)), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable private fun FileContextMenuDialog(c: AppColors, file: SMBFileItem, onDismiss: () -> Unit, onRename: (SMBFileItem) -> Unit, onMove: (SMBFileItem) -> Unit, onDelete: (SMBFileItem) -> Unit, onProperties: (SMBFileItem) -> Unit, onDownload: (SMBFileItem) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { FileIcon(c, file, Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)); Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!file.isDirectory) CtxItem(c, Icons.Default.Download, "Download to Phone", c.accentSecondary) { onDownload(file) }
            CtxItem(c, Icons.Default.DriveFileRenameOutline, "Rename", c.accent) { onRename(file) }
            CtxItem(c, Icons.Default.DriveFileMove, "Move to…", c.accentSecondary) { onMove(file) }
            CtxItem(c, Icons.Default.Info, "Properties", c.textSecondary) { onProperties(file) }
            HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 4.dp))
            CtxItem(c, Icons.Default.Delete, "Delete", c.danger) { onDelete(file) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun CtxItem(c: AppColors, icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)); Text(label, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium) }
    }
}

@Composable private fun RenameDialog(c: AppColors, currentName: String, newName: String, onChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Text("Rename", fontWeight = FontWeight.SemiBold) },
        text = { Column { Text("Current: $currentName", fontSize = 12.sp, color = c.textTertiary); Spacer(Modifier.height(10.dp)); GlassTextField(c, newName, onChange, "New name", "", Icons.Default.DriveFileRenameOutline) } },
        confirmButton = { Button(onClick = onConfirm, enabled = newName.trim().isNotEmpty() && newName.trim() != currentName, colors = ButtonDefaults.buttonColors(c.accent)) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun MoveDialog(c: AppColors, fileName: String, dest: String, onChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Text("Move '$fileName'", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
        text = { Column { Text("Destination path within the share:", fontSize = 12.sp, color = c.textTertiary); Spacer(Modifier.height(10.dp)); GlassTextField(c, dest, onChange, "Destination path", "e.g. Documents/Archive", Icons.Default.DriveFileMove) } },
        confirmButton = { Button(onClick = onConfirm, enabled = dest.trim().isNotEmpty(), colors = ButtonDefaults.buttonColors(c.accent)) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun PropertiesDialog(c: AppColors, file: SMBFileItem, currentPath: String, server: String, share: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = c.accentSecondary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Properties", fontWeight = FontWeight.SemiBold) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PropRow(c, "Name", file.name); PropRow(c, "Type", if (file.isDirectory) "Folder" else "File (.${file.fileExtension})")
            if (!file.isDirectory) PropRow(c, "Size", file.formattedSize); PropRow(c, "Modified", file.formattedDate)
            PropRow(c, "Path", "\\\\$server\\$share${if (currentPath.isNotEmpty()) "\\$currentPath" else ""}\\${file.name}")
            PropRow(c, "Readable", if (file.canRead) "Yes" else "No"); PropRow(c, "Writable", if (file.canWrite) "Yes" else "No")
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.accentSecondary) } }, dismissButton = {})
}

@Composable private fun PropRow(c: AppColors, label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(label, fontSize = 12.sp, color = c.textTertiary, modifier = Modifier.width(80.dp), fontWeight = FontWeight.Medium); Text(value, fontSize = 12.sp, color = c.textPrimary, modifier = Modifier.weight(1f)) } }

@Composable private fun EditServerDialog(c: AppColors, server: SavedServer, onDismiss: () -> Unit, onSave: (SavedServer) -> Unit, onSaveAndConnect: (SavedServer) -> Unit) {
    var label by remember { mutableStateOf(server.label) }; var address by remember { mutableStateOf(server.serverAddress) }; var username by remember { mutableStateOf(server.username) }; var password by remember { mutableStateOf(server.password) }; var share by remember { mutableStateOf(server.shareName) }; var showPw by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Edit, null, tint = c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Edit Server", fontWeight = FontWeight.SemiBold) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(c, label, { label = it }, "Label", icon = Icons.Default.Label)
            GlassTextField(c, address, { address = it }, "Server Address", icon = Icons.Default.Computer)
            GlassTextField(c, share, { share = it }, "Share Name", icon = Icons.Default.FolderShared)
            GlassTextField(c, username, { username = it }, "Username", icon = Icons.Default.Person)
            GlassTextField(c, password, { password = it }, "Password", icon = Icons.Default.Lock, isPassword = true, showPassword = showPw, onTogglePassword = { showPw = !showPw })
        } },
        confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSave(server.copy(label = label, serverAddress = address, username = username, password = password, shareName = share)) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accentSecondary)) { Text("Save", fontSize = 12.sp) }
            Button(onClick = { onSaveAndConnect(server.copy(label = label, serverAddress = address, username = username, password = password, shareName = share)) }, colors = ButtonDefaults.buttonColors(c.accent)) { Text("Save & Connect", fontSize = 12.sp) }
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun GlassConnectionDialog(c: AppColors, uiState: ServerConnectionState, onEvent: (ServerConnectEvent) -> Unit, onConnect: () -> Unit) {
    var showPw by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { onEvent(ServerConnectEvent.HideConnectionDialog) }, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudSync, null, tint = c.accentSecondary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("Connect to Server", fontWeight = FontWeight.SemiBold) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(c, uiState.connectionLabel, { onEvent(ServerConnectEvent.UpdateConnectionLabel(it)) }, "Label (optional)", "Home NAS…", Icons.Default.Label)
            GlassTextField(c, uiState.serverAddress, { onEvent(ServerConnectEvent.UpdateServerAddress(it)) }, "Server Address *", "192.168.1.100", Icons.Default.Computer)
            GlassTextField(c, uiState.shareName, { onEvent(ServerConnectEvent.UpdateShareName(it)) }, "Share Name", "shared-folder", Icons.Default.FolderShared)
            GlassTextField(c, uiState.username, { onEvent(ServerConnectEvent.UpdateUsername(it)) }, "Username *", "administrator", Icons.Default.Person)
            GlassTextField(c, uiState.password, { onEvent(ServerConnectEvent.UpdatePassword(it)) }, "Password", "••••••••", Icons.Default.Lock, isPassword = true, showPassword = showPw, onTogglePassword = { showPw = !showPw })
            Text("Credentials saved automatically.", fontSize = 11.sp, color = c.textTertiary)
        } },
        confirmButton = { Button(onClick = onConnect, enabled = uiState.serverAddress.isNotBlank() && uiState.username.isNotBlank(), colors = ButtonDefaults.buttonColors(c.accent)) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { onEvent(ServerConnectEvent.HideConnectionDialog) }) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun GlassCreateFolderDialog(c: AppColors, name: String, onChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.surface, titleContentColor = c.textPrimary,
        title = { Text("Create New Folder", fontWeight = FontWeight.SemiBold) },
        text = { GlassTextField(c, name, onChange, "Folder Name", icon = Icons.Default.CreateNewFolder) },
        confirmButton = { Button(onClick = onConfirm, enabled = name.trim().isNotEmpty(), colors = ButtonDefaults.buttonColors(c.accent)) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } })
}

@Composable private fun GlassTextField(c: AppColors, value: String, onValueChange: (String) -> Unit, label: String, placeholder: String = "", icon: ImageVector, isPassword: Boolean = false, showPassword: Boolean = false, onTogglePassword: (() -> Unit)? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, color = c.textTertiary) },
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = c.textTertiary.copy(0.5f)) }} else null,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(icon, null, tint = c.accent.copy(0.7f)) },
        trailingIcon = if (isPassword && onTogglePassword != null) {{ IconButton(onClick = onTogglePassword) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle", tint = c.textTertiary) } }} else null,
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accentSecondary, focusedBorderColor = c.accent, unfocusedBorderColor = c.glassBorder, focusedLabelColor = c.accentSecondary, unfocusedLabelColor = c.textTertiary))
}