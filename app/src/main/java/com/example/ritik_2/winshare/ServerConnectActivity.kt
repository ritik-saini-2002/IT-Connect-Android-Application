package com.example.ritik_2.winshare

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.ritik_2.theme.Ritik_2Theme

class ServerConnectActivity : ComponentActivity() {

    private val viewModel: ServerConnectModule by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // ✅ FIX: Lock to portrait so the screen never rotates into landscape.
        // Landscape mode was showing a fullscreen/distorted layout because edge-to-edge
        // with transparent bars + horizontal insets in landscape causes the Scaffold
        // content to fill the full display width including notch/cutout areas.
        // Locking portrait eliminates this entirely without needing custom inset handling.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Ensure decor does NOT fit system windows before setContent() creates Compose UI.
        // This prevents a 1-frame layout jump on activity start.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        setContent {
            Ritik_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerConnectApp(
                        viewModel = viewModel,
                        onNavigateBack = { handleBackNavigation() }
                    )
                }
            }
        }
    }

    private fun handleBackNavigation(): Boolean {
        return if (viewModel.canNavigateBack()) {
            viewModel.navigateUp()
            true
        } else {
            finish()
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!handleBackNavigation()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

@Composable
private fun ServerConnectApp(
    viewModel: ServerConnectModule,
    onNavigateBack: () -> Boolean
) {
    ServerConnectScreen(
        onNavigateBack = onNavigateBack,
        viewModel = viewModel
    )
}