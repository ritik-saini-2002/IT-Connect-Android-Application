package com.example.ritik_2.windowscontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.theme.ITConnectGlass
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlSettingsUI(viewModel: PcControlViewModel) {

    val settings         by viewModel.settings.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    // Glass colors
    val bg1 = if (isDark) ITConnectGlass.darkBg1 else ITConnectGlass.lightBg1
    val bg2 = if (isDark) ITConnectGlass.darkBg2 else ITConnectGlass.lightBg2
    val glassBg = if (isDark) ITConnectGlass.darkGlassBg else ITConnectGlass.lightGlassBg
    val glassBorder = if (isDark) ITConnectGlass.darkGlassBorder else ITConnectGlass.lightGlassBorder
    val accent = if (isDark) ITConnectGlass.darkAccentBlue else ITConnectGlass.lightAccentBlue

    var ipText     by remember { mutableStateOf(settings.pcIpAddress) }
    var portText   by remember { mutableStateOf(settings.port.toString()) }
    var keyText    by remember { mutableStateOf(
        settings.secretKey.ifBlank { "Ritik@2002" }
    ) }
    var saved      by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ipText.isBlank())  ipText  = settings.pcIpAddress
        if (keyText.isBlank()) keyText = settings.secretKey.ifBlank { "Ritik@2002" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg1, bg2)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Glass header (replaces TopAppBar) ────────────────────
            Surface(
                color  = glassBg,
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Settings, null,
                        tint = accent, modifier = Modifier.size(20.dp))
                    Text("Settings", fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))

                    val (chipColor, chipLabel) = when (connectionStatus) {
                        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80) to "Online"
                        PcConnectionStatus.OFFLINE  -> Color(0xFFFF6B6B) to "Offline"
                        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24) to "..."
                        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurface.copy(0.4f) to "Ping"
                    }
                    Surface(
                        onClick = { viewModel.pingPc() },
                        shape   = RoundedCornerShape(12.dp),
                        color   = chipColor.copy(alpha = 0.15f),
                    ) {
                        Text("● $chipLabel",
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize   = 10.sp, fontWeight = FontWeight.Bold, color = chipColor)
                    }
                }
            }

            // ── STATUS CARD ──────────────────────────────
            Surface(
                color  = glassBg,
                shape  = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (connectionStatus) {
                            PcConnectionStatus.ONLINE   -> "✅"
                            PcConnectionStatus.OFFLINE  -> "❌"
                            PcConnectionStatus.CHECKING -> "🔄"
                            PcConnectionStatus.UNKNOWN  -> "❓"
                        }, fontSize = 28.sp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (connectionStatus) {
                                PcConnectionStatus.ONLINE   -> "Connected to PC"
                                PcConnectionStatus.OFFLINE  -> "Cannot reach PC"
                                PcConnectionStatus.CHECKING -> "Checking connection…"
                                PcConnectionStatus.UNKNOWN  -> "Not checked yet"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (connectionStatus == PcConnectionStatus.OFFLINE) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Check PC and phone are on same WiFi\n" +
                                        "Make sure agent_v10.py is running on PC\n" +
                                        "Verify IP address and Secret Key below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(0.8f)
                            )
                        }
                        if (connectionStatus == PcConnectionStatus.UNKNOWN && settings.pcIpAddress.isBlank()) {
                            Text("No IP set — enter your PC's IP below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    }
                    Button(
                        onClick = { viewModel.pingPc() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── HOW TO FIND IP ───────────────────────────
            Surface(
                color  = glassBg,
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How to find your PC's IP", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Win+R → type cmd → Enter → type ipconfig\n" +
                                "Look for: IPv4 Address  e.g. 10.10.201.113\n\n" +
                                "Make sure agent_v10.py is running on your PC.",
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── CONNECTION FIELDS ────────────────────────
            Text("CONNECTION",
                style = MaterialTheme.typography.labelMedium,
                color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp))

            OutlinedTextField(
                value = ipText, onValueChange = { ipText = it; saved = false },
                label = { Text("PC IP Address *") },
                placeholder = { Text("e.g. 10.10.201.113") },
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                isError = ipText.isBlank(),
                supportingText = {
                    if (ipText.isBlank()) Text("Required — get this from ipconfig on your PC",
                        color = MaterialTheme.colorScheme.error)
                    else Text("Current: ${settings.pcIpAddress.ifBlank { "not saved yet" }}")
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = portText, onValueChange = { portText = it; saved = false },
                    label = { Text("Port") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Default: 5000") }
                )
                OutlinedTextField(
                    value = keyText, onValueChange = { keyText = it; saved = false },
                    label = { Text("Secret Key") }, modifier = Modifier.weight(1.6f),
                    shape = RoundedCornerShape(14.dp), singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide" else "Show")
                        }
                    },
                    supportingText = { Text("Must match agent SECRET_KEY") }
                )
            }

            // ── SAVE BUTTON ──────────────────────────────
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 5000
                    viewModel.updateSettings(ipText.trim(), port, keyText.trim())
                    saved = true
                    viewModel.pingPc()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = ipText.isNotBlank()
            ) {
                Icon(if (saved) Icons.Default.Check else Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (saved) "Saved! Testing connection…" else "Save & Connect",
                    fontWeight = FontWeight.Bold)
            }

            if (saved) {
                Text("Settings saved. Check status card above.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF22C55E))
            }

            HorizontalDivider(color = glassBorder)

            // ── AGENT SETUP GUIDE ────────────────────────
            Text("AGENT SETUP ON PC",
                style = MaterialTheme.typography.labelMedium,
                color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp))

            Surface(
                color  = glassBg,
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "1" to "Install Python (python.org)",
                        "2" to "Open Command Prompt as Administrator",
                        "3" to "pip install flask pyautogui pynput psutil pywin32 pystray pillow",
                        "4" to "Copy agent_v10.py to your PC",
                        "5" to "python agent_v10.py",
                        "6" to "Agent shows IP in tray — click 'Show IP & Details'",
                        "7" to "Enter that IP above and tap Save & Connect"
                    ).forEach { (num, text) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(0.15f),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                                }
                            }
                            Text(text, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                        }
                    }

                    HorizontalDivider(color = glassBorder)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = glassBg,
                        border = BorderStroke(0.5.dp, glassBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Current saved connection:",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent, fontWeight = FontWeight.Bold)
                            Text("IP  : ${settings.pcIpAddress.ifBlank { "not set" }}",
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("Port: ${settings.port}",
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("Key : ${settings.secretKey.take(4)}${"*".repeat((settings.secretKey.length - 4).coerceAtLeast(0))}",
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            HorizontalDivider(color = glassBorder)

            // ── MASTER KEY ADMIN SECTION ─────────────────
            Text("MASTER KEY ADMIN",
                style = MaterialTheme.typography.labelMedium,
                color = accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp))

            MasterKeyAdminPanel(
                viewModel = viewModel,
                glassBg = glassBg,
                glassBorder = glassBorder,
                accent = accent
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MASTER KEY ADMIN PANEL
// ─────────────────────────────────────────────────────────────

@Composable
private fun MasterKeyAdminPanel(
    viewModel: PcControlViewModel,
    glassBg: Color,
    glassBorder: Color,
    accent: Color
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var masterKey       by remember { mutableStateOf("ITConnect_Master_2024") }
    var masterKeyVisible by remember { mutableStateOf(false) }
    var isAuthed        by remember { mutableStateOf(false) }
    var authError       by remember { mutableStateOf("") }

    // Connected users state
    var connectedUsers  by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingUsers    by remember { mutableStateOf(false) }

    // Key change state
    var showKeyChange   by remember { mutableStateOf(false) }
    var newSecretKey    by remember { mutableStateOf("") }
    var keyChangeMsg    by remember { mutableStateOf("") }

    // Connection logs state
    var connLogs        by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showLogs        by remember { mutableStateOf(false) }

    val api = remember(settings) {
        com.example.ritik_2.windowscontrol.network.PcControlApiClient(settings)
    }

    // ── Master Key Input ─────────────────────────────────
    Surface(
        color = glassBg, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, glassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Master Key Authentication",
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("Enter the master key to access admin controls.\nDefault: ITConnect_Master_2024",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = masterKey,
                onValueChange = { masterKey = it; isAuthed = false; authError = "" },
                label = { Text("Master Key") },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { masterKeyVisible = !masterKeyVisible }) {
                        Icon(if (masterKeyVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (masterKeyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp)
            )

            if (authError.isNotEmpty()) {
                Text(authError, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    scope.launch {
                        loadingUsers = true
                        val result = api.getConnectedUsers(masterKey.trim())
                        if (result.success) {
                            isAuthed = true; authError = ""
                            // Parse connected users
                            try {
                                val json = org.json.JSONObject(result.data ?: "{}")
                                val arr = json.optJSONArray("connected_users") ?: org.json.JSONArray()
                                connectedUsers = (0 until arr.length()).map { i ->
                                    val obj = arr.getJSONObject(i)
                                    mapOf(
                                        "name" to obj.optString("name", "Unknown"),
                                        "ip" to obj.optString("ip", ""),
                                        "device_id" to obj.optString("device_id", ""),
                                        "connected_at" to obj.optString("connected_at", ""),
                                        "last_seen" to obj.optString("last_seen", "")
                                    )
                                }
                            } catch (_: Exception) { connectedUsers = emptyList() }
                        } else {
                            isAuthed = false
                            authError = "Authentication failed: ${result.error}"
                        }
                        loadingUsers = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                enabled = masterKey.isNotBlank() && settings.pcIpAddress.isNotBlank()
            ) {
                Icon(if (isAuthed) Icons.Default.LockOpen else Icons.Default.Lock, null,
                    Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isAuthed) "Authenticated ✓" else "Authenticate",
                    fontWeight = FontWeight.Bold)
            }
        }
    }

    // ── Authenticated Admin Controls ─────────────────────
    if (isAuthed) {
        // ── Connected Users ──────────────────────────────
        Surface(
            color = glassBg, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, glassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connected Users (${connectedUsers.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = {
                        scope.launch {
                            loadingUsers = true
                            val r = api.getConnectedUsers(masterKey.trim())
                            if (r.success) {
                                try {
                                    val json = org.json.JSONObject(r.data ?: "{}")
                                    val arr = json.optJSONArray("connected_users") ?: org.json.JSONArray()
                                    connectedUsers = (0 until arr.length()).map { i ->
                                        val obj = arr.getJSONObject(i)
                                        mapOf(
                                            "name" to obj.optString("name", "Unknown"),
                                            "ip" to obj.optString("ip", ""),
                                            "device_id" to obj.optString("device_id", ""),
                                            "connected_at" to obj.optString("connected_at", ""),
                                            "last_seen" to obj.optString("last_seen", "")
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                            loadingUsers = false
                        }
                    }) { Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(18.dp)) }
                }

                if (loadingUsers) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (connectedUsers.isEmpty() && !loadingUsers) {
                    Text("No devices currently connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                connectedUsers.forEach { user ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null,
                                tint = Color(0xFF22C55E), modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user["name"]?.toString() ?: "Unknown",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("IP: ${user["ip"]}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Since: ${user["connected_at"]?.toString()?.take(19) ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Kick button
                            OutlinedButton(
                                onClick = {
                                    val deviceId = user["device_id"]?.toString() ?: return@OutlinedButton
                                    scope.launch {
                                        api.kickUser(masterKey.trim(), deviceId)
                                        // Refresh list
                                        val r = api.getConnectedUsers(masterKey.trim())
                                        if (r.success) {
                                            try {
                                                val json = org.json.JSONObject(r.data ?: "{}")
                                                val arr = json.optJSONArray("connected_users") ?: org.json.JSONArray()
                                                connectedUsers = (0 until arr.length()).map { i ->
                                                    val obj = arr.getJSONObject(i)
                                                    mapOf("name" to obj.optString("name"), "ip" to obj.optString("ip"),
                                                        "device_id" to obj.optString("device_id"),
                                                        "connected_at" to obj.optString("connected_at"),
                                                        "last_seen" to obj.optString("last_seen"))
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Kick", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ── Change Secret Key ────────────────────────────
        Surface(
            color = glassBg, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, glassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Change Secret Key", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Switch(checked = showKeyChange, onCheckedChange = { showKeyChange = it })
                }

                if (showKeyChange) {
                    Text("This changes the SECRET_KEY on the agent.\nAll connected users will need the new key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)

                    OutlinedTextField(
                        value = newSecretKey,
                        onValueChange = { newSecretKey = it },
                        label = { Text("New Secret Key") },
                        placeholder = { Text("Min 4 characters") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        isError = newSecretKey.isNotBlank() && newSecretKey.length < 4
                    )

                    if (keyChangeMsg.isNotEmpty()) {
                        Text(keyChangeMsg, style = MaterialTheme.typography.bodySmall,
                            color = if (keyChangeMsg.startsWith("✓")) Color(0xFF22C55E)
                            else MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val r = api.changeSecretKey(masterKey.trim(), newSecretKey.trim())
                                if (r.success) {
                                    keyChangeMsg = "✓ Secret key changed to: ${newSecretKey.trim().take(4)}***"
                                    // Also update local settings
                                    viewModel.updateSettings(
                                        settings.pcIpAddress, settings.port, newSecretKey.trim()
                                    )
                                    newSecretKey = ""
                                } else {
                                    keyChangeMsg = "Failed: ${r.error}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = newSecretKey.length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.VpnKey, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change Key on Agent", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Connection Logs ──────────────────────────────
        Surface(
            color = glassBg, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, glassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connection Logs", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        showLogs = !showLogs
                        if (showLogs) {
                            scope.launch {
                                val r = api.getConnectionLogs(masterKey.trim())
                                if (r.success) {
                                    try {
                                        val json = org.json.JSONObject(r.data ?: "{}")
                                        val arr = json.optJSONArray("logs") ?: org.json.JSONArray()
                                        connLogs = (0 until arr.length()).map { i ->
                                            val obj = arr.getJSONObject(i)
                                            mapOf(
                                                "filename" to obj.optString("filename"),
                                                "size_kb" to obj.optInt("size_kb", 0),
                                                "modified" to obj.optString("modified", "")
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }) {
                        Text(if (showLogs) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (showLogs) {
                    if (connLogs.isEmpty()) {
                        Text("No log files found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    connLogs.forEach { log ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Description, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(log["filename"]?.toString() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium)
                                    Text("${log["size_kb"]} KB • ${log["modified"]?.toString()?.take(10) ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}