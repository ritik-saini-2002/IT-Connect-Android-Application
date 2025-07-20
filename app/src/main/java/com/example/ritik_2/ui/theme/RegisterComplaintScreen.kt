package com.example.ritik_2.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import com.example.ritik_2.ComplaintData
import com.example.ritik_2.HapticType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterComplaintScreen(
    onSaveClick: (ComplaintData) -> Unit,
    onResetClick: () -> Unit,
    onViewComplaintsClick: () -> Unit,
    onFilePickerClick: () -> Unit,
    onHapticFeedback: (HapticType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val view = LocalView.current

    // State variables
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUrgency by remember { mutableStateOf("Medium") }
    var selectedCategory by remember { mutableStateOf("IT") }
    var contactInfo by remember { mutableStateOf("") }
    var attachmentAdded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
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

    // Categories with Material 3 icons
    val categories = listOf(
        "IT" to Icons.Filled.Computer,
        "HR" to Icons.Filled.People,
        "Facilities" to Icons.Filled.Apartment,
        "Security" to Icons.Filled.Security,
        "Finance" to Icons.Filled.AttachMoney,
        "Others" to Icons.Filled.MoreHoriz
    )

    val urgencyLevels = listOf("Low", "Medium", "High", "Critical")

    // Animation for success feedback
    val scale by animateFloatAsState(
        targetValue = if (showSuccessAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "success_scale"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
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
                    // Header Card
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
                            Text(
                                text = "Submit Your Feedback",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "We value your input and are committed to addressing your concerns promptly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Global Toggle Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                onHapticFeedback(HapticType.LIGHT)
                                isGlobal = !isGlobal
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGlobal) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
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
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                    else primaryColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Send to All Users (Global)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isGlobal) "This complaint will be visible to all users."
                                        else "Only you can view this complaint.",
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
                                    checkedThumbColor = primaryColor,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline
                                )
                            )
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
                        label = { Text("Complaint Title") },
                        placeholder = { Text("Brief title for your complaint") },
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
                            Text("${title.length}/100")
                        }
                    )

                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (it.length <= 500) {
                                description = it
                                if (it.isNotBlank()) onHapticFeedback(HapticType.LIGHT)
                            }
                        },
                        label = { Text("Description") },
                        placeholder = { Text("Detailed description of your issue") },
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
                        maxLines = 5,
                        supportingText = {
                            Text("${description.length}/500")
                        }
                    )

                    // Category Section
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
                                    Text(
                                        text = "Category: $selectedCategory",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
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
                                    items(categories.size) { index ->
                                        val (category, icon) = categories[index]
                                        FilterChip(
                                            onClick = {
                                                onHapticFeedback(HapticType.LIGHT)
                                                selectedCategory = category
                                            },
                                            label = { Text(category) },
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
                                    Text(
                                        text = "Priority: $selectedUrgency",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
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
                                    items(urgencyLevels.size) { index ->
                                        val urgency = urgencyLevels[index]
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
                        placeholder = { Text("Email or phone number") },
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
                                        text = if (attachmentAdded) "Tap to change file" else "Tap to select file",
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
                                selectedCategory = "IT"
                                contactInfo = ""
                                attachmentAdded = false
                                isGlobal = false
                                expandedSection = 0
                                onResetClick()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
//                            colors = OutlinedButtomDefaults.outlinedButtonColors(
//                                contentColor = MaterialTheme.colorScheme.onSurface
//                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset", fontWeight = FontWeight.Medium)
                        }

                        // Submit Button
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
                                            category = selectedCategory,
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
                                        selectedCategory = "IT"
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
                                            message = "Please fill in required fields",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
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
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSubmitting) "Submitting..." else "Submit",
                                fontWeight = FontWeight.Medium
                            )
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Complaint Submitted!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Thank you for your feedback",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}