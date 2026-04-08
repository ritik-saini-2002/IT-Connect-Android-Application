package com.example.ritik_2.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.ritik_2.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MemberPickerActivity : ComponentActivity() {

    private val vm: MemberPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sc      = intent.getStringExtra(EXTRA_SC)       ?: ""
        val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)

        vm.load(sc)

        setContent {
            ITConnectTheme {
                MemberPickerScreen(
                    viewModel   = vm,
                    isGroupMode = isGroup,
                    onConfirm   = { ids, names, groupName ->
                        val result = Intent().apply {
                            putStringArrayListExtra(RESULT_IDS,        ArrayList(ids))
                            putStringArrayListExtra(RESULT_NAMES,      ArrayList(names))
                            putExtra(RESULT_GROUP_NAME, groupName)
                            putExtra(RESULT_IS_GROUP,   isGroup)
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SC          = "sc"
        const val EXTRA_IS_GROUP    = "is_group"
        const val RESULT_IDS        = "member_ids"
        const val RESULT_NAMES      = "member_names"
        const val RESULT_GROUP_NAME = "group_name"
        const val RESULT_IS_GROUP   = "is_group_result"

        fun newIntent(
            ctx             : Context,
            sanitizedCompany: String,
            isGroupMode     : Boolean
        ) = Intent(ctx, MemberPickerActivity::class.java).apply {
            putExtra(EXTRA_SC,       sanitizedCompany)
            putExtra(EXTRA_IS_GROUP, isGroupMode)
        }
    }
}