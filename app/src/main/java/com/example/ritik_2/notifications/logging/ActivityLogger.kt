//package com.example.ritik_2.notifications.logging
//
//import android.content.Context
//import android.os.Build
//import android.util.Log
//import com.google.firebase.Timestamp
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FieldValue
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.SetOptions
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//import java.util.*
//
//object ActivityLogger {
//
//    private val firestore = FirebaseFirestore.getInstance()
//    private val auth = FirebaseAuth.getInstance()
//    private val scope = CoroutineScope(Dispatchers.IO)
//
//    const val TAG = "ActivityLogger"
//
//    enum class ActivityType(val displayName: String) {
//        LOGIN("User Login"),
//        LOGOUT("User Logout"),
//        COMPLAINT_SUBMITTED("Complaint Submitted"),
//        COMPLAINT_VIEWED("Complaint Viewed"),
//        COMPLAINT_UPDATED("Complaint Updated"),
//        COMPLAINT_DELETED("Complaint Deleted"),
//        USER_CREATED("User Created"),
//        USER_UPDATED("User Updated"),
//        USER_DELETED("User Deleted"),
//        PROFILE_UPDATED("Profile Updated"),
//        PROFILE_VIEWED("Profile Viewed"),
//        ADMIN_PANEL_ACCESSED("Admin Panel Accessed"),
//        SETTINGS_CHANGED("Settings Changed"),
//        FILE_UPLOADED("File Uploaded"),
//        FILE_DOWNLOADED("File Downloaded"),
//        NOTIFICATION_VIEWED("Notification Viewed"),
//        SEARCH_PERFORMED("Search Performed"),
//        REPORT_GENERATED("Report Generated"),
//        DATA_EXPORTED("Data Exported"),
//        PERMISSION_CHANGED("Permission Changed"),
//        ROLE_ASSIGNED("Role Assigned"),
//        DEPARTMENT_CHANGED("Department Changed"),
//        SERVER_CONNECTED("Server Connected"),
//        API_CALLED("API Called"),
//        ERROR_OCCURRED("Error Occurred"),
//        SYSTEM_MAINTENANCE("System Maintenance")
//    }
//
//    /**
//     * Log a user activity with full context
//     */
//    fun logActivity(
//        context: Context,
//        activityType: ActivityType,
//        description: String,
//        additionalData: Map<String, Any> = emptyMap(),
//        targetUserId: String? = null,
//        targetResource: String? = null
//    ) {
//        val currentUser = auth.currentUser
//        if (currentUser == null) {
//            Log.w(TAG, "Cannot log activity - user not authenticated")
//            return
//        }
//
//        scope.launch {
//            try {
//                // Get current user data
//                val userDoc = firestore.collection("user_access_control")
//                    .document(currentUser.uid)
//                    .get()
//                    .await()
//
//                if (!userDoc.exists()) {
//                    Log.w(TAG, "User data not found for activity logging")
//                    return@launch
//                }
//
//                val userData = userDoc.data ?: return@launch
//                val activityId = UUID.randomUUID().toString()
//                val timestamp = Timestamp.now()
//
//                val activityData = mapOf(
//                    "activityId" to activityId,
//                    "activityType" to activityType.name,
//                    "action" to activityType.displayName,
//                    "description" to description,
//
//                    // User information
//                    "userId" to currentUser.uid,
//                    "userName" to (userData["name"] as? String ?: "Unknown User"),
//                    "userEmail" to (userData["email"] as? String ?: currentUser.email),
//                    "userRole" to (userData["role"] as? String ?: "Unknown"),
//                    "userDepartment" to (userData["department"] as? String ?: "Unknown"),
//
//                    // Company information
//                    "companyName" to (userData["companyName"] as? String ?: "Unknown Company"),
//                    "sanitizedCompanyName" to (userData["sanitizedCompany"] as? String ?: "unknown_company"),
//
//                    // Target information (if applicable)
//                    "targetUserId" to targetUserId,
//                    "targetResource" to targetResource,
//
//                    // Additional context
//                    "additionalData" to additionalData,
//                    "timestamp" to timestamp,
//                    "deviceInfo" to getDeviceInfo(context),
//
//                    // Categorization
//                    "category" to getCategoryForActivity(activityType),
//                    "severity" to getSeverityForActivity(activityType),
//                    "isSecurityRelevant" to isSecurityRelevant(activityType),
//
//                    // Search terms for easy filtering
//                    "searchTerms" to createSearchTerms(
//                        activityType.displayName,
//                        description,
//                        userData["name"] as? String ?: "",
//                        userData["role"] as? String ?: "",
//                        userData["department"] as? String ?: ""
//                    )
//                )
//
//                // Store in activity logs collection
//                val activityLogRef = firestore.collection("activity_logs").document(activityId)
//                activityLogRef.set(activityData).await()
//
//                // Store in company-specific activity logs
//                val companyActivityRef = firestore
//                    .collection("companies_activity_logs")
//                    .document(userData["sanitizedCompany"] as? String ?: "unknown_company")
//                    .collection("activities")
//                    .document(activityId)
//                activityLogRef.set(activityData).await()
//
//                // Create notifications for relevant activities
//                createActivityNotifications(activityData, userData)
//
//                // Update activity statistics
//                updateActivityStatistics(activityData, userData)
//
//                Log.d(TAG, "Activity logged successfully: ${activityType.displayName}")
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error logging activity", e)
//            }
//        }
//    }
//
//    /**
//     * Log user login activity
//     */
//    fun logLogin(context: Context, loginMethod: String = "email") {
//        logActivity(
//            context = context,
//            activityType = ActivityType.LOGIN,
//            description = "User logged in using $loginMethod",
//            additionalData = mapOf(
//                "loginMethod" to loginMethod,
//                "loginTime" to System.currentTimeMillis()
//            )
//        )
//    }
//
//    /**
//     * Log user logout activity
//     */
//    fun logLogout(context: Context) {
//        logActivity(
//            context = context,
//            activityType = ActivityType.LOGOUT,
//            description = "User logged out",
//            additionalData = mapOf(
//                "logoutTime" to System.currentTimeMillis()
//            )
//        )
//    }
//
//    /**
//     * Log complaint submission
//     */
//    fun logComplaintSubmitted(
//        context: Context,
//        complaintId: String,
//        complaintTitle: String,
//        department: String,
//        urgency: String,
//        isGlobal: Boolean
//    ) {
//        logActivity(
//            context = context,
//            activityType = ActivityType.COMPLAINT_SUBMITTED,
//            description = "Submitted complaint: $complaintTitle to $department department",
//            additionalData = mapOf(
//                "complaintId" to complaintId,
//                "complaintTitle" to complaintTitle,
//                "department" to department,
//                "urgency" to urgency,
//                "isGlobal" to isGlobal
//            ),
//            targetResource = complaintId
//        )
//    }
//
//    /**
//     * Log user creation (for admin activities)
//     */
//    fun logUserCreated(
//        context: Context,
//        targetUserId: String,
//        targetUserName: String,
//        targetUserRole: String,
//        targetUserDepartment: String
//    ) {
//        logActivity(
//            context = context,
//            activityType = ActivityType.USER_CREATED,
//            description = "Created user account for $targetUserName ($targetUserRole in $targetUserDepartment)",
//            additionalData = mapOf(
//                "targetUserName" to targetUserName,
//                "targetUserRole" to targetUserRole,
//                "targetUserDepartment" to targetUserDepartment
//            ),
//            targetUserId = targetUserId
//        )
//    }
//
//    /**
//     * Log profile updates
//     */
//    fun logProfileUpdate(context: Context, updatedFields: List<String>) {
//        logActivity(
//            context = context,
//            activityType = ActivityType.PROFILE_UPDATED,
//            description = "Updated profile fields: ${updatedFields.joinToString(", ")}",
//            additionalData = mapOf(
//                "updatedFields" to updatedFields
//            )
//        )
//    }
//
//    /**
//     * Log admin panel access
//     */
//    fun logAdminPanelAccess(context: Context, section: String = "main") {
//        logActivity(
//            context = context,
//            activityType = ActivityType.ADMIN_PANEL_ACCESSED,
//            description = "Accessed admin panel - $section section",
//            additionalData = mapOf(
//                "section" to section,
//                "accessTime" to System.currentTimeMillis()
//            )
//        )
//    }
//
//    /**
//     * Log file operations
//     */
//    fun logFileOperation(
//        context: Context,
//        operation: String, // "upload" or "download"
//        fileName: String,
//        fileSize: Long,
//        relatedResource: String? = null
//    ) {
//        val activityType = if (operation == "upload") ActivityType.FILE_UPLOADED else ActivityType.FILE_DOWNLOADED
//
//        logActivity(
//            context = context,
//            activityType = activityType,
//            description = "${operation.capitalize()} file: $fileName",
//            additionalData = mapOf(
//                "fileName" to fileName,
//                "fileSize" to fileSize,
//                "operation" to operation,
//                "relatedResource" to relatedResource
//            )
//        )
//    }
//
//    /**
//     * Log error occurrences
//     */
//    fun logError(
//        context: Context,
//        errorType: String,
//        errorMessage: String,
//        stackTrace: String? = null,
//        relatedActivity: String? = null
//    ) {
//        logActivity(
//            context = context,
//            activityType = ActivityType.ERROR_OCCURRED,
//            description = "Error occurred: $errorType - $errorMessage",
//            additionalData = mapOf(
//                "errorType" to errorType,
//                "errorMessage" to errorMessage,
//                "stackTrace" to (stackTrace ?: ""),
//                "relatedActivity" to (relatedActivity ?: ""),
//                "errorTime" to System.currentTimeMillis()
//            )
//        )
//    }
//
//    private fun createActivityNotifications(activityData: Map<String, Any>, userData: Map<String, Any>) {
//        scope.launch {
//            try {
//                val activityType = ActivityType.valueOf(activityData["activityType"] as String)
//                val companyName = userData["sanitizedCompany"] as? String ?: return@launch
//
//                // Create notifications for activities that should be visible to admins/managers
//                if (shouldNotifyAdmins(activityType)) {
//                    createAdminNotification(activityData, companyName)
//                }
//
//                // Create notifications for security-relevant activities
//                if (activityData["isSecurityRelevant"] as? Boolean == true) {
//                    createSecurityNotification(activityData, companyName)
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error creating activity notifications", e)
//            }
//        }
//    }
//
//    private suspend fun createAdminNotification(activityData: Map<String, Any>, companyName: String) {
//        val notificationData = mapOf(
//            "notificationId" to UUID.randomUUID().toString(),
//            "type" to "ACTIVITY",
//            "title" to "User Activity: ${activityData["action"]}",
//            "message" to activityData["description"] as String,
//            "priority" to "MEDIUM",
//            "timestamp" to Timestamp.now(),
//            "isRead" to false,
//            "targetRoles" to listOf("Administrator", "Manager"),
//            "companyName" to companyName,
//            "activityData" to activityData
//        )
//
//        // Store notification for admins to see
//        firestore.collection("admin_notifications")
//            .document(companyName)
//            .collection("notifications")
//            .document(notificationData["notificationId"] as String)
//            .set(notificationData)
//            .await()
//    }
//
//    private suspend fun createSecurityNotification(activityData: Map<String, Any>, companyName: String) {
//        val notificationData = mapOf(
//            "notificationId" to UUID.randomUUID().toString(),
//            "type" to "SECURITY",
//            "title" to "Security Alert: ${activityData["action"]}",
//            "message" to "Security-relevant activity: ${activityData["description"]}",
//            "priority" to "HIGH",
//            "timestamp" to Timestamp.now(),
//            "isRead" to false,
//            "targetRoles" to listOf("Administrator"),
//            "companyName" to companyName,
//            "activityData" to activityData
//        )
//
//        firestore.collection("security_notifications")
//            .document(companyName)
//            .collection("notifications")
//            .document(notificationData["notificationId"] as String)
//            .set(notificationData)
//            .await()
//    }
//
//    private fun updateActivityStatistics(activityData: Map<String, Any>, userData: Map<String, Any>) {
//        scope.launch {
//            try {
//                val companyName = userData["sanitizedCompany"] as? String ?: return@launch
//                val activityType = activityData["activityType"] as? String ?: return@launch
//                val userRole = userData["role"] as? String ?: return@launch
//
//                // Update company activity statistics
//                val companyStatsRef = firestore
//                    .collection("companies_statistics")
//                    .document(companyName)
//
//                val updates = mapOf(
//                    "totalActivities" to FieldValue.increment(1),
//                    "activitiesByType.$activityType" to FieldValue.increment(1),
//                    "activitiesByRole.$userRole" to FieldValue.increment(1),
//                    "lastActivityDate" to Timestamp.now(),
//                    "lastUpdated" to Timestamp.now()
//                )
//
//                companyStatsRef.set(updates, SetOptions.merge()).await()
//
//                // Update user activity statistics
//                val userStatsRef = firestore
//                    .collection("user_access_control")
//                    .document(activityData["userId"] as String)
//
//                val userUpdates = mapOf(
//                    "activityStats.totalActivities" to FieldValue.increment(1),
//                    "activityStats.activitiesByType.$activityType" to FieldValue.increment(1),
//                    "activityStats.lastActivityDate" to Timestamp.now()
//                )
//
//                userStatsRef.update(userUpdates).await()
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error updating activity statistics", e)
//            }
//        }
//    }
//
//    private fun getDeviceInfo(context: Context): Map<String, String> {
//        return mapOf(
//            "deviceModel" to Build.MODEL,
//            "osVersion" to Build.VERSION.RELEASE,
//            "appVersion" to try {
//                context.packageManager.getPackageInfo(context.packageName, 0).versionName
//            } catch (e: Exception) {
//                "Unknown"
//            }
//        )
//    }
//
//    private fun getCategoryForActivity(activityType: ActivityType): String {
//        return when (activityType) {
//            ActivityType.LOGIN, ActivityType.LOGOUT -> "Authentication"
//            ActivityType.COMPLAINT_SUBMITTED, ActivityType.COMPLAINT_VIEWED,
//            ActivityType.COMPLAINT_UPDATED, ActivityType.COMPLAINT_DELETED -> "Complaint Management"
//            ActivityType.USER_CREATED, ActivityType.USER_UPDATED, ActivityType.USER_DELETED -> "User Management"
//            ActivityType.PROFILE_UPDATED, ActivityType.PROFILE_VIEWED -> "Profile Management"
//            ActivityType.ADMIN_PANEL_ACCESSED, ActivityType.PERMISSION_CHANGED,
//            ActivityType.ROLE_ASSIGNED -> "Administrative"
//            ActivityType.FILE_UPLOADED, ActivityType.FILE_DOWNLOADED -> "File Operations"
//            ActivityType.SETTINGS_CHANGED, ActivityType.SYSTEM_MAINTENANCE -> "System"
//            else -> "General"
//        }
//    }
//
//    private fun getSeverityForActivity(activityType: ActivityType): String {
//        return when (activityType) {
//            ActivityType.USER_DELETED, ActivityType.PERMISSION_CHANGED,
//            ActivityType.ROLE_ASSIGNED, ActivityType.ERROR_OCCURRED -> "HIGH"
//            ActivityType.USER_CREATED, ActivityType.USER_UPDATED,
//            ActivityType.ADMIN_PANEL_ACCESSED, ActivityType.DATA_EXPORTED -> "MEDIUM"
//            else -> "LOW"
//        }
//    }
//
//    private fun isSecurityRelevant(activityType: ActivityType): Boolean {
//        return when (activityType) {
//            ActivityType.LOGIN, ActivityType.LOGOUT, ActivityType.USER_CREATED,
//            ActivityType.USER_DELETED, ActivityType.PERMISSION_CHANGED,
//            ActivityType.ROLE_ASSIGNED, ActivityType.ADMIN_PANEL_ACCESSED,
//            ActivityType.DATA_EXPORTED -> true
//            else -> false
//        }
//    }
//
//    private fun shouldNotifyAdmins(activityType: ActivityType): Boolean {
//        return when (activityType) {
//            ActivityType.USER_CREATED, ActivityType.USER_UPDATED, ActivityType.USER_DELETED,
//            ActivityType.COMPLAINT_SUBMITTED, ActivityType.ERROR_OCCURRED,
//            ActivityType.ADMIN_PANEL_ACCESSED, ActivityType.DATA_EXPORTED -> true
//            else -> false
//        }
//    }
//
//    private fun createSearchTerms(vararg terms: String): List<String> {
//        return terms.flatMap { term ->
//            term.lowercase().split("\\s+".toRegex())
//        }.filter { it.isNotEmpty() && it.length > 2 }.distinct()
//    }
//}