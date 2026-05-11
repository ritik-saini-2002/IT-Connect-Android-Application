// com/example/ritik_2/chat/ChatNotificationWorker.kt
package com.example.ritik_2.chat

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class ChatNotificationWorker(ctx: Context, params: WorkerParameters)
    : Worker(ctx, params) {

    override fun doWork(): Result {
        val intent = Intent(applicationContext, ChatNotificationService::class.java)
        ContextCompat.startForegroundService(applicationContext, intent)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatNotificationWorker>(
                15, TimeUnit.MINUTES  // Minimum interval WorkManager allows
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "chat_notif_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}