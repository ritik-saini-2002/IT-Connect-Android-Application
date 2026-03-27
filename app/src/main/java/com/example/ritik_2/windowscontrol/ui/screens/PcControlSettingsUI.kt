package com.example.ritik_2.windowscontrol.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlSettingsUI(viewModel: PcControlViewModel) {

    val settings         by viewModel.settings.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    var ipText    by remember(settings.pcIpAddress) { mutableStateOf(settings.pcIpAddress) }
    var portText  by remember(settings.port)        { mutableStateOf(settings.port.toString()) }
    var keyText   by remember(settings.secretKey)   { mutableStateOf(settings.secretKey) }
    var saved     by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                expandedHeight = 21.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {

            // ── STATUS CARD ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionStatus) {
                        PcConnectionStatus.ONLINE   -> Color(0xFF4ADE80).copy(0.15f)
                        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.errorContainer
                        PcConnectionStatus.CHECKING -> Color(0xFFFBBF24).copy(0.15f)
                        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
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
                                PcConnectionStatus.CHECKING -> "Checking connection..."
                                PcConnectionStatus.UNKNOWN  -> "Not checked yet"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (connectionStatus == PcConnectionStatus.OFFLINE) {
                            Text(
                                "Make sure:\n" +
                                        "1. PC and phone are on same WiFi\n" +
                                        "2. agent_v5.py is running on PC\n" +
                                        "3. IP address below is correct",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        if (settings.pcIpAddress.isBlank()) {
                            Text(
                                "⚠ No IP set — enter your PC's IP address below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
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

            // ── HOW TO FIND IP ──────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How to find your PC's IP",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        "On your PC: Press Win+R → type cmd → press Enter\n" +
                                "Then type:  ipconfig\n" +
                                "Look for:   IPv4 Address (e.g. 192.168.1.5)\n\n" +
                                "Make sure agent_v5.py is running on your PC first.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── CONNECTION FIELDS ────────────────────────────
            Text("CONNECTION", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it; saved = false },
                label = { Text("PC IP Address *") },
                placeholder = { Text("e.g. 192.168.1.5") },
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                isError = ipText.isBlank(),
                supportingText = {
                    if (ipText.isBlank())
                        Text("Required — app cannot connect without this",
                            color = MaterialTheme.colorScheme.error)
                    else
                        Text("Enter the IPv4 address shown by ipconfig on your PC")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it; saved = false },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Default: 5000") }
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; saved = false },
                    label = { Text("Secret Key") },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    supportingText = { Text("Must match agent SECRET_KEY") }
                )
            }

            // ── SAVE BUTTON ──────────────────────────────────
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
                Icon(if (saved) Icons.Default.Check else Icons.Default.Save,
                    null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (saved) "Saved! Testing connection..." else "Save & Connect",
                    fontWeight = FontWeight.Bold
                )
            }

            if (saved) {
                Text(
                    "✅ Settings saved. Check status card above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4ADE80)
                )
            }

            HorizontalDivider()

            // ── AGENT SETUP GUIDE ────────────────────────────
            Text("AGENT SETUP ON PC", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    listOf(
                        "1" to "Install Python on PC (python.org)",
                        "2" to "Open Command Prompt as Administrator",
                        "3" to "Run: pip install flask pyautogui pynput psutil pywin32 pystray pillow",
                        "4" to "Copy agent_v5.py to your PC",
                        "5" to "Run: python agent_v5.py",
                        "6" to "Agent will show your PC's IP address",
                        "7" to "Enter that IP above and tap Save & Connect"
                    ).forEach { (num, text) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text(text, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "For auto-start at boot (run as Admin):\n" +
                                "python agent_v5.py --install",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(0.dp))
        }
    }
}