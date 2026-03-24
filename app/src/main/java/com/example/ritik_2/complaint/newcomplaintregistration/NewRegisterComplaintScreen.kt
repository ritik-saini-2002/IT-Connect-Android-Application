package com.example.ritik_2.complaint.newcomplaintregistration

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import com.example.ritik_2.complaint.newcomplaintregistration.models.ComplaintFormEvent
import com.example.ritik_2.complaint.newcomplaintregistration.models.ComplaintFormUiState
import com.example.ritik_2.complaint.newcomplaintregistration.models.avatarColorsStatic
import com.example.ritik_2.complaint.newcomplaintregistration.models.urgencyColorStatic

/**
 * NewRegisterComplaintScreen
 *
 * Pure Compose UI — no Firebase, no Coroutines, no ViewModel access.
 * Receives all data via [uiState] and communicates via [onEvent] / [onSubmit] / [onBack].
 *
 * Layout (top to bottom):
 *   TopAppBar  → profile avatar + user name/role
 *   UserInfoCard
 *   GlobalToggleCard
 *   Title field
 *   Description field
 *   Category picker (LazyRow chips)
 *   Urgency picker (4-up grid)
 *   Contact info field
 *   Attachment card
 *   WhatHappensNextCard
 *   Reset + Submit buttons
 *   SuccessOverlay (when submitting)
 *   Error Snackbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRegisterComplaintScreen(
    uiState: ComplaintFormUiState,
    onEvent: (ComplaintFormEvent) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState       = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // Show error in Snackbar whenever submitError changes
    LaunchedEffect(uiState.submitError) {
        uiState.submitError?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            onEvent(ComplaintFormEvent.ClearError)
        }
    }

    val categories = listOf(
        "Technical"      to Icons.Filled.Computer,
        "HR"             to Icons.Filled.People,
        "Administrative" to Icons.Filled.Apartment,
        "IT Support"     to Icons.Filled.Settings,
        "Finance"        to Icons.Filled.AttachMoney,
        "General"        to Icons.Filled.MoreHoriz
    )
    val urgencies = listOf("Low", "Medium", "High", "Critical")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            profilePictureUrl = uiState.profilePictureUrl,
                            userName          = uiState.userName,
                            isLoaded          = uiState.profileLoaded,
                            size              = 36.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "New Complaint",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp
                            )
                            if (uiState.userName.isNotBlank()) {
                                Text(
                                    "${uiState.userName} · ${uiState.userRole}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── User info card ─────────────────────────────────────
                UserInfoCard(
                    userName          = uiState.userName,
                    userRole          = uiState.userRole,
                    userDept          = uiState.userDept,
                    profilePictureUrl = uiState.profilePictureUrl,
                    isLoaded          = uiState.profileLoaded
                )

                // ── Global toggle ──────────────────────────────────────
                GlobalToggleCard(
                    isGlobal = uiState.isGlobal,
                    onToggle = { onEvent(ComplaintFormEvent.GlobalToggled(it)) },
                    userDept = uiState.userDept
                )

                // ── Title ──────────────────────────────────────────────
                OutlinedTextField(
                    value         = uiState.title,
                    onValueChange = { onEvent(ComplaintFormEvent.TitleChanged(it)) },
                    label         = { Text("Complaint Title *") },
                    placeholder   = { Text("Brief, clear description of the issue") },
                    leadingIcon   = {
                        Icon(Icons.Default.Title, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp),
                    isError       = uiState.title.isBlank(),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (uiState.title.isBlank())
                                Text("Required", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall)
                            else Text("")
                            Text("${uiState.title.length}/100",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // ── Description ────────────────────────────────────────
                OutlinedTextField(
                    value         = uiState.description,
                    onValueChange = { onEvent(ComplaintFormEvent.DescriptionChanged(it)) },
                    label         = { Text("Detailed Description *") },
                    placeholder   = { Text("What happened? When? Impact? Steps to reproduce?") },
                    leadingIcon   = {
                        Icon(Icons.Default.Description, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier  = Modifier.fillMaxWidth().height(140.dp),
                    shape     = RoundedCornerShape(14.dp),
                    isError   = uiState.description.isBlank(),
                    maxLines  = 7,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (uiState.description.isBlank())
                                Text("Required", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall)
                            else Text("")
                            Text("${uiState.description.length}/1000",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // ── Category picker ────────────────────────────────────
                SectionCard(title = "Category", icon = Icons.Default.Category) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding        = PaddingValues(vertical = 4.dp)
                    ) {
                        items(categories) { (cat, icon) ->
                            val isSelected = uiState.selectedCategory == cat
                            FilterChip(
                                selected    = isSelected,
                                onClick     = { onEvent(ComplaintFormEvent.CategoryChanged(cat)) },
                                label       = { Text(cat, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // ── Urgency picker ─────────────────────────────────────
                SectionCard(title = "Priority / Urgency", icon = Icons.Default.PriorityHigh) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        urgencies.forEach { urg ->
                            val isSelected = uiState.selectedUrgency == urg
                            val color      = urgencyColorStatic(urg)
                            Surface(
                                onClick   = { onEvent(ComplaintFormEvent.UrgencyChanged(urg)) },
                                modifier  = Modifier.weight(1f),
                                shape     = RoundedCornerShape(12.dp),
                                color     = if (isSelected) color.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border    = if (isSelected) BorderStroke(2.dp, color) else null
                            ) {
                                Column(
                                    modifier              = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment   = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        urg,
                                        fontSize   = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (isSelected) color
                                                     else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Est. resolution: ${uiState.estimatedResolutionTime}",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }

                // ── Contact info ───────────────────────────────────────
                OutlinedTextField(
                    value         = uiState.contactInfo,
                    onValueChange = { onEvent(ComplaintFormEvent.ContactInfoChanged(it)) },
                    label         = { Text("Contact Info (optional)") },
                    placeholder   = { Text("Phone / alternate email / preferred contact") },
                    leadingIcon   = {
                        Icon(Icons.Default.ContactMail, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction    = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // ── Attachment ─────────────────────────────────────────
                AttachmentCard(
                    hasAttachment = uiState.hasAttachment,
                    onPickFile    = { onEvent(ComplaintFormEvent.FilePicked(Uri.EMPTY)) }, // triggers picker via Activity
                    onRemove      = { onEvent(ComplaintFormEvent.FileRemoved) }
                )

                // ── What happens next ──────────────────────────────────
                WhatHappensNextCard(
                    isGlobal = uiState.isGlobal,
                    category = uiState.selectedCategory
                )

                // ── Buttons ────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick  = { onEvent(ComplaintFormEvent.ResetForm) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }

                    Button(
                        onClick  = onSubmit,
                        modifier = Modifier.weight(2f),
                        enabled  = !uiState.isSubmitting && uiState.isFormValid,
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isGlobal)
                                MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                if (uiState.isGlobal) Icons.Default.Public else Icons.Default.Send,
                                null, modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                uiState.isSubmitting -> "Submitting…"
                                uiState.isGlobal     -> "Submit Global"
                                else                 -> "Submit Complaint"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Submitting overlay ─────────────────────────────────────
            if (uiState.isSubmitting) {
                SubmittingOverlay(isGlobal = uiState.isGlobal)
            }
        }
    }
}

// ─────────────────────────────────────────────
// PROFILE AVATAR
// Shows picture → initials → animated logo (in that priority order)
// ─────────────────────────────────────────────

@Composable
fun ProfileAvatar(
    profilePictureUrl: String?,
    userName: String,
    isLoaded: Boolean,
    size: Dp = 40.dp
) {
    Box(
        modifier        = Modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            !isLoaded -> PulsingAvatar(size)

            !profilePictureUrl.isNullOrBlank() -> {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profilePictureUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile picture",
                    modifier           = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale       = ContentScale.Crop,
                    loading            = { PulsingAvatar(size) },
                    error              = { InitialsAvatar(name = userName, size = size) }
                )
            }

            userName.isNotBlank() -> InitialsAvatar(name = userName, size = size)

            else -> AnimatedLogoAvatar(size = size)
        }
    }
}

@Composable
private fun PulsingAvatar(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun InitialsAvatar(name: String, size: Dp) {
    val initials = remember(name) {
        name.split(" ").take(2).joinToString("") {
            it.firstOrNull()?.uppercaseChar()?.toString() ?: ""
        }
    }
    val colors = remember(name) { avatarColorsStatic(name.hashCode()) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.first, colors.second))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = initials,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = (size.value / 2.8f).sp
        )
    }
}

@Composable
fun AnimatedLogoAvatar(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label         = "logoRotate"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue  = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "logoScale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFF6366F1))
                )
            )
            .rotate(rotation)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Assignment,
            contentDescription = null,
            tint     = Color.White,
            modifier = Modifier.size(size * 0.5f).rotate(-rotation)
        )
    }
}

// ─────────────────────────────────────────────
// USER INFO CARD
// ─────────────────────────────────────────────

@Composable
fun UserInfoCard(
    userName: String,
    userRole: String,
    userDept: String,
    profilePictureUrl: String?,
    isLoaded: Boolean
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileAvatar(
                profilePictureUrl = profilePictureUrl,
                userName          = userName,
                isLoaded          = isLoaded,
                size              = 56.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (userName.isNotBlank()) userName else "Loading…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (userRole.isNotBlank()) {
                    Text(
                        text  = "$userRole · $userDept",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Text(
                        "Submitting as yourself",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// GLOBAL TOGGLE CARD
// ─────────────────────────────────────────────

@Composable
fun GlobalToggleCard(
    isGlobal: Boolean,
    onToggle: (Boolean) -> Unit,
    userDept: String
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isGlobal) MaterialTheme.colorScheme.secondaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isGlobal) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isGlobal) Icons.Default.Public else Icons.Default.Lock,
                        null,
                        tint     = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            if (isGlobal) "Global Complaint" else "Department Complaint",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isGlobal) "Visible to all company users"
                            else "Visible to $userDept department only",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isGlobal)
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = isGlobal, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = isGlobal) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Global complaints get higher priority and notify all department heads",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// SECTION CARD WRAPPER
// ─────────────────────────────────────────────

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────
// ATTACHMENT CARD
// ─────────────────────────────────────────────

@Composable
fun AttachmentCard(
    hasAttachment: Boolean,
    onPickFile: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick   = { if (!hasAttachment) onPickFile() },
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (hasAttachment) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.weight(1f)
            ) {
                Icon(
                    if (hasAttachment) Icons.Default.AttachFile else Icons.Outlined.AttachFile,
                    null,
                    tint     = if (hasAttachment) MaterialTheme.colorScheme.onTertiaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        if (hasAttachment) "File Attached" else "Add Attachment",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color      = if (hasAttachment) MaterialTheme.colorScheme.onTertiaryContainer
                                     else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (hasAttachment) "Tap × to remove (max 2 MB)"
                        else "Screenshots, documents, logs (max 2 MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasAttachment)
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasAttachment) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// WHAT HAPPENS NEXT CARD
// ─────────────────────────────────────────────

@Composable
fun WhatHappensNextCard(isGlobal: Boolean, category: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier              = Modifier.padding(14.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp))
                Text("What happens next?",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            val steps = if (isGlobal) listOf(
                "🔔" to "All department heads are notified immediately",
                "👤" to "A head will assign it to the right team member",
                "📊" to "You can track progress live in 'My Complaints'",
                "✅" to "You'll be notified at every status change"
            ) else listOf(
                "🔔" to "$category department head is notified",
                "👤" to "Head assigns to an employee in that department",
                "📊" to "Track progress live in 'My Complaints'",
                "✅" to "Notified when resolved"
            )
            steps.forEach { (emoji, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(emoji, fontSize = 13.sp)
                    Text(text, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// SUBMITTING OVERLAY
// ─────────────────────────────────────────────

@Composable
fun SubmittingOverlay(isGlobal: Boolean) {
    val scale by animateFloatAsState(
        targetValue  = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "overlayScale"
    )
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue  = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label         = "spinRotate"
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier.scale(scale),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(
                containerColor = if (isGlobal) MaterialTheme.colorScheme.secondaryContainer
                                 else MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier              = Modifier.padding(36.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    if (isGlobal) Icons.Default.Public else Icons.Default.CheckCircle,
                    null,
                    modifier = Modifier.size(52.dp).rotate(rotation),
                    tint     = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                               else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (isGlobal) "Submitting Global Complaint…" else "Submitting Complaint…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = if (isGlobal) MaterialTheme.colorScheme.onSecondaryContainer
                                 else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Notifying department heads…",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = if (isGlobal)
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = if (isGlobal) MaterialTheme.colorScheme.secondary
                               else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
