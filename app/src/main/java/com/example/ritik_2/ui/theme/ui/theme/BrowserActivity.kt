package com.example.ritik_2.ui.theme.ui.theme

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ritik_2.modules.BrowserViewModel
import com.example.ritik_2.modules.SavedPassword

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BrowserApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp() {
    val viewModel: BrowserViewModel = viewModel()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlText by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.currentUrl.value) {
        urlText = viewModel.currentUrl.value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                )
            )
    ) {
        // Top App Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(
                            onClick = { webView?.goBack() },
                            enabled = viewModel.canGoBack.value
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = if (viewModel.canGoBack.value) Color(0xFF667eea) else Color.Gray)
                        }
                        IconButton(
                            onClick = { webView?.goForward() },
                            enabled = viewModel.canGoForward.value
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward", tint = if (viewModel.canGoForward.value) Color(0xFF667eea) else Color.Gray)
                        }
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color(0xFF667eea))
                        }
                    }
                    Row {
                        IconButton(onClick = { viewModel.showBookmarkDialog() }) {
                            Icon(Icons.Default.Star, contentDescription = "Bookmark", tint = Color(0xFFFFD700))
                        }
                        IconButton(onClick = { viewModel.showPasswordManager() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Password Manager", tint = Color(0xFF667eea))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    placeholder = { Text("Enter URL or search...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF667eea))
                    },
                    trailingIcon = {
                        if (viewModel.isLoading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF667eea),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                viewModel.navigateToUrlMobile(urlText)
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Go", tint = Color(0xFF667eea))
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }
        }

        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            viewModel.onPageStarted()
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            viewModel.onPageFinished(view?.title)
                            viewModel.updateNavigationState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webView = this
                }
            },
            update = { webView ->
                webView.loadUrl(viewModel.currentUrl.value)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        )
    }

    // Dialogs
    if (viewModel.showPasswordDialog.value) {
        PasswordSaveDialog(
            onDismiss = { viewModel.hidePasswordDialog() },
            onSave = { website, username, password ->
                viewModel.savePassword(website, username, password)
            }
        )
    }
    if (viewModel.showBookmarkDialog.value) {
        BookmarkDialog(
            currentUrl = viewModel.currentUrl.value,
            currentTitle = viewModel.pageTitle.value,
            onDismiss = { viewModel.hideBookmarkDialog() },
            onSave = { title, url ->
                viewModel.addBookmark(title, url)
            }
        )
    }
    if (viewModel.showPasswordManager.value) {
        PasswordManagerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.hidePasswordManager() }
        )
    }
}

// --- UI Dialogs (unchanged except for scroll/spacing tweaks) ---

@Composable
fun PasswordSaveDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Save Password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF667eea))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username/Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (website.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onSave(website, username, password)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667eea)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun BookmarkDialog(
    currentUrl: String,
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var url by remember { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Add Bookmark", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && url.isNotBlank()) {
                                onSave(title, url)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save", color = Color.Black) }
                }
            }
        }
    }
}

@Composable
fun PasswordManagerSheet(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    var showPasswords by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Password Manager", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF667eea))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Passwords")
                    Switch(
                        checked = showPasswords,
                        onCheckedChange = { showPasswords = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF667eea))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(viewModel.savedPasswords) { password ->
                        PasswordItem(
                            password = password,
                            showPassword = showPasswords,
                            onDelete = { viewModel.deletePassword(password.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.showPasswordDialog() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667eea)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Password")
                }
            }
        }
    }
}

@Composable
fun PasswordItem(
    password: SavedPassword,
    showPassword: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(password.website, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF667eea))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }
            Text("Username: ${password.username}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text("Password: ${if (showPassword) password.password else "•".repeat(password.password.length)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}