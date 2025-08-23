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
import com.example.ritik_2.theme.Ritik_2Theme
import androidx.core.net.toUri

class ContactActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ritik_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContactScreen(
                        onEmailClick = { email ->
                            openEmail(email)
                        },
                        onPhoneClick = { phone ->
                            dialPhone(phone)
                        },
                        onLocationClick = { address ->
                            openMaps(address)
                        }
                    )
                }
            }
        }
    }

    private fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$email")  // Use data property, not setData()
            intent.putExtra(Intent.EXTRA_SUBJECT, "Hello from your app!")
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No email app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun dialPhone(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phone")  // Use data property
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No dialer app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMaps(address: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("geo:0,0?q=${Uri.encode(address)}")  // Use data property
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No map app available", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ContactActivityPreview() {
    Ritik_2Theme {
        ContactScreen(
            onEmailClick = {},
            onPhoneClick = {},
            onLocationClick = {}
        )
    }
}