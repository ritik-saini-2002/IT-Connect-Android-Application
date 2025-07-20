package com.example.ritik_2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.AdminPanelScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class AdminPanelActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isCreatingUser = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify admin role
        verifyAdminRole(currentUserId)

        setContent {
            Ritik_2Theme {
                AdminPanelScreen(
                    isCreating = isCreatingUser.value,
                    onCreateUserClick = { name, email, role, companyName, designation, password ->
                        createUserAccount(name, email, role, companyName, designation, password, currentUserId)
                    }
                )
            }
        }
    }

    private fun verifyAdminRole(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val role = doc.getString("role")
                    if (role != "Administrator") {
                        Toast.makeText(this, "Access denied: Administrator privileges required", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error verifying permissions: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun createUserAccount(name: String, email: String, role: String, companyName: String, designation: String, password: String, createdBy: String) {

        if (isCreatingUser.value) {
            Toast.makeText(this, "User creation in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (name.isBlank()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }


        if (companyName.isBlank()) {
            Toast.makeText(this, "Company name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (designation.isBlank()) {
            Toast.makeText(this, "Designation is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        isCreatingUser.value = true

        // Store current user for re-authentication later
        val currentUser = auth.currentUser

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { task ->
                val newUserId = task.user?.uid
                if (newUserId != null) {
                    // Create user data with same structure as registration
                    val userData = mapOf(
                        "userId" to newUserId,
                        "name" to name,
                        "email" to email,
                        "role" to role,
                        "companyName" to companyName,
                        "designation" to "",
                        "createdAt" to Timestamp.now(),
                        "createdBy" to createdBy,
                        // Empty fields that user can fill later
                        "imageUrl" to "",
                        "phoneNumber" to "",
                        "experience" to 0,
                        "completedProjects" to 0,
                        "activeProjects" to 0,
                        "complaints" to 0
                    )

                    firestore.collection("users").document(newUserId).set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "User created successfully!", Toast.LENGTH_SHORT).show()

                            // Sign out the newly created user and re-authenticate admin
                            auth.signOut()

                            // Note: In production, you should use Firebase Admin SDK or Cloud Functions
                            // to avoid this authentication issue
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Error saving user data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Failed to create user account", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Registration failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                isCreatingUser.value = false
            }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}