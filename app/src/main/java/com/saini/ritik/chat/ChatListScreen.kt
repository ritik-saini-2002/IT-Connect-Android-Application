package com.saini.ritik.chat

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel     : ChatListViewModel,
    onOpenRoom    : (ChatRoom) -> Unit,
    onNewGroup    : () -> Unit,
    onNewDM       : () -> Unit,
    onBack        : () -> Unit
) {
    val state    by viewModel.state.collectAsState()
    var search   by remember { mutableStateOf("") }
    var fabExpand by remember { mutableStateOf(false) }

    val filtered = remember(state.rooms, search) {
        val rooms = if (search.isBlank()) state.rooms
        else state.rooms.filter {
            it.name.contains(search, true) ||
                    it.lastMessage.contains(search, true) ||
                    it.memberNames.any { n -> n.contains(search, true) }
        }
        // Pin broadcast rooms at the top
        val broadcast = rooms.filter { it.type == RoomType.BROADCAST }
        val rest      = rooms.filter { it.type != RoomType.BROADCAST }
        broadcast + rest
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(0.8f)
                    )))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Messages", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${state.rooms.size} conversations",
                            fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }
        },
        floatingActionButton = {
            // FIX: Reduced FAB padding for minimal gravity position
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 8.dp)  // minimal gravity
            ) {
                AnimatedVisibility(visible = fabExpand,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()) {
                    Column(horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FabOption("New Group", Icons.Default.Group,
                            Color(0xFF1976D2)) { fabExpand = false; onNewGroup() }
                        FabOption("New Chat", Icons.Default.PersonAdd,
                            Color(0xFF388E3C)) { fabExpand = false; onNewDM() }
                    }
                }
                SmallFloatingActionButton(
                    onClick           = { fabExpand = !fabExpand },
                    containerColor    = MaterialTheme.colorScheme.primary,
                    modifier          = Modifier.size(48.dp)
                ) {
                    Icon(if (fabExpand) Icons.Default.Close else Icons.Default.Edit,
                        null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Search
            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Search conversations…") },
                leadingIcon   = { Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.primary) },
                trailingIcon  = {
                    if (search.isNotEmpty())
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier   = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                singleLine = true,
                shape      = RoundedCornerShape(14.dp)
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Forum, null, Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text(if (search.isBlank()) "No conversations yet"
                        else "No results for \"$search\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (search.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Tap + to start a chat or create a group",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                        }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { it.id }) { room ->
                        RoomListItem(
                            room     = room,
                            myUserId = state.currentUserId,
                            onClick  = { onOpenRoom(room) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RoomListItem(room: ChatRoom, myUserId: String, onClick: () -> Unit) {
    // FIX: For DMs, show the OTHER user's name (not "UserA & UserB")
    // For groups/broadcast, show room name
    val displayName = when (room.type) {
        RoomType.DIRECT -> {
            // Find the other member's name
            val myIdx   = room.members.indexOf(myUserId)
            val otherIdx = if (myIdx == 0) 1 else 0
            room.memberNames.getOrElse(otherIdx) {
                room.name.replace("&", "·").trim()
            }
        }
        RoomType.GROUP, RoomType.BROADCAST -> room.name
    }

    // FIX: For DMs, show the other user's avatar; for groups, show group avatar or icon
    val otherAvatarUrl = when (room.type) {
        RoomType.DIRECT -> {
            val myIdx   = room.members.indexOf(myUserId)
            val otherIdx = if (myIdx == 0) 1 else 0
            room.memberAvatars.getOrElse(otherIdx) { room.avatarUrl }
        }
        RoomType.GROUP, RoomType.BROADCAST -> room.avatarUrl
    }

    // Subtitle: for groups/broadcast show member info
    val subtitle = when (room.type) {
        RoomType.BROADCAST -> "Company channel \u00B7 visible to all"
        RoomType.GROUP     -> "${room.members.size} members"
        RoomType.DIRECT    -> ""
    }

    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar
        Box(Modifier.size(54.dp)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(
                        when (room.type) {
                            RoomType.BROADCAST -> Color(0xFFF57C00).copy(0.15f)
                            RoomType.GROUP     -> Color(0xFF1976D2).copy(0.15f)
                            else               -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                Alignment.Center
            ) {
                if (otherAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(otherAvatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // FIX: Show initials of the OTHER user for DMs, group/broadcast icon for others
                    if (room.type == RoomType.BROADCAST) {
                        Icon(Icons.Default.Notifications, null,
                            tint = Color(0xFFF57C00), modifier = Modifier.size(28.dp))
                    } else if (room.type == RoomType.GROUP) {
                        Icon(Icons.Default.Group, null,
                            tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                    } else {
                        val initials = displayName
                            .split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                        Text(
                            initials.ifBlank { "?" },
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            // Unread badge
            if (room.unreadCount > 0) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .align(Alignment.TopEnd),
                    Alignment.Center
                ) {
                    Text(
                        if (room.unreadCount > 9) "9+" else room.unreadCount.toString(),
                        fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween,
                Alignment.CenterVertically) {
                Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (room.lastMessageAt > 0)
                    Text(formatRoomTime(room.lastMessageAt),
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Subtitle (member count for groups)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(0.7f))
            }
            Spacer(Modifier.height(2.dp))
            Text(room.lastMessage.ifBlank { "No messages yet" },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FabOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = color) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatRoomTime(ts: Long): String {
    val now  = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000         -> "now"
        diff < 3_600_000      -> "${diff / 60_000}m"
        diff < 86_400_000     -> "${diff / 3_600_000}h"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000}d"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
    }
}