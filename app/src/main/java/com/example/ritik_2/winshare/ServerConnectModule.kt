package com.example.ritik_2.winshare

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.winshare.transfer.HttpFileTransferClient
import com.example.ritik_2.winshare.transfer.TransferProgress
import com.example.ritik_2.winshare.transfer.TransferResult
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

// ─── Data Models ────────────────────────────────────────────────────────────

data class SavedServer(
    val id: String,
    val label: String,
    val serverAddress: String,
    val username: String,
    val password: String,
    val shareName: String
)

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
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.2f", size / (1024.0 * 1024.0 * 1024.0))} GB"
        }

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))

    val fileExtension: String
        get() = if (isDirectory) "" else name.substringAfterLast(".", "")
}

data class TransferStats(
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val currentSpeedBytesPerSec: Long = 0L,
    val peakSpeedBytesPerSec: Long = 0L,
    val avgSpeedBytesPerSec: Long = 0L,
    val elapsedMs: Long = 0L,
    val isUpload: Boolean = true
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val etaSeconds: Long
        get() = if (currentSpeedBytesPerSec > 0 && totalBytes > transferredBytes)
            (totalBytes - transferredBytes) / currentSpeedBytesPerSec
        else -1L

    val formattedEta: String
        get() = when {
            etaSeconds < 0 -> "–"
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }

    val formattedElapsed: String
        get() {
            val s = elapsedMs / 1000
            return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
        }

    val formattedTransferred: String
        get() = "${fmtBytes(transferredBytes)} / ${fmtBytes(totalBytes)}"

    val formattedCurrentSpeed: String get() = fmtSpeed(currentSpeedBytesPerSec)
    val formattedPeakSpeed: String    get() = fmtSpeed(peakSpeedBytesPerSec)
    val formattedAvgSpeed: String     get() = fmtSpeed(avgSpeedBytesPerSec)

    private fun fmtBytes(b: Long): String = when {
        b <= 0 -> "0 B"
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${String.format("%.1f", b / 1024.0)} KB"
        b < 1024L * 1024 * 1024 -> "${String.format("%.1f", b / (1024.0 * 1024.0))} MB"
        else -> "${String.format("%.2f", b / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    private fun fmtSpeed(bps: Long): String = when {
        bps <= 0 -> "0 B/s"
        bps < 1024 -> "$bps B/s"
        bps < 1024 * 1024 -> "${String.format("%.1f", bps / 1024.0)} KB/s"
        bps < 1024L * 1024 * 1024 -> "${String.format("%.1f", bps / (1024.0 * 1024.0))} MB/s"
        else -> "${String.format("%.2f", bps / (1024.0 * 1024.0 * 1024.0))} GB/s"
    }
}

data class ServerConnectionState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val currentServer: String = "",
    val currentShare: String = "",
    val currentPath: String = "",
    val breadcrumbs: List<String> = emptyList(),
    val fileList: List<SMBFileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isTransferring: Boolean = false,
    val transferStats: TransferStats = TransferStats(),
    val showConnectionDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showSavedServersDialog: Boolean = false,
    val newFolderName: String = "",
    val serverAddress: String = "",
    val username: String = "",
    val password: String = "",
    val shareName: String = "",
    val connectionLabel: String = "",
    val savedServers: List<SavedServer> = emptyList(),
    val sortByName: Boolean = true,
    val sortAscending: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showMoveDialog: Boolean = false,
    val showPropertiesDialog: Boolean = false,
    val showFileContextMenu: Boolean = false,
    val showEditServerDialog: Boolean = false,
    val contextMenuFile: SMBFileItem? = null,
    val renameTarget: SMBFileItem? = null,
    val renameNewName: String = "",
    val moveTarget: SMBFileItem? = null,
    val propertiesTarget: SMBFileItem? = null,
    val moveDestination: String = "",
    val editingServer: SavedServer? = null,
    val autoConnectServerId: String? = null
)

sealed class ServerConnectEvent {
    object ShowConnectionDialog : ServerConnectEvent()
    object HideConnectionDialog : ServerConnectEvent()
    object ShowCreateFolderDialog : ServerConnectEvent()
    object HideCreateFolderDialog : ServerConnectEvent()
    object ShowSavedServersDialog : ServerConnectEvent()
    object HideSavedServersDialog : ServerConnectEvent()
    object ToggleMultiSelectMode : ServerConnectEvent()
    object ClearSelection : ServerConnectEvent()
    object ToggleSortOrder : ServerConnectEvent()
    object CancelTransfer : ServerConnectEvent()
    data class UpdateServerAddress(val address: String) : ServerConnectEvent()
    data class UpdateUsername(val username: String) : ServerConnectEvent()
    data class UpdatePassword(val password: String) : ServerConnectEvent()
    data class UpdateShareName(val share: String) : ServerConnectEvent()
    data class UpdateNewFolderName(val name: String) : ServerConnectEvent()
    data class UpdateConnectionLabel(val label: String) : ServerConnectEvent()
    data class ToggleFileSelection(val fileName: String) : ServerConnectEvent()
    data class LoadSavedServer(val server: SavedServer) : ServerConnectEvent()
    data class DeleteSavedServer(val id: String) : ServerConnectEvent()
    data class UpdateSearchQuery(val query: String) : ServerConnectEvent()
    object ToggleSearch : ServerConnectEvent()
    data class ShowFileContextMenu(val file: SMBFileItem) : ServerConnectEvent()
    object HideFileContextMenu : ServerConnectEvent()
    data class ShowRenameDialog(val file: SMBFileItem) : ServerConnectEvent()
    object HideRenameDialog : ServerConnectEvent()
    data class UpdateRenameName(val name: String) : ServerConnectEvent()
    data class ShowMoveDialog(val file: SMBFileItem) : ServerConnectEvent()
    object HideMoveDialog : ServerConnectEvent()
    data class UpdateMoveDestination(val dest: String) : ServerConnectEvent()
    data class ShowPropertiesDialog(val file: SMBFileItem) : ServerConnectEvent()
    object HidePropertiesDialog : ServerConnectEvent()
    data class ShowEditServerDialog(val server: SavedServer) : ServerConnectEvent()
    object HideEditServerDialog : ServerConnectEvent()
    data class AutoConnectServer(val serverId: String) : ServerConnectEvent()
    // Long-press enters multi-select and selects that file
    data class LongPressFile(val fileName: String) : ServerConnectEvent()
}

// ─── Constants ───────────────────────────────────────────────────────────────

private const val PREFS_NAME        = "winshare_prefs"
private const val KEY_SAVED_SERVERS = "saved_servers"

// LOCAL read buffer: large chunks from ContentResolver / local disk (fast)
private const val READ_BUFFER = 8 * 1024 * 1024       // 8 MB

// SMB read buffer: must stay within the server's negotiated MaxReadSize.
// Most SMB2/3 servers cap at 64KB–1MB. Using 60KB is safe for ALL servers
// (including older ones with 64KB limit). jCIFS wraps this into SMB2_READ
// commands — if the buffer exceeds MaxReadSize the server returns
// "The parameter is incorrect" (STATUS_INVALID_PARAMETER).
private const val SMB_READ_BUFFER = 60 * 1024          // 60 KB — safe for all SMB servers

// SMB write chunk: same constraint as reads — stay within MaxWriteSize.
private const val SMB_WRITE_CHUNK = 60 * 1024          // 60 KB

// Local file write buffer (downloading FROM smb TO local disk)
private const val LOCAL_WRITE_BUFFER = 4 * 1024 * 1024 // 4 MB

// HTTP transfer server port — must match the Windows server (default 8765)
private const val HTTP_TRANSFER_PORT = 8765

// ─── ViewModel ──────────────────────────────────────────────────────────────

class ServerConnectModule : ViewModel() {

    private val _uiState = MutableStateFlow(ServerConnectionState())
    val uiState: StateFlow<ServerConnectionState> = _uiState.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages

    private val _successMessages = MutableSharedFlow<String>()
    val successMessages: SharedFlow<String> = _successMessages

    private var cifsContext: CIFSContext? = null
    private val pathStack = mutableListOf<String>()
    private var activeTransferJob: Job? = null
    private var unfilteredFileList: List<SMBFileItem> = emptyList()

    // High-speed HTTP transfer client — created after SMB connects (same server IP).
    // Upload/Download uses HTTP (100–300 Mbps); browsing still uses SMB.
    private var httpClient: HttpFileTransferClient? = null

    // ─── SharedPreferences ──────────────────────────────────────────────────

    fun loadSavedServers(context: Context) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SAVED_SERVERS, "") ?: ""
            val servers = if (raw.isBlank()) emptyList() else parseSavedServers(raw)
            _uiState.update { it.copy(savedServers = servers) }
        }
    }

    private fun persistServers(context: Context, servers: List<SavedServer>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = servers.joinToString("|") { s ->
            listOf(s.id, s.label, s.serverAddress, s.username, s.password, s.shareName)
                .joinToString("::") { it.replace("::", "%%") }
        }
        prefs.edit().putString(KEY_SAVED_SERVERS, raw).apply()
    }

    private fun parseSavedServers(raw: String): List<SavedServer> =
        raw.split("|").mapNotNull { entry ->
            val p = entry.split("::").map { it.replace("%%", "::") }
            if (p.size == 6) SavedServer(p[0], p[1], p[2], p[3], p[4], p[5]) else null
        }

    fun saveCurrentServer(context: Context) {
        val s = _uiState.value
        if (s.serverAddress.isBlank() || s.username.isBlank()) return
        val label = s.connectionLabel.ifBlank { s.serverAddress }
        val newSrv = SavedServer(
            id = System.currentTimeMillis().toString(), label = label,
            serverAddress = s.serverAddress, username = s.username,
            password = s.password, shareName = s.shareName
        )
        val updated = _uiState.value.savedServers.filter { it.serverAddress != newSrv.serverAddress } + newSrv
        _uiState.update { it.copy(savedServers = updated) }
        persistServers(context, updated)
    }

    fun updateSavedServer(context: Context, server: SavedServer) {
        val updated = _uiState.value.savedServers.map { if (it.id == server.id) server else it }
        _uiState.update { it.copy(savedServers = updated, showEditServerDialog = false, editingServer = null) }
        persistServers(context, updated)
    }

    // ─── Events ─────────────────────────────────────────────────────────────

    fun handleEvent(event: ServerConnectEvent) {
        when (event) {
            is ServerConnectEvent.ShowConnectionDialog -> _uiState.update { it.copy(showConnectionDialog = true) }
            is ServerConnectEvent.HideConnectionDialog -> _uiState.update { it.copy(showConnectionDialog = false) }
            is ServerConnectEvent.ShowCreateFolderDialog -> _uiState.update { it.copy(showCreateFolderDialog = true, newFolderName = "") }
            is ServerConnectEvent.HideCreateFolderDialog -> _uiState.update { it.copy(showCreateFolderDialog = false, newFolderName = "") }
            is ServerConnectEvent.ShowSavedServersDialog -> _uiState.update { it.copy(showSavedServersDialog = true) }
            is ServerConnectEvent.HideSavedServersDialog -> _uiState.update { it.copy(showSavedServersDialog = false) }
            is ServerConnectEvent.ToggleMultiSelectMode ->
                _uiState.update { it.copy(isMultiSelectMode = !it.isMultiSelectMode, selectedFiles = emptySet()) }
            is ServerConnectEvent.ClearSelection -> _uiState.update { it.copy(selectedFiles = emptySet()) }
            is ServerConnectEvent.ToggleSortOrder ->
                _uiState.update {
                    it.copy(sortAscending = !it.sortAscending,
                        fileList = sortFiles(it.fileList, it.sortByName, !it.sortAscending))
                }
            is ServerConnectEvent.CancelTransfer -> {
                activeTransferJob?.cancel(); activeTransferJob = null
                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
            }
            is ServerConnectEvent.UpdateServerAddress -> _uiState.update { it.copy(serverAddress = event.address) }
            is ServerConnectEvent.UpdateUsername -> _uiState.update { it.copy(username = event.username) }
            is ServerConnectEvent.UpdatePassword -> _uiState.update { it.copy(password = event.password) }
            is ServerConnectEvent.UpdateShareName -> _uiState.update { it.copy(shareName = event.share) }
            is ServerConnectEvent.UpdateNewFolderName -> _uiState.update { it.copy(newFolderName = event.name) }
            is ServerConnectEvent.UpdateConnectionLabel -> _uiState.update { it.copy(connectionLabel = event.label) }
            is ServerConnectEvent.ToggleFileSelection ->
                _uiState.update { state ->
                    val sel = if (state.selectedFiles.contains(event.fileName))
                        state.selectedFiles - event.fileName else state.selectedFiles + event.fileName
                    state.copy(selectedFiles = sel)
                }
            is ServerConnectEvent.LoadSavedServer ->
                _uiState.update {
                    it.copy(serverAddress = event.server.serverAddress, username = event.server.username,
                        password = event.server.password, shareName = event.server.shareName,
                        connectionLabel = event.server.label, showSavedServersDialog = false, showConnectionDialog = true)
                }
            is ServerConnectEvent.DeleteSavedServer ->
                _uiState.update { s -> s.copy(savedServers = s.savedServers.filter { it.id != event.id }) }
            is ServerConnectEvent.UpdateSearchQuery -> {
                val q = event.query
                _uiState.update { state ->
                    val filtered = if (q.isBlank()) unfilteredFileList
                    else unfilteredFileList.filter { it.name.contains(q, ignoreCase = true) }
                    state.copy(searchQuery = q, fileList = sortFiles(filtered, state.sortByName, state.sortAscending))
                }
            }
            is ServerConnectEvent.ToggleSearch -> {
                _uiState.update {
                    if (it.isSearchActive) {
                        it.copy(isSearchActive = false, searchQuery = "",
                            fileList = sortFiles(unfilteredFileList, it.sortByName, it.sortAscending))
                    } else it.copy(isSearchActive = true)
                }
            }
            is ServerConnectEvent.ShowFileContextMenu ->
                _uiState.update { it.copy(showFileContextMenu = true, contextMenuFile = event.file) }
            is ServerConnectEvent.HideFileContextMenu ->
                _uiState.update { it.copy(showFileContextMenu = false, contextMenuFile = null) }
            is ServerConnectEvent.ShowRenameDialog ->
                _uiState.update { it.copy(showRenameDialog = true, renameTarget = event.file,
                    renameNewName = event.file.name, showFileContextMenu = false) }
            is ServerConnectEvent.HideRenameDialog ->
                _uiState.update { it.copy(showRenameDialog = false, renameTarget = null, renameNewName = "") }
            is ServerConnectEvent.UpdateRenameName -> _uiState.update { it.copy(renameNewName = event.name) }
            is ServerConnectEvent.ShowMoveDialog ->
                _uiState.update { it.copy(showMoveDialog = true, moveTarget = event.file,
                    moveDestination = "", showFileContextMenu = false) }
            is ServerConnectEvent.HideMoveDialog ->
                _uiState.update { it.copy(showMoveDialog = false, moveTarget = null, moveDestination = "") }
            is ServerConnectEvent.UpdateMoveDestination -> _uiState.update { it.copy(moveDestination = event.dest) }
            is ServerConnectEvent.ShowPropertiesDialog ->
                _uiState.update { it.copy(showPropertiesDialog = true, propertiesTarget = event.file, showFileContextMenu = false) }
            is ServerConnectEvent.HidePropertiesDialog ->
                _uiState.update { it.copy(showPropertiesDialog = false, propertiesTarget = null) }
            is ServerConnectEvent.ShowEditServerDialog ->
                _uiState.update { it.copy(showEditServerDialog = true, editingServer = event.server) }
            is ServerConnectEvent.HideEditServerDialog ->
                _uiState.update { it.copy(showEditServerDialog = false, editingServer = null) }
            is ServerConnectEvent.AutoConnectServer ->
                _uiState.update { it.copy(autoConnectServerId = event.serverId) }
            // Long-press: enter multi-select + select this file
            is ServerConnectEvent.LongPressFile -> {
                _uiState.update { state ->
                    if (state.isMultiSelectMode) {
                        // Already in multi-select, toggle this file
                        val sel = if (state.selectedFiles.contains(event.fileName))
                            state.selectedFiles - event.fileName else state.selectedFiles + event.fileName
                        state.copy(selectedFiles = sel)
                    } else {
                        // Enter multi-select and select this file
                        state.copy(isMultiSelectMode = true, selectedFiles = setOf(event.fileName))
                    }
                }
            }
        }
    }

    // ─── Connection ─────────────────────────────────────────────────────────

    suspend fun connectToServer(context: Context? = null, savedServer: SavedServer? = null) {
        viewModelScope.launch {
            val server   = _uiState.value.serverAddress
            val username = _uiState.value.username
            val password = _uiState.value.password
            val share    = _uiState.value.shareName
            try {
                _uiState.update { it.copy(isLoading = true, showConnectionDialog = false) }
                withContext(Dispatchers.IO) {
                    val props = Properties().apply {
                        setProperty("jcifs.smb.client.responseTimeout", "30000")
                        setProperty("jcifs.smb.client.soTimeout", "35000")
                        setProperty("jcifs.smb.client.connTimeout", "60000")
                        setProperty("jcifs.smb.client.dfs.disabled", "true")
                        setProperty("jcifs.smb.client.rcv_buf_size", "8388608")
                        setProperty("jcifs.smb.client.snd_buf_size", "8388608")
                        setProperty("jcifs.smb.client.transaction_buf_size", "8388608")
                        setProperty("jcifs.smb.client.maxVersion", "SMB311")
                        setProperty("jcifs.smb.client.minVersion", "SMB202")
                        setProperty("jcifs.smb.client.signingPreferred", "false")
                        setProperty("jcifs.smb.client.signingEnforced", "false")
                        setProperty("jcifs.smb.client.listSize", "65535")
                        setProperty("jcifs.smb.client.useExtendedSecurity", "true")
                        setProperty("jcifs.smb.client.tcpNoDelay", "true")
                        setProperty("jcifs.smb.client.useLargeReadWrite", "true")
                    }
                    val baseCtx = BaseContext(PropertyConfiguration(props))
                    cifsContext = baseCtx.withCredentials(NtlmPasswordAuthenticator(null, username, password))
                    val url = if (share.isEmpty()) "smb://$server/" else "smb://$server/$share/"
                    val smbFile = SmbFile(url, cifsContext)
                    smbFile.connect()
                    pathStack.clear(); pathStack.add("")
                    val fileList = if (share.isEmpty()) {
                        smbFile.listFiles()?.map { f ->
                            SMBFileItem(f.name.removeSuffix("/"), f.path, f.isDirectory, 0, f.lastModified())
                        } ?: emptyList()
                    } else listFiles(server, share, "")
                    unfilteredFileList = fileList
                    val breadcrumbs = if (share.isEmpty()) emptyList() else listOf(share)
                    // Prepare the HTTP transfer client for high-speed upload/download.
                    httpClient = HttpFileTransferClient(server, HTTP_TRANSFER_PORT)

                    _uiState.update {
                        it.copy(isConnected = true, isLoading = false, currentServer = server,
                            currentShare = share, currentPath = "",
                            fileList = sortFiles(fileList, it.sortByName, it.sortAscending),
                            breadcrumbs = breadcrumbs, autoConnectServerId = null)
                    }
                    _successMessages.emit(if (share.isEmpty()) "Connected to $server" else "Connected to \\\\$server\\$share")
                }
                context?.let { saveCurrentServer(it) }
            } catch (e: SmbAuthException) {
                Log.e("ServerConnectModule", "Auth error", e)
                _uiState.update { it.copy(isLoading = false, autoConnectServerId = null) }
                _errorMessages.emit("Authentication failed — check credentials")
                if (savedServer != null) {
                    _uiState.update { it.copy(showEditServerDialog = true, editingServer = savedServer) }
                } else {
                    _uiState.update { it.copy(showConnectionDialog = true) }
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Connection error", e)
                _uiState.update { it.copy(isLoading = false, autoConnectServerId = null) }
                _errorMessages.emit("Connection failed: ${e.message}")
            }
        }
    }

    fun autoConnectSavedServer(server: SavedServer, context: Context) {
        _uiState.update {
            it.copy(serverAddress = server.serverAddress, username = server.username,
                password = server.password, shareName = server.shareName,
                connectionLabel = server.label, showSavedServersDialog = false)
        }
        viewModelScope.launch { connectToServer(context, savedServer = server) }
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    fun navigateToDirectory(directoryName: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet(), isMultiSelectMode = false, searchQuery = "", isSearchActive = false) }
                val path = if (_uiState.value.currentPath.isEmpty()) directoryName else "${_uiState.value.currentPath}/$directoryName"
                pathStack.add(path)
                val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, path)
                unfilteredFileList = files
                val crumbs = listOf(_uiState.value.currentShare) + path.split("/").filter { it.isNotEmpty() }
                _uiState.update {
                    it.copy(isLoading = false, currentPath = path,
                        fileList = sortFiles(files, it.sortByName, it.sortAscending), breadcrumbs = crumbs)
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
            if (pathStack.size <= 1) false
            else {
                viewModelScope.launch {
                    try {
                        _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet(), isMultiSelectMode = false, searchQuery = "", isSearchActive = false) }
                        pathStack.removeAt(pathStack.size - 1)
                        val parent = pathStack.last()
                        val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, parent)
                        unfilteredFileList = files
                        val crumbs = if (parent.isEmpty()) listOf(_uiState.value.currentShare)
                        else listOf(_uiState.value.currentShare) + parent.split("/").filter { it.isNotEmpty() }
                        _uiState.update {
                            it.copy(isLoading = false, currentPath = parent,
                                fileList = sortFiles(files, it.sortByName, it.sortAscending), breadcrumbs = crumbs)
                        }
                    } catch (e: Exception) {
                        Log.e("ServerConnectModule", "Navigate up error", e)
                        _uiState.update { it.copy(isLoading = false) }
                        _errorMessages.emit("Navigation error: ${e.message}")
                    }
                }
                true
            }
        } catch (e: Exception) { false }
    }

    fun navigateToBreadcrumb(index: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet(), isMultiSelectMode = false, searchQuery = "", isSearchActive = false) }
                val target = if (index == 0) "" else _uiState.value.breadcrumbs.drop(1).take(index).joinToString("/")
                pathStack.clear(); pathStack.add("")
                if (target.isNotEmpty()) {
                    target.split("/").forEachIndexed { i, _ ->
                        pathStack.add(target.split("/").take(i + 1).joinToString("/"))
                    }
                }
                val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, target)
                unfilteredFileList = files
                _uiState.update {
                    it.copy(isLoading = false, currentPath = target,
                        fileList = sortFiles(files, it.sortByName, it.sortAscending),
                        breadcrumbs = it.breadcrumbs.take(index + 1))
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Breadcrumb error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Navigation error: ${e.message}")
            }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, _uiState.value.currentPath)
                unfilteredFileList = files
                val q = _uiState.value.searchQuery
                val filtered = if (q.isBlank()) files else files.filter { it.name.contains(q, ignoreCase = true) }
                _uiState.update { it.copy(isLoading = false, fileList = sortFiles(filtered, it.sortByName, it.sortAscending)) }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Refresh error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Refresh error: ${e.message}")
            }
        }
    }

    // ─── File Operations ─────────────────────────────────────────────────────

    fun createFolder() {
        viewModelScope.launch {
            try {
                val name = _uiState.value.newFolderName.trim()
                if (name.isEmpty()) { _errorMessages.emit("Folder name cannot be empty"); return@launch }
                _uiState.update { it.copy(isLoading = true, showCreateFolderDialog = false) }
                withContext(Dispatchers.IO) {
                    val path = if (_uiState.value.currentPath.isEmpty()) name else "${_uiState.value.currentPath}/$name"
                    SmbFile("smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path/", cifsContext).mkdir()
                }
                refreshFiles()
                _successMessages.emit("Folder '$name' created")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Create folder error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Failed to create folder: ${e.message}")
            }
        }
    }

    fun renameFile() {
        viewModelScope.launch {
            try {
                val target = _uiState.value.renameTarget ?: return@launch
                val newName = _uiState.value.renameNewName.trim()
                if (newName.isEmpty() || newName == target.name) {
                    _uiState.update { it.copy(showRenameDialog = false, renameTarget = null) }; return@launch
                }
                _uiState.update { it.copy(isLoading = true, showRenameDialog = false) }
                withContext(Dispatchers.IO) {
                    val basePath = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/" +
                            (if (_uiState.value.currentPath.isEmpty()) "" else "${_uiState.value.currentPath}/")
                    val suffix = if (target.isDirectory) "/" else ""
                    SmbFile("$basePath${target.name}$suffix", cifsContext)
                        .renameTo(SmbFile("$basePath$newName$suffix", cifsContext))
                }
                _uiState.update { it.copy(renameTarget = null, renameNewName = "") }
                refreshFiles()
                _successMessages.emit("Renamed to '$newName'")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Rename error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Rename failed: ${e.message}")
            }
        }
    }

    fun moveFile() {
        viewModelScope.launch {
            try {
                val target = _uiState.value.moveTarget ?: return@launch
                val dest = _uiState.value.moveDestination.trim()
                if (dest.isEmpty()) { _uiState.update { it.copy(showMoveDialog = false, moveTarget = null) }; return@launch }
                _uiState.update { it.copy(isLoading = true, showMoveDialog = false) }
                withContext(Dispatchers.IO) {
                    val basePath = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/"
                    val srcFolder = if (_uiState.value.currentPath.isEmpty()) "" else "${_uiState.value.currentPath}/"
                    val suffix = if (target.isDirectory) "/" else ""
                    val dstFolder = if (dest.endsWith("/")) dest else "$dest/"
                    SmbFile("$basePath$srcFolder${target.name}$suffix", cifsContext)
                        .renameTo(SmbFile("$basePath$dstFolder${target.name}$suffix", cifsContext))
                }
                _uiState.update { it.copy(moveTarget = null, moveDestination = "") }
                refreshFiles()
                _successMessages.emit("'${target.name}' moved to '$dest'")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Move error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Move failed: ${e.message}")
            }
        }
    }

    suspend fun uploadFile(uri: Uri, context: Context): Boolean {
        activeTransferJob?.cancel()
        var success = false

        val http = httpClient
        if (http == null) {
            viewModelScope.launch {
                _errorMessages.emit(
                    "HTTP transfer server not reachable. " +
                    "Start IT Connect Server on your PC: java -jar itconnect-file-server.jar"
                )
            }
            return false
        }

        val remotePath = "/" + _uiState.value.currentPath.replace("\\", "/").trimStart('/')
        val startTime = System.currentTimeMillis()
        var lastTransferred = 0L
        var peakSpeed = 0L

        val job = viewModelScope.launch {
            try {
                val result = http.uploadFile(
                    uri = uri,
                    remotePath = remotePath,
                    context = context,
                    onProgress = { p: TransferProgress ->
                        lastTransferred = p.transferredBytes
                        val elapsed = System.currentTimeMillis() - startTime
                        val avg = if (elapsed > 0) p.transferredBytes * 1000L / elapsed else 0L
                        if (p.speedBytesPerSec > peakSpeed) peakSpeed = p.speedBytesPerSec
                        _uiState.update {
                            it.copy(
                                isTransferring = true,
                                transferStats = TransferStats(
                                    fileName = p.fileName,
                                    totalBytes = p.totalBytes,
                                    transferredBytes = p.transferredBytes,
                                    currentSpeedBytesPerSec = p.speedBytesPerSec,
                                    peakSpeedBytesPerSec = peakSpeed,
                                    avgSpeedBytesPerSec = avg,
                                    elapsedMs = elapsed,
                                    isUpload = true
                                )
                            )
                        }
                    }
                )

                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }

                when (result) {
                    is TransferResult.Success -> {
                        success = true
                        val totalMs = System.currentTimeMillis() - startTime
                        val avgFinal = if (totalMs > 0) lastTransferred * 1000L / totalMs else 0L
                        refreshFiles()
                        _successMessages.emit(
                            "'${result.filePath.substringAfterLast("/")}' uploaded " +
                            "@ ${TransferStats(avgSpeedBytesPerSec = avgFinal).formattedAvgSpeed}"
                        )
                    }
                    is TransferResult.Failure -> _errorMessages.emit("Upload failed: ${result.error}")
                    is TransferResult.Cancelled -> { /* user cancelled */ }
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Upload error", e)
                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                _errorMessages.emit("Upload error: ${e.message}")
            }
        }

        activeTransferJob = job
        job.join()
        return success
    }

    suspend fun downloadFile(fileItem: SMBFileItem, context: Context): Uri? {
        activeTransferJob?.cancel()
        var resultUri: Uri? = null

        val http = httpClient
        if (http == null) {
            viewModelScope.launch {
                _errorMessages.emit(
                    "HTTP transfer server not reachable. " +
                    "Start IT Connect Server on your PC: java -jar itconnect-file-server.jar"
                )
            }
            return null
        }

        val currentPath = _uiState.value.currentPath
        val remotePath = if (currentPath.isEmpty()) "/${fileItem.name}"
                         else "/${currentPath.replace("\\", "/")}/${fileItem.name}"
        val startTime = System.currentTimeMillis()
        var lastTransferred = 0L
        var peakSpeed = 0L

        val job = viewModelScope.launch {
            try {
                val result = http.downloadFile(
                    remotePath = remotePath,
                    context = context,
                    onProgress = { p: TransferProgress ->
                        lastTransferred = p.transferredBytes
                        val elapsed = System.currentTimeMillis() - startTime
                        val avg = if (elapsed > 0) p.transferredBytes * 1000L / elapsed else 0L
                        if (p.speedBytesPerSec > peakSpeed) peakSpeed = p.speedBytesPerSec
                        _uiState.update {
                            it.copy(
                                isTransferring = true,
                                transferStats = TransferStats(
                                    fileName = p.fileName,
                                    totalBytes = p.totalBytes,
                                    transferredBytes = p.transferredBytes,
                                    currentSpeedBytesPerSec = p.speedBytesPerSec,
                                    peakSpeedBytesPerSec = peakSpeed,
                                    avgSpeedBytesPerSec = avg,
                                    elapsedMs = elapsed,
                                    isUpload = false
                                )
                            )
                        }
                    }
                )

                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }

                when (result) {
                    is TransferResult.Success -> {
                        val totalMs = System.currentTimeMillis() - startTime
                        val avgFinal = if (totalMs > 0) lastTransferred * 1000L / totalMs else 0L
                        resultUri = Uri.fromFile(File(result.filePath))
                        _successMessages.emit(
                            "'${fileItem.name}' → Downloads " +
                            "@ ${TransferStats(avgSpeedBytesPerSec = avgFinal).formattedAvgSpeed}"
                        )
                    }
                    is TransferResult.Failure -> _errorMessages.emit("Download failed: ${result.error}")
                    is TransferResult.Cancelled -> { /* user cancelled */ }
                }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Download error", e)
                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                _errorMessages.emit("Download error: ${e.message}")
            }
        }

        activeTransferJob = job
        job.join()
        return resultUri
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            try {
                val selected = _uiState.value.selectedFiles; if (selected.isEmpty()) return@launch
                _uiState.update { it.copy(isLoading = true) }
                withContext(Dispatchers.IO) {
                    selected.forEach { fileName ->
                        val path = if (_uiState.value.currentPath.isEmpty()) fileName else "${_uiState.value.currentPath}/$fileName"
                        val smb = SmbFile("smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path", cifsContext)
                        if (smb.isDirectory) deleteDirectoryRecursively(smb) else smb.delete()
                    }
                }
                _uiState.update { it.copy(selectedFiles = emptySet(), isMultiSelectMode = false) }
                refreshFiles(); _successMessages.emit("${selected.size} item(s) deleted")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Delete error", e); _uiState.update { it.copy(isLoading = false) }; _errorMessages.emit("Delete failed: ${e.message}")
            }
        }
    }

    fun deleteSingleFile(file: SMBFileItem) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, showFileContextMenu = false, contextMenuFile = null) }
                withContext(Dispatchers.IO) {
                    val path = if (_uiState.value.currentPath.isEmpty()) file.name else "${_uiState.value.currentPath}/${file.name}"
                    val suffix = if (file.isDirectory) "/" else ""
                    val smb = SmbFile("smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path$suffix", cifsContext)
                    if (smb.isDirectory) deleteDirectoryRecursively(smb) else smb.delete()
                }
                refreshFiles(); _successMessages.emit("'${file.name}' deleted")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Delete error", e); _uiState.update { it.copy(isLoading = false) }; _errorMessages.emit("Delete failed: ${e.message}")
            }
        }
    }

    private suspend fun deleteDirectoryRecursively(smbFile: SmbFile) {
        smbFile.listFiles()?.forEach { f -> if (f.isDirectory) deleteDirectoryRecursively(f) else f.delete() }; smbFile.delete()
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                activeTransferJob?.cancel()
                cifsContext = null
                httpClient = null
                pathStack.clear()
                unfilteredFileList = emptyList()
                _uiState.update { ServerConnectionState(serverAddress = it.serverAddress, username = it.username, password = it.password, shareName = it.shareName, savedServers = it.savedServers) }
                _successMessages.emit("Disconnected")
            } catch (e: Exception) { _errorMessages.emit("Disconnect error: ${e.message}") }
        }
    }

    private suspend fun listFiles(server: String, share: String, path: String): List<SMBFileItem> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "smb://$server/$share/${if (path.isNotEmpty()) "$path/" else ""}"
                SmbFile(url, cifsContext).listFiles()?.map { f ->
                    SMBFileItem(name = f.name.removeSuffix("/"), path = f.path, isDirectory = f.isDirectory,
                        size = if (f.isDirectory) 0 else f.length(), lastModified = f.lastModified(),
                        canRead = f.canRead(), canWrite = f.canWrite())
                } ?: emptyList()
            } catch (e: SmbException) { Log.e("ServerConnectModule", "List files error", e); throw e }
        }
    }

    private fun sortFiles(files: List<SMBFileItem>, byName: Boolean, ascending: Boolean): List<SMBFileItem> {
        val comp: Comparator<SMBFileItem> = if (byName) compareBy({ !it.isDirectory }, { it.name.lowercase() })
        else compareBy({ !it.isDirectory }, { it.lastModified })
        return if (ascending) files.sortedWith(comp) else files.sortedWith(comp).reversed()
    }

    fun canNavigateBack(): Boolean = pathStack.size > 1
}