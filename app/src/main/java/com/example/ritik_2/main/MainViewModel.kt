package com.example.ritik_2.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    val userProfileState = MutableLiveData<UserProfiledata?>()
    val isLoadingState = MutableLiveData<Boolean>(true)
    val errorMessageState = MutableLiveData<String?>(null)

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val TAG = "MainViewModel"
    }

    fun loadUserProfile(userId: String) {
        Log.d(TAG, "🔄 Loading user profile for: $userId")

        viewModelScope.launch {
            isLoadingState.value = true
            try {
                val profile = withContext(Dispatchers.IO) {
                    // First, get user access control to find the correct document path
                    val accessControlDoc = firestore.collection("user_access_control")
                        .document(userId).get().await()

                    if (!accessControlDoc.exists()) {
                        Log.e(TAG, "❌ Access control document not found for user: $userId")
                        return@withContext null
                    }

                    val documentPath = accessControlDoc.getString("documentPath")
                    val role = accessControlDoc.getString("role")
                    val isActive = accessControlDoc.getBoolean("isActive") ?: false

                    Log.d(TAG, "📄 Access control found - Role: $role, Path: $documentPath, Active: $isActive")

                    if (!isActive) {
                        Log.w(TAG, "⚠️ User account is deactivated")
                        throw Exception("Your account is deactivated. Please contact administrator.")
                    }

                    if (documentPath.isNullOrEmpty()) {
                        Log.e(TAG, "❌ Document path is null or empty")
                        throw Exception("Invalid user configuration")
                    }

                    // Now get the actual user document
                    val userDoc = firestore.document(documentPath).get().await()

                    if (!userDoc.exists()) {
                        Log.e(TAG, "❌ User document not found at path: $documentPath")
                        throw Exception("User profile not found")
                    }

                    Log.d(TAG, "✅ User document loaded successfully")

                    // Extract data from nested structure
                    val profile = userDoc.get("profile") as? Map<String, Any> ?: emptyMap()
                    val workStats = userDoc.get("workStats") as? Map<String, Any> ?: emptyMap()
                    val issues = userDoc.get("issues") as? Map<String, Any> ?: emptyMap()

                    // Get image URL from profile
                    val imageUrl = profile["imageUrl"]?.toString()
                    val parsedImageUri = if (!imageUrl.isNullOrEmpty()) {
                        try {
                            Uri.parse(imageUrl)
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Failed to parse image URL: $imageUrl", e)
                            null
                        }
                    } else null

                    // Extract skills (if exists in profile or as separate field)
                    val skills = userDoc.get("skills") as? List<String>
                        ?: profile["skills"] as? List<String>
                        ?: emptyList()

                    UserProfiledata(
                        id = userId,
                        name = userDoc.getString("name") ?: "Unknown User",
                        email = userDoc.getString("email") ?: "",
                        role = role ?: "Unknown",
                        companyName = userDoc.getString("companyName") ?: "",
                        imageUrl = parsedImageUri,
                        designation = userDoc.getString("designation") ?: "IT Professional",
                        phoneNumber = profile["phoneNumber"]?.toString() ?: "",
                        skills = skills,
                        experience = (workStats["experience"] as? Number)?.toInt() ?: 0,
                        completedProjects = (workStats["completedProjects"] as? Number)?.toInt() ?: 0,
                        activeProjects = (workStats["activeProjects"] as? Number)?.toInt() ?: 0,
                        pendingTasks = (workStats["pendingTasks"] as? Number)?.toInt() ?: 0,
                        completedTasks = (workStats["completedTasks"] as? Number)?.toInt() ?: 0,
                        totalComplaints = (issues["totalComplaints"] as? Number)?.toInt() ?: 0,
                        resolvedComplaints = (issues["resolvedComplaints"] as? Number)?.toInt() ?: 0,
                        pendingComplaints = (issues["pendingComplaints"] as? Number)?.toInt() ?: 0,
                        isActive = isActive,
                        documentPath = documentPath
                    )
                }

                if (profile != null) {
                    Log.d(TAG, "✅ Profile loaded successfully: ${profile.name}")
                    userProfileState.value = profile
                } else {
                    Log.w(TAG, "⚠️ Profile is null, creating default profile")
                    userProfileState.value = UserProfiledata(
                        id = userId,
                        name = "Unknown User",
                        email = "",
                        role = "Unknown",
                        companyName = "",
                        imageUrl = null
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load user profile", e)
                errorMessageState.value = "Failed to load profile: ${e.message}"
                userProfileState.value = null
            } finally {
                isLoadingState.value = false
            }
        }
    }

    fun refreshUserProfile() {
        userProfileState.value?.let { currentProfile ->
            loadUserProfile(currentProfile.id)
        }
    }

    fun clearError() {
        errorMessageState.value = null
    }

    fun setError(message: String) {
        errorMessageState.value = message
    }
}


data class UserProfiledata(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val imageUrl: Uri? = null,
    val designation: String = "IT Professional",
    val phoneNumber: String = "",
    val skills: List<String> = emptyList(),
    val experience: Int = 0,
    val completedProjects: Int = 0,
    val activeProjects: Int = 0,
    val pendingTasks: Int = 0,
    val completedTasks: Int = 0,
    val totalComplaints: Int = 0,
    val resolvedComplaints: Int = 0,
    val pendingComplaints: Int = 0,
    val isActive: Boolean = true,
    val documentPath: String = "",

    // Computed properties for UI
    val performanceScore: Double = if (completedProjects + activeProjects > 0) {
        (completedProjects.toDouble() / (completedProjects + activeProjects)) * 100
    } else 0.0,

    val complaintsRate: Double = if (totalComplaints > 0 && resolvedComplaints >= 0) {
        (resolvedComplaints.toDouble() / totalComplaints) * 100
    } else 100.0
)