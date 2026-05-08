package com.example.ritik_2.login

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ritik_2.auth.AuthState
import com.example.ritik_2.theme.Ritik_2Theme
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onLoginClick         : (String, String) -> Unit = { _, _ -> },
    onRegisterClick      : () -> Unit               = {},
    onForgotPasswordClick: (String) -> Unit         = {},
    onInfoClick          : () -> Unit               = {},
    onPcControlClick     : () -> Unit               = {},
    onContactClick       : () -> Unit               = {},
    // ✅ NEW — optional ViewModel state (null-safe so Preview still works)
    loginState           : StateFlow<AuthState>?    = null,
    resetState           : StateFlow<AuthState>?    = null,
    onVerifyOtpAndResetPassword: (String, String, String) -> Unit = { _, _, _ -> }


    ) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // ✅ Collect state — isLoading driven by real ViewModel state now
    val loginAuthState = loginState?.collectAsState()?.value
    val resetAuthState = resetState?.collectAsState()?.value
    val isLoading      = loginAuthState is AuthState.Loading

    val focusManager       = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState        = rememberScrollState()

    val containerScale by animateFloatAsState(
        targetValue   = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "containerScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { focusManager.clearFocus() }
    ) {
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 20.dp)
                .size(48.dp)
                .zIndex(1000f)
                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
        ) {
            Icon(Icons.Default.Info, contentDescription = "Contact Information",
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .scale(containerScale),
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier  = Modifier.padding(24.dp, 0.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedUserLogo()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Welcome Back!", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sign in to access your account and continue your work",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Secure access to your workspace", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier  = Modifier.fillMaxWidth().animateContentSize(),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EnhancedLoginTextField(
                        value = email, onValueChange = { email = it },
                        label = "Email Address", icon = Icons.Default.Email,
                        placeholder = "Enter your email",
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EnhancedLoginPasswordField(
                        value = password, onValueChange = { password = it },
                        label = "Password", placeholder = "Enter your password",
                        isPasswordVisible = isPasswordVisible,
                        onPasswordVisibilityChange = { isPasswordVisible = it },
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide(); focusManager.clearFocus()
                        })
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onLoginClick(email, password)
                            }
                        },
                        modifier  = Modifier.fillMaxWidth().height(56.dp),
                        enabled   = !isLoading && email.isNotBlank() && password.isNotBlank(),
                        colors    = ButtonDefaults.buttonColors(
                            containerColor         = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        AnimatedContent(
                            targetState  = isLoading,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(300))  // ✅ fixed deprecated 'with'
                            },
                            label = "buttonContent"
                        ) { loading ->
                            if (loading) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Signing In...", fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Login, null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign In", fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Forgot Password?", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRegisterClick,
                            shape   = RoundedCornerShape(12.dp),
                            border  = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Account", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }

                    // ✅ Show error message inline under buttons
                    if (loginAuthState is AuthState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text      = loginAuthState.message,
                            color     = MaterialTheme.colorScheme.error,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Bottom-left: Contact shortcut ──
        FloatingActionButton(
            onClick          = onContactClick,
            modifier         = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .navigationBarsPadding(),
            containerColor   = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor     = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(Icons.Default.ContactSupport, contentDescription = "Contact")
        }

        // ── Bottom-right: Windows Control shortcut ──
        FloatingActionButton(
            onClick          = onPcControlClick,
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
            containerColor   = MaterialTheme.colorScheme.secondaryContainer,
            contentColor     = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Icons.Default.Computer, contentDescription = "Windows Control")
        }
    }

    // Inside LoginScreen composable — update the dialog call

    if (showForgotPasswordDialog) {
        ModernForgotPasswordDialog(
            onDismiss                   = { showForgotPasswordDialog = false },
            onSendOtp                   = { email -> onForgotPasswordClick(email) },
            onVerifyOtpAndResetPassword = { email, otp, newPass ->
                onVerifyOtpAndResetPassword(email, otp, newPass)
            },
            resetState = resetAuthState ?: AuthState.Idle
        )
    }
    // ✅ Removed: broken LaunchedEffect(isLoading) with hardcoded 1500ms delay
}

// ── Keep all your existing composables below unchanged ────────

@Composable
fun AnimatedUserLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "userLogo")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "logoRotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoScale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Box(modifier = Modifier.size(120.dp).rotate(rotation).border(3.dp,
            Brush.sweepGradient(listOf(
                MaterialTheme.colorScheme.onPrimary,
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )), CircleShape))
        Box(modifier = Modifier.size(80.dp).scale(scale).background(
            Brush.radialGradient(listOf(
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                Color.Transparent
            )), CircleShape))
        Icon(Icons.Default.Person, "User", modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimary)
        repeat(8) { index ->
            val angle    = (index * 45f) + (rotation * 0.2f)
            val dotScale = (scale - 1f) * 0.5f + 1f
            Box(modifier = Modifier.size(8.dp)
                .offset(
                    x = (50f * cos(Math.toRadians(angle.toDouble())).toFloat()).dp,
                    y = (50f * sin(Math.toRadians(angle.toDouble())).toFloat()).dp
                )
                .scale(dotScale)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedLoginTextField(
    value          : String,
    onValueChange  : (String) -> Unit,
    label          : String,
    icon           : ImageVector,
    placeholder    : String         = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedLoginPasswordField(
    value                    : String,
    onValueChange            : (String) -> Unit,
    label                    : String,
    placeholder              : String         = "",
    isPasswordVisible        : Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    keyboardActions          : KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon  = { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            IconButton(onClick = { onPasswordVisibilityChange(!isPasswordVisible) }) {
                Icon(if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    "Toggle Password Visibility", tint = MaterialTheme.colorScheme.primary)
            }
        },
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true
    )
}

// LoginScreen.kt — replace ModernForgotPasswordDialog entirely

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernForgotPasswordDialog(
    onDismiss                   : () -> Unit,
    onSendOtp                   : (String) -> Unit,
    onVerifyOtpAndResetPassword : (String, String, String) -> Unit,
    resetState                  : AuthState = AuthState.Idle
) {
    // Step 1 = email entry, Step 2 = OTP entry, Step 3 = new password
    var step            by remember { mutableStateOf(1) }
    var email           by remember { mutableStateOf("") }
    var otp             by remember { mutableStateOf("") }
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading = resetState is AuthState.Loading

    // Auto-advance when OTP is sent successfully
    LaunchedEffect(resetState) {
        if (resetState is AuthState.OtpSent  && step == 1) step = 2
        if (resetState is AuthState.Success  && step == 3) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (step) {
                        2    -> Icons.Default.Pin
                        3    -> Icons.Default.Lock
                        else -> Icons.Default.Key
                    },
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (step) {
                        2    -> "Enter OTP"
                        3    -> "New Password"
                        else -> "Reset Password"
                    },
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column {
                // ── Step progress indicator ──────────────────────────────
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (step == i + 1) 12.dp else 8.dp)
                                .background(
                                    color = if (step >= i + 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                        if (i < 2) Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                when (step) {
                    // ── Step 1: Email ────────────────────────────────────
                    1 -> {
                        Text(
                            "Enter your registered email. We'll send a 6-digit OTP.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value           = email,
                            onValueChange   = { email = it },
                            label           = { Text("Email Address") },
                            placeholder     = { Text("Enter your email") },
                            leadingIcon     = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier        = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            singleLine = true,
                            colors     = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // ── Step 2: OTP ──────────────────────────────────────
                    2 -> {
                        Text(
                            "A 6-digit OTP was sent to $email. Enter it below.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value           = otp,
                            onValueChange   = { if (it.length <= 6) otp = it },
                            label           = { Text("OTP Code") },
                            placeholder     = { Text("Enter 6-digit OTP") },
                            leadingIcon     = { Icon(Icons.Default.Pin, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier        = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true,
                            colors     = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { onSendOtp(email) },
                            enabled = !isLoading
                        ) { Text("Resend OTP", color = MaterialTheme.colorScheme.primary) }
                    }

                    // ── Step 3: New Password ─────────────────────────────
                    3 -> {
                        Text(
                            "OTP verified! Set your new password.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value                = newPassword,
                            onValueChange        = { newPassword = it },
                            label                = { Text("New Password") },
                            placeholder          = { Text("Min 8 characters") },
                            leadingIcon          = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon         = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        null, tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier        = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            singleLine      = true,
                            colors          = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value                = confirmPassword,
                            onValueChange        = { confirmPassword = it },
                            label                = { Text("Confirm Password") },
                            placeholder          = { Text("Re-enter password") },
                            leadingIcon          = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            isError              = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                            supportingText       = {
                                if (confirmPassword.isNotEmpty() && confirmPassword != newPassword)
                                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier        = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            singleLine      = true,
                            colors          = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // ── Error message ────────────────────────────────────────
                if (resetState is AuthState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text  = resetState.message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (step) {
                        1 -> if (email.isNotBlank()) onSendOtp(email)
                        2 -> if (otp.length == 6)   step = 3   // just advance; verify on submit
                        3 -> if (newPassword.length >= 8 && newPassword == confirmPassword)
                            onVerifyOtpAndResetPassword(email, otp, newPassword)
                    }
                },
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape   = RoundedCornerShape(12.dp),
                enabled = !isLoading && when (step) {
                    1    -> email.isNotBlank()
                    2    -> otp.length == 6
                    else -> newPassword.length >= 8 && newPassword == confirmPassword
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color    = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text  = when (step) { 1 -> "Send OTP"; 2 -> "Next"; else -> "Reset Password" },
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Cancel") }
        }
    )
}

//@Composable
//fun ModernForgotPasswordDialog(
//    onDismiss      : () -> Unit,
//    onSendResetLink: (String) -> Unit,
//    isSending      : Boolean = false   // ✅ NEW param — driven by ViewModel
//) {
//    var email by remember { mutableStateOf("") }
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        containerColor   = MaterialTheme.colorScheme.surface,
//        shape            = RoundedCornerShape(20.dp),
//        title = {
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
//                Spacer(modifier = Modifier.width(8.dp))
//                Text("Reset Password", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
//            }
//        },
//        text = {
//            Column {
//                Text("Enter your email address and we'll send you a link to reset your password.",
//                    color = MaterialTheme.colorScheme.onSurfaceVariant)
//                Spacer(modifier = Modifier.height(16.dp))
//                OutlinedTextField(
//                    value = email, onValueChange = { email = it },
//                    label = { Text("Email Address") },
//                    placeholder = { Text("Enter your email") },
//                    leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
//                    modifier = Modifier.fillMaxWidth(),
//                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
//                    singleLine = true,
//                    colors = OutlinedTextFieldDefaults.colors(
//                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
//                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
//                    )
//                )
//            }
//        },
//        confirmButton = {
//            Button(
//                onClick  = { if (email.isNotBlank()) onSendResetLink(email) },
//                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
//                shape    = RoundedCornerShape(12.dp),
//                enabled  = !isSending && email.isNotBlank()
//            ) {
//                if (isSending) {
//                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary,
//                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
//                } else {
//                    Text("Send Link", color = MaterialTheme.colorScheme.onPrimary)
//                }
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss,
//                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
//            ) { Text("Cancel") }
//        }
//    )
//}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    Ritik_2Theme { LoginScreen() }
}