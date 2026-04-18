package com.example.ritik_2.administrator.companysettings

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
class CompanySettingsActivity : ComponentActivity() {

    private val vm: CompanySettingsViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessAdminPanel(role, dba) &&
                            (dba || PermissionGuard.isSystemAdmin(role) ||
                                    Permissions.PERM_MANAGE_COMPANIES in perms)
                },
                deniedMessage = "Company Settings — admin access required"))
            return

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
