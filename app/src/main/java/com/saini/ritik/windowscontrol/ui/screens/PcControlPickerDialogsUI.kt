package com.saini.ritik.windowscontrol.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saini.ritik.windowscontrol.data.*

// ─────────────────────────────────────────────────────────────
//  PcAppPickerDialog — Search & pick installed PC app
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcAppPickerDialog(
    apps: List<PcInstalledApp>,
    onPick: (PcInstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isEmpty()) apps
        else apps.filter { it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📦 Pick App from PC", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        text = {
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📭", fontSize = 36.sp)
                        Text("No apps loaded yet.\nCheck PC connection.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Running apps first
                    val running = filtered.filter { it.isRunning }
                    val rest = filtered.filter { !it.isRunning }

                    if (running.isNotEmpty()) {
                        item {
                            Text("● RUNNING", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                        items(running, key = { "r_${it.exePath}" }) { app ->
                            PcAppPickerRow(app = app, onPick = onPick)
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                        item {
                            Text("ALL APPS", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(rest, key = { it.exePath }) { app ->
                        PcAppPickerRow(app = app, onPick = onPick)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PcAppPickerRow(app: PcInstalledApp, onPick: (PcInstalledApp) -> Unit) {
    Surface(
        onClick = { onPick(app) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(app.icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.exePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (app.isRunning) {
                Text("●", color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PcFilePickerDialog — Browse PC drives/folders, pick a file
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcFilePickerDialog(
    drives: List<PcDrive>,
    dirItems: List<PcFileItem>,
    currentPath: String,
    filter: PcFileFilter,
    onBrowseDir: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentPath.isNotEmpty()) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("📁 Browse PC Files", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    if (currentPath.isNotEmpty()) {
                        Text(currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentPath.isEmpty()) {
                    // Show drives
                    if (drives.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center) {
                                Text("No drives found. Check PC connection.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(drives, key = { it.letter }) { drive ->
                            Surface(
                                onClick = { onBrowseDir("${drive.letter}:/") },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("💾", fontSize = 22.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${drive.letter}:\\ — ${drive.label}",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text("${drive.freeGb.toInt()} GB free",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Folders first
                    val folders = dirItems.filter { it.isDir }
                    val files = dirItems.filter { !it.isDir }

                    items(folders, key = { "d_${it.path}" }) { folder ->
                        Surface(
                            onClick = { onBrowseDir(folder.path) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📂", fontSize = 20.sp)
                                Text(folder.name, modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ChevronRight, null,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (files.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("${filter.label} Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        items(files, key = { "f_${it.path}" }) { file ->
                            val fileIcon: ImageVector = when (file.extension) {
                                in listOf("mp4","mkv","avi","mov") -> Icons.Default.Movie
                                in listOf("mp3","wav","flac") -> Icons.Default.MusicNote
                                in listOf("py","bat","ps1") -> Icons.Default.Code
                                in listOf("pdf") -> Icons.Default.PictureAsPdf
                                else -> Icons.Default.Description
                            }
                            Surface(
                                onClick = { onPick(file.path) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(fileIcon, null, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (file.sizeKb > 0) {
                                            val sz = if (file.sizeKb > 1024)
                                                "${file.sizeKb/1024} MB" else "${file.sizeKb} KB"
                                            Text(sz, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Icon(Icons.Default.Check, "Pick",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}