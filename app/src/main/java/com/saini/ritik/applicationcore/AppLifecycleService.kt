package com.saini.ritik.applicationcore

import android.app.Service
import android.content.Intent
import androidx.work.WorkManager
import com.saini.ritik.chat.ChatNotificationService
import com.saini.ritik.core.AdminTokenProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
class AppLifecycleService : Service() {

    // Use EntryPointAccessors instead of @Inject lateinit — this is safe even
    // when onTaskRemoved fires before onCreate (e.g. after process kill/restart).
    private val adminTokenProvider: AdminTokenProvider
        get() = EntryPointAccessors.fromApplication(
            applicationContext,
            AppLifecycleEntryPoint::class.java
        ).adminTokenProvider()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY — do NOT restart this service automatically if killed.
        // It will be re-started by MainActivity.onCreate() on next app launch.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away — shut everything down cleanly.
        WorkManager.getInstance(this).cancelAllWork()
        adminTokenProvider.stopKeepAlive()
        stopService(Intent(this, ChatNotificationService::class.java))
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppLifecycleEntryPoint {
    fun adminTokenProvider(): AdminTokenProvider
}