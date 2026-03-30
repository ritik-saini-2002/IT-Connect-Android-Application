package com.example.ritik_2.data.pocketbase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PocketBaseSessionManager {

    private const val TAG          = "PBSessionManager"
    private const val PREFS_NAME   = "pb_secure_session"
    private const val KEY_TOKEN    = "pb_auth_token"
    private const val KEY_USER_ID  = "pb_user_id"
    private const val KEY_EMAIL    = "pb_user_email"
    private const val KEY_ROLE     = "pb_user_role"
    private const val KEY_NAME     = "pb_user_name"
    private const val KEY_DOC_PATH = "pb_doc_path"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Log.d(TAG, "Session manager initialized ✅")
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, using plain: ${e.message}")
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveSession(
        token: String,
        userId: String,
        email: String,
        name: String,
        role: String,
        documentPath: String
    ) {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN,    token)
            putString(KEY_USER_ID,  userId)
            putString(KEY_EMAIL,    email)
            putString(KEY_NAME,     name)
            putString(KEY_ROLE,     role)
            putString(KEY_DOC_PATH, documentPath)
            apply()
        }
        Log.d(TAG, "Session saved for: $email ✅")
    }

    fun getToken(): String?    = prefs?.getString(KEY_TOKEN,    null)
    fun getUserId(): String?   = prefs?.getString(KEY_USER_ID,  null)
    fun getEmail(): String?    = prefs?.getString(KEY_EMAIL,    null)
    fun getName(): String?     = prefs?.getString(KEY_NAME,     null)
    fun getRole(): String?     = prefs?.getString(KEY_ROLE,     null)
    fun getDocPath(): String?  = prefs?.getString(KEY_DOC_PATH, null)
    fun isLoggedIn(): Boolean  = getToken() != null && getUserId() != null

    fun clearSession() {
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "Session cleared ✅")
    }
}