package com.example.ritik_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.ui.theme.LoginScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        const val TAG = "LoginActivity"
        val VALID_ROLES = listOf(
            "Administrator", "Manager", "HR", "Team Lead", "Employee", "Intern"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        Log.d(TAG, "🚀 LoginActivity started")

        // If user is already logged in, check their profile completion status
        val currentUser: FirebaseUser? = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "👤 User already logged in: ${currentUser.email}")
            checkUserProfileAndNavigate(currentUser.uid)
        }

        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password -> performLogin(email, password) },
                    onRegisterClick = {
                        Log.d(TAG, "📝 Navigating to registration")
                        startActivity(Intent(this, RegistrationActivity::class.java))
                    },
                    onForgotPasswordClick = { email, callback -> sendPasswordResetEmail(email, callback) }
                )
            }
        }
    }

    private fun performLogin(email: String, password: String) {
        Log.d(TAG, "🔐 Attempting login for: $email")

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password!", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Firebase Auth login successful")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        // Update last login timestamp
                        updateLastLogin(userId)
                        checkUserProfileAndNavigate(userId)
                    }
                } else {
                    Log.e(TAG, "❌ Login failed: ${task.exception?.message}")
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkUserProfileAndNavigate(userId: String) {
        Log.d(TAG, "🔍 Checking user profile for: $userId")

        // First, get user access control to find the correct document path
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { accessDocument ->
                if (accessDocument.exists()) {
                    val documentPath = accessDocument.getString("documentPath")
                    val role = accessDocument.getString("role")
                    val isActive = accessDocument.getBoolean("isActive") ?: false

                    Log.d(TAG, "📄 Access control found - Role: $role, Path: $documentPath, Active: $isActive")

                    if (!isActive) {
                        Toast.makeText(this, "Your account is deactivated. Please contact administrator.", Toast.LENGTH_LONG).show()
                        firebaseAuth.signOut()
                        return@addOnSuccessListener
                    }

                    if (documentPath.isNullOrEmpty() || role.isNullOrEmpty()) {
                        Toast.makeText(this, "Invalid user configuration. Please contact administrator.", Toast.LENGTH_LONG).show()
                        firebaseAuth.signOut()
                        return@addOnSuccessListener
                    }

                    // Now get the actual user document
                    firestore.document(documentPath).get()
                        .addOnSuccessListener { userDocument ->
                            if (userDocument.exists()) {
                                processUserDocument(userId, userDocument.data, role)
                            } else {
                                Log.e(TAG, "❌ User document not found at path: $documentPath")
                                Toast.makeText(this, "User profile not found. Please contact administrator.", Toast.LENGTH_LONG).show()
                                firebaseAuth.signOut()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "❌ Error fetching user document", exception)
                            Toast.makeText(this, "Error fetching user profile: ${exception.message}", Toast.LENGTH_LONG).show()
                            firebaseAuth.signOut()
                        }
                } else {
                    Log.e(TAG, "❌ User access control not found for: $userId")
                    Toast.makeText(this, "User access not found. Please contact administrator.", Toast.LENGTH_LONG).show()
                    firebaseAuth.signOut()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Error fetching access control", exception)
                Toast.makeText(this, "Error checking user access: ${exception.message}", Toast.LENGTH_LONG).show()
                firebaseAuth.signOut()
            }
    }

    private fun processUserDocument(userId: String, userData: Map<String, Any>?, role: String) {
        Log.d(TAG, "📊 Processing user document for role: $role")

        if (userData == null) {
            Toast.makeText(this, "Invalid user data. Please contact administrator.", Toast.LENGTH_LONG).show()
            firebaseAuth.signOut()
            return
        }

        if (role !in VALID_ROLES) {
            Toast.makeText(this, "Invalid user role: $role. Please contact administrator.", Toast.LENGTH_LONG).show()
            firebaseAuth.signOut()
            return
        }

        val isProfileComplete = checkProfileCompleteness(userData)
        Log.d(TAG, "✅ Profile completeness check: $isProfileComplete")

        when {
            !isProfileComplete -> {
                Log.d(TAG, "📝 Profile incomplete, navigating to completion")
                Toast.makeText(this, "Please complete your profile", Toast.LENGTH_SHORT).show()
                navigateToProfileCompletion(userId)
            }
            else -> {
                Log.d(TAG, "🏠 Profile complete, navigating to main activity")
                Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
        }
    }

    private fun checkProfileCompleteness(userData: Map<String, Any>): Boolean {
        Log.d(TAG, "🔍 Checking profile completeness")

        try {
            // Check main required fields
            val name = userData["name"]?.toString()
            val email = userData["email"]?.toString()
            val companyName = userData["companyName"]?.toString()
            val designation = userData["designation"]?.toString()
            val role = userData["role"]?.toString()

            // Check profile nested object
            val profile = userData["profile"] as? Map<String, Any>
            val phoneNumber = profile?.get("phoneNumber")?.toString()

            // Check workStats nested object
            val workStats = userData["workStats"] as? Map<String, Any>
            val experience = workStats?.get("experience")
            val completedProjects = workStats?.get("completedProjects")

            val requiredFieldsValid = listOf(
                name, email, companyName, designation, role, phoneNumber
            ).all { !it.isNullOrBlank() }

            val numbersValid = experience != null && completedProjects != null &&
                    (experience as? Number)?.toInt() != null &&
                    (completedProjects as? Number)?.toInt() != null

            val isComplete = requiredFieldsValid && numbersValid
            Log.d(TAG, "Profile completeness: $isComplete")

            return isComplete

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking profile completeness", e)
            return false
        }
    }

    private fun updateLastLogin(userId: String) {
        val timestamp = Timestamp.now()

        // Update in user_access_control
        firestore.collection("user_access_control").document(userId)
            .update("lastAccess", timestamp)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Last access updated in access control")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Failed to update last access in access control", e)
            }

        // Update in main user document (we'll get the path from access control)
        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { doc ->
                val documentPath = doc.getString("documentPath")
                if (!documentPath.isNullOrEmpty()) {
                    firestore.document(documentPath)
                        .update("lastLogin", timestamp)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Last login updated in user document")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "⚠️ Failed to update last login in user document", e)
                        }
                }
            }
    }

    private fun navigateToProfileCompletion(userId: String) {
        try {
            val intent = ProfileCompletionActivity.createIntent(this, userId)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to profile completion", e)
            Toast.makeText(this, "Error navigating to profile completion", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMainActivity() {
        try {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to main activity", e)
            Toast.makeText(this, "Error navigating to main screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPasswordResetEmail(email: String, callback: (Boolean) -> Unit) {
        Log.d(TAG, "📧 Sending password reset email to: $email")

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Password reset email sent successfully")
                Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_SHORT).show()
                callback(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to send password reset email", exception)
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    override fun onBackPressed() {
        Log.d(TAG, "🔙 Back pressed - exiting app")
        finishAffinity() // Exits the app instead of reopening RegistrationActivity
    }
}