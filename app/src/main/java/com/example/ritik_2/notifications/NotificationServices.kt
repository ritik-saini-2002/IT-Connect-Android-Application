//package com.example.ritik_2.notifications
//
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.os.IBinder
//import android.util.Log
//import com.example.ritik_2.notifications.NotificationActivity.Companion.TAG
//import com.google.firebase.Timestamp
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.DocumentChange
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.ListenerRegistration
//import com.google.firebase.firestore.Query
//import kotlinx.coroutines.*
//import kotlinx.coroutines.tasks.await
//import java.util.UUID
//
//class NotificationService : Service() {
//
//    private val auth = FirebaseAuth.getInstance()
//    private val firestore = FirebaseFirestore.getInstance()
//    private lateinit var notificationManager: NotificationManager
//
//    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//    private var globalComplaintListener: ListenerRegistration? = null
//    private var userNotificationListener: ListenerRegistration? = null
//    private var adminNotificationListener: ListenerRegistration? = null
//
//    private var currentUserRole: String? = null
//    private var currentCompanyName: String? = null
//
//    companion object {
//        const val TAG = "NotificationService"
//        const val ACTION_START_MONITORING = "START_MONITORING"
//        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
//        const val ACTION_SHOW_NOTIFICATION_POPUP = "SHOW_NOTIFICATION_POPUP"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d(TAG, "NotificationService created")
//
//        notificationManager = NotificationManager.getInstance(this)
//
//        // Load current user data and start monitoring
//        loadUserDataAndStartMonitoring()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        Log.d(TAG, "onStartCommand: ${intent?.action}")
//
//        when (intent?.action) {
//            ACTION_START_MONITORING -> {
//                startNotificationMonitoring()
//            }
//            ACTION_STOP_MONITORING -> {
//                stopNotificationMonitoring()
//                stopSelf()
//            }
//            ACTION_SHOW_NOTIFICATION_POPUP -> {
//                showNotificationPopup()
//            }
//        }
//
//        return START_STICKY // Restart service if killed
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    private fun loadUserDataAndStartMonitoring() {
//        val currentUser = auth.currentUser
//        if (currentUser == null) {
//            Log.w(TAG, "User not authenticated, stopping service")
//            stopSelf()
//            return
//        }
//
//        serviceScope.launch {
//            try {
//                val userDoc = firestore.collection("user_access_control")
//                    .document(currentUser.uid)
//                    .get()
//                    .await()
//
//                if (userDoc.exists()) {
//                    currentUserRole = userDoc.getString("role")
//                    currentCompanyName = userDoc.getString("companyName")
//                    val sanitizedCompanyName = userDoc.getString("sanitizedCompany") ?: ""
//
//                    Log.d(TAG, "User loaded - Role: $currentUserRole, Company: $currentCompanyName")
//
//                    // Start appropriate monitoring based on user role
//                    startRoleBasedMonitoring(currentUser.uid, sanitizedCompanyName)
//                } else {
//                    Log.w(TAG, "User document not found")
//                    stopSelf()
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error loading user data", e)
//                stopSelf()
//            }
//        }
//    }
//
//    private fun startNotificationMonitoring() {
//        Log.d(TAG, "Starting notification monitoring")
//        loadUserDataAndStartMonitoring()
//    }
//
//    private fun stopNotificationMonitoring() {
//        Log.d(TAG, "Stopping notification monitoring")
//
//        globalComplaintListener?.remove()
//        userNotificationListener?.remove()
//        adminNotificationListener?.remove()
//
//        globalComplaintListener = null
//        userNotificationListener = null
//        adminNotificationListener = null
//    }
//
//    private fun startRoleBasedMonitoring(userId: String, sanitizedCompanyName: String) {
//        Log.d(TAG, "Starting role-based monitoring for role: $currentUserRole")
//
//        // All users monitor global complaints
//        startGlobalComplaintMonitoring(sanitizedCompanyName)
//
//        // Role-specific monitoring
//        when (currentUserRole) {
//            "Administrator", "Manager" -> {
//                startAdminMonitoring(sanitizedCompanyName)
//                startUserNotificationMonitoring(userId)
//            }
//            "Team Leader" -> {
//                startTeamLeaderMonitoring(sanitizedCompanyName)
//                startUserNotificationMonitoring(userId)
//            }
//            else -> {
//                startUserNotificationMonitoring(userId)
//            }
//        }
//    }
//
//    private fun startGlobalComplaintMonitoring(sanitizedCompanyName: String) {
//        Log.d(TAG, "Starting global complaint monitoring")
//
//        globalComplaintListener = firestore
//            .collection("complaints")
//            .document(sanitizedCompanyName)
//            .collection("global_complaints")
//            .orderBy("createdAt", Query.Direction.DESCENDING)
//            .limit(5) // Only monitor recent global complaints
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to user notifications", e)
//                    return@addSnapshotListener
//                }
//
//                snapshot?.documentChanges?.forEach { change ->
//                    if (change.type == DocumentChange.Type.ADDED) {
//                        val notificationData = change.document.data
//                        handleUserNotification(notificationData)
//                    }
//                }
//            }
//    }
//
//    private fun handleNewGlobalComplaint(complaintData: Map<String, Any>) {
//        try {
//            val title = complaintData["title"] as? String ?: "New Global Complaint"
//            val urgency = complaintData["urgency"] as? String ?: "Medium"
//            val createdBy = complaintData["createdBy"] as? Map<*, *>
//            val createdByName = createdBy?.get("name") as? String ?: "Unknown User"
//
//            Log.d(TAG, "New global complaint: $title")
//
//            notificationManager.showNewGlobalComplaint(
//                complaintTitle = title,
//                urgency = urgency,
//                submittedBy = createdByName,
//                companyName = currentCompanyName ?: "Company"
//            )
//
//            // Store notification for the popup
//            storeNotificationForPopup(
//                type = "GLOBAL_COMPLAINT",
//                title = "New Global Complaint",
//                message = "$title - Priority: $urgency - By: $createdByName",
//                priority = when (urgency) {
//                    "Critical" -> "CRITICAL"
//                    "High" -> "HIGH"
//                    else -> "MEDIUM"
//                },
//                actionData = mapOf(
//                    "complaintId" to (complaintData["complaintId"] as? String ?: ""),
//                    "urgency" to urgency,
//                    "submittedBy" to createdByName
//                )
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error handling global complaint", e)
//        }
//    }
//
//    private fun handleNewAdminActivity(activityData: Map<String, Any>) {
//        try {
//            val action = activityData["action"] as? String ?: "Unknown Activity"
//            val userName = activityData["userName"] as? String ?: "Unknown User"
//            val description = activityData["description"] as? String ?: "No description"
//            val activityType = activityData["activityType"] as? String ?: "GENERAL"
//
//            Log.d(TAG, "New admin activity: $action by $userName")
//
//            // Only notify for important activities
//            val importantActivities = listOf(
//                "USER_CREATED", "USER_DELETED", "COMPLAINT_SUBMITTED",
//                "ERROR_OCCURRED", "DATA_EXPORTED", "PERMISSION_CHANGED"
//            )
//
//            if (importantActivities.contains(activityType)) {
//                notificationManager.showNotification(
//                    type = NotificationManager.NotificationType.SYSTEM,
//                    title = "System Activity: $action",
//                    content = "By: $userName",
//                    expandedContent = description
//                )
//
//                storeNotificationForPopup(
//                    type = "ACTIVITY",
//                    title = "Activity: $action",
//                    message = "By: $userName - $description",
//                    priority = when (activityType) {
//                        "USER_DELETED", "ERROR_OCCURRED", "PERMISSION_CHANGED" -> "HIGH"
//                        "USER_CREATED", "DATA_EXPORTED" -> "MEDIUM"
//                        else -> "LOW"
//                    },
//                    actionData = mapOf(
//                        "activityType" to activityType,
//                        "userName" to userName,
//                        "userId" to (activityData["userId"] as? String ?: "")
//                    )
//                )
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error handling admin activity", e)
//        }
//    }
//
//    private fun handleTeamLeaderActivity(activityData: Map<String, Any>) {
//        try {
//            val action = activityData["action"] as? String ?: "Unknown Activity"
//            val userName = activityData["userName"] as? String ?: "Unknown User"
//            val userRole = activityData["userRole"] as? String ?: "Unknown"
//            val activityType = activityData["activityType"] as? String ?: "GENERAL"
//
//            Log.d(TAG, "Team leader activity: $action by $userName ($userRole)")
//
//            // Team leaders see activities from Admin, Manager, Team Leader roles
//            // Focus on complaint-related activities
//            val relevantActivities = listOf(
//                "COMPLAINT_SUBMITTED", "COMPLAINT_UPDATED", "USER_CREATED",
//                "ADMIN_PANEL_ACCESSED", "ERROR_OCCURRED"
//            )
//
//            if (relevantActivities.contains(activityType)) {
//                notificationManager.showNotification(
//                    type = NotificationManager.NotificationType.SYSTEM,
//                    title = "Team Activity: $action",
//                    content = "$userName ($userRole)",
//                    expandedContent = activityData["description"] as? String ?: "No description"
//                )
//
//                storeNotificationForPopup(
//                    type = "ACTIVITY",
//                    title = "Team Activity: $action",
//                    message = "$userName ($userRole) - ${activityData["description"] as? String ?: ""}",
//                    priority = "MEDIUM",
//                    actionData = mapOf(
//                        "activityType" to activityType,
//                        "userName" to userName,
//                        "userRole" to userRole
//                    )
//                )
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error handling team leader activity", e)
//        }
//    }
//
//    private fun handleAdminNotification(notificationData: Map<String, Any>) {
//        try {
//            val title = notificationData["title"] as? String ?: "Admin Notification"
//            val message = notificationData["message"] as? String ?: "No message"
//            val priority = notificationData["priority"] as? String ?: "MEDIUM"
//
//            Log.d(TAG, "Admin notification: $title")
//
//            val notificationType = when (notificationData["type"] as? String) {
//                "SECURITY" -> NotificationManager.NotificationType.SYSTEM
//                else -> NotificationManager.NotificationType.SYSTEM
//            }
//
//            notificationManager.showNotification(
//                type = notificationType,
//                title = title,
//                content = message
//            )
//
//            storeNotificationForPopup(
//                type = notificationData["type"] as? String ?: "SYSTEM",
//                title = title,
//                message = message,
//                priority = priority,
//                actionData = notificationData["activityData"] as? Map<String, Any> ?: emptyMap()
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error handling admin notification", e)
//        }
//    }
//
//    private fun handleUserNotification(notificationData: Map<String, Any>) {
//        try {
//            val title = notificationData["title"] as? String ?: "Notification"
//            val message = notificationData["message"] as? String ?: "No message"
//            val type = notificationData["type"] as? String ?: "GENERAL"
//
//            Log.d(TAG, "User notification: $title")
//
//            val notificationType = when (type) {
//                "COMPLAINT" -> NotificationManager.NotificationType.COMPLAINT
//                "SYSTEM" -> NotificationManager.NotificationType.SYSTEM
//                else -> NotificationManager.NotificationType.USER_UPDATE
//            }
//
//            notificationManager.showNotification(
//                type = notificationType,
//                title = title,
//                content = message
//            )
//
//            storeNotificationForPopup(
//                type = type,
//                title = title,
//                message = message,
//                priority = notificationData["priority"] as? String ?: "MEDIUM",
//                actionData = notificationData["actionData"] as? Map<String, Any> ?: emptyMap()
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error handling user notification", e)
//        }
//    }
//
//    private fun storeNotificationForPopup(
//        type: String,
//        title: String,
//        message: String,
//        priority: String,
//        actionData: Map<String, Any> = emptyMap()
//    ) {
//        val userId = auth.currentUser?.uid ?: return
//
//        serviceScope.launch {
//            try {
//                val notificationId = UUID.randomUUID().toString()
//                val notificationData = mapOf(
//                    "notificationId" to notificationId,
//                    "userId" to userId,
//                    "type" to type,
//                    "title" to title,
//                    "message" to message,
//                    "priority" to priority,
//                    "timestamp" to Timestamp.now(),
//                    "isRead" to false,
//                    "actionData" to actionData,
//                    "companyName" to currentCompanyName,
//                    "userRole" to currentUserRole
//                )
//
//                firestore.collection("user_notifications")
//                    .document(notificationId)
//                    .set(notificationData)
//                    .await()
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error storing notification for popup", e)
//            }
//        }
//    }
//
//    private fun showNotificationPopup() {
//        try {
//            Log.d(TAG, "Showing notification popup")
//            NotificationActivity.showAsPopup(this)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error showing notification popup", e)
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d(TAG, "NotificationService destroyed")
//
//        stopNotificationMonitoring()
//        serviceScope.cancel()
//    }
//
//    companion object {
//        fun startService(context: Context) {
//            val intent = Intent(context, NotificationService::class.java)
//            intent.action = ACTION_START_MONITORING
//            context.startForegroundService(intent)
//        }
//
//        fun stopService(context: Context) {
//            val intent = Intent(context, NotificationService::class.java)
//            intent.action = ACTION_STOP_MONITORING
//            context.startService(intent)
//        }
//
//        fun showNotificationPopup(context: Context) {
//            val intent = Intent(context, NotificationService::class.java)
//            intent.action = ACTION_SHOW_NOTIFICATION_POPUP
//            context.startService(intent)
//        }
//    }
//}TAG, "Error listening to global complaints", e)
//return@addSnapshotListener
//}
//
//snapshot?.documentChanges?.forEach { change ->
//    if (change.type == DocumentChange.Type.ADDED) {
//        val complaintData = change.document.data
//        handleNewGlobalComplaint(complaintData)
//    }
//}
//}
//}
//
//private fun startAdminMonitoring(sanitizedCompanyName: String) {
//    Log.d(TAG, "Starting admin monitoring")
//
//    // Monitor all company activities
//    adminNotificationListener = firestore
//        .collection("companies_activity_logs")
//        .document(sanitizedCompanyName)
//        .collection("activities")
//        .orderBy("timestamp", Query.Direction.DESCENDING)
//        .limit(10)
//        .addSnapshotListener { snapshot, e ->
//            if (e != null) {
//                Log.e(TAG, "Error listening to admin activities", e)
//                return@addSnapshotListener
//            }
//
//            snapshot?.documentChanges?.forEach { change ->
//                if (change.type == DocumentChange.Type.ADDED) {
//                    val activityData = change.document.data
//                    handleNewAdminActivity(activityData)
//                }
//            }
//        }
//
//    // Monitor admin-specific notifications
//    firestore
//        .collection("admin_notifications")
//        .document(sanitizedCompanyName)
//        .collection("notifications")
//        .orderBy("timestamp", Query.Direction.DESCENDING)
//        .limit(5)
//        .addSnapshotListener { snapshot, e ->
//            if (e != null) {
//                Log.e(TAG, "Error listening to admin notifications", e)
//                return@addSnapshotListener
//            }
//
//            snapshot?.documentChanges?.forEach { change ->
//                if (change.type == DocumentChange.Type.ADDED) {
//                    val notificationData = change.document.data
//                    handleAdminNotification(notificationData)
//                }
//            }
//        }
//}
//
//private fun startTeamLeaderMonitoring(sanitizedCompanyName: String) {
//    Log.d(TAG, "Starting team leader monitoring")
//
//    // Monitor activities from specific roles (Administrator, Manager, Team Leader)
//    val monitoredRoles = listOf("Administrator", "Manager", "Team Leader")
//
//    adminNotificationListener = firestore
//        .collection("activity_logs")
//        .whereEqualTo("sanitizedCompanyName", sanitizedCompanyName)
//        .whereIn("userRole", monitoredRoles)
//        .orderBy("timestamp", Query.Direction.DESCENDING)
//        .limit(15)
//        .addSnapshotListener { snapshot, e ->
//            if (e != null) {
//                Log.e(TAG, "Error listening to team leader activities", e)
//                return@addSnapshotListener
//            }
//
//            snapshot?.documentChanges?.forEach { change ->
//                if (change.type == DocumentChange.Type.ADDED) {
//                    val activityData = change.document.data
//                    handleTeamLeaderActivity(activityData)
//                }
//            }
//        }
//}
//
////private fun startUserNotificationMonitoring(userId: String) {
////    Log.d(TAG, "Starting user notification monitoring")
////
////    userNotificationListener = firestore
////        .collection("user_notifications")
////        .whereEqualTo("userId", userId)
////        .orderBy("timestamp", Query.Direction.DESCENDING)
////        .limit(10)
////        .addSnapshotListener { snapshot, e ->
////            if (e != null) {
////                Log.e(e