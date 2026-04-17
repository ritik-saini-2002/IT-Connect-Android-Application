package com.example.ritik_2.windowscontrol.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.PcSavedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches a low-res JPEG from the agent's `/screen/capture` endpoint and caches it
 * under `<files>/pc_thumbs/<deviceId>.jpg`. Returns the final absolute path on success.
 * Small on purpose (q=20, s=6) — these are list thumbnails, not viewers.
 */
object PcThumbnailFetcher {

    private const val TAG       = "PcThumbnail"
    private const val DIR_NAME  = "pc_thumbs"
    private const val QUALITY   = 20
    private const val SCALE_DIV = 6

    suspend fun fetch(device: PcSavedDevice, context: Context): String? =
        withContext(Dispatchers.IO) {
            if (device.host.isBlank() || device.secretKey.isBlank()) return@withContext null
            val api = PcControlApiClient(
                PcControlSettings(device.host, device.port, device.secretKey)
            )
            val result = api.captureScreen(quality = QUALITY, scale = SCALE_DIV)
            val b64 = result.data?.takeIf { result.success } ?: return@withContext null

            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }
                .getOrNull()
                ?.takeIf { it.size > 256 }
                ?: return@withContext null

            writeAtomic(context, device.id, bytes)
        }

    fun cachedFile(context: Context, deviceId: String): File =
        File(File(context.filesDir, DIR_NAME), "$deviceId.jpg")

    fun deleteCached(context: Context, deviceId: String) {
        runCatching { cachedFile(context, deviceId).delete() }
    }

    private fun writeAtomic(context: Context, deviceId: String, bytes: ByteArray): String? {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val tmp = File(dir, "$deviceId.tmp")
        val out = File(dir, "$deviceId.jpg")
        return try {
            tmp.writeBytes(bytes)
            if (out.exists()) out.delete()
            if (tmp.renameTo(out)) out.absolutePath else null
        } catch (e: Exception) {
            Log.w(TAG, "thumb write failed for $deviceId: ${e.message}")
            runCatching { tmp.delete() }
            null
        }
    }
}
