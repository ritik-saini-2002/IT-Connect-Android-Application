package com.example.ritik_2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.ui.theme.ProfileCompletionScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileCompletionActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        userId = intent.getStringExtra("userId") ?: ""

        if (userId.isEmpty()) {
            Toast.makeText(this, "Invalid user session", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        setContent {
            Ritik_2Theme {
                ProfileCompletionScreen(
                    userId = userId,
                    onProfileUpdateClick = { updatedData, newPassword, imageUri ->
                        updateUserProfile(updatedData, newPassword, imageUri)
                    },
                    onLogoutClick = { logout() }
                )
            }
        }
    }

    private fun updateUserProfile(
        updatedData: Map<String, Any>,
        newPassword: String?,
        imageUri: Uri?
    ) {
        // Update password if provided
        if (!newPassword.isNullOrEmpty()) {
            firebaseAuth.currentUser?.updatePassword(newPassword)
                ?.addOnSuccessListener {
                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener {
                    Toast.makeText(this, "Failed to update password: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Upload image if provided
        if (imageUri != null) {
            uploadImageAndUpdateProfile(imageUri, updatedData)
        } else {
            updateFirestoreData(updatedData)
        }
    }

    private fun uploadImageAndUpdateProfile(imageUri: Uri, updatedData: Map<String, Any>) {
        val storageRef = storage.reference.child("users/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Update Firebase Auth profile
                    firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
                        photoUri = uri
                    })

                    // Add image URL to data and update Firestore
                    val dataWithImage = updatedData.toMutableMap()
                    dataWithImage["imageUrl"] = uri.toString()
                    updateFirestoreData(dataWithImage)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Image upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFirestoreData(updatedData: Map<String, Any>) {
        firestore.collection("users").document(userId)
            .update(updatedData)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                // Navigate to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        firebaseAuth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        // Prevent going back, user must complete profile or logout
    }
}