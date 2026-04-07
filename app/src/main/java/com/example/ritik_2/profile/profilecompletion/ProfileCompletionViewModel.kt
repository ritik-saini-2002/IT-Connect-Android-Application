package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.localdatabase.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class ProfileCompletionUiState(
    val isLoading        : Boolean      = false,
    val userProfile      : UserProfile? = null,
    val selectedImageUri : Uri?         = null,
    val croppedImageUri  : Uri?         = null,   // ← after crop
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
    private val pbDataSource: com.example.ritik_2.pocketbase.PocketBaseDataSource,
    private val db          : AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCompletionUiState())
    val uiState: StateFlow<ProfileCompletionUiState> = _uiState.asStateFlow()

    // ── Load user — cache-first, then server ──────────────────────────────────

    fun loadUser(userId: String, isEditMode: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isEditMode = isEditMode) }

            // 1. Show from local cache immediately
            val cached = withContext(Dispatchers.IO) { db.userDao().getById(userId) }
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        userProfile = cached.toUserProfile(),
                        isLoading   = false,
                        isEditing   = !isEditMode
                    )
                }
            }

            // 2. Refresh from server in background
            if (isEditMode) {
                try { pbDataSource.ensureAdminToken() } catch (_: Exception) {}
            }
            dataSource.getUserProfile(userId)
                .onSuccess { p ->
                    // Update server data into local cache
                    withContext(Dispatchers.IO) {
                        db.userDao().upsert(p.toUserEntity())
                    }
                    _uiState.update {
                        it.copy(
                            userProfile = p,
                            isLoading   = false,
                            isEditing   = !isEditMode
                        )
                    }
                }
                .onFailure { e ->
                    // If we already showed cache data, just log the error silently
                    if (cached == null) {
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                        android.util.Log.w("ProfileVM", "Server fetch failed, using cache: ${e.message}")
                    }
                }
        }
    }

    fun setSelectedImage(uri: Uri) =
        _uiState.update { it.copy(selectedImageUri = uri) }

    fun setCroppedImage(uri: Uri) =
        _uiState.update { it.copy(croppedImageUri = uri, selectedImageUri = uri) }

    fun clearError()           = _uiState.update { it.copy(error = null) }
    fun toggleEditing()        = _uiState.update { it.copy(isEditing = !it.isEditing) }
    fun setEditing(v: Boolean) = _uiState.update { it.copy(isEditing = v) }

    // ── Save profile — server + local cache ───────────────────────────────────

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

            when {
                isAdmin -> {
                    if (data.name.isNotBlank())        fields["name"]        = data.name
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.role.isNotBlank())        fields["role"]        = data.role
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                    if (data.companyName.isNotBlank()) fields["companyName"] = data.companyName
                }
                isManager -> {
                    if (data.designation.isNotBlank()) fields["designation"] = data.designation
                    if (data.department.isNotBlank())  fields["department"]  = data.department
                }
            }

            dataSource.updateUserProfile(userId, fields)
                .onSuccess {
                    // Update local Room cache immediately so other screens reflect the change
                    withContext(Dispatchers.IO) {
                        val cached = db.userDao().getById(userId)
                        if (cached != null) {
                            db.userDao().upsert(
                                cached.copy(
                                    imageUrl                 = imageUrl,
                                    address                  = data.address,
                                    emergencyContactName     = data.emergencyContactName,
                                    emergencyContactPhone    = data.emergencyContactPhone,
                                    emergencyContactRelation = data.emergencyContactRelation,
                                    experience               = data.experience,
                                    needsProfileCompletion   = false,
                                    // Admin-only fields
                                    name        = if (isAdmin && data.name.isNotBlank())
                                        data.name else cached.name,
                                    designation = if ((isAdmin || isManager) && data.designation.isNotBlank())
                                        data.designation else cached.designation,
                                    role        = if (isAdmin && data.role.isNotBlank())
                                        data.role else cached.role,
                                    department  = if ((isAdmin || isManager) && data.department.isNotBlank())
                                        data.department else cached.department,
                                    companyName = if (isAdmin && data.companyName.isNotBlank())
                                        data.companyName else cached.companyName,
                                    phoneNumber = when {
                                        isAdmin   -> data.phoneNumber
                                        isManager -> cached.phoneNumber
                                        else      -> data.phoneNumber
                                    },
                                    employeeId  = if (isAdmin) data.employeeId  else cached.employeeId,
                                    reportingTo = if (isAdmin) data.reportingTo else cached.reportingTo,
                                    salary      = if (isAdmin) data.salary       else cached.salary,
                                    pendingUpdate = true
                                )
                            )
                        }
                    }
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun UserEntity.toUserProfile() = UserProfile(
        id                       = id,
        name                     = name,
        email                    = email,
        role                     = role,
        companyName              = companyName,
        sanitizedCompany         = sanitizedCompanyName,
        department               = department,
        sanitizedDept            = sanitizedDepartment,
        designation              = designation,
        imageUrl                 = imageUrl,
        phoneNumber              = phoneNumber,
        address                  = address,
        employeeId               = employeeId,
        reportingTo              = reportingTo,
        salary                   = salary,
        experience               = experience,
        completedProjects        = completedProjects,
        activeProjects           = activeProjects,
        pendingTasks             = pendingTasks,
        completedTasks           = completedTasks,
        totalComplaints          = totalComplaints,
        resolvedComplaints       = resolvedComplaints,
        pendingComplaints        = pendingComplaints,
        isActive                 = isActive,
        documentPath             = documentPath,
        permissions              = permissions,
        emergencyContactName     = emergencyContactName,
        emergencyContactPhone    = emergencyContactPhone,
        emergencyContactRelation = emergencyContactRelation,
        needsProfileCompletion   = needsProfileCompletion
    )

    private fun UserProfile.toUserEntity(): UserEntity {
        val cached = db.userDao().let { it }
        return UserEntity(
            id                       = id,
            name                     = name,
            email                    = email,
            role                     = role,
            companyName              = companyName,
            sanitizedCompanyName     = sanitizedCompany,
            department               = department,
            sanitizedDepartment      = sanitizedDept,
            designation              = designation,
            imageUrl                 = imageUrl,
            phoneNumber              = phoneNumber,
            address                  = address,
            employeeId               = employeeId,
            reportingTo              = reportingTo,
            salary                   = salary,
            experience               = experience,
            completedProjects        = completedProjects,
            activeProjects           = activeProjects,
            pendingTasks             = pendingTasks,
            completedTasks           = completedTasks,
            totalComplaints          = totalComplaints,
            resolvedComplaints       = resolvedComplaints,
            pendingComplaints        = pendingComplaints,
            isActive                 = isActive,
            documentPath             = documentPath,
            permissions              = permissions,
            emergencyContactName     = emergencyContactName,
            emergencyContactPhone    = emergencyContactPhone,
            emergencyContactRelation = emergencyContactRelation,
            needsProfileCompletion   = needsProfileCompletion
        )
    }
}