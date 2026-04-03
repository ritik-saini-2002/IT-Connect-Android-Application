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

    private fun token(): String = authRepository.getSession()?.token ?: ""

    init { loadCurrentUser() }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session = authRepository.getSession()
                    ?: throw Exception("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                _state.update { it.copy(currentRole = profile.role) }

                // Load users directly from users collection
                // Group them by company → department → role on the client side
                if (profile.role == "Administrator")
                    loadAllUsers()
                else
                    loadCompanyUsers(profile.sanitizedCompany)

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadAllUsers() {
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/users/records?perPage=500&sort=companyName",
            token()
        )
        processUsersResponse(res)
    }

    private suspend fun loadCompanyUsers(sc: String) {
        val res   = pbGet(
            "${AppConfig.BASE_URL}/api/collections/users/records" +
                    "?filter=(sanitizedCompanyName='$sc')&perPage=500",
            token()
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

                val imageUrl = profile.optString("imageUrl", "")

                MUUser(
                    id                = o.optString("id"),
                    name              = o.optString("name").ifBlank { o.optString("email") },
                    email             = o.optString("email"),
                    role              = o.optString("role"),
                    companyName       = o.optString("sanitizedCompanyName"),
                    deptName          = o.optString("sanitizedDepartment"),
                    designation       = o.optString("designation"),
                    imageUrl          = imageUrl,
                    phoneNumber       = profile.optString("phoneNumber"),
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

        // Build company tree from users data
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

        _state.update { it.copy(isLoading = false, companies = companies, users = allUsers) }
    }

    fun toggleCompany(sc: String) {
        val cur         = _state.value.expandedCompanies.toMutableSet()
        val wasExpanded = !cur.add(sc)
        if (wasExpanded) cur.remove(sc)
        _state.update { it.copy(expandedCompanies = cur) }
    }

    fun toggleDepartment(sc: String, sd: String) {
        val key         = "$sc|$sd"
        val cur         = _state.value.expandedDepartments.toMutableSet()
        val wasExpanded = !cur.add(key)
        if (wasExpanded) cur.remove(key)
        _state.update { it.copy(expandedDepartments = cur) }
    }

    fun toggleRole(sc: String, sd: String, role: String) {
        val key         = "$sc|$sd|$role"
        val cur         = _state.value.expandedRoles.toMutableSet()
        val wasExpanded = !cur.add(key)
        if (wasExpanded) cur.remove(key)
        _state.update { it.copy(expandedRoles = cur) }
    }

    // Build departments from loaded users
    fun getDepts(sc: String): List<MUDepartment> =
        _state.value.users
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

    fun getRoles(sc: String, sd: String): List<MURoleInfo> =
        _state.value.users
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

    fun getUsers(sc: String, sd: String, role: String): List<MUUser> =
        _state.value.users.filter {
            it.companyName == sc && it.deptName == sd && it.role == role
        }

    fun toggleUserStatus(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newStatus = !user.isActive
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token(),
                    JSONObject().apply { put("isActive", newStatus) }.toString()
                )
                // Update access control too
                runCatching {
                    val acRes = pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                "?filter=(userId='${user.id}')",
                        token()
                    )
                    val acId = JSONObject(acRes).optJSONArray("items")
                        ?.optJSONObject(0)?.optString("id")
                    if (!acId.isNullOrEmpty())
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                            token(),
                            JSONObject().apply { put("isActive", newStatus) }.toString()
                        )
                }
                val updated = _state.value.users.map { u ->
                    if (u.id == user.id) u.copy(isActive = newStatus) else u
                }
                _state.update {
                    it.copy(
                        isLoading  = false,
                        users      = updated,
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
                pbDelete(
                    "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token()
                )
                listOf("user_access_control", "user_search_index").forEach { col ->
                    runCatching {
                        val res = pbGet(
                            "${AppConfig.BASE_URL}/api/collections/$col/records" +
                                    "?filter=(userId='${user.id}')",
                            token()
                        )
                        val id = JSONObject(res).optJSONArray("items")
                            ?.optJSONObject(0)?.optString("id")
                        if (!id.isNullOrEmpty())
                            pbDelete(
                                "${AppConfig.BASE_URL}/api/collections/$col/records/$id",
                                token()
                            )
                    }
                }
                _state.update {
                    it.copy(
                        isLoading  = false,
                        users      = _state.value.users.filterNot { it.id == user.id },
                        successMsg = "${user.name} deleted"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

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
            http.newCall(
                Request.Builder().url(url)
                    .patch(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute().close()
        }

    private suspend fun pbDelete(url: String, token: String) =
        withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder().url(url).delete()
                    .addHeader("Authorization", "Bearer $token").build()
            ).execute().close()
        }
}