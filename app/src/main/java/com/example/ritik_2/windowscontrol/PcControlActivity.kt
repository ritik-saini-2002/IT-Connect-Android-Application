package com.example.ritik_2.windowscontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PcControlActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private val viewModel: PcControlViewModel by viewModels {
        PcControlViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba -> PermissionGuard.canAccessFeature(6, role, perms, dba) },
                deniedMessage = "Windows Control — access not granted")) return
        enableEdgeToEdge()
        applyFullscreen()
        applyRotationPolicy()
        PcControlCrashHandler.install(this)

        if (intent.getBooleanExtra("crash_restart", false)) {
            val msg = intent.getStringExtra("crash_message") ?: "Unknown error"
            Toast.makeText(this, "Recovered from crash: $msg", Toast.LENGTH_LONG).show()
            PcControlCrashHandler.clearCrash(this)
        }

        viewModel.startRealTimeRefresh(3000L)
        viewModel.setSession(authRepository.getSession())

        setContent {
            ITConnectTheme {
                PcControlEntry(isLoggedIn = authRepository.isLoggedIn)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
        viewModel.setSession(authRepository.getSession())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyRotationPolicy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    override fun onStop() { super.onStop(); viewModel.stopRealTimeRefresh() }

    override fun onRestart() {
        super.onRestart()
        viewModel.startRealTimeRefresh(3000L)
        viewModel.pingPc()
    }
}