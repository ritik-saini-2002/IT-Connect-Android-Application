package com.example.nagiosmonitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ConnectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already configured, skip straight to MainActivity
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val savedUrl  = prefs[PrefKeys.BASE_URL]
            val savedUser = prefs[PrefKeys.USERNAME]
            val savedPass = prefs[PrefKeys.PASSWORD]
            if (!savedUrl.isNullOrBlank() && !savedUser.isNullOrBlank() && !savedPass.isNullOrBlank()) {
                launchMain(savedUrl, savedUser, savedPass)
                return@launch
            }

            // Otherwise show login UI
            setContent {
                Ritik_2Theme {
                    ConnectScreen(
                        onConnect = { url, user, pass ->
                            lifecycleScope.launch {
                                saveCredentials(url, user, pass)
                                launchMain(url, user, pass)
                            }
                        }
                    )
                }
            }
        }
    }

    private suspend fun saveCredentials(url: String, user: String, pass: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.BASE_URL]  = url
            prefs[PrefKeys.USERNAME]  = user
            prefs[PrefKeys.PASSWORD]  = pass
        }
    }

    private fun launchMain(url: String, user: String, pass: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("BASE_URL",  url)
            putExtra("USERNAME",  user)
            putExtra("PASSWORD",  pass)
        }
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(onConnect: (url: String, user: String, pass: String) -> Unit) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    var url          by remember { mutableStateOf("http://192.168.1.100") }
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading    by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    fun testAndConnect() {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            errorMsg = "All fields are required"
            return
        }
        isLoading = true
        errorMsg  = null

        // Test connection in background thread
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val testUrl = url.trimEnd('/') + "/nagios/cgi-bin/statusjson.cgi?query=hostcount"
                val request = Request.Builder()
                    .url(testUrl)
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                val response = client.newCall(request).execute()
                isLoading = false
                if (response.isSuccessful) {
                    onConnect(url.trimEnd('/'), username, password)
                } else {
                    errorMsg = when (response.code) {
                        401  -> "Invalid credentials (401 Unauthorized)"
                        404  -> "Nagios CGI not found — check URL"
                        else -> "Server error: ${response.code}"
                    }
                }
                response.close()
            } catch (e: Exception) {
                isLoading = false
                errorMsg  = "Cannot reach server: ${e.message}"
            }
        }.start()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text       = "Nagios Monitor",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Text(
                text     = "Connect to your Nagios server",
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 36.dp)
            )

            // Server URL
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it; errorMsg = null },
                label         = { Text("Nagios server URL") },
                placeholder   = { Text("http://192.168.1.100") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(12.dp))

            // Username
            OutlinedTextField(
                value         = username,
                onValueChange = { username = it; errorMsg = null },
                label         = { Text("Username") },
                placeholder   = { Text("nagiosadmin") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; errorMsg = null },
                label         = { Text("Password") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus(); testAndConnect() }
                )
            )

            // Error message
            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = errorMsg!!,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Connect button
            Button(
                onClick  = { testAndConnect() },
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color    = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting...")
                } else {
                    Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Helper note
            Text(
                text     = "Uses your existing Nagios web credentials",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
