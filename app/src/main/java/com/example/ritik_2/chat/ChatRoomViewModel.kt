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
    private var currentUserAvatar = ""
    private var realtimeJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(room: ChatRoom) {
        this.roomId = room.id
        val session = authRepository.getSession() ?: return
        currentUserId    = session.userId
        currentUserName  = session.name
        // Avatar comes from session; or pass it explicitly from the calling screen
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
                    val msg     = newMsg.copy(isOwn = newMsg.senderId == currentUserId)
                    val current = _state.value.messages
                    val existIdx = current.indexOfFirst { it.id == msg.id }
                    if (existIdx >= 0) {
                        // Message was edited — update in place
                        val updated = current.toMutableList().also { it[existIdx] = msg }
                        _state.update { it.copy(messages = updated) }
                    } else {
                        _state.update { it.copy(messages = current + msg) }
                    }
                }
        }
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    fun setReplyTo(msg: ChatMessage?)    = _state.update { it.copy(replyingTo = msg) }
    fun clearReply()                     = _state.update { it.copy(replyingTo = null) }
    fun clearError()                     = _state.update { it.copy(error = null) }
    fun startEditing(msg: ChatMessage)   = _state.update { it.copy(editingMessage = msg) }
    fun clearEditing()                   = _state.update { it.copy(editingMessage = null) }

    fun setSelectedFile(uri: Uri, filename: String, mime: String) =
        _state.update { it.copy(selectedFile = uri, selectedFileName = filename, selectedFileMime = mime) }

    fun clearSelectedFile() =
        _state.update { it.copy(selectedFile = null, selectedFileName = "", selectedFileMime = "") }

    // ── Send text ─────────────────────────────────────────────────────────────

    fun sendText(text: String) {
        if (text.isBlank()) return

        // If in edit mode, update the existing message
        val editing = _state.value.editingMessage
        if (editing != null) {
            editMessage(editing, text)
            return
        }

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
                    isSending    = false,
                    editingMessage = null,
                    error        = "Edit window expired (5 min limit)"
                ) }
            }
        }
    }

    // ── Send file (background via WorkManager) ────────────────────────────────

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
                    else                      -> MessageType.DOCUMENT
                }
                val sent = repo.sendMessage(
                    roomId          = roomId,
                    senderId        = currentUserId,
                    senderName      = currentUserName,
                    type            = type,
                    text            = s.selectedFileName,
                    fileBytes       = bytes,
                    fileName        = s.selectedFileName,
                    fileMime        = mime,
                    senderAvatarUrl = currentUserAvatar
                )
                if (sent != null) {
                    _state.update { st ->
                        st.copy(isSending = false,
                            messages = st.messages + sent.copy(isOwn = true))
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