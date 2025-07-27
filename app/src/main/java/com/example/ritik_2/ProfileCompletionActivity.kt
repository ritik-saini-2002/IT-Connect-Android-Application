package com.example.ritik_2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.google.firebase.Timestamp
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
                var userData by remember { mutableStateOf<Map<String, Any>?>(null) }
                var loading by remember { mutableStateOf(true) }

                LaunchedEffect(userId) {
                    if (userId != null) {
                        firestore.collection("user_access_control").document(userId)
                            .get()
                            .addOnSuccessListener { accessDoc ->
                                val docPath = accessDoc.getString("documentPath")
                                if (!docPath.isNullOrEmpty()) {
                                    firestore.document(docPath)
                                        .get()
                                        .addOnSuccessListener { userDoc ->
                                            userData = userDoc.data
                                            loading = false
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "❌ Failed to load user data", e)
                                            Toast.makeText(this@ProfileCompletionActivity, "Error loading data", Toast.LENGTH_SHORT).show()
                                            loading = false
                                        }
                                }
                            }
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    ProfileCompletionScreen(
                        userId = userId,
                        userData = userData,
                        onSave = { updatedFields ->
                            saveProfileData(userId, updatedFields)
                        },
                        onSkip = {
                            navigateToMainActivity()
                        }
                    )
                }
            }
        }
    }

    private fun saveProfileData(userId: String?, updatedFields: Map<String, Any>) {
        if (userId == null) return
        firestore.collection("user_access_control").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val path = doc.getString("documentPath")
                if (!path.isNullOrEmpty()) {
                    firestore.document(path)
                        .update(updatedFields + ("isProfileComplete" to true))
                        .addOnSuccessListener {
                            firestore.collection("user_access_control").document(userId)
                                .update("isProfileComplete", true)
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Error updating profile", e)
                            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "🚀 Navigating to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to main activity", e)
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
    userData: Map<String, Any>?,
    onSave: (Map<String, Any>) -> Unit,
    onSkip: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }

    val name = userData?.get("name")?.toString() ?: "User"
    val email = userData?.get("email")?.toString() ?: "example@email.com"
    val companyName = userData?.get("companyName")?.toString() ?: "Your Organization"

    val profile = userData?.get("profile") as? Map<String, Any>
    phoneNumber = profile?.get("phoneNumber")?.toString() ?: ""
    address = profile?.get("address")?.toString() ?: ""
    dateOfBirth = profile?.get("dateOfBirth")?.toString() ?: ""

    var updatedPhone by remember { mutableStateOf(phoneNumber) }
    var updatedAddress by remember { mutableStateOf(address) }
    var updatedDob by remember { mutableStateOf(dateOfBirth) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Complete Your Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Read-only fields (Admin provided)
        ReadOnlyField(label = "Name", value = name)
        ReadOnlyField(label = "Email", value = email)
        ReadOnlyField(label = "Company", value = companyName)

        Spacer(modifier = Modifier.height(16.dp))

        // Editable fields
        OutlinedTextField(
            value = updatedPhone,
            onValueChange = { updatedPhone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = updatedAddress,
            onValueChange = { updatedAddress = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = updatedDob,
            onValueChange = { updatedDob = it },
            label = { Text("Date of Birth") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val updates = mapOf(
                    "profile.phoneNumber" to updatedPhone,
                    "profile.address" to updatedAddress,
                    "profile.dateOfBirth" to updatedDob,
                    "lastUpdated" to Timestamp.now()
                )
                onSave(updates)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Continue")
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for Now")
        }
    }
}

@Composable
fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    )
}
