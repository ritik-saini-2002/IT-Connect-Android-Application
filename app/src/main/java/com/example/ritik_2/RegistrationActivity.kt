package com.example.ritik_2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.ui.theme.RegistrationScreen
import com.example.ritik_2.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RegistrationActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        setContent {
            Ritik_2Theme {
                RegistrationScreen(
                    onRegisterClick = { email, password, name, phoneNumber, designation, companyName, experience, completedProjects, activeProjects, complaints, imageUri ->
                        performRegistration(
                            email, password, name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, imageUri
                        )
                    },
                    onLoginClick = { navigateToLoginActivity() }
                )
            }
        }
    }

    private fun performRegistration(
        email: String,
        password: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        imageUri: Uri?
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid ?: return@addOnCompleteListener

                    if (imageUri != null) {
                        uploadImageToFirebaseStorage(
                            userId, imageUri, name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, email
                        )
                    } else {
                        saveUserDataToFirestore(
                            userId, "", name, phoneNumber, designation, companyName,
                            experience, completedProjects, activeProjects, complaints, email
                        )
                    }
                } else {
                    Toast.makeText(
                        this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun uploadImageToFirebaseStorage(
        userId: String,
        imageUri: Uri?,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        email: String
    ) {
        if (imageUri == null) {
            saveUserDataToFirestore(
                userId, "", name, phoneNumber, designation, companyName,
                experience, completedProjects, activeProjects, complaints, email
            )
            return
        }

        val storageRef = storage.reference.child("users/$userId/profile.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateUserProfile(uri)
                    saveUserDataToFirestore(
                        userId, uri.toString(), name, phoneNumber, designation, companyName,
                        experience, completedProjects, activeProjects, complaints, email
                    )
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Image upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserProfile(photoUrl: Uri) {
        firebaseAuth.currentUser?.updateProfile(userProfileChangeRequest {
            photoUri = photoUrl
        })
    }

    private fun saveUserDataToFirestore(
        userId: String,
        imageUrl: String,
        name: String,
        phoneNumber: String,
        designation: String,
        companyName: String,
        experience: Int,
        completedProjects: Int,
        activeProjects: Int,
        complaints: Int,
        email: String
    ) {
        val userData = mapOf(
            "userId" to userId,
            "imageUrl" to imageUrl,
            "name" to name,
            "phoneNumber" to phoneNumber,
            "designation" to designation,
            "companyName" to companyName,
            "experience" to experience,
            "completedProjects" to completedProjects,
            "activeProjects" to activeProjects,
            "complaints" to complaints,
            "email" to email
        )

        firestore.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                navigateToLoginActivity()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToLoginActivity() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}