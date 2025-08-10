//package com.example.ritik_2.profile.profileaccess
//
//import android.net.Uri
//import androidx.compose.foundation.BorderStroke
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.example.ritik_2.authentication.UserData
//import com.example.ritik_2.profile.profilecompletion.*
//import com.example.ritik_2.theme.ThemedColors
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ProfileAccessScreen(
//    targetUserId: String,
//    targetUserName: String,
//    targetUserRole: String,
//    currentUser: UserData,
//    accessPermission: AccessPermission,
//    isDarkTheme: Boolean,
//    onProfileUpdateClick: (Map<String, Any>, String?, Uri?) -> Unit,
//    onBackClick: () -> Unit,
//    onLogoutClick: () -> Unit
//) {
//    // Create permissions based on current user's access level
//    val effectivePermissions = remember(currentUser.role, accessPermission.accessLevel) {
//        createEffectivePermissions(currentUser.role, accessPermission.accessLevel)
//    }
//
//    val scrollState = rememberScrollState()
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(ThemedColors.background(isDarkTheme))
//            .verticalScroll(scrollState)
//            .padding(16.dp)
//    ) {
//        // Header with access info
//        ProfileAccessHeader(
//            targetUserName = targetUserName,
//            targetUserRole = targetUserRole,
//            currentUser = currentUser,
//            accessPermission = accessPermission,
//            isDarkTheme = isDarkTheme,
//            onBackClick = onBackClick
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Access level information card
//        AccessLevelInfoCard(accessPermission, isDarkTheme)
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Main profile completion screen with modified permissions
//        ProfileCompletionScreen(
//            userId = targetUserId,
//            userRole = targetUserRole,
//            isDarkTheme = isDarkTheme,
//            onProfileUpdateClick = onProfileUpdateClick,
//            onSkipClick = onBackClick, // Convert skip to back navigation
//            onLogoutClick = onLogoutClick
//        )
//    }
//}
//
//@Composable
//fun ProfileAccessHeader(
//    targetUserName: String,
//    targetUserRole: String,
//    currentUser: UserData,
//    accessPermission: AccessPermission,
//    isDarkTheme: Boolean,
//    onBackClick: () -> Unit
//) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(containerColor = ThemedColors.primary(isDarkTheme)),
//        shape = RoundedCornerShape(16.dp),
//        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(20.dp)
//        ) {
//            // Back button row
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                IconButton(
//                    onClick = onBackClick,
//                    colors = IconButtonDefaults.iconButtonColors(
//                        contentColor = Color.White
//                    )
//                ) {
//                    Icon(
//                        Icons.Filled.ArrowBack,
//                        contentDescription = "Back",
//                        modifier = Modifier.size(24.dp)
//                    )
//                }
//
//                Icon(
//                    when (accessPermission.accessLevel) {
//                        AccessLevel.FULL_ACCESS -> Icons.Filled.AdminPanelSettings
//                        AccessLevel.COMPANY_ACCESS -> Icons.Filled.Business
//                        AccessLevel.HR_ACCESS -> Icons.Filled.People
//                        AccessLevel.TEAM_ACCESS -> Icons.Filled.Group
//                        else -> Icons.Filled.Visibility
//                    },
//                    contentDescription = null,
//                    tint = Color.White,
//                    modifier = Modifier.size(32.dp)
//                )
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // Title
//            Text(
//                text = "Profile Access",
//                fontSize = 24.sp,
//                fontWeight = FontWeight.Bold,
//                color = Color.White,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            // Target user info
//            Text(
//                text = buildString {
//                    append("Viewing: ")
//                    if (targetUserName.isNotEmpty()) {
//                        append(targetUserName)
//                    } else {
//                        append("User Profile")
//                    }
//                    append(" ($targetUserRole)")
//                },
//                fontSize = 16.sp,
//                color = Color.White.copy(alpha = 0.9f),
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            // Current user info
//            Text(
//                text = "Accessed by: ${currentUser.name} (${currentUser.role})",
//                fontSize = 12.sp,
//                color = Color.White.copy(alpha = 0.7f),
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//        }
//    }
//}
//
//@Composable
//fun AccessLevelInfoCard(accessPermission: AccessPermission, isDarkTheme: Boolean) {
//    val (icon, title, description, color) = when (accessPermission.accessLevel) {
//        AccessLevel.FULL_ACCESS -> {
//            Tuple4(
//                Icons.Filled.AdminPanelSettings,
//                "Full Access",
//                "You can modify all profile fields including core information, sensitive data, and manage passwords",
//                ThemedColors.success(isDarkTheme)
//            )
//        }
//        AccessLevel.COMPANY_ACCESS -> {
//            Tuple4(
//                Icons.Filled.Business,
//                "Company Access",
//                "You can modify most profile fields for users in your company, including personal and work data",
//                ThemedColors.primary(isDarkTheme)
//            )
//        }
//        AccessLevel.HR_ACCESS -> {
//            Tuple4(
//                Icons.Filled.People,
//                "HR Access",
//                "You can modify personal information, work statistics, and sensitive HR data for company users",
//                ThemedColors.warning(isDarkTheme)
//            )
//        }
//        AccessLevel.TEAM_ACCESS -> {
//            Tuple4(
//                Icons.Filled.Group,
//                "Team Access",
//                "You can modify personal information and work statistics for users in your team/department",
//                ThemedColors.warning(isDarkTheme).copy(alpha = 0.8f)
//            )
//        }
//        AccessLevel.NO_ACCESS -> {
//            Tuple4(
//                Icons.Filled.Lock,
//                "No Access",
//                "You have read-only access to this profile",
//                ThemedColors.error(isDarkTheme)
//            )
//        }
//    }
//
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(
//            containerColor = color.copy(alpha = 0.1f)
//        ),
//        border = BorderStroke(
//            1.dp,
//            color.copy(alpha = 0.3f)
//        )
//    ) {
//        Row(
//            modifier = Modifier.padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(
//                icon,
//                contentDescription = null,
//                tint = color,
//                modifier = Modifier.size(24.dp)
//            )
//            Spacer(modifier = Modifier.width(16.dp))
//            Column(modifier = Modifier.weight(1f)) {
//                Text(
//                    title,
//                    fontSize = 16.sp,
//                    fontWeight = FontWeight.SemiBold,
//                    color = ThemedColors.onSurface(isDarkTheme)
//                )
//                Spacer(modifier = Modifier.height(4.dp))
//                Text(
//                    description,
//                    fontSize = 12.sp,
//                    color = ThemedColors.onSurfaceVariant(isDarkTheme),
//                    lineHeight = 16.sp
//                )
//                if (accessPermission.reason.isNotEmpty()) {
//                    Spacer(modifier = Modifier.height(4.dp))
//                    Text(
//                        "Reason: ${accessPermission.reason}",
//                        fontSize = 11.sp,
//                        color = color,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
//        }
//    }
//}
//
//// Helper function to create effective permissions based on access level
//fun createEffectivePermissions(currentUserRole: String, accessLevel: AccessLevel): UserPermissions {
//    return when (accessLevel) {
//        AccessLevel.FULL_ACCESS -> UserPermissions(
//            role = "Administrator (Access Mode)",
//            permissions = listOf("modify_all_data", "create_user", "delete_user", "manage_roles"),
//            canModifyAll = true,
//            canModifyPersonal = true,
//            canModifyPhoto = true
//        )
//        AccessLevel.COMPANY_ACCESS -> UserPermissions(
//            role = "Manager (Access Mode)",
//            permissions = listOf("modify_personal_data", "modify_work_data", "view_company_data"),
//            canModifyAll = false,
//            canModifyPersonal = true,
//            canModifyPhoto = true
//        )
//        AccessLevel.HR_ACCESS -> UserPermissions(
//            role = "HR (Access Mode)",
//            permissions = listOf("modify_personal_data", "modify_sensitive_data", "view_hr_data"),
//            canModifyAll = false,
//            canModifyPersonal = true,
//            canModifyPhoto = true
//        )
//        AccessLevel.TEAM_ACCESS -> UserPermissions(
//            role = "Team Lead (Access Mode)",
//            permissions = listOf("modify_personal_data", "view_team_data"),
//            canModifyAll = false,
//            canModifyPersonal = true,
//            canModifyPhoto = false
//        )
//        AccessLevel.NO_ACCESS -> UserPermissions(
//            role = "Viewer",
//            permissions = listOf("view_profile"),
//            canModifyAll = false,
//            canModifyPersonal = false,
//            canModifyPhoto = false
//        )
//    }
//}
//
//// Helper data class for tuple
//data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)