package com.example.ritik_2.administrator.rolemanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.data.model.UserProfile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(
    viewModel    : RoleManagementViewModel,
    onRoleChanged: (String, String, String) -> Unit
) {
    val state        by viewModel.state.collectAsState()
    var confirmDialog by remember { mutableStateOf<Triple<UserProfile, String, String>?>(null) }
    val snackState   = remember { SnackbarHostState() }

    // Only one snackbar source — successMsg only, Toast handles onRoleChanged
    LaunchedEffect(state.successMsg) {
        state.successMsg?.let {
            snackState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackState.showSnackbar("⚠ $it", duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Role Management", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = viewModel::search,
                placeholder   = { Text("Search by name, role, department…") },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                },
                modifier   = Modifier.fillMaxWidth(),
                shape      = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Loading users…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                state.filteredUsers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PersonSearch,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.searchQuery.isBlank()) "No users found"
                                else "No results for \"${state.searchQuery}\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    // User count header
                    Text(
                        "${state.filteredUsers.size} user${if (state.filteredUsers.size != 1) "s" else ""}",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.filteredUsers, key = { it.id }) { user ->
                            UserRoleCard(
                                user           = user,
                                availableRoles = viewModel.availableRoles,
                                isUpdating     = state.isLoading,
                                onRoleSelected = { newRole ->
                                    confirmDialog = Triple(user, user.role, newRole)
                                }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // Confirm dialog
    confirmDialog?.let { (user, oldRole, newRole) ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            icon  = {
                Icon(Icons.Default.SwapHoriz, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Change Role?", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text("You are about to change:")
                    Spacer(Modifier.height(8.dp))
                    Text("👤  ${user.name}", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RolePill(oldRole, roleColor(oldRole))
                        Icon(Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 8.dp).size(14.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        RolePill(newRole, roleColor(newRole))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Permissions will be automatically updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.changeUserRole(user, newRole, onRoleChanged)
                    confirmDialog = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// ── User card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRoleCard(
    user          : UserProfile,
    availableRoles: List<String>,
    isUpdating    : Boolean,
    onRoleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRot by animateFloatAsState(
        targetValue   = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label         = "arrow"
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {

            // User info row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model        = ImageRequest.Builder(LocalContext.current)
                                .data(user.imageUrl).crossfade(true).build(),
                            contentDescription = "avatar",
                            modifier     = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text       = user.name.take(2).uppercase(),
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(user.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis)
                    Text(user.email,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (user.designation.isNotBlank())
                        Text(user.designation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f))
                }

                Spacer(Modifier.width(8.dp))
                RolePill(user.role, roleColor(user.role))
            }

            Spacer(Modifier.height(10.dp))

            // Role dropdown — single dropdown, no duplicate toggle
            ExposedDropdownMenuBox(
                expanded         = expanded && !isUpdating,
                onExpandedChange = { if (!isUpdating) expanded = it }
            ) {
                OutlinedTextField(
                    value         = "Change role…",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Select new role") },
                    trailingIcon  = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(arrowRot),
                            tint     = if (isUpdating)
                                MaterialTheme.colorScheme.onSurface.copy(0.38f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    },
                    enabled  = !isUpdating,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary)
                )

                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableRoles.forEach { role ->
                        val isCurrent = role == user.role
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RolePill(role, roleColor(role))
                                    if (isCurrent)
                                        Text("current",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                expanded = false
                                if (!isCurrent) onRoleSelected(role)
                            },
                            enabled     = !isCurrent,
                            trailingIcon = {
                                if (isCurrent)
                                    Icon(Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint     = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Role pill ─────────────────────────────────────────────────────────────────

@Composable
private fun RolePill(role: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text     = role,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun roleColor(role: String): Color = when (role) {
    "Administrator" -> Color(0xFFD32F2F)
    "Manager"       -> Color(0xFF1976D2)
    "HR"            -> Color(0xFF388E3C)
    "Team Lead"     -> Color(0xFFF57C00)
    "Employee"      -> Color(0xFF7B1FA2)
    "Intern"        -> Color(0xFF455A64)
    else            -> Color(0xFF616161)
}