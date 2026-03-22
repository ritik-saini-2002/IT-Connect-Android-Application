package com.example.ritik_2.windowscontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
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

        // Install crash handler
        PcControlCrashHandler.install(this)

        // Allow both portrait and landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        // Start real-time refresh
        viewModel.startRealTimeRefresh(3000L)

        setContent {
            ITConnectTheme {
                PcControlEntry()
            }
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