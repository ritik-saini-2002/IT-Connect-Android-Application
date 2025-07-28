package com.example.ritik_2.registration

import android.net.Uri
import android.util.Patterns
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
    onRegisterClick: (String, String, String, String, String, String, Int, Int, Int, Int, Uri?, String?, String?) -> Unit,
    onLoginClick: () -> Unit
) {
    // Form states - keeping defaults for fresh registration
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("Administrative") }
    var designation by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("0") }
    var completedProjects by remember { mutableStateOf("0") }
    var activeProjects by remember { mutableStateOf("0") }
    var complaints by remember { mutableStateOf("0") }

    // Role is fixed to Administrator for fresh registrations
    val selectedRole = "Administrator"

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Enhanced validation for fresh account creation
    val isFormValid = email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            password.length >= 6 &&
            password == confirmPassword &&
            name.isNotBlank() &&
            name.length >= 2 &&
            phoneNumber.isNotBlank() &&
            phoneNumber.length >= 10 &&
            designation.isNotBlank() &&
            companyName.isNotBlank() &&
            companyName.length >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Enhanced Header with fresh account messaging
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = "Admin Registration",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Create Admin Account",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Register as the first Administrator for your organization",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⭐ Full system access with administrative privileges",
                        fontSize = 13.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Registration Form
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

                // Personal Information Section
                SectionHeader(
                    title = "Personal Information",
                    icon = Icons.Filled.Person,
                    description = "Basic details for your administrator account"
                )

                EnhancedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name *",
                    icon = Icons.Filled.Person,
                    placeholder = "Enter your full name",
                    isError = showErrors && (name.isBlank() || name.length < 2),
                    errorMessage = if (name.isBlank()) "Name is required" else "Name must be at least 2 characters",
                    keyboardType = KeyboardType.Text
                )

                EnhancedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email Address *",
                    icon = Icons.Filled.Email,
                    placeholder = "admin@company.com",
                    isError = showErrors && (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()),
                    errorMessage = "Please enter a valid email address",
                    keyboardType = KeyboardType.Email
                )

                EnhancedTextField(
                    value = phoneNumber,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '+' || char == '-' || char == ' ' }) phoneNumber = it },
                    label = "Phone Number *",
                    icon = Icons.Filled.Phone,
                    placeholder = "+1234567890",
                    isError = showErrors && (phoneNumber.isBlank() || phoneNumber.length < 10),
                    errorMessage = "Please enter a valid phone number (min 10 digits)",
                    keyboardType = KeyboardType.Phone
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Professional Information Section
                SectionHeader(
                    title = "Professional Information",
                    icon = Icons.Filled.Work,
                    description = "Your role and company details"
                )

                // Role Display (Fixed as Administrator)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Administrator Role",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Role: Administrator",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Full system access and management privileges",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                EnhancedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = "Job Title/Designation *",
                    icon = Icons.Filled.Badge,
                    placeholder = "CEO, CTO, IT Manager, etc.",
                    isError = showErrors && designation.isBlank(),
                    errorMessage = "Designation is required",
                    keyboardType = KeyboardType.Text
                )

                EnhancedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Company/Organization *",
                    icon = Icons.Filled.Business,
                    placeholder = "Your company name",
                    isError = showErrors && (companyName.isBlank() || companyName.length < 2),
                    errorMessage = if (companyName.isBlank()) "Company name is required" else "Company name must be at least 2 characters",
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Professional Stats Section (Optional)
                SectionHeader(
                    title = "Professional Experience",
                    icon = Icons.Filled.TrendingUp,
                    description = "Optional: Your professional background"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NumberField(
                        value = experience,
                        onValueChange = { experience = it },
                        label = "Experience (Years)",
                        icon = Icons.Filled.WorkHistory,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = completedProjects,
                        onValueChange = { completedProjects = it },
                        label = "Completed Projects",
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Security Section
                SectionHeader(
                    title = "Security Settings",
                    icon = Icons.Filled.Security,
                    description = "Create a secure password for your account"
                )

                EnhancedPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password *",
                    placeholder = "Minimum 6 characters",
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isError = showErrors && password.length < 6,
                    errorMessage = "Password must be at least 6 characters"
                )

                EnhancedPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Password *",
                    placeholder = "Re-enter your password",
                    isPasswordVisible = isConfirmPasswordVisible,
                    onPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                    isError = showErrors && (password != confirmPassword || confirmPassword.isEmpty()),
                    errorMessage = if (confirmPassword.isEmpty()) "Please confirm your password" else "Passwords don't match"
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Enhanced Registration Button
                // In RegistrationScreen composable, update the onRegisterClick call:

                onRegisterClick(
                    email.trim(),
                    password,
                    name.trim(),
                    phoneNumber.trim(),
                    designation.trim(),
                    companyName.trim(),
                    experience.toIntOrNull() ?: 0,
                    completedProjects.toIntOrNull() ?: 0,
                    activeProjects.toIntOrNull() ?: 0,
                    complaints.toIntOrNull() ?: 0,
                    imageUri,
                    selectedRole,  // This should be "Administrator" (the role)
                    department     // This should be "Administrative" (the department)
                )

// Also update the state variables in RegistrationScreen to be clearer:
                var department by remember { mutableStateOf("Administrative") }  // Department name
                val selectedRole = "Administrator"  // User role

// And update the role display card to show correct information:
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Administrator Role",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Role: Administrator",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Department: Administrative",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Full system access and management privileges",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form validation hints
                if (showErrors && !isFormValid) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please fill in all required fields correctly",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Login Link
                TextButton(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Already have an account? Sign In",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
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
                .size(120.dp)
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
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Edit overlay
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Photo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Add Photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add Photo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "(Optional)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EnhancedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error
            ),
            singleLine = true
        )

        if (isError && errorMessage.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun EnhancedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
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
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
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
                .padding(vertical = 6.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error
            ),
            singleLine = true
        )

        if (isError && errorMessage.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = modifier.padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        singleLine = true
    )
}