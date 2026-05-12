package com.saini.ritik.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberPickerScreen(
    viewModel  : MemberPickerViewModel,
    isGroupMode: Boolean,
    onConfirm  : (ids: List<String>, names: List<String>, groupName: String) -> Unit,
    onBack     : () -> Unit
) {
    val state  by viewModel.state.collectAsState()
    var groupName      by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }

    // Only members that are currently selected — kept in sync with full members list
    val selectedMembers = remember(state.members, state.selected) {
        state.members.filter { it.userId in state.selected }
    }

    // ── Group name dialog ─────────────────────────────────────────────────────
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            icon    = { Icon(Icons.Default.Group, null,
                tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Group Name", fontWeight = FontWeight.Bold) },
            text    = {
                OutlinedTextField(
                    value         = groupName,
                    onValueChange = { groupName = it },
                    label         = { Text("Enter group name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNameDialog = false
                        onConfirm(
                            selectedMembers.map { it.userId },
                            selectedMembers.map { it.name },
                            groupName.ifBlank { "Group Chat" }
                        )
                    },
                    enabled = groupName.isNotBlank()
                ) { Text("Create Group") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        ))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text       = if (isGroupMode) "Select Members" else "New Message",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text     = if (state.selected.isEmpty())
                                "Select people to chat with"
                            else
                                "${state.selected.size} selected",
                            fontSize = 11.sp,
                            color    = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    // Confirm button — visible once at least one member is selected
                    if (state.selected.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (isGroupMode) {
                                    showNameDialog = true
                                } else {
                                    onConfirm(
                                        selectedMembers.map { it.userId },
                                        selectedMembers.map { it.name },
                                        ""
                                    )
                                }
                            }
                        ) {
                            Text(
                                text       = if (isGroupMode) "Next" else "Chat",
                                color      = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = viewModel::search,
                placeholder   = { Text("Search people…") },
                leadingIcon   = {
                    Icon(Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                shape      = RoundedCornerShape(14.dp)
            )

            // ── Department + Role filter chips ────────────────────────────────
            LazyRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.departments) { dept ->
                    FilterChip(
                        selected = state.filterDept == dept,
                        onClick  = { viewModel.filterDept(dept) },
                        label    = { Text(dept, fontSize = 11.sp) }
                    )
                }
                // Divider between dept and role chips
                if (state.departments.isNotEmpty() && state.roles.isNotEmpty()) {
                    item {
                        VerticalDivider(
                            Modifier
                                .height(28.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
                items(state.roles) { role ->
                    FilterChip(
                        selected = state.filterRole == role,
                        onClick  = { viewModel.filterRole(role) },
                        label    = { Text(role, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Selected member chips (horizontal scroll) ─────────────────────
            if (selectedMembers.isNotEmpty()) {
                LazyRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedMembers, key = { it.userId }) { m ->
                        InputChip(
                            selected     = true,
                            onClick      = { viewModel.toggleSelect(m.userId) },
                            label        = {
                                Text(m.name.split(" ").first(), fontSize = 12.sp)
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove",
                                    modifier = Modifier.size(14.dp))
                            },
                            avatar = {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        m.name.take(1).uppercase(),
                                        fontSize   = 10.sp,
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ── Member list ───────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonSearch, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                        Spacer(Modifier.height(12.dp))
                        Text("No people found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        Text(
                            "${state.filtered.size} people",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(state.filtered, key = { it.userId }) { member ->
                        val isSelected = member.userId in state.selected
                        MemberRow(
                            member     = member,
                            isSelected = isSelected,
                            onClick    = { viewModel.toggleSelect(member.userId) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Single member row ─────────────────────────────────────────────────────────

@Composable
private fun MemberRow(
    member    : ChatMember,
    isSelected: Boolean,
    onClick   : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar circle with initials
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f)
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                member.name.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                color      = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Name + role · dept
        Column(Modifier.weight(1f)) {
            Text(
                member.name,
                fontWeight = FontWeight.Medium,
                fontSize   = 14.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                "${member.role} · ${member.department}",
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Selection indicator
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle
            else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint     = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
            modifier = Modifier.size(22.dp)
        )
    }
}