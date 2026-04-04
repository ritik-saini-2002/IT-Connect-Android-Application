package com.example.ritik_2.administrator.departmentmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DepartmentActivity : ComponentActivity() {
    private val vm: DepartmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
                DepartmentScreen(viewModel = vm)
            }
        }
    }
}