package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.network.PcControlApiClient
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel
import kotlinx.coroutines.launch

/**
 * Dedicated admin screen for master-key-gated agent operations.
 *
 * Previously embedded inside PcControlSettingsUI as MasterKeyAdminPanel.
 * Extracted into its own Activity so it:
 *   - doesn't clutter the normal settings screen
 *   - can be permission-gated separately (e.g. biometric prompt in Phase 2.3)
 *   - opens full-height with its own back-nav for the admin workflow
 *
 * All endpoints here hit the agent's master-key auth layer (PBKDF2 verified
 * server-side). The user-facing flow: enter master key → authenticate →
 * manage users, rotate secret key, view logs, push/rollback new agent code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlAdminSettingsUI(
    viewModel : PcControlViewModel,
    onBack    : () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var masterKey        by remember { mutableStateOf("ITConnect_Master_2024") }
    var masterKeyVisible by remember { mutableStateOf(false) }
    var isAuthed         by remember { mutableStateOf(false) }
    var authError        by remember { mutableStateOf("") }
    var connectedUsers   by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingUsers     by remember { mutableStateOf(false) }
    var showKeyChange    by remember { mutableStateOf(false) }
    var newSecretKey     by remember { mutableStateOf("") }
    var keyChangeMsg     by remember { mutableStateOf("") }
    var connLogs         by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showLogs         by remember { mutableStateOf(false) }

    val api = remember(settings) { PcControlApiClient(settings) }

    fun parseUsers(data: String?) = try {
        val j = org.json.JSONObject(data ?: "{}")
        val a = j.optJSONArray("connected_users") ?: org.json.JSONArray()
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            mapOf(
                "name"         to o.optString("name", "Unknown"),
                "ip"           to o.optString("ip", ""),
                "device_id"    to o.optString("device_id", ""),
                "connected_at" to o.optString("connected_at", ""),
                "last_seen"    to o.optString("last_seen", "")
            )
        }
    } catch (_: Exception) { emptyList<Map<String, Any>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.pcIpAddress.isBlank()) "No PC configured"
                            else "${settings.pcIpAddress}:${settings.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface,
                    titleContentColor = cs.onSurface
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "MASTER KEY ADMIN",
                style = MaterialTheme.typography.labelSmall,
                color = cs.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            // ── Authenticate ──────────────────────────────────────
            Surface(
                color = cs.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Master Key Authentication", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Default: ITConnect_Master_2024", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    OutlinedTextField(
                        value = masterKey,
                        onValueChange = { masterKey = it; isAuthed = false; authError = "" },
                        label = { Text("Master Key") },
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { masterKeyVisible = !masterKeyVisible }) {
                                Icon(if (masterKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        visualTransformation = if (masterKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (authError.isNotEmpty()) Text(authError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                    Button(
                        onClick = {
                            scope.launch {
                                loadingUsers = true
                                val r = api.getConnectedUsers(masterKey.trim())
                                if (r.success) {
                                    isAuthed = true; authError = ""
                                    connectedUsers = parseUsers(r.data)
                                } else {
                                    isAuthed = false; authError = "Failed: ${r.error}"
                                }
                                loadingUsers = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = masterKey.isNotBlank() && settings.pcIpAddress.isNotBlank()
                    ) {
                        Icon(if (isAuthed) Icons.Default.LockOpen else Icons.Default.Lock, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isAuthed) "Authenticated ✓" else "Authenticate", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (isAuthed) {
                // ── Connected users ──────────────────────────────
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Connected Users (${connectedUsers.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = {
                                scope.launch {
                                    loadingUsers = true
                                    val r = api.getConnectedUsers(masterKey.trim())
                                    if (r.success) connectedUsers = parseUsers(r.data)
                                    loadingUsers = false
                                }
                            }) { Icon(Icons.Default.Refresh, "Refresh", Modifier.size(16.dp)) }
                        }
                        if (loadingUsers) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (connectedUsers.isEmpty() && !loadingUsers)
                            Text("No devices connected", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        connectedUsers.forEach { user ->
                            Surface(
                                color = cs.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, cs.outline.copy(0.15f)),
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(user["name"]?.toString() ?: "Unknown", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${user["ip"]} • ${user["connected_at"]?.toString()?.take(16) ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val did = user["device_id"]?.toString() ?: return@OutlinedButton
                                            scope.launch {
                                                api.kickUser(masterKey.trim(), did)
                                                val r = api.getConnectedUsers(masterKey.trim())
                                                if (r.success) connectedUsers = parseUsers(r.data)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
                                    ) {
                                        Text("Kick", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Change secret key ────────────────────────────
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Change Secret Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Switch(checked = showKeyChange, onCheckedChange = { showKeyChange = it })
                        }
                        if (showKeyChange) {
                            Text("Changes SECRET_KEY on agent.", style = MaterialTheme.typography.bodySmall, color = cs.error)
                            OutlinedTextField(
                                value = newSecretKey,
                                onValueChange = { newSecretKey = it },
                                label = { Text("New Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                isError = newSecretKey.isNotBlank() && newSecretKey.length < 4
                            )
                            if (keyChangeMsg.isNotEmpty()) Text(
                                keyChangeMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (keyChangeMsg.startsWith("✓")) Color(0xFF22C55E) else cs.error
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        val r = api.changeSecretKey(masterKey.trim(), newSecretKey.trim())
                                        if (r.success) {
                                            keyChangeMsg = "✓ Changed: ${newSecretKey.trim().take(4)}***"
                                            viewModel.updateSettings(settings.pcIpAddress, settings.port, newSecretKey.trim())
                                            newSecretKey = ""
                                        } else keyChangeMsg = "Failed: ${r.error}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                enabled = newSecretKey.length >= 4,
                                colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                            ) {
                                Icon(Icons.Default.VpnKey, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Change Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // ── Connection logs ──────────────────────────────
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Connection Logs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = {
                                showLogs = !showLogs
                                if (showLogs) scope.launch {
                                    val r = api.getConnectionLogs(masterKey.trim())
                                    if (r.success) try {
                                        val j = org.json.JSONObject(r.data ?: "{}")
                                        val a = j.optJSONArray("logs") ?: org.json.JSONArray()
                                        connLogs = (0 until a.length()).map { i ->
                                            val o = a.getJSONObject(i)
                                            mapOf(
                                                "filename" to o.optString("filename"),
                                                "size_kb"  to o.optInt("size_kb", 0),
                                                "modified" to o.optString("modified", "")
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }) {
                                Text(if (showLogs) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (showLogs) {
                            if (connLogs.isEmpty()) Text("No logs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            connLogs.forEach { log ->
                                Surface(
                                    color = cs.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, cs.outline.copy(0.15f)),
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Description, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                                        Column(Modifier.weight(1f)) {
                                            Text(log["filename"]?.toString() ?: "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${log["size_kb"]} KB • ${log["modified"]?.toString()?.take(10) ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Agent self-update (Phase 1.4) ────────────────
                AgentUpdatePanel(api = api, masterKey = masterKey.trim())
            }
        }
    }
}
