package com.example.ritik_2.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.auth.SessionStatus
import com.example.ritik_2.core.AppConfig
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.login.LoginActivity
import com.example.ritik_2.main.MainActivity
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var dataSource    : AppDataSource
    @Inject lateinit var db            : AppDatabase

    // Own lightweight client — short timeouts so splash never hangs
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ITConnectTheme { ITConnectSplashScreen(onSplashComplete = {}) } }
        lifecycleScope.launch { route() }
    }

    private suspend fun route() {
        // 1. No local session → Login (no network needed)
        if (!authRepository.isLoggedIn) { go(LoginActivity::class.java); return }

        // 2. Restore cached token into memory
        authRepository.restoreSession()

        // 3. Probe server directly — blocking call with 3s hard timeout
        //    probeNow() on ConnectivityMonitor is fire-and-forget so we do
        //    our own synchronous check here instead.
        val serverUp = withTimeoutOrNull(50) {
            withContext(Dispatchers.IO) {
                try {
                    val res = probeClient.newCall(
                        Request.Builder()
                            .url("${AppConfig.BASE_URL}/api/health")
                            .get().build()
                    ).execute()
                    val ok = res.isSuccessful
                    res.close()
                    ok
                } catch (_: Exception) { false }
            }
        } ?: false

        if (serverUp) {
            // 4a. Online path — validate session
            when (val status = authRepository.validateSession()) {
                is SessionStatus.NoSession -> {
                    go(LoginActivity::class.java); return
                }
                is SessionStatus.TokenInvalid -> {
                    Toast.makeText(this,
                        "Session expired. Please log in again.",
                        Toast.LENGTH_SHORT).show()
                    go(LoginActivity::class.java); return
                }
                is SessionStatus.Deactivated -> {
                    Toast.makeText(this,
                        "Your account has been deactivated. Contact your administrator.",
                        Toast.LENGTH_LONG).show()
                    go(LoginActivity::class.java); return
                }
                is SessionStatus.Valid -> routeToMain(status.session.userId)
            }
        } else {
            // 4b. Offline path — use cached session
            val session = authRepository.getSession()
            if (session == null) {
                go(LoginActivity::class.java); return
            }
            routeToMainOffline(session.userId)
        }
    }

    private suspend fun routeToMain(userId: String) {
        val needsCompletion = try {
            val cached = withContext(Dispatchers.IO) { db.userDao().getById(userId) }
            cached?.needsProfileCompletion
                ?: withTimeoutOrNull(100) {
                    dataSource.getUserProfile(userId).getOrNull()?.isProfileIncomplete
                } ?: false
        } catch (_: Exception) { false }

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (needsCompletion) putExtra("SHOW_COMPLETE_PROFILE_TOGGLE", true)
        })
        finish()
    }

    private suspend fun routeToMainOffline(userId: String) {
        val needsCompletion = try {
            withContext(Dispatchers.IO) {
                db.userDao().getById(userId)?.needsProfileCompletion ?: false
            }
        } catch (_: Exception) { false }

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (needsCompletion) putExtra("SHOW_COMPLETE_PROFILE_TOGGLE", true)
        })
        finish()
    }

    private fun go(cls: Class<*>) {
        startActivity(Intent(this, cls).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}