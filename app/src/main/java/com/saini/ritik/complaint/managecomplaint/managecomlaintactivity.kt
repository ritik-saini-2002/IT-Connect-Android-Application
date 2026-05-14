package com.saini.ritik.complaint.managecomplaint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for managing / viewing all complaint tickets.
 * Supports filtering, status management, assignment, and detail view.
 * Uses Ritik_2Theme (declared in manifest).
 */
@AndroidEntryPoint
class ManageComplaintActivity : ComponentActivity() {

    private val vm: ManageComplaintViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ITConnectTheme {
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                ManageComplaintScreen(
                    uiState            = uiState,
                    onFilterChange     = vm::onFilterChange,
                    onSearchChange     = vm::onSearchChange,
                    onStatusChange     = { id, status ->
                        vm.updateStatus(id, status,
                            onSuccess = { Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show() },
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onAssignUser       = { complaintId, userId, userName ->
                        vm.assignUser(complaintId, userId, userName,
                            onSuccess = { Toast.makeText(this, "Assigned to $userName", Toast.LENGTH_SHORT).show() },
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onDeleteComplaint  = { id ->
                        vm.deleteComplaint(id,
                            onSuccess = { Toast.makeText(this, "Complaint deleted", Toast.LENGTH_SHORT).show() },
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onAddComment       = { id, comment ->
                        vm.addComment(id, comment,
                            onSuccess = {},
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onUpdateResolution = { id, resolution ->
                        vm.updateResolution(id, resolution,
                            onSuccess = { Toast.makeText(this, "Resolution saved", Toast.LENGTH_SHORT).show() },
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onEditComplaint    = { id, title, desc, priority, category ->
                        vm.editComplaint(id, title, desc, priority, category,
                            onSuccess = { Toast.makeText(this, "Complaint updated", Toast.LENGTH_SHORT).show() },
                            onError   = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onRefresh          = vm::reload,
                    onBack             = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }
}