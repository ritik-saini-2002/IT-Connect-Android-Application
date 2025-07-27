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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCompletionScreen(
    userId: String,
    onProfileUpdateClick: (Map<String, Any>, String?, Uri?) -> Unit,
    onLogoutClick: () -> Unit
) {
    // Form states
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("0") }
    var completedProjects by remember { mutableStateOf("0") }
    var activeProjects by remember { mutableStateOf("0") }
    var complaints by remember { mutableStateOf("0") }
    var currentImageUrl by remember { mutableStateOf("") }

    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var isDataLoaded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val firestore = FirebaseFirestore.getInstance()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Load existing user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val data = document.data
                        email = data?.get("email")?.toString() ?: ""
                        name = data?.get("name")?.toString() ?: ""
                        role = data?.get("role")?.toString() ?: ""
                        designation = data?.get("designation")?.toString() ?: ""
                        phoneNumber = data?.get("phoneNumber")?.toString() ?: ""
                        companyName = data?.get("companyName")?.toString() ?: ""
                        experience = data?.get("experience")?.toString() ?: "0"
                        completedProjects = data?.get("completedProjects")?.toString() ?: "0"
                        activeProjects = data?.get("activeProjects")?.toString() ?: "0"
                        complaints = data?.get("complaints")?.toString() ?: "0"
                        currentImageUrl = data?.get("imageUrl")?.toString() ?: ""
                        isDataLoaded = true
                    }
                }
        }
    }

    // Validation
    val isFormValid = phoneNumber.isNotBlank() &&
            companyName.isNotBlank() &&
            (newPassword.isEmpty() || (newPassword.length >= 6 && newPassword == confirmNewPassword))

    if (!isDataLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

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
                    text = "Complete Your Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please complete your profile to continue",
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
            Column(modifier = Modifier.padding(24.dp)) {
                // Profile Image
                ProfileImageSection(
                    imageUri = imageUri,
                    currentImageUrl = currentImageUrl,
                    onImagePick = { imagePickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Personal Information (Read-only)")

                ReadOnlyTextField(name, "Full Name", Icons.Filled.Person)
                ReadOnlyTextField(email, "Email Address", Icons.Filled.Email)
                ReadOnlyTextField(role.replaceFirstChar { it.uppercase() }, "Role", Icons.Filled.AdminPanelSettings)
                ReadOnlyTextField(designation, "Designation", Icons.Filled.Work)

                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle("Contact & Company Information")

                TraditionalTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = "Phone Number",
                    icon = Icons.Filled.Phone,
                    keyboardType = KeyboardType.Phone,
                    isError = showErrors && phoneNumber.isBlank(),
                    errorMessage = "Phone number is required"
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
                SectionTitle("Professional Statistics")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(experience, { experience = it }, "Experience (Years)", Modifier.weight(1f))
                    NumberField(completedProjects, { completedProjects = it }, "Completed Projects", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle("Change Password (Optional)")
                TraditionalPasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password (Optional)",
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isError = showErrors && newPassword.isNotEmpty() && newPassword.length < 6,
                    errorMessage = "Password must be at least 6 characters"
                )
                if (newPassword.isNotEmpty()) {
                    TraditionalPasswordField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = "Confirm New Password",
                        isPasswordVisible = isConfirmPasswordVisible,
                        onPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                        isError = showErrors && newPassword != confirmNewPassword,
                        errorMessage = "Passwords don't match"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        if (isFormValid) {
                            isLoading = true
                            val updatedData = mapOf(
                                "phoneNumber" to phoneNumber,
                                "companyName" to companyName,
                                "experience" to (experience.toIntOrNull() ?: 0),
                                "completedProjects" to (completedProjects.toIntOrNull() ?: 0),
                                "activeProjects" to (activeProjects.toIntOrNull() ?: 0),
                                "complaints" to (complaints.toIntOrNull() ?: 0)
                            )
                            val passwordToUpdate = if (newPassword.isNotEmpty()) newPassword else null
                            onProfileUpdateClick(updatedData, passwordToUpdate, imageUri)
                        } else {
                            showErrors = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("Update Profile & Continue", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ReadOnlyTextField(value: String, label: String, icon: ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        enabled = false
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            isError = isError
        )
        if (isError) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { onPasswordVisibilityChange(!isPasswordVisible) }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            isError = isError
        )
        if (isError) Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
fun ProfileImageSection(imageUri: Uri?, currentImageUrl: String, onImagePick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onImagePick() },
            contentAlignment = Alignment.Center
        ) {
            when {
                imageUri != null -> {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                currentImageUrl.isNotEmpty() -> {
                    AsyncImage(
                        model = currentImageUrl,
                        contentDescription = "Current Profile Picture",
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add Photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        if (imageUri != null || currentImageUrl.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap to change photo", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}
