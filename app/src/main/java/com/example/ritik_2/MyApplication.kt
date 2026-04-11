package com.example.ritik_2

import android.app.Application
import android.util.Log
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseInitializer
import com.example.ritik_2.windowscontrol.PcControlMain
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    @Inject lateinit var dataSource: AppDataSource

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize PC Control — loads saved IP/port/key from SharedPreferences
        PcControlMain.init(this, "")

        appScope.launch {
            try {
                Log.d("MyApplication", "Running PocketBase initializer...")
                PocketBaseInitializer.initialize()
                Log.d("MyApplication", "PocketBase initializer complete")

                dataSource.ensureCollectionsExist()
                    .onSuccess { Log.d("MyApplication", "Collections verified") }
                    .onFailure { Log.e("MyApplication", "Collections check: ${it.message}") }
            } catch (e: Exception) {
                Log.e("MyApplication", "App init failed: ${e.message}", e)
            }
        }

        Log.d("MyApplication", "IT Connect v3.0 initialized")
    }
}