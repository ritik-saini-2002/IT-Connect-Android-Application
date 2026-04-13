package com.example.ritik_2.core

import android.util.Log
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.localdatabase.CollectionEntity
import com.example.ritik_2.localdatabase.CompanyEntity
import com.example.ritik_2.localdatabase.DepartmentEntity
import com.example.ritik_2.localdatabase.RoleEntity
import com.example.ritik_2.localdatabase.SyncQueueEntity
import com.example.ritik_2.localdatabase.UserEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val db     : AppDatabase,
    private val http   : OkHttpClient,
    private val monitor: ConnectivityMonitor
) {
    private val tag        = "SyncManager"
    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokenMutex = Mutex()

    // ── Admin token (writes + collection management only) ─────────────────────
    private var adminToken     = ""
    private var tokenFetchedAt = 0L
    private val tokenTtl       = 10 * 60 * 1000L

    // ── User token (set after login/restore, used for all reads) ─────────────
    @Volatile private var userToken = ""

    /**
     * Call this immediately after a successful login or session restore.
     * Enables all read operations without requiring admin credentials.
     */
    fun setUserToken(token: String) {
        userToken = token
        Log.d(tag, "User token set ✅")
    }

    /**
     * Returns the user token for read operations.
     * Falls back to admin token only if user token is not yet set.
     */
    private suspend fun getReadToken(): String {
        if (userToken.isNotBlank()) return userToken
        return try {
            getAdminToken()
        } catch (e: Exception) {
            error("No auth token available — user not logged in and no admin token configured")
        }
    }

    init {
        scope.launch {
            monitor.serverReachable.collectLatest { reachable ->
                if (reachable) {
                    Log.d(tag, "Server reachable — flushing sync queue")
                    flushQueue()
                }
            }
        }
    }

    // ── Full company refresh (READ — uses user token) ─────────────────────────

    suspend fun refreshCompanyData(sanitizedCompany: String) = withContext(Dispatchers.IO) {
        if (!monitor.serverReachable.value) {
            Log.d(tag, "Offline — skipping refreshCompanyData")
            return@withContext
        }
        try {
            val token = getReadToken()
            refreshUsers(sanitizedCompany, token)
            refreshRoles(sanitizedCompany, token)
            refreshRoleDefinitions(sanitizedCompany, token)
            refreshDepartments(sanitizedCompany, token)
            refreshCompanies(token)
            Log.d(tag, "refreshCompanyData ✅ $sanitizedCompany")
        } catch (e: Exception) {
            Log.e(tag, "refreshCompanyData failed: ${e.message}")
        }
    }

    /**
     * Fire-and-forget: called after login to sync role permission templates.
     * Never blocks the login path — runs on bgScope in AuthRepository.
     */
    suspend fun syncRoleDefinitions(sanitizedCompany: String) = withContext(Dispatchers.IO) {
        if (!monitor.serverReachable.value) return@withContext
        try {
            val token = getReadToken()
            refreshRoleDefinitions(sanitizedCompany, token)
            Log.d(tag, "syncRoleDefinitions ✅ $sanitizedCompany")
        } catch (e: Exception) {
            Log.w(tag, "syncRoleDefinitions failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Refreshes all PocketBase collection metadata.
     * Requires admin token — only called by DatabaseManagerViewModel.
     */
    suspend fun refreshAllCollections() = withContext(Dispatchers.IO) {
        if (!monitor.serverReachable.value) return@withContext
        try {
            val token = getAdminToken()
            val res   = pbGet("${AppConfig.BASE_URL}/api/collections?perPage=200", token)
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext
            val cols  = (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                CollectionEntity(
                    id         = o.optString("id"),
                    name       = o.optString("name"),
                    type       = o.optString("type"),
                    listRule   = o.optString("listRule"),
                    viewRule   = o.optString("viewRule"),
                    createRule = o.optString("createRule"),
                    updateRule = o.optString("updateRule"),
                    deleteRule = o.optString("deleteRule"),
                    fields     = o.optJSONArray("fields")?.toString()
                        ?: o.optJSONArray("schema")?.toString() ?: "[]",
                    indexes    = o.optJSONArray("indexes")?.toString() ?: "[]"
                )
            }
            db.collectionDao().clear()
            db.collectionDao().upsertAll(cols)
            refreshCompanies(token)
            Log.d(tag, "refreshAllCollections ✅ ${cols.size} collections")
        } catch (e: Exception) {
            Log.e(tag, "refreshAllCollections: ${e.message}")
        }
    }

    // ── Flush pending queue (WRITE — needs admin token) ───────────────────────

    suspend fun flushQueue() = withContext(Dispatchers.IO) {
        val pending = db.syncQueueDao().getAll()
        if (pending.isEmpty()) return@withContext

        val token = try {
            getAdminToken()
        } catch (e: Exception) {
            Log.e(tag, "flushQueue: no admin token — ${e.message}. Will retry later.")
            return@withContext
        }

        pending.forEach { op ->
            try {
                when (op.operationType) {
                    "CREATE" -> pbPost(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records",
                        token, op.payload)

                    "UPDATE" -> pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records/${op.recordId}",
                        token, op.payload)

                    "DELETE" -> pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records/${op.recordId}",
                        token)

                    "ROLE_CHANGE" -> {
                        val j = JSONObject(op.payload)
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/users/records/${op.recordId}",
                            token,
                            JSONObject().apply {
                                put("role",         j.optString("role"))
                                put("permissions",  j.optString("permissions"))
                                put("documentPath", j.optString("documentPath"))
                            }.toString()
                        )
                    }

                    "MOVE_USER" -> {
                        val j = JSONObject(op.payload)
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/users/records/${op.recordId}",
                            token,
                            JSONObject().apply {
                                put("role",                j.optString("role"))
                                put("department",          j.optString("department"))
                                put("sanitizedDepartment", j.optString("sanitizedDepartment"))
                                put("documentPath",        j.optString("documentPath"))
                            }.toString()
                        )
                    }
                }
                db.syncQueueDao().dequeue(op.queueId)
                Log.d(tag, "Flushed ${op.operationType} on ${op.collection}/${op.recordId}")
            } catch (e: Exception) {
                db.syncQueueDao().markFailed(op.queueId, e.message ?: "unknown")
                Log.w(tag, "Flush failed for ${op.queueId}: ${e.message}")
            }
        }
    }

    suspend fun enqueue(type: String, collection: String, recordId: String, payload: String) {
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                operationType = type,
                collection    = collection,
                recordId      = recordId,
                payload       = payload
            )
        )
        if (monitor.serverReachable.value) flushQueue()
    }

    suspend fun pendingCount() = db.syncQueueDao().pendingCount()

    // ── Admin token (writes + DatabaseManager only) ───────────────────────────

    suspend fun getAdminToken(): String = tokenMutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (adminToken.isNotBlank() && (now - tokenFetchedAt) < tokenTtl)
                return@withContext adminToken

            if (AppConfig.ADMIN_EMAIL.isBlank() || AppConfig.ADMIN_PASS.isBlank()) {
                error("Admin credentials not configured in local.properties " +
                        "(pb.admin.email / pb.admin.password)")
            }

            listOf(
                "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
                "${AppConfig.BASE_URL}/api/admins/auth-with-password"
            ).forEach { url ->
                try {
                    val body = JSONObject().apply {
                        put("identity", AppConfig.ADMIN_EMAIL)
                        put("password", AppConfig.ADMIN_PASS)
                    }.toString().toRequestBody("application/json".toMediaType())

                    val res     = http.newCall(
                        Request.Builder().url(url).post(body).build()
                    ).execute()
                    val resBody = res.body?.string() ?: ""
                    val ok      = res.isSuccessful
                    res.close()

                    if (ok) {
                        val t = JSONObject(resBody).optString("token")
                        if (t.isNotEmpty()) {
                            adminToken     = t
                            tokenFetchedAt = now
                            Log.d(tag, "getAdminToken ✅ cached")
                            return@withContext t
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "getAdminToken: $url failed — ${e.message}")
                }
            }
            error("No admin token available — check pb.admin.email / pb.admin.password in local.properties")
        }
    }

    // ── Private refresh helpers ───────────────────────────────────────────────

    private suspend fun refreshUsers(sc: String, token: String) {
        var page = 1
        val all  = mutableListOf<UserEntity>()
        while (true) {
            val res   = pbGet(
                "${AppConfig.BASE_URL}/api/collections/users/records" +
                        "?filter=(sanitizedCompanyName='$sc')&perPage=200&page=$page",
                token
            )
            val json  = JSONObject(res)
            val items = json.optJSONArray("items") ?: break
            if (items.length() == 0) break

            for (i in 0 until items.length()) {
                val o       = items.getJSONObject(i)
                val profile = safeObj(o.optString("profile"))
                val work    = safeObj(o.optString("workStats"))
                val issues  = safeObj(o.optString("issues"))
                all.add(
                    UserEntity(
                        id                       = o.optString("id"),
                        name                     = o.optString("name"),
                        email                    = o.optString("email"),
                        role                     = o.optString("role"),
                        companyName              = o.optString("companyName"),
                        sanitizedCompanyName     = o.optString("sanitizedCompanyName"),
                        department               = o.optString("department"),
                        sanitizedDepartment      = o.optString("sanitizedDepartment"),
                        designation              = o.optString("designation"),
                        isActive                 = o.optBoolean("isActive", true),
                        documentPath             = o.optString("documentPath"),
                        imageUrl                 = profile.optString("imageUrl", ""),
                        phoneNumber              = profile.optString("phoneNumber", ""),
                        address                  = profile.optString("address", ""),
                        employeeId               = profile.optString("employeeId", ""),
                        reportingTo              = profile.optString("reportingTo", ""),
                        salary                   = profile.optDouble("salary", 0.0),
                        emergencyContactName     = profile.optString("emergencyContactName", ""),
                        emergencyContactPhone    = profile.optString("emergencyContactPhone", ""),
                        emergencyContactRelation = profile.optString("emergencyContactRelation", ""),
                        experience               = work.optInt("experience"),
                        completedProjects        = work.optInt("completedProjects"),
                        activeProjects           = work.optInt("activeProjects"),
                        pendingTasks             = work.optInt("pendingTasks"),
                        completedTasks           = work.optInt("completedTasks"),
                        totalComplaints          = issues.optInt("totalComplaints"),
                        resolvedComplaints       = issues.optInt("resolvedComplaints"),
                        pendingComplaints        = issues.optInt("pendingComplaints"),
                        needsProfileCompletion   = o.optBoolean("needsProfileCompletion", true)
                    )
                )
            }
            val totalPages = json.optInt("totalPages", 1)
            if (page >= totalPages) break
            page++
        }
        db.userDao().upsertAll(all)
        Log.d(tag, "refreshUsers: cached ${all.size} users for $sc")
    }

    private suspend fun refreshRoles(sc: String, token: String) {
        val res         = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sc')&perPage=1",
            token
        )
        val item        = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
        val companyName = item?.optString("originalName") ?: sc

        // Read from companies_metadata
        val arr = try {
            JSONArray(item?.optString("availableRoles", "[]") ?: "[]")
        } catch (_: Exception) { JSONArray() }
        val serverRoles = (0 until arr.length())
            .map { arr.optString(it) }
            .filter { it.isNotBlank() }

        // Always derive from actual cached users as well — union of both
        val usersInCompany = db.userDao().getByCompany(sc)
        val rolesFromUsers = usersInCompany
            .map { it.role }
            .filter { it.isNotBlank() }
            .distinct()

        val allRoleNames = (serverRoles + rolesFromUsers).distinct()

        if (allRoleNames.isEmpty()) {
            Log.w(tag, "refreshRoles: no roles found for $sc — skipping")
            return
        }

        val roles = allRoleNames.map { name ->
            RoleEntity(
                id                   = "${sc}_$name",
                name                 = name,
                sanitizedCompanyName = sc,
                companyName          = companyName,
                isCustom             = name !in Permissions.ALL_ROLES,
                userCount            = usersInCompany.count { it.role == name }
            )
        }

        db.roleDao().clearCompany(sc)
        db.roleDao().upsertAll(roles)
        Log.d(tag, "refreshRoles: cached ${roles.size} roles for $sc " +
                "(${serverRoles.size} from server, ${rolesFromUsers.size} from users)")

        // Self-heal: patch companies_metadata if server had empty roles but users have some
        if (serverRoles.isEmpty() && rolesFromUsers.isNotEmpty() && item != null) {
            try {
                val cId     = item.optString("id")
                val rolesArr = JSONArray().also { a -> allRoleNames.forEach { a.put(it) } }
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
                    token,
                    JSONObject().apply { put("availableRoles", rolesArr) }.toString()
                )
                Log.d(tag, "refreshRoles: self-healed companies_metadata.availableRoles")
            } catch (e: Exception) {
                Log.w(tag, "refreshRoles: self-heal patch failed: ${e.message}")
            }
        }
    }

    /**
     * Reads the `role_definitions` collection to populate RoleEntity.permissions.
     * If the collection is empty for this company, seeds it from local cache / forRole() defaults.
     */
    private suspend fun refreshRoleDefinitions(sc: String, token: String) {
        val res   = try {
            pbGet(
                "${AppConfig.BASE_URL}/api/collections/role_definitions/records" +
                        "?filter=(sanitizedCompanyName='$sc')&perPage=200",
                token
            )
        } catch (e: Exception) {
            Log.w(tag, "refreshRoleDefinitions: collection may not exist yet — ${e.message}")
            return
        }
        val items = JSONObject(res).optJSONArray("items") ?: return
        if (items.length() == 0) {
            seedRoleDefinitions(sc, token)
            return
        }
        for (i in 0 until items.length()) {
            val obj      = items.getJSONObject(i)
            val roleName = obj.optString("roleName")
            if (roleName.isBlank()) continue
            val permsArr = try { JSONArray(obj.optString("permissions", "[]")) }
                           catch (_: Exception) { JSONArray() }
            val perms    = (0 until permsArr.length())
                .map { permsArr.optString(it) }
                .filter { it.isNotBlank() }

            // System_Administrator always gets ALL_PERMISSIONS regardless of server value
            val finalPerms = if (roleName == Permissions.ROLE_SYSTEM_ADMIN)
                Permissions.ALL_PERMISSIONS else perms

            val existing = db.roleDao().getById("${sc}_$roleName")
            if (existing != null) {
                db.roleDao().upsert(existing.copy(permissions = finalPerms))
            } else {
                val companyName = db.companyDao().getByName(sc)?.originalName ?: sc
                db.roleDao().upsert(
                    RoleEntity(
                        id                   = "${sc}_$roleName",
                        name                 = roleName,
                        sanitizedCompanyName = sc,
                        companyName          = companyName,
                        isCustom             = roleName !in Permissions.ALL_ROLES,
                        permissions          = finalPerms
                    )
                )
            }
        }
        Log.d(tag, "refreshRoleDefinitions: updated ${items.length()} role templates for $sc")
    }

    /**
     * One-time bootstrap: seeds `role_definitions` from local cache or Permissions.forRole() defaults.
     * Only permitted use of Permissions.forRole() — used to populate the server on first run.
     */
    private suspend fun seedRoleDefinitions(sc: String, token: String) {
        val roles   = db.roleDao().getByCompany(sc)
        val toSeed  = if (roles.isNotEmpty()) roles.map { it.name }
                      else Permissions.ALL_ROLES

        var seeded = 0
        for (roleName in toSeed) {
            val cached = roles.find { it.name == roleName }
            @Suppress("DEPRECATION")
            val perms  = when {
                roleName == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                cached?.permissions?.isNotEmpty() == true -> cached.permissions
                else -> Permissions.forRole(roleName)   // one-time bootstrap only
            }
            val payload = JSONObject().apply {
                put("sanitizedCompanyName", sc)
                put("roleName",   roleName)
                put("permissions", JSONArray().also { a -> perms.forEach { a.put(it) } })
                put("isBuiltIn",  roleName in Permissions.ALL_ROLES)
            }.toString()
            try {
                pbPost(
                    "${AppConfig.BASE_URL}/api/collections/role_definitions/records",
                    token, payload
                )
                seeded++
            } catch (e: Exception) {
                Log.w(tag, "seedRoleDefinitions: failed to seed $roleName: ${e.message}")
            }
        }
        Log.d(tag, "seedRoleDefinitions: seeded $seeded roles for $sc")
        if (seeded > 0) refreshRoleDefinitions(sc, token)
    }

    private suspend fun refreshDepartments(sc: String, token: String) {
        val res     = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sc')&perPage=1",
            token
        )
        val item    = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
        val company = item?.optString("originalName") ?: sc

        // Read from companies_metadata
        val arr = try {
            JSONArray(item?.optString("departments", "[]") ?: "[]")
        } catch (_: Exception) { JSONArray() }
        val serverDepts = (0 until arr.length())
            .map { arr.optString(it) }
            .filter { it.isNotBlank() }

        // Always derive from actual cached users as well
        val usersInCompany = db.userDao().getByCompany(sc)
        val deptsFromUsers = usersInCompany
            .filter { it.department.isNotBlank() }
            .map { it.department to it.sanitizedDepartment }
            .distinctBy { it.second }

        // Merge server + user-derived, distinct by sanitized name
        val serverDeptPairs = serverDepts.map { name -> name to StringUtils.sanitize(name) }
        val allDeptPairs    = (serverDeptPairs + deptsFromUsers).distinctBy { it.second }

        if (allDeptPairs.isEmpty()) {
            Log.w(tag, "refreshDepartments: no departments found for $sc — skipping")
            return
        }

        val depts = allDeptPairs.map { (name, sd) ->
            val usersInDept = usersInCompany.filter { it.sanitizedDepartment == sd }
            DepartmentEntity(
                id                   = "${sc}_$sd",
                name                 = name,
                sanitizedName        = sd,
                companyName          = company,
                sanitizedCompanyName = sc,
                userCount            = usersInDept.size,
                activeUsers          = usersInDept.count { it.isActive }
            )
        }

        db.deptDao().clearCompany(sc)
        db.deptDao().upsertAll(depts)
        Log.d(tag, "refreshDepartments: cached ${depts.size} depts for $sc " +
                "(${serverDepts.size} from server, ${deptsFromUsers.size} from users)")

        // Self-heal: patch companies_metadata if server had empty depts but users have some
        if (serverDepts.isEmpty() && deptsFromUsers.isNotEmpty() && item != null) {
            try {
                val cId      = item.optString("id")
                val deptNames = allDeptPairs.map { it.first }
                val deptsArr  = JSONArray().also { a -> deptNames.forEach { a.put(it) } }
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId",
                    token,
                    JSONObject().apply { put("departments", deptsArr) }.toString()
                )
                Log.d(tag, "refreshDepartments: self-healed companies_metadata.departments")
            } catch (e: Exception) {
                Log.w(tag, "refreshDepartments: self-heal patch failed: ${e.message}")
            }
        }
    }

    private suspend fun refreshCompanies(token: String) {
        try {
            val res   = pbGet(
                "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=200",
                token
            )
            val items = JSONObject(res).optJSONArray("items") ?: return
            val companies = (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val o     = items.getJSONObject(i)
                    val roles = try {
                        val a = JSONArray(o.optString("availableRoles", "[]"))
                        (0 until a.length()).map { a.optString(it) }
                    } catch (_: Exception) { emptyList() }
                    val depts = try {
                        val a = JSONArray(o.optString("departments", "[]"))
                        (0 until a.length()).map { a.optString(it) }
                    } catch (_: Exception) { emptyList() }
                    CompanyEntity(
                        sanitizedName  = o.optString("sanitizedName"),
                        originalName   = o.optString("originalName"),
                        totalUsers     = o.optInt("totalUsers", 0),
                        activeUsers    = o.optInt("activeUsers", 0),
                        availableRoles = roles,
                        departments    = depts
                    )
                }.getOrNull()
            }
            db.companyDao().upsertAll(companies)
            Log.d(tag, "refreshCompanies: cached ${companies.size} companies")
        } catch (e: Exception) {
            Log.e(tag, "refreshCompanies: ${e.message}")
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun safeObj(raw: String): JSONObject =
        try { if (raw.startsWith("{")) JSONObject(raw) else JSONObject() }
        catch (_: Exception) { JSONObject() }

    suspend fun pbGet(url: String, token: String): String = withContext(Dispatchers.IO) {
        val res  = http.newCall(
            Request.Builder().url(url).get()
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute()
        val body = res.body?.string() ?: "{}"
        res.close()
        body
    }

    suspend fun pbPost(url: String, token: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res  = http.newCall(
                Request.Builder().url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val rb   = res.body?.string() ?: "{}"
            val code = res.code
            res.close()
            if (!res.isSuccessful) error("POST HTTP $code: $rb")
            rb
        }

    suspend fun pbPatch(url: String, token: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res  = http.newCall(
                Request.Builder().url(url)
                    .patch(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val rb   = res.body?.string() ?: "{}"
            val code = res.code
            res.close()
            if (!res.isSuccessful) error("PATCH HTTP $code: $rb")
            rb
        }

    suspend fun pbDelete(url: String, token: String) = withContext(Dispatchers.IO) {
        http.newCall(
            Request.Builder().url(url).delete()
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute().close()
    }
}