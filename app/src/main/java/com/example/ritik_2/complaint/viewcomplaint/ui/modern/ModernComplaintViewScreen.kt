//package com.example.ritik_2.complaint.viewcomplaint.ui.modern
//
//import androidx.compose.animation.*
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.BorderStroke
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.LazyRow
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.lazy.rememberLazyListState
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.text.BasicTextField
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material.icons.outlined.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.focus.FocusRequester
//import androidx.compose.ui.focus.focusRequester
//import androidx.compose.ui.focus.onFocusChanged
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.StrokeCap
//import androidx.compose.ui.graphics.graphicsLayer
//import androidx.compose.ui.graphics.vector.ImageVector
//import androidx.compose.ui.hapticfeedback.HapticFeedbackType
//import androidx.compose.ui.platform.LocalHapticFeedback
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.zIndex
//import com.example.ritik_2.complaint.viewcomplaint.data.models.*
//import kotlinx.coroutines.delay
//import androidx.compose.animation.*
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.lazy.LazyListState
//import androidx.compose.foundation.lazy.itemsIndexed
//import androidx.compose.runtime.*
//import kotlinx.coroutines.delay
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ModernComplaintViewScreen(
//    complaints: List<ComplaintWithDetails>,
//    isRefreshing: Boolean,
//    showRefreshAnimation: Boolean,
//    searchQuery: String,
//    currentSortOption: SortOption,
//    currentFilterOption: String?,
//    hasMoreData: Boolean,
//    currentUserData: UserData?,
//    userPermissions: UserPermissions?,
//    currentViewMode: ViewMode,
//    availableEmployees: List<UserData>,
//    complaintStats: ComplaintStats?,
//    errorMessage: String?,
//    userProfiles: Map<String, UserProfile>,
//    syncProgress: Float = 0f,
//    onDeleteComplaint: (String) -> Unit,
//    onUpdateComplaint: (String, ComplaintUpdates) -> Unit,
//    onAssignComplaint: (String, String, String) -> Unit,
//    onReopenComplaint: (String) -> Unit,
//    onCloseComplaint: (String, String) -> Unit,
//    onChangeStatus: (String, String, String) -> Unit,
//    onLoadMore: () -> Unit,
//    onRefresh: () -> Unit,
//    onSearchQueryChange: (String) -> Unit,
//    onSortOptionChange: (SortOption) -> Unit,
//    onFilterOptionChange: (String?) -> Unit,
//    onViewModeChange: (ViewMode) -> Unit,
//    onNavigateToActivity: (String) -> Unit,
//    onBackClick: () -> Unit,
//    onClearError: () -> Unit,
//    onViewUserProfile: (String) -> Unit
//) {
//    val haptic = LocalHapticFeedback.current
//    val listState = rememberLazyListState()
//    var showQuickActions by remember { mutableStateOf(false) }
//    var selectedComplaint by remember { mutableStateOf<ComplaintWithDetails?>(null) }
//    var showSyncIndicator by remember { mutableStateOf(false) }
//
//    // Animation states
//    val topBarAlpha by animateFloatAsState(
//        targetValue = if (listState.firstVisibleItemIndex > 0) 0.95f else 1f,
//        animationSpec = tween(300),
//        label = "topBarAlpha"
//    )
//
//    // Sync progress animation
//    LaunchedEffect(isRefreshing) {
//        showSyncIndicator = isRefreshing
//        if (!isRefreshing) {
//            delay(1000)
//            showSyncIndicator = false
//        }
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        // Background gradient
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(
//                    brush = Brush.verticalGradient(
//                        colors = listOf(
//                            MaterialTheme.colorScheme.surface,
//                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
//                        )
//                    )
//                )
//        )
//
//        Column(modifier = Modifier.fillMaxSize()) {
//            // Modern Top App Bar with glass morphism effect
//            ModernTopAppBar(
//                title = getViewModeTitle(currentViewMode),
//                alpha = topBarAlpha,
//                currentViewMode = currentViewMode,
//                currentFilterOption = currentFilterOption,
//                isRefreshing = isRefreshing,
//                onBackClick = onBackClick,
//                onFilterClick = { /* Show filter dialog */ },
//                onSortClick = { /* Show sort dialog */ },
//                onViewModeClick = { /* Show view mode dialog */ },
//                onRefresh = onRefresh,
//                onCreateComplaint = { onNavigateToActivity("RegisterComplain") }
//            )
//
//            // Sync progress indicator
//            AnimatedVisibility(
//                visible = showSyncIndicator,
//                enter = slideInVertically(
//                    initialOffsetY = { -it },
//                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
//                ) + fadeIn(),
//                exit = slideOutVertically(
//                    targetOffsetY = { -it },
//                    animationSpec = tween(300)
//                ) + fadeOut()
//            ) {
//                ModernSyncIndicator(
//                    progress = syncProgress,
//                    isRefreshing = isRefreshing
//                )
//            }
//
//            // Main content
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(horizontal = 16.dp)
//            ) {
//                LazyColumn(
//                    state = listState,
//                    verticalArrangement = Arrangement.spacedBy(12.dp),
//                    contentPadding = PaddingValues(vertical = 16.dp),
//                    modifier = Modifier.fillMaxSize()
//                ) {
//                    // Search bar
//                    item {
//                        ModernSearchBar(
//                            query = searchQuery,
//                            onQueryChange = onSearchQueryChange,
//                            placeholder = "Search complaints...",
//                            //modifier = Modifier.animateItemPlacement()
//                        )
//                    }
//
//                    // Statistics card
//                    complaintStats?.let { stats ->
//                        item {
//                            ModernStatsCard(
//                                stats = stats,
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    //.animateItemPlacement()
//                            )
//                        }
//                    }
//
//                    // Error card
//                    errorMessage?.let { error ->
//                        item {
//                            ModernErrorCard(
//                                message = error,
//                                onDismiss = onClearError,
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    //.animateItemPlacement()
//                            )
//                        }
//                    }
//
//                    // Filter and sort info
//                    item {
//                        ModernFilterSortInfo(
//                            currentFilter = currentFilterOption,
//                            currentSort = currentSortOption,
//                            onClearFilter = { onFilterOptionChange(null) },
//                            //modifier = Modifier.animateItemPlacement()
//                        )
//                    }
//
//                    // Complaints list
//                    if (complaints.isEmpty() && !isRefreshing) {
//                        item {
//                            ModernEmptyState(
//                                viewMode = currentViewMode,
//                                onCreateComplaint = { onNavigateToActivity("RegisterComplain") },
//                                //modifier = Modifier.animateItemPlacement()
//                            )
//                        }
//                    } else {
//                        items(
//                            items = complaints,
//                            key = { complaint -> complaint.id }
//                        ) { complaint ->
//                            ModernComplaintCard(
//                                complaint = complaint,
//                                currentUser = currentUserData,
//                                userPermissions = userPermissions,
//                                availableEmployees = availableEmployees,
//                                userProfile = userProfiles[complaint.createdBy.userId],
//                                isLoading = false,
//                                onEdit = { updates ->
//                                    onUpdateComplaint(complaint.id, updates)
//                                },
//                                onDelete = {
//                                    onDeleteComplaint(complaint.id)
//                                },
//                                onAssign = { assigneeId, assigneeName ->
//                                    onAssignComplaint(complaint.id, assigneeId, assigneeName)
//                                },
//                                onClose = { resolution ->
//                                    onCloseComplaint(complaint.id, resolution)
//                                },
//                                onReopen = {
//                                    onReopenComplaint(complaint.id)
//                                },
//                                onChangeStatus = { newStatus, reason ->
//                                    onChangeStatus(complaint.id, newStatus, reason)
//                                },
//                                onViewDetails = {
//                                    selectedComplaint = complaint
//                                },
//                                onViewUserProfile = onViewUserProfile,
//                                modifier = Modifier.animateItem(
//                                    fadeInSpec = tween(300),
//                                    fadeOutSpec = tween(300),
//                                    placementSpec = spring(
//                                        dampingRatio = Spring.DampingRatioMediumBouncy,
//                                        stiffness = Spring.StiffnessLow
//                                    )
//                                )
//                            )
//                        }
//
//                        // Load more button
//                        if (hasMoreData && complaints.isNotEmpty()) {
//                            item {
//                                ModernLoadMoreButton(
//                                    onClick = onLoadMore,
//                                    isLoading = isRefreshing,
//                                    //modifier = Modifier.AnimatedLazyColumn()
//                                )
//                            }
//                        }
//                    }
//                }
//
//                // Loading skeleton
//                if (isRefreshing && complaints.isEmpty()) {
//                    ModernLoadingSkeleton(
//                        modifier = Modifier.fillMaxSize()
//                    )
//                }
//            }
//        }
//
//        // Floating Quick Actions
//        AnimatedVisibility(
//            visible = showQuickActions && !isRefreshing,
//            enter = slideInVertically(
//                initialOffsetY = { it },
//                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
//            ) + fadeIn(),
//            exit = slideOutVertically(
//                targetOffsetY = { it },
//                animationSpec = tween(300)
//            ) + fadeOut(),
//            modifier = Modifier.align(Alignment.BottomEnd)
//        ) {
//            ModernQuickActionFab(
//                onCreateComplaint = {
//                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
//                    onNavigateToActivity("RegisterComplain")
//                },
//                onRefresh = onRefresh,
//                modifier = Modifier.padding(16.dp)
//            )
//        }
//    }
//
//    // Complaint details dialog
//    selectedComplaint?.let { complaint ->
//        ModernComplaintDetailsDialog(
//            complaint = complaint,
//            currentUser = currentUserData,
//            userPermissions = userPermissions,
//            availableEmployees = availableEmployees,
//            userProfiles = userProfiles,
//            onDismiss = { selectedComplaint = null },
//            onEdit = { updates ->
//                onUpdateComplaint(complaint.id, updates)
//                selectedComplaint = null
//            },
//            onDelete = {
//                onDeleteComplaint(complaint.id)
//                selectedComplaint = null
//            },
//            onAssign = { assigneeId, assigneeName ->
//                onAssignComplaint(complaint.id, assigneeId, assigneeName)
//            },
//            onClose = { resolution ->
//                onCloseComplaint(complaint.id, resolution)
//            },
//            onReopen = {
//                onReopenComplaint(complaint.id)
//            },
//            onChangeStatus = { newStatus, reason ->
//                onChangeStatus(complaint.id, newStatus, reason)
//            },
//            onViewUserProfile = onViewUserProfile
//        )
//    }
//
//    // Auto-show quick actions when scrolled down
//    LaunchedEffect(listState) {
//        snapshotFlow { listState.firstVisibleItemIndex > 2 }
//            .collect { shouldShow ->
//                showQuickActions = shouldShow
//            }
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun ModernTopAppBar(
//    title: String,
//    alpha: Float,
//    currentViewMode: ViewMode,
//    currentFilterOption: String?,
//    isRefreshing: Boolean,
//    onBackClick: () -> Unit,
//    onFilterClick: () -> Unit,
//    onSortClick: () -> Unit,
//    onViewModeClick: () -> Unit,
//    onRefresh: () -> Unit,
//    onCreateComplaint: () -> Unit
//) {
//    Surface(
//        modifier = Modifier
//            .fillMaxWidth()
//            .background(
//                color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
//                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
//            )
//            .zIndex(10f),
//        tonalElevation = 6.dp,
//        shadowElevation = if (alpha < 1f) 8.dp else 0.dp
//    ) {
//        Column {
//            // Top app bar
//            TopAppBar(
//                title = {
//                    Column {
//                        Text(
//                            text = title,
//                            fontSize = 20.sp,
//                            fontWeight = FontWeight.Bold
//                        )
//                        Text(
//                            text = getViewModeSubtitle(currentViewMode),
//                            style = MaterialTheme.typography.labelMedium,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//                },
//                navigationIcon = {
//                    FilledTonalIconButton(
//                        onClick = onBackClick,
//                        modifier = Modifier.size(40.dp)
//                    ) {
//                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
//                    }
//                },
//                actions = {
//                    // View mode button
//                    FilledTonalIconButton(
//                        onClick = onViewModeClick,
//                        modifier = Modifier.size(40.dp)
//                    ) {
//                        Icon(Icons.Default.ViewList, contentDescription = "View Mode")
//                    }
//
//                    // Filter button with indicator
//                    Box {
//                        FilledTonalIconButton(
//                            onClick = onFilterClick,
//                            modifier = Modifier.size(40.dp),
//                            colors = IconButtonDefaults.filledTonalIconButtonColors(
//                                containerColor = if (currentFilterOption != null)
//                                    MaterialTheme.colorScheme.primaryContainer
//                                else MaterialTheme.colorScheme.surfaceVariant
//                            )
//                        ) {
//                            Icon(
//                                Icons.Default.FilterList,
//                                contentDescription = "Filter",
//                                tint = if (currentFilterOption != null)
//                                    MaterialTheme.colorScheme.primary
//                                else MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//
//                        // Active filter indicator
//                        if (currentFilterOption != null) {
//                            Surface(
//                                modifier = Modifier
//                                    .size(8.dp)
//                                    .offset(x = 2.dp, y = 2.dp)
//                                    .align(Alignment.TopEnd),
//                                shape = CircleShape,
//                                color = MaterialTheme.colorScheme.primary
//                            ) {}
//                        }
//                    }
//
//                    // Sort button
//                    FilledTonalIconButton(
//                        onClick = onSortClick,
//                        modifier = Modifier.size(40.dp)
//                    ) {
//                        Icon(Icons.Default.Sort, contentDescription = "Sort")
//                    }
//
//                    // Add complaint button with animation
//                    val infiniteTransition = rememberInfiniteTransition(label = "addButton")
//                    val addButtonScale by infiniteTransition.animateFloat(
//                        initialValue = 1f,
//                        targetValue = 1.1f,
//                        animationSpec = infiniteRepeatable(
//                            animation = tween(2000, easing = FastOutSlowInEasing),
//                            repeatMode = RepeatMode.Reverse
//                        ),
//                        label = "addScale"
//                    )
//
//                    FilledIconButton(
//                        onClick = onCreateComplaint,
//                        modifier = Modifier
//                            .size(40.dp)
//                            .graphicsLayer {
//                                scaleX = addButtonScale
//                                scaleY = addButtonScale
//                            },
//                        colors = IconButtonDefaults.filledIconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.primary
//                        )
//                    ) {
//                        Icon(
//                            Icons.Default.Add,
//                            contentDescription = "Add Complaint",
//                            tint = MaterialTheme.colorScheme.onPrimary
//                        )
//                    }
//
//                    // Refresh button with loading animation
//                    FilledTonalIconButton(
//                        onClick = onRefresh,
//                        modifier = Modifier
//                            .size(40.dp)
//                            .graphicsLayer {
//                                rotationZ = if (isRefreshing)
//                                    (System.currentTimeMillis() / 10) % 360f
//                                else 0f
//                            }
//                    ) {
//                        Icon(
//                            Icons.Default.Refresh,
//                            contentDescription = "Refresh"
//                        )
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = Color.Transparent
//                )
//            )
//        }
//    }
//}
//
//@Composable
//private fun ModernSyncIndicator(
//    progress: Float,
//    isRefreshing: Boolean
//) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 8.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.primaryContainer
//        ),
//        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//    ) {
//        Row(
//            modifier = Modifier.padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            if (isRefreshing) {
//                Box(modifier = Modifier.size(24.dp)) {
//                    CircularProgressIndicator(
//                        progress = { progress },
//                        modifier = Modifier.fillMaxSize(),
//                        color = MaterialTheme.colorScheme.primary,
//                        strokeWidth = 3.dp,
//                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
//                    )
//                }
//            } else {
//                Icon(
//                    Icons.Default.CloudDone,
//                    contentDescription = null,
//                    modifier = Modifier.size(24.dp),
//                    tint = MaterialTheme.colorScheme.primary
//                )
//            }
//
//            Column(modifier = Modifier.weight(1f)) {
//                Text(
//                    text = if (isRefreshing) "Synchronizing..." else "Sync Complete",
//                    style = MaterialTheme.typography.titleSmall,
//                    fontWeight = FontWeight.Medium,
//                    color = MaterialTheme.colorScheme.onPrimaryContainer
//                )
//
//                if (isRefreshing) {
//                    Text(
//                        text = "Updating complaint data...",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
//                    )
//                }
//            }
//
//            if (progress > 0 && isRefreshing) {
//                Surface(
//                    shape = RoundedCornerShape(12.dp),
//                    color = MaterialTheme.colorScheme.primary
//                ) {
//                    Text(
//                        text = "${(progress * 100).toInt()}%",
//                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
//                        style = MaterialTheme.typography.labelSmall,
//                        color = MaterialTheme.colorScheme.onPrimary,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernSearchBar(
//    query: String,
//    onQueryChange: (String) -> Unit,
//    placeholder: String,
//    modifier: Modifier = Modifier
//) {
//    val focusRequester = remember { FocusRequester() }
//    var isFocused by remember { mutableStateOf(false) }
//
//    val animatedElevation by animateDpAsState(
//        targetValue = if (isFocused) 8.dp else 2.dp,
//        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
//        label = "searchElevation"
//    )
//
//    Card(
//        modifier = modifier.fillMaxWidth(),
//        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surface
//        ),
//        shape = RoundedCornerShape(16.dp)
//    ) {
//        Row(
//            modifier = Modifier.padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            Icon(
//                Icons.Default.Search,
//                contentDescription = "Search",
//                tint = if (isFocused) MaterialTheme.colorScheme.primary
//                else MaterialTheme.colorScheme.onSurfaceVariant
//            )
//
//            BasicTextField(
//                value = query,
//                onValueChange = onQueryChange,
//                modifier = Modifier
//                    .weight(1f)
//                    .focusRequester(focusRequester)
//                    .onFocusChanged { isFocused = it.isFocused },
//                textStyle = MaterialTheme.typography.bodyLarge.copy(
//                    color = MaterialTheme.colorScheme.onSurface
//                ),
//                singleLine = true,
//                decorationBox = { innerTextField ->
//                    if (query.isEmpty()) {
//                        Text(
//                            text = placeholder,
//                            style = MaterialTheme.typography.bodyLarge,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//                    innerTextField()
//                }
//            )
//
//            AnimatedVisibility(
//                visible = query.isNotEmpty(),
//                enter = scaleIn() + fadeIn(),
//                exit = scaleOut() + fadeOut()
//            ) {
//                IconButton(
//                    onClick = { onQueryChange("") },
//                    modifier = Modifier.size(24.dp)
//                ) {
//                    Icon(
//                        Icons.Default.Clear,
//                        contentDescription = "Clear search",
//                        modifier = Modifier.size(18.dp)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernStatsCard(
//    stats: ComplaintStats,
//    modifier: Modifier = Modifier
//) {
//    val animatedValues = listOf(
//        animateFloatAsState(
//            targetValue = stats.totalComplaints.toFloat(),
//            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
//            label = "total"
//        ).value,
//        animateFloatAsState(
//            targetValue = stats.openComplaints.toFloat(),
//            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy/*, delayMillis = 100*/),
//            label = "open"
//        ).value,
//        animateFloatAsState(
//            targetValue = stats.inProgressComplaints.toFloat(),
//            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy/*, delayMillis = 200*/),
//            label = "progress"
//        ).value,
//        animateFloatAsState(
//            targetValue = stats.closedComplaints.toFloat(),
//            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy/*, delayMillis = 300*/),
//            label = "closed"
//        ).value
//    )
//
//    Card(
//        modifier = modifier,
//        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceVariant
//        ),
//        shape = RoundedCornerShape(20.dp)
//    ) {
//        Column(
//            modifier = Modifier.padding(20.dp),
//            verticalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                Icon(
//                    Icons.Default.Analytics,
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.primary
//                )
//                Text(
//                    text = "Complaint Statistics",
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Bold
//                )
//            }
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceEvenly
//            ) {
//                ModernStatItem(
//                    label = "Total",
//                    value = animatedValues[0].toInt(),
//                    color = MaterialTheme.colorScheme.primary,
//                    icon = Icons.Default.Assignment
//                )
//                ModernStatItem(
//                    label = "Open",
//                    value = animatedValues[1].toInt(),
//                    color = Color(0xFF2196F3),
//                    icon = Icons.Default.FiberNew
//                )
//                ModernStatItem(
//                    label = "Active",
//                    value = animatedValues[2].toInt(),
//                    color = Color(0xFFFF9800),
//                    icon = Icons.Default.Pending
//                )
//                ModernStatItem(
//                    label = "Closed",
//                    value = animatedValues[3].toInt(),
//                    color = Color(0xFF4CAF50),
//                    icon = Icons.Default.CheckCircle
//                )
//            }
//
//            // Progress visualization
//            LinearProgressIndicator(
//                progress = { if (stats.totalComplaints > 0) stats.closedComplaints.toFloat() / stats.totalComplaints else 0f },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(8.dp)
//                    .clip(RoundedCornerShape(4.dp)),
//                color = Color(0xFF4CAF50),
//                trackColor = Color(0xFF2196F3).copy(alpha = 0.3f),
//                strokeCap = StrokeCap.Round
//            )
//
//            Text(
//                text = "${if (stats.totalComplaints > 0) ((stats.closedComplaints.toFloat() / stats.totalComplaints) * 100).toInt() else 0}% Resolution Rate",
//                style = MaterialTheme.typography.labelMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                modifier = Modifier.align(Alignment.CenterHorizontally)
//            )
//        }
//    }
//}
//
//@Composable
//private fun ModernStatItem(
//    label: String,
//    value: Int,
//    color: Color,
//    icon: ImageVector
//) {
//    Column(
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        Surface(
//            shape = CircleShape,
//            color = color.copy(alpha = 0.1f),
//            modifier = Modifier.size(48.dp)
//        ) {
//            Box(
//                contentAlignment = Alignment.Center,
//                modifier = Modifier.fillMaxSize()
//            ) {
//                Icon(
//                    imageVector = icon,
//                    contentDescription = null,
//                    tint = color,
//                    modifier = Modifier.size(24.dp)
//                )
//            }
//        }
//
//        Text(
//            text = value.toString(),
//            fontSize = 20.sp,
//            fontWeight = FontWeight.Bold,
//            color = color
//        )
//
//        Text(
//            text = label,
//            fontSize = 12.sp,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//            fontWeight = FontWeight.Medium
//        )
//    }
//}
//
//@Composable
//private fun ModernErrorCard(
//    message: String,
//    onDismiss: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var isVisible by remember { mutableStateOf(true) }
//
//    AnimatedVisibility(
//        visible = isVisible,
//        enter = slideInVertically(
//            initialOffsetY = { -it },
//            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
//        ) + fadeIn(),
//        exit = slideOutVertically(
//            targetOffsetY = { -it },
//            animationSpec = tween(300)
//        ) + fadeOut()
//    ) {
//        Card(
//            modifier = modifier,
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.errorContainer
//            ),
//            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
//            shape = RoundedCornerShape(12.dp)
//        ) {
//            Row(
//                modifier = Modifier.padding(16.dp),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                Surface(
//                    shape = CircleShape,
//                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
//                    modifier = Modifier.size(40.dp)
//                ) {
//                    Box(
//                        contentAlignment = Alignment.Center,
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        Icon(
//                            Icons.Default.Error,
//                            contentDescription = "Error",
//                            tint = MaterialTheme.colorScheme.error,
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
//                }
//
//                Text(
//                    text = message,
//                    color = MaterialTheme.colorScheme.onErrorContainer,
//                    modifier = Modifier.weight(1f),
//                    style = MaterialTheme.typography.bodyMedium
//                )
//
//                FilledTonalIconButton(
//                    onClick = {
//                        isVisible = false
//                        onDismiss()
//                    },
//                    modifier = Modifier.size(32.dp),
//                    colors = IconButtonDefaults.filledTonalIconButtonColors(
//                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
//                    )
//                ) {
//                    Icon(
//                        Icons.Default.Close,
//                        contentDescription = "Dismiss",
//                        tint = MaterialTheme.colorScheme.error,
//                        modifier = Modifier.size(16.dp)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernFilterSortInfo(
//    currentFilter: String?,
//    currentSort: SortOption,
//    onClearFilter: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    LazyRow(
//        modifier = modifier.fillMaxWidth(),
//        horizontalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        // Sort chip
//        item {
//            Surface(
//                shape = RoundedCornerShape(20.dp),
//                color = MaterialTheme.colorScheme.secondaryContainer,
//                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
//            ) {
//                Row(
//                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(6.dp)
//                ) {
//                    Icon(
//                        Icons.Default.Sort,
//                        contentDescription = null,
//                        modifier = Modifier.size(16.dp),
//                        tint = MaterialTheme.colorScheme.onSecondaryContainer
//                    )
//                    Text(
//                        text = getSortDisplayName(currentSort),
//                        style = MaterialTheme.typography.labelMedium,
//                        color = MaterialTheme.colorScheme.onSecondaryContainer,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
//        }
//
//        // Filter chip
//        currentFilter?.let { filter ->
//            item {
//                Surface(
//                    shape = RoundedCornerShape(20.dp),
//                    color = MaterialTheme.colorScheme.primaryContainer,
//                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
//                ) {
//                    Row(
//                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(6.dp)
//                    ) {
//                        Icon(
//                            Icons.Default.FilterList,
//                            contentDescription = null,
//                            modifier = Modifier.size(16.dp),
//                            tint = MaterialTheme.colorScheme.primary
//                        )
//                        Text(
//                            text = filter,
//                            style = MaterialTheme.typography.labelMedium,
//                            color = MaterialTheme.colorScheme.primary,
//                            fontWeight = FontWeight.Medium
//                        )
//                        IconButton(
//                            onClick = onClearFilter,
//                            modifier = Modifier.size(20.dp)
//                        ) {
//                            Icon(
//                                Icons.Default.Close,
//                                contentDescription = "Clear filter",
//                                modifier = Modifier.size(14.dp),
//                                tint = MaterialTheme.colorScheme.primary
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernEmptyState(
//    viewMode: ViewMode,
//    onCreateComplaint: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    val infiniteTransition = rememberInfiniteTransition(label = "emptyState")
//    val floatingOffset by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 20f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(3000, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "floating"
//    )
//
//    Column(
//        modifier = modifier
//            .fillMaxSize()
//            .padding(32.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        // Animated illustration
//        Surface(
//            shape = CircleShape,
//            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
//            modifier = Modifier
//                .size(120.dp)
//                .offset(y = floatingOffset.dp)
//        ) {
//            Box(
//                contentAlignment = Alignment.Center,
//                modifier = Modifier.fillMaxSize()
//            ) {
//                Icon(
//                    Icons.Default.Assignment,
//                    contentDescription = null,
//                    modifier = Modifier.size(60.dp),
//                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        Text(
//            text = getEmptyStateMessage(viewMode),
//            style = MaterialTheme.typography.headlineSmall,
//            color = MaterialTheme.colorScheme.onSurface,
//            fontWeight = FontWeight.Bold,
//            textAlign = TextAlign.Center
//        )
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        Text(
//            text = getEmptyStateSubMessage(viewMode),
//            style = MaterialTheme.typography.bodyLarge,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//            textAlign = TextAlign.Center,
//            lineHeight = 24.sp
//        )
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        Button(
//            onClick = onCreateComplaint,
//            modifier = Modifier
//                .fillMaxWidth(0.7f)
//                .height(56.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.primary
//            ),
//            shape = RoundedCornerShape(16.dp),
//            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
//        ) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                Icon(
//                    Icons.Default.Add,
//                    contentDescription = null,
//                    modifier = Modifier.size(24.dp)
//                )
//                Text(
//                    text = "Create Complaint",
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Medium
//                )
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernLoadMoreButton(
//    onClick: () -> Unit,
//    isLoading: Boolean,
//    modifier: Modifier = Modifier
//) {
//    Surface(
//        modifier = modifier
//            .fillMaxWidth()
//            .clickable { onClick() },
//        shape = RoundedCornerShape(16.dp),
//        color = MaterialTheme.colorScheme.surfaceVariant,
//        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
//    ) {
//        Box(
//            contentAlignment = Alignment.Center,
//            modifier = Modifier.padding(24.dp)
//        ) {
//            if (isLoading) {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    CircularProgressIndicator(
//                        modifier = Modifier.size(20.dp),
//                        strokeWidth = 2.dp
//                    )
//                    Text(
//                        text = "Loading more...",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                }
//            } else {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Icon(
//                        Icons.Default.ExpandMore,
//                        contentDescription = null,
//                        modifier = Modifier.size(20.dp),
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                    Text(
//                        text = "Load More Complaints",
//                        style = MaterialTheme.typography.titleSmall,
//                        color = MaterialTheme.colorScheme.primary,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernLoadingSkeleton(
//    modifier: Modifier = Modifier
//) {
//    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
//    val alpha by infiniteTransition.animateFloat(
//        initialValue = 0.3f,
//        targetValue = 0.7f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(1000, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "skeletonAlpha"
//    )
//
//    LazyColumn(
//        modifier = modifier,
//        verticalArrangement = Arrangement.spacedBy(12.dp),
//        contentPadding = PaddingValues(16.dp)
//    ) {
//        items(6) { index ->
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(180.dp),
//                shape = RoundedCornerShape(16.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
//                )
//            ) {
//                Column(
//                    modifier = Modifier.padding(20.dp),
//                    verticalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    Row(
//                        horizontalArrangement = Arrangement.spacedBy(12.dp)
//                    ) {
//                        Surface(
//                            shape = CircleShape,
//                            color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
//                            modifier = Modifier.size(48.dp)
//                        ) {}
//
//                        Column(
//                            modifier = Modifier.weight(1f),
//                            verticalArrangement = Arrangement.spacedBy(6.dp)
//                        ) {
//                            Surface(
//                                shape = RoundedCornerShape(4.dp),
//                                color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
//                                modifier = Modifier
//                                    .fillMaxWidth(0.8f)
//                                    .height(16.dp)
//                            ) {}
//                            Surface(
//                                shape = RoundedCornerShape(4.dp),
//                                color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
//                                modifier = Modifier
//                                    .fillMaxWidth(0.6f)
//                                    .height(12.dp)
//                            ) {}
//                        }
//                    }
//
//                    LazyRow(
//                        horizontalArrangement = Arrangement.spacedBy(8.dp)
//                    ) {
//                        items(3) {
//                            Surface(
//                                shape = RoundedCornerShape(12.dp),
//                                color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
//                                modifier = Modifier
//                                    .width(60.dp)
//                                    .height(24.dp)
//                            ) {}
//                        }
//                    }
//
//                    repeat(2) {
//                        Surface(
//                            shape = RoundedCornerShape(4.dp),
//                            color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .height(12.dp)
//                        ) {}
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun ModernQuickActionFab(
//    onCreateComplaint: () -> Unit,
//    onRefresh: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var isExpanded by remember { mutableStateOf(false) }
//
//    Column(
//        modifier = modifier,
//        horizontalAlignment = Alignment.End,
//        verticalArrangement = Arrangement.spacedBy(12.dp)
//    ) {
//        // Secondary FABs
//        AnimatedVisibility(
//            visible = isExpanded,
//            enter = slideInVertically(
//                initialOffsetY = { it / 2 },
//                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
//            ) + fadeIn(),
//            exit = slideOutVertically(
//                targetOffsetY = { it / 2 },
//                animationSpec = tween(200)
//            ) + fadeOut()
//        ) {
//            Column(
//                verticalArrangement = Arrangement.spacedBy(12.dp),
//                horizontalAlignment = Alignment.End
//            ) {
//                // Refresh FAB
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Surface(
//                        shape = RoundedCornerShape(8.dp),
//                        color = MaterialTheme.colorScheme.surface,
//                        shadowElevation = 4.dp
//                    ) {
//                        Text(
//                            text = "Refresh",
//                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
//                            style = MaterialTheme.typography.labelMedium,
//                            color = MaterialTheme.colorScheme.onSurface
//                        )
//                    }
//
//                    SmallFloatingActionButton(
//                        onClick = onRefresh,
//                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
//                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
//                    ) {
//                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
//                    }
//                }
//
//                // Add complaint FAB
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Surface(
//                        shape = RoundedCornerShape(8.dp),
//                        color = MaterialTheme.colorScheme.surface,
//                        shadowElevation = 4.dp
//                    ) {
//                        Text(
//                            text = "New Complaint",
//                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
//                            style = MaterialTheme.typography.labelMedium,
//                            color = MaterialTheme.colorScheme.onSurface
//                        )
//                    }
//
//                    SmallFloatingActionButton(
//                        onClick = onCreateComplaint,
//                        containerColor = MaterialTheme.colorScheme.primaryContainer,
//                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
//                    ) {
//                        Icon(Icons.Default.Add, contentDescription = "Add Complaint")
//                    }
//                }
//            }
//        }
//
//        // Main FAB
//        FloatingActionButton(
//            onClick = { isExpanded = !isExpanded },
//            containerColor = MaterialTheme.colorScheme.primary,
//            contentColor = MaterialTheme.colorScheme.onPrimary,
//            elevation = FloatingActionButtonDefaults.elevation(
//                defaultElevation = 6.dp,
//                pressedElevation = 12.dp
//            )
//        ) {
//            Icon(
//                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
//                contentDescription = if (isExpanded) "Close menu" else "Open menu",
//                modifier = Modifier.graphicsLayer {
//                    rotationZ = if (isExpanded) 45f else 0f
//                }
//            )
//        }
//    }
//}
//
//@Composable
//fun AnimatedLazyColumn(
//    complaints: List<ComplaintWithDetails>,
//    listState: LazyListState,
//    isRefreshing: Boolean,
//    searchQuery: String,
//    currentSortOption: SortOption,
//    currentFilterOption: String?,
//    hasMoreData: Boolean,
//    currentUserData: UserData?,
//    userPermissions: UserPermissions?,
//    availableEmployees: List<UserData>,
//    complaintStats: ComplaintStats?,
//    errorMessage: String?,
//    userProfiles: Map<String, UserProfile>,
//    onDeleteComplaint: (String) -> Unit,
//    onUpdateComplaint: (String, ComplaintUpdates) -> Unit,
//    onAssignComplaint: (String, String, String) -> Unit,
//    onReopenComplaint: (String) -> Unit,
//    onCloseComplaint: (String, String) -> Unit,
//    onChangeStatus: (String, String, String) -> Unit,
//    onLoadMore: () -> Unit,
//    onSearchQueryChange: (String) -> Unit,
//    onFilterOptionChange: (String?) -> Unit,
//    onNavigateToActivity: (String) -> Unit,
//    onClearError: () -> Unit,
//    onViewUserProfile: (String) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    val animatedComplaints by remember(complaints) {
//        derivedStateOf { complaints }
//    }
//
//    var selectedComplaint by remember { mutableStateOf<ComplaintWithDetails?>(null) }
//
//    LazyColumn(
//        state = listState,
//        verticalArrangement = Arrangement.spacedBy(12.dp),
//        contentPadding = PaddingValues(vertical = 16.dp),
//        modifier = modifier.fillMaxSize()
//    ) {
//        // Search bar
//        item(key = "search_bar") {
//            var isSearchVisible by remember { mutableStateOf(false) }
//
//            LaunchedEffect(Unit) {
//                delay(100)
//                isSearchVisible = true
//            }
//
//            AnimatedVisibility(
//                visible = isSearchVisible,
//                enter = slideInVertically(
//                    initialOffsetY = { -it },
//                    animationSpec = spring(
//                        dampingRatio = Spring.DampingRatioMediumBouncy,
//                        stiffness = Spring.StiffnessMedium
//                    )
//                ) + fadeIn(
//                    animationSpec = tween(400)
//                )
//            ) {
//                ModernSearchBar(
//                    query = searchQuery,
//                    onQueryChange = onSearchQueryChange,
//                    placeholder = "Search complaints...",
//                    modifier = Modifier.fillMaxWidth()
//                )
//            }
//        }
//
//        // Statistics card
//        complaintStats?.let { stats ->
//            item(key = "stats_card") {
//                var isStatsVisible by remember { mutableStateOf(false) }
//
//                LaunchedEffect(Unit) {
//                    delay(200)
//                    isStatsVisible = true
//                }
//
//                AnimatedVisibility(
//                    visible = isStatsVisible,
//                    enter = slideInVertically(
//                        initialOffsetY = { -it / 2 },
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                            stiffness = Spring.StiffnessMedium
//                        )
//                    ) + fadeIn(
//                        animationSpec = tween(400, delayMillis = 100)
//                    ) + scaleIn(
//                        initialScale = 0.9f,
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy
//                        )
//                    )
//                ) {
//                    ModernStatsCard(
//                        stats = stats,
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//        }
//
//        // Error card
//        errorMessage?.let { error ->
//            item(key = "error_card") {
//                AnimatedVisibility(
//                    visible = true,
//                    enter = slideInVertically(
//                        initialOffsetY = { -it },
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy
//                        )
//                    ) + fadeIn() + scaleIn(initialScale = 0.95f),
//                    exit = slideOutVertically(
//                        targetOffsetY = { -it },
//                        animationSpec = tween(300)
//                    ) + fadeOut() + scaleOut(targetScale = 0.95f)
//                ) {
//                    ModernErrorCard(
//                        message = error,
//                        onDismiss = onClearError,
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//        }
//
//        // Filter and sort info
//        item(key = "filter_sort_info") {
//            var isFilterVisible by remember { mutableStateOf(false) }
//
//            LaunchedEffect(Unit) {
//                delay(300)
//                isFilterVisible = true
//            }
//
//            AnimatedVisibility(
//                visible = isFilterVisible,
//                enter = slideInHorizontally(
//                    initialOffsetX = { -it / 2 },
//                    animationSpec = spring(
//                        dampingRatio = Spring.DampingRatioMediumBouncy
//                    )
//                ) + fadeIn(
//                    animationSpec = tween(400, delayMillis = 150)
//                )
//            ) {
//                ModernFilterSortInfo(
//                    currentFilter = currentFilterOption,
//                    currentSort = currentSortOption,
//                    onClearFilter = { onFilterOptionChange(null) }
//                )
//            }
//        }
//
//        // Complaints list with staggered animation
//        if (animatedComplaints.isEmpty() && !isRefreshing) {
//            item(key = "empty_state") {
//                var isEmptyVisible by remember { mutableStateOf(false) }
//
//                LaunchedEffect(Unit) {
//                    delay(500)
//                    isEmptyVisible = true
//                }
//
//                AnimatedVisibility(
//                    visible = isEmptyVisible,
//                    enter = slideInVertically(
//                        initialOffsetY = { it / 3 },
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioLowBouncy,
//                            stiffness = Spring.StiffnessLow
//                        )
//                    ) + fadeIn(
//                        animationSpec = tween(600)
//                    ) + scaleIn(
//                        initialScale = 0.8f,
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy
//                        )
//                    )
//                ) {
//                    ModernEmptyState(
//                        viewMode = ViewMode.PERSONAL, // You might want to pass this as parameter
//                        onCreateComplaint = { onNavigateToActivity("RegisterComplain") }
//                    )
//                }
//            }
//        } else {
//            itemsIndexed(
//                items = animatedComplaints,
//                key = { _, complaint -> complaint.id }
//            ) { index, complaint ->
//                var isVisible by remember { mutableStateOf(false) }
//
//                LaunchedEffect(complaint.id) {
//                    delay(index * 80L) // Staggered animation with 80ms delay between items
//                    isVisible = true
//                }
//
//                AnimatedVisibility(
//                    visible = isVisible,
//                    enter = slideInVertically(
//                        initialOffsetY = { it / 2 },
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                            stiffness = Spring.StiffnessLow
//                        )
//                    ) + fadeIn(
//                        animationSpec = tween(
//                            durationMillis = 400,
//                            delayMillis = index * 50
//                        )
//                    ) + scaleIn(
//                        initialScale = 0.9f,
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                            stiffness = Spring.StiffnessMedium
//                        )
//                    ),
//                    exit = slideOutVertically(
//                        targetOffsetY = { -it / 2 },
//                        animationSpec = tween(250)
//                    ) + fadeOut(
//                        animationSpec = tween(250)
//                    ) + scaleOut(
//                        targetScale = 0.9f,
//                        animationSpec = tween(250)
//                    )
//                ) {
//                    ModernComplaintCard(
//                        complaint = complaint,
//                        currentUser = currentUserData,
//                        userPermissions = userPermissions,
//                        availableEmployees = availableEmployees,
//                        userProfile = userProfiles[complaint.createdBy.userId],
//                        isLoading = false,
//                        onEdit = { updates ->
//                            onUpdateComplaint(complaint.id, updates)
//                        },
//                        onDelete = {
//                            onDeleteComplaint(complaint.id)
//                        },
//                        onAssign = { assigneeId, assigneeName ->
//                            onAssignComplaint(complaint.id, assigneeId, assigneeName)
//                        },
//                        onClose = { resolution ->
//                            onCloseComplaint(complaint.id, resolution)
//                        },
//                        onReopen = {
//                            onReopenComplaint(complaint.id)
//                        },
//                        onChangeStatus = { newStatus, reason ->
//                            onChangeStatus(complaint.id, newStatus, reason)
//                        },
//                        onViewDetails = {
//                            selectedComplaint = complaint
//                        },
//                        onViewUserProfile = onViewUserProfile,
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//
//            // Load more button with animation
//            if (hasMoreData && animatedComplaints.isNotEmpty()) {
//                item(key = "load_more") {
//                    var isLoadMoreVisible by remember { mutableStateOf(false) }
//
//                    LaunchedEffect(Unit) {
//                        delay((animatedComplaints.size * 80L) + 200L)
//                        isLoadMoreVisible = true
//                    }
//
//                    AnimatedVisibility(
//                        visible = isLoadMoreVisible,
//                        enter = slideInVertically(
//                            initialOffsetY = { it / 3 },
//                            animationSpec = spring(
//                                dampingRatio = Spring.DampingRatioMediumBouncy
//                            )
//                        ) + fadeIn(
//                            animationSpec = tween(300)
//                        )
//                    ) {
//                        ModernLoadMoreButton(
//                            onClick = onLoadMore,
//                            isLoading = isRefreshing
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    // Complaint details dialog with animation
//    selectedComplaint?.let { complaint ->
//        AnimatedVisibility(
//            visible = true,
//            enter = fadeIn(
//                animationSpec = tween(300)
//            ) + scaleIn(
//                initialScale = 0.8f,
//                animationSpec = spring(
//                    dampingRatio = Spring.DampingRatioMediumBouncy
//                )
//            ),
//            exit = fadeOut(
//                animationSpec = tween(200)
//            ) + scaleOut(
//                targetScale = 0.8f,
//                animationSpec = tween(200)
//            )
//        ) {
//            ModernComplaintDetailsDialog(
//                complaint = complaint,
//                currentUser = currentUserData,
//                userPermissions = userPermissions,
//                availableEmployees = availableEmployees,
//                userProfiles = userProfiles,
//                onDismiss = { selectedComplaint = null },
//                onEdit = { updates ->
//                    onUpdateComplaint(complaint.id, updates)
//                    selectedComplaint = null
//                },
//                onDelete = {
//                    onDeleteComplaint(complaint.id)
//                    selectedComplaint = null
//                },
//                onAssign = { assigneeId, assigneeName ->
//                    onAssignComplaint(complaint.id, assigneeId, assigneeName)
//                },
//                onClose = { resolution ->
//                    onCloseComplaint(complaint.id, resolution)
//                },
//                onReopen = {
//                    onReopenComplaint(complaint.id)
//                },
//                onChangeStatus = { newStatus, reason ->
//                    onChangeStatus(complaint.id, newStatus, reason)
//                },
//                onViewUserProfile = onViewUserProfile
//            )
//        }
//    }
//}
//
//// Helper functions
//private fun getViewModeTitle(viewMode: ViewMode): String {
//    return when (viewMode) {
//        ViewMode.PERSONAL -> "My Complaints"
//        ViewMode.ASSIGNED_TO_ME -> "Assigned to Me"
//        ViewMode.DEPARTMENT -> "Department Complaints"
//        ViewMode.ALL_COMPANY -> "All Company Complaints"
//        ViewMode.GLOBAL -> "Global Complaints"
//    }
//}
//
//private fun getViewModeSubtitle(viewMode: ViewMode): String {
//    return when (viewMode) {
//        ViewMode.PERSONAL -> "Complaints you created"
//        ViewMode.ASSIGNED_TO_ME -> "Your assigned tasks"
//        ViewMode.DEPARTMENT -> "Department overview"
//        ViewMode.ALL_COMPANY -> "Company-wide view"
//        ViewMode.GLOBAL -> "Global complaints"
//    }
//}
//
//private fun getSortDisplayName(sortOption: SortOption): String {
//    return when (sortOption) {
//        SortOption.DATE_DESC -> "Newest First"
//        SortOption.DATE_ASC -> "Oldest First"
//        SortOption.URGENCY -> "By Urgency"
//        SortOption.STATUS -> "By Status"
//    }
//}
//
//private fun getEmptyStateMessage(viewMode: ViewMode): String {
//    return when (viewMode) {
//        ViewMode.PERSONAL -> "No complaints yet"
//        ViewMode.ASSIGNED_TO_ME -> "Nothing assigned"
//        ViewMode.DEPARTMENT -> "Department is clear"
//        ViewMode.ALL_COMPANY -> "All caught up!"
//        ViewMode.GLOBAL -> "No global issues"
//    }
//}
//
//private fun getEmptyStateSubMessage(viewMode: ViewMode): String {
//    return when (viewMode) {
//        ViewMode.PERSONAL -> "You haven't created any complaints yet. Tap the button below to get started."
//        ViewMode.ASSIGNED_TO_ME -> "No complaints have been assigned to you at the moment."
//        ViewMode.DEPARTMENT -> "Your department has no active complaints right now."
//        ViewMode.ALL_COMPANY -> "Great job! No complaints found across the company."
//        ViewMode.GLOBAL -> "No global complaints have been created yet."
//    }
//}