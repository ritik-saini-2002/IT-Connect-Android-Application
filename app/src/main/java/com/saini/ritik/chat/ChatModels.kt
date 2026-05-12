package com.saini.ritik.chat

import android.net.Uri

enum class MessageType { TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO }
enum class RoomType    { DIRECT, GROUP, BROADCAST }

data class ChatRoom(
    val id           : String,
    val name         : String,
    val type         : RoomType,
    val avatarUrl    : String       = "",
    val members      : List<String> = emptyList(),
    val memberNames  : List<String> = emptyList(),
    val memberAvatars: List<String> = emptyList(),
    val adminIds     : List<String> = emptyList(),
    val companyName  : String       = "",
    val lastMessage  : String       = "",
    val lastMessageAt: Long         = 0L,
    val unreadCount  : Int          = 0,
    val isOnline     : Boolean      = false
)

data class ChatMessage(
    val id           : String,
    val roomId       : String,
    val senderId     : String,
    val senderName   : String,
    val senderAvatar : String        = "",
    val type         : MessageType   = MessageType.TEXT,
    val text         : String        = "",
    val fileUrl      : String        = "",
    val fileName     : String        = "",
    val fileSize     : Long          = 0L,
    val fileMime     : String        = "",
    val thumbnailUrl : String        = "",
    val sentAt       : Long          = System.currentTimeMillis(),
    val editedAt     : Long          = 0L,
    val isOwn        : Boolean       = false,
    val status       : MessageStatus = MessageStatus.SENT,
    val replyToId    : String        = "",
    val replyToText  : String        = ""
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class ChatMember(
    val userId          : String,
    val name            : String,
    val role            : String,
    val department      : String,
    val imageUrl        : String  = "",
    val isOnline        : Boolean = false,
    val companyName     : String  = "",
    val sanitizedCompany: String  = ""
)

// ── File conflict resolution ──────────────────────────────────────────────────

/**
 * Shown when a file with the same name already exists in the room's message history.
 * The user can choose to rename (auto-suffix) or replace (overwrite with same name).
 */
data class FileConflict(
    val originalName: String,
    val suggestedName: String,   // auto-suffixed name
    val fileUri     : Uri,
    val fileMime    : String,
    val fileBytes   : ByteArray? = null
)

// ── Upload progress ───────────────────────────────────────────────────────────

data class UploadProgress(
    val fileName     : String,
    val bytesUploaded: Long,
    val totalBytes   : Long
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesUploaded * 100) / totalBytes).toInt() else 0
    val isDone : Boolean get() = bytesUploaded >= totalBytes
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class ChatListUiState(
    val isLoading      : Boolean        = true,
    val rooms          : List<ChatRoom> = emptyList(),
    val error          : String?        = null,
    val currentUserId  : String         = "",
    val currentUserName: String         = ""
)

data class ChatRoomUiState(
    val isLoading        : Boolean           = true,
    val room             : ChatRoom?         = null,
    val messages         : List<ChatMessage> = emptyList(),
    val isSending        : Boolean           = false,
    val error            : String?           = null,
    val replyingTo       : ChatMessage?      = null,
    val editingMessage   : ChatMessage?      = null,
    val selectedFile     : Uri?              = null,
    val selectedFileName : String            = "",
    val selectedFileMime : String            = "",
    // File conflict — non-null when same filename already exists
    val fileConflict     : FileConflict?     = null,
    // Upload progress — non-null while a large file is uploading
    val uploadProgress   : UploadProgress?   = null
)

data class MemberPickerUiState(
    val isLoading  : Boolean          = true,
    val members    : List<ChatMember> = emptyList(),
    val filtered   : List<ChatMember> = emptyList(),
    val selected   : Set<String>      = emptySet(),
    val departments: List<String>     = emptyList(),
    val roles      : List<String>     = emptyList(),
    val filterDept : String           = "",
    val filterRole : String           = "",
    val searchQuery: String           = ""
)