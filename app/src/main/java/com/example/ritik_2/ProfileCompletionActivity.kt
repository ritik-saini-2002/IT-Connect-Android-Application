package com.example.ritik_2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.ui.theme.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileCompletionActivity : ComponentActivity() {

    companion object {
        const val TAG = "ProfileCompletion"
        const val EXTRA_USER_ID = "extra_user_id"

        fun createIntent(context: Context, userId: String): Intent {
            return Intent(context, ProfileCompletionActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getStringExtra(EXTRA_USER_ID)
        Log.d(TAG, "🎉 ProfileCompletionActivity started for user: $userId")

        setContent {
            Ritik_2Theme {
                ProfileCompletionScreen(
                    userId = userId,
                    onCompleted = {
                        markRegistrationComplete(userId)
                        navigateToMainActivity()
                    },
                    onSkip = {
                        markRegistrationComplete(userId)
                        navigateToMainActivity()
                    }
                )
            }
        }
    }

    private fun markRegistrationComplete(userId: String?) {
        if (userId != null) {
            Log.d(TAG, "✅ Marking registration as complete for user: $userId")

            // Update fresh registration status to fully completed
            firestore.collection("fresh_registrations").document(userId)
                .update(mapOf(
                    "status" to "fully_completed",
                    "fullyCompletedAt" to com.google.firebase.Timestamp.now()
                ))
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Registration marked as fully completed")
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "⚠️ Failed to mark registration as completed: ${exception.message}")
                }
        }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "🚀 Navigating to main activity")
        try {
            // Replace with your main activity class
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to main activity", e)
            // Fallback to login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun ProfileCompletionScreen(
    userId: String?,
    onCompleted: () -> Unit,
    onSkip: () -> Unit
) {
    var userName by remember { mutableStateOf("Administrator") }
    var companyName by remember { mutableStateOf("Your Organization") }

    // Try to get user info from Firebase
    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("user_access_control").document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            userName = document.getString("name") ?: "Administrator"
                            companyName = document.getString("companyName") ?: "Your Organization"
                        }
                    }
            } catch (e: Exception) {
                Log.w("ProfileCompletion", "Failed to get user info: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Animation/Icon
        Card(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(60.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Main Success Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉 Welcome, $userName!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Registration Completed Successfully",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Company info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Business,
                            contentDescription = "Company",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = companyName,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Administrator Account",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Features list
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your Administrator Privileges:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FeatureItem(
                        icon = Icons.Filled.PersonAdd,
                        text = "Create and manage user accounts"
                    )

                    FeatureItem(
                        icon = Icons.Filled.Analytics,
                        text = "Access all system analytics and reports"
                    )

                    FeatureItem(
                        icon = Icons.Filled.Settings,
                        text = "Configure system settings and permissions"
                    )

                    FeatureItem(
                        icon = Icons.Filled.Security,
                        text = "Full security and access control management"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action buttons
                Button(
                    onClick = onCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dashboard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Go to Admin Dashboard",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Complete Profile Setup Later",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Additional info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You can update your profile and company settings anytime from the dashboard.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}