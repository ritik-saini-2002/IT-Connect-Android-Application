package com.example.ritik_2.winshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ritik_2.theme.Ritik_2Theme

class ServerConnectActivity : ComponentActivity() {

    private lateinit var viewModel: ServerConnectModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enableEdgeToEdge lets the app draw behind status bar + navigation bar.
        // WindowCompat.setDecorFitsSystemWindows(false) is called again inside
        // ServerConnectScreen via SideEffect to control icon appearance per screen.
        enableEdgeToEdge()

        setContent {
            Ritik_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerConnectApp(
                        onNavigateBack = { handleBackNavigation() }
                    )
                }
            }
        }
    }

    private fun handleBackNavigation(): Boolean {
        return if (::viewModel.isInitialized && viewModel.canNavigateBack()) {
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
    onNavigateBack: () -> Boolean,
    viewModel: ServerConnectModule = viewModel()
) {
    ServerConnectScreen(
        onNavigateBack = onNavigateBack,
        viewModel = viewModel
    )
}