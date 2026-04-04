package com.example.ritik_2.administrator.manageuser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.model.UserProfile
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.drawer.AppDrawerWrapper
import com.example.ritik_2.theme.ITConnectTheme
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
                    com.example.ritik_2.login.LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            else -> startActivity(
                Intent(this, com.example.ritik_2.main.MainActivity::class.java).apply {
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