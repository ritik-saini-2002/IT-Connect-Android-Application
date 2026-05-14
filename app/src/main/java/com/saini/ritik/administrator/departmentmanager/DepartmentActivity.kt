package com.saini.ritik.administrator.departmentmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.requirePermission
import com.saini.ritik.theme.Ritik_2Theme
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
            Ritik_2Theme() {
                DepartmentScreen(viewModel = vm)
            }
        }
    }
}
