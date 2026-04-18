package com.example.ritik_2.administrator.databasemanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.PermissionGuard
import com.example.ritik_2.core.requirePermission
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// DBTab, DBRecord, DBUiState are defined in DatabaseManagerModels.kt
// DatabaseManagerViewModel is defined in DatabaseManagerViewModel.kt
// DatabaseManagerScreen  is defined in DatabaseManagerScreen.kt

@AndroidEntryPoint
class DatabaseManagerActivity : ComponentActivity() {

    private val vm: DatabaseManagerViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessDatabaseManager(role, perms, dba)
                },
                deniedMessage = "Database Manager — System Administrator only"))
            return

        setContent {
            ITConnectTheme {
                DatabaseManagerScreen(
                    vm          = vm,
                    onShowToast = { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
