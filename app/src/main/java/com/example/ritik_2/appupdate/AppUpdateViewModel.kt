package com.example.ritik_2.appupdate

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.core.AdminTokenProvider
import com.example.ritik_2.core.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

data class AppUpdateRecord(
    val id          : String,
    val versionCode : Int,
    val versionName : String,
    val releaseNotes: String,
    val apkFileName : String,
    val isActive    : Boolean,
    val created     : String
)

data class AppUpdateUiState(
    val records        : List<AppUpdateRecord> = emptyList(),
    val isLoading      : Boolean               = false,
    val isUploading    : Boolean               = false,
    val uploadProgress : Float                 = 0f,
    val error          : String?               = null,
    val successMessage : String?               = null,

    // form fields
    val formVersionCode : String  = "",
    val formVersionName : String  = "",
    val formReleaseNotes: String  = "",
    val formApkUri      : Uri?    = null,
    val formApkName     : String  = "",
    val formIsActive    : Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http              : OkHttpClient,
    private val adminTokenProvider: AdminTokenProvider
) : ViewModel() {

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val baseUrl = AppConfig.BASE_URL
    private val collection = "app_updates"

    init { loadRecords() }

    // ── Form ──────────────────────────────────────────────────────────────────

    fun onVersionCodeChange(v: String)  { _state.update { it.copy(formVersionCode  = v) } }
    fun onVersionNameChange(v: String)  { _state.update { it.copy(formVersionName  = v) } }
    fun onReleaseNotesChange(v: String) { _state.update { it.copy(formReleaseNotes = v) } }
    fun onIsActiveChange(v: Boolean)    { _state.update { it.copy(formIsActive     = v) } }
    fun clearMessages()                 { _state.update { it.copy(error = null, successMessage = null) } }

    fun onApkSelected(uri: Uri, name: String) {
        _state.update { it.copy(formApkUri = uri, formApkName = name) }
    }

    fun resetForm() {
        _state.update { it.copy(
            formVersionCode  = "",
            formVersionName  = "",
            formReleaseNotes = "",
            formApkUri       = null,
            formApkName      = "",
            formIsActive     = false
        )}
    }

    // ── Load records ──────────────────────────────────────────────────────────

    fun loadRecords() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val token = adminTokenProvider.getAdminTokenSync()
                val res = withContext(Dispatchers.IO) {
                    http.newCall(
                        Request.Builder()
                            .url("$baseUrl/api/collections/$collection/records?sort=-created&perPage=20")
                            .addHeader("Authorization", "Bearer $token")
                            .get().build()
                    ).execute()
                }
                val body = res.body?.string() ?: ""
                res.close()
                if (!res.isSuccessful) {
                    _state.update { it.copy(isLoading = false, error = "Failed to load records: ${res.code}") }
                    return@launch
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val records = (0 until items.length()).map { i ->
                    val obj = items.getJSONObject(i)
                    AppUpdateRecord(
                        id           = obj.optString("id"),
                        versionCode  = obj.optInt("version_code"),
                        versionName  = obj.optString("version_name"),
                        releaseNotes = obj.optString("release_notes"),
                        apkFileName  = obj.optString("apk_file"),
                        isActive     = obj.optBoolean("is_active"),
                        created      = obj.optString("created").take(10)
                    )
                }
                _state.update { it.copy(isLoading = false, records = records) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Load error: ${e.message}") }
            }
        }
    }

    // ── Upload new APK ────────────────────────────────────────────────────────

    fun uploadUpdate() {
        val s = _state.value
        val uri  = s.formApkUri      ?: return
        val code = s.formVersionCode.trim().toIntOrNull()
        if (code == null || code <= 0) {
            _state.update { it.copy(error = "Version code must be a positive integer") }; return
        }
        if (s.formVersionName.isBlank()) {
            _state.update { it.copy(error = "Version name cannot be empty") }; return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }
            try {
                val token = adminTokenProvider.getAdminTokenSync()

                // If this is marked active → deactivate all existing active records first
                if (s.formIsActive) deactivateAll(token)

                // Read APK bytes
                val apkBytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw IllegalStateException("Cannot read APK file")
                }
                _state.update { it.copy(uploadProgress = 0.3f) }

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("version_code",  code.toString())
                    .addFormDataPart("version_name",  s.formVersionName.trim())
                    .addFormDataPart("release_notes", s.formReleaseNotes.trim())
                    .addFormDataPart("is_active",     s.formIsActive.toString())
                    .addFormDataPart(
                        "apk_file",
                        s.formApkName.ifBlank { "app-release.apk" },
                        apkBytes.toRequestBody("application/vnd.android.package-archive".toMediaType())
                    )
                    .build()

                _state.update { it.copy(uploadProgress = 0.6f) }

                val res = withContext(Dispatchers.IO) {
                    http.newCall(
                        Request.Builder()
                            .url("$baseUrl/api/collections/$collection/records")
                            .addHeader("Authorization", "Bearer $token")
                            .post(multipart)
                            .build()
                    ).execute()
                }
                val body = res.body?.string() ?: ""
                res.close()

                if (!res.isSuccessful) {
                    _state.update { it.copy(isUploading = false, error = "Upload failed (${res.code}): $body") }
                    return@launch
                }

                _state.update { it.copy(
                    isUploading    = false,
                    uploadProgress = 1f,
                    successMessage = "Version ${s.formVersionName} uploaded successfully"
                )}
                resetForm()
                loadRecords()
            } catch (e: Exception) {
                _state.update { it.copy(isUploading = false, error = "Upload error: ${e.message}") }
            }
        }
    }

    // ── Activate / deactivate ─────────────────────────────────────────────────

    fun setActive(recordId: String, active: Boolean) {
        viewModelScope.launch {
            try {
                val token = adminTokenProvider.getAdminTokenSync()
                if (active) deactivateAll(token)
                patchRecord(recordId, JSONObject().put("is_active", active).toString(), token)
                _state.update { it.copy(successMessage = if (active) "Update activated" else "Update deactivated") }
                loadRecords()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Toggle error: ${e.message}") }
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            try {
                val token = adminTokenProvider.getAdminTokenSync()
                val res = withContext(Dispatchers.IO) {
                    http.newCall(
                        Request.Builder()
                            .url("$baseUrl/api/collections/$collection/records/$recordId")
                            .addHeader("Authorization", "Bearer $token")
                            .delete()
                            .build()
                    ).execute()
                }
                res.close()
                _state.update { it.copy(successMessage = "Record deleted") }
                loadRecords()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Delete error: ${e.message}") }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun deactivateAll(token: String) {
        val current = _state.value.records.filter { it.isActive }
        for (r in current) {
            patchRecord(r.id, JSONObject().put("is_active", false).toString(), token)
        }
    }

    private suspend fun patchRecord(id: String, json: String, token: String) {
        withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder()
                    .url("$baseUrl/api/collections/$collection/records/$id")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .patch(json.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().close()
        }
    }
}
