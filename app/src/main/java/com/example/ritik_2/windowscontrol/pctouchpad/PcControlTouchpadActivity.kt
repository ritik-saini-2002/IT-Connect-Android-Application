package com.example.ritik_2.windowscontrol.pctouchpad

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
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
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PcControlTouchpadActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private val viewModel: PcControlViewModel by viewModels {
        PcControlViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessWindowsControlSub(Permissions.PERM_WINDOWS_CONTROL_TOUCHPAD, role, perms, dba)
                },
                deniedMessage = "Touchpad — access not granted")) return
        enableEdgeToEdge()
        applyFullscreen()
        applyRotationPolicy()

        setContent {
            ITConnectTheme {
                PcControlTouchpadUI(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
        applyRotationPolicy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    /**
     * True fullscreen — hides status bar + navigation bar entirely.
     * Swipe from edge to temporarily reveal them.
     */
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
}