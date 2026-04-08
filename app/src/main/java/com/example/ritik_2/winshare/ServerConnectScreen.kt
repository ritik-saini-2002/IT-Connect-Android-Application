package com.example.ritik_2.winshare

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectScreen(
    onNavigateBack: () -> Boolean,
    viewModel: ServerConnectModule = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── System navigation / status bar sync ─────────────────────────────────
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
//    SideEffect {
//        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        val ctrl = WindowInsetsControllerCompat(window, view)
//        // Hide both bars — same as PC-control activities
//        ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
//        ctrl.systemBarsBehavior =
//            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        // Transparent bars
//        window.statusBarColor     = android.graphics.Color.TRANSPARENT
//        window.navigationBarColor = android.graphics.Color.TRANSPARENT
//    }

    LaunchedEffect(Unit) { viewModel.loadSavedServers(context) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { scope.launch { viewModel.uploadFile(it, context) } } }

    LaunchedEffect(Unit) { viewModel.navigationEvents.collect { if (!it) onNavigateBack() } }
    LaunchedEffect(Unit) {
        viewModel.errorMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg, actionLabel = "OK", duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.successMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        // Scaffold handles system window insets automatically when edge-to-edge is on
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SMBTopBar(
                uiState = uiState,
                onRefresh = { viewModel.refreshFiles() },
                onNavigateUp = { viewModel.navigateUp() },
                onToggleMultiSelect = { viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode) },
                onCreateFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) },
                onUploadFile = { filePickerLauncher.launch("*/*") },
                onDeleteSelected = { viewModel.deleteSelectedFiles() },
                onDisconnect = { viewModel.disconnect() },
                onToggleSort = { viewModel.handleEvent(ServerConnectEvent.ToggleSortOrder) },
                onShowSaved = { viewModel.handleEvent(ServerConnectEvent.ShowSavedServersDialog) }
            )
        },
        floatingActionButton = {
            if (uiState.isConnected && !uiState.isMultiSelectMode && !uiState.isTransferring) {
                SMBFab(
                    onUpload = { filePickerLauncher.launch("*/*") },
                    onNewFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Connected status / breadcrumb bar
            AnimatedVisibility(visible = uiState.isConnected) {
                ConnectionStatusSection(
                    uiState = uiState,
                    onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) }
                )
            }

            // Transfer progress panel — slides in/out
            AnimatedVisibility(
                visible = uiState.isTransferring,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TransferPanel(
                    stats = uiState.transferStats,
                    onCancel = { viewModel.handleEvent(ServerConnectEvent.CancelTransfer) }
                )
            }

            // Main content area
            when {
                uiState.isLoading && !uiState.isTransferring -> LoadingScreen()
                !uiState.isConnected -> DisconnectedScreen(
                    onConnect = { viewModel.handleEvent(ServerConnectEvent.ShowConnectionDialog) },
                    onShowSaved = { viewModel.handleEvent(ServerConnectEvent.ShowSavedServersDialog) },
                    hasSavedServers = uiState.savedServers.isNotEmpty()
                )
                uiState.fileList.isEmpty() -> EmptyDirectoryScreen(
                    onUploadFile = { filePickerLauncher.launch("*/*") },
                    onCreateFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) }
                )
                else -> FileListSection(
                    fileList = uiState.fileList,
                    selectedFiles = uiState.selectedFiles,
                    isMultiSelectMode = uiState.isMultiSelectMode,
                    onFileClick = { fileItem ->
                        if (uiState.isMultiSelectMode) {
                            viewModel.handleEvent(ServerConnectEvent.ToggleFileSelection(fileItem.name))
                        } else if (fileItem.isDirectory) {
                            viewModel.navigateToDirectory(fileItem.name)
                        } else {
                            scope.launch {
                                val uri = viewModel.downloadFile(fileItem, context)
                                uri?.let {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(it, context.contentResolver.getType(it))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            }
                        }
                    },
                    onFileLongClick = { fileItem ->
                        if (!uiState.isMultiSelectMode) viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode)
                        viewModel.handleEvent(ServerConnectEvent.ToggleFileSelection(fileItem.name))
                    }
                )
            }
        }

        // ── Dialogs ──────────────────────────────────────────────────────────
        if (uiState.showConnectionDialog) {
            ConnectionDialog(
                uiState = uiState,
                onEvent = viewModel::handleEvent,
                onConnect = { scope.launch { viewModel.connectToServer(context) } }
            )
        }
        if (uiState.showCreateFolderDialog) {
            CreateFolderDialog(
                folderName = uiState.newFolderName,
                onFolderNameChange = { viewModel.handleEvent(ServerConnectEvent.UpdateNewFolderName(it)) },
                onDismiss = { viewModel.handleEvent(ServerConnectEvent.HideCreateFolderDialog) },
                onConfirm = { viewModel.createFolder() }
            )
        }
        if (uiState.showSavedServersDialog) {
            SavedServersDialog(
                servers = uiState.savedServers,
                onSelect = { viewModel.handleEvent(ServerConnectEvent.LoadSavedServer(it)) },
                onDelete = { viewModel.handleEvent(ServerConnectEvent.DeleteSavedServer(it)) },
                onDismiss = { viewModel.handleEvent(ServerConnectEvent.HideSavedServersDialog) },
                onNewConnection = {
                    viewModel.handleEvent(ServerConnectEvent.HideSavedServersDialog)
                    viewModel.handleEvent(ServerConnectEvent.ShowConnectionDialog)
                }
            )
        }
    }
}

// ─── Top App Bar ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SMBTopBar(
    uiState: ServerConnectionState,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onCreateFolder: () -> Unit,
    onUploadFile: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleSort: () -> Unit,
    onShowSaved: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (uiState.isMultiSelectMode) {
                Text(
                    "${uiState.selectedFiles.size} selected",
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    "SMB Explorer",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (uiState.isMultiSelectMode) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                }
            }
        },
        actions = {
            if (uiState.isConnected) {
                if (uiState.isMultiSelectMode) {
                    if (uiState.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                } else {
                    IconButton(onClick = onToggleSort) {
                        Icon(
                            if (uiState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Toggle sort"
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    if (uiState.breadcrumbs.size > 1) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Navigate up")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Upload File") },
                            leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                            onClick = { showMenu = false; onUploadFile() }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Folder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = { showMenu = false; onCreateFolder() }
                        )
                        DropdownMenuItem(
                            text = { Text("Multi Select") },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                            onClick = { showMenu = false; onToggleMultiSelect() }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
                            onClick = { showMenu = false; onDisconnect() }
                        )
                    }
                }
            } else {
                IconButton(onClick = onShowSaved) {
                    Icon(Icons.Default.Bookmarks, contentDescription = "Saved servers")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// ─── FAB ─────────────────────────────────────────────────────────────────────

@Composable
private fun SMBFab(onUpload: () -> Unit, onNewFolder: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                SmallFabItem(Icons.Default.CreateNewFolder, "New Folder", onNewFolder) { expanded = false }
                SmallFabItem(Icons.Default.Upload, "Upload File", onUpload) { expanded = false }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Actions")
        }
    }
}

@Composable
private fun SmallFabItem(icon: ImageVector, label: String, action: () -> Unit, collapse: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = { action(); collapse() },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Connection Status + Breadcrumbs ─────────────────────────────────────────

@Composable
private fun ConnectionStatusSection(uiState: ServerConnectionState, onBreadcrumbClick: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "\\\\${uiState.currentServer}\\${uiState.currentShare}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.fileList.isNotEmpty()) {
                    Text(
                        text = "${uiState.fileList.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            if (uiState.breadcrumbs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(uiState.breadcrumbs) { index, crumb ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 0) {
                                Icon(
                                    Icons.Default.ChevronRight, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            val isLast = index == uiState.breadcrumbs.lastIndex
                            AssistChip(
                                onClick = { onBreadcrumbClick(index) },
                                label = {
                                    Text(
                                        text = crumb,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.heightIn(max = 28.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isLast) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                    labelColor = if (isLast) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Transfer Panel ───────────────────────────────────────────────────────────

@Composable
private fun TransferPanel(stats: TransferStats, onCancel: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header row: icon + filename + cancel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (stats.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (stats.isUpload) "Uploading" else "Downloading",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stats.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Cancel, contentDescription = "Cancel transfer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { stats.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(8.dp))

            // Stats row 1: transferred / total and percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stats.formattedTransferred,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(stats.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(6.dp))

            // Stats row 2: speed chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedChip(label = "Now", value = stats.formattedCurrentSpeed)
                SpeedChip(label = "Avg", value = stats.formattedAvgSpeed)
                SpeedChip(label = "Peak", value = stats.formattedPeakSpeed)
                Spacer(Modifier.weight(1f))
                // ETA / elapsed
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stats.formattedElapsed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    if (stats.etaSeconds >= 0) {
                        Text(
                            text = "ETA ${stats.formattedEta}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(
                text = "$label ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─── Loading ──────────────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─── Disconnected ─────────────────────────────────────────────────────────────

@Composable
private fun DisconnectedScreen(onConnect: () -> Unit, onShowSaved: () -> Unit, hasSavedServers: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.CloudOff, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Not Connected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect to an SMB server to browse and manage files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect to Server")
                }
                if (hasSavedServers) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onShowSaved,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Saved Servers")
                    }
                }
            }
        }
    }
}

// ─── Empty Directory ──────────────────────────────────────────────────────────

@Composable
private fun EmptyDirectoryScreen(onUploadFile: () -> Unit, onCreateFolder: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Empty Directory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "This directory is empty. You can upload files or create new folders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCreateFolder, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Folder")
                }
                Button(onClick = onUploadFile, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload")
                }
            }
        }
    }
}

// ─── File List ────────────────────────────────────────────────────────────────

@Composable
private fun FileListSection(
    fileList: List<SMBFileItem>,
    selectedFiles: Set<String>,
    isMultiSelectMode: Boolean,
    onFileClick: (SMBFileItem) -> Unit,
    onFileLongClick: (SMBFileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(fileList, key = { it.name }) { fileItem ->
            FileListItem(
                fileItem = fileItem,
                isSelected = selectedFiles.contains(fileItem.name),
                isMultiSelectMode = isMultiSelectMode,
                onClick = { onFileClick(fileItem) },
                onLongClick = { onFileLongClick(fileItem) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    fileItem: SMBFileItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent,
        label = "itemBg"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            FileIcon(fileItem = fileItem, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!fileItem.isDirectory) {
                        Text(
                            text = fileItem.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(" • ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = fileItem.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isMultiSelectMode) {
                if (fileItem.isDirectory) {
                    Icon(
                        Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(onClick = onClick) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = if (isMultiSelectMode) 88.dp else 72.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

// ─── File Icon ────────────────────────────────────────────────────────────────

@Composable
private fun FileIcon(fileItem: SMBFileItem, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        fileItem.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.primary
        else -> when (fileItem.fileExtension.lowercase()) {
            "pdf" -> Icons.Default.PictureAsPdf to Color.Red
            "doc", "docx" -> Icons.Default.Description to Color.Blue
            "xls", "xlsx" -> Icons.Default.TableChart to Color(0xFF2E7D32)
            "ppt", "pptx" -> Icons.Default.Slideshow to Color(0xFFE65100)
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> Icons.Default.Image to Color.Magenta
            "mp4", "avi", "mkv", "mov", "wmv" -> Icons.Default.Movie to Color.Cyan
            "mp3", "wav", "flac", "aac" -> Icons.Default.AudioFile to Color(0xFFFF9800)
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive to Color(0xFF78909C)
            "txt", "md", "log" -> Icons.Default.TextSnippet to MaterialTheme.colorScheme.onSurfaceVariant
            "apk", "exe", "msi" -> Icons.Default.Apps to Color(0xFF66BB6A)
            else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}

// ─── Connection Dialog ────────────────────────────────────────────────────────

@Composable
private fun ConnectionDialog(
    uiState: ServerConnectionState,
    onEvent: (ServerConnectEvent) -> Unit,
    onConnect: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onEvent(ServerConnectEvent.HideConnectionDialog) },
        title = { Text("Connect to SMB Server", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SMBTextField(
                    value = uiState.connectionLabel,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateConnectionLabel(it)) },
                    label = "Label (optional)",
                    placeholder = "Home NAS, Work Server…",
                    icon = Icons.Default.Label
                )
                SMBTextField(
                    value = uiState.serverAddress,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateServerAddress(it)) },
                    label = "Server Address *",
                    placeholder = "192.168.1.100 or server-name",
                    icon = Icons.Default.Computer
                )
                SMBTextField(
                    value = uiState.shareName,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateShareName(it)) },
                    label = "Share Name",
                    placeholder = "shared-folder",
                    icon = Icons.Default.FolderShared
                )
                SMBTextField(
                    value = uiState.username,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateUsername(it)) },
                    label = "Username *",
                    placeholder = "administrator",
                    icon = Icons.Default.Person
                )
                SMBTextField(
                    value = uiState.password,
                    onValueChange = { onEvent(ServerConnectEvent.UpdatePassword(it)) },
                    label = "Password",
                    placeholder = "••••••••",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )
                Text(
                    "Credentials are saved automatically for future use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConnect,
                enabled = uiState.serverAddress.isNotBlank() && uiState.username.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ServerConnectEvent.HideConnectionDialog) }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SMBTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility"
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None
    )
}

// ─── Create Folder Dialog ─────────────────────────────────────────────────────

@Composable
private fun CreateFolderDialog(
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = { Text("Folder Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = folderName.trim().isNotEmpty()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Saved Servers Dialog ─────────────────────────────────────────────────────

@Composable
private fun SavedServersDialog(
    servers: List<SavedServer>,
    onSelect: (SavedServer) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onNewConnection: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmarks, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Saved Servers", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            if (servers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No saved servers yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f).clickable { onSelect(server) }) {
                                    Text(
                                        server.label.ifBlank { server.serverAddress },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "\\\\${server.serverAddress}${if (server.shareName.isNotEmpty()) "\\${server.shareName}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        server.username,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(server.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNewConnection) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Connection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}