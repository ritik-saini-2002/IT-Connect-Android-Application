package com.saini.ritik.registration

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saini.ritik.data.model.Permissions
import com.saini.ritik.data.model.RegistrationRequest
import com.saini.ritik.profile.profilecompletion.ELIGIBLE_IMAGE_MIME_TYPES
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RegistrationScreen(
    onRegisterClick       : (RegistrationRequest) -> Unit,
    onLoginClick          : () -> Unit,
    registrationState     : StateFlow<RegistrationState>,
    // Pass similar company names for duplicate warning (from ViewModel)
    similarCompanyNames   : List<String> = emptyList(),
    onCompanyNameChanged  : (String) -> Unit = {}
) {
    val state     = registrationState.collectAsState().value
    val isLoading = state is RegistrationState.Loading

    var name              by remember { mutableStateOf("") }
    var email             by remember { mutableStateOf("") }
    var password          by remember { mutableStateOf("") }
    var phone             by remember { mutableStateOf("") }
    var designation       by remember { mutableStateOf("") }
    var company           by remember { mutableStateOf("") }
    var department        by remember { mutableStateOf("") }
    var role              by remember { mutableStateOf("Employee") }
    var imageUri          by remember { mutableStateOf<Uri?>(null) }
    var imageBytes        by remember { mutableStateOf<ByteArray?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showMergeDialog   by remember { mutableStateOf(false) }
    var proceedDespiteDup by remember { mutableStateOf(false) }

    val context     = LocalContext.current
    val scrollState = rememberScrollState()

    // Eligible image picker (JPEG, PNG, WebP only)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            imageUri   = it
            imageBytes = try {
                context.contentResolver.openInputStream(it)?.readBytes()
            } catch (_: Exception) { null }
        }
    }

    // Notify parent when company name changes (triggers similarity check)
    LaunchedEffect(company) {
        if (company.length >= 3) onCompanyNameChanged(company)
    }

    // Show merge dialog when similar companies found and user hasn't acknowledged
    val hasDuplicate = similarCompanyNames.isNotEmpty() && !proceedDespiteDup

    if (showMergeDialog && similarCompanyNames.isNotEmpty()) {
        CompanyDuplicateDialog(
            enteredName    = company,
            similarNames   = similarCompanyNames,
            onRename       = { showMergeDialog = false },          // user will rename
            onProceed      = { showMergeDialog = false; proceedDespiteDup = true },
            onDismiss      = { showMergeDialog = false }
        )
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Header
        Card(
            modifier  = Modifier.fillMaxWidth(),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PersonAdd, null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary)
                Text("Join IT Connect", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(0.8f))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Profile image picker
        Box(
            modifier         = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { imagePicker.launch(ELIGIBLE_IMAGE_MIME_TYPES) },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUri).crossfade(true).build(),
                    contentDescription = "Profile",
                    modifier     = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddAPhoto, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("Add Photo (JPG/PNG/WebP)", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Personal section
                RegSectionTitle("Personal Information", Icons.Outlined.Person)
                Spacer(Modifier.height(8.dp))
                RegTextField(name, { name = it }, "Full Name", Icons.Default.Person)
                RegTextField(email, { email = it }, "Email", Icons.Default.Email, KeyboardType.Email)
                RegPasswordField(password, { password = it }, isPasswordVisible, { isPasswordVisible = it })
                RegTextField(phone, { phone = it }, "Phone Number", Icons.Default.Phone, KeyboardType.Phone)

                Spacer(Modifier.height(16.dp))

                // Work section
                RegSectionTitle("Work Information", Icons.Outlined.Work)
                Spacer(Modifier.height(8.dp))
                RegTextField(designation, { designation = it }, "Designation", Icons.Default.Badge)

                // Company field with duplicate warning
                OutlinedTextField(
                    value         = company,
                    onValueChange = { company = it; proceedDespiteDup = false },
                    label         = { Text("Company Name") },
                    leadingIcon   = { Icon(Icons.Default.Business, null,
                        tint = if (hasDuplicate) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary) },
                    trailingIcon  = if (hasDuplicate) ({
                        IconButton(onClick = { showMergeDialog = true }) {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }) else null,
                    isError  = hasDuplicate,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                AnimatedVisibility(visible = hasDuplicate) {
                    Text(
                        "Similar company found: ${similarCompanyNames.first()}. Tap ⚠ to review.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }

                RegTextField(department, { department = it }, "Department", Icons.Default.Group)

                Spacer(Modifier.height(12.dp))
                Text("Role", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                RegRoleDropdown(selected = role, onSelected = { role = it })

                // Error
                if (state is RegistrationState.Error) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (hasDuplicate) { showMergeDialog = true; return@Button }
                        onRegisterClick(
                            RegistrationRequest(
                                email       = email.trim(),
                                password    = password,
                                name        = name.trim(),
                                phoneNumber = phone.trim(),
                                designation = designation.trim(),
                                companyName = company.trim(),
                                department  = department,
                                role        = role,
                                imageBytes  = imageBytes
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = !isLoading && name.isNotBlank() && email.isNotBlank()
                            && password.isNotBlank() && company.isNotBlank()
                            && designation.isNotBlank() && department.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Creating Account…")
                    } else {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Account", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Already have an account? Sign In",
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Company duplicate dialog ───────────────────────────────────────────────────

@Composable
private fun CompanyDuplicateDialog(
    enteredName  : String,
    similarNames : List<String>,
    onRename     : () -> Unit,
    onProceed    : () -> Unit,
    onDismiss    : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Possible Duplicate Company", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The company name \"$enteredName\" is similar to:")
                similarNames.forEach { name ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Text(
                    "To merge with an existing company, ask that company's Administrator " +
                            "to add you. Or rename your company to make it unique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRename) { Text("Rename My Company") }
        },
        dismissButton = {
            OutlinedButton(onClick = onProceed) { Text("Create Anyway") }
        }
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun RegSectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.2f))
}

@Composable
private fun RegTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, icon: ImageVector, keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun RegPasswordField(
    value: String, onValueChange: (String) -> Unit,
    isVisible: Boolean, onToggle: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text("Password") },
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            IconButton(onClick = { onToggle(!isVisible) }) {
                Icon(if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegRoleDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true,
            label = { Text("Role") },
            leadingIcon = { Icon(Icons.Default.ManageAccounts, null,
                tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Permissions.ALL_ROLES.forEach { r ->
                DropdownMenuItem(text = { Text(r) }, onClick = { onSelected(r); expanded = false })
            }
        }
    }
}