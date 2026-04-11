package com.example.ritik_2.windowscontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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

        if (intent.getBooleanExtra("crash_restart", false)) {
            val msg = intent.getStringExtra("crash_message") ?: "Unknown error"
            Toast.makeText(this, "Recovered from crash: $msg", Toast.LENGTH_LONG).show()
            PcControlCrashHandler.clearCrash(this)
        }

        applyRotationPolicy()
        viewModel.startRealTimeRefresh(3000L)

        setContent { ITConnectTheme { PcControlEntry() } }
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
        val autoRotate = try {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (_: Exception) { false }
        requestedOrientation = if (autoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR
        else ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    override fun onStop() { super.onStop(); viewModel.stopRealTimeRefresh() }

    override fun onRestart() {
        super.onRestart()
        viewModel.startRealTimeRefresh(3000L)
        viewModel.pingPc()
    }
}