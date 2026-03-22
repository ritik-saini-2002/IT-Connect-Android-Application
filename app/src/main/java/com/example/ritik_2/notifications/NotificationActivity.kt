//package com.example.ritik_2.notifications
//
//import android.content.Context
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.view.WindowManager
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.vector.ImageVector
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.window.Dialog
//import androidx.compose.ui.window.DialogProperties
//import androidx.lifecycle.lifecycleScope
//import com.example.ritik_2.theme.ITConnectTheme
//import com.google.firebase.Timestamp
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.ListenerRegistration
//import com.google.firebase.firestore.Query
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//import java.text.SimpleDateFormat
//import java.util.*
//
//class NotificationActivity : ComponentActivity() {
//
//    private val auth = FirebaseAuth.getInstance()
//    private val firestore = FirebaseFirestore.getInstance()
//    private var notificationManager: NotificationManager? = null
//
//    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
//    private val notifications: StateFlow<List<NotificationItem>> = _notifications
//
//    private val _currentUserData = MutableStateFlow<UserNotificationData?>(null)
//    private val currentUserData: StateFlow<UserNotificationData?> = _currentUserData
//
//    private val _isLoading = MutableStateFlow(true)
//    private val isLoading: StateFlow<Boolean> = _isLoading
//
//    private var notificationListener: ListenerRegistration? = null
//    private var globalComplaintListener: ListenerRegistration? = null
//    private var activityListener: ListenerRegistration? = null
//
//    companion object {
//        const val TAG = "NotificationActivity"
//        const val EXTRA_SHOW_AS_POPUP = "show_as_popup"
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Initialize notification manager
//        notificationManager = NotificationManager.getInstance(this)
//
//        // Setup as popup if requested
//        val showAsPopup = intent.getBooleanExtra(EXTRA_SHOW_AS_POPUP, false)
//        if (showAsPopup) {
//            setupAsPopup()
//        }
//
//        // Load current user data and setup listeners
//        loadCurrentUserData()
//
//        setContent {
//            ITConnectTheme {
//                if (showAsPopup) {
//                    NotificationPopup(
//                        onDismiss = { finish() }
//                    )
//                } else {
//                    NotificationScreen()
//                }
//            }
//        }
//    }
//
//    private fun setupAsPopup() {
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
//        )
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
//        )
//
//        setFinishOnTouchOutside(true)
//    }
//
//    private fun loadCurrentUserData() {
//        val userId = auth.currentUser?.uid ?: return
//
//        lifecycleScope.launch {
//            try {
//                val userDoc = firestore.collection("user_access_control")
//                    .document(userId)
//                    .get()
//                    .await()
//
//                if (userDoc.exists()) {
//                    val userData = UserNotificationData(
//                        userId = userId,
//                        name = userDoc.getString("name") ?: "Unknown User",
//                        role = userDoc.getString("role") ?: "Employee",
//                        companyName = userDoc.getString("companyName") ?: "",
//                        sanitizedCompanyName = userDoc.getString("sanitizedCompany") ?: "",
//                        department = userDoc.getString("department") ?: "General",
//                        permissions = userDoc.get("permissions") as? List<String> ?: emptyList()
//                    )
//
//                    _currentUserData.value = userData
//                    setupNotificationListeners(userData)
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error loading user data", e)
//            } finally {
//                _isLoading.value = false
//            }
//        }
//    }
//
//    private fun setupNotificationListeners(userData: UserNotificationData) {
//        Log.d(TAG, "Setting up notification listeners for role: ${userData.role}")
//
//        // Setup listeners based on user role
//        when (userData.role) {
//            "Administrator", "Manager" -> {
//                setupAdminNotificationListeners(userData)
//            }
//            "Team Leader" -> {
//                setupTeamLeaderNotificationListeners(userData)
//            }
//            else -> {
//                setupUserNotificationListeners(userData)
//            }
//        }
//
//        // Always listen for global complaints (all users see these)
//        setupGlobalComplaintListener(userData)
//    }
//
//    private fun setupAdminNotificationListeners(userData: UserNotificationData) {
//        Log.d(TAG, "Setting up admin/manager listeners")
//
//        // Listen to all activities in the company
//        activityListener = firestore.collection("activity_logs")
//            .whereEqualTo("companyName", userData.companyName)
//            .orderBy("timestamp", Query.Direction.DESCENDING)
//            .limit(50)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to activities", e)
//                    return@addSnapshotListener
//                }
//
//                snapshot?.documents?.forEach { doc ->
//                    val activityData = doc.data ?: return@forEach
//                    createActivityNotification(activityData, userData)
//                }
//            }
//
//        // Listen to all complaints in company
//        setupAllComplaintsListener(userData)
//
//        // Listen to user management activities
//        setupUserManagementListener(userData)
//    }
//
//    private fun setupTeamLeaderNotificationListeners(userData: UserNotificationData) {
//        Log.d(TAG, "Setting up team leader listeners")
//
//        // Team leaders can see activities from Administrator, Manager, and other Team Leaders
//        val visibleRoles = listOf("Administrator", "Manager", "Team Leader")
//
//        activityListener = firestore.collection("activity_logs")
//            .whereEqualTo("companyName", userData.companyName)
//            .whereIn("userRole", visibleRoles)
//            .orderBy("timestamp", Query.Direction.DESCENDING)
//            .limit(30)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to team activities", e)
//                    return@addSnapshotListener
//                }
//
//                snapshot?.documents?.forEach { doc ->
//                    val activityData = doc.data ?: return@forEach
//                    createActivityNotification(activityData, userData)
//                }
//            }
//
//        // Listen to complaints only (not all activities)
//        setupComplaintsOnlyListener(userData)
//    }
//
//    private fun setupUserNotificationListeners(userData: UserNotificationData) {
//        Log.d(TAG, "Setting up user listeners")
//
//        // Regular users only see their own notifications and global complaints
//        notificationListener = firestore.collection("user_notifications")
//            .whereEqualTo("userId", userData.userId)
//            .orderBy("timestamp", Query.Direction.DESCENDING)
//            .limit(20)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to user notifications", e)
//                    return@addSnapshotListener
//                }
//
//                val userNotifications = snapshot?.documents?.mapNotNull { doc ->
//                    createNotificationFromDoc(doc.data ?: return@mapNotNull null, userData)
//                } ?: emptyList()
//
//                updateNotificationsList(userNotifications)
//            }
//    }
//
//    private fun setupGlobalComplaintListener(userData: UserNotificationData) {
//        Log.d(TAG, "Setting up global complaint listener")
//
//        globalComplaintListener = firestore.collection("complaints")
//            .document(userData.sanitizedCompanyName)
//            .collection("global_complaints")
//            .orderBy("createdAt", Query.Direction.DESCENDING)
//            .limit(10)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to global complaints", e)
//                    return@addSnapshotListener
//                }
//
//                val globalNotifications = snapshot?.documents?.mapNotNull { doc ->
//                    createGlobalComplaintNotification(doc.data ?: return@mapNotNull null, userData)
//                } ?: emptyList()
//
//                // Send device notifications for new global complaints
//                globalNotifications.forEach { notification ->
//                    if (notification.isNew) {
//                        sendDeviceNotification(notification)
//                        markNotificationAsSent(notification)
//                    }
//                }
//
//                updateNotificationsList(globalNotifications)
//            }
//    }
//
//    private fun setupAllComplaintsListener(userData: UserNotificationData) {
//        firestore.collection("all_complaints")
//            .whereEqualTo("companyName", userData.companyName)
//            .orderBy("createdAt", Query.Direction.DESCENDING)
//            .limit(20)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to all complaints", e)
//                    return@addSnapshotListener
//                }
//
//                val complaintNotifications = snapshot?.documents?.mapNotNull { doc ->
//                    createComplaintNotification(doc.data ?: return@mapNotNull null, userData)
//                } ?: emptyList()
//
//                updateNotificationsList(complaintNotifications)
//            }
//    }
//
//    private fun setupComplaintsOnlyListener(userData: UserNotificationData) {
//        firestore.collection("all_complaints")
//            .whereEqualTo("companyName", userData.companyName)
//            .orderBy("createdAt", Query.Direction.DESCENDING)
//            .limit(15)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to complaints", e)
//                    return@addSnapshotListener
//                }
//
//                val complaintNotifications = snapshot?.documents?.mapNotNull { doc ->
//                    createComplaintNotification(doc.data ?: return@mapNotNull null, userData)
//                } ?: emptyList()
//
//                updateNotificationsList(complaintNotifications)
//            }
//    }
//
//    private fun setupUserManagementListener(userData: UserNotificationData) {
//        firestore.collection("user_management_logs")
//            .whereEqualTo("companyName", userData.companyName)
//            .orderBy("timestamp", Query.Direction.DESCENDING)
//            .limit(10)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) {
//                    Log.e(TAG, "Error listening to user management", e)
//                    return@addSnapshotListener
//                }
//
//                val managementNotifications = snapshot?.documents?.mapNotNull { doc ->
//                    createUserManagementNotification(doc.data ?: return@mapNotNull null, userData)
//                } ?: emptyList()
//
//                updateNotificationsList(managementNotifications)
//            }
//    }
//
//    private fun createActivityNotification(activityData: Map<String, Any>, userData: UserNotificationData): NotificationItem? {
//        return try {
//            NotificationItem(
//                id = activityData["activityId"] as? String ?: UUID.randomUUID().toString(),
//                title = "Activity: ${activityData["action"] as? String ?: "Unknown Action"}",
//                message = activityData["description"] as? String ?: "No description",
//                type = NotificationType.ACTIVITY,
//                timestamp = (activityData["timestamp"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
//                isRead = false,
//                priority = NotificationPriority.MEDIUM,
//                actionData = mapOf(
//                    "userId" to (activityData["userId"] as? String ?: ""),
//                    "userName" to (activityData["userName"] as? String ?: "Unknown User"),
//                    "userRole" to (activityData["userRole"] as? String ?: "Unknown")
//                )
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating activity notification", e)
//            null
//        }
//    }
//
//    private fun createGlobalComplaintNotification(complaintData: Map<String, Any>, userData: UserNotificationData): NotificationItem? {
//        return try {
//            val createdBy = complaintData["createdBy"] as? Map<*, *>
//            val title = complaintData["title"] as? String ?: "New Global Complaint"
//            val urgency = complaintData["urgency"] as? String ?: "Medium"
//            val createdByName = createdBy?.get("name") as? String ?: "Unknown User"
//
//            NotificationItem(
//                id = complaintData["complaintId"] as? String ?: UUID.randomUUID().toString(),
//                title = "Global Complaint: $title",
//                message = "Priority: $urgency - Submitted by: $createdByName",
//                type = NotificationType.GLOBAL_COMPLAINT,
//                timestamp = (complaintData["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
//                isRead = false,
//                priority = when (urgency) {
//                    "Critical" -> NotificationPriority.CRITICAL
//                    "High" -> NotificationPriority.HIGH
//                    else -> NotificationPriority.MEDIUM
//                },
//                actionData = mapOf(
//                    "complaintId" to (complaintData["complaintId"] as? String ?: ""),
//                    "urgency" to urgency,
//                    "submittedBy" to createdByName
//                )
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating global complaint notification", e)
//            null
//        }
//    }
//
//    private fun createComplaintNotification(complaintData: Map<String, Any>, userData: UserNotificationData): NotificationItem? {
//        return try {
//            val title = complaintData["title"] as? String ?: "New Complaint"
//            val department = complaintData["department"] as? String ?: "Unknown Department"
//            val urgency = complaintData["urgency"] as? String ?: "Medium"
//            val createdBy = complaintData["createdBy"] as? Map<*, *>
//            val createdByName = createdBy?.get("name") as? String ?: "Unknown User"
//
//            NotificationItem(
//                id = complaintData["complaintId"] as? String ?: UUID.randomUUID().toString(),
//                title = "New Complaint: $title",
//                message = "Department: $department - Priority: $urgency - By: $createdByName",
//                type = NotificationType.COMPLAINT,
//                timestamp = (complaintData["createdAt"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
//                isRead = false,
//                priority = when (urgency) {
//                    "Critical" -> NotificationPriority.CRITICAL
//                    "High" -> NotificationPriority.HIGH
//                    else -> NotificationPriority.MEDIUM
//                },
//                actionData = mapOf(
//                    "complaintId" to (complaintData["complaintId"] as? String ?: ""),
//                    "department" to department,
//                    "urgency" to urgency
//                )
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating complaint notification", e)
//            null
//        }
//    }
//
//    private fun createUserManagementNotification(managementData: Map<String, Any>, userData: UserNotificationData): NotificationItem? {
//        return try {
//            val action = managementData["action"] as? String ?: "Unknown Action"
//            val targetUserName = managementData["targetUserName"] as? String ?: "Unknown User"
//            val performedByName = managementData["performedByName"] as? String ?: "System"
//
//            NotificationItem(
//                id = managementData["logId"] as? String ?: UUID.randomUUID().toString(),
//                title = "User Management: $action",
//                message = "User: $targetUserName - Action by: $performedByName",
//                type = NotificationType.USER_MANAGEMENT,
//                timestamp = (managementData["timestamp"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
//                isRead = false,
//                priority = NotificationPriority.MEDIUM,
//                actionData = mapOf(
//                    "action" to action,
//                    "targetUser" to targetUserName,
//                    "performedBy" to performedByName
//                )
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating user management notification", e)
//            null
//        }
//    }
//
//    private fun createNotificationFromDoc(notificationData: Map<String, Any>, userData: UserNotificationData): NotificationItem? {
//        return try {
//            NotificationItem(
//                id = notificationData["notificationId"] as? String ?: UUID.randomUUID().toString(),
//                title = notificationData["title"] as? String ?: "Notification",
//                message = notificationData["message"] as? String ?: "No message",
//                type = NotificationType.valueOf(notificationData["type"] as? String ?: "GENERAL"),
//                timestamp = (notificationData["timestamp"] as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
//                isRead = notificationData["isRead"] as? Boolean ?: false,
//                priority = NotificationPriority.valueOf(notificationData["priority"] as? String ?: "MEDIUM"),
//                actionData = notificationData["actionData"] as? Map<String, Any> ?: emptyMap()
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating notification from doc", e)
//            null
//        }
//    }
//
//    private fun updateNotificationsList(newNotifications: List<NotificationItem>) {
//        lifecycleScope.launch {
//            val currentNotifications = _notifications.value.toMutableList()
//
//            newNotifications.forEach { newNotification ->
//                val existingIndex = currentNotifications.indexOfFirst { it.id == newNotification.id }
//                if (existingIndex >= 0) {
//                    currentNotifications[existingIndex] = newNotification
//                } else {
//                    currentNotifications.add(0, newNotification)
//                }
//            }
//
//            // Sort by timestamp (newest first) and limit to 100 notifications
//            val sortedNotifications = currentNotifications
//                .sortedByDescending { it.timestamp }
//                .take(100)
//
//            _notifications.value = sortedNotifications
//        }
//    }
//
//    private fun sendDeviceNotification(notification: NotificationItem) {
//        notificationManager?.let { manager ->
//            when (notification.type) {
//                NotificationType.GLOBAL_COMPLAINT -> {
//                    val urgency = notification.actionData["urgency"] as? String ?: "Medium"
//                    val submittedBy = notification.actionData["submittedBy"] as? String ?: "Unknown User"
//                    val companyName = currentUserData.value?.companyName ?: "Company"
//
//                    manager.showNewGlobalComplaint(
//                        notification.title.removePrefix("Global Complaint: "),
//                        urgency,
//                        submittedBy,
//                        companyName
//                    )
//                }
//                NotificationType.COMPLAINT -> {
//                    val department = notification.actionData["department"] as? String ?: "Unknown"
//                    val urgency = notification.actionData["urgency"] as? String ?: "Medium"
//
//                    manager.showComplaintSubmitted(
//                        notification.title.removePrefix("New Complaint: "),
//                        department,
//                        urgency,
//                        false
//                    )
//                }
//                NotificationType.ACTIVITY -> {
//                    manager.showNotification(
//                        NotificationManager.NotificationType.SYSTEM,
//                        "System Activity",
//                        notification.message,
//                        notification.title
//                    )
//                }
//                else -> {
//                    manager.showNotification(
//                        NotificationManager.NotificationType.SYSTEM,
//                        notification.title,
//                        notification.message
//                    )
//                }
//            }
//        }
//    }
//
//    private fun markNotificationAsSent(notification: NotificationItem) {
//        // Mark as sent in local state to avoid duplicate device notifications
//        lifecycleScope.launch {
//            val currentList = _notifications.value.toMutableList()
//            val index = currentList.indexOfFirst { it.id == notification.id }
//            if (index >= 0) {
//                currentList[index] = notification.copy(isNew = false)
//                _notifications.value = currentList
//            }
//        }
//    }
//
//    private fun markAsRead(notificationId: String) {
//        lifecycleScope.launch {
//            try {
//                // Update in Firestore if it's a user notification
//                val userId = auth.currentUser?.uid ?: return@launch
//                firestore.collection("user_notifications")
//                    .whereEqualTo("notificationId", notificationId)
//                    .whereEqualTo("userId", userId)
//                    .get()
//                    .await()
//                    .documents
//                    .firstOrNull()?.reference?.update("isRead", true)
//
//                // Update in local state
//                val currentList = _notifications.value.toMutableList()
//                val index = currentList.indexOfFirst { it.id == notificationId }
//                if (index >= 0) {
//                    currentList[index] = currentList[index].copy(isRead = true)
//                    _notifications.value = currentList
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error marking notification as read", e)
//            }
//        }
//    }
//
//    @Composable
//    fun NotificationPopup(onDismiss: () -> Unit) {
//        Dialog(
//            onDismissRequest = onDismiss,
//            properties = DialogProperties(
//                usePlatformDefaultWidth = false,
//                dismissOnBackPress = true,
//                dismissOnClickOutside = true
//            )
//        ) {
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth(0.95f)
//                    .fillMaxHeight(0.8f),
//                shape = RoundedCornerShape(16.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.surface
//                )
//            ) {
//                Column(
//                    modifier = Modifier.fillMaxSize()
//                ) {
//                    // Header
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text(
//                            text = "Notifications",
//                            style = MaterialTheme.typography.headlineSmall,
//                            fontWeight = FontWeight.Bold
//                        )
//                        IconButton(onClick = onDismiss) {
//                            Icon(Icons.Default.Close, contentDescription = "Close")
//                        }
//                    }
//
//                    Divider()
//
//                    // Content
//                    NotificationContent(
//                        modifier = Modifier.weight(1f)
//                    )
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun NotificationScreen() {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp)
//        ) {
//            // Header
//            Text(
//                text = "Notifications",
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier.padding(bottom = 16.dp)
//            )
//
//            NotificationContent()
//        }
//    }
//
//    @Composable
//    fun NotificationContent(modifier: Modifier = Modifier) {
//        val notificationsList by notifications.collectAsState()
//        val loading by isLoading.collectAsState()
//        val userData by currentUserData.collectAsState()
//
//        Box(modifier = modifier.fillMaxSize()) {
//            AnimatedVisibility(
//                visible = loading,
//                enter = fadeIn(),
//                exit = fadeOut()
//            ) {
//                Box(
//                    modifier = Modifier.fillMaxSize(),
//                    contentAlignment = Alignment.Center
//                ) {
//                    CircularProgressIndicator()
//                }
//            }
//
//            AnimatedVisibility(
//                visible = !loading,
//                enter = fadeIn(),
//                exit = fadeOut()
//            ) {
//                if (notificationsList.isEmpty()) {
//                    Box(
//                        modifier = Modifier.fillMaxSize(),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Column(
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Icon(
//                                Icons.Default.Notifications,
//                                contentDescription = null,
//                                modifier = Modifier.size(64.dp),
//                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = "No notifications yet",
//                                style = MaterialTheme.typography.bodyLarge,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                            if (userData != null) {
//                                Spacer(modifier = Modifier.height(8.dp))
//                                Text(
//                                    text = "Role: ${userData!!.role}",
//                                    style = MaterialTheme.typography.bodySmall,
//                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
//                                )
//                            }
//                        }
//                    }
//                } else {
//                    LazyColumn(
//                        verticalArrangement = Arrangement.spacedBy(8.dp),
//                        contentPadding = PaddingValues(vertical = 8.dp)
//                    ) {
//                        items(
//                            items = notificationsList,
//                            key = { it.id }
//                        ) { notification ->
//                            NotificationCard(
//                                notification = notification,
//                                onClick = { markAsRead(notification.id) }
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun NotificationCard(
//        notification: NotificationItem,
//        onClick: () -> Unit
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .clickable { onClick() },
//            colors = CardDefaults.cardColors(
//                containerColor = if (notification.isRead) {
//                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
//                } else {
//                    MaterialTheme.colorScheme.surfaceVariant
//                }
//            ),
//            elevation = CardDefaults.cardElevation(
//                defaultElevation = if (notification.isRead) 2.dp else 4.dp
//            )
//        ) {
//            Row(
//                modifier = Modifier.padding(16.dp),
//                horizontalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                // Icon
//                Box(
//                    modifier = Modifier
//                        .size(40.dp)
//                        .clip(CircleShape)
//                        .background(notification.type.getColor().copy(alpha = 0.2f)),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Icon(
//                        imageVector = notification.type.getIcon(),
//                        contentDescription = null,
//                        tint = notification.type.getColor(),
//                        modifier = Modifier.size(20.dp)
//                    )
//                }
//
//                // Content
//                Column(
//                    modifier = Modifier.weight(1f)
//                ) {
//                    Row(
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.Top
//                    ) {
//                        Text(
//                            text = notification.title,
//                            style = MaterialTheme.typography.titleSmall,
//                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
//                            maxLines = 2,
//                            overflow = TextOverflow.Ellipsis,
//                            modifier = Modifier.weight(1f)
//                        )
//
//                        if (notification.priority == NotificationPriority.CRITICAL) {
//                            Icon(
//                                Icons.Default.PriorityHigh,
//                                contentDescription = "High Priority",
//                                tint = Color.Red,
//                                modifier = Modifier.size(16.dp)
//                            )
//                        }
//                    }
//
//                    Spacer(modifier = Modifier.height(4.dp))
//
//                    Text(
//                        text = notification.message,
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
//                        maxLines = 2,
//                        overflow = TextOverflow.Ellipsis
//                    )
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    Text(
//                        text = formatTimestamp(notification.timestamp),
//                        style = MaterialTheme.typography.labelSmall,
//                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
//                    )
//                }
//
//                // Read indicator
//                if (!notification.isRead) {
//                    Box(
//                        modifier = Modifier
//                            .size(8.dp)
//                            .clip(CircleShape)
//                            .background(MaterialTheme.colorScheme.primary)
//                    )
//                }
//            }
//        }
//    }
//
//    private fun formatTimestamp(timestamp: Long): String {
//        val now = System.currentTimeMillis()
//        val diff = now - timestamp
//
//        return when {
//            diff < 60_000 -> "Just now"
//            diff < 3600_000 -> "${diff / 60_000}m ago"
//            diff < 86400_000 -> "${diff / 3600_000}h ago"
//            diff < 604800_000 -> "${diff / 86400_000}d ago"
//            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // Clean up listeners
//        notificationListener?.remove()
//        globalComplaintListener?.remove()
//        activityListener?.remove()
//    }
//
//    companion object {
//        fun showAsPopup(context: Context) {
//            val intent = Intent(context, NotificationActivity::class.java)
//            intent.putExtra(EXTRA_SHOW_AS_POPUP, true)
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            context.startActivity(intent)
//        }
//
//        fun showAsActivity(context: Context) {
//            val intent = Intent(context, NotificationActivity::class.java)
//            context.startActivity(intent)
//        }
//    }
//}
//
//// Data classes for notifications
//data class NotificationItem(
//    val id: String,
//    val title: String,
//    val message: String,
//    val type: NotificationType,
//    val timestamp: Long,
//    val isRead: Boolean,
//    val priority: NotificationPriority,
//    val actionData: Map<String, Any> = emptyMap(),
//    val isNew: Boolean = true
//)
//
//data class UserNotificationData(
//    val userId: String,
//    val name: String,
//    val role: String,
//    val companyName: String,
//    val sanitizedCompanyName: String,
//    val department: String,
//    val permissions: List<String>
//)
//
//enum class NotificationType {
//    GLOBAL_COMPLAINT,
//    COMPLAINT,
//    ACTIVITY,
//    USER_MANAGEMENT,
//    SYSTEM,
//    GENERAL;
//
//    fun getIcon(): ImageVector = when (this) {
//        GLOBAL_COMPLAINT -> Icons.Default.Campaign
//        COMPLAINT -> Icons.Default.ReportProblem
//        ACTIVITY -> Icons.Default.Activity
//        USER_MANAGEMENT -> Icons.Default.ManageAccounts
//        SYSTEM -> Icons.Default.Settings
//        GENERAL -> Icons.Default.Notifications
//    }
//
//    fun getColor(): Color = when (this) {
//        GLOBAL_COMPLAINT -> Color(0xFFFF6B6B)
//        COMPLAINT -> Color(0xFFFFB74D)
//        ACTIVITY -> Color(0xFF64B5F6)
//        USER_MANAGEMENT -> Color(0xFF81C784)
//        SYSTEM -> Color(0xFF9C27B0)
//        GENERAL -> Color(0xFF757575)
//    }
//}
//
//enum class NotificationPriority {
//    LOW,
//    MEDIUM,
//    HIGH,
//    CRITICAL
//}