package com.example.ritik_2.windowscontrol.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.windowscontrol.viewmodel.PcConnectionStatus
import com.example.ritik_2.windowscontrol.viewmodel.PcControlViewModel

// ─────────────────────────────────────────────────────────────
//  PcControlSettingsUI — Connection setup & preferences
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcControlSettingsUI(viewModel: PcControlViewModel) {

    val settings by viewModel.settings.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    var ip by remember(settings.pcIpAddress) { mutableStateOf(settings.pcIpAddress) }
    var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var secretKey by remember(settings.secretKey) { mutableStateOf(settings.secretKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Settings", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Setup instructions card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📋 Setup Instructions", fontWeight = FontWeight.Bold)
                    listOf(
                        "1. Run agent.py on your Windows PC",
                        "2. Note the IP address it prints",
                        "3. Enter that IP below",
                        "4. Both devices must be on same WiFi"
                    ).forEach { step ->
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // IP Address
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("PC IP Address") },
                placeholder = { Text("192.168.1.x") },
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Port + Secret Key
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Key") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // Connection status
            val (statusColor, statusText) = when (connectionStatus) {
                PcConnectionStatus.ONLINE   -> MaterialTheme.colorScheme.primary to "✅ PC is reachable!"
                PcConnectionStatus.OFFLINE  -> MaterialTheme.colorScheme.error to "❌ Cannot reach PC. Check IP & WiFi."
                PcConnectionStatus.CHECKING -> MaterialTheme.colorScheme.secondary to "🔄 Checking connection..."
                PcConnectionStatus.UNKNOWN  -> MaterialTheme.colorScheme.onSurfaceVariant to "● Not checked yet"
            }

            if (connectionStatus != PcConnectionStatus.UNKNOWN) {
                Text(statusText, color = statusColor, fontWeight = FontWeight.Medium)
            }

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.updateSettings(ip, port.toIntOrNull() ?: 5000, secretKey)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test")
                }
                Button(
                    onClick = {
                        viewModel.updateSettings(ip, port.toIntOrNull() ?: 5000, secretKey)
                    },
                    modifier = Modifier.weight(2f),
                    enabled = ip.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save & Connect", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider()

            // Package info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ℹ️ About pccontrol Package",
                        fontWeight = FontWeight.Bold)
                    Text("Version: 1.0.0", style = MaterialTheme.typography.bodySmall)
                    Text("Package: com.yourapp.pccontrol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Embed: PcControlMain.init(ctx, ip) → PcControlEntry()",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}