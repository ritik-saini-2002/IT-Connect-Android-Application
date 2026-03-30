package com.example.ritik_2.pocketbase

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.http.URLProtocol

object PocketBaseClient {

    const val HOST = "192.168.7.196"
    const val PORT = 5005
    const val BASE_URL = "http://$HOST:$PORT"

    val instance: PocketbaseClient by lazy {
        PocketbaseClient({
            protocol = URLProtocol.HTTP
            host     = HOST
            port     = PORT
        })
    }
}