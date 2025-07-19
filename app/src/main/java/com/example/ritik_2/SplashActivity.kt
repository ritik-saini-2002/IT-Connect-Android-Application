package com.example.ritik_2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.ritik_2.ui.theme.ui.theme.logo.ITConnectSplashScreen
import com.example.ritik_2.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setContent {
            Ritik_2Theme {
                var splashComplete by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Ensure minimum splash duration of 3 seconds
                    delay(3000)
                    splashComplete = true
                }

                ITConnectSplashScreen(
                    onSplashComplete = {
                        if (splashComplete) {
                            checkAuthenticationAndNavigate()
                        }
                    }
                )
            }
        }
    }

    private fun checkAuthenticationAndNavigate() {
        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            // User is logged in, check their role and navigate to MainActivity
            checkUserRoleAndNavigate(currentUser.uid)
        } else {
            // User is not logged in, navigate to LoginActivity
            navigateToLogin()
        }
    }

    private fun checkUserRoleAndNavigate(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    when (role) {
                        "Administrator", "Manager", "Employee" -> {
                            navigateToMain()
                        }
                        else -> {
                            // Invalid role, sign out and go to login
                            firebaseAuth.signOut()
                            navigateToLogin()
                        }
                    }
                } else {
                    // User document doesn't exist, sign out and go to login
                    firebaseAuth.signOut()
                    navigateToLogin()
                }
            }
            .addOnFailureListener {
                // Error fetching user data, sign out and go to login
                firebaseAuth.signOut()
                navigateToLogin()
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}