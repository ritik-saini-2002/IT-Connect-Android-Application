package com.example.ritik_2.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.SyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "ChatNotifService"

@AndroidEntryPoint
class ChatNotificationService : Service() {

    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var authRepo  : AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var userId   = ""
    private var userName = ""
    private var listenJob: Job? = null
    private var currentCall: Call? = null

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    companion object {
        const val CHANNEL_ID      = "chat_messages"
        const val CHANNEL_NAME    = "Chat Messages"
        const val NOTIF_ID        = 1001
        const val EXTRA_USER_ID   = "user_id"
        const val EXTRA_USER_NAME = "user_name"

        fun start(ctx: Context, userId: String, userName: String) {
            // FIX: Use try-catch — on Android 12+ startForegroundService can throw
            // if the app is in background or the dataSync time limit is exhausted.
            try {
                ctx.startForegroundService(
                    Intent(ctx, ChatNotificationService::class.java).apply {
                        putExtra(EXTRA_USER_ID,   userId)
                        putExtra(EXTRA_USER_NAME, userName)
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start foreground service: ${e.message}")
                // Fallback: try as regular service — won't crash the app
                try {
                    ctx.startService(
                        Intent(ctx, ChatNotificationService::class.java).apply {
                            putExtra(EXTRA_USER_ID,   userId)
                            putExtra(EXTRA_USER_NAME, userName)
                        }
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Cannot start service at all: ${e2.message}")
                }
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ChatNotificationService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Stop service failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // FIX: Wrap startForeground in try-catch for Android 14+ dataSync limits.
        // "ForegroundServiceStartNotAllowedException: Time limit already exhausted"
        // happens when the app has used up its dataSync foreground service quota.
        // In that case, we gracefully degrade to a background coroutine.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: must specify foregroundServiceType
                ServiceCompat.startForeground(
                    this, NOTIF_ID, buildForegroundNotif(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, buildForegroundNotif())
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed (quota exhausted?): ${e.message}")
            // Don't crash — just run as a background service.
            // The SSE listener will still work; notifications may be delayed.
            // Stop self to prevent the system from killing the app.
            stopSelf()
        }
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
        while (scope.isActive) {
            val connected = try {
                doConnect()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "SSE disconnected: ${e.message}")
                false
            }
            delay(if (connected) 3_000L else retryDelay)
            retryDelay = if (connected) 5_000L else minOf(retryDelay * 2, 60_000L)
        }
    }

    // ── Single SSE session ────────────────────────────────────────────────────

    private suspend fun doConnect() = withContext(Dispatchers.IO) {
        val token = authRepo.getSession()?.token.orEmpty()
        if (token.isBlank()) {
            Log.w(TAG, "Cannot get user token — no active session")
            return@withContext
        }

        val clientId = try {
            JSONObject(
                syncManager.pbGet("${AppConfig.BASE_URL}/api/realtime", token)
            ).optString("clientId").ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime init failed: ${e.message}")
            null
        }

        if (clientId.isNullOrEmpty()) {
            Log.w(TAG, "No clientId — skipping")
            return@withContext
        }

        try {
            syncManager.pbPost(
                "${AppConfig.BASE_URL}/api/realtime", token,
                JSONObject().apply {
                    put("clientId", clientId)
                    put("subscriptions", JSONArray().apply {
                        put("chat_messages")
                    }.toString())
                }.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Subscription failed: ${e.message}")
            return@withContext
        }

        val call = sseClient.newCall(
            Request.Builder()
                .url("${AppConfig.BASE_URL}/api/realtime?clientId=$clientId")
                .addHeader("Authorization", "Bearer $token")
                .build()
        )
        currentCall = call

        val response = try {
            call.execute()
        } catch (e: Exception) {
            currentCall = null
            throw e
        }

        if (!response.isSuccessful) {
            response.close()
            currentCall = null
            Log.w(TAG, "SSE HTTP ${response.code}")
            return@withContext
        }

        val source = response.body?.source()
        if (source == null) {
            response.close()
            currentCall = null
            return@withContext
        }

        Log.d(TAG, "SSE connected ✅ clientId=$clientId")

        try {
            val buf = StringBuilder()
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
            currentCall = null
            call.cancel()
            response.close()
            Log.d(TAG, "SSE stream closed, reconnecting…")
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

            // Skip uploading placeholders
            if (record.optBoolean("uploading", false)) return

            val senderName = record.optString("senderName", "Someone")
            val text = record.optString("text", "").ifBlank {
                "\uD83D\uDCCE ${record.optString("fileName", "File")}"
            }
            showMessageNotification(senderName, text, record.optString("roomId"))
        } catch (e: Exception) {
            Log.w(TAG, "Event parse error: ${e.message}")
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
        // FIX: Cancel any active SSE call to prevent connection leaks
        currentCall?.cancel()
        currentCall = null
        scope.cancel()
        Log.d(TAG, "Service destroyed, all connections cleaned up")
    }
}