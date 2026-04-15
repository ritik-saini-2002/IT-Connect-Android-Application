package com.example.ritik_2.nagios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.theme.AppColors

// ─── Color helpers ────────────────────────────────────────────────────────────

@Composable
fun hostStatusColor(status: String): Color = when (status) {
    "UP"          -> AppColors.success
    "DOWN"        -> MaterialTheme.colorScheme.error
    "UNREACHABLE" -> AppColors.warning
    else          -> AppColors.info
}

@Composable
fun serviceStatusColor(status: String): Color = when (status) {
    "OK"       -> AppColors.success
    "WARNING"  -> AppColors.warning
    "CRITICAL" -> MaterialTheme.colorScheme.error
    else       -> AppColors.info
}

@Composable
fun StatusDot(status: String, isHost: Boolean = true) {
    val color = if (isHost) hostStatusColor(status) else serviceStatusColor(status)
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun StatusBadge(status: String, isHost: Boolean = true) {
    val color = if (isHost) hostStatusColor(status) else serviceStatusColor(status)
    val bgColor = color.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text      = status,
            color     = color,
            fontSize  = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Loading / Error composables ─────────────────────────────────────────────

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint     = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "Connection error",
            fontWeight = FontWeight.Medium,
            fontSize  = 16.sp
        )
        Text(
            text     = message,
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

// ─── Stat Card ────────────────────────────────────────────────────────────────

@Composable
fun StatCard(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text     = label,
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text       = value.toString(),
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = color,
                modifier   = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ─── SCREEN 1: Dashboard ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: NagiosViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAlerts: () -> Unit,
    onNavigateToHosts:  () -> Unit
) {
    val summaryState by viewModel.summary.collectAsStateWithLifecycle()
    val alerts       by viewModel.alerts.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState    = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { isRefreshing = true; viewModel.refresh(); isRefreshing = false },
            state        = pullState
        ) {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Dashboard",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Infrastructure overview",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                }

                when (val s = summaryState) {
                    is UiState.Loading -> item { LoadingView() }
                    is UiState.Error   -> item {
                        ErrorView(s.message, onRetry = { viewModel.refresh() })
                    }
                    is UiState.Success -> {
                        val data = s.data
                        item {
                            Text(
                                "Hosts",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    label   = "Up",
                                    value   = data.hostsUp,
                                    color   = AppColors.success,
                                    onClick = onNavigateToHosts,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label   = "Down",
                                    value   = data.hostsDown,
                                    color   = MaterialTheme.colorScheme.error,
                                    onClick = onNavigateToHosts,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label   = "Unreachable",
                                    value   = data.hostsUnreachable,
                                    color   = AppColors.warning,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Services",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard("OK",       data.servicesOk,       AppColors.success,                        modifier = Modifier.weight(1f))
                                StatCard("Warning",  data.servicesWarning,  AppColors.warning,                        modifier = Modifier.weight(1f))
                                StatCard("Critical", data.servicesCritical, MaterialTheme.colorScheme.error,          modifier = Modifier.weight(1f))
                            }
                        }
                        if (alerts.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Active alerts",
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    TextButton(onClick = onNavigateToAlerts) {
                                        Text("See all (${alerts.size})")
                                    }
                                }
                            }
                            items(alerts.take(3)) { svc ->
                                AlertCard(service = svc)
                            }
                        } else {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = AppColors.successContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = AppColors.success)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "All services are running normally",
                                            color    = AppColors.success,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── SCREEN 2: Host List ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    viewModel:   NagiosViewModel,
    modifier:    Modifier = Modifier,
    onHostClick: (NagiosHost) -> Unit
) {
    val hostsState   by viewModel.filteredHosts.collectAsStateWithLifecycle()
    val filterQuery  by viewModel.filterQuery.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // TopBar
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Hosts",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value         = filterQuery,
                onValueChange = { viewModel.setFilter(it) },
                placeholder   = { Text("Search by name or IP...") },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon  = {
                    if (filterQuery.isNotBlank())
                        IconButton(onClick = { viewModel.setFilter("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                },
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
                shape      = RoundedCornerShape(12.dp)
            )
        }

        when (val state = hostsState) {
            is UiState.Loading -> LoadingView()
            is UiState.Error   -> ErrorView(state.message, onRetry = { viewModel.refresh() })
            is UiState.Success -> {
                val hosts = state.data
                if (hosts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (filterQuery.isBlank()) "No hosts found"
                            else "No hosts matching \"$filterQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${hosts.size} host${if (hosts.size == 1) "" else "s"}",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(hosts, key = { it.name }) { host ->
                            HostCard(host = host, onClick = { onHostClick(host) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HostCard(host: NagiosHost, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(host.status, isHost = true)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = host.name,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text     = host.address,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (host.pluginOutput.isNotBlank() && host.status != "UP") {
                    Text(
                        text     = host.pluginOutput,
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(host.status, isHost = true)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── SCREEN 3: Services for a host ──────────────────────────────────────────

@Composable
fun ServiceScreen(
    viewModel: NagiosViewModel,
    hostName:  String,
    modifier:  Modifier = Modifier,
    onBack:    () -> Unit
) {
    val services by viewModel.servicesForSelectedHost.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // Back header
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text       = hostName,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = "${services.size} services",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (services.isEmpty()) {
            LoadingView()
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(services, key = { it.serviceName }) { svc ->
                    ServiceCard(service = svc)
                }
            }
        }
    }
}

@Composable
fun ServiceCard(service: NagiosService) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = serviceStatusColor(service.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(
                        if (service.status != "OK") borderColor.copy(alpha = 0.06f)
                        else Color.Transparent
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(service.status, isHost = false)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = service.serviceName,
                        fontWeight = FontWeight.Medium,
                        fontSize   = 14.sp
                    )
                    Text(
                        text     = "Last check: ${service.lastCheck}",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                StatusBadge(service.status, isHost = false)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(start = 4.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && service.pluginOutput.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text     = service.pluginOutput,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

// ─── SCREEN 4: Alerts ────────────────────────────────────────────────────────

@Composable
fun AlertsScreen(
    viewModel: NagiosViewModel,
    modifier:  Modifier = Modifier
) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Alerts",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Text(
                if (alerts.isEmpty()) "All clear" else "${alerts.size} active problem${if (alerts.size == 1) "" else "s"}",
                fontSize = 13.sp,
                color    = if (alerts.isEmpty()) AppColors.success
                           else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (alerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = AppColors.success,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No active alerts",
                        fontWeight = FontWeight.Medium,
                        fontSize   = 16.sp,
                        color      = AppColors.success
                    )
                    Text(
                        "All hosts and services are healthy",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Critical first
                val criticals = alerts.filter { it.status == "CRITICAL" }
                val warnings  = alerts.filter { it.status == "WARNING" }
                val others    = alerts.filter { it.status != "CRITICAL" && it.status != "WARNING" }

                if (criticals.isNotEmpty()) {
                    item {
                        Text(
                            "Critical",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.error,
                            modifier   = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(criticals, key = { "${it.hostName}:${it.serviceName}" }) { svc ->
                        AlertCard(service = svc)
                    }
                }
                if (warnings.isNotEmpty()) {
                    item {
                        Text(
                            "Warning",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color      = AppColors.warning,
                            modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(warnings, key = { "${it.hostName}:${it.serviceName}" }) { svc ->
                        AlertCard(service = svc)
                    }
                }
                if (others.isNotEmpty()) {
                    item {
                        Text(
                            "Unknown",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color    = AppColors.info,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(others, key = { "${it.hostName}:${it.serviceName}" }) { svc ->
                        AlertCard(service = svc)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(service: NagiosService) {
    val accentColor = serviceStatusColor(service.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = service.serviceName,
                        fontWeight = FontWeight.Medium,
                        fontSize   = 13.sp,
                        modifier   = Modifier.weight(1f),
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    StatusBadge(service.status, isHost = false)
                }
                Text(
                    text     = service.hostName,
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (service.pluginOutput.isNotBlank()) {
                    Text(
                        text     = service.pluginOutput,
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ─── SCREEN 5: Settings ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NagiosViewModel,
    modifier:  Modifier = Modifier,
    onLogout:  () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPassword     by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Settings",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
            modifier   = Modifier.padding(bottom = 20.dp)
        )

        // Connection info card
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Connection",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.success))
                        Spacer(Modifier.width(6.dp))
                        Text("Connected", fontSize = 13.sp, color = AppColors.success, fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Polling", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Every 30 seconds", fontSize = 13.sp)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Background alerts", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Every 15 minutes", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Actions card
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Actions",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.padding(bottom = 12.dp)
                )
                OutlinedButton(
                    onClick  = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh now")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp, MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect & logout")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // About
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nagios Monitor", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "Reads live data from your Nagios server using existing web credentials via the statusjson CGI API. No additional setup required.",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Disconnect?") },
            text  = { Text("This will clear your saved credentials and return to the login screen.") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
