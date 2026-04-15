package com.example.ritik_2.windowscontrol

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ritik_2.windowscontrol.network.PcControlApiClient
import com.example.ritik_2.windowscontrol.network.PcControlBrowseClient
import com.example.ritik_2.windowscontrol.data.PcControlDatabase
import com.example.ritik_2.windowscontrol.data.PcControlRepository
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory
import com.example.ritik_2.windowscontrol.ui.screens.PcControlMainScreen

data class PcControlSettings(
    val pcIpAddress : String = "",
    val port        : Int    = 5000,
    val secretKey   : String = ""
) {
    val baseUrl      get() = "http://$pcIpAddress:$port"
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

        val resolvedIp   = if (pcIp.isNotEmpty()) pcIp else savedIp
        val resolvedPort = savedPort
        val resolvedKey  = savedKey

        _settings    = PcControlSettings(resolvedIp, resolvedPort, resolvedKey)
        apiClient    = PcControlApiClient(_settings!!)
        browseClient = PcControlBrowseClient(_settings!!)

        val db       = PcControlDatabase.getDatabase(context)
        repository   = PcControlRepository(db.planDao())
    }

    fun updateConnection(pcIp: String, port: Int = 5000, secretKey: String = "") {
        val ctx = _context ?: return
        val s = PcControlSettings(pcIp, port, secretKey)
        _settings    = s
        apiClient    = PcControlApiClient(s)
        browseClient = PcControlBrowseClient(s)
        ctx.getSharedPreferences("pccontrol_prefs", Context.MODE_PRIVATE).edit()
            .putString("pc_ip", pcIp).putInt("pc_port", port).putString("pc_key", secretKey).apply()
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