package com.example.ritik_2.registration

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ritik_2.data.repository.RegistrationParams
import com.example.ritik_2.data.repository.UserRepository
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.pocketbase.PocketBaseSessionManager
import com.example.ritik_2.profile.profilecompletion.ProfileCompletionActivity
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationActivity : ComponentActivity() {

    private val repository   = UserRepository.getInstance()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object { const val TAG = "RegistrationActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PocketBaseSessionManager.init(this)

        setContent {
            Ritik_2Theme {
                RegistrationScreen(
                    onRegisterClick = { email, password, name, phoneNumber,
                                        designation, companyName,
                                        experience, completedProjects,
                                        activeProjects, complaints,
                                        imageUri, role, department ->
                        performRegistration(
                            email, password, name, phoneNumber,
                            designation, companyName,
                            experience, completedProjects,
                            activeProjects, complaints,
                            imageUri,
                            role ?: "Administrator",
                            department ?: "Administrative"
                        )
                    },
                    onLoginClick = { navigateToLogin() }
                )
            }
        }
    }

    private fun performRegistration(
        email: String, password: String, name: String,
        phoneNumber: String, designation: String, companyName: String,
        experience: Int, completedProjects: Int, activeProjects: Int,
        complaints: Int, imageUri: Uri?, role: String, department: String
    ) {
        // ── Validation (regular function, not suspend) ─────────
        if (!validateInput(email, password, name, companyName, designation, department)) return

        toast("Creating your account...")

        activityScope.launch {
            try {
                // Read image bytes on IO thread
                val imageBytes = withContext(Dispatchers.IO) {
                    imageUri?.let { uri ->
                        try {
                            contentResolver.openInputStream(uri)?.readBytes()
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read image: ${e.message}")
                            null
                        }
                    }
                }

                val params = RegistrationParams(
                    email             = email.trim(),
                    password          = password,
                    name              = name.trim(),
                    phoneNumber       = phoneNumber.trim(),
                    designation       = designation.trim(),
                    companyName       = companyName.trim(),
                    department        = department,
                    role              = role,
                    experience        = experience,
                    completedProjects = completedProjects,
                    activeProjects    = activeProjects,
                    complaints        = complaints,
                    imageBytes        = imageBytes
                )

                val result = withContext(Dispatchers.IO) {
                    repository.registerUser(params)
                }

                if (result.isSuccess) {
                    val userId = result.getOrThrow()

                    // Save session
                    PocketBaseSessionManager.saveSession(
                        token        = "",
                        userId       = userId,
                        email        = email,
                        name         = name,
                        role         = role,
                        documentPath = "users/${sanitize(companyName)}/${sanitize(department)}/$role/$userId"
                    )

                    toast("Account created! Please complete your profile.")
                    navigateToProfileCompletion(userId)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Registration failed"
                    toast(err)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Registration error: ${e.message}", e)
                toast("Registration failed: ${e.message}")
            }
        }
    }

    // ── Regular function — NOT suspend ────────────────────────
    private fun validateInput(
        email: String, password: String, name: String,
        companyName: String, designation: String, department: String
    ): Boolean {
        when {
            name.isBlank()        -> { toast("Name is required");                          return false }
            name.length < 2       -> { toast("Name must be at least 2 characters");        return false }
            !isValidEmail(email)  -> { toast("Please enter a valid email");                return false }
            password.length < 6   -> { toast("Password must be at least 6 characters");   return false }
            companyName.isBlank() -> { toast("Company name is required");                  return false }
            designation.isBlank() -> { toast("Designation is required");                   return false }
            department.isBlank()  -> { toast("Department is required");                    return false }
            department.length < 2 -> { toast("Department must be at least 2 characters"); return false }
        }
        return true
    }

    private fun sanitize(input: String) =
        input.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_").trim('_').take(100)

    private fun isValidEmail(email: String) =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun navigateToProfileCompletion(userId: String) {
        startActivity(ProfileCompletionActivity.createIntent(this, userId))
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}