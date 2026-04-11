package com.example.ritik_2.windowscontrol.pccontrolappdirectory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
    var showRunning by remember { mutableStateOf(false) }
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    LaunchedEffect(showRunning) {
        if (showRunning) viewModel.loadRunningApps() else viewModel.loadInstalledApps()
    }

    Scaffold(
        topBar = {
            if (isLandscape) {
                // Compact landscape bar
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Apps", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        FilterChip(selected = showRunning, onClick = { showRunning = !showRunning },
                            label = { Text(if (showRunning) "● Running" else "All", style = MaterialTheme.typography.labelSmall) })
                        IconButton(onClick = { if (showRunning) viewModel.loadRunningApps() else viewModel.loadInstalledApps() }, Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, "Refresh", Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("Apps", fontWeight = FontWeight.Bold) },
                    actions = {
                        FilterChip(selected = showRunning, onClick = { showRunning = !showRunning },
                            label = { Text(if (showRunning) "● Running" else "All Apps", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 4.dp))
                        IconButton(onClick = { if (showRunning) viewModel.loadRunningApps() else viewModel.loadInstalledApps() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { viewModel.setAppSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isLandscape) 10.dp else 16.dp, vertical = if (isLandscape) 6.dp else 12.dp),
                placeholder = { Text("Search apps...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setAppSearchQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
                shape = RoundedCornerShape(10.dp), singleLine = true)
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            AppListContent(apps = apps, isLoading = isLoading, searchQuery = searchQuery, showRunning = showRunning, isLandscape = isLandscape,
                onLaunch = { app -> viewModel.executeQuickStep(PcStep("LAUNCH_APP", app.exePath)) },
                onKill = { app -> viewModel.executeQuickStep(PcStep("KILL_APP", if (showRunning) app.exePath else app.name)) },
                onToggleMinMax = { app -> viewModel.minimizeApp(app.exePath) },
                onForceMaximize = { app -> viewModel.restoreApp(app.exePath) },
                onRetry = { viewModel.loadInstalledApps() },
                recentPaths = recentPaths,
                onRecentClick = { recent -> viewModel.executeQuickStep(PcStep("LAUNCH_APP", recent.path)) })
        }
    }
}

@Composable
fun AppListContent(
    apps: List<PcInstalledApp>, isLoading: Boolean, searchQuery: String, showRunning: Boolean, isLandscape: Boolean,
    onLaunch: (PcInstalledApp) -> Unit, onKill: (PcInstalledApp) -> Unit,
    onToggleMinMax: (PcInstalledApp) -> Unit, onForceMaximize: (PcInstalledApp) -> Unit,
    onRetry: () -> Unit, recentPaths: List<PcRecentPath> = emptyList(), onRecentClick: ((PcRecentPath) -> Unit)? = null
) {
    if (apps.isEmpty() && !isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📭", fontSize = 48.sp)
                Text(if (searchQuery.isNotEmpty()) "No apps match \"$searchQuery\"" else "No apps found.\nMake sure agent is running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (searchQuery.isEmpty()) Button(onClick = onRetry) { Text("Retry") }
            }
        }
        return
    }

    val running = apps.filter { it.isRunning }; val notRunning = apps.filter { !it.isRunning }

    if (isLandscape) {
        // Landscape: 2-column grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (running.isNotEmpty() && searchQuery.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("● RUNNING (${running.size})", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80), modifier = Modifier.padding(vertical = 2.dp))
                }
                items(running, key = { "run_${it.exePath}" }) { app ->
                    PcAppItemCard(app = app, onLaunch = { onLaunch(app) }, onKill = { onKill(app) },
                        onToggleMinMax = { onToggleMinMax(app) }, onForceMaximize = { onForceMaximize(app) }, compact = true)
                }
                item(span = { GridItemSpan(2) }) {
                    Text("📦 ALL (${notRunning.size})", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                }
            }
            items(notRunning, key = { it.exePath }) { app ->
                PcAppItemCard(app = app, onLaunch = { onLaunch(app) }, onKill = null, onToggleMinMax = null, onForceMaximize = null, compact = true)
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(60.dp)) }
        }
    } else {
        // Portrait: single column
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val recentApps = recentPaths.filter { it.isApp }
            if (recentApps.isNotEmpty() && searchQuery.isEmpty()) {
                item { Text("🕐 RECENTLY USED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp)) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(recentApps) { recent -> PcRecentChip(recent = recent, onClick = { onRecentClick?.invoke(recent) }) } } }
                item { Spacer(Modifier.height(4.dp)) }
            }
            if (running.isNotEmpty() && searchQuery.isEmpty()) {
                item { Text("● RUNNING (${running.size})", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4ADE80), modifier = Modifier.padding(vertical = 4.dp)) }
                items(running, key = { "run_${it.exePath}" }) { app ->
                    PcAppItemCard(app = app, onLaunch = { onLaunch(app) }, onKill = { onKill(app) },
                        onToggleMinMax = { onToggleMinMax(app) }, onForceMaximize = { onForceMaximize(app) })
                }
                item { Text("📦 ALL APPS (${notRunning.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
            }
            items(notRunning, key = { it.exePath }) { app ->
                PcAppItemCard(app = app, onLaunch = { onLaunch(app) }, onKill = null, onToggleMinMax = null, onForceMaximize = null)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  APP ITEM CARD — buttons ordered: Run → Kill → Min/Max (at end)
//  Min/Max is bigger (36dp) and spaced away from Kill to prevent
//  accidental presses. Single tap toggles, double tap force max.
// ─────────────────────────────────────────────────────────────

@Composable
fun PcAppItemCard(
    app: PcInstalledApp, onLaunch: () -> Unit, onKill: (() -> Unit)?,
    onToggleMinMax: (() -> Unit)?, onForceMaximize: (() -> Unit)?,
    compact: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    var isMinimized by remember { mutableStateOf(false) }
    var lastToggleTap by remember { mutableLongStateOf(0L) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            // App icon
            Box(Modifier.size(if (compact) 38.dp else 48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Text(app.icon, fontSize = if (compact) 18.sp else 22.sp)
                if (app.isRunning) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)).align(Alignment.TopEnd))
            }
            // App info
            Column(Modifier.weight(1f)) {
                Text(app.name, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(app.exePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Buttons: Run first, then Kill, then Min/Max at the END with spacing
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Run button
                Button(onClick = onLaunch, contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.height(if (compact) 28.dp else 32.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(if (compact) 12.dp else 14.dp))
                    if (!compact) { Spacer(Modifier.width(2.dp)); Text("Run", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                }
                // Kill button
                if (onKill != null) {
                    OutlinedButton(onClick = onKill, contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.height(if (compact) 28.dp else 32.dp)) {
                        Text("Kill", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                // Min/Max toggle — AT THE END, bigger touch target, spaced away
                if (app.isRunning && onToggleMinMax != null) {
                    Spacer(Modifier.width(4.dp)) // extra gap to separate from Kill
                    Surface(
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastToggleTap < 350L && onForceMaximize != null) {
                                onForceMaximize(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); isMinimized = false
                            } else {
                                onToggleMinMax(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); isMinimized = !isMinimized
                            }
                            lastToggleTap = now
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMinimized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f)),
                        tonalElevation = 2.dp,
                        modifier = Modifier.size(if (compact) 32.dp else 36.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isMinimized) Icons.Default.OpenInFull else Icons.Default.Minimize,
                                if (isMinimized) "Maximize" else "Minimize",
                                modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PcRecentChip(recent: PcRecentPath, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(recent.icon, fontSize = 14.sp); Text(recent.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}