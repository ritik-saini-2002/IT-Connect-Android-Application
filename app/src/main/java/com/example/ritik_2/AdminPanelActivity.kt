package com.example.ritik_2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ritik_2.ui.theme.AdminPanelScreen
import com.example.ritik_2.ui.theme.Ritik_2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminPanelActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isCreatingUser = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")
                if (role != "Administrator") {
                    Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

        setContent {
            Ritik_2Theme {
                AdminPanelScreen(
                    isCreating = isCreatingUser.value,
                    onCreateUserClick = { email, password, role, designation ->
                        isCreatingUser.value = true
                        val tempAuth = FirebaseAuth.getInstance()
                        tempAuth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { task ->
                                val uid = task.user?.uid
                                if (uid != null) {
                                    val data = mapOf(
                                        "userId" to uid,
                                        "email" to email,
                                        "role" to role,
                                        "designation" to designation
                                    )
                                    firestore.collection("users").document(uid).set(data)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "User created!", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnCompleteListener {
                                isCreatingUser.value = false
                            }
                    }
                )
            }
        }
    }
}
