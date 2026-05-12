package com.saini.ritik.administrator.newusercreation

import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserScreen(
    isCreating    : Boolean,
    companyName   : String,
    error         : String?,
    availableRoles: List<String>,
    availableDepts: List<String>,
    onCreateUser  : (name: String, email: String, role: String,
                     department: String, designation: String, password: String) -> Unit
) {
    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var showPassword    by remember { mutableStateOf(false) }
    var role            by remember(availableRoles) { mutableStateOf(availableRoles.firstOrNull() ?: "Employee") }
    var department      by remember { mutableStateOf("") }
    var designation     by remember { mutableStateOf("") }
    var showErrors      by remember { mutableStateOf(false) }
    var showSuccess     by remember { mutableStateOf(false) }
    var lastCreatedName by remember { mutableStateOf("") }
    var wasCreating     by remember { mutableStateOf(false) }

    val isValid = name.length >= 2
            && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            && department.isNotBlank()
            && designation.isNotBlank()
            && password.length >= 6

    LaunchedEffect(isCreating) {
        if (wasCreating && !isCreating && error == null && lastCreatedName.isNotEmpty()) {
            showSuccess = true; delay(2500); showSuccess = false
            name = ""; email = ""; password = ""; department = ""
            designation = ""; lastCreatedName = ""; showErrors = false
            role = availableRoles.firstOrNull() ?: "Employee"
        }
        wasCreating = isCreating
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )))
                    .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Color.White.copy(0.2f)),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.PersonAdd, null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Create New User", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (companyName.isNotBlank()) "Administrator Panel · $companyName"
                            else "Administrator Panel · Loading…",
                            fontSize = 12.sp, color = Color.White.copy(0.75f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Success banner
            AnimatedVisibility(visible = showSuccess,
                enter = slideInVertically { -it } + fadeIn(),
                exit  = slideOutVertically { -it } + fadeOut()) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2E7D32).copy(0.1f)),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("User created successfully!",
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                            Text("$lastCreatedName will complete profile on first login.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32).copy(0.8f))
                        }
                    }
                }
            }

            // Error banner
            AnimatedVisibility(visible = error != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit  = slideOutVertically { -it } + fadeOut()) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Loading state
            if (companyName.isBlank() && error == null) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading admin profile…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Account section
            FormSection("Account Information", Icons.Default.Person) {
                OutlinedTextField(
                    value = companyName, onValueChange = {},
                    label = { Text("Company") },
                    leadingIcon = { Icon(Icons.Default.Business, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    readOnly = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f))
                )
                CUTextField(name, { name = it }, "Full Name *", Icons.Default.Person,
                    isError = showErrors && name.length < 2, errorMsg = "Minimum 2 characters")
                CUTextField(email, { email = it }, "Email *", Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    isError = showErrors && !Patterns.EMAIL_ADDRESS.matcher(email).matches(),
                    errorMsg = "Enter a valid email")
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password *") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, null,
                            tint = if (showErrors && password.length < 6)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    singleLine = true, isError = showErrors && password.length < 6
                )
                if (showErrors && password.length < 6)
                    Text("Minimum 6 characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp))
            }

            // Work section — departments and roles from DB
            FormSection("Work Information", Icons.Default.Work) {
                // Department dropdown (from DB)
                CUDropdown(
                    options  = availableDepts,
                    selected = department,
                    onSelect = { department = it },
                    label    = "Department *",
                    icon     = Icons.Default.Groups,
                    isError  = showErrors && department.isBlank()
                )
                CUTextField(designation, { designation = it }, "Designation *",
                    Icons.Default.Badge,
                    isError = showErrors && designation.isBlank(), errorMsg = "Required")
                // Role dropdown (from DB)
                CUDropdown(
                    options  = availableRoles,
                    selected = role,
                    onSelect = { role = it },
                    label    = "Role",
                    icon     = Icons.Default.ManageAccounts
                )
            }

            // Info card
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "The new user will be prompted to complete their profile on first login.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Submit
            Button(
                onClick = {
                    showErrors = true
                    if (isValid && companyName.isNotBlank()) {
                        lastCreatedName = name
                        onCreateUser(name.trim(), email.trim(), role,
                            department, designation.trim(), password)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = !isCreating && companyName.isNotBlank()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Creating…")
                } else {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create User", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun FormSection(title: String, icon: ImageVector,
                        content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(0.15f))
            content()
        }
    }
}

@Composable
private fun CUTextField(
    value        : String, onValueChange: (String) -> Unit,
    label        : String, icon: ImageVector,
    keyboardType : KeyboardType = KeyboardType.Text,
    isError      : Boolean = false, errorMsg: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, null,
                tint = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true, shape = RoundedCornerShape(12.dp), isError = isError
    )
    if (isError && errorMsg != null)
        Text(errorMsg, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CUDropdown(
    options : List<String>, selected: String, onSelect: (String) -> Unit,
    label   : String, icon: ImageVector, isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRot by animateFloatAsState(
        if (expanded) 180f else 0f, tween(200), label = "arrow")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {},
            label = { Text(label) },
            leadingIcon = {
                Icon(icon, null,
                    tint = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, null,
                    Modifier.rotate(arrowRot), tint = MaterialTheme.colorScheme.primary)
            },
            readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp), isError = isError
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                    leadingIcon = {
                        if (opt == selected)
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
    if (isError && selected.isBlank())
        Text("Required", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 8.dp))
}