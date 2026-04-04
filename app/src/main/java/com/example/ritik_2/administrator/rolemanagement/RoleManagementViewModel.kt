package com.example.ritik_2.administrator.rolemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.core.StringUtils
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.localdatabase.RoleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class RoleManagementUiState(
    val isLoading     : Boolean           = false,
    val users         : List<UserProfile> = emptyList(),
    val filteredUsers : List<UserProfile> = emptyList(),
    val roles         : List<RoleInfo>    = emptyList(),
    val searchQuery   : String            = "",
    val successMsg    : String?           = null,
    val error         : String?           = null,
    val isOffline     : Boolean           = false
)

data class RoleInfo(
    val id        : String,
    val name      : String,
    val userCount : Int,
    val isCustom  : Boolean,
    val isBuiltIn : Boolean = Permissions.ALL_ROLES.contains(name)
)

@HiltViewModel
class RoleManagementViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val db            : AppDatabase,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(RoleManagementUiState())
    val state: StateFlow<RoleManagementUiState> = _state.asStateFlow()

    private var sanitizedCompany = ""

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session = authRepository.getSession() ?: error("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany

                val offline = !monitor.serverReachable.value

                // Always load from local first for instant display
                loadFromLocal()

                if (!offline) {
                    // Refresh cache then reload
                    syncManager.refreshCompanyData(sanitizedCompany)
                    loadFromLocal()
                }

                _state.update { it.copy(isLoading = false, isOffline = offline) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadFromLocal() {
        val roleEntities = db.roleDao().getByCompany(sanitizedCompany)
        val userEntities = db.userDao().getByCompany(sanitizedCompany)

        val roles = roleEntities.map { r ->
            RoleInfo(
                id        = r.id,
                name      = r.name,
                userCount = userEntities.count { it.role == r.name },
                isCustom  = r.isCustom
            )
        }

        val users = userEntities.map { u ->
            UserProfile(
                id               = u.id,
                name             = u.name,
                email            = u.email,
                role             = u.role,
                companyName      = u.companyName,
                sanitizedCompany = u.sanitizedCompanyName,
                department       = u.department,
                sanitizedDept    = u.sanitizedDepartment,
                designation      = u.designation,
                imageUrl         = u.imageUrl,
                isActive         = u.isActive
            )
        }

        _state.update { s ->
            s.copy(
                roles         = roles,
                users         = users,
                filteredUsers = applySearch(users, s.searchQuery)
            )
        }
    }

    // ── Create role ───────────────────────────────────────────────────────────

    fun createRole(roleName: String) {
        if (roleName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val roleId = "${sanitizedCompany}_$roleName"

                // Save locally immediately
                db.roleDao().upsert(
                    RoleEntity(
                        id = roleId,
                        name = roleName,
                        sanitizedCompanyName = sanitizedCompany,
                        companyName = _state.value.roles.firstOrNull()?.name?.let {
                            db.companyDao().getByName(sanitizedCompany)?.originalName
                        } ?: sanitizedCompany,
                        isCustom = true,
                        pendingCreate = true
                    )
                )

                // Enqueue server update
                syncManager.enqueue(
                    type       = "UPDATE",
                    collection = "companies_metadata",
                    recordId   = sanitizedCompany,
                    payload    = JSONObject().apply {
                        put("action",   "add_role")
                        put("roleName", roleName)
                        put("sc",       sanitizedCompany)
                    }.toString()
                )

                // If online, do it now
                if (monitor.serverReachable.value) {
                    applyRoleToServer(roleName, action = "add")
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "Role '$roleName' created") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Delete role ───────────────────────────────────────────────────────────

    fun deleteRole(role: RoleInfo) {
        if (role.isBuiltIn && !role.isCustom) {
            _state.update { it.copy(error = "Cannot delete built-in role '${role.name}'") }
            return
        }
        if (role.userCount > 0) {
            _state.update { it.copy(error = "Cannot delete '${role.name}' — ${role.userCount} users assigned. Move them first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                db.roleDao().delete(role.id)
                if (monitor.serverReachable.value) {
                    applyRoleToServer(role.name, action = "remove")
                } else {
                    syncManager.enqueue("UPDATE", "companies_metadata", sanitizedCompany,
                        JSONObject().apply {
                            put("action",   "remove_role")
                            put("roleName", role.name)
                            put("sc",       sanitizedCompany)
                        }.toString()
                    )
                }
                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "Role '${role.name}' deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Change user role ──────────────────────────────────────────────────────

    fun changeUserRole(user: UserProfile, newRole: String, onDone: (String, String, String) -> Unit) {
        if (user.role == newRole) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newPerms  = Permissions.forRole(newRole)
                val permsJson = Json.encodeToString(newPerms)
                val newPath   = "users/$sanitizedCompany/${user.sanitizedDept}/$newRole/${user.id}"

                // Update local cache immediately
                db.userDao().setRole(user.id, newRole)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    // PATCH users record
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply {
                            put("role",         newRole)
                            put("permissions",  permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                    // PATCH access_control
                    val acRes = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                "?filter=(userId='${user.id}')&perPage=1", token
                    )
                    val acId = JSONObject(acRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                    if (!acId.isNullOrEmpty()) {
                        syncManager.pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                            token,
                            JSONObject().apply {
                                put("role",         newRole)
                                put("permissions",  permsJson)
                                put("documentPath", newPath)
                            }.toString()
                        )
                    }
                } else {
                    syncManager.enqueue(
                        type       = "ROLE_CHANGE",
                        collection = "users",
                        recordId   = user.id,
                        payload    = JSONObject().apply {
                            put("role",        newRole)
                            put("permissions", permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "${user.name} is now $newRole") }
                onDone(user.name, user.role, newRole)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Role change failed: ${e.message}") }
            }
        }
    }

    fun search(query: String) {
        _state.update { s ->
            s.copy(searchQuery = query, filteredUsers = applySearch(s.users, query))
        }
    }

    fun clearMessages() = _state.update { it.copy(successMsg = null, error = null) }
    fun refresh() = load()

    // ── Server helpers ────────────────────────────────────────────────────────

    private suspend fun applyRoleToServer(roleName: String, action: String) {
        val token = syncManager.getAdminToken()
        val compRes = syncManager.pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sanitizedCompany')&perPage=1",
            token
        )
        val item = JSONObject(compRes).optJSONArray("items")?.optJSONObject(0) ?: return
        val cId  = item.optString("id")
        val arr  = try { JSONArray(item.optString("availableRoles", "[]")) }
        catch (_: Exception) { JSONArray() }

        val current = (0 until arr.length()).map { arr.optString(it) }.toMutableList()
        when (action) {
            "add"    -> if (!current.contains(roleName)) current.add(roleName)
            "remove" -> current.remove(roleName)
        }
        syncManager.pbPatch(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
            token,
            JSONObject().apply {
                put("availableRoles", Json.encodeToString(current))
            }.toString()
        )
    }

    private fun applySearch(users: List<UserProfile>, query: String): List<UserProfile> {
        val q = query.trim()
        return if (q.isBlank()) users
        else users.filter {
            it.name.contains(q, true)        ||
                    it.email.contains(q, true)       ||
                    it.role.contains(q, true)        ||
                    it.designation.contains(q, true) ||
                    it.department.contains(q, true)
        }
    }
}