package com.example.ritik_2.data.repository

import android.net.Uri
import android.util.Log
import com.example.ritik_2.data.source.AccessControlDto
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.data.source.CompanyDto
import com.example.ritik_2.data.source.SearchIndexDto
import com.example.ritik_2.data.source.UserDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserRepository(private val dataSource: AppDataSource) {

    companion object {
        private const val TAG = "UserRepository"

        // ── Singleton — swap dataSource to change DB ──────────
        @Volatile private var INSTANCE: UserRepository? = null

        fun getInstance(): UserRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository(
                    com.example.ritik_2.data.source.PocketBaseDataSource()
                ).also { INSTANCE = it }
            }
    }

    // ── Get user profile (access control first, then full record)
    suspend fun getUserProfile(userId: String): Result<UserProfileDomain> {
        return try {
            val accessResult = dataSource.getUserAccessControl(userId)
            if (accessResult.isFailure) {
                return Result.failure(accessResult.exceptionOrNull()!!)
            }
            val access = accessResult.getOrThrow()

            if (!access.isActive) {
                return Result.failure(Exception("deactivated"))
            }

            val userResult = dataSource.getUserRecord(userId)
            if (userResult.isFailure) {
                // Fallback to access control data only
                return Result.success(
                    UserProfileDomain(
                        id          = userId,
                        name        = access.name,
                        email       = access.email,
                        role        = access.role,
                        companyName = access.companyName,
                        isActive    = access.isActive,
                        documentPath = access.documentPath,
                        permissions = parsePermissions(access.permissions)
                    )
                )
            }

            val user = userResult.getOrThrow()
            val profileJson  = parseJsonMap(user.profile)
            val workJson     = parseJsonMap(user.workStats)
            val issuesJson   = parseJsonMap(user.issues)

            Result.success(
                UserProfileDomain(
                    id                 = userId,
                    name               = user.name.ifBlank { access.name },
                    email              = access.email,
                    role               = access.role,
                    companyName        = user.companyName,
                    designation        = user.designation,
                    imageUrl           = profileJson["imageUrl"] ?: "",
                    phoneNumber        = profileJson["phoneNumber"] ?: "",
                    experience         = workJson["experience"]?.toIntOrNull() ?: 0,
                    completedProjects  = workJson["completedProjects"]?.toIntOrNull() ?: 0,
                    activeProjects     = workJson["activeProjects"]?.toIntOrNull() ?: 0,
                    pendingTasks       = workJson["pendingTasks"]?.toIntOrNull() ?: 0,
                    completedTasks     = workJson["completedTasks"]?.toIntOrNull() ?: 0,
                    totalComplaints    = issuesJson["totalComplaints"]?.toIntOrNull() ?: 0,
                    resolvedComplaints = issuesJson["resolvedComplaints"]?.toIntOrNull() ?: 0,
                    pendingComplaints  = issuesJson["pendingComplaints"]?.toIntOrNull() ?: 0,
                    isActive           = access.isActive,
                    documentPath       = access.documentPath,
                    permissions        = parsePermissions(access.permissions)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Register new user ─────────────────────────────────────
    suspend fun registerUser(params: RegistrationParams): Result<String> {
        return try {
            val sc = sanitize(params.companyName)
            val sd = sanitize(params.department)

            // 1. Check company uniqueness
            if (dataSource.companyExists(sc)) {
                return Result.failure(
                    Exception("Company '${params.companyName}' already exists.")
                )
            }

            // 2. Create auth user
            val userId = dataSource.createUser(params.email, params.password, params.name)

            // 3. Authenticate
            dataSource.login(params.email, params.password)

            // 4. Upload image
            var imageUrl = ""
            params.imageBytes?.let { bytes ->
                val uploadResult = dataSource.uploadProfileImage(
                    userId, bytes, "profile_$userId.jpg"
                )
                imageUrl = uploadResult.getOrDefault("")
            }

            // 5. Build JSON fields
            val documentPath = "users/$sc/$sd/${params.role}/$userId"
            val permissions  = Json.encodeToString(getRolePermissions(params.role))
            val profileJson  = buildProfileJson(imageUrl, params.phoneNumber)
            val workJson     = buildWorkJson(params.experience, params.completedProjects, params.activeProjects)
            val issuesJson   = buildIssuesJson(params.complaints)

            // 6. Update user record
            dataSource.updateUserRecord(
                userId,
                mapOf(
                    "userId"               to userId,
                    "role"                 to params.role,
                    "companyName"          to params.companyName,
                    "sanitizedCompanyName" to sc,
                    "department"           to params.department,
                    "sanitizedDepartment"  to sd,
                    "designation"          to params.designation,
                    "isActive"             to true,
                    "documentPath"         to documentPath,
                    "permissions"          to permissions,
                    "profile"              to profileJson,
                    "workStats"            to workJson,
                    "issues"               to issuesJson
                )
            )

            // 7. Upsert company
            upsertCompany(sc, params.companyName, params.role, params.department)

            // 8. Create access control
            dataSource.createAccessControl(
                AccessControlDto(
                    userId               = userId,
                    name                 = params.name,
                    email                = params.email,
                    role                 = params.role,
                    companyName          = params.companyName,
                    sanitizedCompanyName = sc,
                    department           = params.department,
                    sanitizedDepartment  = sd,
                    isActive             = true,
                    documentPath         = documentPath,
                    permissions          = permissions
                )
            )

            // 9. Create search index
            val searchTerms = Json.encodeToString(
                listOf(params.name, params.email, params.companyName,
                    params.department, params.role, params.designation)
                    .map { it.lowercase() }.filter { it.isNotEmpty() }
            )
            dataSource.createSearchIndex(
                SearchIndexDto(
                    userId               = userId,
                    name                 = params.name.lowercase(),
                    email                = params.email.lowercase(),
                    companyName          = params.companyName,
                    sanitizedCompanyName = sc,
                    department           = params.department,
                    sanitizedDepartment  = sd,
                    role                 = params.role,
                    designation          = params.designation,
                    searchTerms          = searchTerms,
                    documentPath         = documentPath
                )
            )

            Log.d(TAG, "User registered: $userId ✅")
            Result.success(userId)

        } catch (e: Exception) {
            Log.e(TAG, "registerUser failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Update profile ────────────────────────────────────────
    suspend fun updateProfile(userId: String, fields: Map<String, Any>): Result<Unit> =
        dataSource.updateUserRecord(userId, fields)

    // ── Helpers ───────────────────────────────────────────────
    private suspend fun upsertCompany(
        sc: String, originalName: String, role: String, department: String
    ) {
        try {
            val exists = dataSource.companyExists(sc)
            if (!exists) {
                dataSource.createCompany(
                    CompanyDto(
                        originalName   = originalName,
                        sanitizedName  = sc,
                        totalUsers     = 1,
                        activeUsers    = 1,
                        availableRoles = Json.encodeToString(listOf(role)),
                        departments    = Json.encodeToString(listOf(department))
                    )
                )
            }
            // Note: increment is handled separately if company exists
        } catch (e: Exception) {
            Log.w(TAG, "upsertCompany non-critical error: ${e.message}")
        }
    }

    private fun sanitize(input: String): String =
        input.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_').take(100)

    private fun parseJsonMap(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val clean = json.trim('{', '}')
            clean.split(",").forEach { pair ->
                val kv = pair.split(":")
                if (kv.size >= 2) {
                    val key   = kv[0].trim().trim('"')
                    val value = kv[1].trim().trim('"')
                    result[key] = value
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parsePermissions(raw: String): List<String> =
        try { Json.decodeFromString(raw) } catch (_: Exception) { emptyList() }

    private fun buildProfileJson(imageUrl: String, phoneNumber: String) =
        """{"imageUrl":"$imageUrl","phoneNumber":"$phoneNumber","address":"","employeeId":"","reportingTo":"","salary":0}"""

    private fun buildWorkJson(experience: Int, completed: Int, active: Int) =
        """{"experience":$experience,"completedProjects":$completed,"activeProjects":$active,"pendingTasks":0,"completedTasks":0,"totalWorkingHours":0,"avgPerformanceRating":0.0}"""

    private fun buildIssuesJson(complaints: Int) =
        """{"totalComplaints":$complaints,"resolvedComplaints":0,"pendingComplaints":$complaints}"""

    fun getRolePermissions(role: String): List<String> = when (role) {
        "Administrator" -> listOf(
            "create_user","delete_user","modify_user","view_all_users",
            "manage_roles","view_analytics","system_settings","manage_companies",
            "access_all_data","export_data","manage_permissions","access_admin_panel",
            "submit_complaints","view_all_complaints","resolve_complaints"
        )
        "Manager"   -> listOf("view_team_users","modify_team_user","view_team_analytics","assign_projects","approve_requests","view_reports","submit_complaints","view_department_complaints","resolve_complaints")
        "HR"        -> listOf("view_all_users","modify_user","view_hr_analytics","manage_employees","access_personal_data","generate_reports","submit_complaints","view_all_complaints","resolve_complaints")
        "Team Lead" -> listOf("view_team_users","assign_tasks","view_team_performance","approve_leave","submit_complaints","view_team_complaints")
        "Employee"  -> listOf("view_profile","edit_profile","view_assigned_projects","submit_reports","submit_complaints","view_own_complaints")
        "Intern"    -> listOf("view_profile","edit_basic_profile","view_assigned_tasks","submit_complaints")
        else        -> listOf("view_profile","edit_basic_profile")
    }
}

// ── Domain model — UI layer uses this, not DTOs directly ──────
data class UserProfileDomain(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val companyName: String,
    val designation: String         = "IT Professional",
    val imageUrl: String            = "",
    val phoneNumber: String         = "",
    val experience: Int             = 0,
    val completedProjects: Int      = 0,
    val activeProjects: Int         = 0,
    val pendingTasks: Int           = 0,
    val completedTasks: Int         = 0,
    val totalComplaints: Int        = 0,
    val resolvedComplaints: Int     = 0,
    val pendingComplaints: Int      = 0,
    val isActive: Boolean           = true,
    val documentPath: String        = "",
    val permissions: List<String>   = emptyList()
)

// ── Registration params ───────────────────────────────────────
data class RegistrationParams(
    val email: String,
    val password: String,
    val name: String,
    val phoneNumber: String,
    val designation: String,
    val companyName: String,
    val department: String,
    val role: String,
    val experience: Int        = 0,
    val completedProjects: Int = 0,
    val activeProjects: Int    = 0,
    val complaints: Int        = 0,
    val imageBytes: ByteArray? = null
)