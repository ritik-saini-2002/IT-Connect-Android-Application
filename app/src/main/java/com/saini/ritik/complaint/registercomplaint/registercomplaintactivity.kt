package com.saini.ritik.complaint.registercomplaint

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for raising/registering a new complaint ticket.
 * Uses Ritik_2Theme (declared in manifest). Follows the same MVVM pattern
 * as ContactActivity and other feature activities.
 */
@AndroidEntryPoint
class RegisterComplaintActivity : ComponentActivity() {

    private val vm: RegisterComplaintViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ITConnectTheme {
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                // ── File picker launchers ──────────────────────────────────────
                val imagePickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents()
                ) { uris: List<Uri> ->
                    vm.addAttachments(this@RegisterComplaintActivity, uris, isImage = true)
                }

                val pdfPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents()
                ) { uris: List<Uri> ->
                    vm.addAttachments(this@RegisterComplaintActivity, uris, isImage = false)
                }

                // ── Permission launcher (for Android 13+ media access) ────────
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    if (grants.values.all { it }) {
                        imagePickerLauncher.launch("image/*")
                    } else {
                        Toast.makeText(this, "Permission needed to pick files", Toast.LENGTH_SHORT).show()
                    }
                }

                RegisterComplaintScreen(
                    uiState          = uiState,
                    onTitleChange    = vm::onTitleChange,
                    onDescChange     = vm::onDescriptionChange,
                    onCategoryChange = vm::onCategoryChange,
                    onPriorityChange = vm::onPriorityChange,
                    onDepartmentChange = vm::onDepartmentChange,
                    onPickImages     = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val perms = arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                            if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                permissionLauncher.launch(perms)
                            }
                        } else {
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    onPickPdf        = { pdfPickerLauncher.launch("application/pdf") },
                    onRemoveAttachment = vm::removeAttachment,
                    onSubmit         = {
                        vm.submitComplaint(
                            onSuccess = {
                                Toast.makeText(this, "Complaint submitted successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onError = { msg ->
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onBack           = { finish() }
                )
            }
        }
    }
}
