package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcInstalledApp
import com.example.ritik_2.windowscontrol.data.PcRecentPath
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PcControlAppDirectoryUI — Browse all installed PC apps
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlAppDirectoryUI(viewModel: PcControlViewModel) {

    val apps by viewModel.filteredApps.collectAsState()
    val recentPaths by viewModel.recentPaths.collectAsState()
    val isLoading by viewModel.browseLoading.collectAsState()
    val searchQuery by viewModel.appSearchQuery.collectAsState()

    // Callback for when an app is picked (for plan builder use)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 App Directory", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadInstalledApps() }) {
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
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setAppSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading apps from PC...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Recently used section
                if (recentPaths.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Text(
                            "🕐 RECENTLY USED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            items(recentPaths.filter { it.isApp }) { recent ->
                                PcRecentChip(
                                    recent = recent,
                                    onClick = {
                                        viewModel.executeQuickStep(
                                            PcStep("LAUNCH_APP", recent.path)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Running apps first
                val running = apps.filter { it.isRunning }
                val notRunning = apps.filter { !it.isRunning }

                if (running.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Text(
                            "● RUNNING (${running.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(running, key = { "run_${it.exePath}" }) { app ->
                        PcAppItemCard(
                            app = app,
                            onLaunch = {
                                viewModel.executeQuickStep(PcStep("LAUNCH_APP", app.exePath))
                            },
                            onKill = {
                                viewModel.executeQuickStep(PcStep("KILL_APP", app.name))
                            }
                        )
                    }
                    item {
                        Text(
                            "📦 ALL APPS (${notRunning.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                if (apps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📭", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Make sure agent.py is running on your PC",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(notRunning, key = { it.exePath }) { app ->
                        PcAppItemCard(
                            app = app,
                            onLaunch = {
                                viewModel.executeQuickStep(PcStep("LAUNCH_APP", app.exePath))
                            },
                            onKill = null
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  APP ITEM CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun PcAppItemCard(
    app: PcInstalledApp,
    onLaunch: () -> Unit,
    onKill: (() -> Unit)?
) {
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
            // App icon bubble
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(app.icon, fontSize = 24.sp)
                if (app.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80))
                            .align(Alignment.TopEnd)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.exePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onKill != null) {
                    OutlinedButton(
                        onClick = onKill,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Kill", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = onLaunch,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
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