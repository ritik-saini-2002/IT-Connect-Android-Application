package com.example.ritik_2.windowscontrol.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.data.PcDrive
import com.example.ritik_2.windowscontrol.data.PcFileFilter
import com.example.ritik_2.windowscontrol.data.PcFileItem
import com.example.ritik_2.windowscontrol.data.PcRecentPath
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PcControlFileBrowserUI — Browse PC drives, folders, files
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlFileBrowserUI(viewModel: PcControlViewModel) {

    val drives by viewModel.drives.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val dirItems by viewModel.dirItems.collectAsState()
    val isLoading by viewModel.browseLoading.collectAsState()
    val recentPaths by viewModel.recentPaths.collectAsState()

    var selectedFilter by remember { mutableStateOf(PcFileFilter.ALL) }

    // Back navigation
    BackHandler(enabled = currentPath.isNotEmpty()) {
        if (!viewModel.navigateUp()) { /* at root, let system handle */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                title = {
                    Column {
                        Text("📁 File Browser", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        if (currentPath.isNotEmpty()) {
                            Text(
                                currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
            // Filter chips (only show when inside a directory)
            if (currentPath.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PcFileFilter.values()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                selectedFilter = filter
                                viewModel.browseDir(currentPath, filter)
                            },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Browsing PC...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                // Show drives if at root
                if (currentPath.isEmpty()) {

                    // Recent paths
                    if (recentPaths.isNotEmpty()) {
                        item {
                            Text("🕐 RECENT", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                        items(recentPaths.filter { !it.isApp }) { recent ->
                            PcRecentPathRow(
                                recent = recent,
                                onClick = { viewModel.browseDir(recent.path) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    item {
                        Text("💾 DRIVES", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }

                    if (drives.isEmpty()) {
                        item {
                            PcEmptyBrowse("No drives found.\nMake sure agent.py is running.")
                        }
                    } else {
                        items(drives, key = { it.letter }) { drive ->
                            PcDriveCard(
                                drive = drive,
                                onClick = { viewModel.browseDir("${drive.letter}:/") }
                            )
                        }
                    }

                } else {
                    // Show dir contents
                    val folders = dirItems.filter { it.isDir }
                    val files = dirItems.filter { !it.isDir }

                    if (folders.isEmpty() && files.isEmpty()) {
                        item { PcEmptyBrowse("This folder is empty") }
                    }

                    if (folders.isNotEmpty()) {
                        item {
                            Text("📂 FOLDERS (${folders.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp))
                        }
                        items(folders, key = { "d_${it.path}" }) { folder ->
                            PcFileRow(
                                item = folder,
                                onClick = { viewModel.browseDir(folder.path, selectedFilter) },
                                onOpen = null
                            )
                        }
                    }

                    if (files.isNotEmpty()) {
                        item {
                            Text("📄 FILES (${files.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        items(files, key = { "f_${it.path}" }) { file ->
                            PcFileRow(
                                item = file,
                                onClick = null,
                                onOpen = {
                                    viewModel.executeQuickStep(
                                        PcStep(
                                            "SYSTEM_CMD", "OPEN_FOLDER",
                                            args = listOf(file.path)
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  DRIVE CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun PcDriveCard(drive: PcDrive, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("💾", fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${drive.letter}:\\ — ${drive.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                // Usage bar
                val usedFraction = if (drive.totalGb > 0)
                    1f - (drive.freeGb / drive.totalGb) else 0f
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (usedFraction > 0.9f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${drive.freeGb.toInt()} GB free of ${drive.totalGb.toInt()} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  FILE / FOLDER ROW
// ─────────────────────────────────────────────────────────────

@Composable
fun PcFileRow(
    item: PcFileItem,
    onClick: (() -> Unit)?,
    onOpen: (() -> Unit)?
) {
    val icon = when {
        item.isDir -> "📂"
        item.extension in listOf("mp4","mkv","avi","mov") -> "🎬"
        item.extension in listOf("mp3","wav","flac") -> "🎵"
        item.extension in listOf("pdf") -> "📕"
        item.extension in listOf("docx","doc") -> "📘"
        item.extension in listOf("pptx","ppt") -> "📊"
        item.extension in listOf("xlsx","xls") -> "📗"
        item.extension in listOf("py","bat","ps1") -> "⚙️"
        item.extension in listOf("jpg","png","jpeg") -> "🖼"
        item.extension in listOf("zip","rar","7z") -> "🗜"
        else -> "📄"
    }

    Card(
        onClick = { onClick?.invoke() ?: onOpen?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDir)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isDir) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDir && item.sizeKb > 0) {
                    val size = if (item.sizeKb > 1024)
                        "${item.sizeKb / 1024} MB" else "${item.sizeKb} KB"
                    Text(size, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.isDir) {
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            } else if (onOpen != null) {
                IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.OpenInNew, "Open on PC",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  RECENT PATH ROW
// ─────────────────────────────────────────────────────────────

@Composable
fun PcRecentPathRow(recent: PcRecentPath, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(recent.icon, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(recent.label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(recent.path, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PcEmptyBrowse(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}