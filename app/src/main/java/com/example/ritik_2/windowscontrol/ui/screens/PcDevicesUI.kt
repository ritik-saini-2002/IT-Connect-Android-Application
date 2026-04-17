package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.data.PcSavedDevice
import com.example.ritik_2.windowscontrol.network.PcLanScanner
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcDevicesUI(viewModel: PcControlViewModel) {
    val cs = MaterialTheme.colorScheme
    val devices     by viewModel.savedDevices.collectAsStateWithLifecycle()
    val scanning    by viewModel.scanning.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val current     by viewModel.settings.collectAsStateWithLifecycle()

    var savingAgent by remember { mutableStateOf<PcLanScanner.DiscoveredAgent?>(null) }
    var editing     by remember { mutableStateOf<PcSavedDevice?>(null) }
    var pendingDelete by remember { mutableStateOf<PcSavedDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ───────────────────────────────────────────────
        Surface(
            color    = cs.surfaceVariant,
            shape    = RoundedCornerShape(10.dp),
            border   = BorderStroke(1.dp, cs.outline.copy(0.25f)),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Devices, null, tint = cs.primary,
                    modifier = Modifier.size(18.dp))
                Text("Devices", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = cs.onSurface, modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        if (scanning) viewModel.stopLanScan()
                        else          viewModel.startLanScan()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Stop", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Radar, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan LAN", fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Main list ────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader("Saved Devices (${devices.size})")
            }
            if (devices.isEmpty()) {
                item { EmptyHint("No saved devices yet. Run a LAN scan below to find agents on your network.") }
            } else {
                items(devices, key = { it.id }) { device ->
                    SavedDeviceCard(
                        device    = device,
                        isActive  = device.host == current.pcIpAddress && device.port == current.port,
                        onConnect = { viewModel.connectToSaved(device) },
                        onEdit    = { editing = device },
                        onDelete  = { pendingDelete = device }
                    )
                }
            }

            if (scanning || scanResults.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionHeader("Discovered on LAN (${scanResults.size})") }
                if (scanResults.isEmpty() && scanning) {
                    item { EmptyHint("Broadcasting… agents will appear here as they reply.") }
                }
                items(scanResults, key = { "${it.ip}:${it.port}" }) { agent ->
                    DiscoveredAgentCard(
                        agent       = agent,
                        alreadySaved = devices.any { it.host == agent.ip && it.port == agent.port },
                        onSave      = { savingAgent = agent }
                    )
                }
            }
        }

        // ── Add manually ─────────────────────────────────────────
        OutlinedButton(
            onClick  = { editing = PcSavedDevice() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add device manually", fontSize = 13.sp)
        }
    }

    // Dialogs
    savingAgent?.let { agent ->
        SaveAgentDialog(
            agent     = agent,
            onCancel  = { savingAgent = null },
            onConfirm = { label, key, master ->
                viewModel.saveFromScan(agent, label, key, master)
                savingAgent = null
            }
        )
    }

    editing?.let { seed ->
        EditDeviceDialog(
            initial   = seed,
            onCancel  = { editing = null },
            onConfirm = { updated ->
                viewModel.saveDevice(updated)
                editing = null
            }
        )
    }

    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B)) },
            title   = { Text("Remove device?") },
            text    = { Text("'${d.label.ifBlank { d.host }}' will be deleted. Secret key is forgotten.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDevice(d); pendingDelete = null
                }) { Text("Remove", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  LIST ITEMS
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 12.sp,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color  = cs.surfaceVariant.copy(0.5f),
        shape  = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color    = cs.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun SavedDeviceCard(
    device   : PcSavedDevice,
    isActive : Boolean,
    onConnect: () -> Unit,
    onEdit   : () -> Unit,
    onDelete : () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (isActive) cs.primary else cs.outline.copy(0.25f)
    Surface(
        color    = cs.surface,
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(if (isActive) 2.dp else 1.dp, border),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onConnect)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(
                        if (device.isMaster) Color(0xFFFFB020).copy(0.15f)
                        else cs.primary.copy(0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (device.isMaster) Icons.Default.AdminPanelSettings else Icons.Default.Computer,
                    null,
                    tint = if (device.isMaster) Color(0xFFFFB020) else cs.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    device.label.ifBlank { device.pcName.ifBlank { device.host } },
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = cs.onSurface
                )
                Text(
                    "${device.host}:${device.port}" +
                            if (device.pcName.isNotBlank() && device.label.isNotBlank())
                                "  •  ${device.pcName}" else "",
                    fontSize = 11.sp, color = cs.onSurfaceVariant
                )
                if (isActive) {
                    Text("Active connection", fontSize = 10.sp,
                        color = cs.primary, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit",
                    modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove",
                    modifier = Modifier.size(18.dp), tint = Color(0xFFFF6B6B))
            }
        }
    }
}

@Composable
private fun DiscoveredAgentCard(
    agent       : PcLanScanner.DiscoveredAgent,
    alreadySaved: Boolean,
    onSave      : () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color  = cs.surfaceVariant.copy(0.6f),
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cs.outline.copy(0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Color(0xFF4ADE80).copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WifiTethering, null,
                    tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(agent.pcName,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
                Text("${agent.ip}:${agent.port}  •  v${agent.version}",
                    fontSize = 10.sp, color = cs.onSurfaceVariant)
                if (agent.connected > 0) {
                    Text("${agent.connected} user${if (agent.connected == 1) "" else "s"} connected",
                        fontSize = 10.sp, color = cs.primary)
                }
            }
            if (alreadySaved) {
                AssistChip(
                    onClick = onSave,
                    label   = { Text("Update", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                )
            } else {
                FilledTonalButton(
                    onClick = onSave,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  DIALOGS
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveAgentDialog(
    agent    : PcLanScanner.DiscoveredAgent,
    onCancel : () -> Unit,
    onConfirm: (label: String, key: String, isMaster: Boolean) -> Unit
) {
    var label     by remember { mutableStateOf(agent.pcName) }
    var key       by remember { mutableStateOf("") }
    var isMaster  by remember { mutableStateOf(false) }
    var keyVis    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        icon  = { Icon(Icons.Default.WifiTethering, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Save ${agent.pcName}") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${agent.ip}:${agent.port}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(if (isMaster) "Master key" else "Secret key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation =
                        if (keyVis) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVis = !keyVis }) {
                            Icon(
                                if (keyVis) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isMaster, onCheckedChange = { isMaster = it })
                    Spacer(Modifier.width(8.dp))
                    Text("This is an admin / master key",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, key, isMaster) },
                enabled = key.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeviceDialog(
    initial  : PcSavedDevice,
    onCancel : () -> Unit,
    onConfirm: (PcSavedDevice) -> Unit
) {
    var label  by remember { mutableStateOf(initial.label) }
    var host   by remember { mutableStateOf(initial.host) }
    var port   by remember { mutableStateOf(initial.port.toString()) }
    var stream by remember { mutableStateOf(initial.streamPort.toString()) }
    var key    by remember { mutableStateOf(initial.secretKey) }
    var isMaster by remember { mutableStateOf(initial.isMaster) }
    var keyVis by remember { mutableStateOf(false) }

    val isNew = initial.host.isBlank() && initial.secretKey.isBlank() && initial.label.isBlank()

    AlertDialog(
        onDismissRequest = onCancel,
        icon  = { Icon(Icons.Default.Computer, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (isNew) "Add device" else "Edit device") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("IP / Hostname") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stream, onValueChange = { stream = it.filter { c -> c.isDigit() } },
                        label = { Text("Stream") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(if (isMaster) "Master key" else "Secret key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation =
                        if (keyVis) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVis = !keyVis }) {
                            Icon(
                                if (keyVis) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isMaster, onCheckedChange = { isMaster = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Admin / master key",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(initial.copy(
                        label      = label,
                        host       = host,
                        port       = port.toIntOrNull() ?: 5000,
                        streamPort = stream.toIntOrNull() ?: 5001,
                        secretKey  = key,
                        isMaster   = isMaster
                    ))
                },
                enabled = host.isNotBlank() && key.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
