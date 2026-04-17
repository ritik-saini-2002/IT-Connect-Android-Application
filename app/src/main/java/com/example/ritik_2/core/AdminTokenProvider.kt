package com.example.ritik_2.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure admin token provider that stores PocketBase admin credentials
 * in EncryptedSharedPreferences instead of shipping them in the APK.
 *
 * Token lifecycle:
 *  - On login  → call [startKeepAlive]  — token is fetched immediately and then
 *                refreshed every [REFRESH_INTERVAL_MS] in the background.
 *  - On logout → call [stopKeepAlive]   — background refresh stops, token is cleared.
 *  - Token is NEVER considered expired while the user is logged in; the keep-alive
 *    loop ensures a fresh token is always ready before the previous one expires.
 */
@Singleton
class AdminTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG  = "AdminTokenProvider"
        const val PREFS        = "admin_credentials"
        const val KEY_EMAIL    = "admin_email"
        const val KEY_PASSWORD = "admin_password"

        // How often to proactively refresh the token in the background.
        // PocketBase admin tokens default to a short TTL (~15-30 min on most setups).
        // Refreshing every 9 minutes keeps us well within that window.
        const val REFRESH_INTERVAL_MS = 9 * 60 * 1000L   // 9 minutes
    }

    private val prefs: SharedPreferences by lazy { createPrefs() }
    private val tokenMutex = Mutex()
    private val bgScope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http       = OkHttpClient()

    @Volatile private var cachedToken   = ""
    @Volatile private var isLoggedIn    = false
    private var keepAliveJob: Job?      = null

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Credentials ───────────────────────────────────────────────────────────

    /** Store admin credentials securely. Called from admin settings. */
    fun setCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
        // Invalidate cached token so next getAdminToken() re-fetches with new creds
        cachedToken = ""
        Log.d(TAG, "Admin credentials updated")
    }

    fun hasCredentials(): Boolean {
        val email = prefs.getString(KEY_EMAIL, null)
        val pass  = prefs.getString(KEY_PASSWORD, null)
        return !email.isNullOrBlank() && !pass.isNullOrBlank()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
        cachedToken = ""
        Log.d(TAG, "Admin credentials cleared")
    }

    // ── Direct token injection (SA login) ────────────────────────────────────

    /**
     * Directly inject a pre-authenticated token from a System_Administrator login.
     * This avoids a separate admin-credential round-trip when the logged-in user
     * IS already the PocketBase superuser.
     *
     * Called by PocketBaseDataSource.login() when a System_Administrator is detected.
     */
    fun setTokenDirectly(token: String) {
        if (token.isBlank()) return
        cachedToken = token
        Log.d(TAG, "Admin token seeded directly from SA login")
    }

    /** True if a non-blank token is cached (valid for the session). */
    fun hasCachedToken(): Boolean = isLoggedIn && cachedToken.isNotBlank()

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Start the background token keep-alive loop.
     * Call this immediately after a successful login.
     *
     * Behaviour:
     *  1. Fetches a fresh token right away (if we don't already have one).
     *  2. Repeats every [REFRESH_INTERVAL_MS] until [stopKeepAlive] is called.
     */
    fun startKeepAlive() {
        isLoggedIn = true
        keepAliveJob?.cancel()
        keepAliveJob = bgScope.launch {
            Log.d(TAG, "Keep-alive loop started")
            while (isActive && isLoggedIn) {
                try {
                    refreshToken()
                } catch (e: Exception) {
                    // Non-fatal: network may be temporarily unavailable.
                    // We still have the old cached token; next cycle will retry.
                    Log.w(TAG, "Keep-alive refresh failed (will retry): ${e.message}")
                }
                delay(REFRESH_INTERVAL_MS)
            }
            Log.d(TAG, "Keep-alive loop ended")
        }
    }

    /**
     * Stop the background keep-alive loop and clear the cached token.
     * Call this on logout.
     */
    fun stopKeepAlive() {
        isLoggedIn  = false
        cachedToken = ""
        keepAliveJob?.cancel()
        keepAliveJob = null
        Log.d(TAG, "Keep-alive stopped — token cleared")
    }

    // ── Token retrieval ───────────────────────────────────────────────────────

    /**
     * Get a valid admin token.
     *
     * While the user is logged in the keep-alive loop ensures [cachedToken] is
     * always fresh, so this returns instantly from the in-memory cache.
     *
     * If for any reason the cache is empty (first call before the keep-alive
     * loop has fired, or a transient refresh failure), it fetches synchronously.
     */
    suspend fun getAdminToken(): String = tokenMutex.withLock {
        // Happy path: valid cached token
        if (cachedToken.isNotBlank()) return@withLock cachedToken

        // Cache miss: fetch now (keep-alive hasn't fired yet, or just cleared)
        withContext(Dispatchers.IO) {
            refreshTokenInternal()
        }
    }

    /**
     * Synchronous variant for places that already run on a background thread
     * (e.g. WorkManager workers).
     */
    fun getAdminTokenSync(): String {
        if (cachedToken.isNotBlank()) return cachedToken
        return refreshTokenSync()
    }

    // ── Internal refresh ──────────────────────────────────────────────────────

    /** Coroutine-safe refresh (called from keep-alive loop). */
    private suspend fun refreshToken() = withContext(Dispatchers.IO) {
        tokenMutex.withLock { refreshTokenInternal() }
    }

    /**
     * Core token fetch — must be called with [tokenMutex] held or from a
     * context where concurrent access is safe.
     */
    private fun refreshTokenInternal(): String {
        val email = prefs.getString(KEY_EMAIL, null)
        val pass  = prefs.getString(KEY_PASSWORD, null)
        if (email.isNullOrBlank() || pass.isNullOrBlank()) {
            // No stored credentials. This is fine if we already have a token
            // seeded via setTokenDirectly(); otherwise the caller will get an
            // empty string and surface the error in context.
            if (cachedToken.isNotBlank()) return cachedToken
            error("Admin credentials not configured. " +
                  "A System Administrator must log in or set them in Admin Settings.")
        }

        listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"           // PB < 0.23 fallback
        ).forEach { url ->
            try {
                val body = JSONObject().apply {
                    put("identity", email)
                    put("password", pass)
                }.toString().toRequestBody("application/json".toMediaType())

                val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful
                res.close()

                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) {
                        cachedToken = t
                        Log.d(TAG, "Admin token refreshed ✅ via $url")
                        return t
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Admin auth attempt failed for $url: ${e.message}")
            }
        }
        error("Could not obtain admin token — verify admin credentials in Admin Settings.")
    }

    private fun refreshTokenSync(): String {
        val email = prefs.getString(KEY_EMAIL, null)
        val pass  = prefs.getString(KEY_PASSWORD, null)
        if (email.isNullOrBlank() || pass.isNullOrBlank()) {
            if (cachedToken.isNotBlank()) return cachedToken
            error("Admin credentials not configured. A System Administrator must log in first.")
        }

        listOf(
            "${AppConfig.BASE_URL}/api/collections/_superusers/auth-with-password",
            "${AppConfig.BASE_URL}/api/admins/auth-with-password"
        ).forEach { url ->
            try {
                val body = JSONObject().apply {
                    put("identity", email)
                    put("password", pass)
                }.toString().toRequestBody("application/json".toMediaType())

                val res     = http.newCall(Request.Builder().url(url).post(body).build()).execute()
                val resBody = res.body?.string() ?: ""
                val ok      = res.isSuccessful
                res.close()

                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) {
                        cachedToken = t
                        Log.d(TAG, "Admin token refreshed (sync) ✅")
                        return t
                    }
                }
            } catch (_: Exception) {}
        }
        error("Could not obtain admin token.")
    }
}
