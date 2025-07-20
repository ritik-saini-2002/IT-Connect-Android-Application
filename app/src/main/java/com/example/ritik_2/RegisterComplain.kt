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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ritik_2.ui.theme.RegisterComplaintScreen
import com.example.ritik_2.ui.theme.ui.theme.ComplaintAppTheme
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
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadComplaints() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("complaints")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading complaints: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val complaintsList = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data
                        if (data != null) {
                            ComplaintWithId(
                                id = doc.id,
                                title = data["title"] as? String ?: "",
                                description = data["description"] as? String ?: "",
                                category = data["category"] as? String ?: "",
                                urgency = data["urgency"] as? String ?: "",
                                status = data["status"] as? String ?: "Open",
                                timestamp = data["timestamp"] as? Long ?: 0L,
                                contactInfo = data["contactInfo"] as? String ?: "",
                                hasAttachment = data["hasAttachment"] as? Boolean ?: false
                            )
                        } else null
                    }

                    lifecycleScope.launch {
                        _complaints.emit(complaintsList)
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

        // Success haptic feedback
        triggerHapticFeedback(HapticType.SUCCESS)

        lifecycleScope.launch {
            try {
                val complaintId = java.util.UUID.randomUUID().toString()
                var attachmentUrl: String? = null

                if (complaintData.hasAttachment && selectedFileUri != null) {
                    val storageRef = storage.reference
                        .child("users")
                        .child(user.uid)
                        .child("attachments")
                        .child(complaintId)

                    attachmentUrl = try {
                        storageRef.putFile(selectedFileUri!!).await()
                        storageRef.downloadUrl.await().toString()
                    } catch (e: Exception) { null }
                }

                val data = hashMapOf(
                    "title" to complaintData.title,
                    "description" to complaintData.description,
                    "category" to complaintData.category,
                    "urgency" to complaintData.urgency,
                    "status" to "Open",
                    "timestamp" to System.currentTimeMillis(),
                    "userId" to user.uid,
                    "userEmail" to (user.email ?: "Anonymous"),
                    "contactInfo" to complaintData.contactInfo,
                    "hasAttachment" to complaintData.hasAttachment,
                    "attachmentUrl" to attachmentUrl,
                    "isGlobal" to complaintData.isGlobal
                )

                if (complaintData.isGlobal) {
                    firestore.collection("all_complaints")
                        .document(complaintId)
                        .set(data)
                        .await()
                } else {
                    firestore.collection("users")
                        .document(user.uid)
                        .collection("complaints")
                        .document(complaintId)
                        .set(data)
                        .await()
                }

                selectedFileUri = null

                // Show notification
                showComplaintSubmittedNotification(complaintData.title, complaintData.urgency)

                runOnUiThread {
                    Toast.makeText(this@RegisterComplain, "Complaint submitted successfully!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                triggerHapticFeedback(HapticType.ERROR)
                runOnUiThread {
                    Toast.makeText(this@RegisterComplain, "Error saving complaint: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showComplaintSubmittedNotification(title: String, urgency: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Complaint Submitted Successfully")
            .setContentText("$title - Priority: $urgency")
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
        val userId = auth.currentUser?.uid ?: return
        triggerHapticFeedback(HapticType.MEDIUM)

        lifecycleScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("complaints")
                    .document(complaintId)
                    .delete()
                    .await()

                firestore.collection("all_complaints")
                    .document(complaintId)
                    .delete()
                    .await()

                runOnUiThread {
                    Toast.makeText(this@RegisterComplain, "Complaint deleted", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                triggerHapticFeedback(HapticType.ERROR)
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
                    onViewComplaintsClick = {startActivity(Intent(this@RegisterComplain, ComplaintViewActivity::class.java))},
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