package com.example.ritik_2.core

import com.example.ritik_2.BuildConfig

object AppConfig {

    // ── PocketBase connection ─────────────────────────────────
    val PB_HOST    : String = BuildConfig.PB_HOST
    val PB_PORT    : Int    = BuildConfig.PB_PORT.toIntOrNull() ?: 80
    val PB_PATH    : String = BuildConfig.PB_PATH   // e.g. "/pocketbase"
    val BASE_URL   : String = "http://$PB_HOST:$PB_PORT$PB_PATH"

    fun avatarUrl(userId: String, filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        return "$BASE_URL/api/files/users/$userId/$filename"
    }

    fun fileUrl(collectionId: String, recordId: String, filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        return "$BASE_URL/api/files/$collectionId/$recordId/$filename"
    }
}