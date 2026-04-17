package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlSettingsUI(viewModel: PcControlViewModel) {
    val settings         by viewModel.settings.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var ipText     by remember { mutableStateOf(settings.pcIpAddress) }
    var portText   by remember { mutableStateOf(settings.port.toString()) }
    var keyText    by remember { mutableStateOf(settings.secretKey.ifBlank { "Ritik@2002" }) }
    var saved      by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ipText.isBlank())  ipText  = settings.pcIpAddress
        if (keyText.isBlank()) keyText = settings.secretKey.ifBlank { "Ritik@2002" }
    }

    val (chipColor, chipLabel) = when (connectionStatus) {
        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
        PcConnectionStatus.UNKNOWN  -> cs.onSurface.copy(0.4f) to "Ping"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(cs.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Compact header (matches touchpad portrait bar) ───────
        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Settings, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
                Surface(onClick = { viewModel.pingPc() }, shape = RoundedCornerShape(10.dp), color = chipColor.copy(0.15f)) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(chipColor))
                        Text(chipLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = chipColor)
                    }
                }
            }
        }

        // ── STATUS CARD ──────────────────────────────
        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (connectionStatus) { PcConnectionStatus.ONLINE -> "✅"; PcConnectionStatus.OFFLINE -> "❌"; PcConnectionStatus.CHECKING -> "🔄"; PcConnectionStatus.UNKNOWN -> "❓" }, fontSize = 24.sp)
                Column(Modifier.weight(1f)) {
                    Text(when (connectionStatus) { PcConnectionStatus.ONLINE -> "Connected"; PcConnectionStatus.OFFLINE -> "Cannot reach PC"; PcConnectionStatus.CHECKING -> "Checking…"; PcConnectionStatus.UNKNOWN -> "Not checked" },
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (connectionStatus == PcConnectionStatus.OFFLINE) Text("Same WiFi? Agent running? Check IP & Key", style = MaterialTheme.typography.bodySmall, color = cs.error.copy(0.8f))
                }
                FilledTonalButton(onClick = { viewModel.pingPc() }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                    Text("Test", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── HOW TO FIND IP ───────────────────────────
        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How to find your PC's IP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("Win+R → cmd → ipconfig → IPv4 Address", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }

        // ── CONNECTION FIELDS ────────────────────────
        Text("CONNECTION", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 4.dp))

        if (isLandscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = ipText, onValueChange = { ipText = it; saved = false }, label = { Text("PC IP *") },
                    placeholder = { Text("10.10.201.113") }, leadingIcon = { Icon(Icons.Default.Computer, null) },
                    isError = ipText.isBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = portText, onValueChange = { portText = it; saved = false }, label = { Text("Port") },
                    modifier = Modifier.weight(0.5f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = keyText, onValueChange = { keyText = it; saved = false }, label = { Text("Secret Key") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { keyVisible = !keyVisible }) { Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
            }
        } else {
            OutlinedTextField(value = ipText, onValueChange = { ipText = it; saved = false }, label = { Text("PC IP Address *") },
                placeholder = { Text("e.g. 10.10.201.113") }, leadingIcon = { Icon(Icons.Default.Computer, null) },
                isError = ipText.isBlank(), supportingText = { if (ipText.isBlank()) Text("Required", color = cs.error) else Text("Current: ${settings.pcIpAddress.ifBlank { "not saved" }}") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = portText, onValueChange = { portText = it; saved = false }, label = { Text("Port") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Default: 5000") })
                OutlinedTextField(value = keyText, onValueChange = { keyText = it; saved = false }, label = { Text("Secret Key") },
                    modifier = Modifier.weight(1.6f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { keyVisible = !keyVisible }) { Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                    supportingText = { Text("Must match agent SECRET_KEY") })
            }
        }

        Button(onClick = { val port = portText.toIntOrNull() ?: 5000; viewModel.updateSettings(ipText.trim(), port, keyText.trim()); saved = true; viewModel.pingPc() },
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp), enabled = ipText.isNotBlank()
        ) { Icon(if (saved) Icons.Default.Check else Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (saved) "Saved! Testing…" else "Save & Connect", fontWeight = FontWeight.Bold) }

        if (saved) Text("Settings saved ✓", style = MaterialTheme.typography.bodySmall, color = Color(0xFF22C55E))

        HorizontalDivider(color = cs.outline.copy(0.25f))

        // ── AGENT SETUP ──────────────────────────────
        Text("AGENT SETUP", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 4.dp))

        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1" to "Install Python (python.org)", "2" to "pip install flask pyautogui pynput psutil pywin32 pystray pillow",
                    "3" to "Copy agent_v10.py → python agent_v10.py", "4" to "Enter IP above → Save & Connect"
                ).forEach { (num, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(6.dp), color = cs.primary.copy(0.15f), modifier = Modifier.size(20.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(num, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.primary) }
                        }
                        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = cs.outline.copy(0.15f))
                Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outline.copy(0.15f)), tonalElevation = 1.dp) {
                    Text("IP: ${settings.pcIpAddress.ifBlank { "—" }}  Port: ${settings.port}  Key: ${settings.secretKey.take(4)}***",
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                }
            }
        }

        HorizontalDivider(color = cs.outline.copy(0.25f))

        // ── MASTER KEY ADMIN ─────────────────────────
        Text("MASTER KEY ADMIN", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 4.dp))
        MasterKeyAdminPanel(viewModel = viewModel)
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  MASTER KEY ADMIN
// ─────────────────────────────────────────────────────────────
@Composable
private fun MasterKeyAdminPanel(viewModel: PcControlViewModel) {
    val scope = rememberCoroutineScope(); val settings by viewModel.settings.collectAsStateWithLifecycle(); val cs = MaterialTheme.colorScheme
    var masterKey by remember { mutableStateOf("ITConnect_Master_2024") }; var masterKeyVisible by remember { mutableStateOf(false) }
    var isAuthed by remember { mutableStateOf(false) }; var authError by remember { mutableStateOf("") }
    var connectedUsers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }; var loadingUsers by remember { mutableStateOf(false) }
    var showKeyChange by remember { mutableStateOf(false) }; var newSecretKey by remember { mutableStateOf("") }; var keyChangeMsg by remember { mutableStateOf("") }
    var connLogs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }; var showLogs by remember { mutableStateOf(false) }
    val api = remember(settings) { com.example.ritik_2.windowscontrol.network.PcControlApiClient(settings) }

    fun parseUsers(data: String?) = try { val j = org.json.JSONObject(data ?: "{}"); val a = j.optJSONArray("connected_users") ?: org.json.JSONArray()
        (0 until a.length()).map { i -> val o = a.getJSONObject(i); mapOf("name" to o.optString("name", "Unknown"), "ip" to o.optString("ip", ""), "device_id" to o.optString("device_id", ""), "connected_at" to o.optString("connected_at", ""), "last_seen" to o.optString("last_seen", "")) }
    } catch (_: Exception) { emptyList<Map<String, Any>>() }

    Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Master Key Authentication", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("Default: ITConnect_Master_2024", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            OutlinedTextField(value = masterKey, onValueChange = { masterKey = it; isAuthed = false; authError = "" }, label = { Text("Master Key") },
                leadingIcon = { Icon(Icons.Default.Key, null) }, trailingIcon = { IconButton(onClick = { masterKeyVisible = !masterKeyVisible }) { Icon(if (masterKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                visualTransformation = if (masterKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))
            if (authError.isNotEmpty()) Text(authError, style = MaterialTheme.typography.bodySmall, color = cs.error)
            Button(onClick = { scope.launch { loadingUsers = true; val r = api.getConnectedUsers(masterKey.trim()); if (r.success) { isAuthed = true; authError = ""; connectedUsers = parseUsers(r.data) } else { isAuthed = false; authError = "Failed: ${r.error}" }; loadingUsers = false } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = masterKey.isNotBlank() && settings.pcIpAddress.isNotBlank()) {
                Icon(if (isAuthed) Icons.Default.LockOpen else Icons.Default.Lock, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text(if (isAuthed) "Authenticated ✓" else "Authenticate", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (isAuthed) {
        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Connected Users (${connectedUsers.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { scope.launch { loadingUsers = true; val r = api.getConnectedUsers(masterKey.trim()); if (r.success) connectedUsers = parseUsers(r.data); loadingUsers = false } }) { Icon(Icons.Default.Refresh, "Refresh", Modifier.size(16.dp)) }
                }
                if (loadingUsers) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (connectedUsers.isEmpty() && !loadingUsers) Text("No devices connected", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                connectedUsers.forEach { user ->
                    Surface(color = cs.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.15f)), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user["name"]?.toString() ?: "Unknown", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("${user["ip"]} • ${user["connected_at"]?.toString()?.take(16) ?: ""}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { val did = user["device_id"]?.toString() ?: return@OutlinedButton; scope.launch { api.kickUser(masterKey.trim(), did); val r = api.getConnectedUsers(masterKey.trim()); if (r.success) connectedUsers = parseUsers(r.data) } },
                                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                                Text("Kick", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Change Secret Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall); Switch(checked = showKeyChange, onCheckedChange = { showKeyChange = it })
                }
                if (showKeyChange) {
                    Text("Changes SECRET_KEY on agent.", style = MaterialTheme.typography.bodySmall, color = cs.error)
                    OutlinedTextField(value = newSecretKey, onValueChange = { newSecretKey = it }, label = { Text("New Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), isError = newSecretKey.isNotBlank() && newSecretKey.length < 4)
                    if (keyChangeMsg.isNotEmpty()) Text(keyChangeMsg, style = MaterialTheme.typography.bodySmall, color = if (keyChangeMsg.startsWith("✓")) Color(0xFF22C55E) else cs.error)
                    Button(onClick = { scope.launch { val r = api.changeSecretKey(masterKey.trim(), newSecretKey.trim()); if (r.success) { keyChangeMsg = "✓ Changed: ${newSecretKey.trim().take(4)}***"; viewModel.updateSettings(settings.pcIpAddress, settings.port, newSecretKey.trim()); newSecretKey = "" } else keyChangeMsg = "Failed: ${r.error}" } },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = newSecretKey.length >= 4, colors = ButtonDefaults.buttonColors(containerColor = cs.error)) {
                        Icon(Icons.Default.VpnKey, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Change Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.25f)), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Connection Logs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showLogs = !showLogs; if (showLogs) scope.launch { val r = api.getConnectionLogs(masterKey.trim()); if (r.success) try { val j = org.json.JSONObject(r.data ?: "{}"); val a = j.optJSONArray("logs") ?: org.json.JSONArray(); connLogs = (0 until a.length()).map { i -> val o = a.getJSONObject(i); mapOf("filename" to o.optString("filename"), "size_kb" to o.optInt("size_kb", 0), "modified" to o.optString("modified", "")) } } catch (_: Exception) {} } }) {
                        Text(if (showLogs) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (showLogs) {
                    if (connLogs.isEmpty()) Text("No logs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    connLogs.forEach { log ->
                        Surface(color = cs.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, cs.outline.copy(0.15f)), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Description, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(log["filename"]?.toString() ?: "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${log["size_kb"]} KB • ${log["modified"]?.toString()?.take(10) ?: ""}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Self-update (Phase 1.4) — gated on master auth like the other admin panels above.
        AgentUpdatePanel(api = api, masterKey = masterKey.trim())
    }
}