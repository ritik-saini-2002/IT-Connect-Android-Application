package com.saini.ritik.administrator.manageuser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.administrator.manageuser.models.*
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.StringUtils
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private var isDbAdmin        = false

    // Cached permission list of the CURRENT editor (refreshed every loadCurrentUser).
    // Used by canModify() so that an admin who lost user-management permissions
    // mid-session is blocked at the action site, not just at the screen entry.
    private var editorPerms: List<String> = emptyList()

    init { loadCurrentUser() }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session   = authRepository.getSession() ?: error("Not logged in")
                isDbAdmin     = authRepository.isDbAdmin()
                val profile   = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany
                // Prefer the freshly-fetched profile's permissions over the cached
                // session list so a demoted admin gets the new (possibly empty) set.
                editorPerms      = profile.permissions.ifEmpty { session.permissions }

                _state.update { it.copy(
                    currentRole = profile.role,
                    isDbAdmin   = isDbAdmin
                ) }

                if (monitor.serverReachable.value)
                    syncManager.refreshCompanyData(sanitizedCompany)

                loadFromLocal()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadFromLocal() {
        // DB admin sees ALL users across ALL companies; others see only their company
        val users = if (isDbAdmin) db.userDao().getAll()
        else            db.userDao().getByCompany(sanitizedCompany)

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

    // ── Search / navigation helpers ───────────────────────────────────────────

    fun search(query: String) {
        _state.update { s ->
            s.copy(searchQuery = query, filteredUsers = applySearch(s.users, query))
        }
    }

    fun toggleCompany(sc: String) {
        val cur = _state.value.expandedCompanies.toMutableSet()
        if (!cur.add(sc)) cur.remove(sc)
        _state.update { it.copy(expandedCompanies = cur) }
    }

    fun getDepts(sc: String): List<MUDepartment> {
        val src = if (_state.value.searchQuery.isBlank()) _state.value.users
        else _state.value.filteredUsers
        return src.filter { it.companyName == sc }.groupBy { it.deptName }.map { (sd, uList) ->
            MUDepartment(
                sanitizedName  = sd,
                departmentName = uList.firstOrNull()?.originalDept ?: sd,
                companyName    = sc,
                userCount      = uList.size,
                activeUsers    = uList.count { it.isActive },
                roles          = uList.map { it.role }.distinct()
            )
        }
    }

    fun getRoles(sc: String, sd: String): List<MURoleInfo> {
        val src = if (_state.value.searchQuery.isBlank()) _state.value.users
        else _state.value.filteredUsers
        return src.filter { it.companyName == sc && it.deptName == sd }
            .groupBy { it.role }
            .map { (role, uList) ->
                MURoleInfo(role, sc, sd, uList.size, uList.count { it.isActive })
            }
    }

    fun getUsers(sc: String, sd: String, role: String) =
        (if (_state.value.searchQuery.isBlank()) _state.value.users
        else _state.value.filteredUsers)
            .filter { it.companyName == sc && it.deptName == sd && it.role == role }

    fun getFilteredCompanies() = _state.value.let { s ->
        val src = if (s.searchQuery.isBlank()) s.users else s.filteredUsers
        src.groupBy { it.companyName }.map { (sc, uList) ->
            MUCompany(sc, uList.firstOrNull()?.originalCompany ?: sc,
                uList.size, uList.count { it.isActive })
        }
    }

    // ── Toggle active / deactivate ────────────────────────────────────────────

    fun toggleUserStatus(user: MUUser) {
        if (!canModify(user)) {
            _state.update { it.copy(error = "You don't have permission to modify this user") }
            return
        }
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
                    // Sync access_control
                    updateAccessControlField(user.id, token, "isActive", newStatus)
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
        if (!canModify(user)) {
            _state.update { it.copy(error = "You don't have permission to delete this user") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                db.userDao().markDeleted(user.id)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}", token)
                    // Also delete from access_control and search_index
                    listOf("user_access_control", "user_search_index").forEach { col ->
                        runCatching {
                            val res = syncManager.pbGet(
                                "${AppConfig.BASE_URL}/api/collections/$col/records" +
                                        "?filter=(userId='${user.id}')&perPage=1", token)
                            val id  = JSONObject(res).optJSONArray("items")
                                ?.optJSONObject(0)?.optString("id")
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

    // ── Create user (DB admin shortcut — no redirect to CreateUserActivity) ───

    fun createUserDirect(
        name       : String,
        email      : String,
        password   : String,
        role       : String,
        department : String,
        designation: String,
        companyName: String
    ) {
        if (!isDbAdmin) {
            _state.update { it.copy(error = "Only DB admin can create users from this panel") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val sc           = StringUtils.sanitize(companyName)
                val sd           = StringUtils.sanitize(department)
                val token        = syncManager.getAdminToken()
                val roleEntity   = db.roleDao().getById("${sc}_$role")
                val perms        = when {
                    role == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                    roleEntity != null && roleEntity.permissions.isNotEmpty() -> roleEntity.permissions
                    else -> listOf(Permissions.PERM_VIEW_PROFILE)
                }
                val permsJson    = Json.encodeToString(perms)
                val documentPath = "users/$sc/$sd/$role"

                // 1. Create auth record
                val createBody = JSONObject().apply {
                    put("email", email); put("password", password)
                    put("passwordConfirm", password); put("name", name)
                    put("emailVisibility", true)
                }.toString()
                val createRes  = syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections/users/records", token, createBody)
                val userId = JSONObject(createRes).optString("id").ifEmpty { error("No userId") }
                val fullPath = "$documentPath/$userId"

                val profileJson = JSONObject().apply {
                    put("imageUrl",""); put("phoneNumber",""); put("address","")
                    put("employeeId",""); put("reportingTo",""); put("salary", 0)
                    put("emergencyContactName",""); put("emergencyContactPhone","")
                    put("emergencyContactRelation","")
                }.toString()
                val workJson = JSONObject().apply {
                    put("experience",0); put("completedProjects",0)
                    put("activeProjects",0); put("pendingTasks",0)
                    put("completedTasks",0)
                }.toString()
                val issuesJson = JSONObject().apply {
                    put("totalComplaints",0); put("resolvedComplaints",0)
                    put("pendingComplaints",0)
                }.toString()

                // 2. Patch user record with metadata
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/$userId", token,
                    JSONObject().apply {
                        put("userId", userId); put("role", role)
                        put("companyName", companyName); put("sanitizedCompanyName", sc)
                        put("department", department); put("sanitizedDepartment", sd)
                        put("designation", designation); put("isActive", true)
                        put("documentPath", fullPath); put("permissions", permsJson)
                        put("profile", profileJson); put("workStats", workJson)
                        put("issues", issuesJson); put("needsProfileCompletion", true)
                    }.toString()
                )

                // 3. Create access_control record
                syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections/user_access_control/records", token,
                    JSONObject().apply {
                        put("userId", userId); put("name", name); put("email", email)
                        put("companyName", companyName); put("sanitizedCompanyName", sc)
                        put("department", department); put("sanitizedDepartment", sd)
                        put("role", role); put("designation", designation)
                        put("permissions", permsJson); put("isActive", true)
                        put("documentPath", fullPath); put("needsProfileCompletion", true)
                    }.toString()
                )

                // 4. Cache locally
                db.userDao().upsert(UserEntity(
                    id                     = userId,
                    name                   = name,
                    email                  = email,
                    role                   = role,
                    companyName            = companyName,
                    sanitizedCompanyName   = sc,
                    department             = department,
                    sanitizedDepartment    = sd,
                    designation            = designation,
                    isActive               = true,
                    documentPath           = fullPath,
                    needsProfileCompletion = true
                ))

                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "$name created ✓") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Create failed: ${e.message}") }
            }
        }
    }

    // ── Change role (DB admin can change any user's role) ─────────────────────

    fun changeRole(user: MUUser, newRole: String) {
        if (!canModify(user)) {
            _state.update { it.copy(error = "Permission denied") }
            return
        }
        if (!PermissionGuard.canChangeRole(
                editorRole = _state.value.currentRole,
                targetRole = user.role,
                newRole    = newRole,
                isDbAdmin  = isDbAdmin)) {
            _state.update { it.copy(error = "You cannot assign the role '$newRole'") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val roleEntity2 = db.roleDao().getById("${sanitizedCompany}_$newRole")
                val newPerms    = when {
                    newRole == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                    roleEntity2 != null && roleEntity2.permissions.isNotEmpty() -> roleEntity2.permissions
                    else -> listOf(Permissions.PERM_VIEW_PROFILE)
                }
                val permsJson = Json.encodeToString(newPerms)
                val newPath   = "users/${user.companyName}/${user.deptName}/$newRole/${user.id}"

                db.userDao().setRole(user.id, newRole)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply {
                            put("role", newRole); put("permissions", permsJson)
                            put("documentPath", newPath)
                        }.toString()
                    )
                    updateAccessControlField(user.id, token, "role",        newRole)
                    updateAccessControlField(user.id, token, "permissions", permsJson)
                    updateAccessControlField(user.id, token, "documentPath",newPath)
                } else {
                    syncManager.enqueue("ROLE_CHANGE", "users", user.id,
                        JSONObject().apply {
                            put("role", newRole); put("permissions", permsJson)
                            put("documentPath", newPath)
                        }.toString())
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false,
                    successMsg = "${user.name} is now $newRole") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Refresh single user after profile edit ────────────────────────────────

    fun refreshUser(updatedUserId: String) {
        viewModelScope.launch {
            if (monitor.serverReachable.value) {
                try {
                    val token = syncManager.getAdminToken()
                    val res   = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/users/records/$updatedUserId", token)
                    val o     = JSONObject(res)
                    val profile = safeObj(o.optString("profile"))
                    val work    = safeObj(o.optString("workStats"))
                    val issues  = safeObj(o.optString("issues"))
                    db.userDao().upsert(UserEntity(
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
                    ))
                } catch (e: Exception) {
                    android.util.Log.w("ManageUserVM", "refreshUser: ${e.message}")
                }
            }
            loadFromLocal()
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * True when the current editor can modify [user].
     * DB admin → always yes.
     * Administrator → yes within their company.
     * Manager/HR → only Employee, Intern, Team Lead in their company.
     */
    private fun canModify(user: MUUser): Boolean {
        if (isDbAdmin) return true
        val editorRole = _state.value.currentRole
        if (PermissionGuard.isSystemAdmin(editorRole)) return true
        // Must be same company
        if (user.companyName != sanitizedCompany) return false
        // Role hierarchy gate (existing check)
        val roleAllows = PermissionGuard.canEditProfile(
            editorRole = editorRole,
            targetRole = user.role,
            editorId   = "",   // editing other
            targetId   = user.id,
            isDbAdmin  = false
        )
        if (!roleAllows) return false
        // Live permission-list gate. Even if role hierarchy says yes, the editor
        // must currently hold at least one user-management permission. An admin
        // who was demoted mid-session is blocked here instead of acting on stale
        // role information.
        val USER_MOD_PERMS = setOf(
            Permissions.PERM_MODIFY_USER, Permissions.PERM_DELETE_USER, Permissions.PERM_VIEW_ALL_USERS,
            Permissions.PERM_MODIFY_TEAM_USER, Permissions.PERM_MANAGE_EMPLOYEES
        )
        return editorPerms.any { it in USER_MOD_PERMS }
    }

    private suspend fun updateAccessControlField(
        userId: String, token: String, field: String, value: Any
    ) {
        try {
            val res  = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/user_access_control/records" +
                        "?filter=(userId='$userId')&perPage=1", token)
            val acId = JSONObject(res).optJSONArray("items")
                ?.optJSONObject(0)?.optString("id") ?: return
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/user_access_control/records/$acId",
                token,
                JSONObject().apply {
                    when (value) {
                        is Boolean -> put(field, value)
                        is Int     -> put(field, value)
                        else       -> put(field, value.toString())
                    }
                }.toString()
            )
        } catch (e: Exception) {
            android.util.Log.w("ManageUserVM", "updateAC $field: ${e.message}")
        }
    }

    private fun applySearch(users: List<MUUser>, q: String) =
        if (q.isBlank()) users
        else users.filter {
            it.name.contains(q, true)           ||
                    it.email.contains(q, true)          ||
                    it.role.contains(q, true)           ||
                    it.designation.contains(q, true)    ||
                    it.originalCompany.contains(q, true)||
                    it.originalDept.contains(q, true)
        }

    private fun safeObj(raw: String): JSONObject =
        try { if (raw.startsWith("{")) JSONObject(raw) else JSONObject() }
        catch (_: Exception) { JSONObject() }
}