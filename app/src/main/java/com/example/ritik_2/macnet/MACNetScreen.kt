package com.example.ritik_2.macnet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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

data class ArpEntry(
    val ip     : String,
    val mac    : String,
    val iface  : String,
    val state  : String   // reachable / stale / incomplete / etc.
)

enum class ScanState { IDLE, SCANNING, DONE, ERROR }

// ─────────────────────────────────────────────────────────────
//  MAC NET SCREEN
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MACNetScreen() {

    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val keyboard    = LocalSoftwareKeyboardController.current

    // Input
    var macInput    by remember { mutableStateOf("") }
    var macError    by remember { mutableStateOf<String?>(null) }

    // Results
    var scanState   by remember { mutableStateOf(ScanState.IDLE) }
    var arpTable    by remember { mutableStateOf<List<ArpEntry>>(emptyList()) }
    var matchedIp   by remember { mutableStateOf<String?>(null) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var totalHosts  by remember { mutableIntStateOf(0) }
    var errorMsg    by remember { mutableStateOf("") }
    var snackMsg    by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar
    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    fun normalizeMAC(raw: String): String =
        raw.trim().uppercase()
            .replace("-", ":")
            .replace(".", ":")

    fun isValidMAC(mac: String): Boolean =
        mac.matches(Regex("([0-9A-Fa-f]{2}[:\\-.]){5}[0-9A-Fa-f]{2}"))

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IP Address", text))
        snackMsg = "Copied: $text"
    }

    // ── Get device subnet from WiFi ──────────────────────────
    fun getSubnet(): String? {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return null
            val bytes = byteArrayOf(
                (ip and 0xFF).toByte(),
                (ip shr 8 and 0xFF).toByte(),
                (ip shr 16 and 0xFF).toByte(),
                (ip shr 24 and 0xFF).toByte()
            )
            "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}"
        } catch (e: Exception) {
            null
        }
    }

    // ── Read /proc/net/arp (ARP cache) ───────────────────────
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

    // ── Ping sweep subnet to populate ARP cache ──────────────
    suspend fun pingSweep(subnet: String, onProgress: (Int, Int) -> Unit) {
        val hosts = (1..254).map { "$subnet.$it" }
        totalHosts = hosts.size
        withContext(Dispatchers.IO) {
            hosts.forEachIndexed { idx, host ->
                try {
                    InetAddress.getByName(host).isReachable(300)
                } catch (_: Exception) {}
                onProgress(idx + 1, hosts.size)
            }
        }
    }

    // ── Main search logic ────────────────────────────────────
    fun searchMAC() {
        val normalized = normalizeMAC(macInput)
        if (!isValidMAC(normalized)) {
            macError = "Invalid MAC format. Use XX:XX:XX:XX:XX:XX"
            return
        }
        macError  = null
        keyboard?.hide()
        matchedIp = null

        scope.launch {
            scanState = ScanState.SCANNING
            scannedCount = 0

            // Step 1: check ARP cache first (instant)
            val cached = readArpCache()
            arpTable   = cached
            val cacheHit = cached.find { it.mac == normalized }
            if (cacheHit != null) {
                matchedIp = cacheHit.ip
                scanState  = ScanState.DONE
                return@launch
            }

            // Step 2: ping sweep to populate ARP cache
            val subnet = withContext(Dispatchers.IO) { getSubnet() }
            if (subnet == null) {
                errorMsg  = "Cannot detect subnet. Make sure you are connected to WiFi."
                scanState = ScanState.ERROR
                return@launch
            }

            pingSweep(subnet) { done, total ->
                scannedCount = done
                totalHosts   = total
            }

            // Step 3: re-read ARP after sweep
            delay(500) // let ARP table settle
            val freshArp = readArpCache()
            arpTable     = freshArp
            val hit      = freshArp.find { it.mac == normalized }
            matchedIp    = hit?.ip
            scanState    = ScanState.DONE
        }
    }

    // ── UI ────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("MAC → IP Finder",
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onPrimary)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        macInput     = ""
                        macError     = null
                        matchedIp    = null
                        arpTable     = emptyList()
                        scanState    = ScanState.IDLE
                        scannedCount = 0
                    }) {
                        Icon(Icons.Default.Refresh, "Reset",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Info card ──────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier              = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Text("ℹ️", fontSize = 20.sp)
                        Text(
                            "Enter a MAC address to find which IP it currently holds on your network. " +
                                    "The app checks the ARP cache first, then does a background ping sweep if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ── MAC input ──────────────────────────────────
            item {
                OutlinedTextField(
                    value         = macInput,
                    onValueChange = {
                        macInput = it
                        macError = null
                    },
                    label         = { Text("MAC Address") },
                    placeholder   = { Text("e.g. AA:BB:CC:DD:EE:FF") },
                    leadingIcon   = { Icon(Icons.Default.NetworkCheck, null) },
                    isError       = macError != null,
                    supportingText = {
                        macError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            ?: Text("Formats: AA:BB:CC:DD:EE:FF  or  AA-BB-CC-DD-EE-FF",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction      = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { searchMAC() }),
                    trailingIcon  = {
                        if (macInput.isNotEmpty()) {
                            IconButton(onClick = { macInput = ""; macError = null }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    }
                )
            }

            // ── Search button ──────────────────────────────
            item {
                Button(
                    onClick   = { searchMAC() },
                    modifier  = Modifier.fillMaxWidth().height(50.dp),
                    shape     = RoundedCornerShape(14.dp),
                    enabled   = macInput.isNotBlank() && scanState != ScanState.SCANNING
                ) {
                    if (scanState == ScanState.SCANNING) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Scanning network…")
                    } else {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Find IP", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Scan progress ──────────────────────────────
            if (scanState == ScanState.SCANNING) {
                item {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pinging subnet…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("$scannedCount / $totalHosts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = {
                                    if (totalHosts > 0) scannedCount.toFloat() / totalHosts
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(5.dp)
                            )
                            Text(
                                "Checking ARP cache for MAC match after sweep…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Result ─────────────────────────────────────
            if (scanState == ScanState.DONE || scanState == ScanState.ERROR) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        when {
                            scanState == ScanState.ERROR -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier              = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text("❌", fontSize = 28.sp)
                                        Column {
                                            Text("Scan Failed",
                                                fontWeight = FontWeight.Bold,
                                                style      = MaterialTheme.typography.titleSmall,
                                                color      = MaterialTheme.colorScheme.onErrorContainer)
                                            Text(errorMsg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }
                            }
                            matchedIp != null -> {
                                Card(
                                    shape  = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF22C55E).copy(0.12f))
                                ) {
                                    Column(
                                        modifier            = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment     = Alignment.CenterVertically
                                        ) {
                                            Text("✅", fontSize = 26.sp)
                                            Column {
                                                Text("Device Found!",
                                                    fontWeight = FontWeight.Bold,
                                                    style      = MaterialTheme.typography.titleMedium,
                                                    color      = Color(0xFF15803D))
                                                Text("MAC matched on your network",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF22C55E).copy(0.3f))
                                        // MAC row
                                        ResultRow(
                                            label = "MAC Address",
                                            value = normalizeMAC(macInput),
                                            icon  = "🔌"
                                        )
                                        // IP row with copy button
                                        Row(
                                            modifier              = Modifier.fillMaxWidth(),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("🌐", fontSize = 18.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Current IP Address",
                                                    style  = MaterialTheme.typography.labelSmall,
                                                    color  = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(matchedIp!!,
                                                    style      = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color      = MaterialTheme.colorScheme.primary)
                                            }
                                            FilledTonalButton(
                                                onClick = { copyToClipboard(matchedIp!!) },
                                                shape   = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, null,
                                                    modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier              = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text("🔍", fontSize = 28.sp)
                                        Column {
                                            Text("Device Not Found",
                                                fontWeight = FontWeight.Bold,
                                                style      = MaterialTheme.typography.titleSmall,
                                                color      = MaterialTheme.colorScheme.onErrorContainer)
                                            Text(
                                                "MAC address not found on this network.\n" +
                                                        "• Device may be offline or on a different subnet\n" +
                                                        "• Some devices block ARP responses",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── ARP Table ──────────────────────────────────
            if (arpTable.isNotEmpty()) {
                item {
                    Text("ARP CACHE  (${arpTable.size} entries)",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
                items(arpTable, key = { "${it.ip}_${it.mac}" }) { entry ->
                    val isMatch = entry.mac == normalizeMAC(macInput)
                    Surface(
                        shape  = RoundedCornerShape(10.dp),
                        color  = if (isMatch) Color(0xFF22C55E).copy(0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isMatch)
                            androidx.compose.foundation.BorderStroke(
                                1.5.dp, Color(0xFF22C55E).copy(0.5f))
                        else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(if (isMatch) "✅" else "📡", fontSize = 18.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.ip,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    color      = if (isMatch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                                Text(entry.mac,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = when (entry.state) {
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
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  RESULT ROW
// ─────────────────────────────────────────────────────────────

@Composable
private fun ResultRow(label: String, value: String, icon: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 18.sp)
        Column {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis)
        }
    }
}