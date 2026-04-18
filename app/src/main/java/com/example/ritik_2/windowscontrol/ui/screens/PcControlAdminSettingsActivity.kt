package com.example.ritik_2.windowscontrol.ui.screens

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts PcControlAdminSettingsUI in its own Activity so the master-key admin
 * flow doesn't clutter the main settings screen. The back-navigation
 * (Close icon in the top bar) finishes this Activity and returns the user
 * to the previous screen.
 *
 * Shares the same PcControlViewModel-shaped factory used by the other
 * windowscontrol Activities so settings/connection-status stay consistent.
 */
/**
 * Extends [FragmentActivity] (not bare ComponentActivity) because Phase 2.3's
 * [com.example.ritik_2.windowscontrol.security.MasterActionGate] uses
 * `BiometricPrompt`, which requires a FragmentActivity host to stage its
 * internal fragment.
 */
@AndroidEntryPoint
class PcControlAdminSettingsActivity : FragmentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private val viewModel: PcControlViewModel by viewModels {
        PcControlViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessWindowsControlSub("windows_control_admin_settings", role, perms, dba)
                },
                deniedMessage = "Admin Settings — access not granted")) return
        enableEdgeToEdge()
        applyFullscreen()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            ITConnectTheme {
                Surface {
                    PcControlAdminSettingsUI(
                        viewModel = viewModel,
                        onBack    = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
