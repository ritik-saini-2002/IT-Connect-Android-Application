package com.example.ritik_2.contact

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ritik_2.theme.ITConnectTheme

class ContactActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ITConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContactScreen(
                        onEmailClick    = { email   -> openEmail(email) },
                        onPhoneClick    = { phone   -> dialPhone(phone) },
                        onLocationClick = { address -> openMaps(address) }
                    )
                }
            }
        }
    }

    private fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Hello from IT Connect!")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dialPhone(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            })
        } catch (e: Exception) {
            Toast.makeText(this, "No dialer app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMaps(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            })
        } catch (e: Exception) {
            Toast.makeText(this, "No map app available", Toast.LENGTH_SHORT).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ContactActivityPreview() {
    ITConnectTheme {
        ContactScreen(
            onEmailClick    = {},
            onPhoneClick    = {},
            onLocationClick = {}
        )
    }
}