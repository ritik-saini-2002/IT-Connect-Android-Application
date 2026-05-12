// com/example/ritik_2/chat/ChatNotificationService.kt
package com.saini.ritik.chat

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saini.ritik.R
import com.saini.ritik.core.AppConfig
import com.saini.ritik.notifications.NotificationActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ChatNotificationService : Service() {

    @Inject lateinit var authRepository: com.saini.ritik.auth.AuthRepository
    @Inject lateinit var syncManager: com.saini.ritik.core.SyncManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sseCall: Call? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for SSE stream
        .build()

    companion object {
        const val CHANNEL_ID   = "chat_notifications"
        const val CHANNEL_NAME = "Chat Messages"
        const val FOREGROUND_ID = 1001

        fun start(context: android.content.Context, userId: String, name: String) {
            val intent = Intent(context, ChatNotificationService::class.java).apply {
                putExtra("userId", userId)
                putExtra("name", name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ChatNotificationService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        startRealtimeListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY restarts the service if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sseCall?.cancel()
        scope.cancel()
        // Reschedule via WorkManager as a safety net
        ChatNotificationWorker.schedule(this)
    }

    // ── PocketBase Realtime SSE ───────────────────────────────────────────────

    private fun startRealtimeListener() {
        scope.launch {
            try {
                val userId = authRepository.getSession()?.userId ?: return@launch
                val token  = syncManager.getAdminToken()

                // Step 1: Get SSE client ID
                val connectUrl = "${AppConfig.BASE_URL}/api/realtime"
                val connectReq = Request.Builder()
                    .url(connectUrl)
                    .addHeader("Authorization", token)
                    .build()

                sseCall = client.newCall(connectReq)
                sseCall!!.execute().use { response ->
                    val source = response.body?.source() ?: return@launch
                    var clientId = ""

                    // Read the first event to get clientId
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val data = JSONObject(line.removePrefix("data:").trim())
                            if (data.has("clientId")) {
                                clientId = data.getString("clientId")
                                // Step 2: Subscribe to chat_messages collection
                                subscribeToMessages(clientId, token, userId)
                            } else {
                                // Step 3: Handle incoming message events
                                handleEvent(data, userId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Reconnect after 5 seconds on failure
                delay(5_000)
                startRealtimeListener()
            }
        }
    }

    private suspend fun subscribeToMessages(
        clientId: String,
        token: String,
        userId: String
    ) {
        // Subscribe to all chat_messages events for rooms this user is in
        syncManager.pbPost(
            "${AppConfig.BASE_URL}/api/realtime",
            token,
            JSONObject().apply {
                put("clientId", clientId)
                put("subscriptions", org.json.JSONArray().apply {
                    put("chat_messages")   // Listen to ALL new messages
                })
            }.toString()
        )
    }

    private fun handleEvent(data: JSONObject, userId: String) {
        val action = data.optString("action")
        if (action != "create") return // Only care about new messages

        val record   = data.optJSONObject("record") ?: return
        val senderId = record.optString("senderId")

        // Don't notify for own messages
        if (senderId == userId) return

        val senderName = record.optString("senderName", "Someone")
        val roomName   = record.optString("roomName", "Chat")
        val text       = record.optString("text", "📎 File")
        val roomId     = record.optString("roomId")

        showMessageNotification(senderName, roomName, text, roomId)
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun showMessageNotification(
        sender: String,
        room: String,
        text: String,
        roomId: String
    ) {
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = PendingIntent.getActivity(
            this,
            roomId.hashCode(),
            ChatActivity.newIntent(this, roomId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.it_connect_logo)
            .setContentTitle("$sender in $room")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // sound + vibration
            .build()

        // Use roomId hash so each room gets its own notification slot
        notifManager.notify(roomId.hashCode(), notif)
    }

    private fun buildForegroundNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, NotificationActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ITConnect")
            .setContentText("Listening for new messages…")
            .setSmallIcon(R.drawable.it_connect_logo)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description      = "Chat message notifications"
            enableVibration(true)
            enableLights(true)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}