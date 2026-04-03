package com.example.ritik_2.macnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ritik_2.theme.ITConnectTheme

class MACNetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
                MACNetScreen()
            }
        }
    }
}