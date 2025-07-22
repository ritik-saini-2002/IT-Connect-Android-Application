package com.example.ritik_2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.ProfileCompletionScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp

class ProfileCompletionActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String
    private var userCompanyName = mutableStateOf("")
    private var userRole = mutableStateOf("")
    private var sanitizedCompanyName = mutableStateOf("")
    private var isUpdating = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        userId = intent.getStringExtra("userId") ?: firebaseAuth.currentUser?.uid ?: ""

        if (userId.isEmpty()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        // Load user's company and role information
        loadUserHierarchyInfo()

        setContent {
            Ritik_2Theme {
                ProfileCompletionScreen(
                    userId = userId,
                    //isUpdating = isUpdating.value,
                    onProfileUpdateClick = { updatedData, newPassword, imageUri ->
                        updateUserProfile(updatedData, newPassword, imageUri)
                    },
                    onLogoutClick = { logout() }
                )
            }
        }
    }

    private fun loadUserHierarchyInfo() {
        // First check user_access_control to get hierarchy info
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userCompanyName.value = document.getString("companyName") ?: ""
                    userRole.value = document.getString("role") ?: ""
                    sanitizedCompanyName.value = document.getString("sanitizedCompanyName") ?: ""

                    Log.d("ProfileCompletion", "User hierarchy loaded: ${userCompanyName.value}/${userRole.value}")
                } else {
                    Toast.makeText(this, "User access control data not found", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading user data: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileCompletion", "Failed to load user hierarchy", exception)
                navigateToLogin()
            }
    }

    private fun updateUserProfile(
        updatedData: Map<String, Any>,
        newPassword: String?,
        imageUri: Uri?
    ) {
        if (isUpdating.value) {
            Toast.makeText(this, "Update in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (userCompanyName.value.isEmpty() || userRole.value.isEmpty() || sanitizedCompanyName.value.isEmpty()) {
            Toast.makeText(this, "User hierarchy information not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating.value = true

        // Update password if provided
        if (!newPassword.isNullOrEmpty()) {
            updatePassword(newPassword) { passwordSuccess ->
                if (passwordSuccess) {
                    // Continue with profile update
                    if (imageUri != null) {
                        uploadImageAndUpdateProfile(imageUri, updatedData)
                    } else {
                        updateFirestoreData(updatedData)
                    }
                } else {
                    isUpdating.value = false
                }
            }
        } else {
            // No password update, proceed with profile update
            if (imageUri != null) {
                uploadImageAndUpdateProfile(imageUri, updatedData)
            } else {
                updateFirestoreData(updatedData)
            }
        }
    }

    private fun updatePassword(newPassword: String, callback: (Boolean) -> Unit) {
        firebaseAuth.currentUser?.updatePassword(newPassword)
            ?.addOnSuccessListener {
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                callback(true)
            }
            ?.addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to update password: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileCompletion", "Password update failed", exception)
                callback(false)
            }
    }

    private fun uploadImageAndUpdateProfile(imageUri: Uri, updatedData: Map<String, Any>) {
        val storageRef = storage.reference.child("users/${sanitizedCompanyName.value}/${userRole.value}/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firebase Auth profile
                    firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
                        photoUri = downloadUri
                    })?.addOnCompleteListener { authUpdateTask ->
                        if (authUpdateTask.isSuccessful) {
                            Log.d("ProfileCompletion", "Firebase Auth profile updated")
                        }
                    }

                    // Add image URL to data and update Firestore
                    val dataWithImage = updatedData.toMutableMap()
                    dataWithImage["profile.imageUrl"] = downloadUri.toString()
                    dataWithImage["lastUpdated"] = Timestamp.now()

                    updateFirestoreData(dataWithImage)
                }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to get download URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.e("ProfileCompletion", "Download URL failed", exception)
                        isUpdating.value = false
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Image upload failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileCompletion", "Image upload failed", exception)
                isUpdating.value = false
            }
    }

    private fun updateFirestoreData(updatedData: Map<String, Any>) {
        val batch = firestore.batch()

        // Prepare data with timestamp
        val finalData = updatedData.toMutableMap()
        finalData["lastUpdated"] = Timestamp.now()

        // 1. Update main user document in hierarchical structure
        val userDocRef = firestore
            .collection("users")
            .document(sanitizedCompanyName.value)
            .collection(userRole.value)
            .document(userId)

        batch.update(userDocRef, finalData)

        // 2. Update user_access_control (sync basic info)
        val accessControlRef = firestore.collection("user_access_control").document(userId)
        val accessControlUpdates = mutableMapOf<String, Any>()

        // Sync relevant fields to access control
        finalData["name"]?.let { accessControlUpdates["name"] = it }
        finalData["email"]?.let { accessControlUpdates["email"] = it }
        finalData["designation"]?.let { accessControlUpdates["designation"] = it }
        finalData["profile.phoneNumber"]?.let { accessControlUpdates["phoneNumber"] = it }
        accessControlUpdates["lastAccess"] = Timestamp.now()

        if (accessControlUpdates.isNotEmpty()) {
            batch.update(accessControlRef, accessControlUpdates)
        }

        // 3. Update user_search_index (for search functionality)
        val searchIndexRef = firestore.collection("user_search_index").document(userId)
        val searchIndexUpdates = mutableMapOf<String, Any>()

        finalData["name"]?.let { name ->
            searchIndexUpdates["name"] = name.toString().lowercase()
            searchIndexUpdates["searchTerms"] = listOf(
                name.toString().lowercase(),
                finalData["email"]?.toString()?.lowercase() ?: "",
                userCompanyName.value.lowercase(),
                userRole.value.lowercase(),
                finalData["designation"]?.toString()?.lowercase() ?: ""
            ).filter { it.isNotEmpty() }
        }
        finalData["email"]?.let { searchIndexUpdates["email"] = it.toString().lowercase() }
        finalData["designation"]?.let { searchIndexUpdates["designation"] = it }

        if (searchIndexUpdates.isNotEmpty()) {
            batch.update(searchIndexRef, searchIndexUpdates)
        }

        // Execute batch update
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_LONG).show()
                Log.d("ProfileCompletion", "Profile updated at path: users/${sanitizedCompanyName.value}/${userRole.value}/$userId")

                // Navigate to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error updating profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileCompletion", "Profile update failed", exception)
            }
            .addOnCompleteListener {
                isUpdating.value = false
            }
    }

    private fun logout() {
        firebaseAuth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Show confirmation dialog or prevent going back
        Toast.makeText(this, "Please complete your profile or logout", Toast.LENGTH_SHORT).show()
        // Uncomment below if you want to allow back navigation
        // super.onBackPressed()
    }

    companion object {
        const val TAG = "ProfileCompletion"

        // Helper function to create intent with userId
        fun createIntent(context: android.content.Context, userId: String): Intent {
            return Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra("userId", userId)
            }
        }
    }
}