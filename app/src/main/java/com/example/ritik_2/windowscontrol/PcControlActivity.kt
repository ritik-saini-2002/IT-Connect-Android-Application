package com.example.ritik_2.windowscontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.theme.ITConnectTheme

class PcControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {       // ← your existing theme
                PcControlEntry()   // ← the whole pccontrol package
            }
        }
    }
}