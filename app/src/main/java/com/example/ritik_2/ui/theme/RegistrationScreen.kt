package com.example.ritik_2.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onRegisterClick: (String, String, String, String, String, String, Int, Int, Int, Int, Uri?, String?) -> Unit,
    onLoginClick: () -> Unit
) {
    // Form states
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("0") }
    var completedProjects by remember { mutableStateOf("0") }
    var activeProjects by remember { mutableStateOf("0") }
    var complaints by remember { mutableStateOf("0") }
    var selectedRole by remember { mutableStateOf("administrator") }

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var expandedRole by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val roleOptions = listOf("administrator", "manager", "team_leader", "employee")

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Validation
    val isFormValid = email.isNotBlank() &&
            password.length >= 6 &&
            password == confirmPassword &&
            name.isNotBlank() &&
            phoneNumber.isNotBlank() &&
            designation.isNotBlank() &&
            companyName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(top = 50.dp)
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Administrator Registration",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create your Administrator Account",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Profile Image Section
                ProfileImageSection(
                    imageUri = imageUri,
                    onImagePick = { imagePickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Personal Information
                SectionTitle("Personal Information")

                TraditionalTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name",
                    icon = Icons.Filled.Person,
                    isError = showErrors && name.isBlank(),
                    errorMessage = "Name is required"
                )

                TraditionalTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email Address",
                    icon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                    isError = showErrors && email.isBlank(),
                    errorMessage = "Valid email is required"
                )

                TraditionalTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = "Phone Number",
                    icon = Icons.Filled.Phone,
                    keyboardType = KeyboardType.Phone,
                    isError = showErrors && phoneNumber.isBlank(),
                    errorMessage = "Phone number is required"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Role Selection
                SectionTitle("Role & Professional Information")

                // Role Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedRole,
                    onExpandedChange = { expandedRole = !expandedRole }
                ) {
                    OutlinedTextField(
                        value = selectedRole.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Role") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRole)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedRole,
                        onDismissRequest = { expandedRole = false }
                    ) {
                        roleOptions.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedRole = role
                                    expandedRole = false
                                }
                            )
                        }
                    }
                }

                TraditionalTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = "Job Title/Designation",
                    icon = Icons.Filled.Work,
                    isError = showErrors && designation.isBlank(),
                    errorMessage = "Designation is required"
                )

                TraditionalTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Company/Organization",
                    icon = Icons.Filled.Business,
                    isError = showErrors && companyName.isBlank(),
                    errorMessage = "Company name is required"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Professional Stats
                SectionTitle("Professional Statistics")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(
                        value = experience,
                        onValueChange = { experience = it },
                        label = "Experience (Years)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = completedProjects,
                        onValueChange = { completedProjects = it },
                        label = "Completed Projects",
                        modifier = Modifier.weight(1f)
                    )
                }

//                Spacer(modifier = Modifier.height(8.dp))
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    NumberField(
//                        value = activeProjects,
//                        onValueChange = { activeProjects = it },
//                        label = "Active Projects",
//                        modifier = Modifier.weight(1f)
//                    )
//                    NumberField(
//                        value = complaints,
//                        onValueChange = { complaints = it },
//                        label = "Complaints",
//                        modifier = Modifier.weight(1f)
//                    )
//                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security Section
                SectionTitle("Security")

                TraditionalPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isError = showErrors && password.length < 6,
                    errorMessage = "Password must be at least 6 characters"
                )

                TraditionalPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Password",
                    isPasswordVisible = isConfirmPasswordVisible,
                    onPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                    isError = showErrors && password != confirmPassword,
                    errorMessage = "Passwords don't match"
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Register Button
                Button(
                    onClick = {
                        if (isFormValid) {
                            isLoading = true
                            onRegisterClick(
                                email, password, name, phoneNumber, designation, companyName,
                                experience.toIntOrNull() ?: 0,
                                completedProjects.toIntOrNull() ?: 0,
                                activeProjects.toIntOrNull() ?: 0,
                                complaints.toIntOrNull() ?: 0,
                                imageUri,
                                selectedRole
                            )
                        } else {
                            showErrors = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "Create Administrator Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Login Link
                TextButton(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Already have an account? Login",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ProfileImageSection(
    imageUri: Uri?,
    onImagePick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onImagePick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Add Photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Photo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun TraditionalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors()
        )

        if (isError) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun TraditionalPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPasswordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                IconButton(onClick = { onPasswordVisibilityChange(!isPasswordVisible) }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Password Visibility",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors()
        )

        if (isError) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier.padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(),
        singleLine = true
    )
}