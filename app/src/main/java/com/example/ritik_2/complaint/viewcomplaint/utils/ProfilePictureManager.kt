package com.example.ritik_2.complaint.viewcomplaint.utils

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await

class ProfilePictureManager(private val context: Context) {

    private val storage = FirebaseStorage.getInstance()

    fun getProfilePictureRef(
        sanitizedCompanyName: String,
        sanitizedDepartment: String,
        role: String,
        userId: String
    ): StorageReference {
        return storage.reference.child("users/$sanitizedCompanyName/$sanitizedDepartment/$role/$userId/profile.jpg")
    }

    suspend fun getProfilePictureUrl(
        sanitizedCompanyName: String,
        sanitizedDepartment: String,
        role: String,
        userId: String
    ): String? {
        return try {
            val ref = getProfilePictureRef(sanitizedCompanyName, sanitizedDepartment, role, userId)
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadProfilePicture(
        imageUri: Uri,
        sanitizedCompanyName: String,
        sanitizedDepartment: String,
        role: String,
        userId: String
    ): Result<String> {
        return try {
            val ref = getProfilePictureRef(sanitizedCompanyName, sanitizedDepartment, role, userId)
            ref.putFile(imageUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDefaultProfilePictureUrl(userName: String): String {
        // Generate initials-based profile picture URL using a service like UI Avatars
        val initials = userName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
        return "https://ui-avatars.com/api/?name=${initials}&size=200&background=random&color=fff&font-size=0.6"
    }
}