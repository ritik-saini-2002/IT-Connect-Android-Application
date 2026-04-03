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
import com.example.ritik_2.data.model.Permissions          // ✅ correct import
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.theme.ITConnectTheme             // ✅ correct theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    @Inject lateinit var authRepository: AuthRepository     // ✅ inject AuthRepository
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

    private suspend fun loadAdminCompany() {
        try {
            val session = authRepository.getSession() ?: run {   // ✅ use authRepository
                errorMsg.value = "Session not found"; return
            }
            val profile = dataSource.getUserProfile(session.userId).getOrNull() ?: run {
                errorMsg.value = "Could not load admin profile"; return
            }
            if (profile.role !in setOf("Administrator", "Manager")) {
                Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show()
                finish(); return
            }
            adminCompany.value = profile.companyName
        } catch (e: Exception) {
            errorMsg.value = e.message
        }
    }

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

        lifecycleScope.launch {
            try {
                val adminToken = getAdminToken()            // ✅ local helper, not dataSource
                val company    = adminCompany.value
                val sc         = sanitize(company)
                val sd         = sanitize(department)
                val permsJson  = Json.encodeToString(Permissions.forRole(role))

                // 1. Create auth record
                val createBody = JSONObject().apply {
                    put("email",           email)
                    put("password",        password)
                    put("passwordConfirm", password)
                    put("name",            name)
                    put("emailVisibility", true)
                }.toString().toRequestBody("application/json".toMediaType())

                val createReq = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/users/records")
                    .post(createBody)
                    .addHeader("Authorization", "Bearer $adminToken")
                    .build()
                val createRes  = http.newCall(createReq).execute()
                val createJson = createRes.body?.string() ?: ""
                createRes.close()

                if (!createRes.isSuccessful) {
                    val msg = runCatching { JSONObject(createJson).optString("message", "Unknown") }
                        .getOrDefault("Unknown error")
                    throw Exception("Account creation failed: $msg")
                }

                val userId       = JSONObject(createJson).optString("id")
                val documentPath = "users/$sc/$sd/$role/$userId"

                val profileJson = JSONObject().apply {
                    put("imageUrl",    "")
                    put("phoneNumber", "")
                    put("address",     "")
                    put("employeeId",  "")
                    put("reportingTo", "")
                    put("salary",      0)
                    put("emergencyContactName",     "")
                    put("emergencyContactPhone",    "")
                    put("emergencyContactRelation", "")
                }.toString()

                val workJson = JSONObject().apply {
                    put("experience",           0)
                    put("completedProjects",    0)
                    put("activeProjects",       0)
                    put("pendingTasks",         0)
                    put("completedTasks",       0)
                    put("totalWorkingHours",    0)
                    put("avgPerformanceRating", 0.0)
                }.toString()

                val issuesJson = JSONObject().apply {
                    put("totalComplaints",    0)
                    put("resolvedComplaints", 0)
                    put("pendingComplaints",  0)
                }.toString()

                // 2. PATCH user record
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
                        put("documentPath",         documentPath)
                        put("permissions",          permsJson)
                        put("profile",              profileJson)
                        put("workStats",            workJson)
                        put("issues",               issuesJson)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                // 3. user_access_control record
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
                        put("documentPath",         documentPath)
                        put("needsProfileCompletion", true)
                    }.toString()
                )

                // 4. user_search_index record
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
                        put("documentPath",         documentPath)
                    }.toString()
                )

                upsertCompanyMeta(sc, company, role, department, sd, adminToken)

                Log.d(TAG, "Admin created user $userId ✅")
                Toast.makeText(this@CreateUserActivity,
                    "✓ $name added. They'll complete their profile on first login.",
                    Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "createUser failed: ${e.message}", e)
                errorMsg.value = e.message
                Toast.makeText(this@CreateUserActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isCreating.value = false
            }
        }
    }

    private fun upsertCompanyMeta(sc: String, company: String, role: String,
                                  dept: String, sd: String, token: String) {
        lifecycleScope.launch {
            try {
                val cRes = pbGet("${AppConfig.BASE_URL}/api/collections/companies_metadata/records?filter=(sanitizedName='$sc')", token)
                val cId  = JSONObject(cRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                val cPayload = JSONObject().apply {
                    put("sanitizedName", sc); put("originalName", company)
                    put("lastUpdated", System.currentTimeMillis())
                }.toString()
                if (cId.isNullOrEmpty()) pbPost("${AppConfig.BASE_URL}/api/collections/companies_metadata/records", token, cPayload)
                else                     pbPatch("${AppConfig.BASE_URL}/api/collections/companies_metadata/records/$cId", token, cPayload)

                val dRes = pbGet("${AppConfig.BASE_URL}/api/collections/departments_metadata/records?filter=(sanitizedCompanyName='$sc'&&sanitizedName='$sd')", token)
                val dId  = JSONObject(dRes).optJSONArray("items")?.optJSONObject(0)?.optString("id")
                val dPayload = JSONObject().apply {
                    put("departmentName", dept); put("sanitizedName", sd)
                    put("sanitizedCompanyName", sc); put("lastUpdated", System.currentTimeMillis())
                }.toString()
                if (dId.isNullOrEmpty()) pbPost("${AppConfig.BASE_URL}/api/collections/departments_metadata/records", token, dPayload)
                else                     pbPatch("${AppConfig.BASE_URL}/api/collections/departments_metadata/records/$dId", token, dPayload)
            } catch (e: Exception) {
                Log.w(TAG, "upsertCompanyMeta non-fatal: ${e.message}")
            }
        }
    }

    // ── Admin token ───────────────────────────────────────────────────────────

    private fun getAdminToken(): String {
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
                val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful; res.close()
                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) return t
                }
            } catch (_: Exception) {}
        }
        error("Could not obtain admin token")
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun pbPost(url: String, token: String, body: String) {
        http.newCall(Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token").build()).execute().close()
    }

    private fun pbPatch(url: String, token: String, body: String) {
        http.newCall(Request.Builder().url(url)
            .patch(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token").build()).execute().close()
    }

    private fun pbGet(url: String, token: String): String {
        val res = http.newCall(Request.Builder().url(url).get()
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val b = res.body?.string() ?: "{}"; res.close(); return b
    }

    private fun sanitize(input: String) =
        input.lowercase().replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "_").take(50)

    companion object { private const val TAG = "CreateUserActivity" }
}