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
import org.json.JSONArray
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
                val session = authRepository.getSession() ?: throw Exception("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                _state.update { it.copy(currentRole = profile.role) }
                if (profile.role == "Administrator") loadAllCompanies()
                else loadSingleCompany(profile.sanitizedCompany)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadAllCompanies() {
        val res   = pbGet("${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=100", token())
        val items = JSONObject(res).optJSONArray("items") ?: return
        val list  = (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            MUCompany(o.optString("sanitizedName"), o.optString("originalName"),
                o.optInt("totalUsers"), o.optInt("activeUsers"))
        }
        _state.update { it.copy(isLoading = false, companies = list) }
    }

    private suspend fun loadSingleCompany(sc: String) {
        val res = pbGet("${AppConfig.BASE_URL}/api/collections/companies_metadata/records?filter=(sanitizedName='$sc')", token())
        val obj = JSONObject(res).optJSONArray("items")?.optJSONObject(0) ?: return
        _state.update { it.copy(isLoading = false, companies = listOf(
            MUCompany(obj.optString("sanitizedName"), obj.optString("originalName"),
                obj.optInt("totalUsers"), obj.optInt("activeUsers"))
        ))}
    }

    fun toggleCompany(sc: String) {
        val cur = _state.value.expandedCompanies.toMutableSet()
        val wasExpanded = !cur.add(sc)
        if (wasExpanded) cur.remove(sc)
        _state.update { it.copy(expandedCompanies = cur) }
        if (!wasExpanded) viewModelScope.launch { loadDepartments(sc) }
    }

    fun toggleDepartment(sc: String, sd: String) {
        val key = "$sc|$sd"
        val cur = _state.value.expandedDepartments.toMutableSet()
        val wasExpanded = !cur.add(key)
        if (wasExpanded) cur.remove(key)
        _state.update { it.copy(expandedDepartments = cur) }
        if (!wasExpanded) viewModelScope.launch { loadRoles(sc, sd) }
    }

    fun toggleRole(sc: String, sd: String, role: String) {
        val key = "$sc|$sd|$role"
        val cur = _state.value.expandedRoles.toMutableSet()
        val wasExpanded = !cur.add(key)
        if (wasExpanded) cur.remove(key)
        _state.update { it.copy(expandedRoles = cur) }
        if (!wasExpanded) viewModelScope.launch { loadUsersForRole(sc, sd, role) }
    }

    private suspend fun loadDepartments(sc: String) {
        val res   = pbGet("${AppConfig.BASE_URL}/api/collections/departments_metadata/records?filter=(sanitizedCompanyName='$sc')", token())
        val items = JSONObject(res).optJSONArray("items") ?: return
        val list  = (0 until items.length()).map { i ->
            val o     = items.getJSONObject(i)
            val roles = (o.optJSONArray("availableRoles") ?: JSONArray())
                .let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            MUDepartment(o.optString("sanitizedName"), o.optString("departmentName"),
                sc, o.optInt("userCount"), o.optInt("activeUsers"), roles)
        }
        _state.update { it.copy(departments = _state.value.departments.filterNot { it.companyName == sc } + list) }
    }

    private suspend fun loadRoles(sc: String, sd: String) {
        val res   = pbGet("${AppConfig.BASE_URL}/api/collections/roles_metadata/records?filter=(sanitizedCompanyName='$sc'&&sanitizedDepartment='$sd')", token())
        val items = JSONObject(res).optJSONArray("items") ?: return
        val list  = (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            MURoleInfo(o.optString("roleName"), sc, sd, o.optInt("userCount"), o.optInt("activeUsers"))
        }
        _state.update { it.copy(roles = _state.value.roles.filterNot { it.companyName == sc && it.deptName == sd } + list) }
    }

    private suspend fun loadUsersForRole(sc: String, sd: String, role: String) =
        withContext(Dispatchers.IO) {
            val res   = pbGet("${AppConfig.BASE_URL}/api/collections/users/records?filter=(sanitizedCompanyName='$sc'&&sanitizedDepartment='$sd'&&role='$role')&perPage=200", token())
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext
            val list  = (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val o       = items.getJSONObject(i)
                    val profile = runCatching { JSONObject(o.optString("profile", "{}")) }.getOrDefault(JSONObject())
                    val work    = runCatching { JSONObject(o.optString("workStats", "{}")) }.getOrDefault(JSONObject())
                    val issues  = runCatching { JSONObject(o.optString("issues", "{}")) }.getOrDefault(JSONObject())
                    MUUser(
                        id                = o.optString("id"),
                        name              = o.optString("name"),
                        email             = o.optString("email"),
                        role              = role, companyName = sc, deptName = sd,
                        designation       = o.optString("designation"),
                        imageUrl          = AppConfig.avatarUrl(o.optString("id"), profile.optString("avatar")) ?: "",
                        phoneNumber       = profile.optString("phoneNumber"),
                        experience        = work.optInt("experience"),
                        activeProjects    = work.optInt("activeProjects"),
                        completedProjects = work.optInt("completedProjects"),
                        totalComplaints   = issues.optInt("totalComplaints"),
                        isActive          = o.optBoolean("isActive", true),
                        documentPath      = o.optString("documentPath")
                    )
                }.getOrNull()
            }
            _state.update { it.copy(users = _state.value.users.filterNot {
                it.companyName == sc && it.deptName == sd && it.role == role } + list) }
        }

    fun toggleUserStatus(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newStatus = !user.isActive
                pbPatch("${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                    token(), JSONObject().apply { put("isActive", newStatus) }.toString())

                // ✅ Correct collection name
                val acRes = pbGet("${AppConfig.BASE_URL}/api/collections/user_access_control/records?filter=(userId='${user.id}')", token())
                val acId  = JSONObject(acRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                if (!acId.isNullOrEmpty())
                    pbPatch("${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        token(), JSONObject().apply { put("isActive", newStatus) }.toString())

                val updated = _state.value.users.map { u ->
                    if (u.id == user.id) u.copy(isActive = newStatus) else u
                }
                _state.update { it.copy(isLoading = false, users = updated,
                    successMsg = "${user.name} ${if (newStatus) "activated" else "deactivated"}") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteUser(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                pbDelete("${AppConfig.BASE_URL}/api/collections/users/records/${user.id}", token())

                // ✅ Correct collection names
                listOf("user_access_control", "user_search_index").forEach { col ->
                    runCatching {
                        val res = pbGet("${AppConfig.BASE_URL}/api/collections/$col/records?filter=(userId='${user.id}')", token())
                        val id  = JSONObject(res).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                        if (!id.isNullOrEmpty())
                            pbDelete("${AppConfig.BASE_URL}/api/collections/$col/records/$id", token())
                    }
                }
                _state.update { it.copy(isLoading = false,
                    users      = _state.value.users.filterNot { it.id == user.id },
                    successMsg = "${user.name} deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }
    fun getDepts(sc: String) = _state.value.departments.filter { it.companyName == sc }
    fun getRoles(sc: String, sd: String) = _state.value.roles.filter { it.companyName == sc && it.deptName == sd }
    fun getUsers(sc: String, sd: String, role: String) = _state.value.users.filter {
        it.companyName == sc && it.deptName == sd && it.role == role }

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

    private suspend fun pbDelete(url: String, token: String) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).delete()
            .addHeader("Authorization", "Bearer $token").build()).execute().close()
    }
}