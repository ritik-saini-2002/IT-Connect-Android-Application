package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class ProfileCompletionUiState(
    val isLoading       : Boolean      = false,
    val userProfile     : com.example.ritik_2.data.model.UserProfile? = null,
    val selectedImageUri: Uri?         = null,
    val error           : String?      = null,
    val isSaved         : Boolean      = false
)

// ── Only fields that are NOT already collected during registration ────────────
data class ProfileSaveData(
    val address                 : String = "",
    val employeeId              : String = "",
    val reportingTo             : String = "",
    val salary                  : Double = 0.0,
    val experience              : Int    = 0,
    val emergencyContactName    : String = "",
    val emergencyContactPhone   : String = "",
    val emergencyContactRelation: String = "",
    val existingImageUrl        : String = ""
    // name, designation, phoneNumber, role, companyName, department
    // are already saved during registerUser — not needed here
)

@HiltViewModel
class ProfileCompletionViewModel @Inject constructor(
    private val dataSource: AppDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCompletionUiState())
    val uiState: StateFlow<ProfileCompletionUiState> = _uiState.asStateFlow()

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            dataSource.getUserProfile(userId)
                .onSuccess  { profile -> _uiState.update { it.copy(userProfile = profile, isLoading = false) } }
                .onFailure  { e      -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun setSelectedImage(uri: Uri) = _uiState.update { it.copy(selectedImageUri = uri) }
    fun clearError()               = _uiState.update { it.copy(error = null) }

    fun saveProfile(userId: String, data: ProfileSaveData, imageBytes: ByteArray?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Upload avatar if a new image was picked — pass empty token,
            // uploadProfileImage will fall back to the current auth token
            var imageUrl = data.existingImageUrl
            if (imageBytes != null) {
                dataSource.uploadProfileImage(userId, imageBytes, "profile_$userId.jpg", "")
                    .onSuccess { url -> imageUrl = url }
                    .onFailure { e  -> android.util.Log.w("ProfileVM", "Image upload failed: ${e.message}") }
            }

            // Build profile JSON — preserve fields already set during registration
            val existingProfile = _uiState.value.userProfile
            val profileJson = JSONObject().apply {
                put("imageUrl",    imageUrl)
                // Keep phone number that was set during registration
                put("phoneNumber", existingProfile?.phoneNumber ?: "")
                put("address",     data.address)
                put("employeeId",  data.employeeId)
                put("reportingTo", data.reportingTo)
                put("salary",      data.salary)
                put("emergencyContactName",     data.emergencyContactName)
                put("emergencyContactPhone",    data.emergencyContactPhone)
                put("emergencyContactRelation", data.emergencyContactRelation)
            }.toString()

            val workJson = JSONObject().apply {
                // Keep existing work stats set during registration
                put("experience",           data.experience)
                put("completedProjects",    existingProfile?.completedProjects ?: 0)
                put("activeProjects",       existingProfile?.activeProjects    ?: 0)
                put("pendingTasks",         existingProfile?.pendingTasks      ?: 0)
                put("completedTasks",       existingProfile?.completedTasks    ?: 0)
                put("totalWorkingHours",    0)
                put("avgPerformanceRating", 0.0)
            }.toString()

            // Only update fields that profile completion is responsible for
            val fields = mapOf<String, Any>(
                "profile"   to profileJson,
                "workStats" to workJson
            )

            dataSource.updateUserProfile(userId, fields)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}