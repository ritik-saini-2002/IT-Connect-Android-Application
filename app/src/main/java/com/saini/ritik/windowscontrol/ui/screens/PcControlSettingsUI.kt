package com.saini.ritik.windowscontrol.ui.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.windowscontrol.viewmodel.PcConnectionStatus
import com.saini.ritik.windowscontrol.viewmodel.PcControlViewModel

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
        // Moved to its own Activity so the master-key flow has dedicated screen real
        // estate and can be permission-gated separately from regular settings.
        Text("MASTER KEY ADMIN", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 4.dp))
        val launcherContext = androidx.compose.ui.platform.LocalContext.current
        Surface(
            color = cs.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Agent administration",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Manage connected users, rotate the secret key, view connection logs, and push agent self-updates. Requires the master key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            launcherContext,
                            com.saini.ritik.windowscontrol.ui.screens.PcControlAdminSettingsActivity::class.java
                        )
                        launcherContext.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = settings.pcIpAddress.isNotBlank()
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Admin Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                if (settings.pcIpAddress.isBlank()) {
                    Text(
                        "Configure a PC IP above first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

