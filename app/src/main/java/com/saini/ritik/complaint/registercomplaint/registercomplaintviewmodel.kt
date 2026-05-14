package com.saini.ritik.complaint.registercomplaint

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AppConfig
import com.saini.ritik.core.AdminTokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

// ── Data model for attachments ────────────────────────────────────────────────
data class AttachmentItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val bytes: ByteArray? = null
) {
    val isImage get() = mimeType.startsWith("image/")
    val isPdf   get() = mimeType == "application/pdf"
}

// ── UI state ──────────────────────────────────────────────────────────────────
data class RegisterComplaintUiState(
    val title       : String = "",
    val description : String = "",
    val category    : String = "General",
    val priority    : String = "Medium",
    val department  : String = "",
    val attachments : List<AttachmentItem> = emptyList(),
    val isSubmitting: Boolean = false,
    val error       : String? = null,

    // Dropdown options (loaded dynamically)
    val categories  : List<String> = listOf("General", "Hardware", "Software", "Network", "Access", "Other"),
    val priorities  : List<String> = listOf("Low", "Medium", "High", "Critical"),
    val departments : List<String> = emptyList()
)

internal const val COL_COMPLAINTS = "complaints"

@HiltViewModel
class RegisterComplaintViewModel @Inject constructor(
    private val http              : OkHttpClient,
    private val authRepository    : AuthRepository,
    private val adminTokenProvider: AdminTokenProvider
) : ViewModel() {

    companion object { private const val TAG = "RegisterComplaintVM" }

    private val _uiState = MutableStateFlow(RegisterComplaintUiState())
    val uiState: StateFlow<RegisterComplaintUiState> = _uiState.asStateFlow()

    init { loadDepartments() }

    // ── Field change handlers ─────────────────────────────────────────────────
    fun onTitleChange(v: String)       { _uiState.value = _uiState.value.copy(title = v) }
    fun onDescriptionChange(v: String) { _uiState.value = _uiState.value.copy(description = v) }
    fun onCategoryChange(v: String)    { _uiState.value = _uiState.value.copy(category = v) }
    fun onPriorityChange(v: String)    { _uiState.value = _uiState.value.copy(priority = v) }
    fun onDepartmentChange(v: String)  { _uiState.value = _uiState.value.copy(department = v) }

    // ── Attachments ───────────────────────────────────────────────────────────
    fun addAttachments(context: Context, uris: List<Uri>, isImage: Boolean) {
        val items = uris.mapNotNull { uri ->
            try {
                val cr       = context.contentResolver
                val mimeType = cr.getType(uri) ?: if (isImage) "image/jpeg" else "application/pdf"
                val name     = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
                val bytes    = cr.openInputStream(uri)?.use { it.readBytes() }
                AttachmentItem(uri = uri, name = name, mimeType = mimeType, bytes = bytes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read $uri: ${e.message}")
                null
            }
        }
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments + items
        )
    }

    fun removeAttachment(id: String) {
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments.filter { it.id != id }
        )
    }

    // ── Load departments from company metadata ────────────────────────────────
    private fun loadDepartments() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = authRepository.getSession() ?: return@launch

                // ── Derive sanitized company name ────────────────────────────────
                // documentPath is typically "companies/<sanitizedName>/users/<userId>"
                // We want index 1. Log it so we can verify.
                val parts = session.documentPath.split("/")
                val sc = parts.getOrNull(1).orEmpty()
                Log.d(TAG, "loadDepartments: documentPath=${session.documentPath}, sc='$sc'")

                if (sc.isBlank()) {
                    Log.w(TAG, "loadDepartments: could not derive company name — aborting")
                    return@launch
                }

                val token = try { adminTokenProvider.getAdminTokenSync() }
                catch (_: Exception) { session.token }

                val url = "${AppConfig.BASE_URL}/api/collections/companies_metadata/records" +
                        "?filter=(sanitizedName='$sc')&perPage=1"
                Log.d(TAG, "loadDepartments: GET $url")

                val res  = http.newCall(
                    Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer $token")
                        .get().build()
                ).execute()
                val body = res.body?.string() ?: ""; res.close()
                Log.d(TAG, "loadDepartments: response = $body")

                val items   = JSONObject(body).optJSONArray("items")
                val company = items?.optJSONObject(0)
                val depts   = company?.optJSONArray("departments")

                if (depts != null && depts.length() > 0) {
                    val list = (0 until depts.length())
                        .map { depts.optString(it) }
                        .filter { it.isNotBlank() }
                    Log.d(TAG, "loadDepartments: loaded ${list.size} departments: $list")
                    _uiState.value = _uiState.value.copy(
                        departments = list,
                        department  = if (_uiState.value.department.isBlank()) list.first()
                        else _uiState.value.department
                    )
                } else {
                    Log.w(TAG, "loadDepartments: 'departments' field missing or empty in PocketBase record")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadDepartments failed: ${e.message}", e)
            }
        }
    }

    // ── Submit complaint ──────────────────────────────────────────────────────
    fun submitComplaint(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank()) { onError("Title is required"); return }
        if (state.description.isBlank()) { onError("Description is required"); return }

        _uiState.value = state.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                val session = authRepository.getSession() ?: run {
                    onError("Session expired"); return@launch
                }
                val token = try { adminTokenProvider.getAdminTokenSync() } catch (_: Exception) { session.token }
                val sc    = session.documentPath.split("/").getOrNull(1) ?: ""

                val ticketId = "TKT-${System.currentTimeMillis().toString(36).uppercase()}"

                val recordId = withContext(Dispatchers.IO) {
                    // Step 1: Create the complaint record (text fields)
                    val payload = JSONObject().apply {
                        put("ticketId",              ticketId)
                        put("title",                 state.title)
                        put("description",           state.description)
                        put("category",              state.category)
                        put("priority",              state.priority)
                        put("status",                "Open")
                        put("raisedBy",              session.userId)
                        put("raisedByName",          session.name)
                        put("raisedByEmail",         session.email)
                        put("raisedByRole",          session.role)
                        put("raisedByDepartment",    state.department)
                        put("assignedTo",            "")
                        put("assignedToName",        "")
                        put("companyName",           sc)
                        put("department",            state.department)
                        put("resolution",            "")
                        put("comments",              "[]")
                    }.toString()

                    val createRes = http.newCall(
                        Request.Builder()
                            .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPLAINTS/records")
                            .addHeader("Authorization", "Bearer $token")
                            .post(payload.toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute()
                    val createBody = createRes.body?.string() ?: ""
                    val code = createRes.code; createRes.close()

                    if (code !in 200..299) error("Failed to create complaint: HTTP $code - $createBody")
                    JSONObject(createBody).optString("id").ifBlank { error("No record ID returned") }
                }

                // Step 2: Upload attachments to the record
                if (state.attachments.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                        state.attachments.forEach { att ->
                            val data = att.bytes ?: run {
                                Log.w(TAG, "Skipping ${att.name}: bytes are null")
                                return@forEach
                            }
                            // ⚠️ "attachments" must match the exact field name in your PocketBase schema
                            builder.addFormDataPart(
                                "attachments",   // <-- verify this matches your PocketBase field name
                                att.name,
                                data.toRequestBody(att.mimeType.toMediaType())
                            )
                            Log.d(TAG, "Queued attachment: ${att.name} (${data.size} bytes, ${att.mimeType})")
                        }

                        val uploadRes = http.newCall(
                            Request.Builder()
                                .url("${AppConfig.BASE_URL}/api/collections/$COL_COMPLAINTS/records/$recordId")
                                .addHeader("Authorization", "Bearer $token")
                                .patch(builder.build())
                                .build()
                        ).execute()

                        val uploadBody = uploadRes.body?.string() ?: ""
                        val uploadCode = uploadRes.code; uploadRes.close()
                        Log.d(TAG, "Attachment upload HTTP $uploadCode: $uploadBody")   // ← check Logcat

                        if (uploadCode !in 200..299) {
                            Log.e(TAG, "Attachment upload failed: HTTP $uploadCode — $uploadBody")
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(isSubmitting = false)
                withContext(Dispatchers.Main) { onSuccess() }

            } catch (e: Exception) {
                Log.e(TAG, "submitComplaint: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                withContext(Dispatchers.Main) { onError(e.message ?: "Submission failed") }
            }
        }
    }
}
