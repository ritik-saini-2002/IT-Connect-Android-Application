package com.example.ritik_2.core

import com.example.ritik_2.BuildConfig

object AppConfig {

    // ── PocketBase connection ─────────────────────────────────
    val PB_HOST    : String = BuildConfig.PB_HOST
    val PB_PORT    : Int    = BuildConfig.PB_PORT.toIntOrNull() ?: 5005
    val BASE_URL   : String = "http://$PB_HOST:$PB_PORT"

    // Admin credentials are no longer shipped in the APK.
    // Admin token is obtained via authenticated System_Administrator login
    // and cached in SessionManager.

    // ── Avatar URL ────────────────────────────────────────────
    /**
     * Builds the full PocketBase URL for a user's avatar.
     *
     * PocketBase stores only the filename in the `avatar` field
     * (e.g. "profile_abc123_xyz.jpg"), not the full URL.
     * Call this whenever you need to display a user's avatar image.
     *
     * Returns null if filename is blank so Coil shows the
     * fallback initials placeholder instead of a broken image.
     *
     * Example:
     *   avatarUrl("uzv8nfdhq49k435", "profile_uzv8nfdhq49k435_d1yf6902v9.jpg")
     *   → "http://192.168.7.28:5005/api/files/_pb_users_auth_/uzv8nfdhq49k435/profile_uzv8nfdhq49k435_d1yf6902v9.jpg"
     */
    fun avatarUrl(userId: String, filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        return "$BASE_URL/api/files/users/$userId/$filename"
    }

    // ── Generic file URL ──────────────────────────────────────
    /**
     * Builds a PocketBase file URL for any collection.
     *
     * @param collectionId  e.g. "_pb_users_auth_", "companies_metadata"
     * @param recordId      the record's id
     * @param filename      the stored filename
     */
    fun fileUrl(collectionId: String, recordId: String, filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        return "$BASE_URL/api/files/$collectionId/$recordId/$filename"
    }
}