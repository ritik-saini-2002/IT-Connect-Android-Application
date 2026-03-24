package com.example.ritik_2.complaint.newcomplaintregistration

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ritik_2.complaint.newcomplaintregistration.models.ComplaintFormEvent
import com.example.ritik_2.complaint.newcomplaintregistration.models.ComplaintFormViewModel
import com.google.firebase.auth.FirebaseAuth

/**
 * NewRegisterComplaintActivity
 *
 * Responsibilities (thin):
 *   1. Launch the file picker and relay the URI to the ViewModel
 *   2. Create the ViewModel and load the current user
 *   3. Collect uiState and pass it + callbacks to the Screen composable
 *   4. Show a Toast and finish() on success
 *
 * Zero business logic lives here — all in ComplaintFormViewModel.
 * Zero Compose UI code lives here — all in NewRegisterComplaintScreen.
 */
class NewRegisterComplaintActivity : ComponentActivity() {

    private val viewModel: ComplaintFormViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()

    // File picker — forwards the result to the ViewModel
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onEvent(ComplaintFormEvent.FilePicked(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid ?: run { finish(); return }

        // Load user data + profile picture once
        viewModel.loadUser(uid)

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                NewRegisterComplaintScreen(
                    uiState = uiState,
                    onEvent = { event ->
                        when (event) {
                            // FilePicked from UI means "open picker" — Activity handles this
                            is ComplaintFormEvent.FilePicked -> filePicker.launch("*/*")
                            else -> viewModel.onEvent(event)
                        }
                    },
                    onSubmit = {
                        viewModel.submitComplaint(
                            contentResolver = contentResolver,
                            onSuccess = {
                                Toast.makeText(
                                    this,
                                    "Complaint submitted! Department head notified.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        )
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
