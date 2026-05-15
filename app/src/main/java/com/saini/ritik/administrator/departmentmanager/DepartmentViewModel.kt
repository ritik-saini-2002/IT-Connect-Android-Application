package com.saini.ritik.administrator.departmentmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.StringUtils
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.localdatabase.DepartmentEntity
import com.saini.ritik.localdatabase.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class DeptUiState(
    val isLoading   : Boolean              = false,
    val departments : List<DeptInfo>       = emptyList(),
    val users       : List<DeptUserInfo>   = emptyList(),
    val selectedDept: String?              = null,   // sanitizedName of selected dept
    val searchQuery : String               = "",
    val successMsg  : String?              = null,
    val error       : String?              = null,
    val isOffline   : Boolean              = false
)

data class DeptInfo(
    val id               : String,
    val name             : String,
    val sanitizedName    : String,
    val userCount        : Int,
    val activeUsers      : Int,
    val availableRoles   : List<String>
)

data class DeptUserInfo(
    val id          : String,
    val name        : String,
    val email       : String,
    val role        : String,
    val designation : String,
    val imageUrl    : String,
    val isActive    : Boolean,
    val department  : String,
    val sanitizedDept: String
)

@HiltViewModel
class DepartmentViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val db            : AppDatabase,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(DeptUiState())
    val state: StateFlow<DeptUiState> = _state.asStateFlow()

    private var sanitizedCompany = ""
    private var companyName      = ""
    private var allUsers         = emptyList<UserEntity>()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val session = authRepository.getSession() ?: error("Not logged in")
                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany
                companyName      = profile.companyName

                val offline = !monitor.serverReachable.value
                if (!offline) syncManager.refreshCompanyData(sanitizedCompany)

                loadFromLocal()
                _state.update { it.copy(isLoading = false, isOffline = offline) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadFromLocal() {
        if (sanitizedCompany.isBlank()) return
        val depts = db.deptDao().getByCompany(sanitizedCompany)
        allUsers  = db.userDao().getByCompany(sanitizedCompany)

        _state.update { s ->
            s.copy(
                departments = depts.map { d ->
                    DeptInfo(
                        id             = d.id,
                        name           = d.name,
                        sanitizedName  = d.sanitizedName,
                        userCount      = allUsers.count { it.sanitizedDepartment == d.sanitizedName },
                        activeUsers    = allUsers.count { it.sanitizedDepartment == d.sanitizedName && it.isActive },
                        availableRoles = d.availableRoles
                    )
                },
                users = usersForDept(s.selectedDept)
            )
        }
    }

    private fun usersForDept(sd: String?): List<DeptUserInfo> {
        val src = if (sd == null) allUsers
        else allUsers.filter { it.sanitizedDepartment == sd }
        return src.map { u ->
            DeptUserInfo(
                id           = u.id,
                name         = u.name,
                email        = u.email,
                role         = u.role,
                designation  = u.designation,
                imageUrl     = u.imageUrl,
                isActive     = u.isActive,
                department   = u.department,
                sanitizedDept= u.sanitizedDepartment
            )
        }
    }

    // ── Select department (drill-in) ──────────────────────────────────────────

    fun selectDept(sd: String?) {
        _state.update { s ->
            s.copy(selectedDept = sd, users = usersForDept(sd), searchQuery = "")
        }
    }

    // ── Create department ─────────────────────────────────────────────────────

    fun createDepartment(deptName: String) {
        if (deptName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val sd     = StringUtils.sanitize(deptName)
                val deptId = "${sanitizedCompany}_$sd"

                // 1. Write to local Room immediately (optimistic, pendingCreate = true)
                db.deptDao().upsert(
                    DepartmentEntity(
                        id                   = deptId,
                        name                 = deptName,
                        sanitizedName        = sd,
                        companyName          = companyName,
                        sanitizedCompanyName = sanitizedCompany,
                        pendingCreate        = true
                    )
                )

                if (monitor.serverReachable.value) {
                    val serverSuccess = applyDeptToServer(deptName, sd, action = "add")
                    if (serverSuccess) {
                        // Clear the pending flag — server has it now
                        db.deptDao().upsert(
                            DepartmentEntity(
                                id                   = deptId,
                                name                 = deptName,
                                sanitizedName        = sd,
                                companyName          = companyName,
                                sanitizedCompanyName = sanitizedCompany,
                                pendingCreate        = false
                            )
                        )
                    } else {
                        // Server had no company_metadata record — enqueue for later
                        syncManager.enqueue("UPDATE", "companies_metadata", sanitizedCompany,
                            JSONObject().apply {
                                put("action",   "add_dept")
                                put("deptName", deptName)
                            }.toString()
                        )
                    }
                } else {
                    syncManager.enqueue("UPDATE", "companies_metadata", sanitizedCompany,
                        JSONObject().apply {
                            put("action",   "add_dept")
                            put("deptName", deptName)
                        }.toString()
                    )
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "Department '$deptName' created") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Delete department ─────────────────────────────────────────────────────

    fun deleteDepartment(dept: DeptInfo) {
        if (dept.userCount > 0) {
            _state.update { it.copy(error = "Cannot delete '${dept.name}' — ${dept.userCount} users. Move them first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (monitor.serverReachable.value) {
                    val serverSuccess = applyDeptToServer(dept.name, dept.sanitizedName, action = "remove")
                    if (serverSuccess) {
                        db.deptDao().delete(dept.id)   // only delete locally after server confirms
                    } else {
                        // Still delete locally — server record not found means it's already gone
                        db.deptDao().delete(dept.id)
                        syncManager.enqueue("UPDATE", "companies_metadata", sanitizedCompany,
                            JSONObject().apply {
                                put("action",   "remove_dept")
                                put("deptName", dept.name)
                            }.toString()
                        )
                    }
                } else {
                    db.deptDao().delete(dept.id)
                    syncManager.enqueue("UPDATE", "companies_metadata", sanitizedCompany,
                        JSONObject().apply {
                            put("action",   "remove_dept")
                            put("deptName", dept.name)
                        }.toString()
                    )
                }
                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "Department '${dept.name}' deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Move user to different department ─────────────────────────────────────

    fun moveUserToDepartment(user: DeptUserInfo, targetDept: DeptInfo) {
        if (user.sanitizedDept == targetDept.sanitizedName) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val newPath = "users/$sanitizedCompany/${targetDept.sanitizedName}/${user.role}/${user.id}"
                db.userDao().setDepartment(user.id, targetDept.name, targetDept.sanitizedName)

                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply {
                            put("department",          targetDept.name)
                            put("sanitizedDepartment", targetDept.sanitizedName)
                            put("documentPath",        newPath)
                        }.toString()
                    )

                    // Update access_control
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
                                put("department",         targetDept.name)
                                put("sanitizedDepartment",targetDept.sanitizedName)
                                put("documentPath",       newPath)
                            }.toString()
                        )
                    }

                    val siRes = syncManager.pbGet(
                        "${AppConfig.BASE_URL}/api/collections/user_search_index/records" +
                                "?filter=(userId='${user.id}')&perPage=1", token
                    )
                    val siId = JSONObject(siRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                    if (!siId.isNullOrEmpty()) {
                        syncManager.pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/user_search_index/records/$siId",
                            token,
                            JSONObject().apply {
                                put("department",          targetDept.name)
                                put("sanitizedDepartment", targetDept.sanitizedName)
                                put("documentPath",        newPath)
                            }.toString()
                        )
                    }

                } else {
                    syncManager.enqueue(
                        type       = "MOVE_USER",
                        collection = "users",
                        recordId   = user.id,
                        payload    = JSONObject().apply {
                            put("role",                user.role)
                            put("department",          targetDept.name)
                            put("sanitizedDepartment", targetDept.sanitizedName)
                            put("documentPath",        "users/$sanitizedCompany/${targetDept.sanitizedName}/${user.role}/${user.id}")
                        }.toString()
                    )
                }

                loadFromLocal()
                _state.update { it.copy(isLoading = false,
                    successMsg = "${user.name} moved to ${targetDept.name}") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Move user to different role ───────────────────────────────────────────

    fun moveUserToRole(user: DeptUserInfo, newRole: String) {
        if (user.role == newRole) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                db.userDao().setRole(user.id, newRole)
                val roleEntity = db.roleDao().getById("${sanitizedCompany}_$newRole")
                val newPerms  = when {
                    newRole == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                    roleEntity != null && roleEntity.permissions.isNotEmpty() -> roleEntity.permissions
                    else -> listOf(Permissions.PERM_VIEW_PROFILE)
                }
                val permsJson = Json.encodeToString(newPerms)

                if (monitor.serverReachable.value) {
                    val token    = syncManager.getAdminToken()
                    syncManager.pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/users/records/${user.id}",
                        token,
                        JSONObject().apply {
                            put("role",        newRole)
                            put("permissions", permsJson)
                        }.toString()
                    )
                } else {
                    syncManager.enqueue("ROLE_CHANGE", "users", user.id,
                        JSONObject().apply {
                            put("role",        newRole)
                            put("permissions", permsJson)
                        }.toString()
                    )
                }
                loadFromLocal()
                _state.update { it.copy(isLoading = false, successMsg = "${user.name} is now $newRole") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        val sd  = _state.value.selectedDept
        val src = if (sd == null) allUsers else allUsers.filter { it.sanitizedDepartment == sd }
        val q   = query.trim()
        val filtered = if (q.isBlank()) src
        else src.filter {
            it.name.contains(q, true) || it.email.contains(q, true) ||
                    it.role.contains(q, true) || it.designation.contains(q, true)
        }
        _state.update { it.copy(searchQuery = query,
            users = filtered.map { u ->
                DeptUserInfo(u.id, u.name, u.email, u.role, u.designation,
                    u.imageUrl, u.isActive, u.department, u.sanitizedDepartment)
            })
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Server helpers ────────────────────────────────────────────────────────

    private suspend fun applyDeptToServer(deptName: String, sd: String, action: String): Boolean {
        return try {
            val token   = syncManager.getAdminToken()
            val compRes = syncManager.pbGet(
                "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                        "?filter=(sanitizedName='$sanitizedCompany')&perPage=1", token
            )
            val item = JSONObject(compRes).optJSONArray("items")?.optJSONObject(0)
            if (item == null) {
                android.util.Log.w("DeptVM",
                    "applyDeptToServer: no companies_metadata record for $sanitizedCompany")
                return false   // ← was silently returning before; now callers know
            }
            val cId = item.optString("id")

            val arr = when (val raw = item.opt("departments")) {
                is JSONArray -> raw
                is String    -> try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
                else         -> JSONArray()
            }

            val current = (0 until arr.length()).map { arr.optString(it) }.toMutableList()
            when (action) {
                "add"    -> if (!current.contains(deptName)) current.add(deptName)
                "remove" -> current.remove(deptName)
            }
            syncManager.pbPatch(
                "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
                token,
                JSONObject().apply {
                    put("departments", JSONArray(current))
                }.toString()
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("DeptVM", "applyDeptToServer failed: ${e.message}")
            false
        }
    }


}