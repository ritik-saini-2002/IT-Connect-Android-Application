package com.example.ritik_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ritik_2.modules.MainViewModel
import com.example.ritik_2.modules.UserProfiledata
import com.example.ritik_2.ui.theme.ui.theme.ITConnectTheme
import com.example.ritik_2.ui.theme.MainScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "🚀 MainActivity started")

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Check authentication status
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ No authenticated user found")
            navigateToLogin()
            return
        }

        Log.d(TAG, "👤 Authenticated user: ${currentUser.email}")

        // Load user data
        loadUserData()

        setContent {
            ITConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use state holders from ViewModel
                    var userProfile by remember { mutableStateOf<UserProfiledata?>(null) }
                    var isLoading by remember { mutableStateOf(true) }

                    // Update state when ViewModel data changes
                    LaunchedEffect(Unit) {
                        viewModel.userProfileState.observe(this@MainActivity) { profile ->
                            userProfile = profile
                            Log.d(TAG, "📊 Profile state updated: ${profile?.name}")
                        }
                        viewModel.isLoadingState.observe(this@MainActivity) { loading ->
                            isLoading = loading
                            Log.d(TAG, "⏳ Loading state: $loading")
                        }
                        viewModel.errorMessageState.observe(this@MainActivity) { errorMsg ->
                            errorMsg?.let {
                                Log.e(TAG, "❌ Error: $it")
                                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                                viewModel.clearError()

                                // If it's an authentication error, navigate to login
                                if (it.contains("deactivated") || it.contains("not found") || it.contains("authentication")) {
                                    firebaseAuth.signOut()
                                    navigateToLogin()
                                }
                            }
                        }
                    }

                    MainScreen(
                        userProfile = userProfile,
                        isLoading = isLoading,
                        onLogout = {
                            Log.d(TAG, "🚪 User logging out")
                            firebaseAuth.signOut()
                            navigateToLogin()
                        },
                        onCardClick = { cardId -> handleCardClick(cardId) },
                        onProfileClick = { navigateToProfile() },
                        /*onRefresh = {
                            Log.d(TAG, "🔄 Refreshing user data")
                            loadUserData()
                        }*/
                    )
                }
            }
        }
    }

    private fun loadUserData() {
        Log.d(TAG, "📊 Loading user data")
        viewModel.isLoadingState.value = true

        firebaseAuth.currentUser?.uid?.let { userId ->
            Log.d(TAG, "📋 Loading profile for user ID: $userId")
            viewModel.loadUserProfile(userId)
        } ?: run {
            Log.e(TAG, "❌ User not authenticated")
            viewModel.setError("User not authenticated")
            viewModel.isLoadingState.value = false
            navigateToLogin()
        }
    }

    private fun handleCardClick(cardId: Int) {
        Log.d(TAG, "🎯 Card clicked: $cardId")

        // Check if user profile is loaded before navigation
        val userProfile = viewModel.userProfileState.value
        if (userProfile == null) {
            Toast.makeText(this, "Please wait for profile to load", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            when (cardId) {
                1 -> {
                    Log.d(TAG, "📝 Navigating to Register Complaint")
                    startActivity(Intent(this, RegisterComplain::class.java))
                }
                2 -> {
                    Log.d(TAG, "👁️ Navigating to View Complaints")
                    startActivity(Intent(this, ComplaintViewActivity::class.java))
                }
                3 -> {
                    Log.d(TAG, "⚙️ Navigating to Admin Panel")
                    // Check if user has admin permissions
                    if (userProfile.role in listOf("Administrator", "Manager", "HR")) {
                        startActivity(Intent(this, AdminPanelActivity::class.java))
                    } else {
                        Toast.makeText(this, "Access denied. Admin privileges required.", Toast.LENGTH_SHORT).show()
                    }
                }
                4 -> {
                    Log.d(TAG, "🔗 Navigating to Server Connect")
                    startActivity(Intent(this, ServerConnectActivity::class.java))
                }
                // Add more cases as needed
                else -> {
                    Log.w(TAG, "⚠️ Unknown card ID: $cardId")
                    Toast.makeText(this, "Feature coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating from card click", e)
            Toast.makeText(this, "Error opening feature: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLogin() {
        Log.d(TAG, "🔐 Navigating to login")
        try {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to login", e)
        }
    }

    private fun navigateToProfile() {
        Log.d(TAG, "👤 Navigating to profile")
        try {
            val userProfile = viewModel.userProfileState.value
            if (userProfile != null) {
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("userId", userProfile.id)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Profile not loaded yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to profile", e)
            Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 MainActivity resumed - refreshing data")
        // Refresh user data when returning to the activity
        loadUserData()
    }
}