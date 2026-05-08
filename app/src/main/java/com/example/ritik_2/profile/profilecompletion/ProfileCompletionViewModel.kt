package com.example.ritik_2.profile.profilecompletion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.core.AdminTokenProvider
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

data class ProfileCompletionUiState(
    val isLoading        : Boolean      = false,
    val userProfile      : UserProfile? = null,
    val selectedImageUri : Uri?         = null,
    val pendingImageBytes: ByteArray?   = null,
    val pendingImageName : String       = "",
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
    val name                     : String = "",
    val phoneNumber              : String = "",
    val designation              : String = "",
    val companyName              : String = "",
    val department               : String = "",
    val role                     : String = ""
)

@HiltViewModel
class ProfileCompletionViewModel @Inject constructor(
    private val dataSource        : AppDataSource,
    private val pbDataSource      : PocketBaseDataSource,
    private val http              : OkHttpClient,
    private val adminTokenProvider: AdminTokenProvider
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
                    _uiState.update { it.copy(userProfile = p, isLoading = false, isEditing = !isEditMode) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setSelectedImage(uri: Uri) = _uiState.update { it.copy(selectedImageUri = uri) }

    fun setSelectedImageBytes(bytes: ByteArray, filename: String) {
        _uiState.update { it.copy(pendingImageBytes = bytes, pendingImageName = filename) }
    }

    fun clearError()           = _uiState.update { it.copy(error = null) }
    fun toggleEditing()        = _uiState.update { it.copy(isEditing = !it.isEditing) }
    fun setEditing(v: Boolean) = _uiState.update { it.copy(isEditing = v) }

    /**
     * Update the target user's permissions, enforcing that the editor can only
     * grant permissions they themselves hold (or all if sysadmin/dbAdmin).
     * Both user_access_control and users records are kept in sync.
     */
    fun updateUserPermissions(
        targetUserId     : String,
        perms            : List<String>,
        editorPermissions: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Scope: editor can only grant perms they hold.
                // System-only permissions are already blocked in PermissionGuard.
                // Here we enforce it in the ViewModel as a second layer.
                val allowedToGrant = if (
                    Permissions.PERM_GRANT_REVOKE_ANY_PERMISSION in editorPermissions ||
                    editorPermissions.containsAll(Permissions.ALL_PERMISSIONS)
                ) {
                    perms  // sysadmin / DB admin path — no restriction
                } else {
                    // Regular admin: can only assign permissions from their own set
                    perms.filter { it in editorPermissions }
                }

                val permsJson = Json.encodeToString(allowedToGrant)

                // 1) user_access_control.permissions
                val acRes = pbGet(
                    "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                            "?filter=(userId='$targetUserId')&perPage=1"
                )
                val acId = JSONObject(acRes).optJSONArray("items")
                    ?.optJSONObject(0)?.optString("id")
                if (!acId.isNullOrEmpty()) {
                    pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        JSONObject().put("permissions", permsJson).toString()
                    )
                }

                // 2) users.permissions (kept in sync)
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/$targetUserId",
                    JSONObject().put("permissions", permsJson).toString()
                )

                loadUser(targetUserId, isEditMode = true)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to update permissions: ${e.message}")
                }
            }
        }
    }

    private suspend fun pbGet(url: String): String {
        val token = withContext(Dispatchers.IO) { adminTokenProvider.getAdminTokenSync() }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token").get().build()
            http.newCall(req).execute().use { it.body?.string() ?: "{}" }
        }
    }

    private suspend fun pbPatch(url: String, body: String) {
        val token = withContext(Dispatchers.IO) { adminTokenProvider.getAdminTokenSync() }
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .patch(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().close()
        }
    }

    fun saveProfile(
        userId        : String,
        data          : ProfileSaveData,
        imageBytes    : ByteArray?,
        isAdmin       : Boolean,
        isManager     : Boolean        = false,
        editableFields: Set<String>    = emptySet(),
        userToken     : String         = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // FIX: Upload avatar FIRST, get URL, then include in unified profile save.
            // Old code uploaded avatar then separately PATCHed profile JSON with ONLY
            // imageUrl, overwriting address/emergency/salary fields.
            var imageUrl = data.existingImageUrl
            val bytes    = imageBytes ?: _uiState.value.pendingImageBytes
            val fname    = _uiState.value.pendingImageName.ifBlank { "profile_$userId.jpg" }

            if (bytes != null) {
                dataSource.uploadProfileImage(userId, bytes, fname, userToken)
                    .onSuccess { url ->
                        imageUrl = url
                        android.util.Log.d("ProfileVM", "Avatar uploaded OK: $url")
                    }
                    .onFailure { e ->
                        android.util.Log.w("ProfileVM", "Image upload failed: ${e.message}")
                    }
            }

            val existing = _uiState.value.userProfile

            // FIX: Build COMPLETE profile JSON with all fields + new imageUrl
            // Build profile JSON — only include fields the editor is permitted to write.
            // editableFields comes from PermissionGuard.editableFields() in the Activity.
            // For own-profile with PERM_EDIT_BASIC_PROFILE, SELF_EDITABLE_FIELDS are allowed.
            val profileJson = JSONObject().apply {
                // imageUrl — always writable if they reached this screen
                put("imageUrl", imageUrl)

                // address — SELF_EDITABLE_FIELDS and above
                put("address", if ("address" in editableFields || isAdmin) data.address
                else existing?.address ?: "")

                // emergency contacts — SELF_EDITABLE_FIELDS and above
                put("emergencyContactName",
                    if ("emergencyContactName" in editableFields || isAdmin) data.emergencyContactName
                    else existing?.emergencyContactName ?: "")
                put("emergencyContactPhone",
                    if ("emergencyContactPhone" in editableFields || isAdmin) data.emergencyContactPhone
                    else existing?.emergencyContactPhone ?: "")
                put("emergencyContactRelation",
                    if ("emergencyContactRelation" in editableFields || isAdmin) data.emergencyContactRelation
                    else existing?.emergencyContactRelation ?: "")

                // phoneNumber — SELF_EDITABLE_FIELDS and above
                put("phoneNumber",
                    if ("phoneNumber" in editableFields || isAdmin) data.phoneNumber
                    else existing?.phoneNumber ?: "")

                // Admin-only fields
                put("employeeId",  if ("employeeId"  in editableFields || isAdmin) data.employeeId  else existing?.employeeId  ?: "")
                put("reportingTo", if ("reportingTo" in editableFields || isAdmin) data.reportingTo else existing?.reportingTo ?: "")
                put("salary",      if ("salary"      in editableFields || isAdmin) data.salary      else existing?.salary      ?: 0.0)
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
                    if ("name"        in editableFields && data.name.isNotBlank())        fields["name"]        = data.name
                    if ("designation" in editableFields && data.designation.isNotBlank()) fields["designation"] = data.designation
                    if ("role"        in editableFields && data.role.isNotBlank())        fields["role"]        = data.role
                    if ("department"  in editableFields && data.department.isNotBlank())  fields["department"]  = data.department
                    if ("companyName" in editableFields && data.companyName.isNotBlank()) fields["companyName"] = data.companyName
                }
                isManager -> {
                    if ("designation" in editableFields && data.designation.isNotBlank()) fields["designation"] = data.designation
                    if ("department"  in editableFields && data.department.isNotBlank())  fields["department"]  = data.department
                }
                else -> {
                    // Own-profile editor: only write top-level fields they're allowed to
                    if ("experience" in editableFields) {
                        // experience is in workStats JSON below — handled there already
                    }
                }
            }

            dataSource.updateUserProfile(userId, fields, userToken)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading         = false,
                            isSaved           = true,
                            selectedImageUri  = null,
                            pendingImageBytes = null,
                            pendingImageName  = ""
                        )
                    }
                }
                .onFailure { e ->
                    val msg = e.message ?: ""
                    // If the save failed due to an admin/auth/credential issue,
                    // don't expose that internal detail — show a friendly message instead.
                    val userFacingError = when {
                        msg.contains("admin",       ignoreCase = true) ||
                                msg.contains("credential",  ignoreCase = true) ||
                                msg.contains("token",       ignoreCase = true) ||
                                msg.contains("unauthorized",ignoreCase = true) ||
                                msg.contains("403",         ignoreCase = true) ||
                                msg.contains("401",         ignoreCase = true) -> "Profile could not be saved. Please contact your administrator."

                        msg.contains("timeout",     ignoreCase = true) ||
                                msg.contains("connect",     ignoreCase = true) ||
                                msg.contains("network",     ignoreCase = true) -> "No connection to server. Please try again."

                        else -> null  // ← any other error: show nothing, save silently failed
                    }
                    _uiState.update { it.copy(isLoading = false, error = userFacingError) }
                }
        }
    }
}