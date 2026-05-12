package com.saini.ritik.winshare.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI State ────────────────────────────────────────────────────────────────

data class HttpTransferUiState(
    /** IP or hostname of the Windows PC running the server. */
    val hostIp: String = "",
    val port: Int = 8765,
    val isConnected: Boolean = false,
    val isPinging: Boolean = false,

    /** Current remote directory shown in the browser. */
    val currentPath: String = "/",
    val fileList: List<RemoteFileItem> = emptyList(),
    val isLoadingFiles: Boolean = false,

    /** Live transfer state (idle / connecting / transferring / done). */
    val transferState: TransferState = TransferState.Idle,

    /** Human-readable status shown in a snackbar / info bar. */
    val statusMessage: String = ""
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

/**
 * ViewModel for the HTTP-based high-speed file transfer feature.
 *
 * Used by both WinShare and WindowsControl screens.
 * All heavy work runs on [kotlinx.coroutines.Dispatchers.IO] inside
 * [HttpFileTransferClient]; this ViewModel just drives state.
 */
class FileTransferViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HttpTransferUiState())
    val uiState: StateFlow<HttpTransferUiState> = _uiState.asStateFlow()

    private var client: HttpFileTransferClient? = null
    private var activeTransfer: Job? = null

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Pings [ip]:[port] and, on success, loads the root file listing.
     */
    fun connect(ip: String, port: Int = 8765) {
        if (ip.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter a server IP address") }
            return
        }
        client = HttpFileTransferClient(ip, port)
        _uiState.update { it.copy(hostIp = ip, port = port, isPinging = true, statusMessage = "Connecting…") }

        viewModelScope.launch {
            val ok = client!!.ping()
            _uiState.update { state ->
                state.copy(
                    isConnected = ok,
                    isPinging = false,
                    statusMessage = if (ok) "Connected to $ip:$port" else "Cannot reach server at $ip:$port"
                )
            }
            if (ok) loadFiles("/")
        }
    }

    fun disconnect() {
        cancelTransfer()
        client = null
        _uiState.value = HttpTransferUiState()
    }

    // ── File Browsing ─────────────────────────────────────────────────────────

    /**
     * Loads the file listing for [path].
     * Called automatically after [connect] and after a successful upload.
     */
    fun loadFiles(path: String) {
        val c = client ?: return
        _uiState.update { it.copy(isLoadingFiles = true, currentPath = path) }
        viewModelScope.launch {
            val files = c.listFiles(path)
            _uiState.update { it.copy(fileList = files, isLoadingFiles = false) }
        }
    }

    fun navigateInto(item: RemoteFileItem) {
        if (item.isDirectory) loadFiles(item.path)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/") return
        val parent = current.substringBeforeLast("/").ifBlank { "/" }
        loadFiles(parent)
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Streams the file at [uri] to the server's [currentPath] directory.
     *
     * Progress updates arrive on the IO thread; [_uiState.update] is
     * thread-safe so no explicit main-thread hop is needed.
     */
    fun uploadFile(uri: Uri, context: Context) {
        val c = client ?: return
        val remotePath = _uiState.value.currentPath
        cancelTransfer()

        activeTransfer = viewModelScope.launch {
            _uiState.update { it.copy(transferState = TransferState.Connecting) }

            val result = c.uploadFile(
                uri = uri,
                remotePath = remotePath,
                context = context,
                onProgress = { progress ->
                    _uiState.update { it.copy(transferState = TransferState.Transferring(progress)) }
                }
            )

            _uiState.update { state ->
                state.copy(
                    transferState = TransferState.Done(result),
                    statusMessage = result.toMessage()
                )
            }

            if (result is TransferResult.Success) {
                // Refresh the listing so the new file appears immediately
                loadFiles(remotePath)
            }
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Streams [remoteFile] from the server to the device Downloads folder.
     */
    fun downloadFile(remoteFile: RemoteFileItem, context: Context) {
        if (remoteFile.isDirectory) return
        val c = client ?: return
        cancelTransfer()

        activeTransfer = viewModelScope.launch {
            _uiState.update { it.copy(transferState = TransferState.Connecting) }

            val result = c.downloadFile(
                remotePath = remoteFile.path,
                context = context,
                onProgress = { progress ->
                    _uiState.update { it.copy(transferState = TransferState.Transferring(progress)) }
                }
            )

            _uiState.update { state ->
                state.copy(
                    transferState = TransferState.Done(result),
                    statusMessage = result.toMessage()
                )
            }
        }
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    fun cancelTransfer() {
        activeTransfer?.cancel()
        activeTransfer = null
        _uiState.update { it.copy(transferState = TransferState.Idle) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun TransferResult.toMessage(): String = when (this) {
        is TransferResult.Success -> "Done — saved to $filePath"
        is TransferResult.Failure -> "Failed: $error"
        is TransferResult.Cancelled -> "Cancelled"
    }
}
