package com.example.ritik_2.administrator.rolemanagement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.ConnectivityMonitor
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
    val isLoading          : Boolean           = false,
    val users              : List<UserProfile> = emptyList(),
    val filteredUsers      : List<UserProfile> = emptyList(),
    val roles              : List<RoleInfo>    = emptyList(),
    val searchQuery        : String            = "",
    val successMsg         : String?           = null,
    val error              : String?           = null,
    val isOffline          : Boolean           = false,
    // Permissions editing
    val editingPermRole    : RoleInfo?         = null,   // role whose perms are being edited
    val editingPermissions : Set<String>       = emptySet()
)

data class RoleInfo(
    val id          : String,
    val name        : String,
    val userCount   : Int,
    val isCustom    : Boolean,
    val permissions : List<String> = emptyList(),
    val isBuiltIn   : Boolean      = Permissions.ALL_ROLES.contains(name)
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

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session = authRepository.getSession() ?: error("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany

                val offline = !monitor.serverReachable.value
                loadFromLocal()

                if (!offline) {
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

        val dbRoles = roleEntities.map { r ->
            RoleInfo(
                id          = r.id,
                name        = r.name,
                userCount   = userEntities.count { it.role == r.name },
                isCustom    = r.isCustom,
                permissions = r.permissions.ifEmpty { listOf("view_profile") }
            )
        }

        // Ensure all built-in roles always appear even if not yet seeded in local DB
        val dbRoleNames = dbRoles.map { it.name }.toSet()
        @Suppress("DEPRECATION")
        val missingBuiltIn = Permissions.ALL_ROLES
            .filter { it !in dbRoleNames }
            .map { roleName ->
                RoleInfo(
                    id          = "${sanitizedCompany}_$roleName",
                    name        = roleName,
                    userCount   = userEntities.count { it.role == roleName },
                    isCustom    = false,
                    permissions = Permissions.forRole(roleName)
                )
            }

        // Sort: built-in roles in hierarchy order first, then custom roles
        val finalRoles = (dbRoles + missingBuiltIn).sortedBy { role ->
            val idx = Permissions.ALL_ROLES.indexOf(role.name)
            if (idx >= 0) idx else Int.MAX_VALUE
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
                roles         = finalRoles,
                users         = users,
                filteredUsers = applySearch(users, s.searchQuery)
            )
        }
    }

    // ── Create role ───────────────────────────────────────────────────────────

    fun createRole(roleName: String, initialPermissions: List<String> = emptyList()) {
        if (roleName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val roleId   = "${sanitizedCompany}_$roleName"
                val perms    = initialPermissions.ifEmpty { listOf("view_profile") }
                val company  = db.companyDao().getByName(sanitizedCompany)?.originalName
                    ?: sanitizedCompany

                db.roleDao().upsert(
                    RoleEntity(
                        id                   = roleId,
                        name                 = roleName,
                        sanitizedCompanyName = sanitizedCompany,
                        companyName          = company,
                        isCustom             = true,
                        permissions          = perms,
                        pendingCreate        = true
                    )
                )

                if (monitor.serverReachable.value) {
                    applyRoleToServer(roleName, action = "add")
                } else {
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
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false,
                    successMsg = "Role '$roleName' created") }
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
                        }.toString()
                    )
                }
                loadFromLocal()
                _state.update { it.copy(isLoading = false,
                    successMsg = "Role '${role.name}' deleted") }
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
                val roleEntity = db.roleDao().getById("${sanitizedCompany}_$newRole")
                val newPerms   = when {
                    newRole == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                    roleEntity != null && roleEntity.permissions.isNotEmpty() -> roleEntity.permissions
                    else -> listOf("view_profile")
                }
                val permsJson = Json.encodeToString(newPerms)
                val newPath   = "users/$sanitizedCompany/${user.sanitizedDept}/$newRole/${user.id}"

                db.userDao().setRole(user.id, newRole)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply {
                            put("role",         newRole)
                            put("permissions",  permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                    val acRes = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                "?filter=(userId='${user.id}')&perPage=1", token
                    )
                    val acId = JSONObject(acRes).optJSONArray("items")
                        ?.optJSONObject(0)?.optString("id")
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
                    syncManager.enqueue("ROLE_CHANGE", "users", user.id,
                        JSONObject().apply {
                            put("role",         newRole)
                            put("permissions",  permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false,
                    successMsg = "${user.name} is now $newRole") }
                onDone(user.name, user.role, newRole)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false,
                    error = "Role change failed: ${e.message}") }
            }
        }
    }

    // ── Permissions editing ───────────────────────────────────────────────────

    /** Open the permissions editor for a role */
    fun startEditingPermissions(role: RoleInfo) {
        _state.update { it.copy(
            editingPermRole    = role,
            editingPermissions = role.permissions.toSet()
        ) }
    }

    /** Toggle a single permission on/off */
    fun togglePermission(permission: String) {
        val current = _state.value.editingPermissions.toMutableSet()
        if (!current.remove(permission)) current.add(permission)
        _state.update { it.copy(editingPermissions = current) }
    }

    /** Save permissions for the role being edited */
    fun savePermissions() {
        val role  = _state.value.editingPermRole ?: return
        val perms = _state.value.editingPermissions.toList()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val permsJson = Json.encodeToString(perms)

                // Update local DB
                val existing = db.roleDao().getById(role.id)
                if (existing != null) {
                    db.roleDao().upsert(existing.copy(permissions = perms))
                }

                // Persist role template to role_definitions collection
                if (monitor.serverReachable.value) {
                    try {
                        val rdToken = syncManager.getAdminToken()
                        val rdRes   = syncManager.pbGet(
                            "${AppConfig.BASE_URL}/api/collections/role_definitions/records" +
                                    "?filter=(sanitizedCompanyName='$sanitizedCompany'&&roleName='${role.name}')&perPage=1",
                            rdToken
                        )
                        val rdId = JSONObject(rdRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                        if (!rdId.isNullOrEmpty()) {
                            syncManager.pbPatch(
                                "${AppConfig.BASE_URL}/api/collections/role_definitions/records/$rdId",
                                rdToken,
                                JSONObject().apply { put("permissions", permsJson) }.toString()
                            )
                        } else {
                            syncManager.pbPost(
                                "${AppConfig.BASE_URL}/api/collections/role_definitions/records",
                                rdToken,
                                JSONObject().apply {
                                    put("sanitizedCompanyName", sanitizedCompany)
                                    put("roleName",   role.name)
                                    put("permissions", permsJson)
                                    put("isBuiltIn",  role.name in Permissions.ALL_ROLES)
                                }.toString()
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("RoleManagementVM", "role_definitions update failed (non-fatal): ${e.message}")
                    }
                }

                // Update all users in this role so their tokens reflect new permissions
                if (monitor.serverReachable.value) {
                    val token     = syncManager.getAdminToken()
                    val usersInRole = db.userDao().getByRole(sanitizedCompany, role.name)
                    usersInRole.forEach { user ->
                        // Update users collection
                        syncManager.pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                            token,
                            JSONObject().apply { put("permissions", permsJson) }.toString()
                        )
                        // Update access_control
                        val acRes = syncManager.pbGet(
                            "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                                    "?filter=(userId='${user.id}')&perPage=1", token
                        )
                        val acId  = JSONObject(acRes).optJSONArray("items")
                            ?.optJSONObject(0)?.optString("id")
                        if (!acId.isNullOrEmpty()) {
                            syncManager.pbPatch(
                                "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                                token,
                                JSONObject().apply { put("permissions", permsJson) }.toString()
                            )
                        }
                        // Update local user cache
                        db.userDao().upsert(user.copy(permissions = perms))
                    }
                } else {
                    // Queue for sync later — update all users in this role
                    val usersInRole = db.userDao().getByRole(sanitizedCompany, role.name)
                    usersInRole.forEach { user ->
                        syncManager.enqueue("UPDATE", "users", user.id,
                            JSONObject().apply { put("permissions", permsJson) }.toString())
                        db.userDao().upsert(user.copy(permissions = perms))
                    }
                }

                loadFromLocal()
                _state.update { it.copy(
                    isLoading          = false,
                    editingPermRole    = null,
                    editingPermissions = emptySet(),
                    successMsg         = "Permissions updated for '${role.name}'"
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false,
                    error = "Failed to save permissions: ${e.message}") }
            }
        }
    }

    fun cancelEditingPermissions() {
        _state.update { it.copy(editingPermRole = null, editingPermissions = emptySet()) }
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
        val token   = syncManager.getAdminToken()
        val compRes = syncManager.pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sanitizedCompany')&perPage=1", token
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