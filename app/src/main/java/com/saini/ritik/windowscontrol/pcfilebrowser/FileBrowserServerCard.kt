package com.saini.ritik.windowscontrol.pcfilebrowser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saini.ritik.theme.ITConnectGlass

// ─────────────────────────────────────────────────────────────
//  Server credential data model (saved locally via SharedPrefs)
// ─────────────────────────────────────────────────────────────

data class SavedServerCredential(
    val id       : String = "",
    val label    : String = "",
    val address  : String = "",      // e.g. "192.168.1.100"
    val shareName: String = "",      // e.g. "SharedFolder"
    val username : String = "",
    val password : String = "",
    val domain   : String = ".",
    val isConnected: Boolean = false
)

// ─────────────────────────────────────────────────────────────
//  Server Section — shown below Drives in File Browser root
// ─────────────────────────────────────────────────────────────

@Composable
fun ServerSection(
    servers          : List<SavedServerCredential>,
    onConnectServer  : (SavedServerCredential) -> Unit,
    onAddServer      : () -> Unit,
    onEditServer     : (SavedServerCredential) -> Unit,
    onDeleteServer   : (SavedServerCredential) -> Unit,
    modifier         : Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) ITConnectGlass.darkAccentTeal else ITConnectGlass.lightAccentTeal
    val glassBg = if (isDark) ITConnectGlass.darkGlassBg else ITConnectGlass.lightGlassBg
    val glassBorder = if (isDark) ITConnectGlass.darkGlassBorder else ITConnectGlass.lightGlassBorder

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Section header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Cloud, null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Network Servers", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, color = accent)
            }
            Surface(
                onClick = onAddServer,
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(0.15f),
                border = BorderStroke(0.5.dp, accent.copy(0.3f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(14.dp))
                    Text("Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
        }

        if (servers.isEmpty()) {
            // Empty state
            Surface(
                color = glassBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = accent.copy(0.5f),
                        modifier = Modifier.size(32.dp))
                    Text("No servers configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add an SMB/network share to transfer files\nbetween server and your PC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        } else {
            servers.forEach { server ->
                ServerCard(
                    server    = server,
                    onConnect = { onConnectServer(server) },
                    onEdit    = { onEditServer(server) },
                    onDelete  = { onDeleteServer(server) }
                )
            }
        }
    }
}

@Composable
private fun ServerCard(
    server    : SavedServerCredential,
    onConnect : () -> Unit,
    onEdit    : () -> Unit,
    onDelete  : () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val glassBg = if (isDark) ITConnectGlass.darkGlassBg else ITConnectGlass.lightGlassBg
    val glassBorder = if (isDark) ITConnectGlass.darkGlassBorder else ITConnectGlass.lightGlassBorder
    val accent = if (isDark) ITConnectGlass.darkAccentTeal else ITConnectGlass.lightAccentTeal

    Surface(
        onClick = onConnect,
        color = glassBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, if (server.isConnected) accent.copy(0.5f) else glassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server icon
            Box(
                Modifier.size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (server.isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                    null, tint = accent, modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    server.label.ifBlank { server.address },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "\\\\${server.address}\\${server.shareName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (server.isConnected) {
                    Text("● Connected", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF22C55E))
                }
            }

            // Edit button
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(0.7f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Server Credential Popup Dialog
// ─────────────────────────────────────────────────────────────

@Composable
fun ServerCredentialDialog(
    existing  : SavedServerCredential? = null,
    onSave    : (SavedServerCredential) -> Unit,
    onDismiss : () -> Unit
) {
    var label     by remember { mutableStateOf(existing?.label ?: "") }
    var address   by remember { mutableStateOf(existing?.address ?: "") }
    var share     by remember { mutableStateOf(existing?.shareName ?: "") }
    var username  by remember { mutableStateOf(existing?.username ?: "") }
    var password  by remember { mutableStateOf(existing?.password ?: "") }
    var domain    by remember { mutableStateOf(existing?.domain ?: ".") }
    var showPass  by remember { mutableStateOf(false) }

    val isEdit = existing != null
    val canSave = address.isNotBlank() && share.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isEdit) Icons.Default.Edit else Icons.Default.CloudQueue,
                null, tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (isEdit) "Edit Server" else "Add Network Server",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter SMB/network share credentials to browse and transfer files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. Office NAS") },
                    leadingIcon = { Icon(Icons.Default.Label, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Server Address *") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = { Icon(Icons.Default.Computer, null) },
                    isError = address.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = share, onValueChange = { share = it },
                    label = { Text("Share Name *") },
                    placeholder = { Text("SharedFolder") },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    isError = share.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true, shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it },
                        label = { Text("Domain") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true, shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility, null
                            )
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(SavedServerCredential(
                        id        = existing?.id ?: System.currentTimeMillis().toString(),
                        label     = label.trim(),
                        address   = address.trim(),
                        shareName = share.trim(),
                        username  = username.trim(),
                        password  = password,
                        domain    = domain.trim().ifBlank { "." }
                    ))
                },
                enabled = canSave
            ) {
                Text(if (isEdit) "Save" else "Add Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────
//  Server destination folder chooser
//  Used when transferring files between server and PC
// ─────────────────────────────────────────────────────────────

@Composable
fun DestinationFolderDialog(
    title       : String = "Choose Destination",
    currentPath : String,
    folders     : List<String>,
    isLoading   : Boolean,
    onNavigate  : (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelect    : (String) -> Unit,
    onDismiss   : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.7f),
        icon = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Current path
                if (currentPath.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onNavigateUp, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                // Folder list
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(folders) { folder ->
                        Surface(
                            onClick = { onNavigate(folder) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                                Text(
                                    folder.substringAfterLast("/").substringAfterLast("\\"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(currentPath) },
                enabled = currentPath.isNotBlank()
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select This Folder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────
//  Shared Prefs helper for saving server credentials
// ─────────────────────────────────────────────────────────────

object ServerCredentialStore {
    private const val PREFS_NAME = "file_browser_servers"

    fun save(context: android.content.Context, servers: List<SavedServerCredential>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(servers)
        prefs.edit().putString("servers", json).apply()
    }

    fun load(context: android.content.Context): List<SavedServerCredential> {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString("servers", "[]") ?: "[]"
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<SavedServerCredential>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    fun delete(context: android.content.Context, serverId: String) {
        val current = load(context).filter { it.id != serverId }
        save(context, current)
    }
}