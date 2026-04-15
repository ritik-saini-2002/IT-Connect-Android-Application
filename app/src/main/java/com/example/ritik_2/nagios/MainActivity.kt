package com.example.ritik_2.nagios

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.launch

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
        lifecycleScope.launch {
            dataStore.edit { it.clear() }
            val intent = Intent(this@MainActivity, ConnectActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
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
    var selectedHostName by remember { mutableStateOf<String?>(null) }
    val alerts           by viewModel.alerts.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick  = {
                            currentRoute     = item.route
                            selectedHostName = null
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
                viewModel = viewModel,
                modifier  = Modifier.padding(paddingValues),
                onNavigateToAlerts = { currentRoute = "alerts" },
                onNavigateToHosts  = { currentRoute = "hosts" }
            )
            "hosts" -> {
                if (selectedHostName != null) {
                    ServiceScreen(
                        viewModel  = viewModel,
                        hostName   = selectedHostName!!,
                        modifier   = Modifier.padding(paddingValues),
                        onBack     = { selectedHostName = null; viewModel.selectHost(null) }
                    )
                } else {
                    HostListScreen(
                        viewModel = viewModel,
                        modifier  = Modifier.padding(paddingValues),
                        onHostClick = { host ->
                            selectedHostName = host.name
                            viewModel.selectHost(host.name)
                        }
                    )
                }
            }
            "alerts" -> AlertsScreen(
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
