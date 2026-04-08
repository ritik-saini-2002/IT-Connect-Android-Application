package com.example.ritik_2.winshare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
            else -> "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} GB"
        }

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))

    val fileExtension: String
        get() = if (isDirectory) "" else name.substringAfterLast(".", "")
}

// Rich transfer statistics exposed to UI
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
    val sortAscending: Boolean = true
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
}

// ─── Constants ───────────────────────────────────────────────────────────────

private const val PREFS_NAME        = "winshare_prefs"
private const val KEY_SAVED_SERVERS = "saved_servers"

// 🚀 SPEED FIX #1: Increased from 64KB → 4MB for maximum SMB throughput.
// A larger buffer means fewer read/write syscalls per second, which is the
// single biggest win for high-speed LAN transfers.
private const val TRANSFER_BUFFER = 4 * 1024 * 1024  // 4 MB

// ─── ViewModel ──────────────────────────────────────────────────────────────

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
    private var activeTransferJob: Job? = null

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
            id = System.currentTimeMillis().toString(),
            label = label,
            serverAddress = s.serverAddress,
            username = s.username,
            password = s.password,
            shareName = s.shareName
        )
        val updated = _uiState.value.savedServers.filter { it.serverAddress != newSrv.serverAddress } + newSrv
        _uiState.update { it.copy(savedServers = updated) }
        persistServers(context, updated)
    }

    // ─── Events ─────────────────────────────────────────────────────────────

    fun handleEvent(event: ServerConnectEvent) {
        when (event) {
            is ServerConnectEvent.ShowConnectionDialog ->
                _uiState.update { it.copy(showConnectionDialog = true) }
            is ServerConnectEvent.HideConnectionDialog ->
                _uiState.update { it.copy(showConnectionDialog = false) }
            is ServerConnectEvent.ShowCreateFolderDialog ->
                _uiState.update { it.copy(showCreateFolderDialog = true, newFolderName = "") }
            is ServerConnectEvent.HideCreateFolderDialog ->
                _uiState.update { it.copy(showCreateFolderDialog = false, newFolderName = "") }
            is ServerConnectEvent.ShowSavedServersDialog ->
                _uiState.update { it.copy(showSavedServersDialog = true) }
            is ServerConnectEvent.HideSavedServersDialog ->
                _uiState.update { it.copy(showSavedServersDialog = false) }
            is ServerConnectEvent.ToggleMultiSelectMode ->
                _uiState.update { it.copy(isMultiSelectMode = !it.isMultiSelectMode, selectedFiles = emptySet()) }
            is ServerConnectEvent.ClearSelection ->
                _uiState.update { it.copy(selectedFiles = emptySet()) }
            is ServerConnectEvent.ToggleSortOrder ->
                _uiState.update {
                    it.copy(sortAscending = !it.sortAscending,
                        fileList = sortFiles(it.fileList, it.sortByName, !it.sortAscending))
                }
            is ServerConnectEvent.CancelTransfer -> {
                activeTransferJob?.cancel()
                activeTransferJob = null
                _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
            }
            is ServerConnectEvent.UpdateServerAddress ->
                _uiState.update { it.copy(serverAddress = event.address) }
            is ServerConnectEvent.UpdateUsername ->
                _uiState.update { it.copy(username = event.username) }
            is ServerConnectEvent.UpdatePassword ->
                _uiState.update { it.copy(password = event.password) }
            is ServerConnectEvent.UpdateShareName ->
                _uiState.update { it.copy(shareName = event.share) }
            is ServerConnectEvent.UpdateNewFolderName ->
                _uiState.update { it.copy(newFolderName = event.name) }
            is ServerConnectEvent.UpdateConnectionLabel ->
                _uiState.update { it.copy(connectionLabel = event.label) }
            is ServerConnectEvent.ToggleFileSelection ->
                _uiState.update { state ->
                    val sel = if (state.selectedFiles.contains(event.fileName))
                        state.selectedFiles - event.fileName else state.selectedFiles + event.fileName
                    state.copy(selectedFiles = sel)
                }
            is ServerConnectEvent.LoadSavedServer ->
                _uiState.update {
                    it.copy(
                        serverAddress = event.server.serverAddress,
                        username = event.server.username,
                        password = event.server.password,
                        shareName = event.server.shareName,
                        connectionLabel = event.server.label,
                        showSavedServersDialog = false,
                        showConnectionDialog = true
                    )
                }
            is ServerConnectEvent.DeleteSavedServer ->
                _uiState.update { s -> s.copy(savedServers = s.savedServers.filter { it.id != event.id }) }
        }
    }

    // ─── Connection ─────────────────────────────────────────────────────────

    suspend fun connectToServer(context: Context? = null) {
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

                        // 🚀 SPEED FIX #2: Socket buffer sizes increased from 128KB → 4MB.
                        // These control how much data jCIFS can pipeline in-flight at once.
                        // Small buffers cause the sender to stall waiting for ACKs on fast networks.
                        setProperty("jcifs.smb.client.rcv_buf_size", "4194304")   // 4 MB receive
                        setProperty("jcifs.smb.client.snd_buf_size", "4194304")   // 4 MB send
                        setProperty("jcifs.smb.client.transaction_buf_size", "4194304") // 4 MB transaction

                        // 🚀 SPEED FIX #3: Prefer SMB2/3 over SMB1.
                        // SMB2+ has compound requests, larger max I/O sizes, and much better
                        // pipeline depth. SMB1 has a hard 64KB max transfer unit.
                        setProperty("jcifs.smb.client.maxVersion", "SMB300")
                        setProperty("jcifs.smb.client.minVersion", "SMB202")

                        // 🚀 SPEED FIX #4: Disable signing when not required.
                        // Packet signing adds HMAC computation overhead on every packet.
                        // Disable it on trusted LAN connections for a 10–20% CPU saving.
                        setProperty("jcifs.smb.client.signingPreferred", "false")
                        setProperty("jcifs.smb.client.signingEnforced", "false")

                        // 🚀 SPEED FIX #5: Larger directory listing buffer.
                        setProperty("jcifs.smb.client.listSize", "65535")

                        // Misc reliability / performance settings
                        setProperty("jcifs.smb.client.useExtendedSecurity", "true")
                        setProperty("jcifs.smb.client.tcpNoDelay", "true")
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
                    val breadcrumbs = if (share.isEmpty()) emptyList() else listOf(share)
                    _uiState.update {
                        it.copy(isConnected = true, isLoading = false, currentServer = server,
                            currentShare = share, currentPath = "",
                            fileList = sortFiles(fileList, it.sortByName, it.sortAscending),
                            breadcrumbs = breadcrumbs)
                    }
                    _successMessages.emit(if (share.isEmpty()) "Connected to $server" else "Connected to \\\\$server\\$share")
                }
                context?.let { saveCurrentServer(it) }
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Connection error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Connection failed: ${e.message}")
            }
        }
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    fun navigateToDirectory(directoryName: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }
                val path = if (_uiState.value.currentPath.isEmpty()) directoryName
                else "${_uiState.value.currentPath}/$directoryName"
                pathStack.add(path)
                val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, path)
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
                        _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }
                        pathStack.removeAt(pathStack.size - 1)
                        val parent = pathStack.last()
                        val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, parent)
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
                _uiState.update { it.copy(isLoading = true, selectedFiles = emptySet()) }
                val target = if (index == 0) ""
                else _uiState.value.breadcrumbs.drop(1).take(index).joinToString("/")
                pathStack.clear(); pathStack.add("")
                if (target.isNotEmpty()) {
                    target.split("/").forEachIndexed { i, _ ->
                        pathStack.add(target.split("/").take(i + 1).joinToString("/"))
                    }
                }
                val files = listFiles(_uiState.value.currentServer, _uiState.value.currentShare, target)
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
                _uiState.update { it.copy(isLoading = false, fileList = sortFiles(files, it.sortByName, it.sortAscending)) }
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

    suspend fun uploadFile(uri: Uri, context: Context): Boolean {
        activeTransferJob?.cancel()
        var success = false
        val job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val cr = context.contentResolver
                    val cursor = cr.query(uri, null, null, null, null)
                    val fileName = cursor?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) it.getString(idx) else "uploaded_file"
                        } else "uploaded_file"
                    } ?: "uploaded_file"

                    val fileSize = try { cr.openFileDescriptor(uri, "r")?.statSize ?: 0L } catch (_: Exception) { 0L }
                    val targetPath = if (_uiState.value.currentPath.isEmpty()) fileName else "${_uiState.value.currentPath}/$fileName"
                    val smbFile = SmbFile("smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$targetPath", cifsContext)

                    _uiState.update {
                        it.copy(isTransferring = true,
                            transferStats = TransferStats(fileName = fileName, totalBytes = fileSize, isUpload = true))
                    }

                    val startTime = System.currentTimeMillis()
                    var transferred = 0L; var lastTime = startTime; var lastBytes = 0L; var peakSpeed = 0L
                    val speedWindow = ArrayDeque<Pair<Long, Long>>()

                    // 🚀 SPEED FIX #6: Wrap both streams in BufferedInputStream/BufferedOutputStream.
                    // This adds an OS-level read-ahead buffer on the content resolver stream and
                    // a write-behind buffer on the SMB output stream, dramatically reducing the
                    // number of times the kernel has to context-switch for I/O operations.
                    cr.openInputStream(uri)?.let { rawInput ->
                        BufferedInputStream(rawInput, TRANSFER_BUFFER).use { input ->
                            BufferedOutputStream(smbFile.outputStream, TRANSFER_BUFFER).use { output ->
                                val buf = ByteArray(TRANSFER_BUFFER)
                                var bytes = input.read(buf)
                                while (bytes != -1 && isActive) {
                                    output.write(buf, 0, bytes)
                                    transferred += bytes
                                    val now = System.currentTimeMillis()
                                    val elapsed = now - lastTime
                                    speedWindow.addLast(now to transferred)
                                    while (speedWindow.size > 1 && now - speedWindow.first().first > 3000) speedWindow.removeFirst()
                                    if (elapsed >= 300) {
                                        val winSpeed = if (speedWindow.size >= 2) {
                                            val d = speedWindow.last().first - speedWindow.first().first
                                            if (d > 0) ((speedWindow.last().second - speedWindow.first().second) * 1000L) / d else 0L
                                        } else 0L
                                        val cur = if (elapsed > 0) ((transferred - lastBytes) * 1000L) / elapsed else 0L
                                        val tot = now - startTime
                                        val avg = if (tot > 0) (transferred * 1000L) / tot else 0L
                                        if (cur > peakSpeed) peakSpeed = cur
                                        lastTime = now; lastBytes = transferred
                                        _uiState.update {
                                            it.copy(transferStats = TransferStats(
                                                fileName = fileName, totalBytes = fileSize, transferredBytes = transferred,
                                                currentSpeedBytesPerSec = winSpeed.coerceAtLeast(cur),
                                                peakSpeedBytesPerSec = peakSpeed, avgSpeedBytesPerSec = avg,
                                                elapsedMs = tot, isUpload = true))
                                        }
                                    }
                                    bytes = input.read(buf)
                                }
                                output.flush()
                            }
                        }
                    }
                    if (isActive) {
                        success = true
                        val totalMs = System.currentTimeMillis() - startTime
                        val avg = if (totalMs > 0) (transferred * 1000L) / totalMs else 0L
                        _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                        viewModelScope.launch {
                            refreshFiles()
                            _successMessages.emit("'$fileName' uploaded @ ${TransferStats(avgSpeedBytesPerSec = avg).formattedAvgSpeed}")
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("ServerConnectModule", "Upload error", e)
                        _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                        viewModelScope.launch { _errorMessages.emit("Upload failed: ${e.message}") }
                    }
                }
            }
        }
        activeTransferJob = job; job.join()
        return success
    }

    suspend fun downloadFile(fileItem: SMBFileItem, context: Context): Uri? {
        activeTransferJob?.cancel()
        var resultUri: Uri? = null
        val job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val url = "smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/${_uiState.value.currentPath}/${fileItem.name}"
                    val smbFile = SmbFile(url, cifsContext)
                    val fileSize = try { smbFile.length() } catch (_: Exception) { 0L }
                    _uiState.update {
                        it.copy(isTransferring = true,
                            transferStats = TransferStats(fileName = fileItem.name, totalBytes = fileSize, isUpload = false))
                    }
                    val downloadsDir = File(context.cacheDir, "smb_downloads").also { if (!it.exists()) it.mkdirs() }
                    val localFile = File(downloadsDir, fileItem.name)
                    val startTime = System.currentTimeMillis()
                    var transferred = 0L; var lastTime = startTime; var lastBytes = 0L; var peakSpeed = 0L
                    val speedWindow = ArrayDeque<Pair<Long, Long>>()

                    // 🚀 SPEED FIX #6 (download): Same buffered stream wrapping as upload.
                    // BufferedInputStream on the SMB side pre-fetches data, and
                    // BufferedOutputStream on the local file side batches disk writes.
                    BufferedInputStream(smbFile.inputStream, TRANSFER_BUFFER).use { input ->
                        BufferedOutputStream(FileOutputStream(localFile), TRANSFER_BUFFER).use { output ->
                            val buf = ByteArray(TRANSFER_BUFFER)
                            var bytes = input.read(buf)
                            while (bytes != -1 && isActive) {
                                output.write(buf, 0, bytes)
                                transferred += bytes
                                val now = System.currentTimeMillis()
                                val elapsed = now - lastTime
                                speedWindow.addLast(now to transferred)
                                while (speedWindow.size > 1 && now - speedWindow.first().first > 3000) speedWindow.removeFirst()
                                if (elapsed >= 300) {
                                    val winSpeed = if (speedWindow.size >= 2) {
                                        val d = speedWindow.last().first - speedWindow.first().first
                                        if (d > 0) ((speedWindow.last().second - speedWindow.first().second) * 1000L) / d else 0L
                                    } else 0L
                                    val cur = if (elapsed > 0) ((transferred - lastBytes) * 1000L) / elapsed else 0L
                                    val tot = now - startTime
                                    val avg = if (tot > 0) (transferred * 1000L) / tot else 0L
                                    if (cur > peakSpeed) peakSpeed = cur
                                    lastTime = now; lastBytes = transferred
                                    _uiState.update {
                                        it.copy(transferStats = TransferStats(
                                            fileName = fileItem.name, totalBytes = fileSize, transferredBytes = transferred,
                                            currentSpeedBytesPerSec = winSpeed.coerceAtLeast(cur),
                                            peakSpeedBytesPerSec = peakSpeed, avgSpeedBytesPerSec = avg,
                                            elapsedMs = tot, isUpload = false))
                                    }
                                }
                                bytes = input.read(buf)
                            }
                            output.flush()
                        }
                    }
                    if (isActive) {
                        val totalMs = System.currentTimeMillis() - startTime
                        val avg = if (totalMs > 0) (transferred * 1000L) / totalMs else 0L
                        _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                        resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
                        viewModelScope.launch {
                            _successMessages.emit("'${fileItem.name}' downloaded @ ${TransferStats(avgSpeedBytesPerSec = avg).formattedAvgSpeed}")
                        }
                    } else {
                        localFile.delete()
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("ServerConnectModule", "Download error", e)
                        _uiState.update { it.copy(isTransferring = false, transferStats = TransferStats()) }
                        viewModelScope.launch { _errorMessages.emit("Download failed: ${e.message}") }
                    }
                }
            }
        }
        activeTransferJob = job; job.join()
        return resultUri
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            try {
                val selected = _uiState.value.selectedFiles
                if (selected.isEmpty()) return@launch
                _uiState.update { it.copy(isLoading = true) }
                withContext(Dispatchers.IO) {
                    selected.forEach { fileName ->
                        val path = if (_uiState.value.currentPath.isEmpty()) fileName else "${_uiState.value.currentPath}/$fileName"
                        val smb = SmbFile("smb://${_uiState.value.currentServer}/${_uiState.value.currentShare}/$path", cifsContext)
                        if (smb.isDirectory) deleteDirectoryRecursively(smb) else smb.delete()
                    }
                }
                _uiState.update { it.copy(selectedFiles = emptySet(), isMultiSelectMode = false) }
                refreshFiles()
                _successMessages.emit("${selected.size} item(s) deleted")
            } catch (e: Exception) {
                Log.e("ServerConnectModule", "Delete error", e)
                _uiState.update { it.copy(isLoading = false) }
                _errorMessages.emit("Delete failed: ${e.message}")
            }
        }
    }

    private suspend fun deleteDirectoryRecursively(smbFile: SmbFile) {
        smbFile.listFiles()?.forEach { f -> if (f.isDirectory) deleteDirectoryRecursively(f) else f.delete() }
        smbFile.delete()
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                activeTransferJob?.cancel()
                cifsContext = null; pathStack.clear()
                _uiState.update {
                    ServerConnectionState(
                        serverAddress = it.serverAddress, username = it.username,
                        password = it.password, shareName = it.shareName,
                        savedServers = it.savedServers
                    )
                }
                _successMessages.emit("Disconnected")
            } catch (e: Exception) {
                _errorMessages.emit("Disconnect error: ${e.message}")
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun listFiles(server: String, share: String, path: String): List<SMBFileItem> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "smb://$server/$share/${if (path.isNotEmpty()) "$path/" else ""}"
                SmbFile(url, cifsContext).listFiles()?.map { f ->
                    SMBFileItem(
                        name = f.name.removeSuffix("/"), path = f.path, isDirectory = f.isDirectory,
                        size = if (f.isDirectory) 0 else f.length(), lastModified = f.lastModified(),
                        canRead = f.canRead(), canWrite = f.canWrite()
                    )
                } ?: emptyList()
            } catch (e: SmbException) {
                Log.e("ServerConnectModule", "List files error", e)
                throw e
            }
        }
    }

    private fun sortFiles(files: List<SMBFileItem>, byName: Boolean, ascending: Boolean): List<SMBFileItem> {
        val comp: Comparator<SMBFileItem> = if (byName)
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        else
            compareBy({ !it.isDirectory }, { it.lastModified })
        return if (ascending) files.sortedWith(comp) else files.sortedWith(comp).reversed()
    }

    fun canNavigateBack(): Boolean = pathStack.size > 1
}