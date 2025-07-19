package com.example.ritik_2.modules

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

data class SMBFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean = true,
    val canWrite: Boolean = true
) {
    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
                size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
                else -> "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }

    val formattedDate: String
        get() {
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return formatter.format(Date(lastModified))
        }

    val fileExtension: String
        get() = if (isDirectory) "" else name.substringAfterLast(".", "")
}

data class ServerConnectionState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val currentServer: String = "",
    val currentShare: String = "",
    val currentPath: String = "",
    val breadcrumbs: List<String> = emptyList(),
    val fileList: List<SMBFileItem> = emptyList(),
    val showConnectionDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val serverAddress: String = "",
    val username: String = "",
    val password: String = "",
    val shareName: String = "",
    val selectedFiles: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false
)

sealed class ServerConnectEvent {
    object ShowConnectionDialog : ServerConnectEvent()
    object HideConnectionDialog : ServerConnectEvent()
    object ShowCreateFolderDialog : ServerConnectEvent()
    object HideCreateFolderDialog : ServerConnectEvent()
    object ToggleMultiSelectMode : ServerConnectEvent()
    object ClearSelection : ServerConnectEvent()
    data class UpdateServerAddress(val address: String) : ServerConnectEvent()
    data class UpdateUsername(val username: String) : ServerConnectEvent()
    data class UpdatePassword(val password: String) : ServerConnectEvent()
    data class UpdateShareName(val share: String) : ServerConnectEvent()
    data class UpdateNewFolderName(val name: String) : ServerConnectEvent()
    data class ToggleFileSelection(val fileName: String) : ServerConnectEvent()
}

class ServerConnectModule : ViewModel() {
    private val _uiState = MutableStateFlow(ServerConnectionState())
    val uiState: StateFlow<ServerConnectionState> = _uiState.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages

    private val _successMessages = MutableSharedFlow<String>()
    val successMessages: SharedFlow<String> = _successMessages

    private val _navigationEvents = MutableSharedFlow<Boolean>()
    val navigationEvents: SharedFlow<Boolean> = _navigationEvents

    private var cifsContext: CIFSContext? = null
    private val pathStack = mutableListOf<String>()

    fun handleEvent(event: ServerConnectEvent) {
        when (event) {
            is ServerConnectEvent.ShowConnectionDialog -> {
                _uiState.update { it.copy(showConnectionDialog = true) }
            }
            is ServerConnectEvent.HideConnectionDialog -> {
                _uiState.update { it.copy(showConnectionDialog = false) }
            }
            is ServerConnectEvent.ShowCreateFolderDialog -> {
                _uiState.update { it.copy(showCreateFolderDialog = true, newFolderName = "") }
            }
            is ServerConnectEvent.HideCreateFolderDialog -> {
                _uiState.update { it.copy(showCreateFolderDialog = false, newFolderName = "") }
            }
            is ServerConnectEvent.ToggleMultiSelectMode -> {
                _uiState.update {
                    it.copy(
                        isMultiSelectMode = !it.isMultiSelectMode,
                        selectedFiles = emptySet()
                    )
                }
            }
            is ServerConnectEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedFiles = emptySet()) }
            }
            is ServerConnectEvent.UpdateServerAddress -> {
                _uiState.update { it.copy(serverAddress = event.address) }
            }
            is ServerConnectEvent.UpdateUsername -> {
                _uiState.update { it.copy(username = event.username) }
            }
            is ServerConnectEvent.UpdatePassword -> {
                _uiState.update { it.copy(password = event.password) }
            }
            is ServerConnectEvent.UpdateShareName -> {
                _uiState.update { it.copy(shareName = event.share) }
            }
            is ServerConnectEvent.UpdateNewFolderName -> {
                _uiState.update { it.copy(newFolderName = event.name) }
            }
            is ServerConnectEvent.ToggleFileSelection -> {
                _uiState.update { state ->
                    val newSelection = if (state.selectedFiles.contains(event.fileName)) {
                        state.selectedFiles - event.fileName
                    } else {
                        state.selectedFiles + event.fileName
                    }
                    state.copy(selectedFiles = newSelection)
                }
            }
        }
    }

    suspend fun connectToServer() {
        viewModelScope.launch {
            val server = _uiState.value.serverAddress
            val username = _uiState.value.username
            val password = _uiState.value.password
            val share = _uiState.value.shareName

            try {
                _uiState.update { it.copy(isLoading = true, showConnectionDialog = false) }

                withContext(Dispatchers.IO) {
                    val props = Properties()
                    props.setProperty("jcifs.smb.client.responseTimeout", "30000")
                    props.setProperty("jcifs.smb.client.soTimeout", "35000")
                    props.setProperty("jcifs.smb.client.connTimeout", "60000")
                    props.setProperty("jcifs.smb.client.dfs.disabled", "true")

                    val baseContext = BaseContext(PropertyConfiguration(props))
                    cifsContext = baseContext.withCredentials(
                        NtlmPasswordAuthenticator(null, username, password)
                    )

                    val url = if (share.isEmpty()) {
                        "smb://$server/"
                    } else {
                        "smb://$server/$share/"
                    }
                    val smbFile = SmbFile(url, cifsContext)
                    smbFile.connect()

                    pathStack.clear()
                    pathStack.add("")

                    val fileList = if (share.isEmpty()) {
                        smbFile.listFiles()?.map { file ->
                            SMBFileItem(
                                name = file.name.removeSuffix("/"),
                                path = file.path,
                                isDirectory = file.isDirectory,
                                size = 0,
                                lastModified = file.lastModified()
                            )
                        } ?: emptyList()
                    } else {
                        listFiles(server, share, "")
                    }
                    val breadcrumbs = if (share.isEmpty()) emptyList() else listOf(share)

                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            isLoading = false,
                            currentServer = server,
                            currentShare = share,
                            currentPath = "",
                            fileList = fileList,
                            breadcrumbs = breadcrumbs
                        )
                    }
                    _successMessages.emit(
                        if (share.isEmpty()) "Connected to $server. Select a share to continue."
                        else "Connected to $server\\$share successfully!"
                    )
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Connection error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Connection failed: ${e.message}")
            }
        }
    }

    fun navigateToDirectory(directoryName: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }

                val currentPath = if (_uiState.value.currentPath.isEmpty()) {
                    directoryName
                } else {
                    "${_uiState.value.currentPath}/$directoryName"
                }

                pathStack.add(currentPath)

                val fileList = listFiles(
                    _uiState.value.currentServer,
                    _uiState.value.currentShare,
                    currentPath
                )

                val breadcrumbs = listOf(_uiState.value.currentShare) +
                        currentPath.split("/").filter { it.isNotEmpty() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = currentPath,
                        fileList = fileList,
                        breadcrumbs = breadcrumbs
                    )
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Navigation error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Navigation error: ${e.message}")
            }
        }
    }

    fun navigateUp(): Boolean {
        return try {
            if (pathStack.size <= 1) {
                // At root level, allow system back navigation
                false
            } else {
                viewModelScope.launch {
                    try {
                        _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }

                        pathStack.removeAt(pathStack.size - 1)
                        val parentPath = pathStack.last()

                        val fileList = listFiles(
                            _uiState.value.currentServer,
                            _uiState.value.currentShare,
                            parentPath
                        )

                        val breadcrumbs = if (parentPath.isEmpty()) {
                            listOf(_uiState.value.currentShare)
                        } else {
                            listOf(_uiState.value.currentShare) +
                                    parentPath.split("/").filter { it.isNotEmpty() }
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentPath = parentPath,
                                fileList = fileList,
                                breadcrumbs = breadcrumbs
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("ServerConnectModule", "Navigation error", e)
                        _uiState.update { it.copy(isLoading = false) }
                        _errorMessages.emit("Navigation error: ${e.message}")
                    }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }

                val targetPath = if (index == 0) {
                    ""
                } else {
                    _uiState.value.breadcrumbs.drop(1).take(index).joinToString("/")
                }

                // Update path stack
                pathStack.clear()
                if (targetPath.isEmpty()) {
                    pathStack.add("")
                } else {
                    pathStack.add("")
                    val pathParts = targetPath.split("/")
                    for (i in pathParts.indices) {
                        pathStack.add(pathParts.take(i + 1).joinToString("/"))
                    }
                }

                val fileList = listFiles(
                    _uiState.value.currentServer,
                    _uiState.value.currentShare,
                    targetPath
                )

                val breadcrumbs = _uiState.value.breadcrumbs.take(index + 1)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = targetPath,
                        fileList = fileList,
                        breadcrumbs = breadcrumbs
                    )
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Breadcrumb navigation error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Navigation error: ${e.message}")
            }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val fileList = listFiles(
                    _uiState.value.currentServer,
                    _uiState.value.currentShare,
                    _uiState.value.currentPath
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fileList = fileList
                    )
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Refresh error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Refresh error: ${e.message}")
            }
        }
    }

    fun createFolder() {
        viewModelScope.launch {
            try {
                val folderName = _uiState.value.newFolderName.trim()
                if (folderName.isEmpty()) {
                    _errorMessages.emit("Folder name cannot be empty")
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, showCreateFolderDialog = false) }

                withContext(Dispatchers.IO) {
                    val path = if (_uiState.value.currentPath.isEmpty()) {
                        folderName
                    } else {
                        "${_uiState.value.currentPath}/$folderName"
                    }

                    val url = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path/"
                    val smbFile = SmbFile(url, cifsContext)
                    smbFile.mkdir()
                }

                refreshFiles()
                _successMessages.emit("Folder '$folderName' created successfully!")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Create folder error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Failed to create folder: ${e.message}")
            }
        }
    }

    suspend fun uploadFile(uri: Uri, context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isUploading = true, uploadProgress = 0f) }

                val contentResolver = context.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) it.getString(nameIndex) else "uploaded_file"
                    } else "uploaded_file"
                } ?: "uploaded_file"

                val targetPath = if (_uiState.value.currentPath.isEmpty()) {
                    fileName
                } else {
                    "${_uiState.value.currentPath}/$fileName"
                }

                val url = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$targetPath"
                val smbFile = SmbFile(url, cifsContext)

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    smbFile.outputStream.use { outputStream ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0
                        val fileSize = inputStream.available()

                        var bytes = inputStream.read(buffer)
                        while (bytes != -1) {
                            outputStream.write(buffer, 0, bytes)
                            totalBytes += bytes

                            if (fileSize > 0) {
                                val progress = (totalBytes.toFloat() / fileSize.toFloat())
                                _uiState.update { it.copy(uploadProgress = progress) }
                            }

                            bytes = inputStream.read(buffer)
                        }
                    }
                }

                _uiState.update { it.copy(isUploading = false, uploadProgress = 0f) }
                viewModelScope.launch {
                    refreshFiles()
                    _successMessages.emit("File '$fileName' uploaded successfully!")
                }

                true
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Upload error", e)
                _uiState.update { it.copy(isUploading = false, uploadProgress = 0f) }
                viewModelScope.launch {
                    _errorMessages.emit("Upload failed: ${e.message}")
                }
                false
            }
        }
    }

    suspend fun downloadFile(fileItem: SMBFileItem, context: Context): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/${_uiState.value.currentPath}/${fileItem.name}"
                val smbFile = SmbFile(url, cifsContext)

                val downloadsDir = File(context.cacheDir, "smb_downloads")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val localFile = File(downloadsDir, fileItem.name)

                smbFile.inputStream.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )

                viewModelScope.launch {
                    _successMessages.emit("File '${fileItem.name}' downloaded successfully!")
                }

                uri
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Download error", e)
                viewModelScope.launch {
                    _errorMessages.emit("Download failed: ${e.message}")
                }
                null
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            try {
                val selectedFiles = _uiState.value.selectedFiles
                if (selectedFiles.isEmpty()) return@launch

                _uiState.update { it.copy(isLoading = true) }

                withContext(Dispatchers.IO) {
                    selectedFiles.forEach { fileName ->
                        val path = if (_uiState.value.currentPath.isEmpty()) {
                            fileName
                        } else {
                            "${_uiState.value.currentPath}/$fileName"
                        }

                        val url = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path"
                        val smbFile = SmbFile(url, cifsContext)

                        if (smbFile.isDirectory) {
                            // Delete directory recursively
                            deleteDirectoryRecursively(smbFile)
                        } else {
                            smbFile.delete()
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        selectedFiles = emptySet(),
                        isMultiSelectMode = false
                    )
                }

                refreshFiles()
                _successMessages.emit("${selectedFiles.size} item(s) deleted successfully!")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Delete error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Delete failed: ${e.message}")
            }
        }
    }

    private suspend fun deleteDirectoryRecursively(smbFile: SmbFile) {
        val files = smbFile.listFiles()
        files?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectoryRecursively(file)
            } else {
                file.delete()
            }
        }
        smbFile.delete()
    }

    private suspend fun listFiles(server: String, share: String, path: String): List<SMBFileItem> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "smb://$server/$share/${if (path.isNotEmpty()) "$path/" else ""}"
                val smbFile = SmbFile(url, cifsContext)

                smbFile.listFiles()?.map { file ->
                    SMBFileItem(
                        name = file.name.removeSuffix("/"),
                        path = file.path,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0 else file.length(),
                        lastModified = file.lastModified(),
                        canRead = file.canRead(),
                        canWrite = file.canWrite()
                    )
                }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            } catch (e: SmbException) {
                Log.e("ServerConnectModule", "List files error", e)
                throw e
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                cifsContext = null
                pathStack.clear()

                _uiState.update {
                    ServerConnectionState(
                        serverAddress = it.serverAddress,
                        username = it.username,
                        password = it.password,
                        shareName = it.shareName
                    )
                }

                _successMessages.emit("Disconnected from server")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Disconnect error", e)
                _errorMessages.emit("Disconnect error: ${e.message}")
            }
        }
    }

    fun canNavigateBack(): Boolean {
        return pathStack.size > 1
    }
}