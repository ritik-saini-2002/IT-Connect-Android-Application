package com.example.ritik_2.data.source

import com.example.ritik_2.data.model.AuthSession
import com.example.ritik_2.data.model.RegistrationRequest
import com.example.ritik_2.data.model.UserProfile

interface AppDataSource {

    // ── Auth ──────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthSession
    suspend fun sendLoginOtp(email: String): Result<Unit>
    suspend fun loginWithOtp(email: String, otp: String): Result<AuthSession>
    suspend fun logout()
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun sendOtp(email: String): Result<Unit>
    suspend fun verifyOtp(email: String, otp: String): Result<Unit>
    suspend fun verifyOtpAndResetPassword(email: String, otp: String, newPassword: String): Result<Unit>

    suspend fun createUser(email: String, password: String, name: String, adminToken: String = ""): String
    suspend fun restoreSession(token: String)   // ← must return Unit, nothing else

    // ── User ──────────────────────────────────────────────────
    suspend fun getUserProfile(userId: String): Result<UserProfile>
    suspend fun updateUserProfile(userId: String, fields: Map<String, Any>): Result<Unit>
    suspend fun uploadProfileImage(userId: String, bytes: ByteArray, filename: String, token: String = ""): Result<String>

    // ── Registration ──────────────────────────────────────────
    suspend fun registerUser(request: RegistrationRequest): Result<String>

    // ── Company ───────────────────────────────────────────────
    suspend fun companyExists(sanitizedName: String): Boolean
    suspend fun getOrCreateCompany(
        sanitizedName: String,
        originalName : String,
        role         : String,
        department   : String,
        adminToken   : String = ""
    ): Result<Unit>

    // ── Collections setup (admin only) ────────────────────────
    suspend fun ensureCollectionsExist(): Result<Unit>
}