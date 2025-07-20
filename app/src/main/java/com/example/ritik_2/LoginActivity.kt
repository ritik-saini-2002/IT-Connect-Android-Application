package com.example.ritik_2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.ui.theme.LoginScreen
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
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

        // If user is already logged in, check their profile completion status
        val currentUser: FirebaseUser? = firebaseAuth.currentUser
        if (currentUser != null) {
            checkUserProfileAndNavigate(currentUser.uid)
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
                        checkUserProfileAndNavigate(userId)
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkUserProfileAndNavigate(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    val isProfileComplete = checkProfileCompleteness(document.data)

                    when {
                        role.isNullOrEmpty() -> {
                            Toast.makeText(this, "Invalid user role. Please contact administrator.", Toast.LENGTH_LONG).show()
                            firebaseAuth.signOut()
                        }
                        !isProfileComplete -> {
                            // Navigate to profile completion screen
                            val intent = Intent(this, ProfileCompletionActivity::class.java)
                            intent.putExtra("userId", userId)
                            startActivity(intent)
                            finish()
                        }
                        role in listOf("Administrator", "Manager", "Employee", "administrator", "manager", "employee") -> {
                            // Profile is complete, navigate to main activity
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        else -> {
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

    private fun checkProfileCompleteness(userData: Map<String, Any>?): Boolean {
        if (userData == null) return false

        val requiredFields = listOf(
            "name", "phoneNumber", "designation", "companyName",
            "experience", "completedProjects", "role", "email"
        )

        return requiredFields.all { field ->
            val value = userData[field]
            when (field) {
                "experience", "completedProjects" -> {
                    value != null && (value as? Number)?.toInt() != null
                }
                else -> !value.toString().isNullOrBlank()
            }
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