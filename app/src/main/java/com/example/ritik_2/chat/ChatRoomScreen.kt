package com.example.ritik_2.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable    // needs @OptIn below
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
    onPickCamera : () -> Unit
) {
    val state     by viewModel.state.collectAsState()
    val context   = LocalContext.current
    val listState  = rememberLazyListState()
    var text      by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var showInfo  by remember { mutableStateOf(false) }
    var editGroupName by remember { mutableStateOf("") }

    // Scroll to bottom on new message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            listState.animateScrollToItem(state.messages.lastIndex)
    }

    // Group info sheet
    if (showInfo && room.type == RoomType.GROUP) {
        GroupInfoSheet(
            room      = room,
            viewModel = viewModel,
            onDismiss = { showInfo = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Avatar
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            Alignment.Center
                        ) {
                            if (room.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(room.avatarUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier     = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    if (room.type == RoomType.GROUP) Icons.Default.Group
                                    else Icons.Default.Person, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(room.name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (room.type == RoomType.GROUP)
                                    "${room.members.size} members"
                                else "Direct message",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (room.type == RoomType.GROUP) {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Group Info")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            ChatInputBar(
                text            = text,
                onTextChange    = { text = it },
                replyingTo      = state.replyingTo,
                selectedFile    = state.selectedFile,
                selectedName    = state.selectedFileName,
                isSending       = state.isSending,
                showAttachMenu  = showAttach,
                onToggleAttach  = { showAttach = !showAttach },
                onSend          = {
                    if (state.selectedFile != null) viewModel.sendFile(context)
                    else if (text.isNotBlank()) { viewModel.sendText(text); text = "" }
                },
                onClearReply    = { viewModel.clearReply() },
                onClearFile     = { viewModel.clearSelectedFile() },
                onPickImage     = { showAttach = false; onPickImage() },
                onPickFile      = { showAttach = false; onPickFile() },
                onPickCamera    = { showAttach = false; onPickCamera() }
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Group messages by date
            val grouped = state.messages.groupBy { msgDateLabel(it.sentAt) }
            grouped.forEach { (dateLabel, msgs) ->
                item(key = "date_$dateLabel") {
                    DateSeparator(dateLabel)
                }
                items(msgs, key = { it.id }) { msg ->
                    MessageBubble(
                        msg       = msg,
                        onReply   = { viewModel.setReplyTo(msg) },
                        onDelete  = { viewModel.deleteMessage(msg) },
                        onOpen    = { url ->
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (_: Exception) {}
                        }
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
    msg     : ChatMessage,
    onReply : () -> Unit,
    onDelete: () -> Unit,
    onOpen  : (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isOwn) Arrangement.End else Arrangement.Start
    ) {
        // Sender avatar (only for others in group)
        if (!msg.isOwn) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.Bottom),
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
                    Text(msg.senderName.take(1).uppercase(), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 16.dp, topEnd = 16.dp,
                    bottomStart = if (msg.isOwn) 16.dp else 4.dp,
                    bottomEnd   = if (msg.isOwn) 4.dp  else 16.dp
                ),
                color = if (msg.isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick      = {
                            if (msg.type != MessageType.TEXT && msg.fileUrl.isNotBlank())
                                onOpen(msg.fileUrl)
                        },
                        onLongClick  = { showMenu = true }
                    )
            ) {
                Column(Modifier.padding(
                    horizontal = 12.dp,
                    vertical   = 8.dp
                )) {
                    // Sender name (group chats, others' messages)
                    if (!msg.isOwn && msg.senderName.isNotBlank()) {
                        Text(msg.senderName, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                    }

                    // Reply quote
                    if (msg.replyToText.isNotBlank()) {
                        Surface(
                            color = (if (msg.isOwn) Color.White else MaterialTheme.colorScheme.primary)
                                .copy(0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("↩ ${msg.replyToText.take(60)}",
                                Modifier.padding(6.dp),
                                fontSize = 11.sp,
                                color    = if (msg.isOwn) Color.White.copy(0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Content
                    when (msg.type) {
                        MessageType.TEXT -> Text(
                            msg.text,
                            color = if (msg.isOwn) Color.White
                            else MaterialTheme.colorScheme.onSurface
                        )
                        MessageType.IMAGE -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(msg.fileUrl).crossfade(true).build(),
                                contentDescription = "Image",
                                modifier     = Modifier.fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (msg.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(msg.text,
                                    color = if (msg.isOwn) Color.White
                                    else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        else -> FileAttachment(msg = msg, isOwn = msg.isOwn)
                    }

                    // Timestamp + status
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        Arrangement.End,
                        Alignment.CenterVertically
                    ) {
                        Text(formatMsgTime(msg.sentAt), fontSize = 10.sp,
                            color = if (msg.isOwn) Color.White.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (msg.isOwn) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                when (msg.status) {
                                    MessageStatus.SENDING   -> Icons.Default.Schedule
                                    MessageStatus.FAILED    -> Icons.Default.Error
                                    MessageStatus.READ      -> Icons.Default.DoneAll
                                    else                    -> Icons.Default.Done
                                },
                                null,
                                modifier = Modifier.size(12.dp),
                                tint     = when (msg.status) {
                                    MessageStatus.READ   -> Color(0xFF4FC3F7)
                                    MessageStatus.FAILED -> Color.Red
                                    else                 -> Color.White.copy(0.7f)
                                }
                            )
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text        = { Text("Reply") },
                    leadingIcon = { Icon(Icons.Default.Reply, null) },
                    onClick     = { showMenu = false; onReply() }
                )
                if (msg.fileUrl.isNotBlank()) {
                    DropdownMenuItem(
                        text        = { Text("Open") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                        onClick     = { showMenu = false; onOpen(msg.fileUrl) }
                    )
                }
                if (msg.isOwn) {
                    DropdownMenuItem(
                        text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick     = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileAttachment(msg: ChatMessage, isOwn: Boolean) {
    val textColor = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            when (msg.type) {
                MessageType.AUDIO    -> Icons.Default.AudioFile
                MessageType.VIDEO    -> Icons.Default.VideoFile
                MessageType.DOCUMENT -> Icons.Default.Description
                else                 -> Icons.Default.AttachFile
            },
            null,
            tint     = if (isOwn) Color.White.copy(0.9f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(msg.fileName.ifBlank { "File" }, fontSize = 13.sp,
                color = textColor, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (msg.fileSize > 0)
                Text(formatFileSize(msg.fileSize), fontSize = 10.sp,
                    color = textColor.copy(0.7f))
        }
        Icon(Icons.Default.Download, null,
            tint     = if (isOwn) Color.White.copy(0.8f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text           : String,
    onTextChange   : (String) -> Unit,
    replyingTo     : ChatMessage?,
    selectedFile   : Uri?,
    selectedName   : String,
    isSending      : Boolean,
    showAttachMenu : Boolean,
    onToggleAttach : () -> Unit,
    onSend         : () -> Unit,
    onClearReply   : () -> Unit,
    onClearFile    : () -> Unit,
    onPickImage    : () -> Unit,
    onPickFile     : () -> Unit,
    onPickCamera   : () -> Unit
) {
    val canSend = (text.isNotBlank() || selectedFile != null) && !isSending

    Surface(
        Modifier.fillMaxWidth(),
        color          = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {

            // Reply preview
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Reply, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Replying to ${reply.senderName}",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(reply.text.take(60),
                                    fontSize = 11.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onClearReply, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // File preview
            AnimatedVisibility(visible = selectedFile != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, null,
                            tint     = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp))
                        Text(selectedName.ifBlank { "File selected" },
                            Modifier.weight(1f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onClearFile, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Attach menu
            AnimatedVisibility(visible = showAttachMenu) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachOption(Icons.Default.Image, "Image",
                        Color(0xFF1976D2), onPickImage)
                    AttachOption(Icons.Default.Camera, "Camera",
                        Color(0xFF388E3C), onPickCamera)
                    AttachOption(Icons.Default.Description, "Document",
                        Color(0xFFF57C00), onPickFile)
                }
            }

            // Text row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onToggleAttach) {
                    Icon(
                        if (showAttachMenu) Icons.Default.KeyboardArrowDown
                        else Icons.Default.Add,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value           = text,
                    onValueChange   = onTextChange,
                    placeholder     = { Text("Message…") },
                    modifier        = Modifier.weight(1f),
                    maxLines        = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences),
                    shape           = RoundedCornerShape(24.dp)
                )

                // Send / loading
                Box(Modifier.size(48.dp), Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSend, enabled = canSend) {
                            Icon(Icons.Default.Send, "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    color  : Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement    = Arrangement.spacedBy(4.dp),
        modifier               = Modifier.clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(0.12f)),
            Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Group info bottom sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    room     : ChatRoom,
    viewModel: ChatRoomViewModel,
    onDismiss: () -> Unit
) {
    var editName    by remember { mutableStateOf(room.name) }
    var editMode    by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Group, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                if (editMode) {
                    OutlinedTextField(
                        value       = editName,
                        onValueChange = { editName = it },
                        label       = { Text("Group name") },
                        singleLine  = true,
                        modifier    = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        viewModel.updateGroupInfo(editName, null)
                        editMode = false
                    }) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }
                    IconButton(onClick = { editName = room.name; editMode = false }) {
                        Icon(Icons.Default.Close, null)
                    }
                } else {
                    Text(room.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editMode = true }) {
                        Icon(Icons.Default.Edit, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("${room.members.size} members",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            // Member list
            Text("Members", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)

            room.memberNames.zip(room.members).forEach { (name, userId) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                        Alignment.Center) {
                        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(name, Modifier.weight(1f))
                    if (userId in room.adminIds)
                        Surface(color = Color(0xFF1976D2).copy(0.12f),
                            shape = RoundedCornerShape(6.dp)) {
                            Text("admin", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp, color = Color(0xFF1976D2),
                                fontWeight = FontWeight.Bold)
                        }
                    else {
                        IconButton(onClick = { viewModel.removeGroupMember(userId) },
                            modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PersonRemove, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun msgDateLabel(ts: Long): String {
    val now   = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DAY_OF_YEAR)  == msgCal.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR)         == msgCal.get(Calendar.YEAR)         -> "Today"
        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 &&
                now.get(Calendar.YEAR)         == msgCal.get(Calendar.YEAR)         -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatMsgTime(ts: Long) =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else                -> "${bytes / (1024 * 1024)} MB"
}