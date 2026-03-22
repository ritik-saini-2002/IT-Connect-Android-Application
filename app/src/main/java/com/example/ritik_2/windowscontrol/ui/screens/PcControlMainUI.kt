package com.example.ritik_2.windowscontrol.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import com.example.ritik_2.windowscontrol.viewmodel.PcUiState

// ─────────────────────────────────────────────────────────────
//  PcControlMainScreen — Root screen with bottom nav
//  Named: PcControlMainUI
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlMainScreen(viewModel: PcControlViewModel) {

    val currentScreen by viewModel.currentScreen.collectAsState()
    val editingPlan by viewModel.editingPlan.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar feedback
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PcUiState.Success -> { snackbarHostState.showSnackbar(s.message); viewModel.resetUiState() }
            is PcUiState.Error   -> { snackbarHostState.showSnackbar(s.message); viewModel.resetUiState() }
            else -> {}
        }
    }

    // If editing a plan, show the plan editor full screen
    if (editingPlan != null) {
        BackHandler { viewModel.cancelEdit() }
        PcControlPlanEditorUI(viewModel = viewModel)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PcControlBottomNav(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    viewModel.navigateTo(screen)
                    when (screen) {
                        PcScreen.APP_DIRECTORY -> viewModel.loadInstalledApps()
                        PcScreen.FILE_BROWSER  -> viewModel.loadDrives()
                        else -> {}
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() + slideInHorizontally() togetherWith
                            fadeOut() + slideOutHorizontally()
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    PcScreen.PLANS         -> PcControlPlansUI(viewModel)
                    PcScreen.APP_DIRECTORY -> PcControlAppDirectoryUI(viewModel)
                    PcScreen.FILE_BROWSER  -> PcControlFileBrowserUI(viewModel)
                    PcScreen.TOUCHPAD      -> PcControlTouchpadUI(viewModel)
                    PcScreen.KEYBOARD      -> PcControlKeyboardUI(viewModel)
                    PcScreen.SETTINGS      -> PcControlSettingsUI(viewModel)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  BOTTOM NAVIGATION
// ─────────────────────────────────────────────────────────────

data class PcNavItem(
    val screen: PcScreen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

private val navItems = listOf(
    PcNavItem(PcScreen.PLANS,         "Plans",    Icons.Default.List,       Icons.Default.List),
    PcNavItem(PcScreen.APP_DIRECTORY, "Apps",     Icons.Default.Apps,       Icons.Default.Apps),
    PcNavItem(PcScreen.FILE_BROWSER,  "Files",    Icons.Default.Folder,     Icons.Default.FolderOpen),
    PcNavItem(PcScreen.TOUCHPAD,      "Control",  Icons.Default.Mouse,      Icons.Default.Mouse),
    PcNavItem(PcScreen.SETTINGS,      "Settings", Icons.Default.Settings,   Icons.Default.Settings),
)

@Composable
fun PcControlBottomNav(
    currentScreen: PcScreen,
    onNavigate: (PcScreen) -> Unit
) {
    NavigationBar(
        tonalElevation = 8.dp
    ) {
        navItems.forEach { item ->
            val selected = currentScreen == item.screen
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}