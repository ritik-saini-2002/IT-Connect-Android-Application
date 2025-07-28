package com.example.ritik_2.complaint.complaintregistration

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterComplaintScreen(
    onSaveClick: (ComplaintData) -> Unit,
    onResetClick: () -> Unit,
    onViewComplaintsClick: () -> Unit,
    onFilePickerClick: () -> Unit,
    onHapticFeedback: (HapticType) -> Unit,
    availableDepartments: List<DepartmentInfo> = emptyList(),
    currentUserData: UserData? = null
)  {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val view = LocalView.current

    // State variables
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUrgency by remember { mutableStateOf("Medium") }
    var selectedCategory by remember { mutableStateOf("Technical") } // Keep this as category
    var contactInfo by remember { mutableStateOf("") }
    var attachmentAdded by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(0) }
    var isGlobal by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dynamic colors from Material 3
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Urgency level colors with proper Material 3 theming
    val urgencyColors = mapOf(
        "Low" to tertiaryColor,
        "Medium" to MaterialTheme.colorScheme.secondary,
        "High" to MaterialTheme.colorScheme.tertiary,
        "Critical" to errorColor
    )

    // Updated categories with Material 3 icons - matching backend mapping
    val categories = listOf(
        "Technical" to Icons.Filled.Computer,
        "HR" to Icons.Filled.People,
        "Administrative" to Icons.Filled.Apartment,
        "IT Support" to Icons.Filled.Settings,
        "Finance" to Icons.Filled.AttachMoney,
        "General" to Icons.Filled.MoreHoriz
    )

    val categoryToDepartmentMapping = mapOf(
        "Technical" to "Technical",
        "HR" to "Human Resources",
        "Administrative" to "Administration",
        "IT Support" to "IT Support",
        "Finance" to "Finance",
        "General" to "General"
    )

    val selectedDepartmentName = categoryToDepartmentMapping[selectedCategory] ?: selectedCategory

    val urgencyLevels = listOf("Low", "Medium", "High", "Critical")

    // Animation for success feedback
    val scale by animateFloatAsState(
        targetValue = if (showSuccessAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "success_scale"
    )

    // Find target department for selected category
    val targetDepartment = remember(selectedCategory, availableDepartments) {
        val departmentName = categoryToDepartmentMapping[selectedCategory] ?: selectedCategory
        availableDepartments.find { dept ->
            dept.departmentName.equals(departmentName, ignoreCase = true) ||
                    dept.departmentName.contains(departmentName, ignoreCase = true) ||
                    departmentName.contains(dept.departmentName, ignoreCase = true)
        } ?: availableDepartments.firstOrNull()
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Submit Complaint",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        if (currentUserData != null) {
                            Text(
                                text = "${currentUserData.name} • ${currentUserData.department}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onHapticFeedback(HapticType.LIGHT)
                            onViewComplaintsClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assignment,
                            contentDescription = "View Complaints"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {

                    //Header Card with Department Ritik Saini

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Category & Department Assignment",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (targetDepartment != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$selectedCategory → ${targetDepartment.departmentName}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "${targetDepartment.userCount} available team members • ${targetDepartment.availableRoles.joinToString(", ")} roles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    text = "Category: $selectedCategory → Department: $selectedDepartmentName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }


                    // Global Visibility Toggle - Enhanced
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGlobal) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isGlobal) 4.dp else 2.dp
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isGlobal) Icons.Filled.Public else Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isGlobal) "Global Complaint" else "Department Complaint",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isGlobal)
                                                "Visible to all users in ${currentUserData?.companyName ?: "your company"}"
                                            else
                                                "Visible only to ${targetDepartment?.departmentName ?: "assigned"} department",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isGlobal,
                                    onCheckedChange = {
                                        onHapticFeedback(HapticType.LIGHT)
                                        isGlobal = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }

                            if (isGlobal) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Global complaints get higher priority and company-wide notifications",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            if (it.length <= 100) {
                                title = it
                                if (it.isNotBlank()) onHapticFeedback(HapticType.LIGHT)
                            }
                        },
                        label = { Text("Complaint Title *") },
                        placeholder = { Text("Brief, descriptive title") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Title,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = primaryColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${title.length}/100")
                                if (title.isBlank()) {
                                    Text(
                                        "Required field",
                                        color = errorColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        isError = title.isBlank()
                    )

                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (it.length <= 1000) {
                                description = it
                                if (it.isNotBlank()) onHapticFeedback(HapticType.LIGHT)
                            }
                        },
                        label = { Text("Detailed Description *") },
                        placeholder = { Text("Provide comprehensive details about the issue, including steps to reproduce, impact, and any relevant context") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = primaryColor
                        ),
                        maxLines = 6,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${description.length}/1000")
                                if (description.isBlank()) {
                                    Text(
                                        "Required field",
                                        color = errorColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        isError = description.isBlank()
                    )

                    //Category Section with Department

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                onHapticFeedback(HapticType.LIGHT)
                                expandedSection = if (expandedSection == 1) 0 else 1
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Category,
                                        contentDescription = null,
                                        tint = primaryColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Category: $selectedCategory",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (targetDepartment != null) {
                                            Text(
                                                text = "→ ${targetDepartment.departmentName} Department",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = "→ $selectedDepartmentName Department",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (expandedSection == 1) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = expandedSection == 1,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                LazyRow(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(categories) { (category, icon) ->
                                        FilterChip(
                                            onClick = {
                                                onHapticFeedback(HapticType.LIGHT)
                                                selectedCategory = category
                                            },
                                            label = {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(category)
                                                    Text(
                                                        text = "→ ${categoryToDepartmentMapping[category] ?: category}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (selectedCategory == category)
                                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            selected = selectedCategory == category,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Urgency Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                onHapticFeedback(HapticType.LIGHT)
                                expandedSection = if (expandedSection == 2) 0 else 2
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.PriorityHigh,
                                        contentDescription = null,
                                        tint = urgencyColors[selectedUrgency] ?: primaryColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Priority: $selectedUrgency",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = getEstimatedResolutionTime(selectedUrgency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (expandedSection == 2) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = expandedSection == 2,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                LazyRow(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(urgencyLevels) { urgency ->
                                        FilterChip(
                                            onClick = {
                                                onHapticFeedback(HapticType.LIGHT)
                                                selectedUrgency = urgency
                                            },
                                            label = { Text(urgency) },
                                            selected = selectedUrgency == urgency,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = urgencyColors[urgency] ?: primaryColor,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Contact Info
                    OutlinedTextField(
                        value = contactInfo,
                        onValueChange = {
                            contactInfo = it
                            if (it.isNotBlank()) onHapticFeedback(HapticType.LIGHT)
                        },
                        label = { Text("Contact Information (Optional)") },
                        placeholder = { Text("Email, phone, or preferred contact method") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.ContactMail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = primaryColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
                    )

                    // Attachment Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .clickable {
                                onHapticFeedback(HapticType.LIGHT)
                                onFilePickerClick()
                                attachmentAdded = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (attachmentAdded) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    tint = if (attachmentAdded) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (attachmentAdded) "File Attached" else "Add Attachment",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (attachmentAdded) MaterialTheme.colorScheme.onTertiaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (attachmentAdded) "Tap to change file (Max 1MB)" else "Screenshots, documents, etc. (Max 1MB)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (attachmentAdded) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (attachmentAdded) {
                                IconButton(
                                    onClick = {
                                        onHapticFeedback(HapticType.LIGHT)
                                        attachmentAdded = false
                                        onResetClick()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove attachment",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Reset Button
                        OutlinedButton(
                            onClick = {
                                onHapticFeedback(HapticType.MEDIUM)
                                title = ""
                                description = ""
                                selectedUrgency = "Medium"
                                selectedCategory = "Technical" // Reset to default category
                                contactInfo = ""
                                attachmentAdded = false
                                isGlobal = false
                                expandedSection = 0
                                onResetClick()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset", fontWeight = FontWeight.Medium)
                        }

                        // Submit Button (Updated to send department instead of category)
                        Button(
                            onClick = {
                                if (title.isNotBlank() && description.isNotBlank()) {
                                    onHapticFeedback(HapticType.SUCCESS)
                                    keyboardController?.hide()
                                    isSubmitting = true
                                    showSuccessAnimation = true

                                    scope.launch {
                                        delay(500)
                                        val complaintData = ComplaintData(
                                            title = title,
                                            description = description,
                                            department = selectedDepartmentName, // Send department name instead of category
                                            urgency = selectedUrgency,
                                            contactInfo = contactInfo,
                                            hasAttachment = attachmentAdded,
                                            isGlobal = isGlobal
                                        )

                                        onSaveClick(complaintData)

                                        // Reset form after submission
                                        title = ""
                                        description = ""
                                        selectedUrgency = "Medium"
                                        selectedCategory = "Technical" // Reset category
                                        contactInfo = ""
                                        attachmentAdded = false
                                        isGlobal = false
                                        expandedSection = 0
                                        isSubmitting = false
                                        showSuccessAnimation = false
                                    }
                                } else {
                                    onHapticFeedback(HapticType.ERROR)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Please fill in all required fields",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGlobal) MaterialTheme.colorScheme.secondary else primaryColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (isGlobal) Icons.Filled.Public else Icons.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSubmitting) "Submitting..."
                                else if (isGlobal) "Submit Global"
                                else "Submit",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Department Summary Card
                    if (availableDepartments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Business,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Available Departments",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableDepartments) { dept ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (dept == targetDepartment)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 6.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (dept == targetDepartment) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = "${dept.departmentName} (${dept.userCount})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (dept == targetDepartment)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Success Animation Overlay
                if (showSuccessAnimation) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) { },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGlobal) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isGlobal) Icons.Filled.Public else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isGlobal) "Global Complaint Submitted!" else "Complaint Submitted!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isGlobal)
                                        "Notified all company users • High priority processing"
                                    else
                                        "Assigned to ${targetDepartment?.departmentName ?: "appropriate"} department",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

// Helper function for estimated resolution time
private fun getEstimatedResolutionTime(urgency: String): String {
    return when (urgency) {
        "Critical" -> "Est. resolution: 4 hours"
        "High" -> "Est. resolution: 24 hours"
        "Medium" -> "Est. resolution: 3 days"
        "Low" -> "Est. resolution: 1 week"
        else -> "Est. resolution: 1 week"
    }
}