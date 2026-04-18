package com.example.ritik_2.administrator.rolemanagement

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.data.model.Permissions
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RoleManagementActivity : ComponentActivity() {

    private val vm: RoleManagementViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessAdminPanel(role, dba) &&
                            (dba || PermissionGuard.isSystemAdmin(role) ||
                                    perms.any { it in listOf(
                                        Permissions.PERM_MANAGE_ROLES,
                                        Permissions.PERM_MANAGE_PERMISSIONS) })
                },
                deniedMessage = "Role Management — admin access required"))
            return

        // Whether the *current* admin can grant/revoke permissions on roles.
        // System_Administrator (and DB admin) are the only ones permitted to
        // open the permission editor; regular admins can still rename/move
        // users between roles but cannot edit the permission set itself.
        val session = authRepository.getSession()
        val canManagePermissions =
            authRepository.isDbAdmin() ||
                PermissionGuard.isSystemAdmin(session?.role ?: "") ||
                Permissions.PERM_GRANT_REVOKE_ANY_PERMISSION in (session?.permissions ?: emptyList())

        setContent {
            ITConnectTheme {
                RoleManagementScreen(
                    viewModel             = vm,
                    canManagePermissions  = canManagePermissions,
                    onRoleChanged         = { userName, oldRole, newRole ->
                        Toast.makeText(this,
                            "$userName moved from $oldRole → $newRole",
                            Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
