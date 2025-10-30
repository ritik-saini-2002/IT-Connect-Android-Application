package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        Log.d(TAG, "LoginActivity started")

        // Check if user is already authenticated
        checkExistingAuthentication()
    }

    private fun checkExistingAuthentication() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Found existing user: ${currentUser.email}")
            updateLastLogin(currentUser.uid)
            navigateToMainActivity()
        } else {
            Log.d(TAG, "No existing user found")
            clearLoginState()
            showLoginScreen()
        }
    }

    private fun showLoginScreen() {
        Log.d(TAG, "Showing login screen")

        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        performEmailPasswordLogin(email, password)
                    },
                    onRegisterClick = {
                        Log.d(TAG, "Navigating to registration")
                        val intent = Intent(this, RegistrationActivity::class.java)
                        startActivity(intent)
                    },
                    onForgotPasswordClick = { email ->
                        sendPasswordResetEmail(email)
                    },
                    onInfoClick = {
                        Log.d(TAG, "Info button clicked - Opening Contact Activity")
                        val intent = Intent(this, ContactActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    // EMAIL + PASSWORD LOGIN
    private fun performEmailPasswordLogin(email: String, password: String) {
        Log.d(TAG, "Attempting email/password login for: $email")

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password!", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase Auth login successful")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    saveLoginState()

                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        updateLastLogin(userId)
                        navigateToMainActivity()
                    }
                } else {
                    Log.e(TAG, "Login failed: ${task.exception?.message}")
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record", ignoreCase = true) == true ->
                            "No account found with this email"
                        task.exception?.message?.contains("password is invalid", ignoreCase = true) == true ||
                                task.exception?.message?.contains("wrong password", ignoreCase = true) == true ->
                            "Incorrect password"
                        task.exception?.message?.contains("network", ignoreCase = true) == true ->
                            "Network error. Please check your connection"
                        task.exception?.message?.contains("disabled", ignoreCase = true) == true ->
                            "This account has been disabled"
                        else -> "Login failed: ${task.exception?.localizedMessage}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateLastLogin(userId: String) {
        Log.d(TAG, "Updating last login for user: $userId")

        val timestamp = Timestamp.now()

        firestore.collection("users").document(userId)
            .update("lastLogin", timestamp)
            .addOnSuccessListener {
                Log.d(TAG, "Last login updated successfully")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to update last login", e)
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
            Toast.makeText(this, "Error navigating to main screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        Log.d(TAG, "Sending password reset email to: $email")

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Log.d(TAG, "Password reset email sent successfully")
                Toast.makeText(
                    this,
                    "Password reset link sent to your email!",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to send password reset email", exception)
                val errorMessage = when {
                    exception.message?.contains("no user record", ignoreCase = true) == true ->
                        "No account found with this email"
                    exception.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your connection"
                    else -> "Error: ${exception.localizedMessage}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun saveLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }
        Log.d(TAG, "Login state saved")
    }

    private fun clearLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            apply()
        }
        Log.d(TAG, "Login state cleared")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d(TAG, "Back pressed - exiting app")
        finishAffinity()
        super.onBackPressed()
    }
}