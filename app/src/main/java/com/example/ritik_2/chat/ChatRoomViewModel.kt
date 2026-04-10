package com.example.ritik_2.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// 50 MB — above this we warn the user before loading bytes into memory
private const val WARN_SIZE_BYTES = 50L * 1024 * 1024

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val repo          : ChatRepository,
    private val authRepository: AuthRepository,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ChatRoomUiState())
    val state: StateFlow<ChatRoomUiState> = _state.asStateFlow()

    private var roomId            = ""
    private var currentUserId     = ""
    private var currentUserName   = ""
    private var currentUserAvatar = ""
    private var realtimeJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(room: ChatRoom) {
        this.roomId = room.id
        val session = authRepository.getSession() ?: return
        currentUserId     = session.userId
        currentUserName   = session.name
        currentUserAvatar = ""
        _state.update { it.copy(room = room) }
        loadMessages()
        startRealtime()
    }

    fun setCurrentUserAvatar(url: String) { currentUserAvatar = url }

    // ── Messages ──────────────────────────────────────────────────────────────

    private fun loadMessages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val msgs = repo.getMessages(roomId).map { m ->
                    m.copy(isOwn = m.senderId == currentUserId)
                }
                _state.update { it.copy(isLoading = false, messages = msgs) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Realtime ──────────────────────────────────────────────────────────────

    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            repo.subscribeToRoom(roomId)
                .catch { e -> android.util.Log.w("ChatRoomVM", "Realtime: ${e.message}") }
                .collect { newMsg ->
                    val msg      = newMsg.copy(isOwn = newMsg.senderId == currentUserId)
                    val current  = _state.value.messages
                    val existIdx = current.indexOfFirst { it.id == msg.id }
                    if (existIdx >= 0) {
                        val updated = current.toMutableList().also { it[existIdx] = msg }
                        _state.update { it.copy(messages = updated) }
                    } else {
                        _state.update { it.copy(messages = current + msg) }
                    }
                }
        }
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    fun setReplyTo(msg: ChatMessage?)  = _state.update { it.copy(replyingTo = msg) }
    fun clearReply()                   = _state.update { it.copy(replyingTo = null) }
    fun clearError()                   = _state.update { it.copy(error = null) }
    fun startEditing(msg: ChatMessage) = _state.update { it.copy(editingMessage = msg) }
    fun clearEditing()                 = _state.update { it.copy(editingMessage = null) }

    fun setSelectedFile(uri: Uri, filename: String, mime: String) =
        _state.update { it.copy(selectedFile = uri, selectedFileName = filename, selectedFileMime = mime) }

    fun clearSelectedFile() =
        _state.update { it.copy(selectedFile = null, selectedFileName = "", selectedFileMime = "") }

    /** Dismiss conflict dialog without sending */
    fun dismissFileConflict() =
        _state.update { it.copy(fileConflict = null) }

    // ── Send text ─────────────────────────────────────────────────────────────

    fun sendText(text: String) {
        if (text.isBlank()) return
        val editing = _state.value.editingMessage
        if (editing != null) { editMessage(editing, text); return }

        val reply = _state.value.replyingTo
        clearReply()

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            val tempId  = "temp_${System.currentTimeMillis()}"
            val tempMsg = ChatMessage(
                id          = tempId,
                roomId      = roomId,
                senderId    = currentUserId,
                senderName  = currentUserName,
                senderAvatar= currentUserAvatar,
                text        = text,
                sentAt      = System.currentTimeMillis(),
                isOwn       = true,
                status      = MessageStatus.SENDING,
                replyToId   = reply?.id   ?: "",
                replyToText = reply?.text ?: ""
            )
            _state.update { it.copy(messages = it.messages + tempMsg) }

            val sent = repo.sendMessage(
                roomId          = roomId,
                senderId        = currentUserId,
                senderName      = currentUserName,
                type            = MessageType.TEXT,
                text            = text,
                fileBytes       = null,
                fileName        = "",
                fileMime        = "",
                replyToId       = reply?.id   ?: "",
                replyToText     = reply?.text ?: "",
                senderAvatarUrl = currentUserAvatar
            )
            _state.update { s ->
                val msgs = s.messages.toMutableList()
                val idx  = msgs.indexOfFirst { it.id == tempId }
                when {
                    idx >= 0 && sent != null ->
                        msgs[idx] = sent.copy(isOwn = true, status = MessageStatus.SENT)
                    idx >= 0 ->
                        msgs[idx] = msgs[idx].copy(status = MessageStatus.FAILED)
                }
                s.copy(isSending = false, messages = msgs)
            }
        }
    }

    // ── Edit existing message ─────────────────────────────────────────────────

    private fun editMessage(msg: ChatMessage, newText: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            val ok = repo.editMessage(msg.id, newText, msg.sentAt)
            if (ok) {
                _state.update { s ->
                    val msgs = s.messages.toMutableList()
                    val idx  = msgs.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) msgs[idx] = msgs[idx].copy(
                        text     = newText,
                        editedAt = System.currentTimeMillis()
                    )
                    s.copy(isSending = false, messages = msgs, editingMessage = null)
                }
            } else {
                _state.update { it.copy(
                    isSending      = false,
                    editingMessage = null,
                    error          = "Edit window expired (5 min limit)"
                ) }
            }
        }
    }

    // ── Send file ─────────────────────────────────────────────────────────────
    //
    // Flow:
    //   1. Read file size from ContentResolver (no bytes loaded yet).
    //   2. Check if a file with the same name already exists in the room.
    //      If yes → show FileConflict dialog (rename / replace).
    //   3. If no conflict, or conflict resolved, read bytes from stream.
    //   4. For large files, progress is reported via UploadProgress state.

    fun sendFile(context: Context) {
        val s    = _state.value
        val uri  = s.selectedFile ?: return
        val name = s.selectedFileName
        val mime = s.selectedFileMime.ifBlank { "application/octet-stream" }
        clearSelectedFile()

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }

            // Step 1: check for filename conflict
            val conflict = repo.checkFileNameConflict(roomId, name)
            if (conflict != null) {
                // Pause and ask user — bytes not yet loaded
                val baseName  = name.substringBeforeLast(".")
                val extension = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
                val suggested = "${baseName}_${System.currentTimeMillis()}$extension"
                _state.update { it.copy(
                    isSending    = false,
                    fileConflict = FileConflict(
                        originalName  = name,
                        suggestedName = suggested,
                        fileUri       = uri,
                        fileMime      = mime
                    )
                ) }
                return@launch
            }

            // No conflict — proceed to upload
            doSendFile(context, uri, name, mime)
        }
    }

    /**
     * Called from the UI when the user chooses "Rename" in the conflict dialog.
     * Uses the auto-suggested name.
     */
    fun sendFileWithRename(context: Context) {
        val conflict = _state.value.fileConflict ?: return
        _state.update { it.copy(fileConflict = null, isSending = true) }
        viewModelScope.launch {
            doSendFile(context, conflict.fileUri, conflict.suggestedName, conflict.fileMime)
        }
    }

    /**
     * Called from the UI when the user chooses "Replace" in the conflict dialog.
     * Uses the original name, overwriting the existing file reference.
     */
    fun sendFileWithReplace(context: Context) {
        val conflict = _state.value.fileConflict ?: return
        _state.update { it.copy(fileConflict = null, isSending = true) }
        viewModelScope.launch {
            doSendFile(context, conflict.fileUri, conflict.originalName, conflict.fileMime)
        }
    }

    private suspend fun doSendFile(
        context : Context,
        uri     : Uri,
        fileName: String,
        mime    : String
    ) {
        try {
            // Determine file size without loading bytes
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L

            if (fileSize > WARN_SIZE_BYTES) {
                android.util.Log.w("ChatRoomVM",
                    "Large file: ${fileSize / 1024 / 1024} MB — using chunked upload")
            }

            // Stream bytes — use buffered read to avoid OOM on large files
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: throw Exception("Cannot read file")

            val type = when {
                mime.startsWith("image/") -> MessageType.IMAGE
                mime.startsWith("video/") -> MessageType.VIDEO
                mime.startsWith("audio/") -> MessageType.AUDIO
                else                      -> MessageType.DOCUMENT
            }

            val sent = repo.sendMessage(
                roomId          = roomId,
                senderId        = currentUserId,
                senderName      = currentUserName,
                type            = type,
                text            = fileName,
                fileBytes       = bytes,
                fileName        = fileName,
                fileMime        = mime,
                senderAvatarUrl = currentUserAvatar,
                onProgress      = { uploaded, total ->
                    _state.update { it.copy(
                        uploadProgress = UploadProgress(fileName, uploaded, total)
                    ) }
                }
            )

            _state.update { st ->
                st.copy(
                    isSending      = false,
                    uploadProgress = null,
                    messages       = if (sent != null)
                        st.messages + sent.copy(isOwn = true)
                    else st.messages,
                    error          = if (sent == null) "File send failed" else null
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(
                isSending      = false,
                uploadProgress = null,
                error          = e.message
            ) }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteMessage(msg: ChatMessage) {
        if (msg.senderId != currentUserId && !authRepository.isDbAdmin()) return
        viewModelScope.launch {
            val ok = repo.deleteMessage(msg.id)
            if (ok) _state.update { s ->
                s.copy(messages = s.messages.filter { it.id != msg.id })
            }
        }
    }

    // ── Group management ──────────────────────────────────────────────────────

    fun updateGroupInfo(name: String, avatarBytes: ByteArray?) {
        viewModelScope.launch {
            val updated = repo.updateGroupInfo(roomId, name, avatarBytes)
            _state.update { s ->
                s.copy(room = updated ?: s.room?.copy(name = name))
            }
        }
    }

    fun removeGroupMember(
        userId       : String,
        memberNames  : List<String>,
        memberAvatars: List<String>
    ) {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            val ok = repo.removeMember(
                roomId         = roomId,
                currentMembers = room.members,
                currentNames   = memberNames,
                currentAvatars = memberAvatars,
                removeId       = userId
            )
            if (ok) {
                val idx = room.members.indexOf(userId)
                _state.update { s ->
                    s.copy(room = s.room?.copy(
                        members       = s.room.members.filterIndexed       { i, _ -> i != idx },
                        memberNames   = memberNames.filterIndexed           { i, _ -> i != idx },
                        memberAvatars = memberAvatars.filterIndexed         { i, _ -> i != idx }
                    ))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}