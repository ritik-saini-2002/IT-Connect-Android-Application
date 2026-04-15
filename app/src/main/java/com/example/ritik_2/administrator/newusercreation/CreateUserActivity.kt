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
import com.example.ritik_2.localdatabase.AppDatabase
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
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var http          : OkHttpClient
    @Inject lateinit var db            : AppDatabase
    @Inject lateinit var adminTokenProvider: com.example.ritik_2.core.AdminTokenProvider

    private val isCreating     = mutableStateOf(false)
    private val adminCompany   = mutableStateOf("")
    private val errorMsg       = mutableStateOf<String?>(null)
    private val availableRoles = mutableStateOf<List<String>>(Permissions.ALL_ROLES)
    private val availableDepts = mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITConnectTheme {
                CreateUserScreen(
                    isCreating     = isCreating.value,
                    companyName    = adminCompany.value,
                    error          = errorMsg.value,
                    availableRoles = availableRoles.value,
                    availableDepts = availableDepts.value,
                    onCreateUser   = ::createUser
                )
            }
        }

        lifecycleScope.launch { loadAdminData() }
    }

    private suspend fun loadAdminData() {
        try {
            val session = authRepository.getSession() ?: run {
                errorMsg.value = "Session not found. Please log in again."; return
            }
            dataSource.restoreSession(session.token)
            val profile = dataSource.getUserProfile(session.userId).getOrNull() ?: run {
                errorMsg.value = "Could not load admin profile"; return
            }
            val sessionPerms = authRepository.getSession()?.permissions ?: emptyList()
            val canCreate = profile.role == Permissions.ROLE_SYSTEM_ADMIN
                || "create_user"        in sessionPerms
                || "access_admin_panel" in sessionPerms
            if (!canCreate) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateUserActivity, "Access denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }
            adminCompany.value = profile.companyName

            // Load roles from local Room cache (no hardcoded fallback — sync populates this)
            val roles = withContext(Dispatchers.IO) {
                db.roleDao().getByCompany(profile.sanitizedCompany).map { it.name }
            }
            availableRoles.value = roles.ifEmpty { Permissions.ALL_ROLES }

            // Load departments from local Room cache
            val depts = withContext(Dispatchers.IO) {
                db.deptDao().getByCompany(profile.sanitizedCompany).map { it.name }
            }
            availableDepts.value = depts.ifEmpty {
                listOf("Technical", "HR", "Administrative", "IT Support", "Finance", "Operations", "General")
            }

            Log.d(TAG, "Admin data loaded: company=${profile.companyName} roles=$roles depts=$depts")
        } catch (e: Exception) {
            Log.e(TAG, "loadAdminData failed: ${e.message}", e)
            errorMsg.value = "Failed to load profile: ${e.message}"
        }
    }

    fun createUser(
        name       : String, email: String, role: String,
        department : String, designation: String, password: String
    ) {
        if (isCreating.value) return
        isCreating.value = true; errorMsg.value = null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val adminToken   = getAdminToken()
                val company      = adminCompany.value
                val sc           = StringUtils.sanitize(company)
                val sd           = StringUtils.sanitize(department)
                val roleEntity   = db.roleDao().getById("${sc}_$role")
                val perms        = when {
                    role == Permissions.ROLE_SYSTEM_ADMIN -> Permissions.ALL_PERMISSIONS
                    roleEntity != null && roleEntity.permissions.isNotEmpty() -> roleEntity.permissions
                    else -> listOf("view_profile")
                }
                val permsJson    = Json.encodeToString(perms)
                val documentPath = "users/$sc/$sd/$role"

                val createBody = JSONObject().apply {
                    put("email", email); put("password", password)
                    put("passwordConfirm", password); put("name", name)
                    put("emailVisibility", true)
                }.toString().toRequestBody("application/json".toMediaType())

                val createRes  = http.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/collections/users/records")
                        .post(createBody).addHeader("Authorization", "Bearer $adminToken").build()
                ).execute()
                val createJson = createRes.body?.string() ?: ""
                val createCode = createRes.code; createRes.close()
                if (!createRes.isSuccessful)
                    error("Account creation failed: ${parseErrorMessage(createJson, createCode)}")

                val userId      = JSONObject(createJson).optString("id").ifEmpty { error("No userId") }
                val fullDocPath = "$documentPath/$userId"

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
                    put("totalComplaints", 0); put("resolvedComplaints", 0); put("pendingComplaints", 0)
                }.toString()

                pbPatch("${AppConfig.BASE_URL}/api/collections/users/records/$userId", adminToken,
                    JSONObject().apply {
                        put("userId", userId); put("role", role)
                        put("companyName", company); put("sanitizedCompanyName", sc)
                        put("department", department); put("sanitizedDepartment", sd)
                        put("designation", designation); put("isActive", true)
                        put("documentPath", fullDocPath); put("permissions", permsJson)
                        put("profile", profileJson); put("workStats", workJson)
                        put("issues", issuesJson); put("needsProfileCompletion", true)
                    }.toString()
                )

                pbPost("${AppConfig.BASE_URL}/api/collections/user_access_control/records", adminToken,
                    JSONObject().apply {
                        put("userId", userId); put("name", name); put("email", email)
                        put("companyName", company); put("sanitizedCompanyName", sc)
                        put("department", department); put("sanitizedDepartment", sd)
                        put("role", role); put("designation", designation)
                        put("permissions", permsJson); put("isActive", true)
                        put("documentPath", fullDocPath); put("needsProfileCompletion", true)
                    }.toString()
                )

                val searchTerms = Json.encodeToString(
                    listOf(name, email, company, department, role, designation)
                        .map { it.lowercase() }.filter { it.isNotEmpty() })
                pbPost("${AppConfig.BASE_URL}/api/collections/user_search_index/records", adminToken,
                    JSONObject().apply {
                        put("userId", userId); put("name", name.lowercase()); put("email", email.lowercase())
                        put("companyName", company); put("sanitizedCompanyName", sc)
                        put("department", department); put("sanitizedDepartment", sd)
                        put("role", role); put("designation", designation)
                        put("isActive", true); put("searchTerms", searchTerms)
                        put("documentPath", fullDocPath)
                    }.toString()
                )

                upsertCompanyMeta(sc, company, role, department, adminToken)

                // Cache new user locally
                db.userDao().upsert(
                    com.example.ritik_2.localdatabase.UserEntity(
                        id                   = userId,
                        name                 = name,
                        email                = email,
                        role                 = role,
                        companyName          = company,
                        sanitizedCompanyName = sc,
                        department           = department,
                        sanitizedDepartment  = sd,
                        designation          = designation,
                        isActive             = true,
                        documentPath         = fullDocPath,
                        needsProfileCompletion = true
                    )
                )

                Log.d(TAG, "Admin created user $userId ✅")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateUserActivity, "✓ $name added successfully", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "createUser failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorMsg.value = e.message
                    Toast.makeText(this@CreateUserActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isCreating.value = false }
            }
        }
    }

    private fun upsertCompanyMeta(sc: String, company: String, role: String,
                                  dept: String, token: String) {
        try {
            val url    = "${AppConfig.BASE_URL}/api/collections/companies_metadata/records"
            val getRes = pbGet("$url?filter=(sanitizedName='$sc')", token)
            val item   = JSONObject(getRes).optJSONArray("items")?.optJSONObject(0)
            val cId    = item?.optString("id")
            val payload = JSONObject().apply {
                put("sanitizedName", sc); put("originalName", company)
                put("totalUsers",  (item?.optInt("totalUsers",  0) ?: 0) + 1)
                put("activeUsers", (item?.optInt("activeUsers", 0) ?: 0) + 1)
                put("lastUpdated", System.currentTimeMillis())
            }.toString()
            if (cId.isNullOrEmpty()) pbPost(url, token, payload)
            else pbPatch("$url/$cId", token, payload)
        } catch (e: Exception) {
            Log.w(TAG, "upsertCompanyMeta: ${e.message}")
        }
    }

    private fun getAdminToken(): String = adminTokenProvider.getAdminTokenSync()

    private fun pbPost(url: String, token: String, body: String) {
        val res = http.newCall(Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val resBody = res.body?.string() ?: ""; val code = res.code; res.close()
        if (!res.isSuccessful) error("POST HTTP $code: ${parseErrorMessage(resBody, code)}")
    }

    private fun pbPatch(url: String, token: String, body: String) {
        val res = http.newCall(Request.Builder().url(url)
            .patch(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val resBody = res.body?.string() ?: ""; val code = res.code; res.close()
        if (!res.isSuccessful) error("PATCH HTTP $code: ${parseErrorMessage(resBody, code)}")
    }

    private fun pbGet(url: String, token: String): String {
        val res = http.newCall(Request.Builder().url(url).get()
            .addHeader("Authorization", "Bearer $token").build()).execute()
        val body = res.body?.string() ?: "{}"; res.close(); return body
    }

    private fun parseErrorMessage(json: String, code: Int): String = try {
        val obj = JSONObject(json); val d = obj.optJSONObject("data")
        if (d != null && d.length() > 0)
            d.keys().asSequence().joinToString(", ") {
                "$it: ${d.optJSONObject(it)?.optString("message") ?: "invalid"}"
            }
        else obj.optString("message", "HTTP $code")
    } catch (_: Exception) { "HTTP $code" }

    companion object { private const val TAG = "CreateUserActivity" }
}