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
    val pendingImageBytes: ByteArray?   = null,   // ← set after crop
    val pendingImageName : String       = "",      // ← filename for upload
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

    /** Called when the user picks an image URI (before crop) */
    fun setSelectedImage(uri: Uri) = _uiState.update { it.copy(selectedImageUri = uri) }

    /** Called after the crop dialog produces final bytes */
    fun setSelectedImageBytes(bytes: ByteArray, filename: String) {
        _uiState.update { it.copy(pendingImageBytes = bytes, pendingImageName = filename) }
    }

    fun clearError()           = _uiState.update { it.copy(error = null) }
    fun toggleEditing()        = _uiState.update { it.copy(isEditing = !it.isEditing) }
    fun setEditing(v: Boolean) = _uiState.update { it.copy(isEditing = v) }

    /**
     * Save the profile.
     *
     * [isAdmin]   = current user is Administrator or DB admin
     * [isManager] = current user is Manager or HR editing a permitted target
     *
     * The function respects PermissionGuard field sets — fields not in the
     * allowed set are silently dropped rather than overwriting server data.
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

            // ── Upload image if we have pending bytes ─────────────────────────
            var imageUrl = data.existingImageUrl
            val bytes    = imageBytes ?: _uiState.value.pendingImageBytes
            val fname    = _uiState.value.pendingImageName.ifBlank {
                "profile_$userId.jpg"
            }
            if (bytes != null) {
                dataSource.uploadProfileImage(userId, bytes, fname, "")
                    .onSuccess { url -> imageUrl = url }
                    .onFailure { e ->
                        android.util.Log.w("ProfileVM", "Image upload failed: ${e.message}")
                    }
            }

            val existing = _uiState.value.userProfile

            // ── Build profile JSON (merge-safe) ───────────────────────────────
            val profileJson = JSONObject().apply {
                put("imageUrl",    imageUrl)
                put("address",     data.address)
                put("emergencyContactName",     data.emergencyContactName)
                put("emergencyContactPhone",    data.emergencyContactPhone)
                put("emergencyContactRelation", data.emergencyContactRelation)
                put("phoneNumber", when {
                    isAdmin   -> data.phoneNumber
                    isManager -> existing?.phoneNumber ?: ""
                    else      -> data.phoneNumber
                })
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

            // ── Apply role-specific field permissions ─────────────────────────
            when {
                isAdmin -> {
                    if (data.name.isNotBlank())        fields["name"]        = data.name
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.role.isNotBlank())        fields["role"]        = data.role
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                    if (data.companyName.isNotBlank()) fields["companyName"] = data.companyName
                }
                isManager -> {
                    // Manager/HR: designation + department only
                    // CANNOT change role from profile screen — use RoleManagement
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                }
                // Regular user — no extra fields beyond what's in profileJson/workJson
            }

            dataSource.updateUserProfile(userId, fields)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading         = false,
                            isSaved           = true,
                            pendingImageBytes = null,
                            pendingImageName  = ""
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}