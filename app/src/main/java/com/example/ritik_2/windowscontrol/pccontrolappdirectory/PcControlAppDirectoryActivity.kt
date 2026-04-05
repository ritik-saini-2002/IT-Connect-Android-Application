package com.example.ritik_2.windowscontrol.pccontrolappdirectory

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

class PcControlAppDirectoryActivity : ComponentActivity() {

    private val viewModel: PcControlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draws behind system bars so the UI feels full-screen in both orientations
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface {
                    PcControlAppDirectoryUI(viewModel = viewModel)
                }
            }
        }
    }

    /**
     * Called whenever the device rotates.
     * Compose reacts to [LocalConfiguration] automatically, so we just let it recompose.
     * Override here only if you need to do extra work on rotation (e.g. analytics, logging).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // No manual handling needed — Compose observes LocalConfiguration changes reactively.
    }
}