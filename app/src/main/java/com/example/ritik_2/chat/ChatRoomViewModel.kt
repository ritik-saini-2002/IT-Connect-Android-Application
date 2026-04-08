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

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val repo          : ChatRepository,
    private val authRepository: AuthRepository,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ChatRoomUiState())
    val state: StateFlow<ChatRoomUiState> = _state.asStateFlow()

    private var roomId          = ""
    private var currentUserId   = ""
    private var currentUserName = ""
    private var realtimeJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(roomId: String) {
        this.roomId     = roomId
        val session     = authRepository.getSession() ?: return
        currentUserId   = session.userId
        currentUserName = session.name
        loadMessages()
        startRealtime()
    }

    // ── Load history ──────────────────────────────────────────────────────────

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

    // ── Realtime subscription ─────────────────────────────────────────────────

    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            repo.subscribeToRoom(roomId)
                .catch { e -> android.util.Log.w("ChatRoomVM", "Realtime: ${e.message}") }
                .collect { newMsg ->
                    val msg     = newMsg.copy(isOwn = newMsg.senderId == currentUserId)
                    val current = _state.value.messages
                    // Deduplicate — realtime may echo our own optimistic message
                    if (current.none { it.id == msg.id }) {
                        _state.update { it.copy(messages = current + msg) }
                    }
                }
        }
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    fun setReplyTo(msg: ChatMessage?) = _state.update { it.copy(replyingTo = msg) }
    fun clearReply()                  = _state.update { it.copy(replyingTo = null) }
    fun clearError()                  = _state.update { it.copy(error = null) }

    fun setSelectedFile(uri: Uri, filename: String, mime: String) {
        _state.update {
            it.copy(
                selectedFile      = uri,
                selectedFileName  = filename,
                selectedFileMime  = mime
            )
        }
    }

    fun clearSelectedFile() = _state.update {
        it.copy(selectedFile = null, selectedFileName = "", selectedFileMime = "")
    }

    // ── Send text ─────────────────────────────────────────────────────────────

    fun sendText(text: String) {
        if (text.isBlank()) return
        val reply = _state.value.replyingTo
        clearReply()

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }

            // Optimistic bubble — shown immediately
            val tempId  = "temp_${System.currentTimeMillis()}"
            val tempMsg = ChatMessage(
                id          = tempId,
                roomId      = roomId,
                senderId    = currentUserId,
                senderName  = currentUserName,
                text        = text,
                sentAt      = System.currentTimeMillis(),
                isOwn       = true,
                status      = MessageStatus.SENDING,
                replyToId   = reply?.id   ?: "",
                replyToText = reply?.text ?: ""
            )
            _state.update { it.copy(messages = it.messages + tempMsg) }

            // Real send
            val sent = repo.sendMessage(
                roomId      = roomId,
                senderId    = currentUserId,
                senderName  = currentUserName,
                type        = MessageType.TEXT,
                text        = text,
                fileBytes   = null,
                fileName    = "",
                fileMime    = "",
                replyToId   = reply?.id   ?: "",
                replyToText = reply?.text ?: ""
            )

            // Replace temp with confirmed message or mark failed
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

    // ── Send file / image ─────────────────────────────────────────────────────

    fun sendFile(context: Context) {
        val s   = _state.value
        val uri = s.selectedFile ?: return
        clearSelectedFile()

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Cannot read file")
                val mime  = s.selectedFileMime.ifBlank { "application/octet-stream" }
                val type  = when {
                    mime.startsWith("image/") -> MessageType.IMAGE
                    mime.startsWith("video/") -> MessageType.VIDEO
                    mime.startsWith("audio/") -> MessageType.AUDIO
                    else                       -> MessageType.DOCUMENT
                }

                val sent = repo.sendMessage(
                    roomId     = roomId,
                    senderId   = currentUserId,
                    senderName = currentUserName,
                    type       = type,
                    text       = s.selectedFileName,
                    fileBytes  = bytes,
                    fileName   = s.selectedFileName,
                    fileMime   = mime
                )

                if (sent != null) {
                    _state.update { st ->
                        st.copy(
                            isSending = false,
                            messages  = st.messages + sent.copy(isOwn = true)
                        )
                    }
                } else {
                    _state.update { it.copy(isSending = false, error = "File send failed") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteMessage(msg: ChatMessage) {
        // Only sender or DB admin can delete
        if (msg.senderId != currentUserId && !authRepository.isDbAdmin()) return
        viewModelScope.launch {
            val ok = repo.deleteMessage(msg.id)
            if (ok) {
                _state.update { s ->
                    s.copy(messages = s.messages.filter { it.id != msg.id })
                }
            }
        }
    }

    // ── Group management ──────────────────────────────────────────────────────

    fun updateGroupInfo(name: String, avatarBytes: ByteArray?) {
        viewModelScope.launch {
            val ok = repo.updateGroupInfo(roomId, name, avatarBytes)
            if (ok) {
                _state.update { s -> s.copy(room = s.room?.copy(name = name)) }
            }
        }
    }

    fun removeGroupMember(userId: String) {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            repo.removeMember(roomId, room.members, userId)
            _state.update { s ->
                s.copy(room = s.room?.copy(
                    members = s.room.members.filter { it != userId }
                ))
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}