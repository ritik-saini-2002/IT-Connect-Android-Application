package com.saini.ritik.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.saini.ritik.BuildConfig
import com.saini.ritik.administrator.AdministratorPanelActivity
import com.saini.ritik.appupdate.AppUpdateActivity
import com.saini.ritik.appupdate.AppUpdateChecker
import com.saini.ritik.appupdate.AppUpdateDialog
import com.saini.ritik.appupdate.AppUpdateManager
import com.saini.ritik.appupdate.UpdateInfo
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.auth.SessionStatus
import com.saini.ritik.chat.ChatActivity
import com.saini.ritik.chat.ChatNotificationService
import com.saini.ritik.contact.ContactActivity
import com.saini.ritik.core.AdminTokenProvider
import com.saini.ritik.core.AppLaunchGate
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.SyncManager
import com.saini.ritik.login.LoginActivity
//import com.saini.ritik.macnet.MACNetActivity
import com.saini.ritik.nagios.ConnectActivity as NagiosConnectActivity
import com.saini.ritik.notifications.NotificationActivity
import com.saini.ritik.profile.ProfileActivity
import com.saini.ritik.theme.ITConnectTheme
import com.saini.ritik.windowscontrol.PcControlActivity
import com.saini.ritik.winshare.ServerConnectActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Extends [FragmentActivity] (instead of bare ComponentActivity) so
 * [AppLaunchGate] can host BiometricPrompt — that API requires a
 * FragmentActivity to stage its internal fragment.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authRepository    : AuthRepository
    @Inject lateinit var connectMonitor    : ConnectivityMonitor
    @Inject lateinit var syncManager       : SyncManager
    @Inject lateinit var adminTokenProvider: AdminTokenProvider
    @Inject lateinit var appUpdateChecker  : AppUpdateChecker
    @Inject lateinit var appUpdateManager  : AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupOldApk(this)

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
                val roleSyncing     by viewModel.roleSyncing.collectAsStateWithLifecycle()

                var bannerReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(5_000); bannerReady = true }

                // ── App-launch biometric gate ──────────────────────────────────
                // LaunchGateState is process-scoped so returning from a child
                // activity reuses the existing unlock without re-prompting.
                var gateUnlocked by remember { mutableStateOf(LaunchGateState.unlocked) }
                var gateError    by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    if (gateUnlocked) return@LaunchedEffect
                    AppLaunchGate.prompt(
                        activity = this@MainActivity,
                        onAllow  = {
                            LaunchGateState.unlocked = true
                            gateUnlocked = true
                            gateError    = null
                        },
                        onDeny        = { msg -> gateError = msg ?: "Authentication cancelled" },
                        onUnavailable = {
                            LaunchGateState.unlocked = true
                            gateUnlocked = true
                            gateError    = "Device has no screen lock — launch gate skipped."
                        },
                    )
                }

                // ── Update check state ─────────────────────────────────────────
                var pendingUpdate    by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableStateOf<Float?>(null) }

                // updateCheckDone lives in LaunchGateState (process-scoped) so
                // returning from AppUpdateActivity or any child activity does NOT
                // re-trigger the check and re-show the dialog.
                LaunchedEffect(gateUnlocked) {
                    if (!gateUnlocked) return@LaunchedEffect
                    if (LaunchGateState.updateCheckDone) return@LaunchedEffect
                    LaunchGateState.updateCheckDone = true

                    val s = authRepository.getSession() ?: return@LaunchedEffect
                    val update = appUpdateChecker.checkForUpdate(
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        userToken          = s.token
                    )

                    Log.d("AppUpdateChecker",
                        "Remote: ${update?.versionCode}  Current: ${BuildConfig.VERSION_CODE}")

                    if (update != null) {
                        pendingUpdate    = update
                        showUpdateDialog = true
                    }
                }

                Box(Modifier.fillMaxSize()) {

                    MainScreen(
                        uiState                   = uiState,
                        onLogout                  = { handleLogout() },
                        onCardClick               = { id -> handleCardClick(id) },
                        onProfileClick            = { handleProfileClick() },
                        onNotificationClick       = { handleNotificationClick() },
                        roleSyncing               = roleSyncing,
                        showCompleteProfileBanner = showProfileBanner
                    )

                    // ── Offline banner ─────────────────────────────────────────
                    AnimatedVisibility(
                        visible  = bannerReady && !serverReachable,
                        enter    = slideInVertically { -it } + fadeIn(),
                        exit     = slideOutVertically { -it } + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        OfflineBanner(pendingCount = pendingCount)
                    }

                    // ── Biometric lock overlay ─────────────────────────────────
                    if (!gateUnlocked) {
                        LaunchLockOverlay(
                            errorText = gateError,
                            onRetry   = {
                                gateError = null
                                AppLaunchGate.prompt(
                                    activity = this@MainActivity,
                                    onAllow  = {
                                        LaunchGateState.unlocked = true
                                        gateUnlocked = true
                                        gateError    = null
                                    },
                                    onDeny        = { msg -> gateError = msg ?: "Authentication cancelled" },
                                    onUnavailable = {
                                        LaunchGateState.unlocked = true
                                        gateUnlocked = true
                                        gateError    = "Device has no screen lock — launch gate skipped."
                                    },
                                )
                            },
                            onLogout = { handleLogout() },
                        )
                    }

                    // ── App update dialog ──────────────────────────────────────
                    if (showUpdateDialog && pendingUpdate != null) {
                        val update = pendingUpdate!!
                        AppUpdateDialog(
                            versionName      = update.versionName,
                            releaseNotes     = update.releaseNotes,
                            downloadProgress = downloadProgress,
                            onDownload       = {
                                val token = authRepository.getSession()?.token
                                    ?: return@AppUpdateDialog
                                appUpdateManager.downloadAndInstall(
                                    url        = update.downloadUrl,
                                    userToken  = token,
                                    onProgress = { downloadProgress = it },
                                    onError    = { err ->
                                        Toast.makeText(
                                            this@MainActivity, err, Toast.LENGTH_LONG
                                        ).show()
                                        downloadProgress = null
                                    }
                                )
                            },
                            onDismiss = {
                                showUpdateDialog = false
                                pendingUpdate    = null
                            }
                        )
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
            // Refresh admin token on every MainActivity resume — the user got a
            // one-time login, so the keep-alive loop can lapse while the app
            // was backgrounded. No-op for users without admin credentials.
            adminTokenProvider.refreshOnResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep the service alive even when the activity is destroyed so
        // background notifications keep working. Only stop on explicit logout.
    }

    // ── Card routing ──────────────────────────────────────────────────────────

    private fun handleCardClick(id: Int) {
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
            3  -> startActivity(Intent(this, AdministratorPanelActivity::class.java))
            4  -> startActivity(Intent(this, ServerConnectActivity::class.java))
//          5  -> startActivity(Intent(this, MACNetActivity::class.java))
            6  -> startActivity(Intent(this, PcControlActivity::class.java))
            7  -> startActivity(Intent(this, ChatActivity::class.java))
            8  -> {
                val userId = session?.userId ?: return
                startActivity(Intent(this, ContactActivity::class.java).apply {
                    putExtra("userId", userId)
                })
            }
            9  -> startActivity(Intent(this, NagiosConnectActivity::class.java))
            10 -> startActivity(Intent(this, AppUpdateActivity::class.java))
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
            stopService(Intent(this@MainActivity, ChatNotificationService::class.java))
            authRepository.logout()
            // Reset process-scoped gate state so next login gets a fresh
            // biometric prompt and a fresh update check.
            LaunchGateState.unlocked        = false
            LaunchGateState.updateCheckDone = false
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
            stopService(Intent(this@MainActivity, ChatNotificationService::class.java))
            authRepository.logout()
            LaunchGateState.unlocked        = false
            LaunchGateState.updateCheckDone = false
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}

/**
 * Process-scoped state for the app-launch biometric gate and update check.
 * Both flags survive returning from child activities (process alive) but are
 * cleared on logout so the next login gets a fresh start.
 */
private object LaunchGateState {
    @Volatile var unlocked       : Boolean = false
    @Volatile var updateCheckDone: Boolean = false
}

@Composable
private fun LaunchLockOverlay(
    errorText: String?,
    onRetry  : () -> Unit,
    onLogout : () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = cs.background,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color          = cs.surfaceVariant,
                shape          = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint     = cs.primary,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        "IT Connect is locked",
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Authenticate with your fingerprint, face or device PIN to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    if (errorText != null) Text(
                        errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                    )
                    Button(
                        onClick  = onRetry,
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.LockOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Authenticate", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick  = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign out", color = cs.error)
                    }
                }
            }
        }
    }
}

fun cleanupOldApk(context: Context) {
    val apk = File(context.cacheDir, "itconnect_update.apk")
    if (apk.exists()) {
        apk.delete()
        Log.d("AppUpdate", "Cleaned up leftover APK")
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
                Text(
                    "Server unreachable — working offline",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                if (pendingCount > 0)
                    Text(
                        "$pendingCount change${if (pendingCount > 1) "s" else ""} will sync when reconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.8f)
                    )
            }
            if (pendingCount > 0)
                Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}