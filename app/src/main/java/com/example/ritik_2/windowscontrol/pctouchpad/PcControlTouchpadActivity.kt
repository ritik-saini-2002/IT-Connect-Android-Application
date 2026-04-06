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
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory

class PcControlTouchpadActivity : ComponentActivity() {

    // ✅ Use activity-ktx delegate — no lateinit, no manual factory boilerplate
    private val viewModel: PcControlViewModel by viewModels {
        PcControlViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ enableEdgeToEdge() is the modern replacement for
        //    WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        // ✅ Hide system bars for a true full-screen touchpad feel
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        applyRotationPolicy()

        setContent {
            ITConnectTheme {
                PcControlTouchpadUI(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRotationPolicy()
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        // No need to re-call applyRotationPolicy here — orientation lock/unlock
        // is a user preference that doesn't change mid-session on config flip.
        // Composable reacts to the new config automatically via LocalConfiguration.
    }

    /**
     * Reads the system auto-rotate setting once and applies the matching
     * orientation policy. Extracted to avoid duplication.
     *
     * Uses `Settings.System.getIntOrNull`-style safe read with a default of 0
     * so we don't crash on restricted profiles.
     */
    private fun applyRotationPolicy() {
        val autoRotate = runCatching {
            Settings.System.getInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1
        }.getOrDefault(false)

        requestedOrientation = if (autoRotate)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        else
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}