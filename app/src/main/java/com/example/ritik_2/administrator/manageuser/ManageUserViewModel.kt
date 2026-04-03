package com.example.ritik_2.administrator.manageuser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.administrator.manageuser.models.*
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ManageUserViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val http          : OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(ManageUserUiState())
    val state: StateFlow<ManageUserUiState> = _state.asStateFlow()

    init { loadCurrentUser() }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session = authRepository.getSession()
                    ?: throw Exception("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                _state.update { it.copy(currentRole = profile.role) }
                val token = getAdminToken()
                if (profile.role == "Administrator") loadAllUsers(token)
                else loadCompanyUsers(profile.sanitizedCompany, token)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadAllUsers(token: String) {
        val res = pbGet(
            "${AppConfig.BASE_URL}/api/collections/users/records" +
                    "?perPage=500&sort=companyName", token
        )
        processUsersResponse(res)
    }

    private suspend fun loadCompanyUsers(sc: String, token: String) {
        val res = pbGet(
            "${AppConfig.BASE_URL}/api/collections/users/records" +
                    "?filter=(sanitizedCompanyName='$sc')&perPage=500", token
        )
        processUsersResponse(res)
    }

    private fun processUsersResponse(res: String) {
        val items = JSONObject(res).optJSONArray("items") ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }

        val allUsers = (0 until items.length()).mapNotNull { i ->
            runCatching {
                val o       = items.getJSONObject(i)
                val profile = runCatching {
                    JSONObject(o.optString("profile", "{}"))
                }.getOrDefault(JSONObject())
                val work    = runCatching {
                    JSONObject(o.optString("workStats", "{}"))
                }.getOrDefault(JSONObject())
                val issues  = runCatching {
                    JSONObject(o.optString("issues", "{}"))
                }.getOrDefault(JSONObject())

                MUUser(
                    id                = o.optString("id"),
                    name              = o.optString("name").ifBlank { o.optString("email") },
                    email             = o.optString("email"),
                    role              = o.optString("role"),
                    companyName       = o.optString("sanitizedCompanyName"),
                    deptName          = o.optString("sanitizedDepartment"),
                    designation       = o.optString("designation"),
                    imageUrl          = profile.optString("imageUrl", ""),
                    phoneNumber       = profile.optString("phoneNumber", ""),
                    experience        = work.optInt("experience"),
                    activeProjects    = work.optInt("activeProjects"),
                    completedProjects = work.optInt("completedProjects"),
                    totalComplaints   = issues.optInt("totalComplaints"),
                    isActive          = o.optBoolean("isActive", true),
                    documentPath      = o.optString("documentPath"),
                    originalCompany   = o.optString("companyName"),
                    originalDept      = o.optString("department")
                )
            }.getOrNull()
        }

        val companies = allUsers
            .groupBy { it.companyName }
            .map { (sc, users) ->
                MUCompany(
                    sanitizedName = sc,
                    originalName  = users.firstOrNull()?.originalCompany ?: sc,
                    totalUsers    = users.size,
                    activeUsers   = users.count { it.isActive }
                )
            }

        _state.update {
            it.copy(
                isLoading     = false,
                companies     = companies,
                users         = allUsers,
                filteredUsers = allUsers
            )
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(query: String) {
        _state.update { s ->
            val q        = query.trim()
            val filtered = if (q.isBlank()) s.users
            else s.users.filter {
                it.name.contains(q, true)        ||
                        it.email.contains(q, true)       ||
                        it.role.contains(q, true)        ||
                        it.designation.contains(q, true) ||
                        it.originalCompany.contains(q, true) ||
                        it.originalDept.contains(q, true)
            }
            s.copy(searchQuery = query, filteredUsers = filtered)
        }
    }

    // ── Tree expand/collapse ──────────────────────────────────────────────────

    fun toggleCompany(sc: String) {
        val cur = _state.value.expandedCompanies.toMutableSet()
        if (!cur.add(sc)) cur.remove(sc)
        _state.update { it.copy(expandedCompanies = cur) }
    }

    fun toggleDepartment(sc: String, sd: String) {
        val key = "$sc|$sd"
        val cur = _state.value.expandedDepartments.toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        _state.update { it.copy(expandedDepartments = cur) }
    }

    fun toggleRole(sc: String, sd: String, role: String) {
        val key = "$sc|$sd|$role"
        val cur = _state.value.expandedRoles.toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        _state.update { it.copy(expandedRoles = cur) }
    }

    // ── Tree data helpers ─────────────────────────────────────────────────────

    fun getDepts(sc: String): List<MUDepartment> {
        val source = if (_state.value.searchQuery.isBlank())
            _state.value.users else _state.value.filteredUsers
        return source
            .filter { it.companyName == sc }
            .groupBy { it.deptName }
            .map { (sd, users) ->
                MUDepartment(
                    sanitizedName  = sd,
                    departmentName = users.firstOrNull()?.originalDept ?: sd,
                    companyName    = sc,
                    userCount      = users.size,
                    activeUsers    = users.count { it.isActive },
                    roles          = users.map { it.role }.distinct()
                )
            }
    }

    fun getRoles(sc: String, sd: String): List<MURoleInfo> {
        val source = if (_state.value.searchQuery.isBlank())
            _state.value.users else _state.value.filteredUsers
        return source
            .filter { it.companyName == sc && it.deptName == sd }
            .groupBy { it.role }
            .map { (role, users) ->
                MURoleInfo(
                    roleName    = role,
                    companyName = sc,
                    deptName    = sd,
                    userCount   = users.size,
                    activeUsers = users.count { it.isActive }
                )
            }
    }

    fun getUsers(sc: String, sd: String, role: String): List<MUUser> {
        val source = if (_state.value.searchQuery.isBlank())
            _state.value.users else _state.value.filteredUsers
        return source.filter {
            it.companyName == sc && it.deptName == sd && it.role == role
        }
    }

    fun getFilteredCompanies(): List<MUCompany> {
        val source = if (_state.value.searchQuery.isBlank())
            _state.value.users else _state.value.filteredUsers
        return source
            .groupBy { it.companyName }
            .map { (sc, users) ->
                MUCompany(
                    sanitizedName = sc,
                    originalName  = users.firstOrNull()?.originalCompany ?: sc,
                    totalUsers    = users.size,
                    activeUsers   = users.count { it.isActive }
                )
            }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun toggleUserStatus(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val token     = getAdminToken()
                val newStatus = !user.isActive
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token,
                    JSONObject().apply { put("isActive", newStatus) }.toString()
                )
                runCatching {
                    val acRes = pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                "?filter=(userId='${user.id}')&perPage=1", token
                    )
                    val acId = JSONObject(acRes).optJSONArray("items")
                        ?.optJSONObject(0)?.optString("id")
                    if (!acId.isNullOrEmpty())
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                            token,
                            JSONObject().apply { put("isActive", newStatus) }.toString()
                        )
                }
                updateUserInState(user.copy(isActive = newStatus))
                _state.update {
                    it.copy(
                        isLoading  = false,
                        successMsg = "${user.name} ${if (newStatus) "activated" else "deactivated"}"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteUser(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val token = getAdminToken()
                pbDelete(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}", token
                )
                listOf("user_access_control", "user_search_index").forEach { col ->
                    runCatching {
                        val res = pbGet(
                            "${AppConfig.BASE_URL}/api/collections/$col/records" +
                                    "?filter=(userId='${user.id}')&perPage=1", token
                        )
                        val id = JSONObject(res).optJSONArray("items")
                            ?.optJSONObject(0)?.optString("id")
                        if (!id.isNullOrEmpty())
                            pbDelete(
                                "${AppConfig.BASE_URL}/api/collections/$col/records/$id", token
                            )
                    }
                }
                val updated = _state.value.users.filterNot { it.id == user.id }
                _state.update {
                    it.copy(
                        isLoading     = false,
                        users         = updated,
                        filteredUsers = updated,
                        successMsg    = "${user.name} deleted"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Called after ProfileCompletionActivity returns — refresh state
    fun refreshUser(updatedUserId: String) {
        viewModelScope.launch {
            try {
                val token = getAdminToken()
                val res   = pbGet(
                    "${AppConfig.BASE_URL}/api/collections/users/records/$updatedUserId",
                    token
                )
                val o       = JSONObject(res)
                val profile = runCatching {
                    JSONObject(o.optString("profile", "{}"))
                }.getOrDefault(JSONObject())
                val work    = runCatching {
                    JSONObject(o.optString("workStats", "{}"))
                }.getOrDefault(JSONObject())
                val issues  = runCatching {
                    JSONObject(o.optString("issues", "{}"))
                }.getOrDefault(JSONObject())

                val refreshed = MUUser(
                    id                = o.optString("id"),
                    name              = o.optString("name").ifBlank { o.optString("email") },
                    email             = o.optString("email"),
                    role              = o.optString("role"),
                    companyName       = o.optString("sanitizedCompanyName"),
                    deptName          = o.optString("sanitizedDepartment"),
                    designation       = o.optString("designation"),
                    imageUrl          = profile.optString("imageUrl", ""),
                    phoneNumber       = profile.optString("phoneNumber", ""),
                    experience        = work.optInt("experience"),
                    activeProjects    = work.optInt("activeProjects"),
                    completedProjects = work.optInt("completedProjects"),
                    totalComplaints   = issues.optInt("totalComplaints"),
                    isActive          = o.optBoolean("isActive", true),
                    documentPath      = o.optString("documentPath"),
                    originalCompany   = o.optString("companyName"),
                    originalDept      = o.optString("department")
                )
                updateUserInState(refreshed)
            } catch (e: Exception) {
                android.util.Log.w("ManageUserVM", "refreshUser failed: ${e.message}")
            }
        }
    }

    private fun updateUserInState(updated: MUUser) {
        val users    = _state.value.users.map { if (it.id == updated.id) updated else it }
        val filtered = _state.value.filteredUsers.map { if (it.id == updated.id) updated else it }
        _state.update { it.copy(users = users, filteredUsers = filtered) }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Admin token ───────────────────────────────────────────────────────────

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
                val ok      = res.isSuccessful; res.close()
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) return@withContext t
                }
            } catch (e: Exception) {
                android.util.Log.w("ManageUserVM", "Admin auth $url: ${e.message}")
            }
        }
        error("Could not obtain admin token")
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private suspend fun pbGet(url: String, token: String): String =
        withContext(Dispatchers.IO) {
            val res = http.newCall(
                Request.Builder().url(url).get()
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute()
            val b = res.body?.string() ?: "{}"; res.close(); b
        }

    private suspend fun pbPatch(url: String, token: String, body: String) =
        withContext(Dispatchers.IO) {
            val res = http.newCall(
                Request.Builder().url(url)
                    .patch(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute()
            val resBody = res.body?.string() ?: ""
            val code    = res.code; res.close()
            if (!res.isSuccessful) error("PATCH $code: $resBody")
        }

    private suspend fun pbDelete(url: String, token: String) =
        withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder().url(url).delete()
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute().close()
        }
}