package com.example.ritik_2.complaint.viewcomplaint.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.complaint.viewcomplaint.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintViewScreen(
    complaints: List<ComplaintWithDetails>,
    isRefreshing: Boolean,
    showRefreshAnimation: Boolean,
    searchQuery: String,
    currentSortOption: SortOption,
    currentFilterOption: String?,
    hasMoreData: Boolean,
    currentUserData: UserData?,
    userPermissions: UserPermissions?,
    currentViewMode: ViewMode,
    availableEmployees: List<UserData>,
    complaintStats: ComplaintStats?,
    errorMessage: String?,
    onDeleteComplaint: (String) -> Unit,
    onUpdateComplaint: (String, ComplaintUpdates) -> Unit,
    onAssignComplaint: (String, String, String) -> Unit,
    onReopenComplaint: (String) -> Unit,
    onCloseComplaint: (String, String) -> Unit,
    onChangeStatus: (String, String, String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (SortOption) -> Unit,
    onFilterOptionChange: (String?) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onNavigateToActivity: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var selectedComplaint by remember { mutableStateOf<ComplaintWithDetails?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = getViewModeTitle(currentViewMode),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // View Mode Selector
                IconButton(onClick = { showViewModeDialog = true }) {
                    Icon(Icons.Default.ViewList, contentDescription = "View Mode")
                }

                // Filter Button
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (currentFilterOption != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Sort Button
                IconButton(onClick = { showSortDialog = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }

                // Add Complaint Button
                IconButton(onClick = { onNavigateToActivity("RegisterComplain") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Complaint")
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "Search complaints..."
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Statistics Card (if available)
        complaintStats?.let { stats ->
            StatisticsCard(
                stats = stats,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error Message
        errorMessage?.let { error ->
            ErrorCard(
                message = error,
                onDismiss = onClearError,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Filter and Sort Info
        FilterSortInfo(
            currentFilter = currentFilterOption,
            currentSort = currentSortOption,
            onClearFilter = { onFilterOptionChange(null) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Complaints List
        Box(modifier = Modifier.fillMaxSize()) {
            if (complaints.isEmpty() && !isRefreshing) {
                EmptyStateView(
                    viewMode = currentViewMode,
                    onCreateComplaint = { onNavigateToActivity("RegisterComplain") }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = complaints,
                        key = { complaint -> complaint.id }
                    ) { complaint ->
                        ComplaintCard(
                            complaint = complaint,
                            currentUser = currentUserData,
                            userPermissions = userPermissions,
                            availableEmployees = availableEmployees,
                            onEdit = { updates ->
                                onUpdateComplaint(complaint.id, updates)
                            },
                            onDelete = {
                                onDeleteComplaint(complaint.id)
                            },
                            onAssign = { assigneeId, assigneeName ->
                                onAssignComplaint(complaint.id, assigneeId, assigneeName)
                            },
                            onClose = { resolution ->
                                onCloseComplaint(complaint.id, resolution)
                            },
                            onReopen = {
                                onReopenComplaint(complaint.id)
                            },
                            onChangeStatus = { newStatus, reason ->
                                onChangeStatus(complaint.id, newStatus, reason)
                            },
                            onViewDetails = {
                                selectedComplaint = complaint
                            }
                        )
                    }

                    // Load More Button
                    if (hasMoreData && complaints.isNotEmpty()) {
                        item {
                            LoadMoreButton(
                                onClick = onLoadMore,
                                isLoading = isRefreshing
                            )
                        }
                    }
                }
            }

            // Refresh Animation Overlay
            if (showRefreshAnimation) {
                RefreshAnimation(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Loading Indicator
            if (isRefreshing && complaints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Dialogs
    if (showFilterDialog) {
        com.example.ritik_2.complaint.viewcomplaint.ui.components.FilterDialog(
            currentFilter = currentFilterOption,
            onFilterSelected = { filter ->
                onFilterOptionChange(filter)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    if (showSortDialog) {
        com.example.ritik_2.complaint.viewcomplaint.ui.components.SortDialog(
            currentSort = currentSortOption,
            onSortSelected = { sort ->
                onSortOptionChange(sort)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showViewModeDialog) {
        com.example.ritik_2.complaint.viewcomplaint.ui.components.ViewModeDialog(
            currentViewMode = currentViewMode,
            userRole = currentUserData?.role ?: "",
            onViewModeSelected = { viewMode ->
                onViewModeChange(viewMode)
                showViewModeDialog = false
            },
            onDismiss = { showViewModeDialog = false }
        )
    }

    // Complaint Details Dialog
    selectedComplaint?.let { complaint ->
        com.example.ritik_2.complaint.viewcomplaint.ui.components.ComplaintDetailsDialog(
            complaint = complaint,
            currentUser = currentUserData,
            userPermissions = userPermissions,
            availableEmployees = availableEmployees,
            onDismiss = { selectedComplaint = null },
            onEdit = { updates ->
                onUpdateComplaint(complaint.id, updates)
                selectedComplaint = null
            },
            onDelete = {
                onDeleteComplaint(complaint.id)
                selectedComplaint = null
            },
            onAssign = { assigneeId, assigneeName ->
                onAssignComplaint(complaint.id, assigneeId, assigneeName)
            },
            onClose = { resolution ->
                onCloseComplaint(complaint.id, resolution)
            },
            onReopen = {
                onReopenComplaint(complaint.id)
            },
            onChangeStatus = { newStatus, reason ->
                onChangeStatus(complaint.id, newStatus, reason)
            }
        )
    }
}

private fun getViewModeTitle(viewMode: ViewMode): String {
    return when (viewMode) {
        ViewMode.PERSONAL -> "My Complaints"
        ViewMode.ASSIGNED_TO_ME -> "Assigned to Me"
        ViewMode.DEPARTMENT -> "Department Complaints"
        ViewMode.ALL_COMPANY -> "All Company Complaints"
        ViewMode.GLOBAL -> "Global Complaints"
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun StatisticsCard(
    stats: ComplaintStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Total",
                value = stats.totalComplaints.toString(),
                color = MaterialTheme.colorScheme.primary
            )
            StatItem(
                label = "Open",
                value = stats.openComplaints.toString(),
                color = Color(0xFF2196F3)
            )
            StatItem(
                label = "In Progress",
                value = stats.inProgressComplaints.toString(),
                color = Color(0xFFFF9800)
            )
            StatItem(
                label = "Closed",
                value = stats.closedComplaints.toString(),
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun FilterSortInfo(
    currentFilter: String?,
    currentSort: SortOption,
    onClearFilter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Current Sort Chip
        AssistChip(
            onClick = { /* Sort dialog will be shown */ },
            label = {
                Text("Sort: ${getSortDisplayName(currentSort)}")
            },
            leadingIcon = {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        // Current Filter Chip
        currentFilter?.let { filter ->
            FilterChip(
                selected = true,
                onClick = onClearFilter,
                label = { Text("Filter: $filter") },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    viewMode: ViewMode,
    onCreateComplaint: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Assignment,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = getEmptyStateMessage(viewMode),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = getEmptyStateSubMessage(viewMode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreateComplaint
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Complaint")
        }
    }
}

@Composable
private fun LoadMoreButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onClick) {
                Text("Load More")
            }
        }
    }
}

@Composable
private fun RefreshAnimation(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Refreshing complaints...")
        }
    }
}

private fun getSortDisplayName(sortOption: SortOption): String {
    return when (sortOption) {
        SortOption.DATE_DESC -> "Newest First"
        SortOption.DATE_ASC -> "Oldest First"
        SortOption.URGENCY -> "By Urgency"
        SortOption.STATUS -> "By Status"
    }
}

private fun getEmptyStateMessage(viewMode: ViewMode): String {
    return when (viewMode) {
        ViewMode.PERSONAL -> "No complaints found"
        ViewMode.ASSIGNED_TO_ME -> "No complaints assigned"
        ViewMode.DEPARTMENT -> "No department complaints"
        ViewMode.ALL_COMPANY -> "No company complaints"
        ViewMode.GLOBAL -> "No global complaints"
    }
}

private fun getEmptyStateSubMessage(viewMode: ViewMode): String {
    return when (viewMode) {
        ViewMode.PERSONAL -> "You haven't created any complaints yet."
        ViewMode.ASSIGNED_TO_ME -> "No complaints have been assigned to you."
        ViewMode.DEPARTMENT -> "No complaints found in your department."
        ViewMode.ALL_COMPANY -> "No complaints found in your company."
        ViewMode.GLOBAL -> "No global complaints have been created."
    }
}