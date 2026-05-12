package com.saini.ritik

import android.app.Application
import android.util.Log
import com.saini.ritik.core.AdminTokenProvider
import com.saini.ritik.core.GlobalCrashHandler
import com.saini.ritik.data.source.AppDataSource
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.saini.ritik.pocketbase.PocketBaseInitializer
import com.saini.ritik.windowscontrol.PcControlMain
import com.saini.ritik.windowscontrol.scheduler.PcScheduleWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    @Inject lateinit var dataSource: AppDataSource
    @Inject lateinit var adminTokenProvider: AdminTokenProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Install global crash handler first — catches any subsequent crash
        GlobalCrashHandler.install(this)

        // Initialize PC Control — loads saved IP/port/key from SharedPreferences
        PcControlMain.init(this, "")

        // Schedule the periodic worker that fires saved-device automations.
        // 15 min is the WorkManager floor; the worker's own ±3 min window handles jitter.
        val req = PeriodicWorkRequestBuilder<PcScheduleWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PcScheduleWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )

        appScope.launch {
            try {
                PocketBaseInitializer.setAdminTokenProvider(adminTokenProvider)
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