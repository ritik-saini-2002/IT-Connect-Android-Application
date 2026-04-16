package com.example.ritik_2.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.administrator.AdministratorPanelActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.auth.SessionStatus
import com.example.ritik_2.chat.ChatActivity
import com.example.ritik_2.chat.ChatNotificationService
import com.example.ritik_2.contact.ContactActivity
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.macnet.MACNetActivity
import com.example.ritik_2.nagios.ConnectActivity as NagiosConnectActivity
import com.example.ritik_2.notifications.NotificationActivity
import com.example.ritik_2.profile.ProfileActivity
import com.example.ritik_2.theme.ITConnectTheme
import com.example.ritik_2.windowscontrol.PcControlActivity
import com.example.ritik_2.winshare.ServerConnectActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var connectMonitor: ConnectivityMonitor
    @Inject lateinit var syncManager   : SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val showProfileBanner = intent.getBooleanExtra("SHOW_COMPLETE_PROFILE_TOGGLE", false)

        // Start chat background notification service
        val session = authRepository.getSession()
        if (session != null) {
            ChatNotificationService.start(this, session.userId, session.name)
        }

        setContent {
            ITConnectTheme {
                val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
                val serverReachable by connectMonitor.serverReachable.collectAsStateWithLifecycle()
                val pendingCount    by viewModel.pendingCount.collectAsStateWithLifecycle()

                var bannerReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(5_000); bannerReady = true }

                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        uiState                   = uiState,
                        onLogout                  = { handleLogout() },
                        onCardClick               = { id -> handleCardClick(id) },
                        onProfileClick            = { handleProfileClick() },
                        onNotificationClick       = { handleNotificationClick() },
                        showCompleteProfileBanner = showProfileBanner
                    )

                    AnimatedVisibility(
                        visible  = bannerReady && !serverReachable,
                        enter    = slideInVertically { -it } + fadeIn(),
                        exit     = slideOutVertically { -it } + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        OfflineBanner(pendingCount = pendingCount)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
        lifecycleScope.launch {
            connectMonitor.probeNow()
            checkActiveStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep the service alive even when the activity is destroyed so
        // background notifications keep working. Only stop on explicit logout.
    }

    // ── Card routing ──────────────────────────────────────────────────────────

    private fun handleCardClick(id: Int) {
        // ── Hard permission guard (secondary defence after tile is hidden) ────
        val session     = authRepository.getSession()
        val role        = session?.role        ?: ""
        val permissions = session?.permissions ?: emptyList()

        if (!PermissionGuard.canAccessFeature(id, role, permissions)) {
            Toast.makeText(
                this,
                "Access denied: you don't have permission to use ${PermissionGuard.featureName(id)}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        when (id) {
            1 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
            2 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
            3 -> startActivity(Intent(this, AdministratorPanelActivity::class.java))
            4 -> startActivity(Intent(this, ServerConnectActivity::class.java))
            5 -> startActivity(Intent(this, MACNetActivity::class.java))
            6 -> startActivity(Intent(this, PcControlActivity::class.java))
            7 -> startActivity(Intent(this, ChatActivity::class.java))
            8 -> {
                val userId = session?.userId ?: return
                startActivity(Intent(this, ContactActivity::class.java).apply {
                    putExtra("userId", userId)
                })
            }
            9 -> startActivity(Intent(this, NagiosConnectActivity::class.java))
        }
    }

    private fun handleProfileClick() {
        val userId = authRepository.getSession()?.userId ?: return
        startActivity(Intent(this, ProfileActivity::class.java).apply {
            putExtra("userId", userId)
        })
    }

    private fun handleNotificationClick() {
        startActivity(Intent(this, NotificationActivity::class.java))
    }

    // ── Session / logout ──────────────────────────────────────────────────────

    private fun handleLogout() {
        lifecycleScope.launch {
            ChatNotificationService.stop(this@MainActivity)
            authRepository.logout()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private suspend fun checkActiveStatus() {
        if (!connectMonitor.serverReachable.value) return
        when (authRepository.validateSession()) {
            is SessionStatus.Deactivated  -> forceLogout("Your account has been deactivated.")
            is SessionStatus.TokenInvalid -> forceLogout("Session expired. Please log in again.")
            is SessionStatus.NoSession    -> forceLogout("Please log in.")
            is SessionStatus.Valid        -> {}
        }
    }

    private fun forceLogout(message: String) {
        lifecycleScope.launch {
            ChatNotificationService.stop(this@MainActivity)
            authRepository.logout()
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}

@Composable
private fun OfflineBanner(pendingCount: Int) {
    Surface(
        modifier       = Modifier.fillMaxWidth().statusBarsPadding(),
        color          = Color(0xFFB71C1C),
        shape          = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Server unreachable — working offline",
                    style = MaterialTheme.typography.labelMedium, color = Color.White)
                if (pendingCount > 0)
                    Text("$pendingCount change${if (pendingCount > 1) "s" else ""} will sync when reconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.8f))
            }
            if (pendingCount > 0)
                Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}