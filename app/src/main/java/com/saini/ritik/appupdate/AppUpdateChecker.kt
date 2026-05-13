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
    val versionCode : Int,
    val versionName : String,
    val downloadUrl : String,
    val releaseNotes: String
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val http: OkHttpClient
) {
    /**
     * Silently checks for an available update.
     * Returns [UpdateInfo] if remote version > [currentVersionCode], null otherwise.
     * Never throws — returns null on any network or parse error.
     *
     * Uses the authenticated user token (passed in). No admin credentials required for reading.
     */
    suspend fun checkForUpdate(
        currentVersionCode: Int,
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

            val record     = items.getJSONObject(0)
            val remoteCode = record.optInt("version_code", 0)

            if (remoteCode <= 0) return@withContext null

            // No update needed
            if (remoteCode <= currentVersionCode) return@withContext null

            val recordId  = record.optString("id")
            val apkFile   = record.optString("apk_file")
            val url       = "${AppConfig.BASE_URL}/api/files/app_updates/$recordId/$apkFile"

            UpdateInfo(
                versionCode  = remoteCode,
                versionName  = record.optString("version_name"),
                downloadUrl  = url,
                releaseNotes = record.optString("release_notes")
            )
        } catch (_: Exception) {
            // Silent fail — never block app start
            null
        }
    }
}
