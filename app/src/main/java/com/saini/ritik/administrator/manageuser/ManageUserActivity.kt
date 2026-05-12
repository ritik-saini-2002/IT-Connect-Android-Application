package com.saini.ritik.administrator.manageuser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.requirePermission
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.data.model.UserProfile
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.drawer.AppDrawerWrapper
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ManageUserActivity : ComponentActivity() {

    private val vm: ManageUserViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var dataSource    : AppDataSource

    // Result launcher — called when ProfileCompletionActivity finishes
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uid = result.data?.getStringExtra(EXTRA_EDITED_USER_ID)
            if (!uid.isNullOrBlank()) vm.refreshUser(uid)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessAdminPanel(role, dba) &&
                            (dba || PermissionGuard.isSystemAdmin(role) ||
                                    perms.any { it in listOf(
                                        Permissions.PERM_MODIFY_USER, Permissions.PERM_DELETE_USER,
                                        Permissions.PERM_VIEW_ALL_USERS, Permissions.PERM_MODIFY_TEAM_USER,
                                        Permissions.PERM_MANAGE_EMPLOYEES) })
                },
                deniedMessage = "User Management — admin access required"))
            return

        setContent {
            ITConnectTheme {
                val session = remember { authRepository.getSession() }
                var profile by remember { mutableStateOf<UserProfile?>(null) }
                LaunchedEffect(session?.userId) {
                    session?.userId?.let { uid ->
                        dataSource.getUserProfile(uid).onSuccess { profile = it }
                    }
                }
                AppDrawerWrapper(
                    session     = session,
                    profile     = profile,
                    currentItem = "manage_users",
                    permissions = profile?.permissions ?: emptyList(),
                    onNavigate  = { handleDrawerNav(it) }
                ) {
                    ManageUserScreen(vm)
                }
            }
        }
    }

    private fun handleDrawerNav(id: String) {
        when (id) {
            "manage_users" -> {}
            "logout" -> CoroutineScope(Dispatchers.Main).launch {
                authRepository.logout()
                startActivity(Intent(this@ManageUserActivity,
                    com.saini.ritik.login.LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            else -> startActivity(
                Intent(this, com.saini.ritik.main.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", id)
                }
            )
        }
    }

    companion object {
        const val EXTRA_EDITED_USER_ID = "edited_user_id"
        fun createIntent(ctx: Context) = Intent(ctx, ManageUserActivity::class.java)
    }
}