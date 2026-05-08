package com.example.ritik_2.appupdate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppUpdateActivity : FragmentActivity() {

    private val vm: AppUpdateViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Hard permission gate (same pattern as RoleManagementActivity) ──────
        // Only System_Administrator OR users with PERM_MANAGE_APP_UPDATES can enter.
        if (!requirePermission(
                authRepository,
                rule = { role, perms, dba ->
                    dba ||
                    PermissionGuard.isSystemAdmin(role) ||
                    Permissions.PERM_MANAGE_APP_UPDATES in perms
                },
                deniedMessage = "App Update Management — System Administrator access required"
            )
        ) return

        val session = authRepository.getSession()
        val canActivate = authRepository.isDbAdmin() ||
                PermissionGuard.isSystemAdmin(session?.role ?: "")

        setContent {
            ITConnectTheme {
                AppUpdateScreen(
                    viewModel = vm,
                    canActivate = canActivate,
                    onBack = { finish() }
                )
            }
        }
    }
}
