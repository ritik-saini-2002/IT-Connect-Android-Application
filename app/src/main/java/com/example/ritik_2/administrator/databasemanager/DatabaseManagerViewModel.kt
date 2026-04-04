package com.example.ritik_2.administrator.databasemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.administrator.databasemanager.models.*
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.core.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class DatabaseManagerViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val authRepository: AuthRepository,
    private val syncManager   : SyncManager,
    private val monitor       : ConnectivityMonitor,
    private val db            : AppDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DBUiState())
    val state: StateFlow<DBUiState> = _state.asStateFlow()

    private var allRecords   : List<DBRecord> = emptyList()
    private var currentRole  : String         = ""
    private var adminCompany : String         = ""

    init { loadCurrentUser() }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val session = authRepository.getSession() ?: return@launch
                val profile = dataSource.getUserProfile(session.userId).getOrNull() ?: return@launch
                currentRole  = profile.role
                adminCompany = profile.sanitizedCompany

                _state.update { it.copy(
                    adminCompany = profile.companyName,
                    isOffline    = !monitor.serverReachable.value
                ) }

                if (monitor.serverReachable.value) {
                    // Refresh everything so cache is populated
                    syncManager.refreshAllCollections()
                    syncManager.refreshCompanyData(adminCompany)
                }

                // Load tab from cache (now populated)
                loadTab(DBTab.USERS)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun switchTab(tab: DBTab) {
        _state.update { it.copy(currentTab = tab, searchQuery = "") }
        loadTab(tab)
    }

    fun search(q: String) {
        _state.update { it.copy(searchQuery = q) }
        val filtered = if (q.isBlank()) allRecords
        else allRecords.filter { rec ->
            rec.title.contains(q, true) || rec.sub1.contains(q, true) || rec.sub2.contains(q, true)
        }
        _state.update { it.copy(records = filtered) }
    }

    fun refresh() {
        viewModelScope.launch {
            if (monitor.serverReachable.value) {
                syncManager.refreshAllCollections()
                syncManager.refreshCompanyData(adminCompany)
            }
            loadTab(_state.value.currentTab)
        }
    }

    fun deleteRecord(record: DBRecord) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val col = when (_state.value.currentTab) {
                    DBTab.USERS     -> "users"
                    DBTab.COMPANIES -> "companies_metadata"
                    DBTab.COLLECTIONS -> return@launch
                    else            -> return@launch
                }
                if (monitor.serverReachable.value) {
                    val token = syncManager.getAdminToken()
                    syncManager.pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/$col/records/${record.id}", token)
                } else {
                    syncManager.enqueue("DELETE", col, record.id, "{}")
                }
                _state.update { it.copy(successMsg = "${record.title} deleted") }
                loadTab(_state.value.currentTab)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateCollectionRules(collectionId: String, rules: CollectionRules) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (!monitor.serverReachable.value) {
                    _state.update { it.copy(isLoading = false, error = "Server unreachable") }
                    return@launch
                }
                val token = syncManager.getAdminToken()
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/$collectionId", token,
                    JSONObject().apply {
                        put("listRule",   if (rules.listRule.isBlank())   JSONObject.NULL else rules.listRule)
                        put("viewRule",   if (rules.viewRule.isBlank())   JSONObject.NULL else rules.viewRule)
                        put("createRule", if (rules.createRule.isBlank()) JSONObject.NULL else rules.createRule)
                        put("updateRule", if (rules.updateRule.isBlank()) JSONObject.NULL else rules.updateRule)
                        put("deleteRule", if (rules.deleteRule.isBlank()) JSONObject.NULL else rules.deleteRule)
                    }.toString()
                )
                syncManager.refreshAllCollections()
                _state.update { it.copy(isLoading = false, successMsg = "Rules updated") }
                loadTab(DBTab.COLLECTIONS)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createIndex(collectionId: String, idx: DBIndex) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (!monitor.serverReachable.value) {
                    _state.update { it.copy(isLoading = false, error = "Server unreachable") }
                    return@launch
                }
                val token    = syncManager.getAdminToken()
                val existRes = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/$collectionId", token)
                val col      = JSONObject(existRes)
                val indexes  = try { JSONArray(col.optString("indexes", "[]")) }
                catch (_: Exception) { JSONArray() }
                val newIdx = JSONObject().apply {
                    put("name",   idx.name)
                    put("type",   idx.type)
                    put("fields", JSONArray(idx.fields))
                    if (idx.unique) put("unique", true)
                }
                indexes.put(newIdx)
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/$collectionId", token,
                    JSONObject().apply { put("indexes", indexes) }.toString())
                syncManager.refreshAllCollections()
                _state.update { it.copy(isLoading = false, successMsg = "Index '${idx.name}' created") }
                loadTab(DBTab.COLLECTIONS)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createCollection(name: String, type: String, fields: List<DBField>) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                if (!monitor.serverReachable.value) {
                    _state.update { it.copy(isLoading = false, error = "Server unreachable") }
                    return@launch
                }
                val token     = syncManager.getAdminToken()
                val fieldsArr = JSONArray().apply {
                    fields.forEach { f ->
                        put(JSONObject().apply {
                            put("name", f.name); put("type", f.type); put("required", f.required)
                        })
                    }
                }
                syncManager.pbPost(
                    "${AppConfig.BASE_URL}/api/collections", token,
                    JSONObject().apply {
                        put("name", name); put("type", type); put("fields", fieldsArr)
                        put("listRule", JSONObject.NULL); put("viewRule", JSONObject.NULL)
                        put("createRule", JSONObject.NULL); put("updateRule", JSONObject.NULL)
                        put("deleteRule", JSONObject.NULL)
                    }.toString()
                )
                syncManager.refreshAllCollections()
                _state.update { it.copy(isLoading = false, successMsg = "Collection '$name' created") }
                loadTab(DBTab.COLLECTIONS)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Tab loaders ───────────────────────────────────────────────────────────

    private fun loadTab(tab: DBTab) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val records: List<DBRecord> = when (tab) {
                    DBTab.USERS       -> loadUsers()
                    DBTab.DEPARTMENTS -> loadDepartments()
                    DBTab.COMPANIES   -> loadCompanies()
                    DBTab.COLLECTIONS -> loadCollections()
                }
                allRecords = records
                _state.update { it.copy(
                    isLoading  = false,
                    records    = records,
                    totalCount = records.size,
                    currentTab = tab
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadUsers(): List<DBRecord> {
        val users = if (currentRole == "Administrator") db.userDao().getAll()
        else db.userDao().getByCompany(adminCompany)
        return users.map { u ->
            DBRecord(
                id    = u.id,
                title = u.name.ifBlank { u.email },
                sub1  = u.email,
                sub2  = "${u.role} · ${u.department}",
                badge = if (u.isActive) "Active" else "Inactive",
                extra = mapOf(
                    "Company"      to u.companyName,
                    "Designation"  to u.designation,
                    "Department"   to u.department,
                    "Phone"        to u.phoneNumber,
                    "Employee ID"  to u.employeeId,
                    "Reporting To" to u.reportingTo,
                    "Experience"   to if (u.experience > 0) "${u.experience} yrs" else "",
                    "Active Proj"  to u.activeProjects.toString(),
                    "Done Proj"    to u.completedProjects.toString(),
                    "Salary"       to if (u.salary > 0) u.salary.toString() else ""
                )
            )
        }
    }

    private suspend fun loadDepartments(): List<DBRecord> {
        val depts = db.deptDao().getByCompany(adminCompany)
        return depts.map { d ->
            val users = db.userDao().getByDepartment(adminCompany, d.sanitizedName)
            DBRecord(
                id    = d.id,
                title = d.name,
                sub1  = d.companyName,
                sub2  = "${users.size} users · ${users.count { it.isActive }} active",
                badge = d.sanitizedName,
                extra = mapOf(
                    "Company"       to d.companyName,
                    "Total Users"   to users.size.toString(),
                    "Active Users"  to users.count { it.isActive }.toString(),
                    "Roles Present" to users.map { it.role }.distinct().joinToString(", ")
                )
            )
        }
    }

    private suspend fun loadCompanies(): List<DBRecord> {
        // Try Room cache first
        var companies = db.companyDao().getAll()

        // If cache empty and server reachable, fetch directly
        if (companies.isEmpty() && monitor.serverReachable.value) {
            try {
                val token = syncManager.getAdminToken()
                val res   = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=200",
                    token)
                val items = JSONObject(res).optJSONArray("items") ?: return emptyList()
                val fetched = (0 until items.length()).mapNotNull { i ->
                    runCatching {
                        val o = items.getJSONObject(i)
                        val roles = try {
                            val arr = JSONArray(o.optString("availableRoles", "[]"))
                            (0 until arr.length()).map { arr.optString(it) }
                        } catch (_: Exception) { emptyList() }
                        val depts = try {
                            val arr = JSONArray(o.optString("departments", "[]"))
                            (0 until arr.length()).map { arr.optString(it) }
                        } catch (_: Exception) { emptyList() }
                        com.example.ritik_2.localdatabase.CompanyEntity(
                            sanitizedName  = o.optString("sanitizedName"),
                            originalName   = o.optString("originalName"),
                            totalUsers     = o.optInt("totalUsers", 0),
                            activeUsers    = o.optInt("activeUsers", 0),
                            availableRoles = roles,
                            departments    = depts
                        )
                    }.getOrNull()
                }
                db.companyDao().upsertAll(fetched)
                companies = fetched
            } catch (e: Exception) {
                android.util.Log.e("DBManagerVM", "loadCompanies direct fetch: ${e.message}")
            }
        }

        return companies.map { c ->
            DBRecord(
                id    = c.sanitizedName,
                title = c.originalName,
                sub1  = "${c.totalUsers} total · ${c.activeUsers} active",
                sub2  = c.sanitizedName,
                badge = "${c.totalUsers} users",
                extra = mapOf(
                    "Roles"       to c.availableRoles.joinToString(", "),
                    "Departments" to c.departments.joinToString(", ")
                )
            )
        }
    }

    private suspend fun loadCollections(): List<DBRecord> {
        val cols = db.collectionDao().getAll()
        return cols.map { c ->
            val fields  = try { JSONArray(c.fields).length()  } catch (_: Exception) { 0 }
            val indexes = try { JSONArray(c.indexes).length() } catch (_: Exception) { 0 }
            DBRecord(
                id    = c.id,
                title = c.name,
                sub1  = "Type: ${c.type}",
                sub2  = "$fields fields · $indexes indexes",
                badge = c.type,
                extra = mapOf(
                    "List Rule"   to c.listRule.ifBlank  { "null (open)" },
                    "View Rule"   to c.viewRule.ifBlank  { "null (open)" },
                    "Create Rule" to c.createRule.ifBlank{ "null (open)" },
                    "Update Rule" to c.updateRule.ifBlank{ "null (open)" },
                    "Delete Rule" to c.deleteRule.ifBlank{ "null (open)" },
                    "Fields"      to fields.toString(),
                    "Indexes"     to indexes.toString()
                ),
                collectionId = c.id,
                fieldsJson   = c.fields,
                indexesJson  = c.indexes
            )
        }
    }
}