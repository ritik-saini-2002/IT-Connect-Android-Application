package com.example.ritik_2.complaint.viewcomplaint.utils

import android.Manifest
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ritik_2.R
import com.example.ritik_2.complaint.viewcomplaint.data.UserRepository
import com.example.ritik_2.complaint.viewcomplaint.data.models.NotificationData
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationManager(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "NotificationManager"
        private const val CHANNEL_ID = "complaint_notifications"
        private const val ASSIGNMENT_CHANNEL_ID = "assignment_notifications"
        private const val MANAGEMENT_CHANNEL_ID = "management_notifications"
        private const val GLOBAL_CHANNEL_ID = "global_notifications"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager

            // Regular complaint notifications
            val regularChannel = NotificationChannel(
                CHANNEL_ID,
                "Complaint Updates",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for complaint status updates"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // Assignment notifications
            val assignmentChannel = NotificationChannel(
                ASSIGNMENT_CHANNEL_ID,
                "Complaint Assignments",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when complaints are assigned to you"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
            }

            // Management notifications
            val managementChannel = NotificationChannel(
                MANAGEMENT_CHANNEL_ID,
                "Management Updates",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for managers about team activities"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }

            // Global notifications
            val globalChannel = NotificationChannel(
                GLOBAL_CHANNEL_ID,
                "Global Complaints",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for company-wide complaints"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 100, 400, 100, 400)
            }

            notificationManager.createNotificationChannel(regularChannel)
            notificationManager.createNotificationChannel(assignmentChannel)
            notificationManager.createNotificationChannel(managementChannel)
            notificationManager.createNotificationChannel(globalChannel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun showNotification(
        channelId: String,
        title: String,
        content: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        icon: Int = android.R.drawable.ic_dialog_info
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted")
            return
        }

        try {
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(priority)
                .setAutoCancel(true)
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), notification)
            }

            Log.d(TAG, "Notification sent: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    fun sendAssignmentNotification(
        assigneeId: String,
        complaintTitle: String,
        assignedBy: String
    ) {
        scope.launch {
            try {
                val assigneeData = userRepository.getUserData(assigneeId)
                if (assigneeData == null) {
                    Log.w(TAG, "Could not find assignee data for notification")
                    return@launch
                }

                val title = "New Complaint Assigned"
                val content = "'$complaintTitle' has been assigned to you by $assignedBy"

                showNotification(
                    channelId = ASSIGNMENT_CHANNEL_ID,
                    title = title,
                    content = content,
                    priority = NotificationCompat.PRIORITY_HIGH,
                    icon = android.R.drawable.ic_dialog_alert
                )

                // Store notification in database for persistence
                storeNotificationInDatabase(
                    userId = assigneeId,
                    notificationData = NotificationData(
                        title = title,
                        message = content,
                        type = "assignment",
                        priority = "high"
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error sending assignment notification", e)
            }
        }
    }

    fun notifyManagement(
        complaintTitle: String,
        action: String,
        currentUser: UserData
    ) {
        scope.launch {
            try {
                // Find managers and team leaders in the same department and company
                val managementUsers = userRepository.getUsersByRole(
                    companyName = currentUser.sanitizedCompanyName,
                    roles = listOf("Manager", "Team Leader", "TeamLeader", "Administrator")
                ).filter { user ->
                    user.sanitizedDepartment == currentUser.sanitizedDepartment ||
                            user.role.lowercase().contains("admin")
                }

                managementUsers.forEach { manager ->
                    if (manager.userId != currentUser.userId) {
                        val title = "Complaint $action"
                        val content = "'$complaintTitle' has been $action by ${currentUser.name}"

                        showNotification(
                            channelId = MANAGEMENT_CHANNEL_ID,
                            title = title,
                            content = content,
                            priority = NotificationCompat.PRIORITY_DEFAULT,
                            icon = android.R.drawable.ic_dialog_info
                        )

                        // Store notification in database
                        storeNotificationInDatabase(
                            userId = manager.userId,
                            notificationData = NotificationData(
                                title = title,
                                message = content,
                                type = "management",
                                priority = "normal"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error notifying management", e)
            }
        }
    }

    fun sendComplaintStatusNotification(
        userId: String,
        complaintTitle: String,
        oldStatus: String,
        newStatus: String,
        changedBy: String
    ) {
        scope.launch {
            try {
                val title = "Complaint Status Updated"
                val content = "'$complaintTitle' status changed from $oldStatus to $newStatus by $changedBy"

                showNotification(
                    channelId = CHANNEL_ID,
                    title = title,
                    content = content,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    icon = android.R.drawable.ic_dialog_info
                )

                // Store notification in database
                storeNotificationInDatabase(
                    userId = userId,
                    notificationData = NotificationData(
                        title = title,
                        message = content,
                        type = "status_update",
                        priority = "normal"
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error sending status notification", e)
            }
        }
    }

    fun sendComplaintClosedNotification(
        userId: String,
        complaintTitle: String,
        resolution: String,
        closedBy: String
    ) {
        scope.launch {
            try {
                val title = "Complaint Resolved"
                val content = "'$complaintTitle' has been resolved by $closedBy. Resolution: $resolution"

                showNotification(
                    channelId = CHANNEL_ID,
                    title = title,
                    content = content,
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    icon = android.R.drawable.ic_dialog_info
                )

                // Store notification in database
                storeNotificationInDatabase(
                    userId = userId,
                    notificationData = NotificationData(
                        title = title,
                        message = content,
                        type = "complaint_closed",
                        priority = "normal"
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error sending closure notification", e)
            }
        }
    }

    fun sendGlobalComplaintNotification(
        companyName: String,
        complaintTitle: String,
        urgency: String,
        createdBy: String
    ) {
        scope.launch {
            try {
                // Get all users in the company
                val companyUsers = firestore.collection("user_access_control")
                    .whereEqualTo("sanitizedCompanyName", companyName)
                    .get()
                    .await()

                val title = "New Global Complaint"
                val content = "Global complaint: $complaintTitle - Priority: $urgency - By: $createdBy"

                // Send notification to all company users
                companyUsers.documents.forEach { userDoc ->
                    val userId = userDoc.id

                    showNotification(
                        channelId = GLOBAL_CHANNEL_ID,
                        title = title,
                        content = content,
                        priority = NotificationCompat.PRIORITY_HIGH,
                        icon = android.R.drawable.ic_dialog_alert
                    )

                    // Store notification in database
                    storeNotificationInDatabase(
                        userId = userId,
                        notificationData = NotificationData(
                            title = title,
                            message = content,
                            type = "global_complaint",
                            priority = "high"
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending global complaint notification", e)
            }
        }
    }

    fun sendDepartmentComplaintNotification(
        companyName: String,
        departmentName: String,
        complaintTitle: String,
        urgency: String,
        createdBy: String
    ) {
        scope.launch {
            try {
                // Get all users in the department
                val departmentUsers = userRepository.getDepartmentUsers(companyName, departmentName)

                val title = "New Department Complaint"
                val content = "$complaintTitle - Priority: $urgency - Assigned to: $departmentName - By: $createdBy"

                // Send notification to department users
                departmentUsers.forEach { user ->
                    showNotification(
                        channelId = CHANNEL_ID,
                        title = title,
                        content = content,
                        priority = NotificationCompat.PRIORITY_DEFAULT,
                        icon = android.R.drawable.ic_dialog_info
                    )

                    // Store notification in database
                    storeNotificationInDatabase(
                        userId = user.userId,
                        notificationData = NotificationData(
                            title = title,
                            message = content,
                            type = "department_complaint",
                            priority = "normal"
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending department complaint notification", e)
            }
        }
    }

    fun sendOverdueComplaintNotification(
        userId: String,
        complaintTitle: String,
        daysPastDue: Int
    ) {
        scope.launch {
            try {
                val title = "Overdue Complaint"
                val content = "'$complaintTitle' is $daysPastDue days overdue. Please take action."

                showNotification(
                    channelId = ASSIGNMENT_CHANNEL_ID,
                    title = title,
                    content = content,
                    priority = NotificationCompat.PRIORITY_HIGH,
                    icon = android.R.drawable.ic_dialog_alert
                )

                // Store notification in database
                storeNotificationInDatabase(
                    userId = userId,
                    notificationData = NotificationData(
                        title = title,
                        message = content,
                        type = "overdue_complaint",
                        priority = "high"
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error sending overdue notification", e)
            }
        }
    }

    fun sendBulkNotification(
        userIds: List<String>,
        title: String,
        message: String,
        type: String = "general",
        priority: String = "normal"
    ) {
        scope.launch {
            try {
                val channelId = when (type) {
                    "assignment" -> ASSIGNMENT_CHANNEL_ID
                    "management" -> MANAGEMENT_CHANNEL_ID
                    "global" -> GLOBAL_CHANNEL_ID
                    else -> CHANNEL_ID
                }

                val notificationPriority = when (priority) {
                    "high" -> NotificationCompat.PRIORITY_HIGH
                    "low" -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }

                userIds.forEach { userId ->
                    showNotification(
                        channelId = channelId,
                        title = title,
                        content = message,
                        priority = notificationPriority,
                        icon = android.R.drawable.ic_dialog_info
                    )

                    // Store notification in database
                    storeNotificationInDatabase(
                        userId = userId,
                        notificationData = NotificationData(
                            title = title,
                            message = message,
                            type = type,
                            priority = priority
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending bulk notification", e)
            }
        }
    }

    private suspend fun storeNotificationInDatabase(
        userId: String,
        notificationData: NotificationData
    ) {
        try {
            val timestamp = com.google.firebase.Timestamp.now()
            val notificationDoc = mapOf(
                "title" to notificationData.title,
                "message" to notificationData.message,
                "type" to notificationData.type,
                "priority" to notificationData.priority,
                "complaintId" to notificationData.complaintId,
                "createdAt" to timestamp,
                "isRead" to false
            )

            firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .add(notificationDoc)
                .await()

            Log.d(TAG, "Notification stored in database for user: $userId")

        } catch (e: Exception) {
            Log.e(TAG, "Error storing notification in database", e)
        }
    }

    suspend fun markNotificationAsRead(userId: String, notificationId: String) {
        try {
            firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true, "readAt", com.google.firebase.Timestamp.now())
                .await()

            Log.d(TAG, "Notification marked as read: $notificationId")

        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read", e)
        }
    }

    suspend fun getUnreadNotificationCount(userId: String): Int {
        return try {
            val unreadNotifications = firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()

            unreadNotifications.size()

        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread notification count", e)
            0
        }
    }

    suspend fun clearAllNotifications(userId: String) {
        try {
            val notifications = firestore.collection("user_notifications")
                .document(userId)
                .collection("notifications")
                .get()
                .await()

            val batch = firestore.batch()
            notifications.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            Log.d(TAG, "All notifications cleared for user: $userId")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notifications", e)
        }
    }
}