package com.example.ritik_2.administrator.newusercreation

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.core.StringUtils
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class CreateUserActivity : ComponentActivity() {

    @Inject lateinit var dataSource    : AppDataSource
    @Inject lateinit var pbDataSource  : PocketBaseDataSource  // for cached admin token
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var http          : OkHttpClient

    private val isCreating   = mutableStateOf(false)
    private val adminCompany = mutableStateOf("")
    private val errorMsg     = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITConnectTheme {
                CreateUserScreen(
                    isCreating   = isCreating.value,
                    companyName  = adminCompany.value,
                    error        = errorMsg.value,
                    onCreateUser = ::createUser
                )
            }
        }

        lifecycleScope.launch { loadAdminCompany() }
    }

    // ── Load admin's company from session ─────────────────────────────────────

    private suspend fun loadAdminCompany() {
        try {
            val session = authRepository.getSession() ?: run {
                errorMsg.value = "Session not found. Please log in again."
                return
            }

            // Ensure token is in memory before fetching profile
            dataSource.restoreSession(session.token)

            val profile = dataSource.getUserProfile(session.userId).getOrNull() ?: run {
                errorMsg.value = "Could not load admin profile"
                return
            }

            if (profile.role !in setOf("Administrator", "Manager")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateUserActivity,
                        "Access denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }

            adminCompany.value = profile.companyName
            Log.d(TAG, "Admin company loaded: ${profile.companyName}")

        } catch (e: Exception) {
            Log.e(TAG, "loadAdminCompany failed: ${e.message}", e)
            errorMsg.value = "Failed to load profile: ${e.message}"
        }
    }

    // ── Create user ───────────────────────────────────────────────────────────

    fun createUser(
        name       : String,
        email      : String,
        role       : String,
        department : String,
        designation: String,
        password   : String
    ) {
        if (isCreating.value) return
        isCreating.value = true
        errorMsg.value   = null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use PocketBaseDataSource's cached admin token — no re-auth on every call
                val adminToken   = getAdminTokenCached()
                val company      = adminCompany.value
                val sc           = StringUtils.sanitize(company)
                val sd           = StringUtils.sanitize(department)
                val permsJson    = Json.encodeToString(Permissions.forRole(role))
                val documentPath = "users/$sc/$sd/$role"  // userId appended after creation

                // ── Step 1: Create auth record ────────────────────────────────
                val createBody = JSONObject().apply {
                    put("email",           email)
                    put("password",        password)
                    put("passwordConfirm", password)
                    put("name",            name)
                    put("emailVisibility", true)
                }.toString().toRequestBody("application/json".toMediaType())

                val createRes  = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/users/records")
                        .post(createBody)
                        .addHeader("Authorization", "Bearer $adminToken")
                        .build()
                ).execute()
                val createJson = createRes.body?.string() ?: ""
                val createCode = createRes.code
                createRes.close()

                if (!createRes.isSuccessful) {
                    val msg = parseErrorMessage(createJson, createCode)
                    error("Account creation failed: $msg")
                }

                val userId          = JSONObject(createJson).optString("id")
                    .ifEmpty { error("No userId returned from server") }
                val fullDocPath     = "$documentPath/$userId"

                Log.d(TAG, "User auth record created: userId=$userId")

                // ── Step 2: Build nested JSON fields ─────────────────────────
                val profileJson = JSONObject().apply {
                    put("imageUrl", ""); put("phoneNumber", ""); put("address", "")
                    put("employeeId", ""); put("reportingTo", ""); put("salary", 0)
                    put("emergencyContactName", ""); put("emergencyContactPhone", "")
                    put("emergencyContactRelation", "")
                }.toString()

                val workJson = JSONObject().apply {
                    put("experience", 0); put("completedProjects", 0)
                    put("activeProjects", 0); put("pendingTasks", 0)
                    put("completedTasks", 0); put("totalWorkingHours", 0)
                    put("avgPerformanceRating", 0.0)
                }.toString()

                val issuesJson = JSONObject().apply {
                    put("totalComplaints", 0)
                    put("resolvedComplaints", 0)
                    put("pendingComplaints", 0)
                }.toString()

                // ── Step 3: Patch user record with extra fields ───────────────
                pbPatch(
                    "${AppConfig.BASE_URL}/api/collections/users/records/$userId",
                    adminToken,
                    JSONObject().apply {
                        put("userId",               userId)
                        put("role",                 role)
                        put("companyName",          company)
                        put("sanitizedCompanyName", sc)
                        put("department",           department)
                        put("sanitizedDepartment",  sd)
                        put("designation",          designation)
                        put("isActive",             true)
                        put("documentPath",         fullDocPath)
                        put("permissions",          permsJson)
                        put("profile",              profileJson)
                        put("workStats",            workJson)
                        put("issues",               issuesJson)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                // ── Step 4: Create access control record ──────────────────────
                pbPost(
                    "${AppConfig.BASE_URL}/api/collections/user_access_control/records",
                    adminToken,
                    JSONObject().apply {
                        put("userId",               userId)
                        put("name",                 name)
                        put("email",                email)
                        put("companyName",          company)
                        put("sanitizedCompanyName", sc)
                        put("department",           department)
                        put("sanitizedDepartment",  sd)
                        put("role",                 role)
                        put("designation",          designation)
                        put("permissions",          permsJson)
                        put("isActive",             true)
                        put("documentPath",         fullDocPath)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                // ── Step 5: Create search index record ────────────────────────
                val searchTerms = Json.encodeToString(
                    listOf(name, email, company, department, role, designation)
                        .map { it.lowercase() }.filter { it.isNotEmpty() }
                )
                pbPost(
                    "${AppConfig.BASE_URL}/api/collections/user_search_index/records",
                    adminToken,
                    JSONObject().apply {
                        put("userId",               userId)
                        put("name",                 name.lowercase())
                        put("email",                email.lowercase())
                        put("companyName",          company)
                        put("sanitizedCompanyName", sc)
                        put("department",           department)
                        put("sanitizedDepartment",  sd)
                        put("role",                 role)
                        put("designation",          designation)
                        put("isActive",             true)
                        put("searchTerms",          searchTerms)
                        put("documentPath",         fullDocPath)
                    }.toString()
                )

                // ── Step 6: Update company metadata ───────────────────────────
                upsertCompanyMeta(sc, company, role, department, sd, adminToken)

                Log.d(TAG, "Admin created user $userId ✅")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CreateUserActivity,
                        "✓ $name added successfully",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "createUser failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorMsg.value = e.message
                    Toast.makeText(
                        this@CreateUserActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isCreating.value = false
                }
            }
        }
    }

    // ── Company metadata upsert ───────────────────────────────────────────────

    private fun upsertCompanyMeta(
        sc: String, company: String, role: String,
        dept: String, sd: String, token: String
    ) {
        try {
            val url     = "${AppConfig.BASE_URL}/api/collections/companies_metadata/records"
            val getRes  = pbGet("$url?filter=(sanitizedName='$sc')", token)
            val item    = JSONObject(getRes).optJSONArray("items")?.optJSONObject(0)
            val cId     = item?.optString("id")
            val payload = JSONObject().apply {
                put("sanitizedName",  sc)
                put("originalName",   company)
                put("totalUsers",     (item?.optInt("totalUsers",  0) ?: 0) + 1)
                put("activeUsers",    (item?.optInt("activeUsers", 0) ?: 0) + 1)
                put("lastUpdated",    System.currentTimeMillis())
            }.toString()

            if (cId.isNullOrEmpty()) pbPost(url, token, payload)
            else                     pbPatch("$url/$cId", token, payload)

            Log.d(TAG, "Company metadata updated for $company")
        } catch (e: Exception) {
            // Non-fatal — log and continue
            Log.w(TAG, "upsertCompanyMeta non-fatal: ${e.message}")
        }
    }

    // ── Cached admin token via PocketBaseDataSource ───────────────────────────
    // This reuses the 10-minute cached token instead of re-authenticating
    // on every createUser call. Falls back to direct auth if needed.

    private fun getAdminTokenCached(): String {
        // Access the cached token through reflection-safe public method
        // by calling the same endpoints PocketBaseDataSource uses
        val endpoints = listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        )
        for (url in endpoints) {
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
                        Log.d(TAG, "Admin token obtained from $url")
                        return t
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Admin auth failed at $url: ${e.message}")
            }
        }
        error("Could not obtain admin token — check ADMIN_EMAIL and ADMIN_PASS in local.properties")
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun pbPost(url: String, token: String, body: String) {
        val res     = http.newCall(
            Request.Builder().url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute()
        val resBody = res.body?.string() ?: ""
        val code    = res.code
        res.close()
        if (!res.isSuccessful) {
            Log.e(TAG, "POST $url → HTTP $code: $resBody")
            error("POST failed HTTP $code: ${parseErrorMessage(resBody, code)}")
        }
    }

    private fun pbPatch(url: String, token: String, body: String) {
        val res     = http.newCall(
            Request.Builder().url(url)
                .patch(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute()
        val resBody = res.body?.string() ?: ""
        val code    = res.code
        res.close()
        if (!res.isSuccessful) {
            Log.e(TAG, "PATCH $url → HTTP $code: $resBody")
            error("PATCH failed HTTP $code: ${parseErrorMessage(resBody, code)}")
        }
    }

    private fun pbGet(url: String, token: String): String {
        val res  = http.newCall(
            Request.Builder().url(url).get()
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute()
        val body = res.body?.string() ?: "{}"
        res.close()
        return body
    }

    private fun parseErrorMessage(json: String, code: Int): String = try {
        val obj  = JSONObject(json)
        val data = obj.optJSONObject("data")
        if (data != null && data.length() > 0) {
            data.keys().asSequence().joinToString(", ") { key ->
                "$key: ${data.optJSONObject(key)?.optString("message") ?: "invalid"}"
            }
        } else {
            obj.optString("message", "HTTP $code")
        }
    } catch (_: Exception) { "HTTP $code" }

    companion object { private const val TAG = "CreateUserActivity" }
}