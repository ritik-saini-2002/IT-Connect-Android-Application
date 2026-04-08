package com.example.ritik_2.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ChatNotificationService : Service() {

    @Inject lateinit var syncManager: SyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var userId   = ""
    private var userName = ""
    private var listenJob: Job? = null

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    companion object {
        const val CHANNEL_ID      = "chat_messages"
        const val CHANNEL_NAME    = "Chat Messages"
        const val NOTIF_ID        = 1001
        const val EXTRA_USER_ID   = "user_id"
        const val EXTRA_USER_NAME = "user_name"

        fun start(ctx: Context, userId: String, userName: String) {
            ctx.startForegroundService(
                Intent(ctx, ChatNotificationService::class.java).apply {
                    putExtra(EXTRA_USER_ID,   userId)
                    putExtra(EXTRA_USER_NAME, userName)
                }
            )
        }

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, ChatNotificationService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildForegroundNotif())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId   = intent?.getStringExtra(EXTRA_USER_ID)   ?: return START_NOT_STICKY
        userName = intent.getStringExtra(EXTRA_USER_NAME)  ?: ""
        listenJob?.cancel()
        listenJob = scope.launch { connectLoop() }
        return START_STICKY
    }

    // ── Reconnect loop ────────────────────────────────────────────────────────

    private suspend fun connectLoop() {
        var retryDelay = 5_000L
        // Use isActive from the coroutine scope, not GlobalScope.coroutineContext
        while (scope.isActive) {
            val connected = try {
                doConnect()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatNotifService", "SSE disconnected: ${e.message}")
                false
            }
            delay(if (connected) 3_000L else retryDelay)
            retryDelay = if (connected) 5_000L else minOf(retryDelay * 2, 60_000L)
        }
    }

    // ── Single SSE session ────────────────────────────────────────────────────

    private suspend fun doConnect() = withContext(Dispatchers.IO) {
        val token = syncManager.getAdminToken()

        val clientId = JSONObject(
            syncManager.pbGet("${AppConfig.BASE_URL}/api/realtime", token)
        ).optString("clientId").ifEmpty {
            Log.w("ChatNotifService", "No clientId — skipping")
            return@withContext
        }

        syncManager.pbPost(
            "${AppConfig.BASE_URL}/api/realtime", token,
            JSONObject().apply {
                put("clientId", clientId)
                put("subscriptions", JSONArray().apply {
                    put("chat_messages")
                }.toString())
            }.toString()
        )

        val call = sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/realtime?clientId=$clientId")
                .addHeader("Authorization", "Bearer $token")
                .build()
        )

        val response = call.execute()
        if (!response.isSuccessful) {
            response.close()
            Log.w("ChatNotifService", "SSE HTTP ${response.code}")
            return@withContext
        }

        val source = response.body?.source()
        if (source == null) { response.close(); return@withContext }

        Log.d("ChatNotifService", "SSE connected ✅ clientId=$clientId")

        try {
            val buf = StringBuilder()
            // isActive here refers to the withContext(Dispatchers.IO) coroutine context
            while (isActive) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("data:") ->
                        buf.append(line.removePrefix("data:").trim())
                    line.isEmpty() && buf.isNotEmpty() -> {
                        handleEvent(buf.toString())
                        buf.clear()
                    }
                }
            }
        } finally {
            call.cancel()
            response.close()
            Log.d("ChatNotifService", "SSE stream closed, reconnecting…")
        }
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    private fun handleEvent(data: String) {
        try {
            val json   = JSONObject(data)
            if (json.optString("action") != "create") return
            val record = json.optJSONObject("record") ?: return

            val senderId = record.optString("senderId")
            if (senderId == userId) return

            val senderName = record.optString("senderName", "Someone")
            val text = record.optString("text", "").ifBlank {
                "\uD83D\uDCCE ${record.optString("fileName", "File")}"
            }
            showMessageNotification(senderName, text, record.optString("roomId"))
        } catch (e: Exception) {
            Log.w("ChatNotifService", "Event parse error: ${e.message}")
        }
    }

    private fun showMessageNotification(sender: String, text: String, roomId: String) {
        val tapIntent = PendingIntent.getActivity(
            this, roomId.hashCode(),
            ChatActivity.newIntent(this, roomId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(roomId.hashCode(), notif)
    }

    private fun buildForegroundNotif() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Chat")
            .setContentText("Connected for new messages")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New chat message notifications"
            enableVibration(true)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}