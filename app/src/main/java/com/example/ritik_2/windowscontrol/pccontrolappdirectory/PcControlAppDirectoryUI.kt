package com.example.ritik_2.windowscontrol.pccontrolappdirectory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import com.example.ritik_2.windowscontrol.data.PcInstalledApp
import com.example.ritik_2.windowscontrol.data.PcRecentPath
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlAppDirectoryUI(viewModel: PcControlViewModel) {

    val apps        by viewModel.filteredApps.collectAsStateWithLifecycle()
    val recentPaths by viewModel.recentPaths.collectAsStateWithLifecycle()
    val isLoading   by viewModel.browseLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    var showRunning  by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(showRunning) {
        if (showRunning) viewModel.loadRunningApps()
        else viewModel.loadInstalledApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps", fontWeight = FontWeight.Bold) },
                actions = {
                    FilterChip(
                        selected = showRunning,
                        onClick  = { showRunning = !showRunning },
                        label    = {
                            Text(
                                if (showRunning) "● Running" else "All Apps",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = {
                        if (showRunning) viewModel.loadRunningApps()
                        else viewModel.loadInstalledApps()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setAppSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon  = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setAppSearchQuery("") }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AppListContent(
                apps        = apps,
                isLoading   = isLoading,
                searchQuery = searchQuery,
                showRunning = showRunning,
                isLandscape = isLandscape,
                onLaunch    = { app ->
                    viewModel.executeQuickStep(PcStep("LAUNCH_APP", app.exePath))
                },
                onKill      = { app ->
                    val killName = if (showRunning) app.exePath else app.name
                    viewModel.executeQuickStep(PcStep("KILL_APP", killName))
                },
                // Single button: tap to toggle minimize/maximize
                onToggleMinMax  = { app ->
                    viewModel.minimizeApp(app.exePath)  // toggles min/max
                },
                onForceMaximize = { app ->
                    viewModel.restoreApp(app.exePath)   // force maximize
                },
                onRetry     = { viewModel.loadInstalledApps() },
                recentPaths = recentPaths,
                onRecentClick = { recent ->
                    viewModel.executeQuickStep(PcStep("LAUNCH_APP", recent.path))
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SHARED APP LIST CONTENT
// ─────────────────────────────────────────────────────────────

@Composable
fun AppListContent(
    apps: List<PcInstalledApp>,
    isLoading: Boolean,
    searchQuery: String,
    showRunning: Boolean,
    isLandscape: Boolean,
    onLaunch: (PcInstalledApp) -> Unit,
    onKill: (PcInstalledApp) -> Unit,
    onToggleMinMax: (PcInstalledApp) -> Unit,
    onForceMaximize: (PcInstalledApp) -> Unit,
    onRetry: () -> Unit,
    recentPaths: List<PcRecentPath> = emptyList(),
    onRecentClick: ((PcRecentPath) -> Unit)? = null
) {
    if (apps.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📭", fontSize = 48.sp)
                Text(
                    if (searchQuery.isNotEmpty()) "No apps match \"$searchQuery\""
                    else "No apps found.\nMake sure agent_v10.py is running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (searchQuery.isEmpty()) {
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        return
    }

    val running    = apps.filter { it.isRunning }
    val notRunning = apps.filter { !it.isRunning }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Recent row — portrait only
        val recentApps = recentPaths.filter { it.isApp }
        if (recentApps.isNotEmpty() && searchQuery.isEmpty() && !isLandscape) {
            item {
                Text("🕐 RECENTLY USED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentApps) { recent ->
                        PcRecentChip(
                            recent  = recent,
                            onClick = { onRecentClick?.invoke(recent) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        if (running.isNotEmpty() && searchQuery.isEmpty()) {
            item {
                Text("● RUNNING (${running.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4ADE80),
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            items(running, key = { "run_${it.exePath}" }) { app ->
                PcAppItemCard(
                    app        = app,
                    onLaunch   = { onLaunch(app) },
                    onKill     = { onKill(app) },
                    onToggleMinMax = { onToggleMinMax(app) },
                    onForceMaximize = { onForceMaximize(app) }
                )
            }
            item {
                Text("📦 ALL APPS (${notRunning.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
        }

        items(notRunning, key = { it.exePath }) { app ->
            PcAppItemCard(
                app        = app,
                onLaunch   = { onLaunch(app) },
                onKill     = null,
                onToggleMinMax = null,
                onForceMaximize = null
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────
//  APP ITEM CARD — with minimize/restore buttons for running apps
// ─────────────────────────────────────────────────────────────

@Composable
fun PcAppItemCard(
    app: PcInstalledApp,
    onLaunch: () -> Unit,
    onKill: (() -> Unit)?,
    onToggleMinMax: (() -> Unit)?,
    onForceMaximize: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isMinimized by remember { mutableStateOf(false) }
    var lastToggleTap by remember { mutableLongStateOf(0L) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(app.icon, fontSize = 22.sp)
                if (app.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80))
                            .align(Alignment.TopEnd)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(app.exePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Smart minimize/maximize toggle — single tap toggles, double tap force maximize
                if (app.isRunning && onToggleMinMax != null) {
                    IconButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastToggleTap < 350L && onForceMaximize != null) {
                                // Double tap → force maximize
                                onForceMaximize()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isMinimized = false
                            } else {
                                // Single tap → toggle
                                onToggleMinMax()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isMinimized = !isMinimized
                            }
                            lastToggleTap = now
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isMinimized) Icons.Default.OpenInFull else Icons.Default.Minimize,
                            if (isMinimized) "Maximize" else "Minimize",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (onKill != null) {
                    OutlinedButton(
                        onClick = onKill,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(32.dp)
                    ) { Text("Kill", style = MaterialTheme.typography.labelSmall) }
                }

                Button(
                    onClick = onLaunch,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Run", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  RECENT CHIP
// ─────────────────────────────────────────────────────────────

@Composable
fun PcRecentChip(recent: PcRecentPath, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(recent.icon, fontSize = 16.sp)
            Text(
                recent.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}