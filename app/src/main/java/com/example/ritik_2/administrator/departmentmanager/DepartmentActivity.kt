package com.example.ritik_2.administrator.departmentmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DepartmentActivity : ComponentActivity() {
    private val vm: DepartmentViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, _, dba -> PermissionGuard.canAccessAdminPanel(role, dba) },
                deniedMessage = "Department Management — admin access required"))
            return

        setContent {
            ITConnectTheme {
                DepartmentScreen(viewModel = vm)
            }
        }
    }
}
