package com.example.ritik_2.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.ritik_2.core.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val _serverReachable = MutableStateFlow(true)   // optimistic default
    val serverReachable: StateFlow<Boolean> = _serverReachable.asStateFlow()

    private val cm    = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val probe = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)   // short — don't block UI
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val probing = AtomicBoolean(false)

    init {
        registerCallbacks()
        // Start periodic probe every 30s
        scope.launch {
            while (true) {
                doProbe()
                delay(30_000)
            }
        }
    }

    private fun registerCallbacks() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                // Device got network — probe the actual PocketBase server
                scope.launch { doProbe() }
            }
            override fun onLost(n: Network) {
                // Device lost all network — definitely offline
                _serverReachable.value = false
            }
        })
    }

    fun probeNow() {
        scope.launch { doProbe() }
    }

    private suspend fun doProbe() {
        // Prevent concurrent probes
        if (!probing.compareAndSet(false, true)) return
        try {
            val reachable = try {
                val res = probe.newCall(
                    Request.Builder()
                        .url("${AppConfig.BASE_URL}/api/health")
                        .get().build()
                ).execute()
                val ok = res.isSuccessful
                res.close()
                ok
            } catch (e: Exception) {
                Log.d("ConnectivityMonitor", "PocketBase probe failed: ${e.message}")
                false
            }
            // Only update if value actually changed — avoids unnecessary recompositions
            if (_serverReachable.value != reachable) {
                _serverReachable.value = reachable
                Log.d("ConnectivityMonitor",
                    if (reachable) "PocketBase ✅ reachable" else "PocketBase ❌ unreachable")
            }
        } finally {
            probing.set(false)
        }
    }
}