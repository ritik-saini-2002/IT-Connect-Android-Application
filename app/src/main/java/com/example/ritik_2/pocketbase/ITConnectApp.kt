package com.example.ritik_2.pocketbase

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ITConnectApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("ITConnectApp", "Application starting...")

        // Initialize PocketBase collections once on app start
        appScope.launch {
            try {
                PocketBaseInitializer.initializeCollections(PocketBaseClient.instance)
                Log.d("ITConnectApp", "PocketBase initialized ✅")
            } catch (e: Exception) {
                Log.e("ITConnectApp", "PocketBase init failed: ${e.message}")
            }
        }
    }
}