package com.example.ritik_2.administrator.administratorpanel.newusercreation

import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserScreen(
    onCreateUserClick: (String, String, String, String, String, String) -> Unit,
    isCreating: Boolean,
    companyName: String
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Employee") }
    var department by remember { mutableStateOf("Technical") }
    var designation by remember { mutableStateOf("") }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var userCreated by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var createdUserName by remember { mutableStateOf("") }

    val roleOptions = listOf("Employee", "Team Leader", "Manager", "Administrator", "HR", "Intern")

    val departmentOptions = listOf(
        "Technical" to Icons.Filled.Computer,
        "HR" to Icons.Filled.People,
        "Administrative" to Icons.Filled.Apartment,
        "IT Support" to Icons.Filled.Settings,
        "Finance" to Icons.Filled.AttachMoney,
        "General" to Icons.Filled.MoreHoriz
    )

    val isFormValid = name.isNotBlank() &&
            name.length >= 2 &&
            email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            department.isNotBlank() &&
            designation.isNotBlank() &&
            password.length >= 4

    LaunchedEffect(isCreating) {
        if (!isCreating && userCreated) {
            showSuccessAnimation = true
            delay(3000)
            showSuccessAnimation = false
            delay(400)

            name = ""
            email = ""
            password = ""
            role = "Employee"
            department = "Technical"
            designation = ""

            userCreated = false
            showErrors = false
            createdUserName = ""
        }
    }

    // ✅ FIXED: Scaffold at ROOT
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Create New User",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Administrator Panel · $companyName",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // ✅ IMPORTANT
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {

            Spacer(modifier = Modifier.height(6.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // ✅ Success Banner
            AnimatedVisibility(
                visible = showSuccessAnimation,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("User Created Successfully!", fontWeight = FontWeight.SemiBold)
                        if (createdUserName.isNotBlank()) {
                            Text("$createdUserName added to $companyName", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── FORM ─────────────────────────────
            SectionLabel("Account Information")

            StyledReadOnlyField(
                value = companyName,
                label = "Company Name",
                icon = Icons.Default.Business
            )

            StyledTextField(
                value = name,
                onValueChange = { name = it },
                label = "Full Name",
                icon = Icons.Default.Person,
                isError = showErrors && name.length < 2
            )

            StyledTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                isError = showErrors && !Patterns.EMAIL_ADDRESS.matcher(email).matches()
            )

            SectionLabel("Role & Department")

            DepartmentDropdownMenuBox(
                departmentOptions,
                department,
                { department = it },
                isError = showErrors && department.isBlank()
            )

            StyledTextField(
                value = designation,
                onValueChange = { designation = it },
                label = "Designation",
                icon = Icons.Default.Work,
                isError = showErrors && designation.isBlank()
            )

            RoleDropdownMenuBox(roleOptions, role) { role = it }

            SectionLabel("Security")

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = showErrors && password.length < 4
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Button
            Button(
                onClick = {
                    if (isFormValid) {
                        createdUserName = name
                        userCreated = true
                        onCreateUserClick(name, email, role, department, designation, password)
                    } else showErrors = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create User", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Helper Composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        placeholder = if (placeholder != null) ({ Text(placeholder, fontSize = 13.sp) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        isError = isError
    )
    if (errorText != null) {
        ErrorText(errorText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledReadOnlyField(
    value: String,
    label: String,
    icon: ImageVector
) {
    TextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent
        )
    )
}

// ── Department Dropdown ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentDropdownMenuBox(
    departmentOptions: List<Pair<String, ImageVector>>,
    selected: String,
    onDepartmentSelected: (String) -> Unit,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "arrowRotation"
    )

    val selectedIcon = departmentOptions.find { it.first == selected }?.second ?: Icons.Default.Apartment

    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text("Department") },
            leadingIcon = {
                Icon(
                    imageVector = selectedIcon,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .rotate(arrowRotation)
                        .clickable { expanded = !expanded },
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(14.dp),
            isError = isError
        )

        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column {
                    departmentOptions.forEachIndexed { index, (departmentName, icon) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (departmentName == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = departmentName,
                                        fontSize = 15.sp,
                                        color = if (departmentName == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (departmentName == selected) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = { onDepartmentSelected(departmentName); expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (index < departmentOptions.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        if (expanded) {
            Box(modifier = Modifier.fillMaxSize().clickable { expanded = false })
        }
    }
}

// ── Role Dropdown ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleDropdownMenuBox(
    roleOptions: List<String>,
    selected: String,
    onRoleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
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
                    modifier = Modifier.rotate(arrowRotation).clickable { expanded = !expanded },
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(14.dp)
        )

        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column {
                    roleOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (option) {
                                            "Employee" -> Icons.Default.Person
                                            "Team Leader" -> Icons.Default.SupervisorAccount
                                            "Manager" -> Icons.Default.ManageAccounts
                                            "Administrator" -> Icons.Default.AdminPanelSettings
                                            "HR" -> Icons.Default.Groups
                                            "Intern" -> Icons.Default.School
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
                                        fontSize = 15.sp,
                                        color = if (option == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (option == selected) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = { onRoleSelected(option); expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (index < roleOptions.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        if (expanded) {
            Box(modifier = Modifier.fillMaxSize().clickable { expanded = false })
        }
    }
}