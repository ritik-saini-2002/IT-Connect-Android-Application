// ── ReportsActivity.kt ────────────────────────────────────────────────────────
package com.saini.ritik.administrator.reports

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.PermissionGuard
import com.saini.ritik.core.requirePermission
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.theme.Ritik_2Theme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ReportsActivity : ComponentActivity() {

    private val vm: ReportsViewModel by viewModels()

    @Inject lateinit var authRepository: AuthRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.pendingExport?.let { doExport(it) }
        else Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requirePermission(authRepository,
                rule = { role, perms, dba ->
                    PermissionGuard.canAccessAdminPanel(role, dba) &&
                            (dba || PermissionGuard.isSystemAdmin(role) ||
                                    perms.any { it in listOf(
                                        Permissions.PERM_VIEW_REPORTS, Permissions.PERM_VIEW_ANALYTICS,
                                        Permissions.PERM_VIEW_TEAM_ANALYTICS, Permissions.PERM_VIEW_HR_ANALYTICS,
                                        Permissions.PERM_GENERATE_REPORTS, Permissions.PERM_EXPORT_DATA) })
                },
                deniedMessage = "Reports — analytics access required"))
            return

        setContent {
            Ritik_2Theme() {
                ReportsScreen(
                    viewModel   = vm,
                    onBack      = { finish() },
                    onExport    = { format -> handleExport(format) },
                    onShareFile = { uri -> shareFile(uri) }
                )
            }
        }
    }

    private fun handleExport(format: ExportFormat) {
        vm.pendingExport = format
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(perm)
                return
            }
        }
        doExport(format)
    }

    private fun doExport(format: ExportFormat) {
        vm.export(format) { content, filename ->
            try {
                val uri = saveToDownloads(content, filename, format.mimeType)
                Toast.makeText(this, "Saved: $filename", Toast.LENGTH_LONG).show()
                shareFile(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveToDownloads(content: String, filename: String, mimeType: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            contentResolver.openOutputStream(uri)!!.use { it.write(content.toByteArray()) }
            cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, cv, null, null)
            uri
        } else {
            val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, filename)
            file.writeText(content)
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Report"))
    }
}

// ── ExportFormat ──────────────────────────────────────────────────────────────

enum class ExportFormat(val label: String, val ext: String, val mimeType: String) {
    CSV("CSV", "csv", "text/csv"),
    JSON("JSON", "json", "application/json"),
    TXT("Plain Text", "txt", "text/plain")
}