package com.example.ritik_2.chat

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatRoomScreen(
    room         : ChatRoom,
    viewModel    : ChatRoomViewModel,
    onBack       : () -> Unit,
    onPickFile   : () -> Unit,
    onPickImage  : () -> Unit,
    onPickCamera : () -> Unit,
    onViewProfile: (String) -> Unit
) {
    val state      by viewModel.state.collectAsState()
    val context    = LocalContext.current
    val listState   = rememberLazyListState()
    var text       by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var showInfo   by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.editingMessage?.id) {
        text = state.editingMessage?.text ?: ""
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            listState.animateScrollToItem(state.messages.lastIndex)
    }

    // Fullscreen image viewer
    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .clickable { fullscreenImageUrl = null },
                Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url).crossfade(true).build(),
                    contentDescription = "Full image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) { Icon(Icons.Default.Close, null, tint = Color.White) }
                IconButton(
                    onClick = { downloadFile(context, url, url.substringAfterLast("/"), "image/*") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Download, null,
                        tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showInfo && room.type == RoomType.GROUP) {
        GroupInfoSheet(
            room          = room,
            viewModel     = viewModel,
            onDismiss     = { showInfo = false },
            onViewProfile = onViewProfile
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable {
                            if (room.type == RoomType.GROUP) showInfo = true
                            else {
                                val otherId = room.members
                                    .firstOrNull { it != state.room?.members?.firstOrNull() }
                                    ?: room.members.firstOrNull() ?: return@clickable
                                onViewProfile(otherId)
                            }
                        }
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            Alignment.Center
                        ) {
                            if (room.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(room.avatarUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    if (room.type == RoomType.GROUP) Icons.Default.Group
                                    else Icons.Default.Person, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(room.name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (room.type == RoomType.GROUP)
                                    "${room.members.size} members"
                                else "Tap to view profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (room.type == RoomType.GROUP)
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Group Info")
                        }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text           = text,
                onTextChange   = { text = it },
                replyingTo     = state.replyingTo,
                editingMessage = state.editingMessage,
                selectedFile   = state.selectedFile,
                selectedName   = state.selectedFileName,
                isSending      = state.isSending,
                showAttachMenu = showAttach,
                onToggleAttach = { showAttach = !showAttach },
                onSend         = {
                    when {
                        state.selectedFile != null -> viewModel.sendFile(context)
                        text.isNotBlank()          -> { viewModel.sendText(text); text = "" }
                    }
                },
                onClearReply   = { viewModel.clearReply() },
                onCancelEdit   = { viewModel.clearEditing(); text = "" },
                onClearFile    = { viewModel.clearSelectedFile() },
                onPickImage    = { showAttach = false; onPickImage() },
                onPickFile     = { showAttach = false; onPickFile() },
                onPickCamera   = { showAttach = false; onPickCamera() }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val grouped = state.messages.groupBy { msgDateLabel(it.sentAt) }
            grouped.forEach { (dateLabel, msgs) ->
                item(key = "date_$dateLabel") { DateSeparator(dateLabel) }
                items(msgs, key = { it.id }) { msg ->
                    MessageBubble(
                        msg           = msg,
                        isGroup       = room.type == RoomType.GROUP,
                        onReply       = { viewModel.setReplyTo(msg) },
                        onEdit        = { viewModel.startEditing(msg) },
                        onDelete      = { viewModel.deleteMessage(msg) },
                        onAvatarClick = { onViewProfile(msg.senderId) },
                        onImageClick  = { url -> fullscreenImageUrl = url },
                        onFileOpen    = { url, name, mime -> downloadOrOpen(context, url, name, mime) }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg          : ChatMessage,
    isGroup      : Boolean,
    onReply      : () -> Unit,
    onEdit       : () -> Unit,
    onDelete     : () -> Unit,
    onAvatarClick: () -> Unit,
    onImageClick : (String) -> Unit,
    onFileOpen   : (String, String, String) -> Unit
) {
    var showMenu  by remember { mutableStateOf(false) }
    val editAllowed = msg.isOwn && (System.currentTimeMillis() - msg.sentAt) < 5 * 60_000L

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalArrangement = if (msg.isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        if (!msg.isOwn) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onAvatarClick() },
                Alignment.Center
            ) {
                if (msg.senderAvatar.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(msg.senderAvatar).crossfade(true).build(),
                        contentDescription = null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(msg.senderName.take(1).uppercase(), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(horizontalAlignment = if (msg.isOwn) Alignment.End else Alignment.Start) {
            if (!msg.isOwn && isGroup && msg.senderName.isNotBlank()) {
                Text(msg.senderName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart    = 18.dp, topEnd = 18.dp,
                        bottomStart = if (msg.isOwn) 18.dp else 4.dp,
                        bottomEnd   = if (msg.isOwn) 4.dp  else 18.dp
                    ),
                    color = if (msg.isOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 280.dp).combinedClickable(
                        onClick = {
                            when (msg.type) {
                                MessageType.IMAGE ->
                                    if (msg.fileUrl.isNotBlank()) onImageClick(msg.fileUrl)
                                MessageType.VIDEO, MessageType.DOCUMENT, MessageType.AUDIO ->
                                    if (msg.fileUrl.isNotBlank())
                                        onFileOpen(msg.fileUrl, msg.fileName, msg.fileMime)
                                else -> {}
                            }
                        },
                        onLongClick = { showMenu = true }
                    )
                ) {
                    Column(Modifier.padding(
                        start  = if (msg.type == MessageType.IMAGE) 0.dp else 10.dp,
                        end    = if (msg.type == MessageType.IMAGE) 0.dp else 10.dp,
                        top    = if (msg.type == MessageType.IMAGE) 0.dp else 8.dp,
                        bottom = 6.dp
                    )) {
                        // Reply quote
                        if (msg.replyToText.isNotBlank()) {
                            Surface(
                                color = (if (msg.isOwn) Color.White
                                else MaterialTheme.colorScheme.primary).copy(0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = if (msg.type == MessageType.IMAGE) 8.dp else 0.dp,
                                    end   = if (msg.type == MessageType.IMAGE) 8.dp else 0.dp,
                                    top   = if (msg.type == MessageType.IMAGE) 8.dp else 0.dp
                                )
                            ) {
                                Text("↩ ${msg.replyToText.take(80)}",
                                    Modifier.padding(6.dp), fontSize = 11.sp,
                                    color = if (msg.isOwn) Color.White.copy(0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (msg.type != MessageType.IMAGE) Spacer(Modifier.height(4.dp))
                        }

                        // Content
                        when (msg.type) {
                            MessageType.TEXT -> Text(msg.text,
                                color = if (msg.isOwn) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 2.dp))

                            MessageType.IMAGE -> {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(msg.fileUrl).crossfade(true).build(),
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .heightIn(min = 80.dp, max = 260.dp)
                                        .clip(RoundedCornerShape(
                                            topStart    = 18.dp, topEnd = 18.dp,
                                            bottomStart = if (msg.isOwn) 18.dp else 4.dp,
                                            bottomEnd   = if (msg.isOwn) 4.dp  else 18.dp
                                        )),
                                    contentScale = ContentScale.Crop
                                )
                                if (msg.text.isNotBlank() && msg.text != msg.fileName) {
                                    Text(msg.text,
                                        color = if (msg.isOwn) Color.White
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }

                            MessageType.VIDEO -> {
                                Box(
                                    Modifier.widthIn(max = 260.dp).height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black),
                                    Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(msg.fileUrl).crossfade(true).build(),
                                        contentDescription = "Video",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        Modifier.size(52.dp).clip(CircleShape)
                                            .background(Color.Black.copy(0.55f)),
                                        Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null,
                                            tint = Color.White, modifier = Modifier.size(34.dp))
                                    }
                                }
                                if (msg.fileName.isNotBlank())
                                    Text(msg.fileName, fontSize = 11.sp,
                                        color = if (msg.isOwn) Color.White.copy(0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            MessageType.AUDIO -> {
                                Row(
                                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.AudioFile, null,
                                        tint = if (msg.isOwn) Color.White
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp))
                                    Column {
                                        Text(msg.fileName.ifBlank { "Audio" }, fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (msg.isOwn) Color.White
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (msg.fileSize > 0)
                                            Text(formatFileSize(msg.fileSize), fontSize = 10.sp,
                                                color = if (msg.isOwn) Color.White.copy(0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.PlayCircle, null,
                                        tint = if (msg.isOwn) Color.White
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp))
                                }
                            }

                            MessageType.DOCUMENT -> {
                                Row(
                                    Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (msg.isOwn) Color.White.copy(0.2f)
                                                else MaterialTheme.colorScheme.primary.copy(0.12f)),
                                        Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Description, null,
                                            tint = if (msg.isOwn) Color.White
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(msg.fileName.ifBlank { "Document" }, fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (msg.isOwn) Color.White
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (msg.fileSize > 0)
                                            Text(formatFileSize(msg.fileSize), fontSize = 10.sp,
                                                color = if (msg.isOwn) Color.White.copy(0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Download, null,
                                        tint = if (msg.isOwn) Color.White
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // Timestamp + status
                        Row(
                            Modifier.padding(
                                start = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                end   = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                top   = 2.dp
                            ).align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (msg.editedAt > 0L)
                                Text("edited", fontSize = 9.sp,
                                    color = if (msg.isOwn) Color.White.copy(0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMsgTime(msg.sentAt), fontSize = 10.sp,
                                color = if (msg.isOwn) Color.White.copy(0.75f)
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (msg.isOwn)
                                Icon(
                                    when (msg.status) {
                                        MessageStatus.SENDING -> Icons.Default.Schedule
                                        MessageStatus.FAILED  -> Icons.Default.Error
                                        MessageStatus.READ    -> Icons.Default.DoneAll
                                        else                  -> Icons.Default.Done
                                    }, null,
                                    modifier = Modifier.size(12.dp),
                                    tint = when (msg.status) {
                                        MessageStatus.READ   -> Color(0xFF4FC3F7)
                                        MessageStatus.FAILED -> Color.Red
                                        else                 -> Color.White.copy(0.7f)
                                    }
                                )
                        }
                    }
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("Reply") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                        onClick     = { showMenu = false; onReply() }
                    )
                    if (msg.fileUrl.isNotBlank())
                        DropdownMenuItem(
                            text        = { Text("Download") },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick     = { showMenu = false; onFileOpen(msg.fileUrl, msg.fileName, msg.fileMime) }
                        )
                    if (msg.isOwn && editAllowed && msg.type == MessageType.TEXT)
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick     = { showMenu = false; onEdit() }
                        )
                    if (msg.isOwn)
                        DropdownMenuItem(
                            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() }
                        )
                }
            }
        }

        if (msg.isOwn) Spacer(Modifier.width(4.dp))
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text           : String,
    onTextChange   : (String) -> Unit,
    replyingTo     : ChatMessage?,
    editingMessage : ChatMessage?,
    selectedFile   : Uri?,
    selectedName   : String,
    isSending      : Boolean,
    showAttachMenu : Boolean,
    onToggleAttach : () -> Unit,
    onSend         : () -> Unit,
    onClearReply   : () -> Unit,
    onCancelEdit   : () -> Unit,
    onClearFile    : () -> Unit,
    onPickImage    : () -> Unit,
    onPickFile     : () -> Unit,
    onPickCamera   : () -> Unit
) {
    val canSend = (text.isNotBlank() || selectedFile != null) && !isSending

    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {

            AnimatedVisibility(visible = editingMessage != null) {
                editingMessage?.let { em ->
                    Surface(color = Color(0xFFFFF9C4)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Edit, null,
                                tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Editing message", fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = Color(0xFFF57F17))
                                Text(em.text.take(60), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onCancelEdit, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Replying to ${reply.senderName}", fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(reply.text.take(60), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onClearReply, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedFile != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AttachFile, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp))
                        Text(selectedName.ifBlank { "File selected" }, Modifier.weight(1f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onClearFile, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showAttachMenu) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachOption(Icons.Default.Image,       "Image",    Color(0xFF1976D2), onPickImage)
                    AttachOption(Icons.Default.Camera,      "Camera",   Color(0xFF388E3C), onPickCamera)
                    AttachOption(Icons.Default.Description, "Document", Color(0xFFF57C00), onPickFile)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleAttach) {
                    Icon(
                        if (showAttachMenu) Icons.Default.KeyboardArrowDown else Icons.Default.Add,
                        null, tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value           = text,
                    onValueChange   = onTextChange,
                    placeholder     = { Text(if (editingMessage != null) "Edit message…" else "Message…") },
                    modifier        = Modifier.weight(1f),
                    maxLines        = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape           = RoundedCornerShape(24.dp)
                )
                Box(Modifier.size(48.dp), Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSend, enabled = canSend) {
                            Icon(
                                if (editingMessage != null) Icons.Default.Check
                                else Icons.AutoMirrored.Filled.Send, "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, color: Color, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(0.12f)),
            Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Group info sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    room         : ChatRoom,
    viewModel    : ChatRoomViewModel,
    onDismiss    : () -> Unit,
    onViewProfile: (String) -> Unit
) {
    val context   = LocalContext.current
    var editMode  by remember { mutableStateOf(false) }
    var editName  by remember { mutableStateOf(room.name) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()) { uri -> avatarUri = uri }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            // Header
            Box(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(20.dp),
                Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Box(
                            Modifier.size(90.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(0.2f))
                                .clickable { avatarPicker.launch("image/*") },
                            Alignment.Center
                        ) {
                            val displayData = avatarUri
                                ?: if (room.avatarUrl.isNotBlank()) Uri.parse(room.avatarUrl) else null
                            if (displayData != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(displayData).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Group, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp))
                            }
                        }
                        Box(Modifier.size(26.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.BottomEnd), Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (editMode) {
                        OutlinedTextField(
                            value         = editName,
                            onValueChange = { editName = it },
                            label         = { Text("Group name") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(0.8f),
                            trailingIcon  = {
                                Row {
                                    IconButton(onClick = {
                                        val bytes = avatarUri?.let { uri ->
                                            context.contentResolver.openInputStream(uri)?.readBytes()
                                        }
                                        viewModel.updateGroupInfo(editName, bytes)
                                        avatarUri = null; editMode = false
                                    }) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }
                                    IconButton(onClick = { editName = room.name; editMode = false }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(room.name, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            IconButton(onClick = { editMode = true }) {
                                Icon(Icons.Default.Edit, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text("${room.members.size} members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Add member
            ListItem(
                headlineContent = { Text("Add member", fontWeight = FontWeight.Medium) },
                leadingContent  = {
                    Box(Modifier.size(42.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(0.12f)),
                        Alignment.Center) {
                        Icon(Icons.Default.PersonAdd, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clickable { /* TODO: launch member picker */ }
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            Text("${room.members.size} members",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            room.members.forEachIndexed { idx, userId ->
                val name      = room.memberNames.getOrElse(idx) { "Member" }
                val avatarUrl = room.memberAvatars.getOrElse(idx) { "" }
                val isAdmin   = userId in room.adminIds

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(name, fontWeight = FontWeight.Medium)
                            if (isAdmin)
                                Surface(color = Color(0xFF1976D2).copy(0.15f),
                                    shape = RoundedCornerShape(4.dp)) {
                                    Text("admin",
                                        Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = Color(0xFF1976D2),
                                        fontWeight = FontWeight.Bold)
                                }
                        }
                    },
                    leadingContent = {
                        Box(Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onViewProfile(userId) }, Alignment.Center) {
                            if (avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    trailingContent = {
                        if (!isAdmin)
                            IconButton(onClick = {
                                viewModel.removeGroupMember(
                                    userId        = userId,
                                    memberNames   = room.memberNames,
                                    memberAvatars = room.memberAvatars
                                )
                            }) {
                                Icon(Icons.Default.PersonRemove, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                            }
                    },
                    modifier = Modifier.clickable { onViewProfile(userId) }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun downloadOrOpen(context: Context, url: String, fileName: String, mime: String) {
    if (mime.startsWith("image/") || mime.startsWith("video/")) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setDataAndType(Uri.parse(url), mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent); return
        }
    }
    downloadFile(context, url, fileName, mime)
}

private fun downloadFile(context: Context, url: String, fileName: String, mime: String) {
    try {
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName.ifBlank { "Download" })
            setDescription("Downloading…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                fileName.ifBlank { "itconnect_${System.currentTimeMillis()}" })
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        android.widget.Toast.makeText(context,
            "Downloading \"${fileName.ifBlank { "file" }}\"…",
            android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot download file",
            android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun msgDateLabel(ts: Long): String {
    val now    = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> "Today"
        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 &&
                now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatMsgTime(ts: Long) =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatFileSize(bytes: Long) = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else                -> "${bytes / (1024 * 1024)} MB"
}