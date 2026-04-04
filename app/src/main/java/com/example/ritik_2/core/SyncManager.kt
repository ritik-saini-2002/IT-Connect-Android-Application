package com.example.ritik_2.core

import android.util.Log
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

    private var adminToken     = ""
    private var tokenFetchedAt = 0L
    private val tokenTtl       = 10 * 60 * 1000L

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

    // ── Full company refresh ──────────────────────────────────────────────────

    suspend fun refreshCompanyData(sanitizedCompany: String) = withContext(Dispatchers.IO) {
        if (!monitor.serverReachable.value) {
            Log.d(tag, "Offline — skipping refresh")
            return@withContext
        }
        try {
            val token = getAdminToken()
            refreshUsers(sanitizedCompany, token)
            refreshRoles(sanitizedCompany, token)
            refreshDepartments(sanitizedCompany, token)
            refreshCompanies(token)          // ← always refresh all companies
            Log.d(tag, "refreshCompanyData ✅ $sanitizedCompany")
        } catch (e: Exception) {
            Log.e(tag, "refreshCompanyData failed: ${e.message}")
        }
    }

    suspend fun refreshAllCollections() = withContext(Dispatchers.IO) {
        if (!monitor.serverReachable.value) return@withContext
        try {
            val token = getAdminToken()
            val res   = pbGet("${AppConfig.BASE_URL}/api/collections?perPage=200", token)
            val items = JSONObject(res).optJSONArray("items") ?: return@withContext
            val cols  = (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                CollectionEntity(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    type = o.optString("type"),
                    listRule = o.optString("listRule"),
                    viewRule = o.optString("viewRule"),
                    createRule = o.optString("createRule"),
                    updateRule = o.optString("updateRule"),
                    deleteRule = o.optString("deleteRule"),
                    fields = o.optJSONArray("fields")?.toString()
                        ?: o.optJSONArray("schema")?.toString() ?: "[]",
                    indexes = o.optJSONArray("indexes")?.toString() ?: "[]"
                )
            }
            db.collectionDao().clear()
            db.collectionDao().upsertAll(cols)
            // Also refresh companies while we're at it
            refreshCompanies(token)
        } catch (e: Exception) {
            Log.e(tag, "refreshAllCollections: ${e.message}")
        }
    }

    // ── Flush pending queue ───────────────────────────────────────────────────

    suspend fun flushQueue() = withContext(Dispatchers.IO) {
        val pending = db.syncQueueDao().getAll()
        if (pending.isEmpty()) return@withContext
        val token = try { getAdminToken() } catch (e: Exception) {
            Log.e(tag, "flushQueue: no admin token"); return@withContext
        }
        pending.forEach { op ->
            try {
                when (op.operationType) {
                    "CREATE"      -> pbPost(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records",
                        token, op.payload)
                    "UPDATE"      -> pbPatch(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records/${op.recordId}",
                        token, op.payload)
                    "DELETE"      -> pbDelete(
                        "${AppConfig.BASE_URL}/api/collections/${op.collection}/records/${op.recordId}",
                        token)
                    "ROLE_CHANGE" -> {
                        val j = JSONObject(op.payload)
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/users/records/${op.recordId}",
                            token,
                            JSONObject().apply {
                                put("role",        j.optString("role"))
                                put("permissions", j.optString("permissions"))
                            }.toString())
                    }
                    "MOVE_USER"   -> {
                        val j = JSONObject(op.payload)
                        pbPatch(
                            "${AppConfig.BASE_URL}/api/collections/users/records/${op.recordId}",
                            token,
                            JSONObject().apply {
                                put("role",                j.optString("role"))
                                put("department",          j.optString("department"))
                                put("sanitizedDepartment", j.optString("sanitizedDepartment"))
                                put("documentPath",        j.optString("documentPath"))
                            }.toString())
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
                operationType = type, collection = collection,
                recordId = recordId, payload = payload
            )
        )
        if (monitor.serverReachable.value) flushQueue()
    }

    suspend fun pendingCount() = db.syncQueueDao().pendingCount()

    // ── Admin token ───────────────────────────────────────────────────────────

    suspend fun getAdminToken(): String = tokenMutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (adminToken.isNotBlank() && (now - tokenFetchedAt) < tokenTtl)
                return@withContext adminToken
            listOf(
                "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
                "${AppConfig.BASE_URL}/api/admins/auth-with-password"
            ).forEach { url ->
                try {
                    val body = JSONObject().apply {
                        put("identity", AppConfig.ADMIN_EMAIL)
                        put("password", AppConfig.ADMIN_PASS)
                    }.toString().toRequestBody("application/json".toMediaType())
                    val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                    val resBody = res.body?.string() ?: ""; val ok = res.isSuccessful; res.close()
                    if (ok) {
                        val t = JSONObject(resBody).optString("token")
                        if (t.isNotEmpty()) {
                            adminToken = t; tokenFetchedAt = now
                            return@withContext t
                        }
                    }
                } catch (_: Exception) {}
            }
            error("No admin token available")
        }
    }

    // ── Private refresh helpers ───────────────────────────────────────────────

    private suspend fun refreshUsers(sc: String, token: String) {
        var page = 1
        val all  = mutableListOf<UserEntity>()
        while (true) {
            val res   = pbGet(
                "${AppConfig.BASE_URL}/api/collections/users/records" +
                        "?filter=(sanitizedCompanyName='$sc')&perPage=200&page=$page", token)
            val json  = JSONObject(res)
            val items = json.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val o       = items.getJSONObject(i)
                val profile = safeObj(o.optString("profile"))
                val work    = safeObj(o.optString("workStats"))
                val issues  = safeObj(o.optString("issues"))
                all.add(UserEntity(
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
                ))
            }
            val totalPages = json.optInt("totalPages", 1)
            if (page >= totalPages) break
            page++
        }
        db.userDao().upsertAll(all)
        Log.d(tag, "refreshUsers: cached ${all.size} users for $sc")
    }

    private suspend fun refreshRoles(sc: String, token: String) {
        val res  = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sc')&perPage=1", token)
        val item = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
        val arr  = try { JSONArray(item?.optString("availableRoles", "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }
        val roles = (0 until arr.length()).map { i ->
            val name = arr.optString(i)
            RoleEntity(
                id = "${sc}_$name",
                name = name,
                sanitizedCompanyName = sc,
                companyName = item?.optString("originalName") ?: sc,
                isCustom = false,
                userCount = db.userDao().getByCompany(sc).count { it.role == name }
            )
        }
        db.roleDao().clearCompany(sc)
        db.roleDao().upsertAll(roles)
    }

    private suspend fun refreshDepartments(sc: String, token: String) {
        val res     = pbGet(
            "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                    "?filter=(sanitizedName='$sc')&perPage=1", token)
        val item    = JSONObject(res).optJSONArray("items")?.optJSONObject(0)
        val arr     = try { JSONArray(item?.optString("departments", "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }
        val company = item?.optString("originalName") ?: sc
        val depts   = (0 until arr.length()).map { i ->
            val name  = arr.optString(i)
            val sd    = StringUtils.sanitize(name)
            val users = db.userDao().getByDepartment(sc, sd)
            DepartmentEntity(
                id = "${sc}_$sd",
                name = name,
                sanitizedName = sd,
                companyName = company,
                sanitizedCompanyName = sc,
                userCount = users.size,
                activeUsers = users.count { it.isActive }
            )
        }
        db.deptDao().clearCompany(sc)
        db.deptDao().upsertAll(depts)
    }

    private suspend fun refreshCompanies(token: String) {
        try {
            val res   = pbGet(
                "${AppConfig.BASE_URL}/api/collections/companies_metadata/records?perPage=200",
                token)
            val items = JSONObject(res).optJSONArray("items") ?: return
            val companies = (0 until items.length()).mapNotNull { i ->
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
                    CompanyEntity(
                        sanitizedName = o.optString("sanitizedName"),
                        originalName = o.optString("originalName"),
                        totalUsers = o.optInt("totalUsers", 0),
                        activeUsers = o.optInt("activeUsers", 0),
                        availableRoles = roles,
                        departments = depts
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
        val res  = http.newCall(Request.Builder().url(url).get()
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val body = res.body?.string() ?: "{}"; res.close(); body
    }

    suspend fun pbPost(url: String, token: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res  = http.newCall(Request.Builder().url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token").build()).execute()
            val rb   = res.body?.string() ?: "{}"; val code = res.code; res.close()
            if (!res.isSuccessful) error("POST HTTP $code: $rb")
            rb
        }

    suspend fun pbPatch(url: String, token: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res  = http.newCall(Request.Builder().url(url)
                .patch(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token").build()).execute()
            val rb   = res.body?.string() ?: "{}"; val code = res.code; res.close()
            if (!res.isSuccessful) error("PATCH HTTP $code: $rb")
            rb
        }

    suspend fun pbDelete(url: String, token: String) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).delete()
            .addHeader("Authorization", "Bearer $token").build()).execute().close()
    }
}