package com.example.ritik_2.windowscontrol.pctouchpad

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModelFactory

class PcControlTouchpadActivity : ComponentActivity() {

    private lateinit var viewModel: PcControlViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel = ViewModelProvider(
            this,
            PcControlViewModelFactory(applicationContext)
        )[PcControlViewModel::class.java]
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
        applyRotationPolicy()
    }

    private fun applyRotationPolicy() {
        val autoRotate = Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1
        requestedOrientation = if (autoRotate)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        else
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}