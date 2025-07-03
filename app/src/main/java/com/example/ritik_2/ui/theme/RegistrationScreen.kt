package com.example.ritik_2.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.times

@Composable
fun RegistrationScreen(
    onRegisterClick: (String, String, String, String, String, String, Int, Int, Int, Int, Uri?) -> Unit,
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

    // UI states
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    // Animation states
    val screenTransition = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated space background
        AnimatedSpaceBackgroundbg()

        // Main content with entrance animation
        AnimatedVisibility(
            visibleState = screenTransition,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(1000))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { keyboardController?.hide() })
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Header with animation
                EnhancedRegistrationHeader()

                Spacer(modifier = Modifier.height(32.dp))

                // Main registration form
                EnhancedRegistrationForm(
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it },
                    name = name,
                    onNameChange = { name = it },
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    designation = designation,
                    onDesignationChange = { designation = it },
                    companyName = companyName,
                    onCompanyNameChange = { companyName = it },
                    experience = experience,
                    onExperienceChange = { experience = it },
                    completedProjects = completedProjects,
                    onCompletedProjectsChange = { completedProjects = it },
                    activeProjects = activeProjects,
                    onActiveProjectsChange = { activeProjects = it },
                    complaints = complaints,
                    onComplaintsChange = { complaints = it },
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = it },
                    isConfirmPasswordVisible = isConfirmPasswordVisible,
                    onConfirmPasswordVisibilityChange = { isConfirmPasswordVisible = it },
                    imageUri = imageUri,
                    onImagePick = { imagePickerLauncher.launch("image/*") },
                    isLoading = isLoading,
                    showErrors = showErrors,
                    isFormValid = isFormValid,
                    onRegisterClick = {
                        if (isFormValid) {
                            isLoading = true
                            onRegisterClick(
                                email, password, name, phoneNumber, designation, companyName,
                                experience.toIntOrNull() ?: 0,
                                completedProjects.toIntOrNull() ?: 0,
                                activeProjects.toIntOrNull() ?: 0,
                                complaints.toIntOrNull() ?: 0,
                                imageUri
                            )
                        } else {
                            showErrors = true
                        }
                    },
                    onLoginClick = onLoginClick
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun EnhancedRegistrationHeader() {
    var headerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        headerVisible = true
    }

    AnimatedVisibility(
        visible = headerVisible,
        enter = slideInVertically(
            initialOffsetY = { -it / 2 },
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(800))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing title
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Cyan, Color.Blue, Color.Magenta)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Create Account",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Join the IT Connect Community",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedRegistrationForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    designation: String,
    onDesignationChange: (String) -> Unit,
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    experience: String,
    onExperienceChange: (String) -> Unit,
    completedProjects: String,
    onCompletedProjectsChange: (String) -> Unit,
    activeProjects: String,
    onActiveProjectsChange: (String) -> Unit,
    complaints: String,
    onComplaintsChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    isConfirmPasswordVisible: Boolean,
    onConfirmPasswordVisibilityChange: (Boolean) -> Unit,
    imageUri: Uri?,
    onImagePick: () -> Unit,
    isLoading: Boolean,
    showErrors: Boolean,
    isFormValid: Boolean,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    var formVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        formVisible = true
    }

    AnimatedVisibility(
        visible = formVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(800))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Cyan.copy(alpha = 0.3f),
                    spotColor = Color.Blue.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Cyan.copy(alpha = 0.6f),
                        Color.Blue.copy(alpha = 0.4f),
                        Color.Magenta.copy(alpha = 0.3f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Image Section
                EnhancedProfileImagePicker(
                    imageUri = imageUri,
                    onImagePick = onImagePick,
                    animationDelay = 100
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Personal Information Section
                EnhancedSectionHeader("Personal Information", 200)

                EnhancedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = "Full Name",
                    icon = Icons.Filled.Person,
                    animationDelay = 300,
                    isError = showErrors && name.isBlank()
                )

                EnhancedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = "Email Address",
                    icon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                    animationDelay = 400,
                    isError = showErrors && email.isBlank()
                )

                EnhancedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = "Phone Number",
                    icon = Icons.Filled.Phone,
                    keyboardType = KeyboardType.Phone,
                    animationDelay = 500,
                    isError = showErrors && phoneNumber.isBlank()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Professional Information Section
                EnhancedSectionHeader("Professional Information", 600)

                EnhancedTextField(
                    value = designation,
                    onValueChange = onDesignationChange,
                    label = "Job Title/Designation",
                    icon = Icons.Filled.Work,
                    animationDelay = 700,
                    isError = showErrors && designation.isBlank()
                )

                EnhancedTextField(
                    value = companyName,
                    onValueChange = onCompanyNameChange,
                    label = "Company/Organization",
                    icon = Icons.Filled.Business,
                    animationDelay = 800,
                    isError = showErrors && companyName.isBlank()
                )

                // Professional Stats Grid (2x2)
                EnhancedStatsGrid(
                    experience = experience,
                    onExperienceChange = onExperienceChange,
                    completedProjects = completedProjects,
                    onCompletedProjectsChange = onCompletedProjectsChange,
                    activeProjects = activeProjects,
                    onActiveProjectsChange = onActiveProjectsChange,
                    complaints = complaints,
                    onComplaintsChange = onComplaintsChange,
                    animationDelay = 900
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Security Section
                EnhancedSectionHeader("Security", 1000)

                EnhancedPasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = onPasswordVisibilityChange,
                    animationDelay = 1100,
                    isError = showErrors && password.length < 6
                )

                EnhancedPasswordField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = "Confirm Password",
                    isPasswordVisible = isConfirmPasswordVisible,
                    onPasswordVisibilityChange = onConfirmPasswordVisibilityChange,
                    animationDelay = 1200,
                    isError = showErrors && password != confirmPassword
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Register Button with loading
                EnhancedRegisterButton(
                    isLoading = isLoading,
                    isFormValid = isFormValid,
                    onClick = onRegisterClick,
                    animationDelay = 1300
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Login link
                EnhancedLoginLink(
                    onClick = onLoginClick,
                    animationDelay = 1400
                )
            }
        }
    }
}

@Composable
fun EnhancedProfileImagePicker(
    imageUri: Uri?,
    onImagePick: () -> Unit,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "profile")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(600)) + fadeIn(tween(600))
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Cyan.copy(alpha = glowAlpha),
                            Color.Blue.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
                .clickable { onImagePick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(116.dp)
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
                        tint = Color.Cyan,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Photo",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedSectionHeader(title: String, animationDelay: Int) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(500)) + fadeIn(tween(500))
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Cyan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
fun EnhancedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    animationDelay: Int,
    isError: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(400)) + fadeIn(tween(400))
    ) {
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isError) Color.Red else if (isFocused) Color.Cyan else Color.White.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = if (isError) Color.Red else Color.Cyan,
                    unfocusedBorderColor = if (isError) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.Cyan
                ),
                shape = RoundedCornerShape(12.dp),
                isError = isError
            )

            if (isError) {
                Text(
                    text = when (label) {
                        "Full Name" -> "Name is required"
                        "Email Address" -> "Valid email is required"
                        "Phone Number" -> "Phone number is required"
                        "Job Title/Designation" -> "Designation is required"
                        "Company/Organization" -> "Company name is required"
                        else -> "This field is required"
                    },
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
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
    isPasswordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    animationDelay: Int,
    isError: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(400)) + fadeIn(tween(400))
    ) {
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (isError) Color.Red else Color.Cyan
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onPasswordVisibilityChange(!isPasswordVisible) }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle Password",
                            tint = Color.Cyan
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = if (isError) Color.Red else Color.Cyan,
                    unfocusedBorderColor = if (isError) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.Cyan
                ),
                shape = RoundedCornerShape(12.dp),
                isError = isError
            )

            if (isError) {
                Text(
                    text = if (label == "Password") "Password must be at least 6 characters" else "Passwords don't match",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EnhancedStatsGrid(
    experience: String,
    onExperienceChange: (String) -> Unit,
    completedProjects: String,
    onCompletedProjectsChange: (String) -> Unit,
    activeProjects: String,
    onActiveProjectsChange: (String) -> Unit,
    complaints: String,
    onComplaintsChange: (String) -> Unit,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(600)) + fadeIn(tween(600))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancedNumberField(
                    value = experience,
                    onValueChange = onExperienceChange,
                    label = "Experience (Years)",
                    icon = Icons.Outlined.Work,
                    modifier = Modifier.weight(1f)
                )
                EnhancedNumberField(
                    value = completedProjects,
                    onValueChange = onCompletedProjectsChange,
                    label = "Completed Projects",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }

            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancedNumberField(
                    value = activeProjects,
                    onValueChange = onActiveProjectsChange,
                    label = "Active Projects",
                    icon = Icons.Outlined.Pending,
                    modifier = Modifier.weight(1f)
                )
                EnhancedNumberField(
                    value = complaints,
                    onValueChange = onComplaintsChange,
                    label = "Complaints",
                    icon = Icons.Outlined.Error,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun EnhancedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Cyan,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = modifier.padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            cursorColor = Color.Cyan
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true
    )
}

@Composable
fun EnhancedRegisterButton(
    isLoading: Boolean,
    isFormValid: Boolean,
    onClick: () -> Unit,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "button")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(500)) + fadeIn(tween(500))
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Cyan.copy(alpha = 0.3f)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(28.dp),
            enabled = !isLoading && isFormValid
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (isFormValid) {
                            Brush.linearGradient(
                                colors = listOf(Color.Cyan, Color.Blue, Color.Magenta),
                                start = Offset(shimmer * 300, 0f),
                                end = Offset(shimmer * 300 + 200f, 0f)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(Color.Gray, Color.DarkGray)
                            )
                        },
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "Create Account",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedLoginLink(
    onClick: () -> Unit,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400))
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = "Already have an account? Login",
                color = Color.Cyan,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun AnimatedSpaceBackgroundbg() {
    val infiniteTransition = rememberInfiniteTransition(label = "space")

    val stars1Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "stars1"
    )

    val stars2Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "stars2"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val nebulaOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(80000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "nebula"
    )

    // Move this OUTSIDE the Canvas
    val starPositions = remember {
        (1..200).map {
            Offset(
                x = Random.nextFloat() * 1080f, // Use a default width
                y = Random.nextFloat() * 1920f  // Use a default height
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(0.5.dp)
    ) {
        // Dark space background with gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0D1B2A),
                    Color(0xFF1B263B),
                    Color(0xFF0D1B2A)
                )
            )
        )

//        // Generate static star positions
//        val starPositions = remember {
//            (1..200).map {
//                Offset(
//                    x = Random.nextFloat() * size.width,
//                    y = Random.nextFloat() * size.height
//                )
//            }
//        }

        // Draw twinkling stars
        starPositions.forEachIndexed { index, position ->
            val twinklePhase = (System.currentTimeMillis() / 100 + index * 50) % 3000
            val alpha = (sin(twinklePhase / 1000f * 2 * kotlin.math.PI) + 1) / 2
            val starSize = Random.nextFloat() * 3f + 1f

            drawCircle(
                color = when (index % 4) {
                    0 -> Color.White.copy(alpha = alpha.toFloat())
                    1 -> Color.Cyan.copy(alpha = alpha.toFloat() * 0.8f)
                    2 -> Color.Blue.copy(alpha = alpha.toFloat() * 0.6f)
                    else -> Color.Magenta.copy(alpha = alpha.toFloat() * 0.4f)
                },
                radius = starSize,
                center = position
            )
        }

        // Animated nebula clouds
        val nebulaColors = listOf(
            Color.Cyan.copy(alpha = 0.1f),
            Color.Blue.copy(alpha = 0.08f),
            Color.Magenta.copy(alpha = 0.06f)
        )

        nebulaColors.forEachIndexed { index, color ->
            val offsetX = (nebulaOffset + index * 200) % (size.width + 400) - 200
            val offsetY = sin((nebulaOffset + index * 300) / 1000f) * 100 + size.height / 2

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    radius = 300f + index * 100
                ),
                radius = 300f + index * 100,
                center = Offset(offsetX, offsetY)
            )
        }

        // Rotating constellation patterns
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = 150f

        // First constellation
        (0..7).forEach { i ->
            val angle = (stars1Rotation + i * 45) * kotlin.math.PI / 180
            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius

            drawCircle(
                color = Color.Cyan.copy(alpha = pulseAlpha * 0.8f),
                radius = 4f,
                center = Offset(x.toFloat(), y.toFloat())
            )
        }

        // Second constellation
        (0..5).forEach { i ->
            val angle = (stars2Rotation + i * 60) * kotlin.math.PI / 180
            val x = centerX + cos(angle) * (radius * 0.7f)
            val y = centerY + sin(angle) * (radius * 0.7f)

            drawCircle(
                color = Color.Magenta.copy(alpha = pulseAlpha * 0.6f),
                radius = 3f,
                center = Offset(x.toFloat(), y.toFloat())
            )
        }

        // Shooting stars
        val shootingStarProgress = (System.currentTimeMillis() / 50) % 2000 / 2000f
        if (shootingStarProgress < 0.8f) {
            val startX = -100f
            val startY = size.height * 0.3f
            val endX = size.width + 100f
            val endY = size.height * 0.7f

            val currentX = startX + (endX - startX) * shootingStarProgress
            val currentY = startY + (endY - startY) * shootingStarProgress

            // Draw shooting star trail
            (0..10).forEach { i ->
                val trailX = currentX - i * 15
                val trailY = currentY - i * 8
                val trailAlpha = (1f - i / 10f) * (1f - shootingStarProgress)

                drawCircle(
                    color = Color.White.copy(alpha = trailAlpha),
                    radius = 2f - i * 0.1f,
                    center = Offset(trailX, trailY)
                )
            }
        }
    }
}