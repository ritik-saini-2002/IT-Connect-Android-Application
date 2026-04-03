package com.example.ritik_2.administrator.rolemanagement

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ritik_2.data.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(
    viewModel    : RoleManagementViewModel,
    onRoleChanged: (String, String, String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var confirmDialog by remember { mutableStateOf<Triple<UserProfile, String, String>?>(null) }

    // Show success / error snackbar
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMsg) {
        state.successMsg?.let { snackState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackState.showSnackbar("⚠ $it"); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Role Management", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->

        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {

            Spacer(Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = viewModel::search,
                placeholder   = { Text("Search by name, role, designation…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredUsers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonSearch, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text("No users found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.filteredUsers, key = { it.id }) { user ->
                        UserRoleCard(
                            user           = user,
                            availableRoles = viewModel.availableRoles,
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

    // Confirm role change dialog
    confirmDialog?.let { (user, oldRole, newRole) ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            icon  = { Icon(Icons.Default.SwapHoriz, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Change Role?", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text("You are about to change:")
                    Spacer(Modifier.height(8.dp))
                    Text("👤  ${user.name}", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RolePill(oldRole, Color(0xFF607D8B))
                        Icon(Icons.Default.ArrowForward, null,
                            Modifier.padding(horizontal = 6.dp).size(16.dp))
                        RolePill(newRole, roleColor(newRole))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Permissions will be automatically updated for the new role.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRoleCard(
    user           : UserProfile,
    availableRoles : List<String>,
    onRoleSelected : (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.imageUrl.isNotBlank()) {
                        AsyncImage(
                            ImageRequest.Builder(LocalContext.current)
                                .data(user.imageUrl).crossfade(true).build(),
                            "avatar", Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            user.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(user.name, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (user.designation.isNotBlank())
                        Text(user.designation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                }

                RolePill(user.role, roleColor(user.role))
            }

            Spacer(Modifier.height(10.dp))

            // Role selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value         = "Change role…",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    shape         = RoundedCornerShape(10.dp),
                    label         = { Text("Select new role") },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableRoles.forEach { role ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RolePill(role, roleColor(role))
                                    if (role == user.role) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("(current)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                if (role != user.role) onRoleSelected(role)
                            },
                            enabled = role != user.role
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RolePill(role: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            role,
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
    "Team Leader"   -> Color(0xFFF57C00)
    "Employee"      -> Color(0xFF7B1FA2)
    "Intern"        -> Color(0xFF455A64)
    else            -> Color(0xFF616161)
}