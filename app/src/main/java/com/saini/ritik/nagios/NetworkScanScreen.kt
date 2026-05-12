package com.saini.ritik.nagios

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.NetworkInterface

// ─────────────────────────────────────────────────────────────
//  DATA MODELS
// ─────────────────────────────────────────────────────────────

private data class ArpEntry(
    val ip    : String,
    val mac   : String,
    val iface : String,
    val state : String
)

private enum class ScanState { IDLE, SCANNING, DONE, ERROR }
private enum class LookupMode { MAC_TO_IP, IP_TO_MAC }

// ─────────────────────────────────────────────────────────────
//  NETWORK SCAN SCREEN (inside Nagios)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScanScreen(modifier: Modifier = Modifier) {

    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Mode
    var mode by remember { mutableStateOf(LookupMode.MAC_TO_IP) }

    // Input
    var input    by remember { mutableStateOf("") }
    var inputErr by remember { mutableStateOf<String?>(null) }

    // Scan state
    var scanState    by remember { mutableStateOf(ScanState.IDLE) }
    var arpTable     by remember { mutableStateOf<List<ArpEntry>>(emptyList()) }
    var result       by remember { mutableStateOf<String?>(null) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var totalHosts   by remember { mutableIntStateOf(0) }
    var errorMsg     by remember { mutableStateOf("") }
    var snackMsg     by remember { mutableStateOf<String?>(null) }

    // Extra subnets for multi-VLAN scanning
    var extraSubnets by remember { mutableStateOf("") }

    val snackState = remember { SnackbarHostState() }

    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackState.showSnackbar(it); snackMsg = null }
    }

    // ── Helpers ──────────────────────────────────────────────

    fun normalizeMAC(raw: String): String =
        raw.trim().uppercase().replace("-", ":").replace(".", ":")

    fun isValidMAC(mac: String): Boolean =
        mac.matches(Regex("([0-9A-Fa-f]{2}[:\\-.]){5}[0-9A-Fa-f]{2}"))

    fun isValidIP(ip: String): Boolean =
        ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) &&
                ip.split(".").all { it.toIntOrNull() in 0..255 }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Network", text))
        snackMsg = "Copied: $text"
    }

    fun getSubnet(): String? {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return null
            val b = byteArrayOf(
                (ip and 0xFF).toByte(),
                (ip shr 8 and 0xFF).toByte(),
                (ip shr 16 and 0xFF).toByte(),
                (ip shr 24 and 0xFF).toByte()
            )
            "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}"
        } catch (_: Exception) { null }
    }

    fun getDeviceSubnets(): List<String> {
        val subnets = mutableSetOf<String>()
        try {
            val wifi = getSubnet()
            if (wifi != null) subnets.add(wifi)
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
                nic.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                        val parts = addr.hostAddress!!.split(".")
                        if (parts.size == 4) subnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                    }
                }
            }
        } catch (_: Exception) {}
        // Add user-provided extra subnets
        extraSubnets.split(",", " ", "\n")
            .map { it.trim() }
            .filter { it.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) }
            .forEach { subnets.add(it) }
        return subnets.toList()
    }

    fun readArpCache(): List<ArpEntry> {
        val entries = mutableListOf<ArpEntry>()
        try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.trim().split(Regex("\\s+"))
                if (parts.size >= 6) {
                    val mac = parts[3].uppercase()
                    if (mac != "00:00:00:00:00:00") {
                        entries.add(ArpEntry(
                            ip    = parts[0],
                            mac   = mac,
                            iface = parts[5],
                            state = when (parts[2]) {
                                "0x2" -> "reachable"
                                "0x4" -> "stale"
                                "0x0" -> "incomplete"
                                else  -> "unknown"
                            }
                        ))
                    }
                }
            }
            reader.close()
        } catch (_: Exception) {}
        return entries
    }

    suspend fun pingSweepAll(subnets: List<String>, onProgress: (Int, Int) -> Unit) {
        val allHosts = subnets.flatMap { sub -> (1..254).map { "$sub.$it" } }
        totalHosts = allHosts.size
        withContext(Dispatchers.IO) {
            allHosts.forEachIndexed { idx, host ->
                try { InetAddress.getByName(host).isReachable(300) } catch (_: Exception) {}
                onProgress(idx + 1, allHosts.size)
            }
        }
    }

    // ── Search logic ─────────────────────────────────────────

    fun doSearch() {
        keyboard?.hide()
        result   = null
        inputErr = null

        when (mode) {
            LookupMode.MAC_TO_IP -> {
                val normalized = normalizeMAC(input)
                if (!isValidMAC(normalized)) {
                    inputErr = "Invalid MAC. Use AA:BB:CC:DD:EE:FF"
                    return
                }
                scope.launch {
                    scanState = ScanState.SCANNING; scannedCount = 0

                    // Check ARP cache first
                    val cached = readArpCache()
                    arpTable = cached
                    val hit = cached.find { it.mac == normalized }
                    if (hit != null) {
                        result = hit.ip; scanState = ScanState.DONE; return@launch
                    }

                    // Ping sweep all subnets
                    val subnets = withContext(Dispatchers.IO) { getDeviceSubnets() }
                    if (subnets.isEmpty()) {
                        errorMsg = "No network subnets detected. Check WiFi connection."
                        scanState = ScanState.ERROR; return@launch
                    }

                    pingSweepAll(subnets) { done, total ->
                        scannedCount = done; totalHosts = total
                    }

                    delay(500)
                    val fresh = readArpCache()
                    arpTable = fresh
                    result = fresh.find { it.mac == normalized }?.ip
                    scanState = ScanState.DONE
                }
            }

            LookupMode.IP_TO_MAC -> {
                val ip = input.trim()
                if (!isValidIP(ip)) {
                    inputErr = "Invalid IP. Use format like 192.168.1.100"
                    return
                }
                scope.launch {
                    scanState = ScanState.SCANNING; scannedCount = 0

                    // Ping the target first to populate ARP
                    withContext(Dispatchers.IO) {
                        try { InetAddress.getByName(ip).isReachable(2000) } catch (_: Exception) {}
                    }
                    delay(300)

                    val cached = readArpCache()
                    arpTable = cached
                    val hit = cached.find { it.ip == ip }
                    if (hit != null) {
                        result = hit.mac; scanState = ScanState.DONE; return@launch
                    }

                    // Ping again harder via system ping
                    withContext(Dispatchers.IO) {
                        try {
                            val p = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "3", "-W", "2", ip))
                            p.waitFor()
                        } catch (_: Exception) {}
                    }
                    delay(500)

                    val fresh = readArpCache()
                    arpTable = fresh
                    result = fresh.find { it.ip == ip }?.mac
                    scanState = ScanState.DONE
                }
            }
        }
    }

    fun reset() {
        input = ""; inputErr = null; result = null
        arpTable = emptyList(); scanState = ScanState.IDLE
        scannedCount = 0; extraSubnets = ""
    }

    // ── UI ───────────────────────────────────────────────────

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1976D2), Color(0xFF1565C0))),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Wifi, null, tint = Color.White,
                                modifier = Modifier.size(28.dp))
                            Text("Network Scanner", fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("IP \u2194 MAC bidirectional lookup across VLANs",
                            fontSize = 12.sp, color = Color.White.copy(0.8f))
                    }
                }
            }

            // Mode toggle
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == LookupMode.MAC_TO_IP,
                        onClick  = { mode = LookupMode.MAC_TO_IP; reset() },
                        label    = { Text("MAC \u2192 IP", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Router, null, Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == LookupMode.IP_TO_MAC,
                        onClick  = { mode = LookupMode.IP_TO_MAC; reset() },
                        label    = { Text("IP \u2192 MAC", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.SettingsEthernet, null, Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Input field
            item {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it; inputErr = null },
                    label = { Text(if (mode == LookupMode.MAC_TO_IP) "MAC Address" else "IP Address") },
                    placeholder = { Text(
                        if (mode == LookupMode.MAC_TO_IP) "e.g. AA:BB:CC:DD:EE:FF"
                        else "e.g. 192.168.1.100"
                    ) },
                    leadingIcon = {
                        Icon(
                            if (mode == LookupMode.MAC_TO_IP) Icons.Default.Router
                            else Icons.Default.Language,
                            null
                        )
                    },
                    isError        = inputErr != null,
                    supportingText = {
                        inputErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            ?: Text(
                                if (mode == LookupMode.MAC_TO_IP)
                                    "Formats: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF"
                                else "Enter a valid IPv4 address",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    },
                    modifier    = Modifier.fillMaxWidth(),
                    shape       = RoundedCornerShape(14.dp),
                    singleLine  = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (mode == LookupMode.MAC_TO_IP)
                            KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                        keyboardType   = if (mode == LookupMode.IP_TO_MAC)
                            KeyboardType.Number else KeyboardType.Ascii,
                        imeAction      = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    trailingIcon = {
                        if (input.isNotEmpty())
                            IconButton(onClick = { input = ""; inputErr = null }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                    }
                )
            }

            // Extra subnets for multi-VLAN (only in MAC→IP mode)
            if (mode == LookupMode.MAC_TO_IP) {
                item {
                    OutlinedTextField(
                        value         = extraSubnets,
                        onValueChange = { extraSubnets = it },
                        label         = { Text("Extra subnets (optional)") },
                        placeholder   = { Text("e.g. 10.0.1, 172.16.0, 192.168.2") },
                        leadingIcon   = { Icon(Icons.Default.AccountTree, null) },
                        supportingText = {
                            Text("Comma-separated subnet prefixes for multi-VLAN scan",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier   = Modifier.fillMaxWidth(),
                        shape      = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            }

            // Search button
            item {
                Button(
                    onClick  = { doSearch() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = input.isNotBlank() && scanState != ScanState.SCANNING
                ) {
                    if (scanState == ScanState.SCANNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Scanning network\u2026")
                    } else {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (mode == LookupMode.MAC_TO_IP) "Find IP" else "Find MAC",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Progress
            if (scanState == ScanState.SCANNING && mode == LookupMode.MAC_TO_IP) {
                item {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Sweeping subnets\u2026",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("$scannedCount / $totalHosts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = {
                                    if (totalHosts > 0) scannedCount.toFloat() / totalHosts else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(5.dp)
                            )
                        }
                    }
                }
            }

            // Result
            if (scanState == ScanState.DONE || scanState == ScanState.ERROR) {
                item {
                    when {
                        scanState == ScanState.ERROR -> ErrorCard(errorMsg)

                        result != null -> {
                            val isMAC = mode == LookupMode.MAC_TO_IP
                            Card(
                                shape  = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF22C55E).copy(0.12f))
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null,
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(28.dp))
                                        Column {
                                            Text("Device Found!",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color(0xFF15803D))
                                            Text(
                                                if (isMAC) "MAC matched on your network"
                                                else "IP resolved from ARP cache",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF22C55E).copy(0.3f))

                                    // Input row
                                    NetResultRow(
                                        label = if (isMAC) "MAC Address" else "IP Address",
                                        value = if (isMAC) normalizeMAC(input) else input.trim(),
                                        icon  = Icons.Default.Router
                                    )

                                    // Result row with copy
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            if (isMAC) Icons.Default.Language
                                            else Icons.Default.SettingsEthernet,
                                            null, tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (isMAC) "Current IP Address"
                                                else "MAC Address",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(result!!,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                        FilledTonalButton(
                                            onClick = { copyToClipboard(result!!) },
                                            shape   = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, null,
                                                Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy",
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        else -> NotFoundCard(mode)
                    }
                }
            }

            // ARP table
            if (arpTable.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text("ARP CACHE  (${arpTable.size} entries)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            scope.launch {
                                arpTable = withContext(Dispatchers.IO) { readArpCache() }
                                snackMsg = "ARP table refreshed"
                            }
                        }) {
                            Text("Refresh", fontSize = 11.sp)
                        }
                    }
                }

                items(arpTable, key = { "${it.ip}_${it.mac}" }) { entry ->
                    val isMatch = when (mode) {
                        LookupMode.MAC_TO_IP -> entry.mac == normalizeMAC(input)
                        LookupMode.IP_TO_MAC -> entry.ip == input.trim()
                    }
                    ArpEntryRow(entry, isMatch) { copyToClipboard("${entry.ip}  ${entry.mac}") }
                }
            }

            // Quick scan button to just populate ARP table
            if (scanState == ScanState.IDLE && arpTable.isEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                scanState = ScanState.SCANNING
                                val subnets = withContext(Dispatchers.IO) { getDeviceSubnets() }
                                if (subnets.isNotEmpty()) {
                                    pingSweepAll(subnets) { done, total ->
                                        scannedCount = done; totalHosts = total
                                    }
                                    delay(500)
                                    arpTable = readArpCache()
                                }
                                scanState = ScanState.IDLE
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Radar, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Network & View ARP Table")
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  SUB-COMPOSABLES
// ─────────────────────────────────────────────────────────────

@Composable
private fun NetResultRow(
    label: String,
    value: String,
    icon : androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp))
        Column {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ErrorCard(msg: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp))
            Column {
                Text("Scan Failed",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun NotFoundCard(mode: LookupMode) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SearchOff, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp))
            Column {
                Text("Device Not Found",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    if (mode == LookupMode.MAC_TO_IP)
                        "MAC not found on this network.\n" +
                                "\u2022 Device may be offline or on a different subnet\n" +
                                "\u2022 Try adding extra subnets above"
                    else
                        "No MAC found for this IP.\n" +
                                "\u2022 Device may be offline or unreachable\n" +
                                "\u2022 Some devices block ARP/ICMP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ArpEntryRow(entry: ArpEntry, isMatch: Boolean, onCopy: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = if (isMatch) Color(0xFF22C55E).copy(0.12f)
                   else MaterialTheme.colorScheme.surfaceVariant,
        border   = if (isMatch) BorderStroke(1.5.dp, Color(0xFF22C55E).copy(0.5f)) else null,
        modifier = Modifier.fillMaxWidth().clickable { onCopy() }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isMatch) Icons.Default.CheckCircle else Icons.Default.DeviceHub,
                null,
                tint = if (isMatch) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(entry.ip,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMatch) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                Text(entry.mac,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (entry.state) {
                        "reachable" -> Color(0xFF22C55E).copy(0.15f)
                        "stale"     -> Color(0xFFF59E0B).copy(0.15f)
                        else        -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(entry.state,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (entry.state) {
                            "reachable" -> Color(0xFF15803D)
                            "stale"     -> Color(0xFFB45309)
                            else        -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
                Text(entry.iface,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
