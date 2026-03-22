package com.example.ritik_2

import android.app.Application
import com.example.ritik_2.windowscontrol.PcControlMain
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp   // ← Fixes: "Hilt Activity must be attached to @HiltAndroidApp"
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ← Fixes: "PcControlMain not initialized"
        PcControlMain.init(
            context = this,
            pcIp = ""   // User sets IP in Settings screen inside the app
        )
    }
}