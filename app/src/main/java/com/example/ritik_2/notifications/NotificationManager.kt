//package com.example.ritik_2.notifications
//
//import android.Manifest
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import android.content.pm.PackageManager
//import android.os.Build
//import androidx.core.app.ActivityCompat
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import com.example.ritik_2.R
//
//class NotificationManager private constructor(private val context: Context) {
//
//    companion object {
//        @Volatile
//        private var INSTANCE: NotificationManager? = null
//
//        fun getInstance(context: Context): NotificationManager {
//            return INSTANCE ?: synchronized(this) {
//                (INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE  }) as NotificationManager
//            }
//        }
//
//        // Notification Channels
//        const val COMPLAINT_CHANNEL = "complaint_notifications"
//        const val GLOBAL_COMPLAINT_CHANNEL = "global_complaint_notifications"
//        const val SYSTEM_CHANNEL = "system_notifications"
//        const val USER_UPDATES_CHANNEL = "user_updates_notifications"
//    }
//
//    enum class NotificationType(
//        val channelId: String,
//        val priority: Int,
//        val iconRes: Int
//    ) {
//        COMPLAINT(COMPLAINT_CHANNEL, NotificationCompat.PRIORITY_DEFAULT, R.drawable.ic_baseline_report_24),
//        GLOBAL_COMPLAINT(GLOBAL_COMPLAINT_CHANNEL, NotificationCompat.PRIORITY_HIGH, R.drawable.ic_baseline_campaign_24),
//        SYSTEM(SYSTEM_CHANNEL, NotificationCompat.PRIORITY_HIGH, R.drawable.ic_baseline_settings_24),
//        USER_UPDATE(USER_UPDATES_CHANNEL, NotificationCompat.PRIORITY_DEFAULT, R.drawable.ic_baseline_person_24)
//    }
//
//    init {
//        setupNotificationChannels()
//    }
//
//    private fun setupNotificationChannels() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//            val channels = listOf(
//                NotificationChannel(
//                    COMPLAINT_CHANNEL,
//                    "Complaint Notifications",
//                    NotificationManager.IMPORTANCE_DEFAULT
//                ).apply {
//                    description = "Notifications for complaint submissions and updates"
//                    enableVibration(true)
//                    vibrationPattern = longArrayOf(0, 500, 200, 500)
//                },
//
//                NotificationChannel(
//                    GLOBAL_COMPLAINT_CHANNEL,
//                    "Global Complaint Notifications",
//                    NotificationManager.IMPORTANCE_HIGH
//                ).apply {
//                    description = "High priority notifications for global complaints"
//                    enableVibration(true)
//                    vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
//                },
//
//                NotificationChannel(
//                    SYSTEM_CHANNEL,
//                    "System Notifications",
//                    NotificationManager.IMPORTANCE_HIGH
//                ).apply {
//                    description = "Important system notifications and updates"
//                    enableVibration(true)
//                },
//
//                NotificationChannel(
//                    USER_UPDATES_CHANNEL,
//                    "User Updates",
//                    NotificationManager.IMPORTANCE_DEFAULT
//                ).apply {
//                    description = "Notifications for user account updates and changes"
//                    enableVibration(false)
//                }
//            )
//
//            channels.forEach { channel ->
//                notificationManager.createNotificationChannel(channel)
//            }
//        }
//    }
//
//    /**
//     * Show a notification with the specified type and content
//     */
//    fun showNotification(
//        type: NotificationType,
//        title: String,
//        content: String,
//        expandedContent: String? = null,
//        notificationId: Int = generateNotificationId()
//    ) {
//        if (!hasNotificationPermission()) return
//
//        val builder = NotificationCompat.Builder(context, type.channelId)
//            .setSmallIcon(type.iconRes)
//            .setContentTitle(title)
//            .setContentText(content)
//            .setPriority(type.priority)
//            .setAutoCancel(true)
//
//        // Add expanded content if provided
//        expandedContent?.let { expanded ->
//            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
//        }
//
//        // Add vibration pattern based on type
//        when (type) {
//            NotificationType.GLOBAL_COMPLAINT -> builder.setVibrate(longArrayOf(0, 300, 100, 300, 100, 300))
//            NotificationType.COMPLAINT -> builder.setVibrate(longArrayOf(0, 500, 200, 500))
//            NotificationType.SYSTEM -> builder.setVibrate(longArrayOf(0, 200, 100, 200))
//            NotificationType.USER_UPDATE -> { /* No vibration for user updates */ }
//        }
//
//        with(NotificationManagerCompat.from(context)) {
//            notify(notificationId, builder.build())
//        }
//    }
//
//    /**
//     * Show complaint submission notification
//     */
//    fun showComplaintSubmitted(
//        complaintTitle: String,
//        department: String,
//        urgency: String,
//        isGlobal: Boolean = false
//    ) {
//        val type = if (isGlobal) NotificationType.GLOBAL_COMPLAINT else NotificationType.COMPLAINT
//        val title = if (isGlobal) "Global Complaint Submitted" else "Complaint Submitted"
//        val content = "$complaintTitle - Assigned to: $department"
//        val expanded = "Priority: $urgency\nDepartment: $department\nStatus: Processing"
//
//        showNotification(type, title, content, expanded)
//    }
//
//    /**
//     * Show complaint status update notification
//     */
//    fun showComplaintStatusUpdate(
//        complaintTitle: String,
//        newStatus: String,
//        updatedBy: String
//    ) {
//        val title = "Complaint Status Updated"
//        val content = "$complaintTitle - Status: $newStatus"
//        val expanded = "Your complaint status has been updated to: $newStatus\nUpdated by: $updatedBy"
//
//        showNotification(NotificationType.COMPLAINT, title, content, expanded)
//    }
//
//    /**
//     * Show new global complaint notification (for other users)
//     */
//    fun showNewGlobalComplaint(
//        complaintTitle: String,
//        urgency: String,
//        submittedBy: String,
//        companyName: String
//    ) {
//        val title = "New Global Complaint"
//        val content = "$complaintTitle - Priority: $urgency"
//        val expanded = "A new global complaint has been submitted by $submittedBy in $companyName\n" +
//                "Priority: $urgency\nComplaint: $complaintTitle"
//
//        showNotification(NotificationType.GLOBAL_COMPLAINT, title, content, expanded)
//    }
//
//    /**
//     * Show user account created notification
//     */
//    fun showUserAccountCreated(userName: String, role: String, department: String) {
//        val title = "Account Created Successfully"
//        val content = "Welcome $userName to the system"
//        val expanded = "Your account has been created successfully!\n" +
//                "Role: $role\nDepartment: $department\nYou can now access the system."
//
//        showNotification(NotificationType.USER_UPDATE, title, content, expanded)
//    }
//
//    /**
//     * Show system maintenance notification
//     */
//    fun showSystemMaintenance(message: String, scheduledTime: String? = null) {
//        val title = "System Maintenance"
//        val content = message
//        val expanded = if (scheduledTime != null) {
//            "$message\nScheduled for: $scheduledTime"
//        } else message
//
//        showNotification(NotificationType.SYSTEM, title, content, expanded)
//    }
//
//    /**
//     * Check if the app has notification permission
//     */
//    private fun hasNotificationPermission(): Boolean {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.POST_NOTIFICATIONS
//            ) == PackageManager.PERMISSION_GRANTED
//        } else {
//            true // Permissions not required for older versions
//        }
//    }
//
//    /**
//     * Generate a unique notification ID
//     */
//    private fun generateNotificationId(): Int {
//        return System.currentTimeMillis().toInt()
//    }
//
//    /**
//     * Cancel a specific notification
//     */
//    fun cancelNotification(notificationId: Int) {
//        NotificationManagerCompat.from(context).cancel(notificationId)
//    }
//
//    /**
//     * Cancel all notifications
//     */
//    fun cancelAllNotifications() {
//        NotificationManagerCompat.from(context).cancelAll()
//    }
//}