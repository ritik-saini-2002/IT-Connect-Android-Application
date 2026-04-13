package com.example.ritik_2.pocketbase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ritik_2.data.model.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG       = "SessionManager"
        const val PREFS     = "pb_session"
        const val KEY_TOKEN       = "token"
        const val KEY_UID         = "uid"
        const val KEY_EMAIL       = "email"
        const val KEY_NAME        = "name"
        const val KEY_ROLE        = "role"
        const val KEY_PATH        = "doc_path"
        const val KEY_PERMISSIONS = "permissions"
    }

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences failed, using plain: ${e.message}")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun save(session: AuthSession) {
        prefs.edit().apply {
            putString(KEY_TOKEN,       session.token)
            putString(KEY_UID,         session.userId)
            putString(KEY_EMAIL,       session.email)
            putString(KEY_NAME,        session.name)
            putString(KEY_ROLE,        session.role)
            putString(KEY_PATH,        session.documentPath)
            putString(KEY_PERMISSIONS, Json.encodeToString(session.permissions))
        }.apply()
        Log.d(TAG, "Session saved for ${session.email} role=${session.role} perms=${session.permissions.size}")
    }

    fun get(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val uid   = prefs.getString(KEY_UID,   null) ?: return null
        val permsJson = prefs.getString(KEY_PERMISSIONS, "[]") ?: "[]"
        val permissions = try { Json.decodeFromString<List<String>>(permsJson) }
                          catch (_: Exception) { emptyList() }
        return AuthSession(
            userId       = uid,
            token        = token,
            email        = prefs.getString(KEY_EMAIL, "") ?: "",
            name         = prefs.getString(KEY_NAME,  "") ?: "",
            role         = prefs.getString(KEY_ROLE,  "") ?: "",
            documentPath = prefs.getString(KEY_PATH,  "") ?: "",
            permissions  = permissions
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Session cleared")
    }

    fun isLoggedIn(): Boolean = get() != null
}