package com.example.ritik_2.data.source

import android.net.Uri

// ─────────────────────────────────────────────────────────────
// THIS IS THE ONLY FILE YOU CHANGE TO SWAP DATABASE
// Currently implemented by PocketBaseDataSource
// To switch to Supabase: create SupabaseDataSource : AppDataSource
// To switch to Firebase: create FirebaseDataSource : AppDataSource
// Then change AppModule to inject the new implementation
// ─────────────────────────────────────────────────────────────
interface AppDataSource {

    // ── Auth ──────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthResult
    suspend fun logout()
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun createUser(email: String, password: String, name: String): String
    suspend fun restoreSession(token: String)

    // ── User Records ──────────────────────────────────────────
    suspend fun updateUserRecord(userId: String, fields: Map<String, Any>): Result<Unit>
    suspend fun getUserRecord(userId: String): Result<UserDto>
    suspend fun getUserAccessControl(userId: String): Result<AccessControlDto>

    // ── Company ───────────────────────────────────────────────
    suspend fun companyExists(sanitizedName: String): Boolean
    suspend fun createCompany(dto: CompanyDto): Result<Unit>
    suspend fun incrementCompanyUsers(companyId: String, currentTotal: Int, currentActive: Int): Result<Unit>

    // ── Access Control ────────────────────────────────────────
    suspend fun createAccessControl(dto: AccessControlDto): Result<Unit>

    // ── Search Index ──────────────────────────────────────────
    suspend fun createSearchIndex(dto: SearchIndexDto): Result<Unit>

    // ── File Upload ───────────────────────────────────────────
    suspend fun uploadProfileImage(userId: String, bytes: ByteArray, filename: String): Result<String>
}

// ── Auth result wrapper ───────────────────────────────────────
data class AuthResult(
    val userId: String,
    val token: String,
    val email: String
)

// ── DTOs — database-agnostic data transfer objects ────────────
data class UserDto(
    val id: String           = "",
    val name: String         = "",
    val email: String        = "",
    val role: String         = "",
    val companyName: String  = "",
    val department: String   = "",
    val designation: String  = "",
    val isActive: Boolean    = true,
    val documentPath: String = "",
    val permissions: String  = "[]",
    val profile: String      = "{}",
    val workStats: String    = "{}",
    val issues: String       = "{}"
)

data class AccessControlDto(
    val id: String                   = "",
    val userId: String               = "",
    val name: String                 = "",
    val email: String                = "",
    val role: String                 = "",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val isActive: Boolean            = true,
    val documentPath: String         = "",
    val permissions: String          = "[]"
)

data class CompanyDto(
    val originalName: String  = "",
    val sanitizedName: String = "",
    val totalUsers: Int       = 1,
    val activeUsers: Int      = 1,
    val availableRoles: String = "[]",
    val departments: String   = "[]"
)

data class SearchIndexDto(
    val userId: String               = "",
    val name: String                 = "",
    val email: String                = "",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val role: String                 = "",
    val designation: String          = "",
    val searchTerms: String          = "[]",
    val documentPath: String         = ""
)