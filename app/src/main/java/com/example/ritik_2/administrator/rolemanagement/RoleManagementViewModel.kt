package com.example.ritik_2.administrator.rolemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
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

data class RoleManagementUiState(
    val isLoading     : Boolean           = false,
    val users         : List<UserProfile> = emptyList(),
    val filteredUsers : List<UserProfile> = emptyList(),
    val searchQuery   : String            = "",
    val successMsg    : String?           = null,
    val error         : String?           = null
)

@HiltViewModel
class RoleManagementViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val http          : OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(RoleManagementUiState())
    val state: StateFlow<RoleManagementUiState> = _state.asStateFlow()

    val availableRoles = Permissions.ALL_ROLES

    init { loadUsers() }

    private fun token() = authRepository.getSession()?.token ?: ""

    private fun loadUsers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session = authRepository.getSession() ?: throw Exception("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                val users   = fetchUsersForCompany(profile.sanitizedCompany)
                _state.update { it.copy(isLoading = false, users = users, filteredUsers = users) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        _state.update { s ->
            val filtered = if (query.isBlank()) s.users
            else s.users.filter {
                it.name.contains(query, true) || it.email.contains(query, true) ||
                        it.role.contains(query, true) || it.designation.contains(query, true)
            }
            s.copy(searchQuery = query, filteredUsers = filtered)
        }
    }

    fun changeUserRole(user: UserProfile, newRole: String, onDone: (String, String, String) -> Unit) {
        if (user.role == newRole) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val newPerms  = Permissions.forRole(newRole)
                val permsJson = Json.encodeToString(newPerms)
                val newPath   = "users/${user.sanitizedCompany}/${user.sanitizedDept}/$newRole/${user.id}"

                // 1. PATCH users
                pbPatch("${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token(), JSONObject().apply {
                        put("role", newRole); put("permissions", permsJson); put("documentPath", newPath)
                    }.toString())

                // 2. PATCH user_access_control  ✅ correct name
                val acRes = pbGet("${AppConfig.BASE_URL}/api/collections/user_access_control/records?filter=(userId='${user.id}')", token())
                val acId  = JSONObject(acRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!acId.isNullOrEmpty())
                    pbPatch("${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        token(), JSONObject().apply {
                            put("role", newRole); put("permissions", permsJson); put("documentPath", newPath)
                        }.toString())

                // 3. PATCH user_search_index  ✅ correct name
                val siRes = pbGet("${AppConfig.BASE_URL}/api/collections/user_search_index/records?filter=(userId='${user.id}')", token())
                val siId  = JSONObject(siRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!siId.isNullOrEmpty())
                    pbPatch("${AppConfig.BASE_URL}/api/collections/user_search_index/records/$siId",
                        token(), JSONObject().apply { put("role", newRole); put("documentPath", newPath) }.toString())

                val updated = _state.value.users.map {
                    if (it.id == user.id) it.copy(role = newRole, permissions = newPerms, documentPath = newPath)
                    else it
                }
                _state.update { it.copy(isLoading = false, users = updated, filteredUsers = updated,
                    successMsg = "${user.name} → $newRole") }
                onDone(user.name, user.role, newRole)

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Role change failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(successMsg = null, error = null) }

    private suspend fun fetchUsersForCompany(sc: String): List<UserProfile> =
        withContext(Dispatchers.IO) {
            val res   = pbGet("${AppConfig.BASE_URL}/api/collections/users/records?filter=(sanitizedCompanyName='$sc')&perPage=200", token())
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val o       = items.getJSONObject(i)
                    val profile = runCatching { JSONObject(o.optString("profile", "{}")) }.getOrDefault(JSONObject())
                    UserProfile(
                        id               = o.optString("id"),
                        name             = o.optString("name"),
                        email            = o.optString("email"),
                        role             = o.optString("role"),
                        companyName      = o.optString("companyName"),
                        sanitizedCompany = o.optString("sanitizedCompanyName"),
                        department       = o.optString("department"),
                        sanitizedDept    = o.optString("sanitizedDepartment"),
                        designation      = o.optString("designation"),
                        imageUrl         = AppConfig.avatarUrl(o.optString("id"), profile.optString("avatar")) ?: "",
                        needsProfileCompletion = o.optBoolean("needsProfileCompletion", false)
                    )
                }.getOrNull()
            }
        }

    private suspend fun pbGet(url: String, token: String): String = withContext(Dispatchers.IO) {
        val res = http.newCall(Request.Builder().url(url).get()
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val b = res.body?.string() ?: "{}"; res.close(); b
    }

    private suspend fun pbPatch(url: String, token: String, body: String) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url)
            .patch(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token").build()).execute().close()
    }
}