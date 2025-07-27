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

        // If user is already logged in, check profile
        val currentUser: FirebaseUser? = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "👤 User already logged in: ${currentUser.email}")
            checkUserProfile(currentUser.uid)
            return
        }

        // Show login screen only if user is not logged in
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
                        updateLastLogin(userId)
                        checkUserProfile(userId)
                    }
                } else {
                    Log.e(TAG, "❌ Login failed: ${task.exception?.message}")
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Checks if the user's profile is complete. If incomplete, navigate to ProfileCompletionActivity.
     */
    private fun checkUserProfile(userId: String) {
        Log.d(TAG, "🔍 Checking user profile for: $userId")

        val userDocRef = firestore.collection("users").document(userId)
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "pending"
                    val phone = document.getString("phoneNumber") ?: ""
                    val company = document.getString("companyName") ?: ""

                    // If profile is incomplete, navigate to ProfileCompletionActivity
                    if (status == "pending" || phone.isEmpty() || company.isEmpty()) {
                        Log.d(TAG, "📝 Profile incomplete, redirecting to ProfileCompletionActivity")
                        val intent = ProfileCompletionActivity.createIntent(this, userId)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d(TAG, "🏠 Profile complete, navigating to MainActivity")
                        navigateToMainActivity()
                    }
                } else {
                    Log.d(TAG, "⚠️ User document not found, redirecting to ProfileCompletionActivity")
                    val intent = ProfileCompletionActivity.createIntent(this, userId)
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Error checking profile", exception)
                Toast.makeText(this, "Error checking profile: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastLogin(userId: String) {
        val timestamp = Timestamp.now()

        firestore.collection("user_access_control").document(userId)
            .update("lastAccess", timestamp)
            .addOnSuccessListener { Log.d(TAG, "✅ Last access updated in access control") }
            .addOnFailureListener { e -> Log.w(TAG, "⚠️ Failed to update last access in access control", e) }

        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { doc ->
                val documentPath = doc.getString("documentPath")
                if (!documentPath.isNullOrEmpty()) {
                    firestore.document(documentPath)
                        .update("lastLogin", timestamp)
                        .addOnSuccessListener { Log.d(TAG, "✅ Last login updated in user document") }
                        .addOnFailureListener { e -> Log.w(TAG, "⚠️ Failed to update last login in user document", e) }
                }
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
        finishAffinity()
    }
}
