package com.example.ritik_2.profile.profilecompletion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileCompletionActivity : ComponentActivity() {

    companion object {
        const val TAG = "ProfileCompletion"
        const val EXTRA_USER_ID = "extra_user_id"

        fun createIntent(context: Context, userId: String): Intent {
            return Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
        }
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        Log.d(TAG, "ProfileCompletionActivity started for user: $userId")

        if (userId.isEmpty()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        setContent {
            Ritik_2Theme {
                val isDarkTheme = isSystemInDarkTheme()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFAFAFA)
                ) {
                    ProfileCompletionScreen(
                        userId = userId,
                        isDarkTheme = isDarkTheme,
                        onProfileUpdateClick = { updatedData, newPassword, imageUri ->
                            handleProfileUpdate(updatedData, newPassword, imageUri, userId)
                        },
                        onSkipClick = {
                            navigateToMainActivity()
                        },
                        onLogoutClick = {
                            handleLogout()
                        }
                    )
                }
            }
        }
    }

    private fun handleProfileUpdate(
        updatedData: Map<String, Any>,
        newPassword: String?,
        imageUri: Uri?,
        userId: String
    ) {
        Log.d(TAG, "Starting profile update for user: $userId")

        try {
            if (imageUri != null) {
                uploadImageAndUpdateProfile(imageUri, updatedData, newPassword, userId)
            } else {
                updateHierarchicalProfileData(updatedData, newPassword, userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling profile update", e)
            Toast.makeText(this, "Profile update failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageAndUpdateProfile(
        imageUri: Uri,
        updatedData: Map<String, Any>,
        newPassword: String?,
        userId: String
    ) {
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { accessDoc ->
                if (accessDoc.exists()) {
                    val sanitizedCompany = accessDoc.getString("sanitizedCompany") ?: "default_company"
                    val sanitizedDepartment = accessDoc.getString("sanitizedDepartment") ?: "default_department"
                    val role = accessDoc.getString("role") ?: "Employee"

                    val imageRef = storage.reference.child("users/$sanitizedCompany/$sanitizedDepartment/$role/$userId/profile.jpg")

                    imageRef.putFile(imageUri)
                        .addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                                val dataWithImage = updatedData.toMutableMap()
                                dataWithImage["profile.imageUrl"] = downloadUrl.toString()
                                dataWithImage["lastUpdated"] = Timestamp.now()
                                updateHierarchicalProfileData(dataWithImage, newPassword, userId)
                            }.addOnFailureListener { e ->
                                Log.e(TAG, "Failed to get download URL", e)
                                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Image upload failed", e)
                            Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Log.e(TAG, "User access control not found")
                    Toast.makeText(this, "User access control not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user access control", e)
                Toast.makeText(this, "Failed to get user information: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateHierarchicalProfileData(
        updatedData: Map<String, Any>,
        newPassword: String?,
        userId: String
    ) {
        Log.d(TAG, "Updating hierarchical profile data for user: $userId")

        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { accessDoc ->
                if (accessDoc.exists()) {
                    val sanitizedCompany = accessDoc.getString("sanitizedCompany") ?: "default_company"
                    val sanitizedDepartment = accessDoc.getString("sanitizedDepartment") ?: "default_department"
                    val role = accessDoc.getString("role") ?: "Employee"

                    val userDocRef = firestore
                        .collection("users")
                        .document(sanitizedCompany)
                        .collection(sanitizedDepartment)
                        .document(role)
                        .collection("users")
                        .document(userId)

                    val batch = firestore.batch()
                    val finalUpdateData = updatedData.toMutableMap()
                    finalUpdateData["lastUpdated"] = Timestamp.now()
                    finalUpdateData["isProfileComplete"] = true

                    batch.update(userDocRef, finalUpdateData)

                    // Update user_access_control
                    batch.update(
                        firestore.collection("user_access_control").document(userId),
                        mapOf("lastAccess" to Timestamp.now())
                    )

                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "Hierarchical profile updated successfully")
                            if (newPassword != null && newPassword.isNotEmpty()) {
                                updatePassword(newPassword)
                            } else {
                                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                navigateToMainActivity()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Hierarchical profile update failed", e)
                            Toast.makeText(this, "Profile update failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Log.e(TAG, "User access control document not found")
                    Toast.makeText(this, "User access control not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user access control", e)
                Toast.makeText(this, "Failed to get user information: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updatePassword(newPassword: String) {
        val user = firebaseAuth.currentUser
        if (user != null) {
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    Log.d(TAG, "Password updated successfully")
                    Toast.makeText(this, "Profile and password updated successfully!", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Password update failed", e)
                    Toast.makeText(this, "Password update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    navigateToMainActivity()
                }
        } else {
            Log.w(TAG, "No current user for password update")
            navigateToMainActivity()
        }
    }

    private fun handleLogout() {
        Log.d(TAG, "Logging out user")
        firebaseAuth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to login", e)
            finish()
        }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navigating to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to main activity", e)
            navigateToLogin()
        }
    }
}