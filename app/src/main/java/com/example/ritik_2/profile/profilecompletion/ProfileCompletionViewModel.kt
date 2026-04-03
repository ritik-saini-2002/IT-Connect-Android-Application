package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class ProfileCompletionUiState(
    val isLoading        : Boolean      = false,
    val userProfile      : UserProfile? = null,
    val selectedImageUri : Uri?         = null,
    val error            : String?      = null,
    val isSaved          : Boolean      = false,
    val isEditMode       : Boolean      = false,
    val isEditing        : Boolean      = false
)

data class ProfileSaveData(
    val address                  : String = "",
    val employeeId               : String = "",
    val reportingTo              : String = "",
    val salary                   : Double = 0.0,
    val experience               : Int    = 0,
    val emergencyContactName     : String = "",
    val emergencyContactPhone    : String = "",
    val emergencyContactRelation : String = "",
    val existingImageUrl         : String = "",
    // Admin-only fields
    val name                     : String = "",
    val phoneNumber              : String = "",
    val designation              : String = "",
    val companyName              : String = "",
    val department               : String = "",
    val role                     : String = ""
)

@HiltViewModel
class ProfileCompletionViewModel @Inject constructor(
    private val dataSource  : AppDataSource,
    private val pbDataSource: com.example.ritik_2.pocketbase.PocketBaseDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCompletionUiState())
    val uiState: StateFlow<ProfileCompletionUiState> = _uiState.asStateFlow()

    fun loadUser(userId: String, isEditMode: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isEditMode = isEditMode) }
            // When editing another user (isEditMode = true), the current user's token
            // may not have access to that record. Ensure admin token is active first.
            if (isEditMode) {
                try { pbDataSource.ensureAdminToken() } catch (_: Exception) {}
            }
            dataSource.getUserProfile(userId)
                .onSuccess { p ->
                    _uiState.update {
                        it.copy(
                            userProfile = p,
                            isLoading   = false,
                            isEditing   = !isEditMode  // first-time = always editing
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setSelectedImage(uri: Uri) = _uiState.update { it.copy(selectedImageUri = uri) }
    fun clearError()               = _uiState.update { it.copy(error = null) }
    fun toggleEditing()            = _uiState.update { it.copy(isEditing = !it.isEditing) }
    fun setEditing(v: Boolean)     = _uiState.update { it.copy(isEditing = v) }

    /**
     * Save the profile.
     *
     * [isAdmin]   = current user is Administrator  → can edit all fields
     * [isManager] = current user is Manager or HR  → can edit a restricted set
     *               (designation, department, experience, emergency contact, address)
     *               but NOT role, companyName, salary, employeeId, reportingTo
     */
    fun saveProfile(
        userId    : String,
        data      : ProfileSaveData,
        imageBytes: ByteArray?,
        isAdmin   : Boolean,
        isManager : Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            var imageUrl = data.existingImageUrl
            if (imageBytes != null) {
                dataSource.uploadProfileImage(userId, imageBytes, "profile_$userId.jpg", "")
                    .onSuccess { url -> imageUrl = url }
                    .onFailure { e ->
                        android.util.Log.w("ProfileVM", "Image upload failed: ${e.message}")
                    }
            }

            val existing = _uiState.value.userProfile

            val profileJson = JSONObject().apply {
                put("imageUrl",    imageUrl)
                put("address",     data.address)
                put("emergencyContactName",     data.emergencyContactName)
                put("emergencyContactPhone",    data.emergencyContactPhone)
                put("emergencyContactRelation", data.emergencyContactRelation)
                // phone: admin sets it freely; manager can't change it; user can change own
                put("phoneNumber", when {
                    isAdmin   -> data.phoneNumber
                    isManager -> existing?.phoneNumber ?: ""  // manager cannot change phone
                    else      -> data.phoneNumber              // own profile
                })
                // employeeId / reportingTo / salary — admin only
                put("employeeId",  if (isAdmin) data.employeeId  else existing?.employeeId  ?: "")
                put("reportingTo", if (isAdmin) data.reportingTo else existing?.reportingTo ?: "")
                put("salary",      if (isAdmin) data.salary       else existing?.salary      ?: 0.0)
            }.toString()

            val workJson = JSONObject().apply {
                put("experience",           data.experience)
                put("completedProjects",    existing?.completedProjects    ?: 0)
                put("activeProjects",       existing?.activeProjects       ?: 0)
                put("pendingTasks",         existing?.pendingTasks         ?: 0)
                put("completedTasks",       existing?.completedTasks       ?: 0)
                put("totalWorkingHours",    0)
                put("avgPerformanceRating", 0.0)
            }.toString()

            val fields = mutableMapOf<String, Any>(
                "profile"                to profileJson,
                "workStats"              to workJson,
                "needsProfileCompletion" to false
            )

            when {
                isAdmin -> {
                    // Administrator can change everything
                    if (data.name.isNotBlank())        fields["name"]        = data.name
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.role.isNotBlank())        fields["role"]        = data.role
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                    if (data.companyName.isNotBlank()) fields["companyName"] = data.companyName
                }
                isManager -> {
                    // Manager / HR can change designation and department only
                    // (role, company, salary, employeeId, reportingTo stay locked)
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                }
                // Regular user editing their own profile — no extra fields
            }

            dataSource.updateUserProfile(userId, fields)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSaved = true) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}