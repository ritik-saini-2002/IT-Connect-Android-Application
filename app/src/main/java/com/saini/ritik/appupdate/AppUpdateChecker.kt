package com.saini.ritik.appupdate

import com.saini.ritik.core.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName : String,
    val downloadUrl : String,
    val releaseNotes: String
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val http: OkHttpClient
) {
    /**
     * Compares version names using semver (e.g. "1.3.0" > "1.2.1").
     * Returns [UpdateInfo] if remote version > [currentVersionName], null otherwise.
     * Never throws — returns null on any network or parse error.
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        userToken         : String
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val res = http.newCall(
                Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/collections/app_updates/records" +
                            "?filter=(is_active=true)&sort=-version_code&perPage=1")
                    .addHeader("Authorization", "Bearer $userToken")
                    .get().build()
            ).execute()

            val body = res.body?.string() ?: return@withContext null
            res.close()

            if (!res.isSuccessful) return@withContext null

            val items = JSONObject(body).optJSONArray("items") ?: return@withContext null
            if (items.length() == 0) return@withContext null

            val record         = items.getJSONObject(0)
            val remoteVersion  = record.optString("version_name").trim()

            if (remoteVersion.isBlank()) return@withContext null
            if (!isNewerVersion(remoteVersion, currentVersionName)) return@withContext null

            val recordId  = record.optString("id")
            val apkFile   = record.optString("apk_file")
            val url       = "${AppConfig.BASE_URL}/api/files/app_updates/$recordId/$apkFile"

            UpdateInfo(
                versionName  = remoteVersion,
                downloadUrl  = url,
                releaseNotes = record.optString("release_notes")
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if [remote] is strictly greater than [current].
     * Compares each semver segment numerically. "1.3.0" > "1.2.9" → true.
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}