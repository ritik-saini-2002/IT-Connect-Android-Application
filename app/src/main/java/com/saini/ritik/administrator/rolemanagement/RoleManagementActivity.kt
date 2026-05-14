package com.saini.ritik.administrator.rolemanagement

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.requirePermission
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.theme.Ritik_2Theme
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
            Ritik_2Theme() {
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
