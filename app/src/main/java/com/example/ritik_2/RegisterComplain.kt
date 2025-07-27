package com.example.ritik_2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ritik_2.ui.theme.RegisterComplaintScreen
import com.example.ritik_2.ui.theme.ui.theme.ComplaintAppTheme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterComplain : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedFileUri = selectedUri
            triggerHapticFeedback(HapticType.LIGHT)
        }
    }

    private var selectedFileUri: Uri? = null
    private val _complaints = MutableStateFlow<List<ComplaintWithId>>(emptyList())
    val complaints: StateFlow<List<ComplaintWithId>> = _complaints

    private val _availableUsers = MutableStateFlow<List<AssignableUser>>(emptyList())
    val availableUsers: StateFlow<List<AssignableUser>> = _availableUsers

    // Notification permission launcher
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    companion object {
        private const val CHANNEL_ID = "complaint_notifications"
        private const val NOTIFICATION_PERMISSION_CODE = 100
        private const val TAG = "RegisterComplaint"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup notifications
        setupNotificationChannel()
        requestNotificationPermissionIfNeeded()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            signInAnonymously()
        } else {
            loadComplaints()
            loadAvailableUsers()
        }

        setContent {
            ComplaintAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Complaint Notifications"
            val descriptionText = "Notifications for complaint submissions and updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnSuccessListener {
                Toast.makeText(this, "Signed in anonymously", Toast.LENGTH_SHORT).show()
                loadComplaints()
                loadAvailableUsers()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadComplaints() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("complaints")
            .whereEqualTo("createdBy.userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading complaints: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error loading complaints", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val complaintsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val createdByData = data["createdBy"] as? Map<*, *>
                            val assignedToData = data["assignedTo"] as? Map<*, *>
                            val statusData = data["status"] as? Map<*, *>
                            val attachmentData = data["attachment"] as? Map<*, *>

                            ComplaintWithId(
                                id = doc.id,
                                title = data["title"] as? String ?: "",
                                description = data["description"] as? String ?: "",
                                category = data["category"] as? String ?: "",
                                urgency = data["urgency"] as? String ?: "",
                                status = statusData?.get("current") as? String ?: "Open",
                                timestamp = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                                contactInfo = createdByData?.get("contactInfo") as? String ?: "",
                                hasAttachment = attachmentData?.get("hasFile") as? Boolean ?: false
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing complaint document", e)
                            null
                        }
                    }

                    lifecycleScope.launch {
                        _complaints.emit(complaintsList)
                    }
                }
            }
    }

    private fun loadAvailableUsers() {
        // Load users from all companies and roles for assignment
        firestore.collection("user_search_index")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading users", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val usersList = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            AssignableUser(
                                userId = data["userId"] as? String ?: "",
                                name = data["name"] as? String ?: "",
                                email = data["email"] as? String ?: "",
                                role = data["role"] as? String ?: "",
                                companyName = data["companyName"] as? String ?: "",
                                designation = data["designation"] as? String ?: "",
                                documentPath = data["documentPath"] as? String ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user document", e)
                            null
                        }
                    }

                    lifecycleScope.launch {
                        _availableUsers.emit(usersList)
                    }
                }
            }
    }

    private fun findBestAssigneeForComplaint(category: String, urgency: String): AssignableUser? {
        val users = _availableUsers.value
        if (users.isEmpty()) return null

        // Define category to role mapping
        val categoryRoleMapping = mapOf(
            "Technical" to listOf("Administrator", "Manager", "Team Lead"),
            "HR" to listOf("HR", "Manager"),
            "Administrative" to listOf("Administrator", "Manager"),
            "IT Support" to listOf("Administrator", "Team Lead"),
            "Finance" to listOf("Administrator", "Manager"),
            "General" to listOf("Manager", "Team Lead", "HR")
        )

        // Get preferred roles for this category
        val preferredRoles = categoryRoleMapping[category] ?: listOf("Manager", "Administrator")

        // Filter users by preferred roles
        val eligibleUsers = users.filter { user ->
            preferredRoles.contains(user.role)
        }

        if (eligibleUsers.isEmpty()) {
            // Fallback to any Manager or Administrator
            return users.firstOrNull { it.role in listOf("Manager", "Administrator") }
        }

        // For high urgency, prefer Administrators and Managers
        return if (urgency == "High" || urgency == "Critical") {
            eligibleUsers.firstOrNull { it.role in listOf("Administrator", "Manager") }
                ?: eligibleUsers.first()
        } else {
            eligibleUsers.first()
        }
    }

    private fun saveComplaint(complaintData: ComplaintData) {
        val user = auth.currentUser ?: run {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            triggerHapticFeedback(HapticType.ERROR)
            return
        }

        if (complaintData.title.isBlank() || complaintData.description.isBlank()) {
            Toast.makeText(this, "Please fill out required fields", Toast.LENGTH_SHORT).show()
            triggerHapticFeedback(HapticType.ERROR)
            return
        }

        // Check file size before proceeding
        if (complaintData.hasAttachment && selectedFileUri != null) {
            val fileSize = contentResolver.openFileDescriptor(selectedFileUri!!, "r")?.use { it.statSize } ?: 0L
            if (fileSize > 1 * 1024 * 1024) { // 1 MB
                Toast.makeText(this, "Attachment must be less than 1 MB", Toast.LENGTH_SHORT).show()
                triggerHapticFeedback(HapticType.ERROR)
                return
            }
        }

        triggerHapticFeedback(HapticType.SUCCESS)

        lifecycleScope.launch {
            try {
                val complaintId = java.util.UUID.randomUUID().toString()
                val timestamp = Timestamp.now()

                // Find best assignee for this complaint
                val assignee = findBestAssigneeForComplaint(complaintData.category, complaintData.urgency)

                // Upload attachment if exists
                var attachmentUrl: String? = null
                var attachmentFileName: String? = null
                var attachmentFileSize: Long? = null

                if (complaintData.hasAttachment && selectedFileUri != null) {
                    try {
                        val storageRef = storage.reference
                            .child("complaints")
                            .child(complaintId)
                            .child("attachments")
                            .child("attachment_${System.currentTimeMillis()}")

                        val uploadResult = storageRef.putFile(selectedFileUri!!).await()
                        attachmentUrl = storageRef.downloadUrl.await().toString()
                        attachmentFileName = uploadResult.metadata?.name
                        attachmentFileSize = uploadResult.metadata?.sizeBytes
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to upload attachment", e)
                        // Continue without attachment
                    }
                }

                // Get current user details for created by
                val currentUserData = getCurrentUserData()

                // Create complaint document structure
                val complaintData = hashMapOf(
                    "complaintId" to complaintId,
                    "title" to complaintData.title,
                    "description" to complaintData.description,
                    "category" to complaintData.category,
                    "urgency" to complaintData.urgency,

                    // Created by information
                    "createdBy" to mapOf(
                        "userId" to user.uid,
                        "name" to (currentUserData?.get("name") ?: "Anonymous User"),
                        "email" to (user.email ?: "anonymous@example.com"),
                        "contactInfo" to complaintData.contactInfo,
                        "companyName" to (currentUserData?.get("companyName") ?: "Unknown Company"),
                        "role" to (currentUserData?.get("role") ?: "User"),
                        "designation" to (currentUserData?.get("designation") ?: ""),
                        "documentPath" to (currentUserData?.get("documentPath") ?: "")
                    ),

                    // Assignment information
                    "assignedTo" to if (assignee != null) mapOf(
                        "userId" to assignee.userId,
                        "name" to assignee.name,
                        "email" to assignee.email,
                        "role" to assignee.role,
                        "companyName" to assignee.companyName,
                        "designation" to assignee.designation,
                        "documentPath" to assignee.documentPath,
                        "assignedAt" to timestamp,
                        "assignedBy" to "system_auto_assignment"
                    ) else null,

                    // Status tracking
                    "status" to mapOf(
                        "current" to "Open",
                        "history" to listOf(
                            mapOf(
                                "status" to "Open",
                                "changedAt" to timestamp,
                                "changedBy" to "system",
                                "reason" to "Complaint created"
                            )
                        )
                    ),

                    // Attachment information
                    "attachment" to if (complaintData.hasAttachment) mapOf(
                        "hasFile" to true,
                        "url" to attachmentUrl,
                        "fileName" to attachmentFileName,
                        "fileSize" to attachmentFileSize,
                        "uploadedAt" to timestamp
                    ) else mapOf(
                        "hasFile" to false
                    ),

                    // Timestamps
                    "createdAt" to timestamp,
                    "updatedAt" to timestamp,
                    "lastModified" to timestamp,

                    // Additional metadata
                    "isGlobal" to complaintData.isGlobal,
                    "priority" to calculatePriority(complaintData.urgency, complaintData.category),
                    "estimatedResolutionTime" to getEstimatedResolutionTime(complaintData.urgency),
                    "tags" to generateTags(complaintData.title, complaintData.description, complaintData.category),

                    // Search indexing
                    "searchTerms" to createSearchTerms(
                        complaintData.title,
                        complaintData.description,
                        complaintData.category,
                        complaintData.urgency
                    )
                )

                // Save to complaints collection
                firestore.collection("complaints")
                    .document(complaintId)
                    .set(complaintData)
                    .await()

                // Create complaint tracking entry
                if (assignee != null) {
                    createComplaintTrackingEntry(complaintId, assignee,
                        complaintData as Map<String, Any>
                    )
                }

                // Update user's complaint count
                updateUserComplaintStats(user.uid)

                selectedFileUri = null

                // Show notification
                //showComplaintSubmittedNotification(complaintData.title, complaintData.urgency, assignee?.name)

                runOnUiThread {
                    val assignmentMessage = if (assignee != null) {
                        "Complaint submitted and assigned to ${assignee.name} (${assignee.role})"
                    } else {
                        "Complaint submitted successfully!"
                    }
                    Toast.makeText(this@RegisterComplain, assignmentMessage, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                triggerHapticFeedback(HapticType.ERROR)
                Log.e(TAG, "Error saving complaint", e)
                runOnUiThread {
                    Toast.makeText(this@RegisterComplain, "Error saving complaint: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getCurrentUserData(): Map<String, Any>? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            val userDoc = firestore.collection("user_access_control")
                .document(userId)
                .get()
                .await()
            userDoc.data
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            null
        }
    }

    private suspend fun createComplaintTrackingEntry(
        complaintId: String,
        assignee: AssignableUser,
        complaintData: Map<String, Any>
    ) {
        try {
            val trackingData = mapOf(
                "complaintId" to complaintId,
                "assignedUserId" to assignee.userId,
                "assignedUserName" to assignee.name,
                "assignedUserRole" to assignee.role,
                "complaintTitle" to complaintData["title"],
                "complaintCategory" to complaintData["category"],
                "complaintUrgency" to complaintData["urgency"],
                "assignedAt" to Timestamp.now(),
                "status" to "Assigned",
                "isActive" to true
            )

            firestore.collection("complaint_assignments")
                .document(complaintId)
                .set(trackingData)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating complaint tracking entry", e)
        }
    }

    private suspend fun updateUserComplaintStats(userId: String) {
        try {
            val userDoc = firestore.collection("user_access_control").document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDoc)
                val currentStats = snapshot.data?.get("complaintStats") as? Map<*, *>
                val totalComplaints = (currentStats?.get("totalSubmitted") as? Long ?: 0) + 1

                val updatedStats = mapOf(
                    "totalSubmitted" to totalComplaints,
                    "lastSubmissionDate" to Timestamp.now()
                )

                transaction.update(userDoc, "complaintStats", updatedStats)
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user complaint stats", e)
        }
    }

    private fun calculatePriority(urgency: String, category: String): Int {
        val urgencyScore = when (urgency) {
            "Critical" -> 4
            "High" -> 3
            "Medium" -> 2
            "Low" -> 1
            else -> 1
        }

        val categoryScore = when (category) {
            "Technical", "IT Support" -> 1
            "Administrative", "Finance" -> 0
            else -> 0
        }

        return urgencyScore + categoryScore
    }

    private fun getEstimatedResolutionTime(urgency: String): String {
        return when (urgency) {
            "Critical" -> "4 hours"
            "High" -> "24 hours"
            "Medium" -> "3 days"
            "Low" -> "1 week"
            else -> "1 week"
        }
    }

    private fun generateTags(title: String, description: String, category: String): List<String> {
        val commonWords = setOf("the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "a", "an", "is", "are", "was", "were")
        val allText = "$title $description $category".lowercase()
        return allText.split("\\s+".toRegex())
            .filter { it.length > 3 && !commonWords.contains(it) }
            .distinct()
            .take(10)
    }

    private fun createSearchTerms(title: String, description: String, category: String, urgency: String): List<String> {
        return listOf(
            title.lowercase(),
            description.lowercase(),
            category.lowercase(),
            urgency.lowercase()
        ).flatMap { it.split("\\s+".toRegex()) }
            .filter { it.isNotEmpty() && it.length > 2 }
            .distinct()
    }

    private fun showComplaintSubmittedNotification(title: String, urgency: String, assigneeName: String?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val contentText = if (assigneeName != null) {
            "$title - Priority: $urgency - Assigned to: $assigneeName"
        } else {
            "$title - Priority: $urgency"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Complaint Submitted Successfully")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun triggerHapticFeedback(type: HapticType) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.VIBRATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = when (type) {
            HapticType.LIGHT -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            HapticType.MEDIUM -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            HapticType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1)
            HapticType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1)
        }
        vibrator.vibrate(effect)
    }

    private fun openFilePicker() {
        getContent.launch("*/*")
        triggerHapticFeedback(HapticType.LIGHT)
    }

    private fun deleteComplaint(complaintId: String) {
        triggerHapticFeedback(HapticType.MEDIUM)

        lifecycleScope.launch {
            try {
                // Delete complaint
                firestore.collection("complaints")
                    .document(complaintId)
                    .delete()
                    .await()

                // Delete assignment tracking
                firestore.collection("complaint_assignments")
                    .document(complaintId)
                    .delete()
                    .await()

                runOnUiThread {
                    Toast.makeText(this@RegisterComplain, "Complaint deleted", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                triggerHapticFeedback(HapticType.ERROR)
                Log.e(TAG, "Error deleting complaint", e)
                runOnUiThread {
                    Toast.makeText(
                        this@RegisterComplain,
                        "Error deleting complaint: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "complaint_form") {
            composable("complaint_form") {
                RegisterComplaintScreen(
                    onSaveClick = { complaintData -> saveComplaint(complaintData) },
                    onResetClick = { selectedFileUri = null },
                    onViewComplaintsClick = {
                        startActivity(Intent(this@RegisterComplain, ComplaintViewActivity::class.java))
                    },
                    onFilePickerClick = { openFilePicker() },
                    onHapticFeedback = { type -> triggerHapticFeedback(type) }
                )
            }
        }
    }
}

// Haptic feedback types
enum class HapticType {
    LIGHT, MEDIUM, SUCCESS, ERROR
}

// Data classes
data class ComplaintData(
    val title: String,
    val description: String,
    val category: String,
    val urgency: String,
    val contactInfo: String = "",
    val hasAttachment: Boolean = false,
    val isGlobal: Boolean = false
)

data class ComplaintWithId(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val urgency: String,
    val status: String,
    val timestamp: Long,
    val contactInfo: String,
    val hasAttachment: Boolean
)

data class AssignableUser(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val designation: String,
    val documentPath: String
)