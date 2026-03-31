package com.example.ritik_2.profile.profilecompletion.components

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Local model classes (self-contained, no external dependency) ──────────────

data class UserModel(
    val userId: String               = "",
    val email: String                = "",
    val name: String                 = "",
    val role: String                 = "Employee",
    val companyName: String          = "",
    val sanitizedCompanyName: String = "",
    val department: String           = "",
    val sanitizedDepartment: String  = "",
    val designation: String          = "",
    val documentPath: String         = "",
    val isActive: Boolean            = true,
    val createdAt: Long              = 0L,
    val lastUpdated: Long            = 0L,
    val createdBy: String            = ""
)

data class UserProfileModel(
    val userId: String      = "",
    val imageUrl: String    = "",
    val phoneNumber: String = "",
    val address: String     = "",
    val dateOfBirth: Long?  = null,
    val joiningDate: Long?  = null,
    val employeeId: String  = "",
    val reportingTo: String = "",
    val salary: Double      = 0.0,
    val emergencyContactName: String     = "",
    val emergencyContactPhone: String    = "",
    val emergencyContactRelation: String = ""
)

data class WorkStatsModel(
    val userId: String = "",
    val experience: Int = 0,
    val completedProjects: Int = 0,
    val activeProjects: Int    = 0,
    val pendingTasks: Int      = 0,
    val completedTasks: Int    = 0,
    val totalWorkingHours: Int = 0,
    val avgPerformanceRating: Double = 0.0
)

// ── UI State ──────────────────────────────────────────────────────────────────

data class ProfileCompletionUiState(
    val isLoading: Boolean             = false,
    val currentProfile: UserProfileModel? = null,
    val currentWorkStats: WorkStatsModel? = null,
    val selectedImageUri: Uri?         = null,
    val error: String?                 = null,
    val dataExists: Boolean            = false,
    val isDataLoaded: Boolean          = false,
    val isNewUser: Boolean             = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ProfileCompletionViewModel : ViewModel() {

    private val _uiState     = MutableStateFlow(ProfileCompletionUiState())
    val uiState: StateFlow<ProfileCompletionUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserModel?>(null)
    val currentUser: StateFlow<UserModel?> = _currentUser.asStateFlow()

    fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    fun setCurrentUser(user: UserModel) {
        _currentUser.value = user
        _uiState.value = _uiState.value.copy(isDataLoaded = true)
    }

    fun setCurrentProfile(profile: UserProfileModel) {
        _uiState.value = _uiState.value.copy(currentProfile = profile, isDataLoaded = true)
    }

    fun setCurrentWorkStats(stats: WorkStatsModel) {
        _uiState.value = _uiState.value.copy(currentWorkStats = stats, isDataLoaded = true)
    }

    fun setSelectedImageUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun setError(error: String?) {
        _uiState.value = _uiState.value.copy(error = error)
    }

    fun setDataExists(exists: Boolean) {
        _uiState.value = _uiState.value.copy(dataExists = exists)
    }

    fun setNewUser(isNew: Boolean) {
        _uiState.value = _uiState.value.copy(isNewUser = isNew)
    }

    fun clearSelectedImage() {
        _uiState.value = _uiState.value.copy(selectedImageUri = null)
    }

    fun initializeNewUser(userId: String, email: String, displayName: String? = null) {
        val newUser = UserModel(
            userId               = userId,
            email                = email,
            name                 = displayName ?: "",
            role                 = "Administrator",
            companyName          = "",
            sanitizedCompanyName = "",
            department           = "",
            sanitizedDepartment  = "",
            designation          = "",
            documentPath         = "",
            isActive             = true,
            createdAt            = System.currentTimeMillis(),
            lastUpdated          = System.currentTimeMillis(),
            createdBy            = userId
        )
        _currentUser.value = newUser
        _uiState.value = _uiState.value.copy(
            isDataLoaded = true,
            dataExists   = false,
            isNewUser    = true
        )
    }

    // ── Called from ProfileCompletionActivity after PocketBase load ────────────
    fun initializeFromPocketBase(
        userId: String,
        name: String,
        email: String,
        role: String,
        companyName: String,
        department: String,
        designation: String,
        profileJson: String,
        workJson: String
    ) {
        val imageUrl    = extractString(profileJson, "imageUrl")
        val phoneNumber = extractString(profileJson, "phoneNumber")
        val address     = extractString(profileJson, "address")
        val employeeId  = extractString(profileJson, "employeeId")
        val reportingTo = extractString(profileJson, "reportingTo")
        val salary      = extractDouble(profileJson, "salary")
        val experience  = extractInt(workJson, "experience")

        val sc = sanitizeId(companyName)
        val sd = sanitizeId(department)

        val user = UserModel(
            userId               = userId,
            email                = email,
            name                 = name,
            role                 = role,
            companyName          = companyName,
            sanitizedCompanyName = sc,
            department           = department,
            sanitizedDepartment  = sd,
            designation          = designation,
            documentPath         = "users/$sc/$sd/$role/$userId",
            isActive             = true,
            createdAt            = System.currentTimeMillis(),
            lastUpdated          = System.currentTimeMillis(),
            createdBy            = userId
        )

        val profile = UserProfileModel(
            userId      = userId,
            imageUrl    = imageUrl,
            phoneNumber = phoneNumber,
            address     = address,
            employeeId  = employeeId,
            reportingTo = reportingTo,
            salary      = salary
        )

        val stats = WorkStatsModel(
            userId     = userId,
            experience = experience
        )

        _currentUser.value = user
        _uiState.value = _uiState.value.copy(
            currentProfile   = profile,
            currentWorkStats = stats,
            isDataLoaded     = true,
            dataExists       = true,
            isNewUser        = false
        )
    }

    // ── Tiny JSON extractors ───────────────────────────────────────────────────
    private fun extractString(json: String, key: String): String =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1) ?: ""

    private fun extractInt(json: String, key: String): Int =
        Regex(""""$key"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun extractDouble(json: String, key: String): Double =
        Regex(""""$key"\s*:\s*([\d.]+)""").find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private fun sanitizeId(input: String): String =
        input.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_").trim('_').take(100)
}

// ── ProfileData (shared with Activity/Screen) ─────────────────────────────────

data class ProfileData(
    val name: String                     = "",
    val email: String                    = "",
    val role: String                     = "Employee",
    val companyName: String              = "",
    val department: String               = "",
    val designation: String              = "",
    val phoneNumber: String              = "",
    val address: String                  = "",
    val dateOfBirth: Long?               = null,
    val joiningDate: Long?               = null,
    val employeeId: String               = "",
    val reportingTo: String              = "",
    val salary: Double                   = 0.0,
    val emergencyContactName: String     = "",
    val emergencyContactPhone: String    = "",
    val emergencyContactRelation: String = "",
    val experience: Int                  = 0,
    val imageUrl: String                 = "",
    val imageUri: Uri?                   = null
)