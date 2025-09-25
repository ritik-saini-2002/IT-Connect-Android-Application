package com.example.ritik_2.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.ritik_2.authentication.AuthManager
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.registration.RegistrationActivity
import com.example.ritik_2.theme.Ritik_2Theme
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class LoginActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val authManager = AuthManager.getInstance()

    // For phone authentication
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    // For email link authentication
    private var emailForSignIn: String? = null

    companion object {
        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        Log.d(TAG, "LoginActivity started")

        // Check if user is already authenticated
        checkExistingAuthentication()
    }

    private fun checkExistingAuthentication() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Found existing user: ${currentUser.email ?: currentUser.phoneNumber}")
            updateLastLogin(currentUser.uid)
            navigateToMainActivity()
        } else {
            Log.d(TAG, "No existing user found")
            clearLoginState()
            showLoginScreen()
        }
    }

    private fun showLoginScreen() {
        Log.d(TAG, "Showing login screen")

        setContent {
            Ritik_2Theme {
                val authMode = remember { mutableStateOf(AuthMode.EMAIL_PASSWORD) }
                val showOtpScreen = remember { mutableStateOf(false) }
                val pendingCredential = remember { mutableStateOf<String?>(null) }

                if (showOtpScreen.value) {
                    OtpVerificationScreen(
                        authMode = authMode.value,
                        credential = pendingCredential.value ?: "",
                        onVerifyOtp = { otp ->
                            when (authMode.value) {
                                AuthMode.PHONE_OTP -> verifyPhoneOtp(otp)
                                AuthMode.EMAIL_OTP -> verifyEmailLink(pendingCredential.value ?: "", otp)
                                else -> {}
                            }
                        },
                        onResendOtp = {
                            when (authMode.value) {
                                AuthMode.PHONE_OTP -> resendPhoneOtp(pendingCredential.value ?: "")
                                AuthMode.EMAIL_OTP -> sendEmailOtp(pendingCredential.value ?: "")
                                else -> {}
                            }
                        },
                        onBackClick = {
                            showOtpScreen.value = false
                            verificationId = null
                        }
                    )
                } else {
                    LoginScreen(
                        authMode = authMode.value,
                        onAuthModeChange = { authMode.value = it },
                        onEmailPasswordLogin = { email, password ->
                            performEmailPasswordLogin(email, password)
                        },
                        onPhoneOtpLogin = { phoneNumber ->
                            pendingCredential.value = phoneNumber
                            initiatePhoneOtp(phoneNumber) { success ->
                                if (success) showOtpScreen.value = true
                            }
                        },
                        onEmailOtpLogin = { email ->
                            pendingCredential.value = email
                            sendEmailOtp(email) { success ->
                                if (success) showOtpScreen.value = true
                            }
                        },
                        onRegisterClick = {
                            Log.d(TAG, "Navigating to registration")
                            startActivity(Intent(this, RegistrationActivity::class.java))
                        },
                        onForgotPasswordClick = { email, callback ->
                            sendPasswordResetEmail(email, callback)
                        },
                        onInfoClick = {
                            Log.d(TAG, "Opening Contact/About page")
                            navigateToContactActivity()
                        }
                    )
                }
            }
        }
    }

    // EMAIL + PASSWORD LOGIN
    private fun performEmailPasswordLogin(email: String, password: String) {
        Log.d(TAG, "Attempting email/password login for: $email")

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password!", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase Auth login successful")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    saveLoginState()

                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        updateLastLogin(userId)
                        navigateToMainActivity()
                    }
                } else {
                    Log.e(TAG, "Login failed: ${task.exception?.message}")
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // PHONE OTP - INITIATE
    private fun initiatePhoneOtp(phoneNumber: String, onResult: (Boolean) -> Unit) {
        Log.d(TAG, "Initiating phone OTP for: $phoneNumber")

        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
            onResult(false)
            return
        }

        val formattedPhone = if (!phoneNumber.startsWith("+")) {
            "+91$phoneNumber"
        } else {
            phoneNumber
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "Phone verification completed automatically")
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Log.e(TAG, "Phone verification failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Verification failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                onResult(false)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "OTP sent successfully")
                this@LoginActivity.verificationId = verificationId
                this@LoginActivity.resendToken = token

                Toast.makeText(
                    this@LoginActivity,
                    "OTP sent to $formattedPhone",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(true)
            }
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // PHONE OTP - VERIFY
    private fun verifyPhoneOtp(otp: String) {
        Log.d(TAG, "Verifying phone OTP")

        if (otp.isEmpty() || otp.length != 6) {
            Toast.makeText(this, "Please enter valid 6-digit OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (verificationId == null) {
            Toast.makeText(this, "Please request OTP first", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId!!, otp)
        signInWithPhoneCredential(credential)
    }

    // PHONE OTP - RESEND
    private fun resendPhoneOtp(phoneNumber: String) {
        Log.d(TAG, "Resending phone OTP")

        val formattedPhone = if (!phoneNumber.startsWith("+")) {
            "+91$phoneNumber"
        } else {
            phoneNumber
        }

        if (resendToken == null) {
            // If no resend token, initiate new verification
            initiatePhoneOtp(phoneNumber) {}
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Toast.makeText(
                    this@LoginActivity,
                    "Failed to resend OTP: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@LoginActivity.verificationId = verificationId
                this@LoginActivity.resendToken = token
                Toast.makeText(this@LoginActivity, "OTP resent successfully", Toast.LENGTH_SHORT).show()
            }
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken!!)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        Log.d(TAG, "Signing in with phone credential")

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Phone authentication successful")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()

                    val user = task.result?.user
                    if (user != null) {
                        checkUserExistsInFirestore(user.uid, user.phoneNumber)
                    }
                } else {
                    Log.e(TAG, "Phone authentication failed", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // EMAIL OTP (PASSWORDLESS) - SEND
    private fun sendEmailOtp(email: String, onResult: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "Sending email OTP to: $email")

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter email address", Toast.LENGTH_SHORT).show()
            onResult?.invoke(false)
            return
        }

        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://yourapp.page.link/finishSignUp?email=$email") // Replace with your deep link
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                packageName,
                true, // Install app if not installed
                null // Minimum version
            )
            .build()

        firebaseAuth.sendSignInLinkToEmail(email, actionCodeSettings)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Email OTP sent successfully")

                    // Save email for verification
                    getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit()
                        .putString("emailForSignIn", email)
                        .apply()

                    emailForSignIn = email

                    Toast.makeText(
                        this,
                        "Verification link sent to $email. Please check your inbox.",
                        Toast.LENGTH_LONG
                    ).show()
                    onResult?.invoke(true)
                } else {
                    Log.e(TAG, "Failed to send email OTP", task.exception)
                    Toast.makeText(
                        this,
                        "Failed to send verification email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    onResult?.invoke(false)
                }
            }
    }

    // EMAIL OTP - VERIFY (This would be called when user opens the email link)
    private fun verifyEmailLink(email: String, link: String) {
        Log.d(TAG, "Verifying email link")

        if (firebaseAuth.isSignInWithEmailLink(link)) {
            firebaseAuth.signInWithEmailLink(email, link)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Email link authentication successful")
                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()

                        val user = task.result?.user
                        if (user != null) {
                            checkUserExistsInFirestore(user.uid, user.email)
                        }

                        // Clear saved email
                        getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit()
                            .remove("emailForSignIn")
                            .apply()
                    } else {
                        Log.e(TAG, "Email link verification failed", task.exception)
                        Toast.makeText(
                            this,
                            "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun checkUserExistsInFirestore(userId: String, credential: String?) {
        Log.d(TAG, "Checking if user exists in Firestore: $userId")

        firestore.collection("user_access_control")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "User found in Firestore")
                    saveLoginState()
                    updateLastLogin(userId)
                    navigateToMainActivity()
                } else {
                    Log.w(TAG, "User not found in Firestore")
                    Toast.makeText(
                        this,
                        "Account not registered. Please complete registration first.",
                        Toast.LENGTH_LONG
                    ).show()
                    firebaseAuth.signOut()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking user in Firestore", e)
                Toast.makeText(
                    this,
                    "Error checking user data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateLastLogin(userId: String) {
        Log.d(TAG, "Updating last login for user: $userId")

        val timestamp = Timestamp.now()

        firestore.collection("user_access_control").document(userId)
            .update("lastAccess", timestamp)
            .addOnSuccessListener {
                Log.d(TAG, "Last access updated in access control")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to update last access in access control", e)
            }

        firestore.collection("user_access_control").document(userId).get()
            .addOnSuccessListener { doc ->
                val documentPath = doc.getString("documentPath")
                if (!documentPath.isNullOrEmpty()) {
                    firestore.document(documentPath)
                        .update("lastLogin", timestamp)
                        .addOnSuccessListener {
                            Log.d(TAG, "Last login updated in user document")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Failed to update last login in user document", e)
                        }
                }
            }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navigating to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to main activity", e)
            Toast.makeText(this, "Error navigating to main screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToContactActivity() {
        try {
            val intent = Intent(this, ContactActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Successfully opened ContactActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening ContactActivity", e)
            Toast.makeText(this, "Unable to open contact page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPasswordResetEmail(email: String, callback: (Boolean) -> Unit) {
        Log.d(TAG, "Sending password reset email to: $email")

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Log.d(TAG, "Password reset email sent successfully")
                Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_SHORT).show()
                callback(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to send password reset email", exception)
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    private fun saveLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }
        Log.d(TAG, "Login state saved")
    }

    private fun clearLoginState() {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", false)
            apply()
        }
        Log.d(TAG, "Login state cleared")
    }

    override fun onBackPressed() {
        Log.d(TAG, "Back pressed - exiting app")
        finishAffinity()
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "LoginActivity resumed")

        val currentUser = authManager.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User authenticated on resume, navigating to MainActivity")
            navigateToMainActivity()
        }
    }
}

enum class AuthMode {
    EMAIL_PASSWORD,
    PHONE_OTP,
    EMAIL_OTP
}