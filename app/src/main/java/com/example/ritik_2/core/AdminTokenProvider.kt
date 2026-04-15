package com.example.ritik_2.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 * Credentials are set once by a System_Administrator via the admin settings
 * screen and cached encrypted on device.
 */
@Singleton
class AdminTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "AdminTokenProvider"
        const val PREFS = "admin_credentials"
        const val KEY_EMAIL = "admin_email"
        const val KEY_PASSWORD = "admin_password"
        const val TOKEN_TTL = 10 * 60 * 1000L // 10 minutes
    }

    private val prefs: SharedPreferences by lazy { createPrefs() }
    private val tokenMutex = Mutex()
    private var cachedToken = ""
    private var tokenFetchedAt = 0L
    private val http = OkHttpClient()

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Store admin credentials securely. Called from admin settings. */
    fun setCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
        cachedToken = ""
        tokenFetchedAt = 0L
    }

    fun hasCredentials(): Boolean {
        val email = prefs.getString(KEY_EMAIL, null)
        val pass = prefs.getString(KEY_PASSWORD, null)
        return !email.isNullOrBlank() && !pass.isNullOrBlank()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
        cachedToken = ""
        tokenFetchedAt = 0L
    }

    /**
     * Get a valid admin token. Uses cached token if within TTL,
     * otherwise authenticates against PocketBase.
     */
    suspend fun getAdminToken(): String = tokenMutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (cachedToken.isNotBlank() && (now - tokenFetchedAt) < TOKEN_TTL) {
                return@withContext cachedToken
            }

            val email = prefs.getString(KEY_EMAIL, null)
            val pass = prefs.getString(KEY_PASSWORD, null)
            if (email.isNullOrBlank() || pass.isNullOrBlank()) {
                error("Admin credentials not configured. A System Administrator must set them in Admin Settings.")
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

                    val res = http.newCall(
                        Request.Builder().url(url).post(body).build()
                    ).execute()
                    val resBody = res.body?.string() ?: ""
                    val ok = res.isSuccessful
                    res.close()

                    if (ok) {
                        val t = JSONObject(resBody).optString("token")
                        if (t.isNotEmpty()) {
                            cachedToken = t
                            tokenFetchedAt = now
                            return@withContext t
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Admin auth failed for $url")
                }
            }
            error("Could not obtain admin token — verify admin credentials in Admin Settings.")
        }
    }

    /**
     * Synchronous variant for places that already run on a background thread.
     */
    fun getAdminTokenSync(): String {
        val now = System.currentTimeMillis()
        if (cachedToken.isNotBlank() && (now - tokenFetchedAt) < TOKEN_TTL) {
            return cachedToken
        }

        val email = prefs.getString(KEY_EMAIL, null)
        val pass = prefs.getString(KEY_PASSWORD, null)
        if (email.isNullOrBlank() || pass.isNullOrBlank()) {
            error("Admin credentials not configured.")
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

                val res = http.newCall(
                    Request.Builder().url(url).post(body).build()
                ).execute()
                val resBody = res.body?.string() ?: ""
                val ok = res.isSuccessful
                res.close()

                if (ok) {
                    val t = JSONObject(resBody).optString("token")
                    if (t.isNotEmpty()) {
                        cachedToken = t
                        tokenFetchedAt = now
                        return t
                    }
                }
            } catch (_: Exception) { }
        }
        error("Could not obtain admin token.")
    }
}
