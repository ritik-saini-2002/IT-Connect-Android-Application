package com.example.ritik_2.administrator.manageuser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

// ── Activity ──────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class ManageUserActivity : ComponentActivity() {
    private val vm: ManageUserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ITConnectTheme { ManageUserScreen(vm) } }
    }

    companion object {
        fun createIntent(ctx: Context) = Intent(ctx, ManageUserActivity::class.java)
    }
}