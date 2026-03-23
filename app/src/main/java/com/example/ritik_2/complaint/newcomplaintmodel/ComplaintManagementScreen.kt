package com.example.ritik_2.complaint.newcomplaintmodel

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─────────────────────────────────────────────
// ROOT SCREEN
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintManagementScreen(
    myComplaints: List<LiveComplaint>,
    deptComplaints: List<LiveComplaint>,
    assignedComplaints: List<LiveComplaint>,
    notifications: List<InAppNotification>,
    unreadCount: Int,
    isLoading: Boolean,
    session: CurrentUserSession?,
    departmentMembers: List<DepartmentMember>,
    errorMessage: String?,
    selectedComplaint: LiveComplaint?,
    selectedMemberProfile: DepartmentMember?,
    onSelectComplaint: (LiveComplaint) -> Unit,
    onDismissComplaint: () -> Unit,
    onAssignComplaint: (LiveComplaint, DepartmentMember, String) -> Unit,
    onUpdateStatus: (LiveComplaint, String, String, String?) -> Unit,
    onCloseComplaint: (LiveComplaint, String) -> Unit,
    onTapAssignee: (String) -> Unit,
    onDismissProfile: () -> Unit,
    onNotificationTap: (InAppNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearError: () -> Unit,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit
) {
    // Tab definitions (Department only shown to head roles)
    val tabs = remember(session) {
        buildList {
            add(TabInfo("My Complaints", Icons.Default.Person))
            if (session?.isHeadRole == true)
                add(TabInfo("Department", Icons.Default.Business))
            add(TabInfo("Assigned to Me", Icons.Default.AssignmentInd))
        }
    }
    var activeTab by remember { mutableStateOf(0) }
    var showNotificationsPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Map tab index → list
    val rawList = remember(myComplaints, deptComplaints, assignedComplaints, activeTab, session) {
        when {
            activeTab == 0 -> myComplaints
            session?.isHeadRole == true && activeTab == 1 -> deptComplaints
            else -> assignedComplaints
        }
    }

    // Apply search filter
    val filteredComplaints = remember(rawList, searchQuery) {
        if (searchQuery.isBlank()) rawList
        else {
            val q = searchQuery.trim().lowercase()
            rawList.filter { c ->
                c.title.lowercase().contains(q)
                        || c.description.lowercase().contains(q)
                        || c.category.lowercase().contains(q)
                        || c.status.lowercase().contains(q)
                        || c.urgency.lowercase().contains(q)
                        || c.createdBy.name.lowercase().contains(q)
                        || c.assignedToUser?.name?.lowercase()?.contains(q) == true
                        || c.tags.any { it.lowercase().contains(q) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Complaint Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        session?.let {
                            Text(
                                "${it.name} · ${it.role}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = { showNotificationsPanel = true }) {
                            Icon(
                                if (unreadCount > 0) Icons.Filled.Notifications
                                else Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = if (unreadCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            errorMessage?.let { msg ->
                ErrorBanner(message = msg, onDismiss = onClearError)
            }

            // ── Styled tab row ───────────────────────────────────────────
            ComplaintTabRow(
                tabs = tabs,
                selectedIndex = activeTab,
                onTabSelected = { activeTab = it }
            )

            // ── Search bar ───────────────────────────────────────────────
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Summary counts row ───────────────────────────────────────
            SummaryRow(complaints = rawList)

            // ── List ─────────────────────────────────────────────────────
            if (filteredComplaints.isEmpty() && !isLoading) {
                EmptyComplaintState(
                    message = when {
                        searchQuery.isNotBlank() -> "No results for \"$searchQuery\""
                        activeTab == 0 -> "No complaints raised yet"
                        session?.isHeadRole == true && activeTab == 1 -> "No department complaints"
                        else -> "Nothing assigned to you"
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredComplaints,
                        key = { it.id }
                    ) { complaint ->
                        SwipeableComplaintCard(
                            complaint = complaint,
                            session = session,
                            onTap = { onSelectComplaint(complaint) },
                            onTapAssignee = onTapAssignee,
                            onSwipeClose = { resolution ->
                                onCloseComplaint(complaint, resolution)
                            },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(200),
                                fadeOutSpec = tween(200)
                            )
                        )
                    }
                }
            }
        }
    }

    // ── Overlays ───────────────────────────────────────────────────────────
    if (showNotificationsPanel) {
        NotificationPanelDialog(
            notifications = notifications,
            onDismiss = { showNotificationsPanel = false },
            onNotificationTap = { onNotificationTap(it); showNotificationsPanel = false },
            onMarkAllRead = onMarkAllRead
        )
    }

    selectedComplaint?.let { complaint ->
        ComplaintDetailDialog(
            complaint = complaint,
            session = session,
            departmentMembers = departmentMembers,
            onDismiss = onDismissComplaint,
            onAssign = { member, note -> onAssignComplaint(complaint, member, note) },
            onUpdateStatus = { status, desc, res -> onUpdateStatus(complaint, status, desc, res) },
            onCloseComplaint = { res -> onCloseComplaint(complaint, res) },
            onTapAssignee = onTapAssignee
        )
    }

    selectedMemberProfile?.let { member ->
        AssigneeProfileDialog(member = member, onDismiss = onDismissProfile)
    }
}

// ─────────────────────────────────────────────
// TAB INFO MODEL
// ─────────────────────────────────────────────

data class TabInfo(val label: String, val icon: ImageVector)

// ─────────────────────────────────────────────
// STYLED TAB ROW
// ─────────────────────────────────────────────

@Composable
fun ComplaintTabRow(
    tabs: List<TabInfo>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    if (tabs.size <= 1) return

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedIndex == index
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// SEARCH BAR
// ─────────────────────────────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search by title, status, person…", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    )
}

// ─────────────────────────────────────────────
// SUMMARY ROW
// ─────────────────────────────────────────────

@Composable
fun SummaryRow(complaints: List<LiveComplaint>) {
    val open    = remember(complaints) { complaints.count { it.status.lowercase() == "open" } }
    val inProg  = remember(complaints) { complaints.count { it.status.lowercase() == "in progress" } }
    val closed  = remember(complaints) { complaints.count { it.status.lowercase() in listOf("closed", "resolved") } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip("Open", open.toString(), Color(0xFF2196F3), Modifier.weight(1f))
        SummaryChip("In Progress", inProg.toString(), Color(0xFFFF9800), Modifier.weight(1f))
        SummaryChip("Closed", closed.toString(), Color(0xFF4CAF50), Modifier.weight(1f))
    }
}

@Composable
fun SummaryChip(label: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

// ─────────────────────────────────────────────
// SWIPEABLE COMPLAINT CARD
// (Swipe LEFT → quick close, swipe RIGHT → quick assign if head role)
// ─────────────────────────────────────────────

@Composable
fun SwipeableComplaintCard(
    complaint: LiveComplaint,
    session: CurrentUserSession?,
    onTap: () -> Unit,
    onTapAssignee: (String) -> Unit,
    onSwipeClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )

    val canClose = session?.canClosComplaint(complaint) == true && !complaint.isClosed
    val swipeThreshold = 200f

    Box(modifier = modifier.fillMaxWidth()) {
        // Background actions
        if (canClose && offsetX < -40f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .width(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Close", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .pointerInput(canClose) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (canClose && offsetX < -swipeThreshold) {
                                showCloseConfirm = true
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f }
                    ) { _, dragAmount ->
                        if (canClose) {
                            offsetX = (offsetX + dragAmount).coerceIn(-swipeThreshold * 1.2f, 0f)
                        }
                    }
                }
        ) {
            ComplaintLiveCard(
                complaint = complaint,
                session = session,
                onTap = onTap,
                onTapAssignee = onTapAssignee
            )
        }
    }

    if (showCloseConfirm) {
        QuickCloseDialog(
            complaintTitle = complaint.title,
            onConfirm = { res -> onSwipeClose(res); showCloseConfirm = false },
            onDismiss = { showCloseConfirm = false }
        )
    }
}

@Composable
fun QuickCloseDialog(
    complaintTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var resolution by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF4CAF50)) },
        title = { Text("Close Complaint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"$complaintTitle\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    label = { Text("Resolution summary *") },
                    placeholder = { Text("What was the resolution?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(resolution) },
                enabled = resolution.isNotBlank()
            ) { Text("Close Complaint") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────
// COMPLAINT LIVE CARD
// ─────────────────────────────────────────────

@Composable
fun ComplaintLiveCard(
    complaint: LiveComplaint,
    session: CurrentUserSession?,
    onTap: () -> Unit,
    onTapAssignee: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverdue = remember(complaint) {
        if (complaint.isClosed) false
        else {
            val threshold = when (complaint.urgency.lowercase()) {
                "critical" -> 4 * 3_600_000L
                "high" -> 24 * 3_600_000L
                "medium" -> 3 * 86_400_000L
                else -> 7 * 86_400_000L
            }
            System.currentTimeMillis() - complaint.createdAt > threshold
        }
    }

    val cardColor = when {
        complaint.isClosed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        complaint.urgency.lowercase() == "critical" -> MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = onTap,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (complaint.isClosed) 1.dp else 3.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (complaint.isClosed) 0.7f else 1f)
                .padding(14.dp)
        ) {
            // ── Header row ───────────────────────────────────────────
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                UserAvatar(
                    name = complaint.createdBy.name,
                    size = 38.dp,
                    onClick = { onTapAssignee(complaint.createdBy.userId) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = complaint.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (complaint.isClosed)
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                            else
                                androidx.compose.ui.text.style.TextDecoration.None
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        UrgencyBadge(urgency = complaint.urgency)
                    }
                    Text(
                        text = "by ${complaint.createdBy.name} · ${complaint.createdAt.timeAgo()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Description preview (FIXED — always shown) ───────────
            if (complaint.description.isNotBlank()) {
                Text(
                    text = complaint.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Status chips row ─────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChipLive(status = complaint.status)
                CategoryChip(category = complaint.category)
                if (complaint.isGlobal) GlobalChip()
                if (isOverdue) OverdueChip()
                Spacer(modifier = Modifier.weight(1f))
                if (complaint.hasAttachment) {
                    Icon(
                        Icons.Default.Attachment,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Compact timeline ─────────────────────────────────────
            if (complaint.timeline.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                CompactTimeline(timeline = complaint.timeline)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(6.dp))

            // ── Footer row ───────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (complaint.assignedToUser != null) Icons.Filled.Person
                    else Icons.Outlined.PersonOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (complaint.assignedToUser != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (complaint.assignedToUser != null) {
                    Text(
                        text = complaint.assignedToUser.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onTapAssignee(complaint.assignedToUser.userId) }
                    )
                } else {
                    Text(
                        text = "Unassigned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = complaint.updatedAt.timeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // ── Resolution note for closed complaints ─────────────────
            if (complaint.isClosed && !complaint.resolution.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = complaint.resolution,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// COMPACT PROGRESS TIMELINE
// ─────────────────────────────────────────────

@Composable
fun CompactTimeline(timeline: List<TimelineEvent>) {
    val last3 = timeline.takeLast(3)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        last3.forEachIndexed { index, event ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(timelineColor(event.type))
                    )
                    if (index < last3.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.timestamp.timeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun timelineColor(type: TimelineEventType): Color = when (type) {
    TimelineEventType.CREATED     -> Color(0xFF2196F3)
    TimelineEventType.ASSIGNED    -> Color(0xFFFF9800)
    TimelineEventType.STATUS_CHANGE -> Color(0xFF9C27B0)
    TimelineEventType.RESOLVED    -> Color(0xFF4CAF50)
    TimelineEventType.REOPENED    -> Color(0xFFE91E63)
    TimelineEventType.ESCALATED   -> Color(0xFFFF5722)
    TimelineEventType.COMMENT     -> Color(0xFF607D8B)
}

// ─────────────────────────────────────────────
// COMPLAINT DETAIL DIALOG
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintDetailDialog(
    complaint: LiveComplaint,
    session: CurrentUserSession?,
    departmentMembers: List<DepartmentMember>,
    onDismiss: () -> Unit,
    onAssign: (DepartmentMember, String) -> Unit,
    onUpdateStatus: (String, String, String?) -> Unit,
    onCloseComplaint: (String) -> Unit,
    onTapAssignee: (String) -> Unit
) {
    var showAssignSheet by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }

    val canClose = session?.canClosComplaint(complaint) == true && !complaint.isClosed
    val canAssign = session?.isHeadRole == true && !complaint.isClosed
    val canUpdateStatus = (session?.isHeadRole == true
            || session?.userId == complaint.assignedToUser?.userId) && !complaint.isClosed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Gradient header ──────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.0f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Complaint Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Text(
                            text = complaint.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChipLive(complaint.status)
                            UrgencyBadge(complaint.urgency)
                            if (complaint.isGlobal) GlobalChip()
                        }
                    }
                }

                // ── Scrollable body ──────────────────────────────────
                LazyColumn(
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Description (FULL)
                    item {
                        DetailSection("Description", Icons.Default.Description) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = complaint.description.ifBlank { "No description provided." },
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = if (complaint.description.isBlank())
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Resolution (if closed)
                    if (complaint.isClosed && !complaint.resolution.isNullOrBlank()) {
                        item {
                            DetailSection("Resolution", Icons.Default.CheckCircle) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = complaint.resolution,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }

                    // People
                    item {
                        DetailSection("People", Icons.Default.People) {
                            PersonRow(
                                label = "Raised by",
                                info = complaint.createdBy,
                                onTap = { onTapAssignee(complaint.createdBy.userId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (complaint.assignedToUser != null) {
                                PersonRow(
                                    label = "Assigned to",
                                    info = complaint.assignedToUser,
                                    onTap = { onTapAssignee(complaint.assignedToUser.userId) }
                                )
                            } else {
                                UnassignedRow()
                            }
                        }
                    }

                    // Full Timeline
                    if (complaint.timeline.isNotEmpty()) {
                        item {
                            DetailSection("Progress Timeline", Icons.Default.Timeline) {
                                FullTimeline(timeline = complaint.timeline)
                            }
                        }
                    }

                    // Details
                    item {
                        DetailSection("Details", Icons.Default.Info) {
                            DetailRow(Icons.Default.Category, "Category", complaint.category)
                            DetailRow(Icons.Default.Schedule, "Created", complaint.createdAt.formatDate())
                            DetailRow(Icons.Default.Update, "Last Updated", complaint.updatedAt.formatDate())
                            complaint.resolvedAt?.let {
                                DetailRow(Icons.Default.CheckCircle, "Closed At", it.formatDate())
                            }
                            if (complaint.estimatedResolutionTime.isNotBlank()) {
                                DetailRow(Icons.Default.Timer, "Est. Resolution", complaint.estimatedResolutionTime)
                            }
                        }
                    }
                }

                // ── Action buttons ────────────────────────────────────
                if (canAssign || canUpdateStatus || canClose) {
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row: Assign + Update Status
                        if (canAssign || canUpdateStatus) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (canAssign) {
                                    OutlinedButton(
                                        onClick = { showAssignSheet = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Assign")
                                    }
                                }
                                if (canUpdateStatus) {
                                    Button(
                                        onClick = { showStatusSheet = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Update Status")
                                    }
                                }
                            }
                        }
                        // Close button (full width, green)
                        if (canClose) {
                            Button(
                                onClick = { showCloseDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Close Complaint", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Sub-sheets
    if (showAssignSheet) {
        AssignmentSheet(
            departmentMembers = departmentMembers,
            currentAssigneeId = complaint.assignedToUser?.userId,
            onConfirm = { member, note -> onAssign(member, note); showAssignSheet = false },
            onDismiss = { showAssignSheet = false }
        )
    }
    if (showStatusSheet) {
        StatusUpdateSheet(
            currentStatus = complaint.status,
            session = session,
            complaint = complaint,
            onConfirm = { status, desc, res -> onUpdateStatus(status, desc, res); showStatusSheet = false },
            onDismiss = { showStatusSheet = false }
        )
    }
    if (showCloseDialog) {
        QuickCloseDialog(
            complaintTitle = complaint.title,
            onConfirm = { res -> onCloseComplaint(res); showCloseDialog = false },
            onDismiss = { showCloseDialog = false }
        )
    }
}

// ─────────────────────────────────────────────
// ASSIGNMENT SHEET
// ─────────────────────────────────────────────

@Composable
fun AssignmentSheet(
    departmentMembers: List<DepartmentMember>,
    currentAssigneeId: String?,
    onConfirm: (DepartmentMember, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMember by remember { mutableStateOf<DepartmentMember?>(null) }
    var note by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(departmentMembers, searchQuery) {
        if (searchQuery.isBlank()) departmentMembers
        else departmentMembers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.role.contains(searchQuery, ignoreCase = true) ||
                    it.designation.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Assign Complaint", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select a team member to handle this", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search members") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = filtered.groupBy { it.role }
                    grouped.forEach { (role, members) ->
                        item {
                            Text(
                                text = role.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(members) { member ->
                            MemberSelectionCard(
                                member = member,
                                isSelected = selectedMember == member,
                                isCurrentAssignee = member.userId == currentAssigneeId,
                                onSelect = { selectedMember = member }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                    Button(
                        onClick = { selectedMember?.let { onConfirm(it, note) } },
                        enabled = selectedMember != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Assign") }
                }
            }
        }
    }
}

@Composable
fun MemberSelectionCard(
    member: DepartmentMember,
    isSelected: Boolean,
    isCurrentAssignee: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(name = member.name, size = 42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (isCurrentAssignee) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("Current", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                Text("${member.role} · ${member.designation.ifBlank { member.department }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (member.email.isNotBlank()) {
                    Text(member.email, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// STATUS UPDATE SHEET
// (filtered options based on role — employees can't choose Closed)
// ─────────────────────────────────────────────

@Composable
fun StatusUpdateSheet(
    currentStatus: String,
    session: CurrentUserSession?,
    complaint: LiveComplaint,
    onConfirm: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus) }
    var description by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }

    // Employees assigned to complaint can only set In Progress / On Hold / Needs Info
    // Head roles can set anything except Close (which has its own dedicated button)
    val statusOptions = remember(session, complaint) {
        val base = listOf(
            "In Progress" to Icons.Default.Pending,
            "On Hold" to Icons.Default.PauseCircle,
            "Needs Info" to Icons.Default.HelpOutline,
            "Resolved" to Icons.Default.CheckCircle,
            "Reopened" to Icons.Default.Refresh
        )
        if (session?.isHeadRole == true) base
        else base.filter { (s, _) -> s !in listOf("Resolved") || complaint.assignedToUser?.userId == session?.userId }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Update Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Current: $currentStatus", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Status grid
                statusOptions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (status, icon) ->
                            val isSelected = selectedStatus == status
                            Surface(
                                onClick = { selectedStatus = status },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) statusColor(status).copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(2.dp, statusColor(status)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(icon, null, tint = statusColor(status), modifier = Modifier.size(17.dp))
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) statusColor(status) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Progress note *") },
                    placeholder = { Text("What was done / why is this changing?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                if (selectedStatus.lowercase() == "resolved") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolution,
                        onValueChange = { resolution = it },
                        label = { Text("Resolution summary *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val res = if (selectedStatus.lowercase() == "resolved") resolution.ifBlank { null } else null
                            onConfirm(selectedStatus, description.ifBlank { "Status updated" }, res)
                        },
                        enabled = selectedStatus != currentStatus && description.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Update") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// NOTIFICATION PANEL
// ─────────────────────────────────────────────

@Composable
fun NotificationPanelDialog(
    notifications: List<InAppNotification>,
    onDismiss: () -> Unit,
    onNotificationTap: (InAppNotification) -> Unit,
    onMarkAllRead: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = onMarkAllRead) { Text("Mark all read") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    }
                }
                HorizontalDivider()

                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Notifications, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No notifications yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            NotificationCard(notification = notif, onTap = { onNotificationTap(notif) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notification: InAppNotification, onTap: () -> Unit) {
    val notifColor = notificationColor(notification.type)
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(14.dp),
        color = if (!notification.isRead) notifColor.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (!notification.isRead) BorderStroke(1.dp, notifColor.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = notifColor.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(notificationIcon(notification.type), null, tint = notifColor,
                        modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(notification.createdAt.timeAgo(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(notification.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (notification.fromUserName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("From: ${notification.fromUserName}", style = MaterialTheme.typography.labelSmall,
                        color = notifColor, fontWeight = FontWeight.Medium)
                }
            }
            if (!notification.isRead) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(notifColor))
            }
        }
    }
}

// ─────────────────────────────────────────────
// ASSIGNEE PROFILE DIALOG
// ─────────────────────────────────────────────

@Composable
fun AssigneeProfileDialog(member: DepartmentMember, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(name = member.name, size = 80.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(member.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        "${member.role} · ${member.designation.ifBlank { member.department }}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                ContactDetailRow(Icons.Default.Email, "Email", member.email)
                if (member.phoneNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ContactDetailRow(Icons.Default.Phone, "Phone", member.phoneNumber)
                }
                Spacer(modifier = Modifier.height(10.dp))
                ContactDetailRow(Icons.Default.Business, "Department", member.department)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) { Text("Close") }
            }
        }
    }
}

// ─────────────────────────────────────────────
// FULL TIMELINE
// ─────────────────────────────────────────────

@Composable
fun FullTimeline(timeline: List<TimelineEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        timeline.forEachIndexed { index, event ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(timelineColor(event.type)))
                    if (index < timeline.size - 1) {
                        Box(modifier = Modifier.width(2.dp).height(42.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                        .padding(bottom = if (index < timeline.size - 1) 18.dp else 0.dp)
                ) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (event.description.isNotBlank()) {
                        Text(event.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${event.performedBy} · ${event.timestamp.formatDate()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// REUSABLE SMALL COMPONENTS
// ─────────────────────────────────────────────

@Composable
fun UserAvatar(name: String, size: Dp, onClick: (() -> Unit)? = null) {
    val colors = remember(name) { avatarColors(name.hashCode()) }
    val initials = remember(name) {
        name.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.first, colors.second)))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 3).sp
        )
    }
}

private fun avatarColors(seed: Int): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF6366F1) to Color(0xFF8B5CF6),
        Color(0xFF10B981) to Color(0xFF059669),
        Color(0xFFF59E0B) to Color(0xFFD97706),
        Color(0xFFEF4444) to Color(0xFFDC2626),
        Color(0xFF3B82F6) to Color(0xFF2563EB),
        Color(0xFF8B5CF6) to Color(0xFF7C3AED),
        Color(0xFFF97316) to Color(0xFFEA580C),
        Color(0xFF14B8A6) to Color(0xFF0D9488)
    )
    return palette[abs(seed) % palette.size]
}

@Composable
fun StatusChipLive(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun UrgencyBadge(urgency: String, modifier: Modifier = Modifier) {
    val color = urgencyColor(urgency)
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f)) {
        Text(urgency, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CategoryChip(category: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(category, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GlobalChip() {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Public, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(3.dp))
            Text("Global", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun OverdueChip() {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(3.dp))
            Text("Overdue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun EmptyComplaintState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Assignment, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DetailSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun PersonRow(label: String, info: LiveUserInfo, onTap: () -> Unit) {
    Surface(onClick = onTap, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UserAvatar(name = info.name, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(info.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${info.role.ifBlank { "—" }} · ${info.department.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun UnassignedRow() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.PersonOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp))
                }
            }
            Column {
                Text("Not assigned", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Awaiting assignment by department head",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ContactDetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────
// COLOR & FORMAT HELPERS
// ─────────────────────────────────────────────

@Composable
fun statusColor(status: String): Color = when (status.lowercase()) {
    "open"                      -> Color(0xFF2196F3)
    "in progress", "assigned"   -> Color(0xFFFF9800)
    "on hold", "needs info"     -> Color(0xFF9C27B0)
    "resolved", "closed"        -> Color(0xFF4CAF50)
    "reopened"                  -> Color(0xFFE91E63)
    else                        -> MaterialTheme.colorScheme.outline
}

@Composable
fun urgencyColor(urgency: String): Color = when (urgency.lowercase()) {
    "critical" -> Color(0xFFFF0000)
    "high"     -> Color(0xFFFF6600)
    "medium"   -> Color(0xFFFFB300)
    "low"      -> Color(0xFF4CAF50)
    else       -> MaterialTheme.colorScheme.outline
}

@Composable
fun notificationColor(type: NotificationType): Color = when (type) {
    NotificationType.COMPLAINT_CREATED  -> Color(0xFF2196F3)
    NotificationType.COMPLAINT_ASSIGNED -> Color(0xFFFF9800)
    NotificationType.STATUS_UPDATE      -> Color(0xFF9C27B0)
    NotificationType.COMPLAINT_RESOLVED -> Color(0xFF4CAF50)
    NotificationType.COMPLAINT_REOPENED -> Color(0xFFE91E63)
    NotificationType.NEW_COMMENT        -> Color(0xFF607D8B)
    NotificationType.GENERAL            -> MaterialTheme.colorScheme.primary
}

@Composable
fun notificationIcon(type: NotificationType) = when (type) {
    NotificationType.COMPLAINT_CREATED  -> Icons.Default.Add
    NotificationType.COMPLAINT_ASSIGNED -> Icons.Default.PersonAdd
    NotificationType.STATUS_UPDATE      -> Icons.Default.SwapHoriz
    NotificationType.COMPLAINT_RESOLVED -> Icons.Default.CheckCircle
    NotificationType.COMPLAINT_REOPENED -> Icons.Default.Refresh
    NotificationType.NEW_COMMENT        -> Icons.Default.Comment
    NotificationType.GENERAL            -> Icons.Default.Notifications
}

fun Long.timeAgo(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000      -> "just now"
        diff < 3_600_000   -> "${diff / 60_000}m ago"
        diff < 86_400_000  -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else               -> formatDate()
    }
}

fun Long.formatDate(): String {
    if (this == 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(this))
}