package com.example.ritik_2.administrator.manageuser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.administrator.manageuser.models.*
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ManageUserViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val db            : AppDatabase,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ManageUserUiState())
    val state: StateFlow<ManageUserUiState> = _state.asStateFlow()

    private var sanitizedCompany = ""

    init { loadCurrentUser() }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session = authRepository.getSession() ?: error("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany
                _state.update { it.copy(currentRole = profile.role) }

                // Refresh from server if online
                if (monitor.serverReachable.value)
                    syncManager.refreshCompanyData(sanitizedCompany)

                loadFromLocal()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadFromLocal() {
        val users = if (_state.value.currentRole == "Administrator")
            db.userDao().getAll()
        else
            db.userDao().getByCompany(sanitizedCompany)

        val allUsers = users.filter { !it.pendingDelete }.map { u ->
            MUUser(
                id                = u.id,
                name              = u.name.ifBlank { u.email },
                email             = u.email,
                role              = u.role,
                companyName       = u.sanitizedCompanyName,
                deptName          = u.sanitizedDepartment,
                designation       = u.designation,
                imageUrl          = u.imageUrl,
                phoneNumber       = u.phoneNumber,
                experience        = u.experience,
                activeProjects    = u.activeProjects,
                completedProjects = u.completedProjects,
                totalComplaints   = u.totalComplaints,
                isActive          = u.isActive,
                documentPath      = u.documentPath,
                originalCompany   = u.companyName,
                originalDept      = u.department
            )
        }

        val companies = allUsers.groupBy { it.companyName }.map { (sc, uList) ->
            MUCompany(
                sanitizedName = sc,
                originalName  = uList.firstOrNull()?.originalCompany ?: sc,
                totalUsers    = uList.size,
                activeUsers   = uList.count { it.isActive }
            )
        }

        _state.update { s ->
            s.copy(
                isLoading     = false,
                companies     = companies,
                users         = allUsers,
                filteredUsers = applySearch(allUsers, s.searchQuery)
            )
        }
    }

    fun search(query: String) {
        _state.update { s ->
            s.copy(searchQuery = query, filteredUsers = applySearch(s.users, query))
        }
    }

    fun toggleCompany(sc: String) {
        val cur = _state.value.expandedCompanies.toMutableSet()
        if (!cur.add(sc)) cur.remove(sc); _state.update { it.copy(expandedCompanies = cur) }
    }

    fun toggleDepartment(sc: String, sd: String) {
        val key = "$sc|$sd"; val cur = _state.value.expandedDepartments.toMutableSet()
        if (!cur.add(key)) cur.remove(key); _state.update { it.copy(expandedDepartments = cur) }
    }

    fun toggleRole(sc: String, sd: String, role: String) {
        val key = "$sc|$sd|$role"; val cur = _state.value.expandedRoles.toMutableSet()
        if (!cur.add(key)) cur.remove(key); _state.update { it.copy(expandedRoles = cur) }
    }

    fun getDepts(sc: String): List<MUDepartment> {
        val source = if (_state.value.searchQuery.isBlank()) _state.value.users
        else _state.value.filteredUsers
        return source.filter { it.companyName == sc }.groupBy { it.deptName }.map { (sd, uList) ->
            MUDepartment(
                sanitizedName  = sd,
                departmentName = uList.firstOrNull()?.originalDept ?: sd,
                companyName    = sc, userCount = uList.size,
                activeUsers    = uList.count { it.isActive },
                roles          = uList.map { it.role }.distinct()
            )
        }
    }

    fun getRoles(sc: String, sd: String): List<MURoleInfo> {
        val source = if (_state.value.searchQuery.isBlank()) _state.value.users
        else _state.value.filteredUsers
        return source.filter { it.companyName == sc && it.deptName == sd }
            .groupBy { it.role }.map { (role, uList) ->
                MURoleInfo(role, sc, sd, uList.size, uList.count { it.isActive })
            }
    }

    fun getUsers(sc: String, sd: String, role: String) =
        (if (_state.value.searchQuery.isBlank()) _state.value.users else _state.value.filteredUsers)
            .filter { it.companyName == sc && it.deptName == sd && it.role == role }

    fun getFilteredCompanies() = _state.value.let { s ->
        val src = if (s.searchQuery.isBlank()) s.users else s.filteredUsers
        src.groupBy { it.companyName }.map { (sc, uList) ->
            MUCompany(sc, uList.firstOrNull()?.originalCompany ?: sc, uList.size, uList.count { it.isActive })
        }
    }

    // ── Toggle active status ──────────────────────────────────────────────────

    fun toggleUserStatus(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newStatus = !user.isActive
                db.userDao().setActive(user.id, newStatus)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply { put("isActive", newStatus) }.toString()
                    )
                    val acRes = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                "?filter=(userId='${user.id}')&perPage=1", token
                    )
                    val acId = JSONObject(acRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                    if (!acId.isNullOrEmpty()) syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                        token, JSONObject().apply { put("isActive", newStatus) }.toString()
                    )
                } else {
                    syncManager.enqueue("UPDATE", "users", user.id,
                        JSONObject().apply { put("isActive", newStatus) }.toString())
                }

                loadFromLocal()
                _state.update {
                    it.copy(isLoading = false,
                        successMsg = "${user.name} ${if (newStatus) "activated" else "deactivated"}")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Delete user ───────────────────────────────────────────────────────────

    fun deleteUser(user: MUUser) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                db.userDao().markDeleted(user.id)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}", token)
                    listOf("user_access_control", "user_search_index").forEach { col ->
                        runCatching {
                            val res = syncManager.pbGet(
                                "${AppConfig.BASE_URL}/api/collections/$col/records" +
                                        "?filter=(userId='${user.id}')&perPage=1", token)
                            val id = JSONObject(res).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                            if (!id.isNullOrEmpty())
                                syncManager.pbDelete(
                                    "${AppConfig.BASE_URL}/api/collections/$col/records/$id", token)
                        }
                    }
                    db.userDao().hardDelete(user.id)
                } else {
                    syncManager.enqueue("DELETE", "users", user.id, "{}")
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "${user.name} deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Called after ProfileCompletionActivity returns — just re-sync from local
    fun refreshUser(updatedUserId: String) {
        viewModelScope.launch {
            if (monitor.serverReachable.value) {
                try {
                    val token   = syncManager.getAdminToken()
                    val res     = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/users/records/$updatedUserId", token)
                    val o       = JSONObject(res)
                    val profile = try { JSONObject(o.optString("profile", "{}")) } catch (_: Exception) { JSONObject() }
                    val work    = try { JSONObject(o.optString("workStats", "{}")) } catch (_: Exception) { JSONObject() }
                    val issues  = try { JSONObject(o.optString("issues", "{}")) } catch (_: Exception) { JSONObject() }
                    db.userDao().upsert(
                        com.example.ritik_2.localdatabase.UserEntity(
                            id                   = o.optString("id"),
                            name                 = o.optString("name"),
                            email                = o.optString("email"),
                            role                 = o.optString("role"),
                            companyName          = o.optString("companyName"),
                            sanitizedCompanyName = o.optString("sanitizedCompanyName"),
                            department           = o.optString("department"),
                            sanitizedDepartment  = o.optString("sanitizedDepartment"),
                            designation          = o.optString("designation"),
                            isActive             = o.optBoolean("isActive", true),
                            documentPath         = o.optString("documentPath"),
                            imageUrl             = profile.optString("imageUrl", ""),
                            phoneNumber          = profile.optString("phoneNumber", ""),
                            experience           = work.optInt("experience"),
                            activeProjects       = work.optInt("activeProjects"),
                            completedProjects    = work.optInt("completedProjects"),
                            totalComplaints      = issues.optInt("totalComplaints"),
                            needsProfileCompletion = o.optBoolean("needsProfileCompletion", true)
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ManageUserVM", "refreshUser: ${e.message}")
                }
            }
            loadFromLocal()
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    private fun applySearch(users: List<MUUser>, q: String) =
        if (q.isBlank()) users
        else users.filter {
            it.name.contains(q, true)        ||
                    it.email.contains(q, true)       ||
                    it.role.contains(q, true)        ||
                    it.designation.contains(q, true) ||
                    it.originalCompany.contains(q, true) ||
                    it.originalDept.contains(q, true)
        }
}