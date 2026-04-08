package com.example.ritik_2.administrator.databasemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.administrator.databasemanager.models.*
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.core.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// ── Collection folder model ───────────────────────────────────────────────────

data class CollectionFolder(
    val id          : String,
    val name        : String,
    val type        : String,           // "base" | "auth" | "view"
    val fieldCount  : Int,
    val indexCount  : Int,
    val listRule    : String,
    val viewRule    : String,
    val createRule  : String,
    val updateRule  : String,
    val deleteRule  : String,
    val fieldsJson  : String = "[]",
    val indexesJson : String = "[]",
    // Drill-down records
    val records     : List<DBRecord> = emptyList(),
    val isExpanded  : Boolean = false,
    val isLoading   : Boolean = false
)

data class DBUiState(
    val isLoading      : Boolean                 = false,
    val accessDenied   : Boolean                 = false,    // ← new gate
    val currentTab     : DBTab                   = DBTab.COLLECTIONS,
    val records        : List<DBRecord>           = emptyList(),
    val collections    : List<CollectionFolder>   = emptyList(), // ← folder view
    val expandedCollId : String?                  = null,
    val searchQuery    : String                   = "",
    val totalCount     : Int                      = 0,
    val error          : String?                  = null,
    val successMsg     : String?                  = null,
    val adminCompany   : String                   = "",
    val isOffline      : Boolean                  = false,
    val isDbAdmin      : Boolean                  = false
)

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
                val session   = authRepository.getSession() ?: return@launch
                val isDbAdmin = authRepository.isDbAdmin()
                val profile   = dataSource.getUserProfile(session.userId).getOrNull()
                    ?: return@launch

                currentRole  = profile.role
                adminCompany = profile.sanitizedCompany

                // ── Access gate: DB admin OR has "database_manager" permission ─
                val hasAccess = PermissionGuard.canAccessDatabaseManager(
                    permissions = profile.permissions,
                    isDbAdmin   = isDbAdmin
                )
                if (!hasAccess) {
                    _state.update { it.copy(accessDenied = true) }
                    return@launch
                }

                _state.update { it.copy(
                    adminCompany = profile.companyName,
                    isOffline    = !monitor.serverReachable.value,
                    isDbAdmin    = isDbAdmin
                ) }

                if (monitor.serverReachable.value) {
                    syncManager.refreshAllCollections()
                    syncManager.refreshCompanyData(adminCompany)
                }

                // Start on COLLECTIONS tab (folder view)
                loadCollectionFolders()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Collection folder view ────────────────────────────────────────────────

    private fun loadCollectionFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val cached = db.collectionDao().getAll()
                val folders = cached.map { c ->
                    CollectionFolder(
                        id         = c.id,
                        name       = c.name,
                        type       = c.type,
                        fieldCount = try { JSONArray(c.fields).length()  } catch (_: Exception) { 0 },
                        indexCount = try { JSONArray(c.indexes).length() } catch (_: Exception) { 0 },
                        listRule   = c.listRule,
                        viewRule   = c.viewRule,
                        createRule = c.createRule,
                        updateRule = c.updateRule,
                        deleteRule = c.deleteRule,
                        fieldsJson = c.fields,
                        indexesJson= c.indexes
                    )
                }
                _state.update { it.copy(
                    isLoading    = false,
                    collections  = folders,
                    currentTab   = DBTab.COLLECTIONS
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Drill into a collection — loads its records from PocketBase and
     * shows them inline in the folder row.
     * Only DB admin can trigger this.
     */
    fun toggleCollectionExpand(collId: String) {
        val isDbAdmin = _state.value.isDbAdmin
        viewModelScope.launch {
            val alreadyExpanded = _state.value.expandedCollId == collId
            if (alreadyExpanded) {
                _state.update { it.copy(expandedCollId = null) }
                return@launch
            }

            // Mark loading
            _state.update { s ->
                s.copy(
                    expandedCollId = collId,
                    collections    = s.collections.map { f ->
                        if (f.id == collId) f.copy(isLoading = true) else f
                    }
                )
            }

            try {
                if (!monitor.serverReachable.value || !isDbAdmin) {
                    _state.update { s ->
                        s.copy(
                            collections = s.collections.map { f ->
                                if (f.id == collId) f.copy(isLoading = false) else f
                            },
                            error = if (!isDbAdmin) "Only DB admin can browse records"
                            else "Server unreachable"
                        )
                    }
                    return@launch
                }

                val token   = syncManager.getAdminToken()
                val collName = _state.value.collections.find { it.id == collId }?.name ?: return@launch
                val res     = syncManager.pbGet(
                    "${AppConfig.BASE_URL}/api/collections/$collName/records?perPage=50", token)
                val items   = JSONObject(res).optJSONArray("items") ?: JSONArray()
                val records = (0 until items.length()).map { i ->
                    val o = items.getJSONObject(i)
                    DBRecord(
                        id    = o.optString("id"),
                        title = o.optString("id"),
                        sub1  = o.keys().asSequence().take(3).joinToString(" · ") {
                            "$it: ${o.optString(it).take(20)}"
                        },
                        sub2  = "",
                        badge = "record",
                        extra = o.keys().asSequence().associate { k -> k to o.optString(k) },
                        rawJson = o.toString(2)
                    )
                }

                _state.update { s ->
                    s.copy(
                        collections = s.collections.map { f ->
                            if (f.id == collId) f.copy(
                                records   = records,
                                isLoading = false,
                                isExpanded = true
                            ) else f.copy(isExpanded = false)
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        collections = s.collections.map { f ->
                            if (f.id == collId) f.copy(isLoading = false) else f
                        },
                        error = e.message
                    )
                }
            }
        }
    }

    // ── Tab switch (non-collection tabs) ──────────────────────────────────────

    fun switchTab(tab: DBTab) {
        if (tab == DBTab.COLLECTIONS) { loadCollectionFolders(); return }
        _state.update { it.copy(currentTab = tab, searchQuery = "", expandedCollId = null) }
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

    // Also filter collection folders by name
    fun searchCollections(q: String) {
        _state.update { it.copy(searchQuery = q) }
    }

    fun refresh() {
        viewModelScope.launch {
            if (monitor.serverReachable.value) {
                syncManager.refreshAllCollections()
                syncManager.refreshCompanyData(adminCompany)
            }
            if (_state.value.currentTab == DBTab.COLLECTIONS) loadCollectionFolders()
            else loadTab(_state.value.currentTab)
        }
    }

    // ── Record delete — DB admin only ─────────────────────────────────────────

    fun deleteRecord(record: DBRecord) {
        if (!_state.value.isDbAdmin) {
            _state.update { it.copy(error = "Only DB admin can delete records") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val col = when (_state.value.currentTab) {
                    DBTab.USERS     -> "users"
                    DBTab.COMPANIES -> "companies_metadata"
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

    // ── Collection management — DB admin only ─────────────────────────────────

    fun updateCollectionRules(collectionId: String, rules: CollectionRules) {
        if (!_state.value.isDbAdmin) {
            _state.update { it.copy(error = "Only DB admin can edit collection rules") }
            return
        }
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
                loadCollectionFolders()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createCollection(name: String, type: String, fields: List<DBField>) {
        if (!_state.value.isDbAdmin) {
            _state.update { it.copy(error = "Only DB admin can create collections") }
            return
        }
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
                loadCollectionFolders()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createIndex(collectionId: String, idx: DBIndex) {
        if (!_state.value.isDbAdmin) {
            _state.update { it.copy(error = "Only DB admin can create indexes") }
            return
        }
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
                val newIdx   = JSONObject().apply {
                    put("name", idx.name); put("type", idx.type)
                    put("fields", JSONArray(idx.fields))
                    if (idx.unique) put("unique", true)
                }
                indexes.put(newIdx)
                syncManager.pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/$collectionId", token,
                    JSONObject().apply { put("indexes", indexes) }.toString())
                syncManager.refreshAllCollections()
                _state.update { it.copy(isLoading = false, successMsg = "Index '${idx.name}' created") }
                loadCollectionFolders()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, successMsg = null) }

    // ── Non-collection tab loaders ────────────────────────────────────────────

    private fun loadTab(tab: DBTab) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val records: List<DBRecord> = when (tab) {
                    DBTab.USERS       -> loadUsers()
                    DBTab.DEPARTMENTS -> loadDepartments()
                    DBTab.COMPANIES   -> loadCompanies()
                    DBTab.COLLECTIONS -> return@launch
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
        val users = if (currentRole == "Administrator" || _state.value.isDbAdmin)
            db.userDao().getAll()
        else db.userDao().getByCompany(adminCompany)
        return users.map { u ->
            DBRecord(id = u.id, title = u.name.ifBlank { u.email },
                sub1 = u.email, sub2 = "${u.role} · ${u.department}",
                badge = if (u.isActive) "Active" else "Inactive",
                extra = mapOf(
                    "Company"     to u.companyName,
                    "Designation" to u.designation,
                    "Department"  to u.department,
                    "Active Proj" to u.activeProjects.toString(),
                    "Done Proj"   to u.completedProjects.toString()
                ))
        }
    }

    private suspend fun loadDepartments(): List<DBRecord> {
        val depts = db.deptDao().getByCompany(adminCompany)
        return depts.map { d ->
            val users = db.userDao().getByDepartment(adminCompany, d.sanitizedName)
            DBRecord(id = d.id, title = d.name, sub1 = d.companyName,
                sub2 = "${users.size} users · ${users.count { it.isActive }} active",
                badge = d.sanitizedName,
                extra = mapOf("Total Users" to users.size.toString(),
                    "Active" to users.count { it.isActive }.toString()))
        }
    }

    private suspend fun loadCompanies(): List<DBRecord> {
        val companies = db.companyDao().getAll()
        return companies.map { c ->
            DBRecord(id = c.sanitizedName, title = c.originalName,
                sub1 = "${c.totalUsers} total · ${c.activeUsers} active",
                sub2 = c.sanitizedName, badge = "${c.totalUsers} users",
                extra = mapOf(
                    "Roles" to c.availableRoles.joinToString(", "),
                    "Depts" to c.departments.joinToString(", ")
                ))
        }
    }
}