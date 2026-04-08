package com.example.ritik_2.administrator.companysettings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CompanySettingsActivity : ComponentActivity() {

    private val vm: CompanySettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
                CompanySettingsScreen(
                    viewModel   = vm,
                    onBack      = { finish() },
                    onShowToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}