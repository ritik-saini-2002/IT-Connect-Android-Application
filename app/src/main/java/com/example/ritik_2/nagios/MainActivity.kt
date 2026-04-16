package com.example.ritik_2.nagios

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import com.example.ritik_2.theme.Ritik_2Theme

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NagiosViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val baseUrl  = intent.getStringExtra("BASE_URL")  ?: ""
        val username = intent.getStringExtra("USERNAME")  ?: ""
        val password = intent.getStringExtra("PASSWORD")  ?: ""

        viewModel = ViewModelProvider(
            this,
            NagiosViewModel.Factory(baseUrl, username, password)
        )[NagiosViewModel::class.java]

        // Schedule background polling worker
        NagiosNotifications.scheduleWorker(this, baseUrl, username, password)
        NagiosNotifications.createNotificationChannels(this)

        setContent {
            Ritik_2Theme {
                MainShell(
                    viewModel = viewModel,
                    onLogout  = { logout() }
                )
            }
        }
    }

    private fun logout() {
        // Capture current server details BEFORE clearing credentials
        val prefillUrl  = viewModel.serverUrl
        val prefillUser = viewModel.serverUsername

        getSharedPreferences("nagios_connect", MODE_PRIVATE).edit().clear().apply()
        NagiosNotifications.cancelWorker(this)

        // Navigate to ConnectActivity with SHOW_FORM=true so the user always sees
        // the login form pre-filled with the old server URL + username.
        // Password is intentionally left blank — user must type it.
        val intent = Intent(this@MainActivity, ConnectActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SHOW_FORM",        true)
            putExtra("PREFILL_URL",      prefillUrl)
            putExtra("PREFILL_USERNAME", prefillUser)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    viewModel: NagiosViewModel,
    onLogout: () -> Unit
) {
    val navItems = listOf(
        NavItem("Dashboard", Icons.Default.Dashboard,    "dashboard"),
        NavItem("Hosts",     Icons.Default.Computer,     "hosts"),
        NavItem("Alerts",    Icons.Default.Notifications,"alerts"),
        NavItem("Settings",  Icons.Default.Settings,     "settings")
    )

    var currentRoute     by remember { mutableStateOf("dashboard") }
    var selectedHost     by remember { mutableStateOf<NagiosHost?>(null) }
    var troubleshootHost by remember { mutableStateOf<NagiosHost?>(null) }
    val alerts           by viewModel.alerts.collectAsState()

    // ── System back navigation ────────────────────────────────────────────────
    // Priority: TroubleshootScreen → ServiceScreen → HostList → Dashboard → exit
    val isAtRoot = currentRoute == "dashboard" && selectedHost == null && troubleshootHost == null
    BackHandler(enabled = !isAtRoot) {
        when {
            troubleshootHost != null -> {
                troubleshootHost = null
                viewModel.clearToolResults()
            }
            selectedHost != null -> {
                selectedHost = null
                viewModel.selectHost(null)
            }
            else -> {
                currentRoute = "dashboard"
            }
        }
    }
    // When isAtRoot == true (on dashboard, no host selected), BackHandler is
    // disabled so the system handles it and finishes the activity naturally.

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick  = {
                            currentRoute     = item.route
                            selectedHost     = null
                            troubleshootHost = null
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (item.route == "alerts" && alerts.isNotEmpty()) {
                                        Badge { Text(alerts.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (currentRoute) {
            "dashboard" -> DashboardScreen(
                viewModel          = viewModel,
                modifier           = Modifier.padding(paddingValues),
                onNavigateToAlerts = { currentRoute = "alerts" },
                // Navigate to Hosts tab with a pre-set status filter
                onNavigateToHosts  = { filter ->
                    viewModel.setHostStatusFilter(filter)
                    currentRoute = "hosts"
                }
            )
            "hosts" -> {
                when {
                    troubleshootHost != null -> TroubleshootScreen(
                        viewModel = viewModel,
                        host      = troubleshootHost!!,
                        modifier  = Modifier.padding(paddingValues),
                        onBack    = { troubleshootHost = null; viewModel.clearToolResults() }
                    )
                    selectedHost != null -> ServiceScreen(
                        viewModel      = viewModel,
                        host           = selectedHost!!,
                        modifier       = Modifier.padding(paddingValues),
                        onBack         = { selectedHost = null; viewModel.selectHost(null) },
                        onTroubleshoot = { troubleshootHost = selectedHost }
                    )
                    else -> HostListScreen(
                        viewModel   = viewModel,
                        modifier    = Modifier.padding(paddingValues),
                        onHostClick = { host ->
                            selectedHost = host
                            viewModel.selectHost(host.name)
                        }
                    )
                }
            }
            "alerts"   -> AlertsScreen(
                viewModel = viewModel,
                modifier  = Modifier.padding(paddingValues)
            )
            "settings" -> SettingsScreen(
                viewModel = viewModel,
                modifier  = Modifier.padding(paddingValues),
                onLogout  = onLogout
            )
        }
    }
}
