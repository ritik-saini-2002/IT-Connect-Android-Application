package com.example.ritik_2.administrator.rolemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
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

data class RoleManagementUiState(
    val isLoading    : Boolean           = false,
    val users        : List<UserProfile> = emptyList(),
    val filteredUsers: List<UserProfile> = emptyList(),
    val searchQuery  : String            = "",
    val successMsg   : String?           = null,
    val error        : String?           = null
)

@HiltViewModel
class RoleManagementViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val pbDataSource  : PocketBaseDataSource, // for cached admin token
    private val authRepository: AuthRepository,
    private val http          : OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(RoleManagementUiState())
    val state: StateFlow<RoleManagementUiState> = _state.asStateFlow()

    val availableRoles = Permissions.ALL_ROLES

    init { loadUsers() }

    // ── Use admin token for all operations ────────────────────────────────────
    // User token only has access to the current user's own record.
    // Changing OTHER users' roles requires admin token.
    // getAdminToken() is suspend — always call from a coroutine on Dispatchers.IO

    private fun loadUsers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session = authRepository.getSession()
                    ?: throw Exception("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                val token   = getAdminToken()
                val users   = fetchUsersForCompany(profile.sanitizedCompany, token)
                _state.update {
                    it.copy(isLoading = false, users = users, filteredUsers = users)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        _state.update { s ->
            val q        = query.trim()
            val filtered = if (q.isBlank()) s.users
            else s.users.filter {
                it.name.contains(q, ignoreCase = true)        ||
                        it.email.contains(q, ignoreCase = true)       ||
                        it.role.contains(q, ignoreCase = true)        ||
                        it.designation.contains(q, ignoreCase = true) ||
                        it.department.contains(q, ignoreCase = true)
            }
            s.copy(searchQuery = query, filteredUsers = filtered)
        }
    }

    fun changeUserRole(
        user   : UserProfile,
        newRole: String,
        onDone : (String, String, String) -> Unit
    ) {
        if (user.role == newRole) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val token     = getAdminToken()   // suspend — runs on IO
                val newPerms  = Permissions.forRole(newRole)
                val permsJson = Json.encodeToString(newPerms)
                val newPath   = "users/${user.sanitizedCompany}/${user.sanitizedDept}/$newRole/${user.id}"

                // 1. PATCH users record
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token,
                    JSONObject().apply {
                        put("role",         newRole)
                        put("permissions",  permsJson)
                        put("documentPath", newPath)
                    }.toString()
                )

                // 2. PATCH user_access_control record
                val acBody = pbGet(
                    "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                            "?filter=(userId='${user.id}')&perPage=1",
                    token
                )
                val acId = JSONObject(acBody)
                    .optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!acId.isNullOrEmpty()) {
                    pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        token,
                        JSONObject().apply {
                            put("role",         newRole)
                            put("permissions",  permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                }

                // 3. PATCH user_search_index record
                val siBody = pbGet(
                    "${AppConfig.BASE_URL}/api/collections/user_search_index/records" +
                            "?filter=(userId='${user.id}')&perPage=1",
                    token
                )
                val siId = JSONObject(siBody)
                    .optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!siId.isNullOrEmpty()) {
                    pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/user_search_index/records/$siId",
                        token,
                        JSONObject().apply {
                            put("role",         newRole)
                            put("documentPath", newPath)
                        }.toString()
                    )
                }

                // Update local state so UI reflects change immediately
                val updated = _state.value.users.map {
                    if (it.id == user.id)
                        it.copy(role = newRole, permissions = newPerms, documentPath = newPath)
                    else it
                }
                _state.update {
                    it.copy(
                        isLoading     = false,
                        users         = updated,
                        filteredUsers = applySearch(updated, it.searchQuery),
                        successMsg    = "${user.name} is now $newRole"
                    )
                }
                onDone(user.name, user.role, newRole)

            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Role change failed: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(successMsg = null, error = null) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applySearch(users: List<UserProfile>, query: String): List<UserProfile> {
        val q = query.trim()
        return if (q.isBlank()) users
        else users.filter {
            it.name.contains(q, true) || it.email.contains(q, true) ||
                    it.role.contains(q, true) || it.designation.contains(q, true)
        }
    }

    private suspend fun fetchUsersForCompany(sc: String, token: String): List<UserProfile> =
        withContext(Dispatchers.IO) {
            val res   = pbGet(
                "${AppConfig.BASE_URL}/api/collections/users/records" +
                        "?filter=(sanitizedCompanyName='$sc')&perPage=200",
                token
            )
            val items = JSONObject(res).optJSONArray("items")
                ?: return@withContext emptyList()

            (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val o       = items.getJSONObject(i)
                    val uid     = o.optString("id")
                    val profile = runCatching {
                        JSONObject(o.optString("profile", "{}"))
                    }.getOrDefault(JSONObject())
                    val avatarFile = profile.optString("imageUrl", "")
                        .substringAfterLast("/", "")

                    UserProfile(
                        id               = uid,
                        name             = o.optString("name"),
                        email            = o.optString("email"),
                        role             = o.optString("role"),
                        companyName      = o.optString("companyName"),
                        sanitizedCompany = o.optString("sanitizedCompanyName"),
                        department       = o.optString("department"),
                        sanitizedDept    = o.optString("sanitizedDepartment"),
                        designation      = o.optString("designation"),
                        isActive         = o.optBoolean("isActive", true),
                        documentPath     = o.optString("documentPath"),
                        imageUrl         = profile.optString("imageUrl", ""),
                        needsProfileCompletion = o.optBoolean("needsProfileCompletion", false)
                    )
                }.getOrNull()
            }
        }

    // Fetches admin token on IO thread — must always be called from a suspend context
    private suspend fun getAdminToken(): String = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        )
        for (url in endpoints) {
            try {
                val body = JSONObject().apply {
                    put("identity", AppConfig.ADMIN_EMAIL)
                    put("password", AppConfig.ADMIN_PASS)
                }.toString().toRequestBody("application/json".toMediaType())
                val res     = http.newCall(
                    Request.Builder().url(url).post(body).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful
                res.close()
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) return@withContext t
                }
            } catch (e: Exception) {
                android.util.Log.w("RoleManagementVM", "Admin auth failed at $url: ${e.message}")
            }
        }
        error("Could not obtain admin token — check ADMIN_EMAIL/ADMIN_PASS in local.properties")
    }

    private suspend fun pbGet(url: String, token: String): String =
        withContext(Dispatchers.IO) {
            val res = http.newCall(
                Request.Builder().url(url).get()
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute()
            val body = res.body?.string() ?: "{}"
            res.close()
            body
        }

    private suspend fun pbPatch(url: String, token: String, body: String) =
        withContext(Dispatchers.IO) {
            val res = http.newCall(
                Request.Builder().url(url)
                    .patch(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute()
            val resBody = res.body?.string() ?: ""
            val code    = res.code
            res.close()
            if (!res.isSuccessful)
                error("PATCH failed HTTP $code: $resBody")
        }
}