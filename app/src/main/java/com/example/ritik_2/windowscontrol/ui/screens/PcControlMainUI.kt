package com.example.ritik_2.windowscontrol.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  MAIN SCREEN — Navigation drawer + bottom nav
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlMainScreen(viewModel: PcControlViewModel) {

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val editingPlan   by viewModel.editingPlan.collectAsStateWithLifecycle()
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()

    // Plan editor overlay
    if (editingPlan != null) {
        BackHandler { viewModel.cancelEdit() }
        PcControlPlanEditorUI(viewModel = viewModel)
        return
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            NavigationPanel(
                viewModel   = viewModel,
                currentScreen = currentScreen,
                onNavigate  = { screen ->
                    viewModel.navigateTo(screen)
                    scope.launch { drawerState.close() }
                    when (screen) {
                        PcScreen.APP_DIRECTORY -> viewModel.loadInstalledApps()
                        PcScreen.FILE_BROWSER  -> viewModel.loadDrives()
                        else -> {}
                    }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                PcControlBottomNav(
                    currentScreen = currentScreen,
                    onNavigate    = { screen ->
                        viewModel.navigateTo(screen)
                        when (screen) {
                            PcScreen.APP_DIRECTORY -> viewModel.loadInstalledApps()
                            PcScreen.FILE_BROWSER  -> viewModel.loadDrives()
                            else -> {}
                        }
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AnimatedContent(
                    targetState  = currentScreen,
                    transitionSpec = {
                        fadeIn(tween(220)) + slideInHorizontally(tween(220)) togetherWith
                                fadeOut(tween(180)) + slideOutHorizontally(tween(180))
                    },
                    label = "screen"
                ) { screen ->
                    when (screen) {
                        PcScreen.TOUCHPAD      -> PcControlTouchpadUI(viewModel)
                        PcScreen.PLANS         -> PcControlPlansUI(viewModel)
                        PcScreen.APP_DIRECTORY -> PcControlAppDirectoryUI(viewModel)
                        PcScreen.FILE_BROWSER  -> PcControlFileBrowserUI(viewModel)
                        PcScreen.KEYBOARD      -> PcControlKeyboardUI(viewModel)
                        PcScreen.SETTINGS      -> PcControlSettingsUI(viewModel)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  NAVIGATION PANEL (drawer)
// ─────────────────────────────────────────────────────────────

@Composable
fun NavigationPanel(
    viewModel    : PcControlViewModel,
    currentScreen: PcScreen,
    onNavigate   : (PcScreen) -> Unit,
    onClose      : () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // Header
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(20.dp)
            ) {
                Column {
                    Text("IT Connect", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("PC Remote Control by Ritik Saini", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Nav items
            Text("NAVIGATE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            listOf(
                Triple(PcScreen.TOUCHPAD,       "Touchpad",    Icons.Default.Mouse),
                Triple(PcScreen.PLANS,         "Plans",       Icons.Default.List),
                Triple(PcScreen.KEYBOARD,       "Keyboard",    Icons.Default.Keyboard),
                Triple(PcScreen.APP_DIRECTORY,  "Apps",        Icons.Default.Apps),
                Triple(PcScreen.FILE_BROWSER,   "Files",       Icons.Default.Folder),
                Triple(PcScreen.SETTINGS,       "Settings",    Icons.Default.Settings),
            ).forEach { (screen, label, icon) ->
                NavigationDrawerItem(
                    label    = { Text(label, fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal) },
                    icon     = { Icon(icon, null) },
                    selected = currentScreen == screen,
                    onClick  = { onNavigate(screen) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Quick commands
            Text("QUICK COMMANDS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            listOf(
                Triple("🔒 Lock PC",    PcStep("SYSTEM_CMD", "LOCK"),     Icons.Default.Lock),
                Triple("😴 Sleep",      PcStep("SYSTEM_CMD", "SLEEP"),    Icons.Default.DarkMode),
                Triple("📸 Screenshot", PcStep("SYSTEM_CMD","SCREENSHOT"),Icons.Default.Screenshot),
                Triple("🔇 Mute",       PcStep("SYSTEM_CMD","MUTE"),      Icons.Default.VolumeOff),
            ).forEach { (label, step, icon) ->
                ListItem(
                    headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent  = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.clickable {
                        viewModel.executeQuickStep(step)
                        onClose()
                    }.padding(horizontal = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Connection info
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ListItem(
                headlineContent = {
                    Text(if (settings.pcIpAddress.isNotBlank())
                        settings.pcIpAddress else "Not configured",
                        style = MaterialTheme.typography.bodySmall)
                },
                overlineContent = { Text("PC IP Address") },
                leadingContent  = { Icon(Icons.Default.Computer, null, Modifier.size(20.dp)) },
                trailingContent = {
                    TextButton(onClick = { onNavigate(PcScreen.SETTINGS) }) {
                        Text("Change", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  BOTTOM NAVIGATION
//  Double-tap Touchpad icon → Keyboard screen
// ─────────────────────────────────────────────────────────────

data class PcNavItem(
    val screen: PcScreen,
    val label : String,
    val icon  : ImageVector,
    val selectedIcon: ImageVector = icon
)

private val navItems = listOf(
    //PcNavItem(PcScreen.PLANS,         "Plans",    Icons.Default.List),
    PcNavItem(PcScreen.TOUCHPAD,      "Control",  Icons.Default.Mouse),
    PcNavItem(PcScreen.APP_DIRECTORY, "Apps",     Icons.Default.Apps),
    PcNavItem(PcScreen.FILE_BROWSER,  "Files",    Icons.Default.Folder,    Icons.Default.FolderOpen),
    //PcNavItem(PcScreen.SETTINGS,      "Settings", Icons.Default.Settings),
)

@Composable
fun PcControlBottomNav(
    currentScreen: PcScreen,
    onNavigate   : (PcScreen) -> Unit,
    onOpenDrawer : () -> Unit
) {
    var lastTouchpadTap by remember { mutableLongStateOf(0L) }

    NavigationBar(tonalElevation = 8.dp) {
        // Menu button (opens drawer)
        NavigationBarItem(
            selected = false,
            onClick  = onOpenDrawer,
            icon     = { Icon(Icons.Default.Menu, "Menu") },
            label    = { Text("Menu", style = MaterialTheme.typography.labelSmall) }
        )

        navItems.forEach { item ->
            val selected = currentScreen == item.screen
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    if (item.screen == PcScreen.TOUCHPAD) {
                        val now = System.currentTimeMillis()
                        if (selected && now - lastTouchpadTap < 400L) {
                            onNavigate(PcScreen.KEYBOARD)
                        } else {
                            onNavigate(item.screen)
                        }
                        lastTouchpadTap = now
                    } else {
                        onNavigate(item.screen)
                    }
                },
                icon = {
                    if (item.screen == PcScreen.TOUCHPAD && selected) {
                        BadgedBox(badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("⌨", fontSize = 7.sp)
                            }
                        }) { Icon(item.selectedIcon, item.label) }
                    } else {
                        Icon(if (selected) item.selectedIcon else item.icon, item.label)
                    }
                },
                label = {
                    Text(item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}