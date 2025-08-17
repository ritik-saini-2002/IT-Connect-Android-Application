package com.example.ritik_2.complaint.complaintregistration

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.example.ritik_2.complaint.viewcomplaint.ComplaintViewActivity
import com.example.ritik_2.theme.ComplaintAppTheme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

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

    private val _availableDepartments = MutableStateFlow<List<DepartmentInfo>>(emptyList())
    val availableDepartments: StateFlow<List<DepartmentInfo>> = _availableDepartments

    private val _currentUserData = MutableStateFlow<UserData?>(null)
    val currentUserData: StateFlow<UserData?> = _currentUserData

    // Notification permission launcher
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    companion object {
        private const val CHANNEL_ID = "complaint_notifications"
        private const val GLOBAL_CHANNEL_ID = "global_complaint_notifications"
        private const val NOTIFICATION_PERMISSION_CODE = 100
        private const val TAG = "RegisterComplaint"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup notifications
        setupNotificationChannels()
        requestNotificationPermissionIfNeeded()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            signInAnonymously()
        } else {
            loadCurrentUserData()
            loadComplaints()
            loadAvailableDepartments()
        }

        setContent {
            ComplaintAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    private fun setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Regular complaint notifications
            val regularChannel = NotificationChannel(
                CHANNEL_ID,
                "Complaint Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for complaint submissions and updates"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // Global complaint notifications
            val globalChannel = NotificationChannel(
                GLOBAL_CHANNEL_ID,
                "Global Complaint Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for global complaints visible to all users"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
            }

            notificationManager.createNotificationChannel(regularChannel)
            notificationManager.createNotificationChannel(globalChannel)
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
                loadCurrentUserData()
                loadComplaints()
                loadAvailableDepartments()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCurrentUserData() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("user_access_control")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading user data", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val data = snapshot.data ?: return@addSnapshotListener
                        val userData = UserData(
                            userId = data["userId"] as? String ?: userId,
                            name = data["name"] as? String ?: "Unknown User",
                            email = data["email"] as? String ?: "unknown@example.com",
                            companyName = data["companyName"] as? String ?: "Unknown Company",
                            sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
                            department = data["department"] as? String ?: "General",
                            sanitizedDepartment = data["sanitizedDepartment"] as? String ?: "general",
                            role = data["role"] as? String ?: "Employee",
                            documentPath = data["documentPath"] as? String ?: "",
                            phoneNumber = "", // Will be loaded from profile if needed
                            designation = "" // Will be loaded from profile if needed
                        )

                        lifecycleScope.launch {
                            _currentUserData.emit(userData)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user data", e)
                    }
                }
            }
    }

    private fun loadComplaints() {
        val userId = auth.currentUser?.uid ?: return

        // Load from the flat structure first (more reliable)
        firestore.collection("all_complaints")
            .whereEqualTo("createdBy.userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading complaints from flat structure", e)
                    // Fallback to hierarchical structure
                    loadComplaintsFromHierarchical()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val complaintsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val createdByData = data["createdBy"] as? Map<*, *>

                            ComplaintWithId(
                                id = doc.id,
                                title = data["title"] as? String ?: "",
                                description = "", // Will be loaded from hierarchical path if needed
                                department = data["department"] as? String ?: "",
                                urgency = data["urgency"] as? String ?: "",
                                status = data["status"] as? String ?: "Open",
                                timestamp = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                                contactInfo = "", // Will be loaded from hierarchical path if needed
                                hasAttachment = false // Will be loaded from hierarchical path if needed
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

    private fun loadComplaintsFromHierarchical() {
        // Your existing loadComplaints logic here as fallback
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("complaints")
            .whereEqualTo("createdBy.userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                // ... existing implementation
            }
    }
    private fun loadAvailableDepartments() {
        val currentUser = _currentUserData.value
        if (currentUser?.sanitizedCompanyName.isNullOrEmpty()) {
            // Wait for user data to load
            lifecycleScope.launch {
                _currentUserData.collect { userData ->
                    if (userData != null && userData.sanitizedCompanyName.isNotEmpty()) {
                        loadDepartmentsForCompany(userData.sanitizedCompanyName)
                    }
                }
            }
            return
        }

        loadDepartmentsForCompany(currentUser!!.sanitizedCompanyName)
    }

    private fun loadDepartmentsForCompany(sanitizedCompanyName: String) {
        firestore.collection("companies_metadata")
            .document(sanitizedCompanyName)
            .collection("departments_metadata")
            .whereEqualTo("activeUsers", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading departments", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val departmentsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            DepartmentInfo(
                                departmentId = doc.id,
                                departmentName = data["departmentName"] as? String ?: "",
                                companyName = data["companyName"] as? String ?: "",
                                sanitizedName = data["sanitizedName"] as? String ?: "",
                                userCount = (data["userCount"] as? Long)?.toInt() ?: 0,
                                availableRoles = (data["availableRoles"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing department document", e)
                            null
                        }
                    }

                    lifecycleScope.launch {
                        _availableDepartments.emit(departmentsList)
                    }
                }
            }
    }

    private fun saveComplaint(complaintData: ComplaintData) {
        val user = auth.currentUser ?: run {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            triggerHapticFeedback(HapticType.ERROR)
            return
        }

        val currentUser = _currentUserData.value ?: run {
            Toast.makeText(this, "User data not loaded. Please wait and try again.", Toast.LENGTH_SHORT).show()
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
                val complaintId = UUID.randomUUID().toString()
                val timestamp = Timestamp.now()
                val sanitizedCompanyName = currentUser.sanitizedCompanyName

                // Map category to department
                val categoryToDepartmentMapping = mapOf(
                    "Technical" to "Technical",
                    "HR" to "Human Resources",
                    "Administrative" to "Administration",
                    "IT Support" to "IT Support",
                    "Finance" to "Finance",
                    "General" to "General"
                )

                val departmentName = categoryToDepartmentMapping[complaintData.department] ?: complaintData.department

                // Find assigned department from available departments
                val assignedDepartment = _availableDepartments.value.find { dept ->
                    dept.departmentName.equals(departmentName, ignoreCase = true) ||
                            dept.departmentName.contains(departmentName, ignoreCase = true) ||
                            departmentName.contains(dept.departmentName, ignoreCase = true)
                }

                // Upload attachment if exists
                var attachmentUrl: String? = null
                var attachmentFileName: String? = null
                var attachmentFileSize: Long? = null

                if (complaintData.hasAttachment && selectedFileUri != null) {
                    try {
                        val storageRef = storage.reference
                            .child("complaints")
                            .child(sanitizedCompanyName)
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

                // Create batch for atomic operations
                val batch = firestore.batch()

                // FIXED: Correct document path construction
                val complaintDocPath = if (complaintData.isGlobal) {
                    // Global complaints: collection/document/collection/document (even number of segments)
                    "complaints/$sanitizedCompanyName/global_complaints/$complaintId"
                } else {
                    // Department complaints: collection/document/collection/document (even number of segments)
                    "complaints/$sanitizedCompanyName/department_complaints/$complaintId"
                }

                val complaintDocRef = firestore.document(complaintDocPath)

                // Create comprehensive complaint document
                val complaintDataMap = hashMapOf(
                    "complaintId" to complaintId,
                    "title" to complaintData.title,
                    "description" to complaintData.description,
                    "department" to departmentName, // Store mapped department name
                    "originalCategory" to complaintData.department, // Store original category selection
                    "urgency" to complaintData.urgency,

                    // Path information
                    "documentPath" to complaintDocPath,
                    "companyName" to currentUser.companyName,
                    "sanitizedCompanyName" to sanitizedCompanyName,
                    "isGlobal" to complaintData.isGlobal,

                    // Created by information
                    "createdBy" to mapOf(
                        "userId" to user.uid,
                        "name" to currentUser.name,
                        "email" to currentUser.email,
                        "contactInfo" to complaintData.contactInfo,
                        "companyName" to currentUser.companyName,
                        "sanitizedCompanyName" to sanitizedCompanyName,
                        "department" to currentUser.department,
                        "sanitizedDepartment" to currentUser.sanitizedDepartment,
                        "role" to currentUser.role,
                        "designation" to currentUser.designation,
                        "userDocumentPath" to currentUser.documentPath
                    ),

                    // Assigned to department information
                    "assignedToDepartment" to if (assignedDepartment != null) mapOf(
                        "departmentId" to assignedDepartment.departmentId,
                        "departmentName" to assignedDepartment.departmentName,
                        "sanitizedDepartmentName" to assignedDepartment.sanitizedName,
                        "companyName" to assignedDepartment.companyName,
                        "assignedAt" to timestamp,
                        "assignedBy" to "system_auto_assignment",
                        "availableRoles" to assignedDepartment.availableRoles,
                        "userCount" to assignedDepartment.userCount
                    ) else mapOf(
                        "departmentName" to departmentName,
                        "assignedAt" to timestamp,
                        "assignedBy" to "system_category_mapping"
                    ),

                    // Status tracking
                    "status" to mapOf(
                        "current" to "Open",
                        "history" to listOf(
                            mapOf(
                                "status" to "Open",
                                "changedAt" to timestamp,
                                "changedBy" to "system",
                                "reason" to "Complaint created and assigned to department"
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
                    "priority" to calculatePriority(complaintData.urgency, departmentName),
                    "estimatedResolutionTime" to getEstimatedResolutionTime(complaintData.urgency),
                    "tags" to generateTags(complaintData.title, complaintData.description, departmentName),

                    // Search indexing
                    "searchTerms" to createSearchTerms(
                        complaintData.title,
                        complaintData.description,
                        departmentName,
                        complaintData.urgency,
                        assignedDepartment?.departmentName ?: departmentName
                    )
                )

                batch.set(complaintDocRef, complaintDataMap)

                // Create search index with FIXED path
                val searchIndexRef = firestore.collection("complaint_search_index").document(complaintId)
                val searchIndexData = mapOf(
                    "complaintId" to complaintId,
                    "title" to complaintData.title.lowercase(),
                    "department" to departmentName.lowercase(),
                    "originalCategory" to complaintData.department.lowercase(),
                    "urgency" to complaintData.urgency.lowercase(),
                    "companyName" to currentUser.companyName,
                    "sanitizedCompanyName" to sanitizedCompanyName,
                    "userDepartment" to currentUser.department,
                    "sanitizedUserDepartment" to currentUser.sanitizedDepartment,
                    "assignedDepartment" to (assignedDepartment?.departmentName ?: departmentName),
                    "createdBy" to currentUser.name.lowercase(),
                    "status" to "open",
                    "isGlobal" to complaintData.isGlobal,
                    "documentPath" to complaintDocPath,
                    "createdAt" to timestamp,
                    "searchTerms" to createSearchTerms(
                        complaintData.title,
                        complaintData.description,
                        departmentName,
                        complaintData.urgency,
                        assignedDepartment?.departmentName ?: departmentName
                    )
                )
                batch.set(searchIndexRef, searchIndexData)

                // Create flat structure for easier querying
                val flatComplaintRef = firestore.collection("all_complaints").document(complaintId)
                val flatComplaintData = mapOf(
                    "complaintId" to complaintId,
                    "title" to complaintData.title,
                    "department" to departmentName,
                    "originalCategory" to complaintData.department,
                    "urgency" to complaintData.urgency,
                    "status" to "Open",
                    "companyName" to currentUser.companyName,
                    "sanitizedCompanyName" to sanitizedCompanyName,
                    "userDepartment" to currentUser.department,
                    "sanitizedUserDepartment" to currentUser.sanitizedDepartment,
                    "createdBy" to mapOf(
                        "userId" to user.uid,
                        "name" to currentUser.name
                    ),
                    "isGlobal" to complaintData.isGlobal,
                    "hierarchicalPath" to complaintDocPath,
                    "createdAt" to timestamp
                )
                batch.set(flatComplaintRef, flatComplaintData)

                // Update statistics (optional - with error handling)
                try {
                    if (assignedDepartment != null) {
                        val deptStatsRef = firestore
                            .collection("companies_metadata")
                            .document(sanitizedCompanyName)
                            .collection("departments_metadata")
                            .document(assignedDepartment.sanitizedName)

                        val deptStatsUpdate = mapOf(
                            "totalComplaints" to FieldValue.increment(1),
                            "openComplaints" to FieldValue.increment(1),
                            "lastComplaintDate" to timestamp,
                            "lastUpdated" to timestamp
                        )
                        batch.update(deptStatsRef, deptStatsUpdate)
                    }

                    // Update company stats
                    val companyStatsRef = firestore.collection("companies_metadata").document(sanitizedCompanyName)
                    val companyStatsUpdate = mapOf(
                        "totalComplaints" to FieldValue.increment(1),
                        "openComplaints" to FieldValue.increment(1),
                        "lastComplaintDate" to timestamp,
                        "lastUpdated" to timestamp
                    )
                    batch.update(companyStatsRef, companyStatsUpdate)

                    // Update user stats
                    val userStatsRef = firestore.collection("user_access_control").document(user.uid)
                    val userStatsUpdate = mapOf(
                        "complaintStats.totalSubmitted" to FieldValue.increment(1),
                        "complaintStats.lastSubmissionDate" to timestamp
                    )
                    batch.update(userStatsRef, userStatsUpdate)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating statistics, continuing with complaint creation", e)
                }

                // Execute batch operation
                batch.commit().await()

                selectedFileUri = null

                // Send notifications
                if (complaintData.isGlobal) {
                    sendGlobalComplaintNotification(
                        complaintData.title,
                        complaintData.urgency,
                        currentUser.name,
                        sanitizedCompanyName
                    )
                } else {
                    sendDepartmentComplaintNotification(
                        complaintData.title,
                        complaintData.urgency,
                        assignedDepartment?.departmentName ?: departmentName,
                        currentUser.name
                    )
                }

                runOnUiThread {
                    val assignmentMessage = if (assignedDepartment != null) {
                        if (complaintData.isGlobal) {
                            "Global complaint submitted successfully! Visible to all company users."
                        } else {
                            "Complaint submitted and assigned to ${assignedDepartment.departmentName} department"
                        }
                    } else {
                        "Complaint submitted to $departmentName department successfully!"
                    }
                    Toast.makeText(this@RegisterComplain, assignmentMessage, Toast.LENGTH_LONG).show()

                    Log.d(TAG, "Complaint saved successfully with path: $complaintDocPath")
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

    private fun sendGlobalComplaintNotification(
        title: String,
        urgency: String,
        createdBy: String,
        companyName: String
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        // Send notification to all users in company for global complaints
        lifecycleScope.launch {
            try {
                val contentText = "Global complaint: $title - Priority: $urgency - By: $createdBy"

                val notification = NotificationCompat.Builder(this@RegisterComplain, GLOBAL_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_dialog_alert)
                    .setContentTitle("New Global Complaint")
                    .setContentText(contentText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setVibrate(longArrayOf(0, 300, 100, 300, 100, 300))
                    .build()

                with(NotificationManagerCompat.from(this@RegisterComplain)) {
                    notify("global_complaint_${System.currentTimeMillis()}".hashCode(), notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending global notification", e)
            }
        }
    }

    private fun sendDepartmentComplaintNotification(
        title: String,
        urgency: String,
        departmentName: String,
        createdBy: String
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val contentText = "$title - Priority: $urgency - Assigned to: $departmentName - By: $createdBy"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Complaint Assigned to Department")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify("dept_complaint_${System.currentTimeMillis()}".hashCode(), notification)
        }
    }

    private fun calculatePriority(urgency: String, department: String): Int {
        val urgencyScore = when (urgency) {
            "Critical" -> 4
            "High" -> 3
            "Medium" -> 2
            "Low" -> 1
            else -> 1
        }

        val departmentScore = when (department) {
            "Technical", "IT Support" -> 1
            "Administrative", "Finance" -> 0
            else -> 0
        }

        return urgencyScore + departmentScore
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

    private fun generateTags(title: String, description: String, department: String): List<String> {
        val commonWords = setOf("the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "a", "an", "is", "are", "was", "were")
        val allText = "$title $description $department".lowercase()
        return allText.split("\\s+".toRegex())
            .filter { it.length > 3 && !commonWords.contains(it) }
            .distinct()
            .take(10)
    }

    private fun createSearchTerms(
        title: String,
        description: String,
        department: String,
        urgency: String,
        departmentName: String
    ): List<String> {
        return listOf(
            title.lowercase(),
            description.lowercase(),
            department.lowercase(),
            urgency.lowercase(),
            departmentName.lowercase()
        ).flatMap { it.split("\\s+".toRegex()) }
            .filter { it.isNotEmpty() && it.length > 2 }
            .distinct()
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
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
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
                val batch = firestore.batch()

                // Delete from hierarchical complaints structure
                val complaintSearchDoc = firestore.collection("complaint_search_index")
                    .document(complaintId)
                    .get()
                    .await()

                if (complaintSearchDoc.exists()) {
                    val documentPath = complaintSearchDoc.getString("documentPath")
                    if (!documentPath.isNullOrEmpty()) {
                        val complaintDocRef = firestore.document(documentPath)
                        batch.delete(complaintDocRef)
                    }
                }

                // Delete from search index
                batch.delete(firestore.collection("complaint_search_index").document(complaintId))

                // Execute batch operation
                batch.commit().await()

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
                    onHapticFeedback = { type -> triggerHapticFeedback(type) },
                    availableDepartments = _availableDepartments.value,
                    currentUserData = _currentUserData.value
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
    val department: String,
    val urgency: String,
    val contactInfo: String = "",
    val hasAttachment: Boolean = false,
    val isGlobal: Boolean = false
)

data class ComplaintWithId(
    val id: String,
    val title: String,
    val description: String,
    val department: String,
    val urgency: String,
    val status: String,
    val timestamp: Long,
    val contactInfo: String,
    val hasAttachment: Boolean
)

data class DepartmentInfo(
    val departmentId: String,
    val departmentName: String,
    val companyName: String,
    val sanitizedName: String,
    val userCount: Int,
    val availableRoles: List<String>
)

data class UserData(
    val userId: String,
    val name: String,
    val email: String,
    val companyName: String,
    val sanitizedCompanyName: String,
    val department: String,
    val sanitizedDepartment: String,
    val role: String,
    val documentPath: String,
    val phoneNumber: String,
    val designation: String
)