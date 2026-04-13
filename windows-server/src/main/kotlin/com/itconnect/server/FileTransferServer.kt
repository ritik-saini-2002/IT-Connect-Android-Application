package com.itconnect.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.Charset

// ─── Configuration ────────────────────────────────────────────────────────────

/** TCP port the server binds on. Change here or pass -Dport=XXXX at runtime. */
private val PORT = System.getProperty("port")?.toIntOrNull() ?: 8765

/**
 * Root directory shared to Android clients.
 *
 * Default: %USERPROFILE%\ITConnectShare   (e.g. C:\Users\YourName\ITConnectShare)
 *
 * Override with:  java -DshareRoot=D:\MyShare -jar ...
 */
private val SHARE_ROOT: File = run {
    val custom = System.getProperty("shareRoot")
    if (custom != null) File(custom) else File(System.getProperty("user.home"), "ITConnectShare")
}

/** 1 MB — matches the Android client buffer for maximum throughput. */
private const val BUFFER_SIZE = 1024 * 1024

private val log = LoggerFactory.getLogger("ITConnectServer")

// ─── Entry Point ──────────────────────────────────────────────────────────────

fun main() {
    SHARE_ROOT.mkdirs()

    println("╔══════════════════════════════════════════════════════╗")
    println("║       IT Connect  –  High-Speed File Server          ║")
    println("╠══════════════════════════════════════════════════════╣")
    println("║  Shared folder : ${SHARE_ROOT.absolutePath.padEnd(36)}║")
    println("║  Port          : $PORT${" ".repeat(37)}║")
    println("╠══════════════════════════════════════════════════════╣")
    println("║  Connect your Android device to one of these IPs:    ║")
    localIpv4Addresses().forEach { ip ->
        val line = "  →  http://$ip:$PORT"
        println("║  ${line.padEnd(52)}║")
    }
    println("╚══════════════════════════════════════════════════════╝")

    embeddedServer(
        factory = Netty,
        port = PORT,
        host = "0.0.0.0",
        configure = {
            // Disable response/request timeouts — large files need unbounded time.
            responseWriteTimeoutSeconds = 0
            requestReadTimeoutSeconds  = 0
            // Netty worker threads: default is 2× CPUs, which is fine for I/O.
        }
    ) {
        installPlugins()
        configureRouting()
    }.start(wait = true)
}

// ─── Plugins ──────────────────────────────────────────────────────────────────

fun Application.installPlugins() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.path()}", cause)
            call.respondText(
                text = "Internal server error: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Application.configureRouting() {
    routing {

        // ── Health check ─────────────────────────────────────────────────────
        get("/api/ping") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // ── Server info ──────────────────────────────────────────────────────
        get("/api/info") {
            val info = buildString {
                append("{\"shareRoot\":\"${SHARE_ROOT.absolutePath.replace("\\", "\\\\")}\",")
                append("\"freeBytes\":${SHARE_ROOT.freeSpace},")
                append("\"totalBytes\":${SHARE_ROOT.totalSpace}}")
            }
            call.respondText(info, ContentType.Application.Json)
        }

        // ── List directory ───────────────────────────────────────────────────
        // GET /api/files?path=/Documents
        get("/api/files") {
            val path = call.queryParam("path") ?: "/"
            val dir = resolveSecure(path)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Path traversal denied")

            if (!dir.exists() || !dir.isDirectory) {
                return@get call.respond(HttpStatusCode.NotFound, "Directory not found: $path")
            }

            val entries = withContext(Dispatchers.IO) {
                dir.listFiles()
                    ?.map { f ->
                        FileEntry(
                            name = f.name,
                            path = "$path/${f.name}".replace("//", "/"),
                            size = if (f.isFile) f.length() else 0L,
                            isDirectory = f.isDirectory,
                            lastModified = f.lastModified()
                        )
                    }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()
            }

            call.respondText(
                text = Json.encodeToString(entries),
                contentType = ContentType.Application.Json
            )
        }

        // ── Download file ─────────────────────────────────────────────────────
        // GET /api/download?path=/Documents/report.pdf
        //
        // Performance notes:
        //  • Content-Length is always set → Android client shows accurate progress.
        //  • Data is streamed in 1 MB chunks directly from FileInputStream to the
        //    Netty channel, never fully buffered in the JVM heap.
        //  • flush() after each chunk keeps the Netty send-buffer full.
        get("/api/download") {
            val path = call.queryParam("path")
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing path")
            val file = resolveSecure(path)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Path traversal denied")

            if (!file.exists() || !file.isFile) {
                return@get call.respond(HttpStatusCode.NotFound, "File not found: $path")
            }

            val encodedName = file.name.encodeUrlComponent()
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${file.name}\"; filename*=UTF-8''$encodedName"
            )
            call.response.header(HttpHeaders.ContentLength, file.length().toString())
            call.response.header(HttpHeaders.AcceptRanges, "bytes")

            call.respondOutputStream(
                contentType = ContentType.Application.OctetStream,
                status = HttpStatusCode.OK
            ) {
                withContext(Dispatchers.IO) {
                    file.inputStream().buffered(BUFFER_SIZE).use { input ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            write(buf, 0, n)
                            flush()          // push to wire immediately
                        }
                    }
                }
            }
        }

        // ── Upload file ───────────────────────────────────────────────────────
        // POST /api/upload?path=/Documents&name=photo.jpg
        //
        // The Android client sends the raw binary body (Content-Type:
        // application/octet-stream) with Content-Length set to the file size.
        // We stream directly from receiveStream() to a FileOutputStream — the
        // file never lives in the JVM heap.
        post("/api/upload") {
            val path = call.queryParam("path") ?: "/"
            val name = call.queryParam("name")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing name")

            val dir = resolveSecure(path)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Path traversal denied")

            withContext(Dispatchers.IO) { dir.mkdirs() }

            val outFile = File(dir, sanitiseName(name))

            withContext(Dispatchers.IO) {
                call.receiveStream().use { input ->
                    outFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                        }
                        output.flush()
                    }
                }
            }

            log.info("Received: ${outFile.absolutePath}  (${outFile.length()} bytes)")

            call.respondText(
                text = "{\"path\":\"${outFile.absolutePath.replace("\\", "\\\\")}\"}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }

        // ── Create directory ──────────────────────────────────────────────────
        // POST /api/mkdir?path=/Documents/NewFolder
        post("/api/mkdir") {
            val path = call.queryParam("path")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing path")
            val dir = resolveSecure(path)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Path traversal denied")

            withContext(Dispatchers.IO) { dir.mkdirs() }
            call.respond(HttpStatusCode.OK)
        }

        // ── Delete file / directory ───────────────────────────────────────────
        // DELETE /api/delete?path=/Documents/old.txt
        delete("/api/delete") {
            val path = call.queryParam("path")
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing path")
            val target = resolveSecure(path)
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Path traversal denied")

            if (!target.exists()) {
                return@delete call.respond(HttpStatusCode.NotFound, "Not found: $path")
            }

            withContext(Dispatchers.IO) { target.deleteRecursively() }
            call.respond(HttpStatusCode.OK)
        }

        // ── Rename / move ─────────────────────────────────────────────────────
        // POST /api/rename?from=/Docs/old.txt&to=/Docs/new.txt
        post("/api/rename") {
            val from = call.queryParam("from")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing from")
            val to = call.queryParam("to")
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing to")

            val src = resolveSecure(from)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Path traversal denied (from)")
            val dst = resolveSecure(to)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Path traversal denied (to)")

            if (!src.exists()) {
                return@post call.respond(HttpStatusCode.NotFound, "Source not found")
            }

            withContext(Dispatchers.IO) { src.renameTo(dst) }
            call.respond(HttpStatusCode.OK)
        }
    }
}

// ─── Security ─────────────────────────────────────────────────────────────────

/**
 * Resolves [path] against [SHARE_ROOT] and verifies that the canonical result
 * is still inside [SHARE_ROOT].  Returns null if a path-traversal attack is
 * detected (e.g. path contains "..").
 */
fun resolveSecure(path: String): File? {
    val root = SHARE_ROOT.canonicalFile
    val resolved = File(root, path.replace('/', File.separatorChar)).canonicalFile
    return if (resolved.path.startsWith(root.path + File.separator) || resolved == root)
        resolved
    else
        null
}

/**
 * Strips characters that are illegal in Windows file names and limits length.
 */
fun sanitiseName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]"""), "_").trimEnd('.', ' ').take(240).ifBlank { "upload" }

// ─── Data Model ───────────────────────────────────────────────────────────────

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)

// ─── Utilities ────────────────────────────────────────────────────────────────

private fun ApplicationCall.queryParam(name: String): String? =
    request.queryParameters[name]?.let { URLDecoder.decode(it, "UTF-8") }

private fun String.encodeUrlComponent(): String =
    java.net.URLEncoder.encode(this, Charset.forName("UTF-8")).replace("+", "%20")

private fun localIpv4Addresses(): List<String> = try {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { !it.isLoopback && it.isUp }
        ?.flatMap { iface -> iface.inetAddresses.asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.map { it.hostAddress }
        ?.toList()
        ?: emptyList()
} catch (e: Exception) {
    emptyList()
}
