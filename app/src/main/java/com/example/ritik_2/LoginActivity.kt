package com.example.ritik_2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.ui.LoginScreen
import com.example.ritik_2.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // If user is already logged in, check their role and navigate accordingly
        val currentUser: FirebaseUser? = firebaseAuth.currentUser
        if (currentUser != null) {
            checkUserRoleAndNavigate(currentUser.uid)
        }

        setContent {
            Ritik_2Theme {
                LoginScreen(
                    onLoginClick = { email, password -> performLogin(email, password) },
                    onRegisterClick = {
                        startActivity(Intent(this, RegistrationActivity::class.java))
                    },
                    onForgotPasswordClick = { email, callback -> sendPasswordResetEmail(email, callback) }
                )
            }
        }
    }

    private fun performLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password!", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        checkUserRoleAndNavigate(userId)
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkUserRoleAndNavigate(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    when (role) {
                        "Administrator" -> {
                            // Navigate to Admin Panel
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        "Manager", "Employee" -> {
                            // Navigate to Main Activity for regular users
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        else -> {
                            // Handle unknown role or null role
                            Toast.makeText(this, "Invalid user role. Please contact administrator.", Toast.LENGTH_LONG).show()
                            firebaseAuth.signOut()
                        }
                    }
                } else {
                    // User document doesn't exist in Firestore
                    Toast.makeText(this, "User data not found. Please contact administrator.", Toast.LENGTH_LONG).show()
                    firebaseAuth.signOut()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error fetching user data: ${exception.message}", Toast.LENGTH_LONG).show()
                firebaseAuth.signOut()
            }
    }

    private fun sendPasswordResetEmail(email: String, callback: (Boolean) -> Unit) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Reset link sent!", Toast.LENGTH_SHORT).show()
                callback(true)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    override fun onBackPressed() {
        finishAffinity() // Exits the app instead of reopening RegistrationActivity
    }
}