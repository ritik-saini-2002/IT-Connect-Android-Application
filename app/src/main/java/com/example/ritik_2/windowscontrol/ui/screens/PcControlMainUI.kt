package com.example.ritik_2.windowscontrol.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.pccontrolappdirectory.PcControlAppDirectoryUI
import com.example.ritik_2.windowscontrol.pcfilebrowser.PcFileBrowserCompat
import com.example.ritik_2.windowscontrol.pctouchpad.PcControlTouchpadUI
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import com.example.ritik_2.windowscontrol.viewmodel.PcScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlMainScreen(viewModel: PcControlViewModel, isLoggedIn: Boolean = true) {
    val currentScreen     by viewModel.currentScreen.collectAsStateWithLifecycle()
    val editingPlan       by viewModel.editingPlan.collectAsStateWithLifecycle()
    val showLoginRequired by viewModel.showLoginRequired.collectAsStateWithLifecycle()
    val drawerState       = rememberDrawerState(DrawerValue.Closed)
    val scope             = rememberCoroutineScope()
    val cfg               = LocalConfiguration.current
    val isLandscape       = cfg.screenWidthDp > cfg.screenHeightDp
    val context           = LocalContext.current

    // ── Windows Project popup state ──
    var showProjectPopup by remember { mutableStateOf(false) }

    // ── System bars: hide in landscape touchpad for full immersive mode ──
    LaunchedEffect(currentScreen, isLandscape) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isLandscape && currentScreen == PcScreen.TOUCHPAD) {
            // Full immersive: hide status bar + nav bar, swipe from edge to reveal
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (editingPlan != null) {
        BackHandler { viewModel.cancelEdit() }
        PcControlPlanEditorUI(viewModel = viewModel)
        return
    }

    // ── Windows Project Popup ──
    if (showProjectPopup) {
        WindowsProjectPopup(
            onDismiss = { showProjectPopup = false },
            onSelect  = { step ->
                viewModel.executeQuickStep(step)
                showProjectPopup = false
            }
        )
    }

    fun navigateTo(screen: PcScreen) {
        viewModel.navigateTo(screen, isLoggedIn)
        if (isLoggedIn || screen in viewModel.guestScreens) {
            when (screen) {
                PcScreen.APP_DIRECTORY -> viewModel.loadInstalledApps()
                PcScreen.FILE_BROWSER  -> viewModel.loadDrives()
                else                   -> {}
            }
        }
    }

    // ── Login required dialog ──
    if (showLoginRequired) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLoginRequired() },
            title   = { Text("Login Required") },
            text    = { Text("Please log in to use this feature.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissLoginRequired()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                }) { Text("Log In") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLoginRequired() }) { Text("Cancel") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            NavigationPanel(
                viewModel     = viewModel,
                currentScreen = currentScreen,
                onNavigate    = { screen ->
                    navigateTo(screen)
                    scope.launch { drawerState.close() }
                },
                onClose    = { scope.launch { drawerState.close() } },
                isLoggedIn = isLoggedIn
            )
        }
    ) {
        if (isLandscape) {
            // ── LANDSCAPE: content + nav rail on right ──
            // FIX: Bottom bar / nav rail is ALWAYS visible, including for touchpad.
            // This was the user's main complaint — touchpad hid the navigation.
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState   = currentScreen,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                        label          = "screen"
                    ) { screen -> ScreenContent(screen, viewModel) }
                }

                // Nav rail with Windows Project button
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header   = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                ) {
                    Spacer(Modifier.weight(0.3f))

                    railItems.forEach { item ->
                        val selected = currentScreen == item.screen
                        NavigationRailItem(
                            selected = selected,
                            onClick  = { navigateTo(item.screen) },
                            icon     = { Icon(if (selected) item.selectedIcon else item.icon, item.label) },
                            label    = {
                                Text(item.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontSize   = 10.sp)
                            }
                        )
                    }

                    Spacer(Modifier.weight(0.3f))

                    // Windows Project button — landscape only, between nav items
                    NavigationRailItem(
                        selected = false,
                        onClick  = { showProjectPopup = true },
                        icon     = { Icon(Icons.Default.CastConnected, "Project") },
                        label    = { Text("Project", fontSize = 9.sp, style = MaterialTheme.typography.labelSmall) }
                    )

                    Spacer(Modifier.weight(0.4f))
                }
            }
        } else {
            // Portrait: bottom nav bar
            Scaffold(
                bottomBar = {
                    PcControlBottomNav(
                        currentScreen    = currentScreen,
                        onNavigate       = { navigateTo(it) },
                        onOpenDrawer     = { scope.launch { drawerState.open() } },
                        onFilesDoubleTap = { viewModel.toggleFileBrowserMode() }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    AnimatedContent(
                        targetState   = currentScreen,
                        transitionSpec = {
                            fadeIn(tween(220)) + slideInHorizontally(tween(220)) togetherWith
                                    fadeOut(tween(180)) + slideOutHorizontally(tween(180))
                        },
                        label = "screen"
                    ) { screen -> ScreenContent(screen, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: PcScreen, viewModel: PcControlViewModel) {
    when (screen) {
        PcScreen.TOUCHPAD      -> PcControlTouchpadUI(viewModel)
        PcScreen.PLANS         -> PcControlPlansUI(viewModel)
        PcScreen.APP_DIRECTORY -> PcControlAppDirectoryUI(viewModel)
        PcScreen.FILE_BROWSER  -> PcFileBrowserCompat(viewModel)
        PcScreen.KEYBOARD      -> PcControlKeyboardUI(viewModel)
        PcScreen.SETTINGS      -> PcControlSettingsUI(viewModel)
    }
}

// ─────────────────────────────────────────────────────────────
//  WINDOWS PROJECT POPUP — display projection via displayswitch
// ─────────────────────────────────────────────────────────────

@Composable
private fun WindowsProjectPopup(
    onDismiss: () -> Unit,
    onSelect: (PcStep) -> Unit
) {
    val options = listOf(
        Triple("PC Screen Only",    "💻", PcStep("SYSTEM_CMD", "DISPLAY_INTERNAL")),
        Triple("Duplicate",         "🔁", PcStep("SYSTEM_CMD", "DISPLAY_CLONE")),
        Triple("Extend",            "↔️", PcStep("SYSTEM_CMD", "DISPLAY_EXTEND")),
        Triple("Second Screen Only","📺", PcStep("SYSTEM_CMD", "DISPLAY_EXTERNAL")),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.CastConnected, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Display Projection", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select display mode (uses displayswitch.exe on the PC).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { (label, emoji, step) ->
                    Surface(
                        onClick  = { onSelect(step) },
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(emoji, fontSize = 24.sp)
                            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight, null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────
//  NAV ITEMS
// ─────────────────────────────────────────────────────────────

private val railItems = listOf(
    PcNavItem(PcScreen.TOUCHPAD,      "Control",  Icons.Default.Mouse),
    PcNavItem(PcScreen.PLANS,         "Plans",    Icons.AutoMirrored.Filled.List),
    PcNavItem(PcScreen.APP_DIRECTORY, "Apps",     Icons.Default.Apps),
    PcNavItem(PcScreen.FILE_BROWSER,  "Files",    Icons.Default.Folder, Icons.Default.FolderOpen),
    PcNavItem(PcScreen.KEYBOARD,      "Keys",     Icons.Default.Keyboard),
    PcNavItem(PcScreen.SETTINGS,      "Settings", Icons.Default.Settings),
)

@Composable
fun NavigationPanel(
    viewModel    : PcControlViewModel,
    currentScreen: PcScreen,
    onNavigate   : (PcScreen) -> Unit,
    onClose      : () -> Unit,
    isLoggedIn   : Boolean = true
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("IT Connect",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Text("PC Remote Control v3.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(0.75f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("NAVIGATE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf(
                Triple(PcScreen.TOUCHPAD,      "Control",  Icons.Default.Mouse),
                Triple(PcScreen.PLANS,         "Plans",    Icons.AutoMirrored.Filled.List),
                Triple(PcScreen.KEYBOARD,      "Keyboard", Icons.Default.Keyboard),
                Triple(PcScreen.APP_DIRECTORY, "Apps",     Icons.Default.Apps),
                Triple(PcScreen.FILE_BROWSER,  "Files",    Icons.Default.Folder),
                Triple(PcScreen.SETTINGS,      "Settings", Icons.Default.Settings),
            ).forEach { (screen, label, icon) ->
                val isLocked = !isLoggedIn && screen !in viewModel.guestScreens
                NavigationDrawerItem(
                    label    = { Text(label, fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal) },
                    icon     = { Icon(if (isLocked) Icons.Default.Lock else icon, null) },
                    selected = currentScreen == screen,
                    onClick  = { onNavigate(screen) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("QUICK COMMANDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf(
                Triple("Lock PC",    PcStep("SYSTEM_CMD","LOCK"),       Icons.Default.Lock),
                Triple("Sleep",      PcStep("SYSTEM_CMD","SLEEP"),      Icons.Default.DarkMode),
                Triple("Screenshot", PcStep("SYSTEM_CMD","SCREENSHOT"), Icons.Default.Screenshot),
                Triple("Mute",       PcStep("SYSTEM_CMD","MUTE"),       Icons.AutoMirrored.Filled.VolumeOff),
            ).forEach { (label, step, icon) ->
                ListItem(
                    headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent  = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
                    modifier        = Modifier.clickable { viewModel.executeQuickStep(step); onClose() }.padding(horizontal = 8.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ListItem(
                headlineContent = {
                    Text(if (settings.pcIpAddress.isNotBlank()) settings.pcIpAddress else "Not configured",
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

data class PcNavItem(
    val screen      : PcScreen,
    val label       : String,
    val icon        : ImageVector,
    val selectedIcon: ImageVector = icon
)

private val navItems = listOf(
    PcNavItem(PcScreen.TOUCHPAD,      "Control", Icons.Default.Mouse),
    PcNavItem(PcScreen.APP_DIRECTORY, "Apps",    Icons.Default.Apps),
    PcNavItem(PcScreen.FILE_BROWSER,  "Files",   Icons.Default.Folder, Icons.Default.FolderOpen),
)

@Composable
fun PcControlBottomNav(
    currentScreen    : PcScreen,
    onNavigate       : (PcScreen) -> Unit,
    onOpenDrawer     : () -> Unit,
    onFilesDoubleTap : () -> Unit = {}
) {
    var lastTouchpadTap by remember { mutableLongStateOf(0L) }
    var lastFilesTap    by remember { mutableLongStateOf(0L) }

    NavigationBar(tonalElevation = 8.dp) {
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
                    when (item.screen) {
                        PcScreen.TOUCHPAD -> {
                            val now = System.currentTimeMillis()
                            if (selected && now - lastTouchpadTap < 400L) onNavigate(PcScreen.KEYBOARD)
                            else onNavigate(item.screen)
                            lastTouchpadTap = now
                        }
                        PcScreen.FILE_BROWSER -> {
                            val now = System.currentTimeMillis()
                            if (selected && now - lastFilesTap < 400L) onFilesDoubleTap()
                            else onNavigate(item.screen)
                            lastFilesTap = now
                        }
                        else -> onNavigate(item.screen)
                    }
                },
                icon = {
                    if (item.screen == PcScreen.TOUCHPAD && selected) {
                        BadgedBox(badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("K", fontSize = 7.sp)
                            }
                        }) { Icon(item.selectedIcon, item.label) }
                    } else {
                        Icon(if (selected) item.selectedIcon else item.icon, item.label)
                    }
                },
                label = {
                    Text(item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style      = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}