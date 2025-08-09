package com.example.ritik_2.administrator.administratorpanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.administrator.administratorpanel.newusercreation.CreateUserActivity
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdministratorPanelScreen(
    adminData: AdministratorPanelActivity.AdminData?,
    departmentData: List<AdministratorPanelActivity.DepartmentData>,
    organizationStats: AdministratorPanelActivity.OrganizationStats?,
    isLoading: Boolean,
    hasAccess: Boolean,
    onFunctionClick: (AdministratorPanelActivity.AdminFunction) -> Unit
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(hasAccess) {
        if (hasAccess) {
            delay(300)
            isVisible = true
        }
    }

    if (!hasAccess) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF6C757D))
        }
        return
    }

    val adminFunctions = listOf(
        AdministratorPanelActivity.AdminFunction(
            id = "create_user",
            title = "Create User",
            description = "Add new users to the organization",
            icon = Icons.Default.PersonAdd,
            color = Color(0xFF4CAF50),
            activityClass = CreateUserActivity::class.java,
            permissions = listOf("create_user")
        ),
        AdministratorPanelActivity.AdminFunction(
            id = "manage_users",
            title = "Manage Users",
            description = "View, edit, and manage existing users",
            icon = Icons.Default.People,
            color = Color(0xFF2196F3),
            permissions = listOf("view_all_users", "modify_user")
        ),
        AdministratorPanelActivity.AdminFunction(
            id = "view_analytics",
            title = "Analytics Dashboard",
            description = "View organization analytics and insights",
            icon = Icons.Default.Analytics,
            color = Color(0xFF9C27B0),
            permissions = listOf("view_analytics")
        ),
        AdministratorPanelActivity.AdminFunction(
            id = "company_settings",
            title = "Company Settings",
            description = "Manage company information and settings",
            icon = Icons.Default.Business,
            color = Color(0xFFFF9800),
            permissions = listOf("manage_companies")
        ),
        AdministratorPanelActivity.AdminFunction(
            id = "role_management",
            title = "Role Management",
            description = "Create and manage user roles and permissions",
            icon = Icons.Default.AdminPanelSettings,
            color = Color(0xFFF44336),
            permissions = listOf("manage_roles", "manage_permissions")
        ),
        AdministratorPanelActivity.AdminFunction(
            id = "reports",
            title = "Reports & Export",
            description = "Generate reports and export data",
            icon = Icons.Default.Assessment,
            color = Color(0xFF607D8B),
            permissions = listOf("export_data", "generate_reports")
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAFAFA),
                        Color(0xFFF5F5F5)
                    )
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
                AdminHeaderSection(
                    adminData = adminData,
                    isLoading = isLoading
                )
            }
        }

        // Organization Statistics
        item {
            AnimatedVisibility(
                visible = isVisible && organizationStats != null,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            ) {
                organizationStats?.let { stats ->
                    OrganizationStatsSection(stats = stats)
                }
            }
        }

        // Department Overview
        if (departmentData.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    DepartmentOverviewSection(departmentData = departmentData)
                }
            }
        }

        // Admin Functions
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 600,
                        delayMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                Text(
                    text = "Quick Actions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        itemsIndexed(adminFunctions) { index, function ->
            var isPressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "scale_animation"
            )

            AdminFunctionCard(
                adminFunction = function,
                index = index,
                modifier = Modifier
                    .scale(scale)
                    .clickable {
                        onFunctionClick(function)
                    },
                onPressStart = { isPressed = true },
                onPressEnd = { isPressed = false }
            )
        }
    }
}

@Composable
fun AdminHeaderSection(
    adminData: AdministratorPanelActivity.AdminData?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Administrator Panel",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on loading state
            if (isLoading && adminData == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6C757D),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                adminData?.let { data ->
                    // Admin Info
                    Column {
                        Text(
                            text = "Welcome, ${data.name}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF34495E)
                        )
                        Text(
                            text = data.email,
                            fontSize = 14.sp,
                            color = Color(0xFF6C757D)
                        )
                        Text(
                            text = "${data.companyName} • ${data.department}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = "Role: ${data.role}",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrganizationStatsSection(stats: AdministratorPanelActivity.OrganizationStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Organization Overview",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("Total Users", stats.totalUsers.toString(), Color(0xFF3498DB))
                StatCard("Departments", stats.totalDepartments.toString(), Color(0xFF9B59B6))
                StatCard("Roles", stats.totalRoles.toString(), Color(0xFFE67E22))
            }
        }
    }
}

@Composable
fun DepartmentOverviewSection(departmentData: List<AdministratorPanelActivity.DepartmentData>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Departments (${departmentData.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(departmentData) { department ->
                    DepartmentCard(department = department)
                }
            }
        }
    }
}

@Composable
fun DepartmentCard(department: AdministratorPanelActivity.DepartmentData) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE9ECEF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = department.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2C3E50),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                Text(
                    text = department.userCount.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Text(
                    text = "Total Users",
                    fontSize = 10.sp,
                    color = Color(0xFF6C757D)
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier
            .size(100.dp, 60.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AdminFunctionCard(
    adminFunction: AdministratorPanelActivity.AdminFunction,
    index: Int,
    modifier: Modifier = Modifier,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(index) {
        delay((index * 100).toLong())
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 100
            )
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Card(
                    modifier = Modifier.size(48.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = adminFunction.color.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = adminFunction.icon,
                            contentDescription = adminFunction.title,
                            tint = adminFunction.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = adminFunction.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = adminFunction.description,
                        fontSize = 12.sp,
                        color = Color(0xFF6C757D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Arrow
                Icon(
                    Icons.Default.ArrowForwardIos,
                    contentDescription = "Navigate",
                    tint = Color(0xFF6C757D),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF2196F3),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ErrorMessage(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = message,
            color = Color(0xFFE74C3C),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        onRetry?.let { retry ->
            Button(
                onClick = retry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Retry", color = Color.White)
            }
        }
    }
}

// Color extensions for consistent theming
object AdminPanelColors {
    val Primary = Color(0xFF2C3E50)
    val Secondary = Color(0xFF34495E)
    val Accent = Color(0xFF3498DB)
    val Success = Color(0xFF2ECC71)
    val Warning = Color(0xFFE67E22)
    val Danger = Color(0xFFE74C3C)
    val Info = Color(0xFF3498DB)
    val Light = Color(0xFFF8F9FA)
    val Dark = Color(0xFF343A40)
    val Muted = Color(0xFF6C757D)

    // Background colors
    val BackgroundPrimary = Color(0xFFFAFAFA)
    val BackgroundSecondary = Color(0xFFF5F5F5)
    val CardBackground = Color.White
    val DividerColor = Color(0xFFE9ECEF)
}

// Extension functions for formatting
fun Int.formatCount(): String {
    return when {
        this >= 1000000 -> "${(this / 1000000.0).let { "%.1f".format(it) }}M"
        this >= 1000 -> "${(this / 1000.0).let { "%.1f".format(it) }}K"
        else -> this.toString()
    }
}

fun String.toInitials(): String {
    return this.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
}