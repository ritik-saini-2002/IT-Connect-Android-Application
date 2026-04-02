package com.example.ritik_2.pocketbase

import com.example.ritik_2.core.AppConfig
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketBaseClientProvider @Inject constructor() {
    val client: PocketbaseClient by lazy {
        PocketbaseClient({
            protocol = URLProtocol.HTTP
            host     = AppConfig.PB_HOST
            port     = AppConfig.PB_PORT
        })
    }
}