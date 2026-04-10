package com.example.ritik_2.chat

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

// ── Glassmorphism colour tokens ───────────────────────────────────────────────

private val GlassWhite12  = Color.White.copy(alpha = 0.12f)
private val GlassWhite18  = Color.White.copy(alpha = 0.18f)
private val GlassWhite22  = Color.White.copy(alpha = 0.22f)
private val GlassBorder   = Color.White.copy(alpha = 0.30f)
private val GlassBorder16 = Color.White.copy(alpha = 0.16f)
private val AccentBlue    = Color(0xFF4F8EF7)
private val AccentPurple  = Color(0xFF9B72F7)
private val AccentTeal    = Color(0xFF43E8D8)
private val BgDark1       = Color(0xFF0D1B2A)
private val BgDark2       = Color(0xFF1B2B44)
private val BgDark3       = Color(0xFF162032)

// Gradient used for the entire screen background
private val chatBgBrush = Brush.linearGradient(
    colors = listOf(BgDark1, BgDark2, BgDark3),
    start  = Offset(0f, 0f),
    end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Sent-bubble gradient
private val sentBubbleBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF4F8EF7), Color(0xFF9B72F7)),
    start  = Offset(0f, 0f),
    end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// ── Main screen ───────────────────────────────────────────────────────────────

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
    val state     by viewModel.state.collectAsState()
    val context   = LocalContext.current
    val listState  = rememberLazyListState()
    var text      by remember { mutableStateOf("") }
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

    // ── File conflict dialog ──────────────────────────────────────────────────
    state.fileConflict?.let { conflict ->
        GlassFileConflictDialog(
            conflict  = conflict,
            onRename  = { viewModel.sendFileWithRename(context) },
            onReplace = { viewModel.sendFileWithReplace(context) },
            onDismiss = { viewModel.dismissFileConflict() }
        )
    }

    // ── Upload progress dialog ────────────────────────────────────────────────
    state.uploadProgress?.let { progress ->
        GlassUploadProgressDialog(progress = progress)
    }

    // ── Fullscreen image viewer ───────────────────────────────────────────────
    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.95f))
                    .clickable { fullscreenImageUrl = null },
                Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    contentDescription = "Full image",
                    modifier     = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                // Glass close button
                GlassIconButton(
                    icon     = Icons.Default.Close,
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                    onClick  = { fullscreenImageUrl = null }
                )
                GlassIconButton(
                    icon     = Icons.Default.Download,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    onClick  = { downloadFile(context, url, url.substringAfterLast("/"), "image/*") }
                )
            }
        }
    }

    // ── Group info ────────────────────────────────────────────────────────────
    if (showInfo && room.type == RoomType.GROUP) {
        GlassGroupInfoSheet(
            room          = room,
            viewModel     = viewModel,
            onDismiss     = { showInfo = false },
            onViewProfile = onViewProfile
        )
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(chatBgBrush)) {

        // Decorative blurred orbs in the background
        BackgroundOrbs()

        Scaffold(
            containerColor = Color.Transparent,
            topBar         = {
                GlassTopBar(
                    room         = room,
                    state        = state,
                    onBack       = onBack,
                    onShowInfo   = { showInfo = true },
                    onViewProfile = onViewProfile
                )
            },
            bottomBar = {
                GlassChatInputBar(
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
                    CircularProgressIndicator(color = AccentBlue)
                }
                return@Scaffold
            }

            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val grouped = state.messages.groupBy { msgDateLabel(it.sentAt) }
                grouped.forEach { (dateLabel, msgs) ->
                    item(key = "date_$dateLabel") { GlassDateSeparator(dateLabel) }
                    items(msgs, key = { it.id }) { msg ->
                        GlassMessageBubble(
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
}

// ── Background decoration ─────────────────────────────────────────────────────

@Composable
private fun BackgroundOrbs() {
    val inf = rememberInfiniteTransition(label = "orbs")
    val orb1 by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb1")
    val orb2 by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb2")

    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(AccentBlue.copy(0.25f + orb1 * 0.1f), Color.Transparent),
                radius = size.width * 0.55f
            ),
            radius = size.width * 0.55f,
            center = Offset(size.width * 0.15f, size.height * 0.15f)
        )
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(AccentPurple.copy(0.20f + orb2 * 0.08f), Color.Transparent),
                radius = size.width * 0.5f
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width * 0.85f, size.height * 0.65f)
        )
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(AccentTeal.copy(0.12f), Color.Transparent),
                radius = size.width * 0.4f
            ),
            radius = size.width * 0.4f,
            center = Offset(size.width * 0.5f, size.height * 0.92f)
        )
    }
}

// ── Glass top bar ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTopBar(
    room         : ChatRoom,
    state        : ChatRoomUiState,
    onBack       : () -> Unit,
    onShowInfo   : () -> Unit,
    onViewProfile: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .glassBackground(cornerRadius = 0.dp, alpha = 0.18f)
            .drawBehind {
                drawLine(GlassBorder16, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            // Avatar
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(GlassWhite18)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable {
                        if (room.type == RoomType.GROUP) onShowInfo()
                        else {
                            val otherId = room.members
                                .firstOrNull { it != state.room?.members?.firstOrNull() }
                                ?: room.members.firstOrNull() ?: return@clickable
                            onViewProfile(otherId)
                        }
                    },
                Alignment.Center
            ) {
                if (room.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(room.avatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (room.type == RoomType.GROUP) Icons.Default.Group
                        else Icons.Default.Person,
                        null, tint = Color.White.copy(0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name + subtitle
            Column(
                Modifier.weight(1f).clickable {
                    if (room.type == RoomType.GROUP) onShowInfo()
                    else {
                        val otherId = room.members
                            .firstOrNull { it != state.room?.members?.firstOrNull() }
                            ?: room.members.firstOrNull() ?: return@clickable
                        onViewProfile(otherId)
                    }
                }
            ) {
                Text(
                    room.name,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    if (room.type == RoomType.GROUP) "${room.members.size} members"
                    else "Tap to view profile",
                    fontSize = 12.sp,
                    color    = Color.White.copy(0.6f)
                )
            }

            // Info button (groups only)
            if (room.type == RoomType.GROUP)
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Default.Info, "Group Info", tint = Color.White.copy(0.7f))
                }
        }
    }
}

// ── Glass message bubble ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassMessageBubble(
    msg          : ChatMessage,
    isGroup      : Boolean,
    onReply      : () -> Unit,
    onEdit       : () -> Unit,
    onDelete     : () -> Unit,
    onAvatarClick: () -> Unit,
    onImageClick : (String) -> Unit,
    onFileOpen   : (String, String, String) -> Unit
) {
    var showMenu    by remember { mutableStateOf(false) }
    val editAllowed  = msg.isOwn && (System.currentTimeMillis() - msg.sentAt) < 5 * 60_000L
    val enterAnim    = if (msg.isOwn)
        fadeIn() + slideInHorizontally { it / 3 }
    else
        fadeIn() + slideInHorizontally { -it / 3 }

    AnimatedVisibility(visible = true, enter = enterAnim) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = if (msg.isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment     = Alignment.Bottom
        ) {
            // Receiver avatar
            if (!msg.isOwn) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(GlassWhite18)
                        .border(1.dp, GlassBorder, CircleShape)
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
                        Text(
                            msg.senderName.take(1).uppercase(),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = AccentBlue
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            Column(
                horizontalAlignment = if (msg.isOwn) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                // Sender name (group, received)
                if (!msg.isOwn && isGroup && msg.senderName.isNotBlank()) {
                    Text(
                        msg.senderName,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = AccentTeal,
                        modifier   = Modifier.padding(start = 6.dp, bottom = 2.dp)
                    )
                }

                Box {
                    // Bubble
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart    = 20.dp, topEnd = 20.dp,
                                    bottomStart = if (msg.isOwn) 20.dp else 5.dp,
                                    bottomEnd   = if (msg.isOwn) 5.dp  else 20.dp
                                )
                            )
                            .then(
                                if (msg.isOwn)
                                    Modifier.background(sentBubbleBrush)
                                else
                                    Modifier
                                        .background(GlassWhite18)
                                        .border(
                                            1.dp, GlassBorder,
                                            RoundedCornerShape(
                                                topStart    = 20.dp, topEnd = 20.dp,
                                                bottomStart = 5.dp, bottomEnd = 20.dp
                                            )
                                        )
                            )
                            .combinedClickable(
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
                        Column(
                            Modifier.padding(
                                start  = if (msg.type == MessageType.IMAGE) 0.dp else 12.dp,
                                end    = if (msg.type == MessageType.IMAGE) 0.dp else 12.dp,
                                top    = if (msg.type == MessageType.IMAGE) 0.dp else 9.dp,
                                bottom = 7.dp
                            )
                        ) {
                            // Reply quote
                            if (msg.replyToText.isNotBlank()) {
                                Box(
                                    Modifier
                                        .padding(
                                            start  = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                            end    = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                            top    = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                            bottom = 6.dp
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(0.15f))
                                        .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                        Box(
                                            Modifier.width(3.dp).fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (msg.isOwn) Color.White else AccentTeal)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            msg.replyToText.take(80),
                                            fontSize = 11.sp,
                                            color    = Color.White.copy(0.8f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Content
                            when (msg.type) {
                                MessageType.TEXT ->
                                    Text(
                                        msg.text,
                                        color    = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )

                                MessageType.IMAGE -> {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(msg.fileUrl).crossfade(true).build(),
                                        contentDescription = "Image",
                                        modifier = Modifier
                                            .widthIn(max = 260.dp)
                                            .heightIn(min = 80.dp, max = 260.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart    = 20.dp, topEnd = 20.dp,
                                                    bottomStart = if (msg.isOwn) 20.dp else 5.dp,
                                                    bottomEnd   = if (msg.isOwn) 5.dp  else 20.dp
                                                )
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (msg.text.isNotBlank() && msg.text != msg.fileName) {
                                        Text(
                                            msg.text, color = Color.White, fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                MessageType.VIDEO -> {
                                    Box(
                                        Modifier.widthIn(max = 260.dp).height(160.dp)
                                            .clip(RoundedCornerShape(14.dp))
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
                                        // Glass play button
                                        Box(
                                            Modifier.size(56.dp).clip(CircleShape)
                                                .background(Color.Black.copy(0.45f))
                                                .border(1.5.dp, GlassBorder, CircleShape),
                                            Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null,
                                                tint = Color.White, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                    if (msg.fileName.isNotBlank())
                                        Text(msg.fileName, fontSize = 11.sp,
                                            color = Color.White.copy(0.75f),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                MessageType.AUDIO -> {
                                    Row(
                                        Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            Modifier.size(40.dp).clip(CircleShape)
                                                .background(Color.White.copy(0.15f))
                                                .border(1.dp, GlassBorder, CircleShape),
                                            Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Mic, null,
                                                tint = if (msg.isOwn) Color.White else AccentTeal,
                                                modifier = Modifier.size(22.dp))
                                        }
                                        Column {
                                            Text(msg.fileName.ifBlank { "Audio" },
                                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (msg.fileSize > 0)
                                                Text(formatFileSize(msg.fileSize),
                                                    fontSize = 10.sp, color = Color.White.copy(0.6f))
                                        }
                                        Icon(Icons.Default.PlayCircle, null,
                                            tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }

                                MessageType.DOCUMENT -> {
                                    Row(
                                        Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(0.15f))
                                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
                                            Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Description, null,
                                                tint = if (msg.isOwn) Color.White else AccentBlue,
                                                modifier = Modifier.size(26.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(msg.fileName.ifBlank { "Document" },
                                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (msg.fileSize > 0)
                                                Text(formatFileSize(msg.fileSize),
                                                    fontSize = 10.sp, color = Color.White.copy(0.6f))
                                        }
                                        Box(
                                            Modifier.size(32.dp).clip(CircleShape)
                                                .background(Color.White.copy(0.15f)),
                                            Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Download, null,
                                                tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Timestamp + status row
                            Row(
                                Modifier
                                    .padding(
                                        start = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                        end   = if (msg.type == MessageType.IMAGE) 10.dp else 0.dp,
                                        top   = 2.dp
                                    )
                                    .align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (msg.editedAt > 0L)
                                    Text("edited", fontSize = 9.sp, color = Color.White.copy(0.5f))
                                Text(formatMsgTime(msg.sentAt), fontSize = 10.sp,
                                    color = Color.White.copy(0.6f))
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
                                            MessageStatus.READ   -> AccentTeal
                                            MessageStatus.FAILED -> Color(0xFFFF6B6B)
                                            else                 -> Color.White.copy(0.6f)
                                        }
                                    )
                            }
                        }
                    }

                    // Context menu
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
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
                                text        = { Text("Delete", color = Color(0xFFFF6B6B)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B)) },
                                onClick     = { showMenu = false; onDelete() }
                            )
                    }
                }
            }
        }
    }
}

// ── Glass input bar ───────────────────────────────────────────────────────────

@Composable
private fun GlassChatInputBar(
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

    Box(
        Modifier
            .fillMaxWidth()
            .glassBackground(cornerRadius = 0.dp, alpha = 0.18f)
            .drawBehind {
                drawLine(GlassBorder16, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {

            // Editing banner
            AnimatedVisibility(visible = editingMessage != null) {
                editingMessage?.let { em ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFFF57F17).copy(0.15f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null,
                            tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Editing message", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFFF57F17))
                            Text(em.text.take(60), fontSize = 11.sp,
                                color = Color.White.copy(0.7f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onCancelEdit, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null,
                                Modifier.size(16.dp), tint = Color.White.copy(0.7f))
                        }
                    }
                }
            }

            // Reply banner
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(AccentBlue.copy(0.15f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.width(3.dp).height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AccentBlue)
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Replying to ${reply.senderName}", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = AccentBlue)
                            Text(reply.text.take(60), fontSize = 11.sp,
                                color = Color.White.copy(0.7f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onClearReply, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null,
                                Modifier.size(16.dp), tint = Color.White.copy(0.7f))
                        }
                    }
                }
            }

            // File selected banner
            AnimatedVisibility(visible = selectedFile != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(AccentTeal.copy(0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AttachFile, null,
                        tint = AccentTeal, modifier = Modifier.size(18.dp))
                    Text(selectedName.ifBlank { "File selected" },
                        Modifier.weight(1f), fontSize = 12.sp,
                        color = Color.White.copy(0.9f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onClearFile, Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null,
                            Modifier.size(16.dp), tint = Color.White.copy(0.7f))
                    }
                }
            }

            // Attach options
            AnimatedVisibility(
                visible = showAttachMenu,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GlassAttachOption(Icons.Default.Image,       "Image",    AccentBlue,   onPickImage)
                    GlassAttachOption(Icons.Default.CameraAlt,   "Camera",   AccentTeal,   onPickCamera)
                    GlassAttachOption(Icons.Default.Description, "Document", AccentPurple, onPickFile)
                }
            }

            // Text field row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Attach toggle button
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(GlassWhite18)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onToggleAttach() },
                    Alignment.Center
                ) {
                    Icon(
                        if (showAttachMenu) Icons.Default.Close else Icons.Default.Add,
                        null,
                        tint     = if (showAttachMenu) Color(0xFFFF6B6B) else AccentBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Text field
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(GlassWhite12)
                        .border(1.dp, GlassBorder16, RoundedCornerShape(26.dp))
                ) {
                    BasicGlassTextField(
                        value         = text,
                        onValueChange = onTextChange,
                        placeholder   = if (editingMessage != null) "Edit message…" else "Message…",
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                // Send / progress
                Box(Modifier.size(44.dp), Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            color       = AccentBlue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(
                                    if (canSend) Brush.linearGradient(
                                        listOf(AccentBlue, AccentPurple))
                                    else Brush.linearGradient(
                                        listOf(GlassWhite18, GlassWhite18))
                                )
                                .clickable(enabled = canSend, onClick = onSend),
                            Alignment.Center
                        ) {
                            Icon(
                                if (editingMessage != null) Icons.Default.Check
                                else Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint     = if (canSend) Color.White else Color.White.copy(0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Basic glass text field (no Material border) ───────────────────────────────

@Composable
private fun BasicGlassTextField(
    value        : String,
    onValueChange: (String) -> Unit,
    placeholder  : String,
    modifier     : Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        textStyle     = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines        = 4,
        decorationBox  = { inner ->
            Box(modifier) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = Color.White.copy(0.4f))
                }
                inner()
            }
        }
    )
}

// ── Glass attach option ───────────────────────────────────────────────────────

@Composable
private fun GlassAttachOption(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    color  : Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(color.copy(0.15f))
                .border(1.dp, color.copy(0.35f), CircleShape),
            Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Text(label, fontSize = 11.sp, color = Color.White.copy(0.7f))
    }
}

// ── Glass icon button ─────────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick : () -> Unit
) {
    Box(
        modifier.size(42.dp).clip(CircleShape)
            .background(GlassWhite18)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable { onClick() },
        Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// ── Date separator ────────────────────────────────────────────────────────────

@Composable
private fun GlassDateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(GlassWhite12)
                .border(1.dp, GlassBorder16, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(0.6f))
        }
    }
}

// ── Glass group info sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassGroupInfoSheet(
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
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgDark2,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier.width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GlassWhite22)
                        .align(Alignment.Center)
                )
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            // Header
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(
                            AccentBlue.copy(0.2f), AccentPurple.copy(0.1f), Color.Transparent
                        ))
                    )
                    .padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Box {
                        Box(
                            Modifier.size(90.dp).clip(CircleShape)
                                .background(GlassWhite18)
                                .border(2.dp,
                                    Brush.linearGradient(listOf(AccentBlue, AccentPurple)),
                                    CircleShape)
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
                                    tint = AccentBlue, modifier = Modifier.size(44.dp))
                            }
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(AccentBlue, AccentPurple)))
                                .align(Alignment.BottomEnd),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (editMode) {
                        Box(
                            Modifier.fillMaxWidth(0.8f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassWhite18)
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicGlassTextField(
                                    value         = editName,
                                    onValueChange = { editName = it },
                                    placeholder   = "Group name",
                                    modifier      = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(onClick = {
                                        val bytes = avatarUri?.let { uri ->
                                            context.contentResolver.openInputStream(uri)?.readBytes()
                                        }
                                        viewModel.updateGroupInfo(editName, bytes)
                                        avatarUri = null; editMode = false
                                    }) {
                                        Icon(Icons.Default.Check, null, tint = AccentTeal)
                                    }
                                    IconButton(onClick = { editName = room.name; editMode = false }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.6f))
                                    }
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(room.name,
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White)
                            IconButton(onClick = { editMode = true }) {
                                Icon(Icons.Default.Edit, null,
                                    tint = AccentBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text("${room.members.size} members",
                        fontSize = 13.sp, color = Color.White.copy(0.5f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add member row
            Row(
                Modifier.fillMaxWidth()
                    .clickable { /* launch member picker */ }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(AccentBlue.copy(0.15f))
                        .border(1.dp, AccentBlue.copy(0.35f), CircleShape),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, null, tint = AccentBlue)
                }
                Text("Add member", fontWeight = FontWeight.Medium, color = Color.White)
            }

            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                .background(GlassWhite12))

            Text(
                "${room.members.size} members",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = AccentBlue,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            room.members.forEachIndexed { idx, userId ->
                val name      = room.memberNames.getOrElse(idx) { "Member" }
                val avatarUrl = room.memberAvatars.getOrElse(idx) { "" }
                val isAdmin   = userId in room.adminIds

                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onViewProfile(userId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(GlassWhite18)
                            .border(1.dp,
                                if (isAdmin) Brush.linearGradient(listOf(AccentBlue, AccentPurple))
                                else Brush.linearGradient(listOf(GlassBorder, GlassBorder)),
                                CircleShape),
                        Alignment.Center
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarUrl).crossfade(true).build(),
                                contentDescription = null,
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold, color = AccentBlue)
                        }
                    }

                    // Name + admin badge
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(name, fontWeight = FontWeight.Medium, color = Color.White)
                            if (isAdmin)
                                Box(
                                    Modifier.clip(RoundedCornerShape(5.dp))
                                        .background(AccentBlue.copy(0.2f))
                                        .border(1.dp, AccentBlue.copy(0.4f), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("admin", fontSize = 9.sp,
                                        color = AccentBlue, fontWeight = FontWeight.Bold)
                                }
                        }
                    }

                    // Remove button (non-admins only)
                    if (!isAdmin)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(Color(0xFFFF6B6B).copy(0.12f))
                                .border(1.dp, Color(0xFFFF6B6B).copy(0.3f), CircleShape)
                                .clickable {
                                    viewModel.removeGroupMember(
                                        userId        = userId,
                                        memberNames   = room.memberNames,
                                        memberAvatars = room.memberAvatars
                                    )
                                },
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonRemove, null,
                                tint     = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp))
                        }
                }
            }
        }
    }
}

// ── File conflict dialog ──────────────────────────────────────────────────────

@Composable
fun GlassFileConflictDialog(
    conflict : FileConflict,
    onRename : () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(listOf(BgDark1, BgDark2))
                )
                .border(1.dp,
                    Brush.linearGradient(listOf(AccentBlue.copy(0.4f), AccentPurple.copy(0.4f))),
                    RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Icon + title
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(AccentBlue.copy(0.15f))
                            .border(1.dp, AccentBlue.copy(0.35f), CircleShape),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.FileCopy, null, tint = AccentBlue)
                    }
                    Text("File already exists",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }

                // Description
                Text(
                    "\"${conflict.originalName}\" was already sent in this chat.",
                    fontSize = 14.sp, color = Color.White.copy(0.7f)
                )

                // Suggested rename box
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentTeal.copy(0.08f))
                        .border(1.dp, AccentTeal.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, null,
                            tint = AccentTeal, modifier = Modifier.size(16.dp))
                        Text("Rename to: ${conflict.suggestedName}",
                            fontSize = 12.sp, color = Color.White.copy(0.7f))
                    }
                }

                // Buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(GlassWhite12)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        Alignment.Center
                    ) {
                        Text("Cancel", color = Color.White.copy(0.7f), fontWeight = FontWeight.Medium)
                    }

                    // Replace
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFF6B6B).copy(0.15f))
                            .border(1.dp, Color(0xFFFF6B6B).copy(0.4f), RoundedCornerShape(14.dp))
                            .clickable { onReplace() }
                            .padding(vertical = 12.dp),
                        Alignment.Center
                    ) {
                        Text("Replace", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
                    }

                    // Rename
                    Box(
                        Modifier.weight(1.3f).clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(listOf(AccentBlue, AccentPurple)))
                            .clickable { onRename() }
                            .padding(vertical = 12.dp),
                        Alignment.Center
                    ) {
                        Text("Rename", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Upload progress dialog ────────────────────────────────────────────────────

@Composable
fun GlassUploadProgressDialog(progress: UploadProgress) {
    Dialog(onDismissRequest = { }) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(BgDark1, BgDark2)))
                .border(1.dp,
                    Brush.linearGradient(listOf(AccentBlue.copy(0.35f), AccentTeal.copy(0.35f))),
                    RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Animated cloud icon
                val inf  = rememberInfiniteTransition(label = "upload")
                val anim by inf.animateFloat(0.7f, 1f,
                    infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(AccentBlue.copy(0.15f * anim))
                        .border(1.dp, AccentBlue.copy(0.4f), CircleShape),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.CloudUpload, null,
                        tint     = AccentBlue,
                        modifier = Modifier.size(28.dp))
                }

                Text("Uploading file…",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)

                Text(progress.fileName,
                    style   = MaterialTheme.typography.bodySmall,
                    color   = Color.White.copy(0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                // Glass progress bar
                Box(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GlassWhite12)
                        .border(1.dp, GlassBorder16, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress.percent / 100f).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.linearGradient(listOf(AccentBlue, AccentTeal)))
                    )
                }

                Text(
                    "${progress.percent}%  ·  " +
                            "${progress.bytesUploaded / 1024 / 1024} MB / " +
                            "${progress.totalBytes / 1024 / 1024} MB",
                    fontSize = 12.sp,
                    color    = Color.White.copy(0.55f)
                )
            }
        }
    }
}

// ── Glass background modifier ─────────────────────────────────────────────────

private fun Modifier.glassBackground(
    cornerRadius: Dp    = 20.dp,
    alpha       : Float = 0.18f,
    borderAlpha : Float = 0.25f
): Modifier = this
    .background(Color.White.copy(alpha = alpha))
    .border(
        1.dp,
        Color.White.copy(alpha = borderAlpha),
        if (cornerRadius == 0.dp) RectangleShape
        else RoundedCornerShape(cornerRadius)
    )

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
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
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