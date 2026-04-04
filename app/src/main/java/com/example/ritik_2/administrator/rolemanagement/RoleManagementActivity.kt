package com.example.ritik_2.administrator.rolemanagement

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RoleManagementActivity : ComponentActivity() {

    private val vm: RoleManagementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITConnectTheme {
                RoleManagementScreen(
                    viewModel    = vm,
                    onRoleChanged = { userName, oldRole, newRole ->
                        Toast.makeText(this,
                            "$userName moved from $oldRole → $newRole",
                            Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}