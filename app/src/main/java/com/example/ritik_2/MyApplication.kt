package com.example.ritik_2

import android.app.Application
import android.util.Log
import com.example.ritik_2.data.source.AppDataSource
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

        // ✅ PcControlMain initialized here — simple and direct
        PcControlMain.init(this, "")

        // Initialize PocketBase collections
        appScope.launch {
            dataSource.ensureCollectionsExist()
                .onSuccess { Log.d("MyApplication", "Collections ready ✅") }
                .onFailure { Log.e("MyApplication", "Collections setup failed: ${it.message}") }
        }
    }
}