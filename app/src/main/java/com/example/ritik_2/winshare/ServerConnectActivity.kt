package com.example.ritik_2.winshare

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

    // 🚀 FIX #1: Use `by viewModels()` delegate instead of `lateinit var`.
    // This is the idiomatic Jetpack way — the ViewModel is created lazily on
    // first access and is guaranteed to be initialized, so `::viewModel.isInitialized`
    // checks are no longer needed. This also prevents a potential crash if
    // handleBackNavigation() was ever called before onCreate() completed.
    private val viewModel: ServerConnectModule by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚀 FIX #2: Request the FEATURE_NO_TITLE window feature before super.onCreate()
        // so the window is set up correctly before the DecorView is created.
        // This avoids a subtle flicker on some devices when using edge-to-edge.
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        super.onCreate(savedInstanceState)

        // 🚀 FIX #3: Call WindowCompat here in addition to enableEdgeToEdge()
        // to ensure the decor does NOT fit system windows at the earliest possible
        // point — before setContent() creates the Compose hierarchy. This prevents
        // a single-frame layout jump on activity start.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // enableEdgeToEdge lets the app draw behind status bar + navigation bar.
        // The SideEffect inside ServerConnectScreen fine-tunes icon appearance per screen.
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

    // 🚀 FIX #4: No longer needs the isInitialized guard because the delegate
    // guarantees initialization. Logic is otherwise identical.
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
    // 🚀 FIX #5: ViewModel is now passed in directly from the Activity instead of
    // re-requesting it via viewModel() inside the composable. This guarantees that
    // the Activity and the Composable always share the exact same ViewModel instance,
    // and avoids a redundant ViewModelProvider lookup on every recomposition.
    ServerConnectScreen(
        onNavigateBack = onNavigateBack,
        viewModel = viewModel
    )
}