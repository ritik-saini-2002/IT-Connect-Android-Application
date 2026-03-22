package com.example.ritik_2.windowscontrol

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ritik_2.windowscontrol.data.PcControlDatabase
import com.example.ritik_2.windowscontrol.data.PcControlRepository
import com.example.ritik_2.windowscontrol.network.PcControlApiClient
import com.example.ritik_2.windowscontrol.network.PcControlBrowseClient
import com.example.ritik_2.windowscontrol.ui.screens.PcControlMainScreen
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory

// ─────────────────────────────────────────────────────────────
//  PcControlMain — Single entry point for your major project
//
//  HOW TO EMBED:
//
//  Step 1 — In your Application class or first screen:
//      PcControlMain.init(context, pcIp = "192.168.1.5")
//
//  Step 2 — Anywhere in your Compose NavGraph:
//      composable("pc_control") { PcControlEntry() }
//
//  That's it! Full PC control embedded in 2 steps.
// ─────────────────────────────────────────────────────────────

object PcControlMain {

    private var _context: Context? = null
    private var _settings: PcControlSettings? = null
    internal var apiClient: PcControlApiClient? = null
    internal var browseClient: PcControlBrowseClient? = null
    internal var repository: PcControlRepository? = null

    val isInitialized get() = _context != null

    /**
     * Initialize the PC Control package.
     * Call once — in Application.onCreate() or your main Activity.
     */
    fun init(
        context: Context,
        pcIp: String = "",
        port: Int = 5000,
        secretKey: String = "my_secret_123"
    ) {
        _context = context.applicationContext
        val prefs = context.getSharedPreferences("pccontrol_prefs", Context.MODE_PRIVATE)

        // Use saved IP if not provided
        val resolvedIp = pcIp.ifEmpty { prefs.getString("pc_ip", "") ?: "" }

        _settings = PcControlSettings(
            pcIpAddress = resolvedIp,
            port = port,
            secretKey = secretKey
        )

        apiClient = PcControlApiClient(_settings!!)
        browseClient = PcControlBrowseClient(_settings!!)

        val db = PcControlDatabase.getDatabase(context)
        repository = PcControlRepository(db.planDao())
    }

    /**
     * Update connection settings at runtime (e.g. user changes IP)
     */
    fun updateConnection(pcIp: String, port: Int = 5000, secretKey: String = "my_secret_123") {
        val ctx = _context ?: return
        val newSettings = PcControlSettings(pcIp, port, secretKey)
        _settings = newSettings
        apiClient = PcControlApiClient(newSettings)
        browseClient = PcControlBrowseClient(newSettings)

        // Persist
        ctx.getSharedPreferences("pccontrol_prefs", Context.MODE_PRIVATE)
            .edit().putString("pc_ip", pcIp).apply()
    }

    /**
     * Get current settings
     */
    fun getSettings(): PcControlSettings {
        return _settings ?: PcControlSettings()
    }

    internal fun requireContext(): Context =
        _context ?: throw IllegalStateException(
            "PcControlMain not initialized. Call PcControlMain.init(context) first."
        )
}

// ─────────────────────────────────────────────────────────────
//  PcControlEntry — Drop this anywhere in your Compose NavGraph
// ─────────────────────────────────────────────────────────────

@Composable
fun PcControlEntry() {
    val context = PcControlMain.requireContext()
    val factory = remember { PcControlViewModelFactory(context) }
    val viewModel: PcControlViewModel = viewModel(factory = factory)
    PcControlMainScreen(viewModel = viewModel)
}

// ─────────────────────────────────────────────────────────────
//  Settings Data Class
// ─────────────────────────────────────────────────────────────

data class PcControlSettings(
    val pcIpAddress: String = "",
    val port: Int = 5000,
    val secretKey: String = "my_secret_123"
) {
    val baseUrl get() = "http://$pcIpAddress:$port"
    val isConfigured get() = pcIpAddress.isNotEmpty()
}