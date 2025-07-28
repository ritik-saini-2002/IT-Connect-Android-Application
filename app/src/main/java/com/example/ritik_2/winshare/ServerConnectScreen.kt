package com.example.ritik_2.winshare

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                viewModel.uploadFile(it, context)
            }
        }
    }

    // Handle back navigation
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { handled ->
            if (!handled) {
                onNavigateBack()
            }
        }
    }

    // Handle error and success messages
    LaunchedEffect(Unit) {
        viewModel.errorMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.successMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ServerConnectTopBar(
                uiState = uiState,
                onRefresh = { viewModel.refreshFiles() },
                onNavigateUp = { viewModel.navigateUp() },
                onToggleMultiSelect = { viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode) },
                onCreateFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) },
                onUploadFile = { filePickerLauncher.launch("*/*") },
                onDeleteSelected = { viewModel.deleteSelectedFiles() },
                onDisconnect = { viewModel.disconnect() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Connection status and breadcrumbs
            if (uiState.isConnected) {
                ConnectionStatusSection(
                    uiState = uiState,
                    onBreadcrumbClick = { index -> viewModel.navigateToBreadcrumb(index) }
                )
            }

            // Upload progress
            if (uiState.isUploading) {
                UploadProgressSection(progress = uiState.uploadProgress)
            }

            // Main content
            when {
                uiState.isLoading && !uiState.isUploading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                !uiState.isConnected -> {
                    DisconnectedScreen(
                        onConnect = { viewModel.handleEvent(ServerConnectEvent.ShowConnectionDialog) }
                    )
                }
                uiState.fileList.isEmpty() -> {
                    EmptyDirectoryScreen(
                        onUploadFile = { filePickerLauncher.launch("*/*") },
                        onCreateFolder = { viewModel.handleEvent(ServerConnectEvent.ShowCreateFolderDialog) }
                    )
                }
                else -> {
                    FileListSection(
                        fileList = uiState.fileList,
                        selectedFiles = uiState.selectedFiles,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        onFileClick = { fileItem ->
                            if (uiState.isMultiSelectMode) {
                                viewModel.handleEvent(ServerConnectEvent.ToggleFileSelection(fileItem.name))
                            } else {
                                if (fileItem.isDirectory) {
                                    viewModel.navigateToDirectory(fileItem.name)
                                } else {
                                    scope.launch {
                                        val uri = viewModel.downloadFile(fileItem, context)
                                        uri?.let {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(it, context.contentResolver.getType(it))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Handle case where no app can open the file
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onFileLongClick = { fileItem ->
                            if (!uiState.isMultiSelectMode) {
                                viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode)
                            }
                            viewModel.handleEvent(ServerConnectEvent.ToggleFileSelection(fileItem.name))
                        }
                    )
                }
            }
        }

        // Dialogs
        if (uiState.showConnectionDialog) {
            ConnectionDialog(
                uiState = uiState,
                onEvent = viewModel::handleEvent,
                onConnect = { scope.launch { viewModel.connectToServer() } }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConnectTopBar(
    uiState: ServerConnectionState,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onCreateFolder: () -> Unit,
    onUploadFile: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDisconnect: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (uiState.isMultiSelectMode)
                    "${uiState.selectedFiles.size} selected"
                else "SMB Explorer",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Navigate Up")
                    }

                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Upload File") },
                            onClick = {
                                showMenu = false
                                onUploadFile()
                            },
                            leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Folder") },
                            onClick = {
                                showMenu = false
                                onCreateFolder()
                            },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Multi Select") },
                            onClick = {
                                showMenu = false
                                onToggleMultiSelect()
                            },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = {
                                showMenu = false
                                onDisconnect()
                            },
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null) }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun ConnectionStatusSection(
    uiState: ServerConnectionState,
    onBreadcrumbClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Connection status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Connected",
                    tint = Color.Green,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\\\\${uiState.currentServer}\\${uiState.currentShare}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // Breadcrumbs
            if (uiState.breadcrumbs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(uiState.breadcrumbs) { index, breadcrumb ->
                        BreadcrumbChip(
                            text = breadcrumb,
                            isLast = index == uiState.breadcrumbs.lastIndex,
                            onClick = { onBreadcrumbClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbChip(
    text: String,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isLast)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface,
                labelColor = if (isLast)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        )
        if (!isLast) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UploadProgressSection(progress: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Uploading",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Uploading file...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DisconnectedScreen(onConnect: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Not Connected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to an SMB server to browse and manage files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Server")
                }
            }
        }
    }
}

@Composable
private fun EmptyDirectoryScreen(
    onUploadFile: () -> Unit,
    onCreateFolder: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Empty Directory",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This directory is empty. You can upload files or create new folders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateFolder,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Folder")
                }
                Button(
                    onClick = onUploadFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload File")
                }
            }
        }
    }
}

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
        items(fileList) { fileItem ->
            FileListItem(
                fileItem = fileItem,
                isSelected = selectedFiles.contains(fileItem.name),
                isMultiSelectMode = isMultiSelectMode,
                onClick = { onFileClick(fileItem) },
                onLongClick = { onFileLongClick(fileItem) }
            )
        }
    }
}

@Composable
private fun FileListItem(
    fileItem: SMBFileItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isMultiSelectMode) {
                    Modifier.selectable(
                        selected = isSelected,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            if (isMultiSelectMode) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Not selected",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // File icon
            FileIcon(
                fileItem = fileItem,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!fileItem.isDirectory) {
                        Text(
                            text = fileItem.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = fileItem.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action indicator
            if (!isMultiSelectMode) {
                if (fileItem.isDirectory) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Navigate into folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download file"
                        )
                    }
                }
            }
        }
    }

    Divider(
        modifier = Modifier.padding(start = if (isMultiSelectMode) 88.dp else 72.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
private fun FileIcon(
    fileItem: SMBFileItem,
    modifier: Modifier = Modifier
) {
    val icon = if (fileItem.isDirectory) {
        Icons.Default.Folder
    } else {
        when (fileItem.fileExtension.lowercase()) {
            "pdf" -> Icons.Default.PictureAsPdf
            "doc", "docx" -> Icons.Default.Description
            "xls", "xlsx" -> Icons.Default.TableChart
            "ppt", "pptx" -> Icons.Default.Slideshow
            "jpg", "jpeg", "png", "gif", "bmp" -> Icons.Default.Image
            "mp4", "avi", "mkv", "mov" -> Icons.Default.Movie
            "mp3", "wav", "flac", "aac" -> Icons.Default.AudioFile
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive
            "txt", "md", "log" -> Icons.Default.TextSnippet
            "exe", "msi", "deb", "rpm" -> Icons.Default.Apps
            else -> Icons.Default.InsertDriveFile
        }
    }

    val iconColor = if (fileItem.isDirectory) {
        MaterialTheme.colorScheme.primary
    } else {
        when (fileItem.fileExtension.lowercase()) {
            "pdf" -> Color.Red
            "doc", "docx" -> Color.Blue
            "xls", "xlsx" -> Color.Green
            "jpg", "jpeg", "png", "gif", "bmp" -> Color.Magenta
            "mp4", "avi", "mkv", "mov" -> Color.Cyan
            "mp3", "wav", "flac", "aac" -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Box(
        modifier = modifier
            .background(
                iconColor.copy(alpha = 0.1f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ConnectionDialog(
    uiState: ServerConnectionState,
    onEvent: (ServerConnectEvent) -> Unit,
    onConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onEvent(ServerConnectEvent.HideConnectionDialog) },
        title = {
            Text(
                "Connect to SMB Server",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.serverAddress,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateServerAddress(it)) },
                    label = { Text("Server Address") },
                    placeholder = { Text("192.168.1.100 or server-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Computer, contentDescription = null)
                    }
                )

                OutlinedTextField(
                    value = uiState.shareName,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateShareName(it)) },
                    label = { Text("Share Name") },
                    placeholder = { Text("shared-folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.FolderShared, contentDescription = null)
                    }
                )

                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = { onEvent(ServerConnectEvent.UpdateUsername(it)) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { onEvent(ServerConnectEvent.UpdatePassword(it)) },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConnect,
                enabled = uiState.serverAddress.isNotBlank() &&
                        //uiState.shareName.isNotBlank() &&
                        uiState.username.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ServerConnectEvent.HideConnectionDialog) }
            ) {
                Text("Cancel")
            }
        }
    )
}

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
                leadingIcon = {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = folderName.trim().isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}