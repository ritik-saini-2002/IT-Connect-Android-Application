package com.saini.ritik.appupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    private val context: Context,
    private val http   : OkHttpClient
) {
    private val TAG = "AppUpdateManager"

    /**
     * Downloads the APK from PocketBase into the app's private cache directory
     * (no external storage permission required), then triggers the system installer.
     *
     * [userToken] — the logged-in user's auth token (required to access protected file endpoint).
     * [onProgress] — called with 0f..1f during download; runs on main thread.
     * [onError]    — called with error message if download fails; runs on main thread.
     */
    fun downloadAndInstall(
        url       : String,
        userToken : String,
        onProgress: (Float) -> Unit = {},
        onError   : (String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(context.cacheDir, "itconnect_update.apk")

                if (file.exists()) file.delete()

                val res = http.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $userToken")
                        .build()
                ).execute()

                if (!res.isSuccessful) {
                    withContext(Dispatchers.Main) { onError("Download failed: server returned ${res.code}") }
                    res.close()
                    return@launch
                }

                val body   = res.body ?: run {
                    withContext(Dispatchers.Main) { onError("Download failed: empty response body") }
                    return@launch
                }
                val total  = body.contentLength().takeIf { it > 0 } ?: -1L
                var written = 0L

                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val progress = (written.toFloat() / total).coerceIn(0f, 1f)
                                CoroutineScope(Dispatchers.Main).launch { onProgress(progress) }
                            }
                        }
                    }
                }
                res.close()

                withContext(Dispatchers.Main) {
                    onProgress(1f)
                    triggerInstall(file)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                withContext(Dispatchers.Main) { onError("Download error: ${e.message ?: "unknown"}") }
            }
        }
    }

    private fun triggerInstall(apkFile: File) {

        // Check install permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return // User must re-tap after granting
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
