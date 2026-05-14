package com.saini.ritik.complaint.registercomplaint

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterComplaintScreen(
    uiState           : RegisterComplaintUiState,
    onTitleChange     : (String) -> Unit,
    onDescChange      : (String) -> Unit,
    onCategoryChange  : (String) -> Unit,
    onPriorityChange  : (String) -> Unit,
    onDepartmentChange: (String) -> Unit,
    onPickImages      : () -> Unit,
    onPickPdf         : () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSubmit          : () -> Unit,
    onBack            : () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Raise Ticket",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface,
                    titleContentColor = cs.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header card ───────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.primaryContainer.copy(0.3f))
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(cs.primary, cs.tertiary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SupportAgent, null,
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Submit a Support Ticket",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface)
                            Text("Fill in the details below to raise your concern",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant)
                        }
                    }
                }

                // ── Title ─────────────────────────────────────────────────────
                SectionLabel("Ticket Title", Icons.Outlined.Title)
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = onTitleChange,
                    label = { Text("Enter ticket title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = uiState.title.isBlank() && uiState.error != null,
                    leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = cs.primary) }
                )

                // ── Description ───────────────────────────────────────────────
                SectionLabel("Description", Icons.Outlined.Description)
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = onDescChange,
                    label = { Text("Describe your issue in detail") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4,
                    maxLines = 8,
                    isError = uiState.description.isBlank() && uiState.error != null
                )

                // ── Category dropdown ─────────────────────────────────────────
                SectionLabel("Category", Icons.Outlined.Category)
                DropdownSelector(
                    selected = uiState.category,
                    options  = uiState.categories,
                    onSelect = onCategoryChange,
                    icon     = Icons.Outlined.Category
                )

                // ── Priority chips ────────────────────────────────────────────
                SectionLabel("Priority", Icons.Outlined.PriorityHigh)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.priorities.forEach { p ->
                        val selected = p == uiState.priority
                        val chipColor = when (p) {
                            "Low"      -> Color(0xFF4CAF50)
                            "Medium"   -> Color(0xFFFF9800)
                            "High"     -> Color(0xFFFF5722)
                            "Critical" -> Color(0xFFD32F2F)
                            else       -> cs.primary
                        }
                        FilterChip(
                            selected = selected,
                            onClick  = { onPriorityChange(p) },
                            label    = { Text(p, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (selected) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(0.15f),
                                selectedLabelColor     = chipColor,
                                selectedLeadingIconColor = chipColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (selected) chipColor else cs.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Department dropdown ───────────────────────────────────────
                if (uiState.departments.isNotEmpty()) {
                    SectionLabel("Department", Icons.Outlined.Business)
                    DropdownSelector(
                        selected = uiState.department,
                        options  = uiState.departments,
                        onSelect = onDepartmentChange,
                        icon     = Icons.Outlined.Business
                    )
                }

                // ── Attachments ───────────────────────────────────────────────
                SectionLabel("Attachments", Icons.Outlined.AttachFile)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickImages,
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Images")
                    }
                    OutlinedButton(
                        onClick = onPickPdf,
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PDF")
                    }
                }

                // ── Attachment preview row ────────────────────────────────────
                if (uiState.attachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.attachments, key = { it.id }) { att ->
                            AttachmentChip(att, onRemove = { onRemoveAttachment(att.id) })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Submit button ─────────────────────────────────────────────
                Button(
                    onClick  = onSubmit,
                    enabled  = !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp)),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = cs.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Submitting…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Submit Ticket", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Dropdown selector ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    selected: String,
    options : List<String>,
    onSelect: (String) -> Unit,
    icon    : ImageVector
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                    leadingIcon = {
                        if (opt == selected) Icon(Icons.Default.Check, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

// ── Attachment chip ───────────────────────────────────────────────────────────
@Composable
private fun AttachmentChip(attachment: AttachmentItem, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (attachment.isImage) cs.primaryContainer.copy(0.5f)
                else cs.tertiaryContainer.copy(0.5f),
        modifier = Modifier.width(140.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.PictureAsPdf,
                null,
                tint = if (attachment.isImage) cs.primary else cs.tertiary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                attachment.name.take(12),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(cs.error.copy(0.1f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null,
                    tint = cs.error, modifier = Modifier.size(12.dp))
            }
        }
    }
}
