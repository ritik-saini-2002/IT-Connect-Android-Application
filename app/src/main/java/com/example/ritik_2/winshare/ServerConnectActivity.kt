package com.example.ritik_2.winshare

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        // ── Back button: folder-by-folder → close dialogs → disconnect → finish ──
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = viewModel.uiState.value
                when {
                    // Close multi-select first
                    state.isMultiSelectMode -> {
                        viewModel.handleEvent(ServerConnectEvent.ToggleMultiSelectMode)
                    }
                    // Close search
                    state.isSearchActive -> {
                        viewModel.handleEvent(ServerConnectEvent.ToggleSearch)
                    }
                    // Close any open dialog
                    state.showConnectionDialog -> viewModel.handleEvent(ServerConnectEvent.HideConnectionDialog)
                    state.showCreateFolderDialog -> viewModel.handleEvent(ServerConnectEvent.HideCreateFolderDialog)
                    state.showFileContextMenu -> viewModel.handleEvent(ServerConnectEvent.HideFileContextMenu)
                    state.showRenameDialog -> viewModel.handleEvent(ServerConnectEvent.HideRenameDialog)
                    state.showMoveDialog -> viewModel.handleEvent(ServerConnectEvent.HideMoveDialog)
                    state.showPropertiesDialog -> viewModel.handleEvent(ServerConnectEvent.HidePropertiesDialog)
                    state.showEditServerDialog -> viewModel.handleEvent(ServerConnectEvent.HideEditServerDialog)
                    // Navigate up through folders one by one
                    viewModel.canNavigateBack() -> viewModel.navigateUp()
                    // At root? Disconnect first
                    state.isConnected -> viewModel.disconnect()
                    // Nothing left — exit activity
                    else -> finish()
                }
            }
        })

        val autoConnectId = intent.getStringExtra("auto_connect_server_id")

        setContent {
            Ritik_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerConnectApp(
                        viewModel = viewModel,
                        autoConnectServerId = autoConnectId
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerConnectApp(
    viewModel: ServerConnectModule,
    autoConnectServerId: String? = null
) {
    if (autoConnectServerId != null) {
        viewModel.handleEvent(ServerConnectEvent.AutoConnectServer(autoConnectServerId))
    }
    ServerConnectScreen(viewModel = viewModel)
}