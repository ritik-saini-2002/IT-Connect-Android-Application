package com.example.ritik_2.windowscontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory

class PcControlActivity : ComponentActivity() {

    private val viewModel: PcControlViewModel by viewModels {
        PcControlViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PcControlCrashHandler.install(this)

        // Respect system auto-rotate setting
        applyRotationPolicy()

        viewModel.startRealTimeRefresh(3000L)

        setContent {
            ITConnectTheme {
                PcControlEntry()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRotationPolicy()
    }

    override fun onResume() {
        super.onResume()
        applyRotationPolicy()
    }

    private fun applyRotationPolicy() {
        val autoRotateOn = Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1

        requestedOrientation = if (autoRotateOn) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            // Lock to current orientation — don't override user preference
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopRealTimeRefresh()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.startRealTimeRefresh(3000L)
        viewModel.pingPc()
    }
}