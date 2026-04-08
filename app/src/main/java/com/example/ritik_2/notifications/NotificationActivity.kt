package com.example.ritik_2.notifications

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.chat.ChatActivity
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class NotifItem(
    val id        : String,
    val roomId    : String,
    val roomName  : String,
    val sender    : String,
    val text      : String,
    val sentAt    : Long,
    val isOwn     : Boolean
)

@AndroidEntryPoint
class NotificationActivity : ComponentActivity() {

    @Inject lateinit var syncManager   : SyncManager
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userId = authRepository.getSession()?.userId ?: run { finish(); return }

        setContent {
            ITConnectTheme {
                NotificationScreen(
                    userId      = userId,
                    syncManager = syncManager,
                    onBack      = { finish() },
                    onOpenRoom  = { roomId ->
                        startActivity(ChatActivity.newIntent(this, roomId))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationScreen(
    userId     : String,
    syncManager: SyncManager,
    onBack     : () -> Unit,
    onOpenRoom : (String) -> Unit
) {
    var items     by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope     = rememberCoroutineScope()

    // Load recent messages from rooms the user belongs to
    LaunchedEffect(Unit) {
        scope.launch {
            items     = fetchRecentMessages(userId, syncManager)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notifications", fontWeight = FontWeight.Bold)
                        if (!isLoading)
                            Text("${items.filter { !it.isOwn }.size} messages",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isLoading = true
                        scope.launch {
                            items     = fetchRecentMessages(userId, syncManager)
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                items.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsNone, null,
                            Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                        Spacer(Modifier.height(12.dp))
                        Text("No recent messages",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Messages from your chats will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Group by date
                        val grouped = items.groupBy { dateLabel(it.sentAt) }
                        grouped.forEach { (label, msgs) ->
                            item(key = "hdr_$label") {
                                Text(
                                    label,
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(msgs, key = { it.id }) { item ->
                                NotifCard(
                                    item    = item,
                                    onClick = { onOpenRoom(item.roomId) }
                                )
                                HorizontalDivider(
                                    Modifier.padding(start = 72.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(item: NotifItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (!item.isOwn) MaterialTheme.colorScheme.primaryContainer.copy(0.15f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar initials
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(
                    if (!item.isOwn) MaterialTheme.colorScheme.primary.copy(0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            Alignment.Center
        ) {
            Text(
                item.sender.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = if (!item.isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(
                    if (item.isOwn) "You → ${item.roomName}" else item.sender,
                    fontWeight = if (!item.isOwn) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    timeLabel(item.sentAt),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                // Show room name as subtitle for group chats
                if (item.isOwn) item.text
                else "${item.roomName}: ${item.text}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Unread indicator for others' messages
        if (!item.isOwn) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// ── Data fetching ─────────────────────────────────────────────────────────────

private suspend fun fetchRecentMessages(
    userId     : String,
    syncManager: SyncManager
): List<NotifItem> = withContext(Dispatchers.IO) {
    try {
        val token = syncManager.getAdminToken()

        // Fetch rooms for this user
        val roomsRes = syncManager.pbGet(
            "${AppConfig.BASE_URL}/api/collections/chat_rooms/records" +
                    "?filter=(members~'$userId')&sort=-lastMessageAt&perPage=30",
            token
        )
        val roomItems = JSONObject(roomsRes).optJSONArray("items")
            ?: return@withContext emptyList()

        val roomNames = mutableMapOf<String, String>()
        val roomIds   = mutableListOf<String>()
        for (i in 0 until roomItems.length()) {
            val r = roomItems.getJSONObject(i)
            val rid = r.optString("id")
            roomNames[rid] = r.optString("name", "Chat")
            roomIds += rid
        }

        if (roomIds.isEmpty()) return@withContext emptyList()

        // Fetch recent messages across all those rooms (last 50)
        // PocketBase doesn't support OR filters across multiple rooms in one query,
        // so we fetch per-room for the top 5 most recent rooms
        val result = mutableListOf<NotifItem>()
        for (roomId in roomIds.take(10)) {
            val msgsRes = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/chat_messages/records" +
                        "?filter=(roomId='$roomId')&sort=-sentAt&perPage=5",
                token
            )
            val msgItems = JSONObject(msgsRes).optJSONArray("items") ?: continue
            for (j in 0 until msgItems.length()) {
                val m = msgItems.getJSONObject(j)
                val text = m.optString("text", "").ifBlank {
                    "\uD83D\uDCCE ${m.optString("fileName", "File")}"
                }
                result += NotifItem(
                    id       = m.optString("id"),
                    roomId   = roomId,
                    roomName = roomNames[roomId] ?: "Chat",
                    sender   = m.optString("senderName", "Unknown"),
                    text     = text,
                    sentAt   = m.optLong("sentAt", 0L),
                    isOwn    = m.optString("senderId") == userId
                )
            }
        }

        result.sortedByDescending { it.sentAt }
    } catch (e: Exception) {
        emptyList()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun dateLabel(ts: Long): String {
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

private fun timeLabel(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000         -> "now"
        diff < 3_600_000      -> "${diff / 60_000}m ago"
        diff < 86_400_000     -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else                  -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
    }
}