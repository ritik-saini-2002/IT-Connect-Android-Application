package com.example.ritik_2.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onCreateUserClick: (String, String, String, String, String, String) -> Unit,
    isCreating: Boolean
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("employee") }
    var companyName by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var userCreated by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val roleOptions = listOf("Employee", "Team Leader", "Manager", "Administrator")

    // Success animation scale
    val successScale by animateFloatAsState(
        targetValue = if (showSuccessAnimation) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    // Form validation
    val isFormValid = email.isNotBlank() &&
            password.length >= 6 &&
            companyName.isNotBlank()

    // Monitor creation completion
    LaunchedEffect(isCreating) {
        if (!isCreating && userCreated) {
            showSuccessAnimation = true
            delay(2000)
            showSuccessAnimation = false
            // Reset form after animation
            delay(500)
            name = ""
            email = ""
            password = ""
            role = "Employee"
            companyName = ""
            designation = ""
            userCreated = false
            showErrors = false
        }
    }

    // Centralized container with border
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section with animation
                AnimatedVisibility(
                    visible = !showSuccessAnimation,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Administrator Panel",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Create New User Account",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Success Animation
                AnimatedVisibility(
                    visible = showSuccessAnimation,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(successScale),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "User Created Successfully!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "The new user account has been created and can complete their profile",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Form Section with animation
                AnimatedVisibility(
                    visible = !showSuccessAnimation,
                    enter = slideInVertically(
                        animationSpec = tween(300, delayMillis = 100)
                    ) + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section Title
                            Text(
                                text = "Basic Account Information",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Name Field
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (showErrors && email.isBlank()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && email.isBlank()
                            )

                            if (showErrors && name.isBlank()) {
                                Text(
                                    text = "Name is required",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }

                            // Email Field
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (showErrors && email.isBlank()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && email.isBlank()
                            )

                            if (showErrors && email.isBlank()) {
                                Text(
                                    text = "Email address is required",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }


                            // Company Name Field
                            OutlinedTextField(
                                value = companyName,
                                onValueChange = { companyName = it },
                                label = { Text("Company/Organization Name") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = if (showErrors && companyName.isBlank()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && companyName.isBlank()
                            )

                            if (showErrors && companyName.isBlank()) {
                                Text(
                                    text = "Company name is required",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }

                            // Designation Field
                            OutlinedTextField(
                                value = designation,
                                onValueChange = { designation = it },
                                label = { Text("Designation") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (showErrors && email.isBlank()) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && email.isBlank()
                            )

                            if (showErrors && designation.isBlank()) {
                                Text(
                                    text = "Designation is required",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }

                            // Role Dropdown
                            DropdownMenuBox(
                                roleOptions = roleOptions,
                                selected = role,
                                onRoleSelected = { role = it }
                            )

                                        // Password Field
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = { Text("Password") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = if (showErrors && password.length < 6) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            isError = showErrors && password.length < 6
                                        )

                                        if (showErrors && password.length < 6) {
                                            Text(
                                                text = "Password must be at least 6 characters",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                            // Info Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Text(
                                            text = "Profile Completion",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "The user will be able to complete their profile with additional information like name, phone number, designation, and professional details after their first login.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }

                            }
                        }
                    }
                }

                // Action Button with animation
                AnimatedVisibility(
                    visible = !showSuccessAnimation,
                    enter = slideInVertically(
                        animationSpec = tween(300, delayMillis = 200)
                    ) + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Button(
                        onClick = {
                            if (isFormValid) {
                                userCreated = true
                                onCreateUserClick(name, email, role, companyName, designation, password)
                            } else {
                                showErrors = true
                            }
                        },
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isCreating) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Creating Account...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = "Create User Account",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Footer Text with animation
                AnimatedVisibility(
                    visible = !showSuccessAnimation,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 300)),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Name, email, company name, designation and password are required. User can complete remaining profile details after first login.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(
    roleOptions: List<String>,
    selected: String,
    onRoleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Rotation animation for dropdown arrow
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "arrowRotation"
    )

    Box {
        OutlinedTextField(
            value = selected.replaceFirstChar { it.uppercase() }.replace("_", " "),
            onValueChange = {},
            label = { Text("User Role") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .rotate(arrowRotation)
                        .clickable { expanded = !expanded },
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp)
        )

        // Animated dropdown menu
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    roleOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = when (option) {
                                            "Employee" -> Icons.Default.Person
                                            "Team Leader" -> Icons.Default.SupervisorAccount
                                            "Manager" -> Icons.Default.ManageAccounts
                                            "Administrator" -> Icons.Default.AdminPanelSettings
                                            else -> Icons.Default.Person
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (option == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = option.replaceFirstChar { it.uppercase() }.replace("_", " "),
                                        fontSize = 16.sp,
                                        color = if (option == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (option == selected) FontWeight.Medium
                                        else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                onRoleSelected(option)
                                expanded = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        )
                        if (index < roleOptions.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // Invisible clickable overlay to close dropdown
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { expanded = false }
            )
        }
    }
}