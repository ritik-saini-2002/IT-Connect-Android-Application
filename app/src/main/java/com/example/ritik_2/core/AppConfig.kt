// ─── core/AppConfig.kt ────────────────────────────────────────
package com.example.ritik_2.core

import com.example.ritik_2.BuildConfig

object AppConfig {
    val PB_HOST     : String = BuildConfig.PB_HOST
    val PB_PORT     : Int    = BuildConfig.PB_PORT.toIntOrNull() ?: 8090
    val BASE_URL    : String = "http://$PB_HOST:$PB_PORT"
    val ADMIN_EMAIL : String = BuildConfig.PB_ADMIN_EMAIL
    val ADMIN_PASS  : String = BuildConfig.PB_ADMIN_PASSWORD
}