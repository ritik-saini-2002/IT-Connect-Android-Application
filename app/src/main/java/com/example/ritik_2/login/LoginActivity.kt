package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.authentication.AuthState
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.example.ritik_2.contact.ContactActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val authManager = AuthManager.getInstance()

    companion object {
        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        Log.d(TAG, "🚀 LoginActivity started")

        // Check if user is already authenticated
        checkExistingAuthentication()
    }

    private fun checkExistingAuthentication() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "👤 Found existing user: ${currentUser.email}")

            // User is authenticated, navigate to MainActivity
            // MainActivity will handle profile completion logic
            updateLastLogin(currentUser.uid)
            navigateToMainActivity()
        } else {
            Log.d(TAG, "❌ No existing user found")
            // Clear any stored login state
            clearLoginState()
            showLoginScreen()
        }
    }

    private fun showLoginScreen() {
        Log.d(TAG, "🖥️ Showing login screen")

        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password -> performLogin(email, password) },
                    onRegisterClick = {
                        Log.d(TAG, "📝 Navigating to registration")
                        startActivity(Intent(this, RegistrationActivity::class.java))
                    },
                    onForgotPasswordClick = { email, callback ->
                        sendPasswordResetEmail(email, callback)
                    },
                    onInfoClick = {
                        Log.d(TAG, "ℹ️ Opening Contact/About page")
                        navigateToContactActivity()
                    }
                )
            }
        }
    }

    private fun navigateToContactActivity() {
        try {
            val intent = Intent(this, ContactActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "✅ Successfully opened ContactActivity")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening ContactActivity", e)
            Toast.makeText(this, "Unable to open contact page", Toast.LENGTH_SHORT).show()
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
                    saveLoginState()

                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        updateLastLogin(userId)
                        // Always navigate to MainActivity
                        // MainActivity will handle profile completion check
                        navigateToMainActivity()
                    } else {
                        Log.e(TAG, "❌ No user ID after successful login")
                        Toast.makeText(this, "Login error: No user ID", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "❌ Login failed: ${task.exception?.message}")
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateLastLogin(userId: String) {
        Log.d(TAG, "⏰ Updating last login for user: $userId")

        val timestamp = Timestamp.now()

        // Update access control
        firestore.collection("user_access_control").document(userId)
            .update("lastAccess", timestamp)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Last access updated in access control")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Failed to update last access in access control", e)
            }

        // Update user document
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
                } else {
                    // Fallback: update users collection directly
                    firestore.collection("users").document(userId)
                        .update("lastLogin", timestamp)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Last login updated in users collection")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "⚠️ Failed to update last login in users collection", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Failed to get document path", e)
                // Fallback: update users collection directly
                firestore.collection("users").document(userId)
                    .update("lastLogin", timestamp)
            }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "🏠 Navigating to main activity")
        Log.d(TAG, "🔍 Current user before navigation: ${authManager.currentUser?.email}")
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
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

    private fun saveLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }
        Log.d(TAG, "💾 Login state saved")
    }

    private fun clearLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            apply()
        }
        Log.d(TAG, "🗑️ Login state cleared")
    }

    override fun onBackPressed() {
        Log.d(TAG, "🔙 Back pressed - exiting app")
        finishAffinity()
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 LoginActivity resumed")

        // If user is authenticated on resume, go to MainActivity
        val currentUser = authManager.currentUser
        if (currentUser != null) {
            Log.d(TAG, "✅ User authenticated on resume, navigating to MainActivity")
            navigateToMainActivity()
        }
    }
}