package com.saini.ritik.windowscontrol

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saini.ritik.windowscontrol.network.PcControlApiClient
import com.saini.ritik.windowscontrol.network.PcControlBrowseClient
import com.saini.ritik.windowscontrol.data.PcControlDatabase
import com.saini.ritik.windowscontrol.data.PcControlRepository
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModel
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModelFactory
import com.saini.ritik.windowscontrol.ui.screens.PcControlMainScreen

data class PcControlSettings(
    val pcIpAddress    : String  = "",
    val port           : Int     = 5000,
    val secretKey      : String  = "",
    /**
     * Phase 2.1 — SHA-256 of the agent's HTTPS certificate in either
     * "hex-with-colons" (AA:BB:…) or "sha256/<base64>" form. When non-null,
     * [baseUrl] switches to HTTPS and OkHttp pins the chain to this fp;
     * any other cert aborts the handshake. Null keeps the legacy HTTP flow.
     */
    val certFingerprint: String? = null,
    /**
     * Agent's MJPEG viewer port (defaults to 5001 — matches the agent's
     * built-in `/screen/viewer` server). Kept separate from [port] so the
     * control API and the live-view stream can live on different ports,
     * which matches how the Flask agent actually serves them.
     */
    val streamPort     : Int     = 5001,
) {
    /** Scheme flips to HTTPS as soon as the user has attested a cert fingerprint. */
    val baseUrl      get() = "${if (certFingerprint.isNullOrBlank()) "http" else "https"}://$pcIpAddress:$port"
    val isConfigured get() = pcIpAddress.isNotEmpty() && secretKey.isNotEmpty()
}

@SuppressLint("StaticFieldLeak")
object PcControlMain {

    private var _context     : Context?               = null
    private var _settings    : PcControlSettings?     = null
    internal var apiClient   : PcControlApiClient?    = null
    internal var browseClient: PcControlBrowseClient? = null
    internal var repository  : PcControlRepository?   = null

    val isInitialized get() = _context != null

    @SuppressLint("StaticFieldLeak")
    private const val LEGACY_DEFAULT_KEY = "Ritik@2002"

    fun init(context: Context, pcIp: String = "", port: Int = 5000, secretKey: String = "") {
        _context = context.applicationContext
        val prefs = context.getSharedPreferences("pccontrol_prefs", Context.MODE_PRIVATE)

        val savedIp = prefs.getString("pc_ip", "") ?: ""
        val savedPort = try {
            prefs.getInt("pc_port", port)
        } catch (e: ClassCastException) {
            prefs.getString("pc_port", port.toString())?.toIntOrNull() ?: port
        }
        val rawKey = prefs.getString("pc_key", secretKey) ?: secretKey
        // Treat legacy hardcoded key as empty — force user to set a new one
        val savedKey = if (rawKey == LEGACY_DEFAULT_KEY) "" else rawKey
        val savedFp  = prefs.getString("pc_cert_fp", null)?.takeIf { it.isNotBlank() }
        val savedStreamPort = try {
            prefs.getInt("pc_stream_port", 5001)
        } catch (e: ClassCastException) {
            prefs.getString("pc_stream_port", "5001")?.toIntOrNull() ?: 5001
        }

        val resolvedIp   = if (pcIp.isNotEmpty()) pcIp else savedIp
        val resolvedPort = savedPort
        val resolvedKey  = savedKey

        _settings    = PcControlSettings(resolvedIp, resolvedPort, resolvedKey, savedFp, savedStreamPort)
        apiClient    = PcControlApiClient(_settings!!)
        browseClient = PcControlBrowseClient(_settings!!)

        val db       = PcControlDatabase.getDatabase(context)
        repository   = PcControlRepository(
            planDao     = db.planDao(),
            logDao      = db.connectionLogDao(),
            deviceDao   = db.savedDeviceDao(),
            scheduleDao = db.scheduleDao()
        )
    }

    fun updateConnection(
        pcIp           : String,
        port           : Int     = 5000,
        secretKey      : String  = "",
        certFingerprint: String? = null,
        streamPort     : Int     = 5001,
    ) {
        val ctx = _context ?: return
        val s = PcControlSettings(
            pcIpAddress     = pcIp,
            port            = port,
            secretKey       = secretKey,
            certFingerprint = certFingerprint?.takeIf { it.isNotBlank() },
            streamPort      = streamPort,
        )
        _settings    = s
        apiClient    = PcControlApiClient(s)
        browseClient = PcControlBrowseClient(s)
        ctx.getSharedPreferences("pccontrol_prefs", Context.MODE_PRIVATE).edit()
            .putString("pc_ip", pcIp)
            .putInt("pc_port", port)
            .putString("pc_key", secretKey)
            .putString("pc_cert_fp", s.certFingerprint) // null clears it
            .putInt("pc_stream_port", streamPort)
            .apply()
    }

    fun getSettings(): PcControlSettings = _settings ?: PcControlSettings()
    fun getBaseUrl(): String = getSettings().baseUrl
    internal fun requireContext(): Context =
        _context ?: throw IllegalStateException("PcControlMain not initialized. Call init() first.")
}

@Composable
fun PcControlEntry(isLoggedIn: Boolean = true) {
    val context = PcControlMain.requireContext()
    val factory = remember { PcControlViewModelFactory(context) }
    val viewModel: PcControlViewModel = viewModel(factory = factory)
    PcControlMainScreen(viewModel = viewModel, isLoggedIn = isLoggedIn)
}