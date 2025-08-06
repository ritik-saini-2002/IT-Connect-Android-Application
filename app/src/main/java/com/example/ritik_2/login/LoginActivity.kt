package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.theme.Ritik_2Theme
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

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User is authenticated, check profile and proceed
            checkUserProfile(currentUser.uid)
            return
        }

        // No Firebase user, clear login state if set
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            apply()
        }

        // Show login screen
        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password -> performLogin(email, password) },
                    onRegisterClick = {
                        startActivity(Intent(this, RegistrationActivity::class.java))
                    },
                    onForgotPasswordClick = { email, callback ->
                        sendPasswordResetEmail(email, callback)
                    }
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
                    // Save login state
                    val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putBoolean("isLoggedIn", true)
                        apply()
                    }
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

    private fun checkUserProfile(userId: String) {
        Log.d(TAG, "🔍 Checking user profile for: $userId")

        val userDocRef = firestore.collection("users").document(userId)
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    navigateToMainActivity()
                } else {
                    navigateToMainActivity()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Error checking profile", exception)
                Toast.makeText(this, "Error checking profile: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastLogin(userId: String) {
        val timestamp = Timestamp.Companion.now()

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
        super.onBackPressed()
    }
}