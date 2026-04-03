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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    // Use LaunchedEffect to populate once on first load only — never reset on recomposition
    var ipText     by remember { mutableStateOf(settings.pcIpAddress) }
    var portText   by remember { mutableStateOf(settings.port.toString()) }
    var keyText    by remember { mutableStateOf(
        settings.secretKey.ifBlank { "Ritik@2002" }   // never start with empty key
    ) }
    var saved      by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    // Only sync from settings on first composition, not on every recomposition
    LaunchedEffect(Unit) {
        if (ipText.isBlank())  ipText   = settings.pcIpAddress
        if (keyText.isBlank()) keyText  = settings.secretKey.ifBlank { "Ritik@2002" }
    }

    Scaffold(
        topBar = {
            PcTopBar(
                title            = "Settings",
                connectionStatus = connectionStatus,
                onPing           = { viewModel.pingPc() },
                actions = {
                    PcConnectionChip(
                        status  = connectionStatus,
                        onClick = { viewModel.pingPc() }
                    )
                    Spacer(Modifier.width(6.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── STATUS CARD ──────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = when (connectionStatus) {
                        PcConnectionStatus.ONLINE   -> Color(0xFF22C55E).copy(0.13f)
                        PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.errorContainer
                        PcConnectionStatus.CHECKING -> Color(0xFFF59E0B).copy(0.13f)
                        PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
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
                            style      = MaterialTheme.typography.titleSmall
                        )
                        when (connectionStatus) {
                            PcConnectionStatus.OFFLINE -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "• Check PC and phone are on same WiFi\n" +
                                            "• Make sure agent_v5.py is running on PC\n" +
                                            "• Verify IP address below is correct\n" +
                                            "• Check Secret Key matches agent config",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            PcConnectionStatus.UNKNOWN -> {
                                if (settings.pcIpAddress.isBlank()) {
                                    Text(
                                        "⚠ No IP set — enter your PC's IP below",
                                        style      = MaterialTheme.typography.bodySmall,
                                        color      = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                    Button(
                        onClick        = { viewModel.pingPc() },
                        shape          = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── HOW TO FIND IP ───────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier            = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("How to find your PC's IP",
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.titleSmall)
                    Text(
                        "Win+R → type cmd → Enter → type ipconfig\n" +
                                "Look for: IPv4 Address  e.g. 10.10.201.113\n\n" +
                                "Make sure agent_v5.py is running on your PC.",
                        style      = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ── CONNECTION FIELDS ────────────────────────
            Text("CONNECTION",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)

            // IP Address
            OutlinedTextField(
                value         = ipText,
                onValueChange = { ipText = it; saved = false },
                label         = { Text("PC IP Address *") },
                placeholder   = { Text("e.g. 10.10.201.113") },
                leadingIcon   = { Icon(Icons.Default.Computer, null) },
                isError       = ipText.isBlank(),
                supportingText = {
                    if (ipText.isBlank())
                        Text("Required — get this from ipconfig on your PC",
                            color = MaterialTheme.colorScheme.error)
                    else
                        Text("Current: ${settings.pcIpAddress.ifBlank { "not saved yet" }}")
                },
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // Port + Secret Key on same row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = portText,
                    onValueChange = { portText = it; saved = false },
                    label         = { Text("Port") },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(14.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText  = { Text("Default: 5000") }
                )
                OutlinedTextField(
                    value         = keyText,
                    onValueChange = { keyText = it; saved = false },
                    label         = { Text("Secret Key") },
                    modifier      = Modifier.weight(1.6f),
                    shape         = RoundedCornerShape(14.dp),
                    singleLine    = true,
                    visualTransformation = if (keyVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon  = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide" else "Show"
                            )
                        }
                    },
                    supportingText = { Text("Must match agent SECRET_KEY") }
                )
            }

            // ── Quick-fill hint if key looks wrong ───────
            if (keyText != "Ritik@2002" && keyText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Text(
                            "Agent uses \"Ritik@2002\" — tap to auto-fill",
                            style  = MaterialTheme.typography.bodySmall,
                            color  = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { keyText = "Ritik@2002"; saved = false }) {
                            Text("Fix", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── SAVE BUTTON ──────────────────────────────
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 5000
                    viewModel.updateSettings(ipText.trim(), port, keyText.trim())
                    saved = true
                    viewModel.pingPc()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape   = RoundedCornerShape(14.dp),
                enabled = ipText.isNotBlank()
            ) {
                Icon(
                    if (saved) Icons.Default.Check else Icons.Default.Save,
                    null, Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (saved) "Saved! Testing connection…" else "Save & Connect",
                    fontWeight = FontWeight.Bold
                )
            }

            if (saved) {
                Text(
                    "✅ Settings saved. Check status card above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF22C55E)
                )
            }

            HorizontalDivider()

            // ── AGENT SETUP GUIDE ────────────────────────
            Text("AGENT SETUP ON PC",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "1" to "Install Python (python.org)",
                        "2" to "Open Command Prompt as Administrator",
                        "3" to "pip install flask pyautogui pynput psutil pywin32 pystray pillow",
                        "4" to "Copy agent_v5.py to your PC",
                        "5" to "python agent_v5.py",
                        "6" to "Agent shows IP in tray — click 'Show IP & Details'",
                        "7" to "Enter that IP above and tap Save & Connect"
                    ).forEach { (num, text) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.Top
                        ) {
                            Surface(
                                shape    = RoundedCornerShape(6.dp),
                                color    = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(
                                    modifier         = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text(text,
                                style    = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                        }
                    }

                    HorizontalDivider()

                    // Current connection summary
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier            = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Current saved connection:",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text("IP  : ${settings.pcIpAddress.ifBlank { "not set" }}",
                                style      = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace)
                            Text("Port: ${settings.port}",
                                style      = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace)
                            Text("Key : ${settings.secretKey}",
                                style      = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace)
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Auto-start at boot (run as Admin):\n" +
                                "python agent_v5.py --install",
                        style      = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}