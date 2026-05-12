package com.saini.ritik.nagios

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// ─── Notification Channel IDs ─────────────────────────────────────────────────

object Channels {
    const val CRITICAL = "nagios_critical"
    const val WARNING  = "nagios_warning"
    const val INFO     = "nagios_info"
}

// ─── Worker input data keys ───────────────────────────────────────────────────

object WorkerKeys {
    const val BASE_URL  = "base_url"
    const val USERNAME  = "username"
    const val PASSWORD  = "password"
    const val WORKER_TAG = "nagios_poll_worker"
}

// ─── NagiosNotifications helper object ───────────────────────────────────────

object NagiosNotifications {

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            // CRITICAL — high importance, sound + vibrate
            val criticalChannel = NotificationChannel(
                Channels.CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description     = "Nagios CRITICAL and host DOWN alerts"
                enableVibration(true)
                enableLights(true)
            }

            // WARNING — default importance
            val warningChannel = NotificationChannel(
                Channels.WARNING,
                "Warning Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nagios WARNING state alerts"
            }

            // INFO — low importance
            val infoChannel = NotificationChannel(
                Channels.INFO,
                "Info",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nagios informational notifications"
            }

            manager.createNotificationChannels(
                listOf(criticalChannel, warningChannel, infoChannel)
            )
        }
    }

    fun scheduleWorker(context: Context, baseUrl: String, username: String, password: String) {
        val inputData = workDataOf(
            WorkerKeys.BASE_URL to baseUrl,
            WorkerKeys.USERNAME to username,
            WorkerKeys.PASSWORD to password
        )

        val workRequest = PeriodicWorkRequestBuilder<NagiosPollWorker>(
            15, TimeUnit.MINUTES   // Android minimum — actual polls in app are every 30s
        )
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WorkerKeys.WORKER_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WorkerKeys.WORKER_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelWorker(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WorkerKeys.WORKER_TAG)
    }

    fun sendCriticalNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "alerts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Channels.CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFEF4444.toInt())   // error red from your theme
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun sendWarningNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "alerts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Channels.WARNING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFF59E0B.toInt())   // warning amber from your theme
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

// ─── Background Worker ────────────────────────────────────────────────────────

class NagiosPollWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val baseUrl  = inputData.getString(WorkerKeys.BASE_URL)  ?: return Result.failure()
        val username = inputData.getString(WorkerKeys.USERNAME)  ?: return Result.failure()
        val password = inputData.getString(WorkerKeys.PASSWORD)  ?: return Result.failure()

        return try {
            val repo     = NagiosRepository(baseUrl, username, password)
            val hosts    = repo.fetchHosts()
            val services = repo.fetchServices()
            val newAlerts = repo.alerts(hosts, services)

            // Load previously known alerts from SharedPreferences
            val prefs       = context.getSharedPreferences("nagios_alerts", Context.MODE_PRIVATE)
            val knownAlerts = prefs.getStringSet("known_alerts", emptySet()) ?: emptySet()

            // Find brand-new problems not seen before
            val newProblems = newAlerts.filter { it !in knownAlerts }

            // Fire notifications for new problems
            newProblems.forEachIndexed { index, alertId ->
                val parts = alertId.split(":")
                when {
                    alertId.startsWith("HOST:") -> {
                        val hostName = parts.getOrElse(1) { "Unknown" }
                        val status   = parts.getOrElse(2) { "DOWN" }
                        val output   = parts.getOrElse(3) { "" }
                        NagiosNotifications.sendCriticalNotification(
                            context         = context,
                            title           = "Host $status: $hostName",
                            body            = output.ifBlank { "Host is $status" },
                            notificationId  = 1000 + index
                        )
                    }
                    alertId.startsWith("SVC:") -> {
                        val hostName    = parts.getOrElse(1) { "Unknown" }
                        val serviceName = parts.getOrElse(2) { "Unknown" }
                        val status      = parts.getOrElse(3) { "CRITICAL" }
                        val output      = parts.getOrElse(4) { "" }
                        val isCritical  = status == "CRITICAL"
                        if (isCritical) {
                            NagiosNotifications.sendCriticalNotification(
                                context        = context,
                                title          = "$serviceName CRITICAL on $hostName",
                                body           = output.ifBlank { "Service is CRITICAL" },
                                notificationId = 2000 + index
                            )
                        } else {
                            NagiosNotifications.sendWarningNotification(
                                context        = context,
                                title          = "$serviceName WARNING on $hostName",
                                body           = output.ifBlank { "Service is WARNING" },
                                notificationId = 3000 + index
                            )
                        }
                    }
                }
            }

            // Save current alert set as the new "known" baseline
            prefs.edit().putStringSet("known_alerts", newAlerts.toSet()).apply()

            Result.success()
        } catch (e: Exception) {
            // Retry up to 3 times on network failure
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
