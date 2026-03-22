package com.example.ritik_2.complaint.viewcomplaint.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserProfile
import com.example.ritik_2.complaint.viewcomplaint.utils.ProfilePictureManager
import kotlinx.coroutines.tasks.await

class UserProfileRepository(
    private val firestore: FirebaseFirestore,
    private val profilePictureManager: ProfilePictureManager
) {

    suspend fun getUserProfile(userId: String): Result<UserProfile> {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                val userData = userDoc.toObject(UserProfile::class.java)
                userData?.let {
                    // Get profile picture URL
                    val profilePictureUrl = profilePictureManager.getProfilePictureUrl(
                        it.companyName.replace(" ", "_").lowercase(),
                        it.department.replace(" ", "_").lowercase(),
                        it.role,
                        userId
                    )

                    Result.success(it.copy(profilePictureUrl = profilePictureUrl))
                } ?: Result.failure(Exception("User data not found"))
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserComplaintStats(userId: String): Pair<Int, Int> {
        return try {
            val createdQuery = firestore.collection("complaints")
                .whereEqualTo("createdBy.userId", userId)
                .get().await()

            val resolvedQuery = firestore.collection("complaints")
                .whereEqualTo("resolvedBy", userId)
                .get().await()

            createdQuery.size() to resolvedQuery.size()
        } catch (e: Exception) {
            0 to 0
        }
    }
}