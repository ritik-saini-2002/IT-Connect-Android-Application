package com.example.ritik_2.winshare.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance HTTP file transfer client.
 *
 * Architecture: HTTP/1.1 over TCP with chunked streaming.
 * No full file loaded into memory — all I/O is streamed with a 1 MB buffer.
 *
 * Performance target: 100–300+ Mbps on 5 GHz WiFi / LAN.
 *
 * Key optimisations:
 *  • 1 MB buffer — reduces JVM/syscall overhead vs. small buffers
 *  • OkHttp connection pool — reuses sockets across calls
 *  • No read/write timeout — large files need unbounded time
 *  • runInterruptible — coroutine cancellation interrupts blocking I/O
 *  • Streaming RequestBody — zero-copy upload from ContentResolver InputStream
 *  • Okio sink flush after each 1 MB chunk — keeps network pipe full
 *  • Rolling speed window (500 ms) — accurate real-time MB/s display
 */
class HttpFileTransferClient(
    private val host: String,
    private val port: Int = 8765
) {
    private val baseUrl = "http://$host:$port"

    // ── OkHttp client ─────────────────────────────────────────────────────────
    // • connect timeout: 30 s (fail fast if PC is unreachable)
    // • read/write timeout: 0 = disabled (necessary for files > a few hundred MB)
    // • default connection pool: 5 connections, 5 min keep-alive
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    companion object {
        /** 1 MB — optimal for WiFi; large enough to keep the pipe full. */
        private const val BUFFER_SIZE = 1024 * 1024

        /** Speed averaging window in milliseconds. */
        private const val SPEED_WINDOW_MS = 500L
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Upload a file identified by a content [Uri] to [remotePath] on the server.
     *
     * Runs entirely on [Dispatchers.IO]. Cooperative cancellation via
     * [runInterruptible] + [Thread.currentThread().isInterrupted] in the inner
     * streaming loop.
     *
     * @param uri         Content URI (from file picker / SAF).
     * @param remotePath  Server-side directory path, e.g. "/Documents".
     * @param context     Used to open an InputStream from [ContentResolver].
     * @param onProgress  Called on the IO thread; post to main thread via ViewModel.
     */
    suspend fun uploadFile(
        uri: Uri,
        remotePath: String,
        context: Context,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val (fileName, fileSize) = resolveUriMeta(uri, cr)
        val inputStream = cr.openInputStream(uri)
            ?: return@withContext TransferResult.Failure("Cannot open file stream for URI")

        try {
            val body = streamingRequestBody(inputStream, fileSize, fileName, onProgress)
            val url = "$baseUrl/api/upload?path=${enc(remotePath)}&name=${enc(fileName)}"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("X-File-Size", fileSize.toString())
                .build()

            // runInterruptible: if this coroutine is cancelled, the IO thread is
            // interrupted, causing OkHttp to throw IOException (converted to
            // CancellationException by runInterruptible).
            runInterruptible {
                http.newCall(request).execute()
            }.use { response ->
                if (response.isSuccessful)
                    TransferResult.Success("$remotePath/$fileName")
                else
                    TransferResult.Failure("Server ${response.code}: ${response.message}")
            }
        } catch (e: CancellationException) {
            TransferResult.Cancelled
        } catch (e: IOException) {
            TransferResult.Failure(e.message ?: "Upload IO error", e)
        } catch (e: Exception) {
            TransferResult.Failure(e.message ?: "Upload failed", e)
        } finally {
            inputStream.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DOWNLOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Download a file at [remotePath] and save it to the device Downloads folder.
     *
     * Content-Length from the server drives the progress bar. Falls back to
     * indeterminate (-1) when the header is absent.
     *
     * @param remotePath  Full path on the server, e.g. "/Documents/report.pdf".
     * @param context     Used to resolve the Downloads directory.
     * @param onProgress  Called on the IO thread.
     */
    suspend fun downloadFile(
        remotePath: String,
        context: Context,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/download?path=${enc(remotePath)}"
        val request = Request.Builder().url(url).get().build()

        try {
            val response = runInterruptible { http.newCall(request).execute() }

            if (!response.isSuccessful) {
                response.close()
                return@withContext TransferResult.Failure("Server ${response.code}: ${response.message}")
            }

            val totalBytes = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val fileName = remotePath.substringAfterLast("/").ifBlank { "itconnect_download" }
            val outFile = createOutputFile(context, fileName)

            response.use { resp ->
                resp.body?.byteStream()?.use { input ->
                    outFile.outputStream().use { output ->
                        pipeWithProgress(
                            input = input,
                            output = output,
                            totalBytes = totalBytes,
                            fileName = fileName,
                            isUpload = false,
                            onProgress = onProgress
                        )
                    }
                }
            }

            TransferResult.Success(outFile.absolutePath)
        } catch (e: CancellationException) {
            TransferResult.Cancelled
        } catch (e: IOException) {
            TransferResult.Failure(e.message ?: "Download IO error", e)
        } catch (e: Exception) {
            TransferResult.Failure(e.message ?: "Download failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILE LISTING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * List files in a remote directory. Returns an empty list on any error.
     */
    suspend fun listFiles(remotePath: String): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/files?path=${enc(remotePath)}"
            val request = Request.Builder().url(url).get().build()
            runInterruptible { http.newCall(request).execute() }.use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseFileList(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Checks whether the server is reachable. Returns false on any error.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/ping").get().build()
            runInterruptible { http.newCall(request).execute() }.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a streaming [RequestBody] that reads directly from [inputStream]
     * without loading the whole file into memory.
     *
     * Cancellation is detected via [Thread.currentThread().isInterrupted], which
     * is set by [runInterruptible] when the coroutine is cancelled.
     */
    private fun streamingRequestBody(
        inputStream: InputStream,
        totalBytes: Long,
        fileName: String,
        onProgress: (TransferProgress) -> Unit
    ): RequestBody = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength() = totalBytes

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(BUFFER_SIZE)
            var transferred = 0L
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L
            var currentSpeed = 0L

            while (!Thread.currentThread().isInterrupted) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                sink.write(buffer, 0, read)
                // Flush after every chunk so Netty pushes bytes onto the wire
                // immediately rather than accumulating them in an OS send buffer.
                sink.flush()

                transferred += read
                windowBytes += read

                val now = System.currentTimeMillis()
                val elapsed = now - windowStart
                if (elapsed >= SPEED_WINDOW_MS) {
                    // Bytes per second over the last ~500 ms window
                    currentSpeed = windowBytes * 1_000L / elapsed
                    windowStart = now
                    windowBytes = 0L
                }

                onProgress(
                    TransferProgress(
                        fileName = fileName,
                        totalBytes = totalBytes,
                        transferredBytes = transferred,
                        speedBytesPerSec = currentSpeed,
                        isUpload = true
                    )
                )
            }
        }
    }

    /**
     * Streams bytes from [input] to [output] with 1 MB chunks and live progress.
     *
     * This is a suspend function so [isActive] correctly reflects coroutine
     * cancellation state — the loop exits cleanly without exceptions.
     */
    private suspend fun pipeWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        fileName: String,
        isUpload: Boolean,
        onProgress: (TransferProgress) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var transferred = 0L
        var windowStart = System.currentTimeMillis()
        var windowBytes = 0L
        var currentSpeed = 0L

        while (isActive) {
            val read = input.read(buffer)
            if (read == -1) break

            output.write(buffer, 0, read)
            transferred += read
            windowBytes += read

            val now = System.currentTimeMillis()
            val elapsed = now - windowStart
            if (elapsed >= SPEED_WINDOW_MS) {
                currentSpeed = windowBytes * 1_000L / elapsed
                windowStart = now
                windowBytes = 0L
            }

            onProgress(
                TransferProgress(
                    fileName = fileName,
                    totalBytes = totalBytes,
                    transferredBytes = transferred,
                    speedBytesPerSec = currentSpeed,
                    isUpload = isUpload
                )
            )
        }
        output.flush()
    }

    /** Reads display name and byte size from a content URI via ContentResolver. */
    private fun resolveUriMeta(uri: Uri, cr: ContentResolver): Pair<String, Long> {
        var name = "upload_${System.currentTimeMillis()}"
        var size = -1L
        cr.query(uri, null, null, null, null)?.use { cursor ->
            val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (ni >= 0) name = cursor.getString(ni)
                if (si >= 0) size = cursor.getLong(si)
            }
        }
        return name to size
    }

    /**
     * Resolves the local Downloads directory for saving received files.
     * On API 29+ uses the app's scoped external storage (no permission needed).
     * On older APIs writes to public Downloads.
     */
    private fun createOutputFile(context: Context, fileName: String): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        dir?.mkdirs()
        // Deduplicate: if file already exists append a counter
        var outFile = File(dir, fileName)
        var counter = 1
        val baseName = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        while (outFile.exists()) {
            outFile = if (ext.isEmpty()) File(dir, "${baseName}($counter)")
            else File(dir, "${baseName}($counter).$ext")
            counter++
        }
        return outFile
    }

    private fun parseFileList(json: String): List<RemoteFileItem> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                RemoteFileItem(
                    name = getString("name"),
                    path = getString("path"),
                    size = optLong("size", 0L),
                    isDirectory = optBoolean("isDirectory", false),
                    lastModified = optLong("lastModified", 0L)
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
