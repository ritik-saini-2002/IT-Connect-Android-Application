package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.core.StringUtils
import com.example.ritik_2.core.parseJsonMap
import com.example.ritik_2.data.model.UserProfile
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
    val userProfile     : UserProfile? = null,
    val selectedImageUri: Uri?         = null,
    val error           : String?      = null,
    val isSaved         : Boolean      = false
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
                .onSuccess { profile ->
                    _uiState.update { it.copy(userProfile = profile, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setSelectedImage(uri: Uri) = _uiState.update { it.copy(selectedImageUri = uri) }
    fun clearError()               = _uiState.update { it.copy(error = null) }

    fun saveProfile(userId: String, data: ProfileSaveData, imageBytes: ByteArray?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Validate
            val validation = validate(data)
            if (validation != null) {
                _uiState.update { it.copy(isLoading = false, error = validation) }
                return@launch
            }

            // Upload image if selected
            var imageUrl = data.existingImageUrl
            imageBytes?.let { bytes ->
                dataSource.uploadProfileImage(userId, bytes, "profile_$userId.jpg")
                    .onSuccess { url -> imageUrl = url }
            }

            val profileJson = JSONObject().apply {
                put("imageUrl",    imageUrl)
                put("phoneNumber", data.phoneNumber)
                put("address",     data.address)
                put("employeeId",  data.employeeId)
                put("reportingTo", data.reportingTo)
                put("salary",      data.salary)
                put("emergencyContactName",     data.emergencyContactName)
                put("emergencyContactPhone",    data.emergencyContactPhone)
                put("emergencyContactRelation", data.emergencyContactRelation)
            }.toString()

            val workJson = JSONObject().apply {
                put("experience",         data.experience)
                put("completedProjects",  0)
                put("activeProjects",     0)
                put("pendingTasks",       0)
                put("completedTasks",     0)
                put("totalWorkingHours",  0)
                put("avgPerformanceRating", 0.0)
            }.toString()

            val fields = mapOf<String, Any>(
                "name"        to data.name,
                "designation" to data.designation,
                "profile"     to profileJson,
                "workStats"   to workJson
            )

            dataSource.updateUserProfile(userId, fields)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun validate(data: ProfileSaveData): String? = when {
        data.name.isBlank()        -> "Name is required"
        data.name.length < 2       -> "Name must be at least 2 characters"
        data.designation.isBlank() -> "Designation is required"
        else -> null
    }
}

data class ProfileSaveData(
    val name                    : String,
    val designation             : String,
    val phoneNumber             : String = "",
    val address                 : String = "",
    val employeeId              : String = "",
    val reportingTo             : String = "",
    val salary                  : Double = 0.0,
    val experience              : Int    = 0,
    val emergencyContactName    : String = "",
    val emergencyContactPhone   : String = "",
    val emergencyContactRelation: String = "",
    val existingImageUrl        : String = ""
)