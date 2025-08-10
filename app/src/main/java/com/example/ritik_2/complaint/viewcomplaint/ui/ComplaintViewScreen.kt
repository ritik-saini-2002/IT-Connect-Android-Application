package com.example.ritik_2.complaint.viewcomplaint.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.ritik_2.complaint.viewcomplaint.data.models.*
import com.example.ritik_2.complaint.viewcomplaint.ui.profile.UserProfileDialog

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
    userProfiles: Map<String, UserProfile>,
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
    onClearError: () -> Unit,
    onViewUserProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = screenWidth >= 600.dp

    // Responsive padding based on screen size
    val horizontalPadding = when {
        screenWidth >= 840.dp -> 24.dp // Large tablets/desktop
        screenWidth >= 600.dp -> 20.dp // Small tablets
        else -> 16.dp // Phones
    }

    val verticalSpacing = if (isTablet) 12.dp else 8.dp
    val cardElevation = if (isTablet) 6.dp else 4.dp

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var selectedComplaint by remember { mutableStateOf<ComplaintWithDetails?>(null) }
    var selectedUserProfile by remember { mutableStateOf<UserProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar with responsive design
        TopAppBar(
            title = {
                Text(
                    text = getViewModeTitle(currentViewMode),
                    fontSize = if (isTablet) 22.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                // View Mode Selector
                IconButton(onClick = { showViewModeDialog = true }) {
                    Icon(
                        Icons.Default.ViewList,
                        contentDescription = "View Mode",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Filter Button
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (currentFilterOption != null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Sort Button
                IconButton(onClick = { showSortDialog = true }) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Add Complaint Button
                IconButton(onClick = { onNavigateToActivity("RegisterComplain") }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Complaint",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Refresh Button
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = if (isRefreshing) Modifier.size(20.dp) else Modifier
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Progress Indicator with animation
        AnimatedVisibility(
            visible = isRefreshing || showRefreshAnimation,
            enter = fadeIn(animationSpec = spring()),
            exit = fadeOut(animationSpec = spring())
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (!isRefreshing && !showRefreshAnimation) {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Main Content with responsive layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(verticalSpacing))

            // Search Bar with responsive design
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search complaints...",
                isTablet = isTablet
            )

            Spacer(modifier = Modifier.height(verticalSpacing))

            // Statistics Card (if available) with responsive design
            complaintStats?.let { stats ->
                StatisticsCard(
                    stats = stats,
                    modifier = Modifier.fillMaxWidth(),
                    isTablet = isTablet,
                    elevation = cardElevation
                )
                Spacer(modifier = Modifier.height(verticalSpacing))
            }

            // Error Message with responsive design
            errorMessage?.let { error ->
                ErrorCard(
                    message = error,
                    onDismiss = onClearError,
                    modifier = Modifier.fillMaxWidth(),
                    isTablet = isTablet,
                    elevation = cardElevation
                )
                Spacer(modifier = Modifier.height(verticalSpacing))
            }

            // Filter and Sort Info with responsive design
            FilterSortInfo(
                currentFilter = currentFilterOption,
                currentSort = currentSortOption,
                onClearFilter = { onFilterOptionChange(null) },
                isTablet = isTablet
            )

            Spacer(modifier = Modifier.height(verticalSpacing))

            // Complaints List
            Box(modifier = Modifier.fillMaxSize()) {
                if (complaints.isEmpty() && !isRefreshing) {
                    EmptyStateView(
                        viewMode = currentViewMode,
                        onCreateComplaint = { onNavigateToActivity("RegisterComplain") },
                        isTablet = isTablet
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (isTablet) 24.dp else 16.dp)
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
                                userProfile = userProfiles[complaint.createdBy.userId],
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
                                },
                                onViewUserProfile = { userId ->
                                    val profile = userProfiles[userId]
                                    if (profile != null) {
                                        selectedUserProfile = profile
                                    } else {
                                        onViewUserProfile(userId)
                                    }
                                }
                            )
                        }

                        // Load More Button
                        if (hasMoreData && complaints.isNotEmpty()) {
                            item {
                                LoadMoreButton(
                                    onClick = onLoadMore,
                                    isLoading = isRefreshing,
                                    isTablet = isTablet
                                )
                            }
                        }
                    }
                }

                // Loading Indicator for initial load
                if (isRefreshing && complaints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(if (isTablet) 48.dp else 40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = if (isTablet) 5.dp else 4.dp
                            )
                            Text(
                                text = "Loading complaints...",
                                style = if (isTablet)
                                    MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // User Profile Dialog
    selectedUserProfile?.let { profile ->
        UserProfileDialog(
            userProfile = profile,
            onDismiss = { selectedUserProfile = null },
            onSendMessage = {
                selectedUserProfile = null
            },
            onViewAllComplaints = {
                onSearchQueryChange(profile.name)
                selectedUserProfile = null
            }
        )
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
            userProfiles = userProfiles,
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
            },
            onViewUserProfile = onViewUserProfile
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
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = if (isTablet) 16.sp else 14.sp
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = LocalTextStyle.current.copy(
            fontSize = if (isTablet) 16.sp else 14.sp
        )
    )
}

@Composable
private fun StatisticsCard(
    stats: ComplaintStats,
    modifier: Modifier = Modifier,
    isTablet: Boolean,
    elevation: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(if (isTablet) 16.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isTablet) 20.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Total",
                value = stats.totalComplaints.toString(),
                color = MaterialTheme.colorScheme.primary,
                isTablet = isTablet
            )
            StatItem(
                label = "Open",
                value = stats.openComplaints.toString(),
                color = Color(0xFF2196F3),
                isTablet = isTablet
            )
            StatItem(
                label = "In Progress",
                value = stats.inProgressComplaints.toString(),
                color = Color(0xFFFF9800),
                isTablet = isTablet
            )
            StatItem(
                label = "Closed",
                value = stats.closedComplaints.toString(),
                color = Color(0xFF4CAF50),
                isTablet = isTablet
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    isTablet: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = if (isTablet) 28.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = if (isTablet) 14.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean,
    elevation: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = RoundedCornerShape(if (isTablet) 16.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isTablet) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(if (isTablet) 28.dp else 24.dp)
            )
            Spacer(modifier = Modifier.width(if (isTablet) 12.dp else 8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                fontSize = if (isTablet) 16.sp else 14.sp
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterSortInfo(
    currentFilter: String?,
    currentSort: SortOption,
    onClearFilter: () -> Unit,
    isTablet: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
    ) {
        // Current Sort Chip
        AssistChip(
            onClick = { /* Sort dialog will be shown */ },
            label = {
                Text(
                    "Sort: ${getSortDisplayName(currentSort)}",
                    fontSize = if (isTablet) 14.sp else 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(if (isTablet) 18.dp else 16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )

        // Current Filter Chip
        currentFilter?.let { filter ->
            FilterChip(
                selected = true,
                onClick = onClearFilter,
                label = {
                    Text(
                        "Filter: $filter",
                        fontSize = if (isTablet) 14.sp else 12.sp
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear filter",
                        modifier = Modifier.size(if (isTablet) 18.dp else 16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    viewMode: ViewMode,
    onCreateComplaint: () -> Unit,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isTablet) 120.dp else 80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(if (isTablet) 64.dp else 48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(if (isTablet) 24.dp else 16.dp))

        Text(
            text = getEmptyStateMessage(viewMode),
            style = if (isTablet)
                MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 8.dp))

        Text(
            text = getEmptyStateSubMessage(viewMode),
            style = if (isTablet)
                MaterialTheme.typography.bodyLarge
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = if (isTablet) 48.dp else 32.dp)
        )

        Spacer(modifier = Modifier.height(if (isTablet) 32.dp else 24.dp))

        Button(
            onClick = onCreateComplaint,
            shape = RoundedCornerShape(if (isTablet) 16.dp else 12.dp),
            modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(if (isTablet) 20.dp else 18.dp)
            )
            Spacer(modifier = Modifier.width(if (isTablet) 10.dp else 8.dp))
            Text(
                "Create Complaint",
                fontSize = if (isTablet) 16.sp else 14.sp
            )
        }
    }
}

@Composable
private fun LoadMoreButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    isTablet: Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isTablet) 28.dp else 24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = if (isTablet) 4.dp else 3.dp
            )
        } else {
            TextButton(
                onClick = onClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Load More",
                    fontSize = if (isTablet) 16.sp else 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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
        ViewMode.PERSONAL -> "No Complaints Found"
        ViewMode.ASSIGNED_TO_ME -> "No Complaints Assigned"
        ViewMode.DEPARTMENT -> "No Department Complaints"
        ViewMode.ALL_COMPANY -> "No Company Complaints"
        ViewMode.GLOBAL -> "No Global Complaints"
    }
}

private fun getEmptyStateSubMessage(viewMode: ViewMode): String {
    return when (viewMode) {
        ViewMode.PERSONAL -> "You haven't created any complaints yet. Start by creating your first complaint."
        ViewMode.ASSIGNED_TO_ME -> "No complaints have been assigned to you at this time."
        ViewMode.DEPARTMENT -> "No complaints found in your department. Everything looks good!"
        ViewMode.ALL_COMPANY -> "No complaints found in your company. Great job everyone!"
        ViewMode.GLOBAL -> "No global complaints have been created yet."
    }
}