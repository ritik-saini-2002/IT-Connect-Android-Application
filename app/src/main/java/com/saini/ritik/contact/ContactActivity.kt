package com.saini.ritik.contact

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saini.ritik.appupdate.UpdateInfo  // ✅ FIX: removed wrong androidx.security.state.UpdateInfo import
import com.saini.ritik.BuildConfig
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ContactActivity : ComponentActivity() {

    private val vm: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ITConnectTheme {
                val updateState by vm.updateState.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    ContactScreen(
                        onEmailClick       = { email   -> openEmail(email) },
                        onPhoneClick       = { phone   -> dialPhone(phone) },
                        onLocationClick    = { address -> openMaps(address) },
                        currentVersionName = BuildConfig.VERSION_NAME,
                        updateCheckState   = updateState,
                        onCheckForUpdate   = { vm.checkForUpdate() },
                        onInstallUpdate    = { info -> handleInstall(info) }
                    )
                }
            }
        }
    }

    // ✅ FIX: type is now com.saini.ritik.appupdate.UpdateInfo — matches ViewModel exactly
    private fun handleInstall(info: UpdateInfo) {
        vm.downloadAndInstall(
            info    = info,
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun openEmail(email: String) {
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                    putExtra(Intent.EXTRA_SUBJECT, "Hello from IT Connect!")
                }, "Send Email"
            ))
        } catch (_: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dialPhone(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") })
        } catch (_: Exception) {
            Toast.makeText(this, "No dialer app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMaps(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No map app available", Toast.LENGTH_SHORT).show()
        }
    }
}