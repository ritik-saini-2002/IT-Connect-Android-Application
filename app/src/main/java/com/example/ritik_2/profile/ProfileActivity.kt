package com.example.ritik_2.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : ComponentActivity() {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var profileImageUri by mutableStateOf<Uri?>(null)
    private var name by mutableStateOf("Loading...")
    private var email by mutableStateOf("")
    private var phoneNumber by mutableStateOf("")
    private var designation by mutableStateOf("")
    private var companyName by mutableStateOf("")
    private var role by mutableStateOf("")
    private var experience by mutableStateOf(0)
    private var completedProjects by mutableStateOf(0)
    private var activeProjects by mutableStateOf(0)
    private var isLoading by mutableStateOf(true)

    private var userDocumentPath by mutableStateOf<String?>(null)
    private var sanitizedCompanyName by mutableStateOf<String?>(null)

    private val userId = firebaseAuth.currentUser?.uid

    companion object {
        const val TAG = "ProfileActivity"

        fun createIntent(context: Context, userId: String): Intent {
            return Intent(context, ProfileActivity::class.java).apply {
                putExtra("userId", userId)
            }
        }
    }

    // Image picker
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            profileImageUri = it
            uploadProfilePicture(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "🚀 ProfileActivity started")

        if (userId == null) {
            Log.e(TAG, "❌ No authenticated user found")
            navigateToLogin()
            return
        }

        loadUserProfile()

        setContent {
            Ritik_2Theme {
                ProfileScreen(
                    profileImageUrl = profileImageUri,
                    name = name,
                    email = email,
                    phoneNumber = phoneNumber,
                    designation = designation,
                    companyName = companyName,
                    role = role,
                    experience = experience,
                    completedProjects = completedProjects,
                    activeProjects = activeProjects,
                    isLoading = isLoading,
                    onLogoutClick = { logoutUser() },
                    onEditClick = { field, newValue -> updateUserData(field, newValue) },
                    onChangeProfilePic = { imagePicker.launch("image/*") },
                    onBackClick = { finish() }
                )
            }
        }
    }

    private fun loadUserProfile() {
        Log.d(TAG, "📊 Loading user profile for: $userId")
        isLoading = true

        userId?.let { uid ->
            // First, get user access control to find the correct document path
            firestore.collection("user_access_control").document(uid).get()
                .addOnSuccessListener { accessDocument ->
                    if (accessDocument.exists()) {
                        userDocumentPath = accessDocument.getString("documentPath")
                        role = accessDocument.getString("role") ?: ""
                        val isActive = accessDocument.getBoolean("isActive") ?: false

                        Log.d(TAG, "📄 Access control found - Role: $role, Path: $userDocumentPath, Active: $isActive")

                        if (!isActive) {
                            showToast("Your account is deactivated")
                            logoutUser()
                            return@addOnSuccessListener
                        }

                        if (userDocumentPath.isNullOrEmpty()) {
                            showToast("Invalid user configuration")
                            return@addOnSuccessListener
                        }

                        // Now get the actual user document
                        firestore.document(userDocumentPath!!).get()
                            .addOnSuccessListener { userDocument ->
                                if (userDocument.exists()) {
                                    loadUserDataFromDocument(userDocument.data)
                                } else {
                                    Log.e(TAG, "❌ User document not found at path: $userDocumentPath")
                                    showToast("User profile not found")
                                }
                                isLoading = false
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "❌ Error fetching user document", exception)
                                showToast("Failed to load profile data: ${exception.message}")
                                isLoading = false
                            }
                    } else {
                        Log.e(TAG, "❌ User access control not found")
                        showToast("User access not found")
                        isLoading = false
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Error fetching access control", exception)
                    showToast("Failed to load user access: ${exception.message}")
                    isLoading = false
                }
        }
    }

    private fun loadUserDataFromDocument(userData: Map<String, Any>?) {
        Log.d(TAG, "📋 Loading user data from document")

        if (userData == null) {
            showToast("Invalid user data")
            return
        }

        try {
            // Basic user info
            name = userData["name"]?.toString() ?: "Unknown"
            email = userData["email"]?.toString() ?: ""
            designation = userData["designation"]?.toString() ?: ""
            companyName = userData["companyName"]?.toString() ?: ""
            sanitizedCompanyName = userData["sanitizedCompanyName"]?.toString()

            // Profile nested object
            val profile = userData["profile"] as? Map<String, Any>
            if (profile != null) {
                phoneNumber = profile["phoneNumber"]?.toString() ?: ""
                val imageUrl = profile["imageUrl"]?.toString()
                profileImageUri = if (!imageUrl.isNullOrEmpty()) {
                    try {
                        Uri.parse(imageUrl)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to parse image URL", e)
                        null
                    }
                } else null
            }

            // Work stats nested object
            val workStats = userData["workStats"] as? Map<String, Any>
            if (workStats != null) {
                experience = (workStats["experience"] as? Number)?.toInt() ?: 0
                completedProjects = (workStats["completedProjects"] as? Number)?.toInt() ?: 0
                activeProjects = (workStats["activeProjects"] as? Number)?.toInt() ?: 0
            }

            Log.d(TAG, "✅ User profile loaded successfully: $name")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing user data", e)
            showToast("Error parsing profile data")
        }
    }

    private fun updateUserData(field: String, newValue: String) {
        Log.d(TAG, "📝 Updating field: $field with value: $newValue")

        if (userDocumentPath.isNullOrEmpty()) {
            showToast("Cannot update profile - invalid configuration")
            return
        }

        userId?.let { uid ->
            val updateData = when (field) {
                "name" -> mapOf("name" to newValue)
                "email" -> mapOf("email" to newValue)
                "designation" -> mapOf("designation" to newValue)
                "phoneNumber" -> mapOf("profile.phoneNumber" to newValue)
                "experience" -> {
                    val intValue = newValue.toIntOrNull() ?: 0
                    mapOf("workStats.experience" to intValue)
                }
                "completedProjects" -> {
                    val intValue = newValue.toIntOrNull() ?: 0
                    mapOf("workStats.completedProjects" to intValue)
                }
                "activeProjects" -> {
                    val intValue = newValue.toIntOrNull() ?: 0
                    mapOf("workStats.activeProjects" to intValue)
                }
                else -> {
                    showToast("Unknown field: $field")
                    return
                }
            }

            // Update main user document
            firestore.document(userDocumentPath!!).update(updateData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Updated $field successfully")

                    // Update local state
                    when (field) {
                        "name" -> name = newValue
                        "email" -> {
                            email = newValue
                            // Also update in user_access_control and search index
                            updateEmailInAllDocuments(uid, newValue)
                        }
                        "designation" -> designation = newValue
                        "phoneNumber" -> phoneNumber = newValue
                        "experience" -> experience = newValue.toIntOrNull() ?: 0
                        "completedProjects" -> completedProjects = newValue.toIntOrNull() ?: 0
                        "activeProjects" -> activeProjects = newValue.toIntOrNull() ?: 0
                    }

                    showToast("$field updated successfully!")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to update $field", exception)
                    showToast("Failed to update $field: ${exception.message}")
                }
        }
    }

    private fun updateEmailInAllDocuments(userId: String, newEmail: String) {
        Log.d(TAG, "📧 Updating email in all documents")

        // Update in user_access_control
        firestore.collection("user_access_control").document(userId)
            .update("email", newEmail)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Email updated in access control")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Failed to update email in access control", e)
            }

        // Update in user_search_index
        firestore.collection("user_search_index").document(userId)
            .update(
                mapOf(
                    "email" to newEmail.lowercase(),
                    "searchTerms" to listOf(
                        name.lowercase(),
                        newEmail.lowercase(),
                        companyName.lowercase(),
                        role.lowercase(),
                        designation.lowercase()
                    ).filter { it.isNotEmpty() }
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ Email updated in search index")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Failed to update email in search index", e)
            }
    }

    private fun uploadProfilePicture(imageUri: Uri) {
        Log.d(TAG, "📸 Uploading profile picture")

        if (sanitizedCompanyName.isNullOrEmpty() || role.isEmpty() || userId.isNullOrEmpty()) {
            showToast("Cannot upload image - missing configuration")
            return
        }

        showToast("Uploading image...")

        // Use the same storage path structure as RegistrationActivity
        val storageRef = storage.reference.child("users/$sanitizedCompanyName/$role/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Image uploaded successfully")
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Log.d(TAG, "✅ Download URL obtained: $downloadUri")
                    updateUserProfilePicture(downloadUri)
                }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Failed to get download URL", exception)
                        showToast("Failed to get image URL: ${exception.message}")
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to upload image", exception)
                showToast("Failed to upload image: ${exception.message}")
            }
    }

    private fun updateUserProfilePicture(photoUrl: Uri) {
        Log.d(TAG, "🖼️ Updating profile picture URL")

        // Update Firebase Auth profile
        firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
            photoUri = photoUrl  // ✅ Correct - direct property assignment
        })
            ?.addOnSuccessListener {
                Log.d(TAG, "✅ Firebase Auth profile updated")

                // Update Firestore user document
                if (!userDocumentPath.isNullOrEmpty()) {
                    firestore.document(userDocumentPath!!).update("profile.imageUrl", photoUrl.toString())
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Profile image updated in Firestore")
                            profileImageUri = photoUrl
                            showToast("Profile picture updated successfully!")
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "❌ Failed to update image URL in Firestore", exception)
                            showToast("Failed to update profile picture in database")
                        }
                } else {
                    showToast("Cannot update image - invalid document path")
                }
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to update Firebase Auth profile", exception)
                showToast("Failed to update authentication profile")
            }
    }

    private fun logoutUser() {
        Log.d(TAG, "🚪 User logging out")
        firebaseAuth.signOut()
        // Clear login state
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            apply()
        }
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        Log.d(TAG, "🔙 Back pressed")
        finish()
    }
}