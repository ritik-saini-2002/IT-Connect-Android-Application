package com.example.ritik_2.nagios

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.BuildConfig
import com.example.ritik_2.theme.Ritik_2Theme
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ConnectActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("nagios_connect", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // After "Disconnect & logout" — always show the form pre-filled with the old server
        val showForm       = intent.getBooleanExtra("SHOW_FORM", false)
        val prefillUrl     = intent.getStringExtra("PREFILL_URL")
        val prefillUsername = intent.getStringExtra("PREFILL_USERNAME")

        if (showForm) {
            // Show the connect screen pre-filled so the user can re-connect or switch server
            showConnectScreen(prefillUrl, prefillUsername)
            return
        }

        // 1. Already configured (saved credentials) → go straight to main
        val savedUrl  = prefs.getString("base_url", null)
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        if (!savedUrl.isNullOrBlank() && !savedUser.isNullOrBlank() && !savedPass.isNullOrBlank()) {
            launchMain(savedUrl, savedUser, savedPass)
            return
        }

        // 2. BuildConfig has default credentials → auto-connect silently on first launch
        val defaultUrl  = BuildConfig.NAGIOS_DEFAULT_URL
        val defaultUser = BuildConfig.NAGIOS_DEFAULT_USERNAME
        val defaultPass = BuildConfig.NAGIOS_DEFAULT_PASSWORD
        if (defaultUrl.isNotBlank() && defaultUser.isNotBlank() && defaultPass.isNotBlank()) {
            saveCredentials(defaultUrl, defaultUser, defaultPass)
            launchMain(defaultUrl, defaultUser, defaultPass)
            return
        }

        // 3. Nothing saved, no defaults → show blank connect screen
        showConnectScreen(null, null)
    }

    private fun showConnectScreen(prefillUrl: String?, prefillUsername: String?) {
        setContent {
            Ritik_2Theme {
                ConnectScreen(
                    prefillUrl      = prefillUrl,
                    prefillUsername = prefillUsername,
                    onConnect       = { url, user, pass ->
                        saveCredentials(url, user, pass)
                        launchMain(url, user, pass)
                    }
                )
            }
        }
    }

    private fun saveCredentials(url: String, user: String, pass: String) {
        prefs.edit()
            .putString("base_url", url)
            .putString("username", user)
            .putString("password", pass)
            .apply()
    }

    private fun launchMain(url: String, user: String, pass: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("BASE_URL", url)
            putExtra("USERNAME", user)
            putExtra("PASSWORD", pass)
        }
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    prefillUrl:      String? = null,
    prefillUsername: String? = null,
    onConnect: (url: String, user: String, pass: String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Pre-fill URL: use what was passed (e.g. after disconnect), then BuildConfig, then placeholder
    var url      by remember {
        mutableStateOf(
            prefillUrl ?: BuildConfig.NAGIOS_DEFAULT_URL.ifBlank { "http://192.168.1.100" }
        )
    }
    // Pre-fill username: use what was passed, then BuildConfig
    var username by remember {
        mutableStateOf(prefillUsername ?: BuildConfig.NAGIOS_DEFAULT_USERNAME)
    }
    // Password is NEVER pre-filled — user must type it every time after disconnect
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

        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(130, TimeUnit.SECONDS)
                    .readTimeout(130, TimeUnit.SECONDS)
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = "Nagios Monitor",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Text(
                text     = if (prefillUrl != null) "Reconnect or switch to a different server"
                           else "Connect to your Nagios server",
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 36.dp)
            )

            // Server URL
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it; errorMsg = null },
                label         = { Text("Nagios server URL") },
                placeholder   = { Text("http://192.168.7.247") },
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

            // Password — always blank, user must enter it
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; errorMsg = null },
                label         = { Text("Password") },
                placeholder   = { Text("Enter password") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector        = if (showPassword) Icons.Default.VisibilityOff
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

            // Error card
            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors   = CardDefaults.cardColors(
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
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting...")
                } else {
                    Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text     = "Uses your existing Nagios web credentials",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
