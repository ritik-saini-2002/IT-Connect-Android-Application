//package com.example.ritik_2.modules
//
//import androidx.compose.animation.*
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.*
//import androidx.compose.ui.focus.*
//import androidx.compose.ui.graphics.*
//import androidx.compose.ui.unit.*
//import androidx.compose.foundation.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.itemsIndexed
//import androidx.compose.foundation.text.KeyboardActions
//import androidx.compose.foundation.text.KeyboardOptions
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material.icons.outlined.*
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.vector.ImageVector
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.window.Dialog
//import androidx.compose.ui.window.DialogProperties
//import kotlinx.coroutines.delay
//import com.example.ritik_2.ui.theme.CategoryItem
//import com.example.ritik_2.ui.theme.UrgencyItem
//
//@Composable
//fun EnhancedHeaderCard(animationDelay: Int) {
//    var visible by remember { mutableStateOf(false) }
//
//    LaunchedEffect(Unit) {
//        delay(animationDelay.toLong())
//        visible = true
//    }
//
//    AnimatedVisibility(
//        visible = visible,
//        enter = slideInVertically(
//            initialOffsetY = { -it },
//            animationSpec = tween(600, easing = FastOutSlowInEasing)
//        ) + fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.8f)
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .shadow(
//                    elevation = 24.dp,
//                    shape = RoundedCornerShape(28.dp),
//                    ambientColor = Color.Cyan.copy(alpha = 0.4f),
//                    spotColor = Color.Blue.copy(alpha = 0.4f)
//                )
//                .blur(0.8.dp), // iOS-like blur
//            shape = RoundedCornerShape(28.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = Color.White.copy(alpha = 0.12f) // iOS glass effect
//            ),
//            border = BorderStroke(
//                width = 1.dp,
//                brush = Brush.linearGradient(
//                    colors = listOf(
//                        Color.White.copy(alpha = 0.5f),
//                        Color.White.copy(alpha = 0.1f),
//                        Color.Cyan.copy(alpha = 0.3f)
//                    )
//                )
//            )
//        ) {
//            Box {
//                // Animated background gradient with iOS-like blur
//                val infiniteTransition = rememberInfiniteTransition(label = "header")
//                val gradientOffset by infiniteTransition.animateFloat(
//                    initialValue = 0f,
//                    targetValue = 1f,
//                    animationSpec = infiniteRepeatable(
//                        animation = tween(4000, easing = EaseInOutSine),
//                        repeatMode = RepeatMode.Reverse
//                    ), label = "gradient"
//                )
//
//                Canvas(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(140.dp)
//                        .clip(RoundedCornerShape(28.dp))
//                        .blur(1.2.dp) // Enhanced blur for iOS effect
//                ) {
//                    val gradient = Brush.radialGradient(
//                        0.0f to Color.Cyan.copy(alpha = 0.15f * gradientOffset),
//                        0.4f to Color.Blue.copy(alpha = 0.12f * gradientOffset),
//                        0.8f to Color.Magenta.copy(alpha = 0.08f * gradientOffset),
//                        1.0f to Color.White.copy(alpha = 0.03f * gradientOffset),
//                        center = Offset(size.width * 0.3f, size.height * 0.2f),
//                        radius = size.width * 0.8f
//                    )
//                    drawRect(brush = gradient, size = size)
//                }
//
//                Column(
//                    modifier = Modifier.padding(28.dp)
//                ) {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(56.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    brush = Brush.radialGradient(
//                                        colors = listOf(
//                                            Color.White.copy(alpha = 0.25f),
//                                            Color.Cyan.copy(alpha = 0.15f),
//                                            Color.Blue.copy(alpha = 0.05f)
//                                        )
//                                    )
//                                )
//                                .blur(0.6.dp), // iOS glass button effect
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Filled.ReportProblem,
//                                contentDescription = null,
//                                tint = Color.Cyan.copy(alpha = 0.9f),
//                                modifier = Modifier.size(28.dp)
//                            )
//                        }
//
//                        Spacer(modifier = Modifier.width(20.dp))
//
//                        Column {
//                            Text(
//                                text = "Submit Your Complaint",
//                                style = MaterialTheme.typography.titleLarge.copy(
//                                    fontWeight = FontWeight.Bold,
//                                    fontSize = 24.sp
//                                ),
//                                color = Color.White.copy(alpha = 0.95f)
//                            )
//
//                            Text(
//                                text = "We value your feedback and commitment to excellence",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = Color.White.copy(alpha = 0.75f)
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EnhancedInputField(
//    value: String,
//    onValueChange: (String) -> Unit,
//    label: String,
//    placeholder: String,
//    leadingIcon: ImageVector,
//    error: String = "",
//    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
//    keyboardActions: KeyboardActions = KeyboardActions.Default,
//    focusRequester: FocusRequester? = null,
//    maxLines: Int = 1,
//    height: Dp = 56.dp,
//    animationDelay: Int
//) {
//    var visible by remember { mutableStateOf(false) }
//    var isFocused by remember { mutableStateOf(false) }
//
//    LaunchedEffect(Unit) {
//        delay(animationDelay.toLong())
//        visible = true
//    }
//
//    AnimatedVisibility(
//        visible = visible,
//        enter = slideInHorizontally(
//            initialOffsetX = { -it },
//            animationSpec = tween(500, easing = FastOutSlowInEasing)
//        ) + fadeIn(tween(500))
//    ) {
//        Column {
//            OutlinedTextField(
//                value = value,
//                onValueChange = onValueChange,
//                label = {
//                    Text(
//                        label,
//                        color = if (error.isNotEmpty()) Color.Red.copy(alpha = 0.8f)
//                        else if (isFocused) Color.Cyan.copy(alpha = 0.9f)
//                        else Color.White.copy(alpha = 0.7f)
//                    )
//                },
//                placeholder = {
//                    Text(
//                        placeholder,
//                        color = Color.White.copy(alpha = 0.5f)
//                    )
//                },
//                leadingIcon = {
//                    Icon(
//                        leadingIcon,
//                        contentDescription = null,
//                        tint = if (error.isNotEmpty()) Color.Red.copy(alpha = 0.8f)
//                        else if (isFocused) Color.Cyan.copy(alpha = 0.9f)
//                        else Color.White.copy(alpha = 0.7f)
//                    )
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(height)
//                    .apply { focusRequester?.let { focusRequester(it) } }
//                    .onFocusChanged { isFocused = it.isFocused }
//                    .blur(0.8.dp) // iOS-like blur
//                    .background(
//                        brush = Brush.linearGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.12f),
//                                Color.White.copy(alpha = 0.08f)
//                            )
//                        ),
//                        shape = RoundedCornerShape(18.dp)
//                    ),
//                shape = RoundedCornerShape(18.dp),
////                colors = OutlinedTextFieldDefaults.colors(
////                    focusedBorderColor = if (error.isNotEmpty()) Color.Red.copy(alpha = 0.8f)
////                    else Color.Cyan.copy(alpha = 0.8f),
////                    unfocusedBorderColor = if (error.isNotEmpty()) Color.Red.copy(alpha = 0.5f)
////                    else Color.White.copy(alpha = 0.3f),
////                    focusedLabelColor = if (error.isNotEmpty()) Color.Red.copy(alpha = 0.8f)
////                    else Color.Cyan.copy(alpha = 0.9f),
////                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
////                    focusedTextColor = Color.White.copy(alpha = 0.95f),
////                    unfocusedTextColor = Color.White.copy(alpha = 0.9f),
////                    containerColor = Color.Transparent, // iOS glass effect
////                    cursorColor = Color.Cyan.copy(alpha = 0.9f)
////                ),
//                keyboardOptions = keyboardOptions,
//                keyboardActions = keyboardActions,
//                maxLines = maxLines,
//                isError = error.isNotEmpty()
//            )
//
//            AnimatedVisibility(
//                visible = error.isNotEmpty(),
//                enter = slideInVertically(tween(300)) + fadeIn(tween(300)),
//                exit = slideOutVertically(tween(300)) + fadeOut(tween(300))
//            ) {
//                Row(
//                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        imageVector = Icons.Filled.Error,
//                        contentDescription = null,
//                        tint = Color.Red.copy(alpha = 0.8f),
//                        modifier = Modifier.size(16.dp)
//                    )
//                    Spacer(modifier = Modifier.width(4.dp))
//                    Text(
//                        text = error,
//                        color = Color.Red.copy(alpha = 0.8f),
//                        style = MaterialTheme.typography.bodySmall
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedSectionCard(
//    title: String,
//    subtitle: String,
//    icon: ImageVector,
//    color: Color,
//    isExpanded: Boolean,
//    onToggle: () -> Unit,
//    animationDelay: Int,
//    content: @Composable () -> Unit
//) {
//    var visible by remember { mutableStateOf(false) }
//
//    LaunchedEffect(Unit) {
//        delay(animationDelay.toLong())
//        visible = true
//    }
//
//    AnimatedVisibility(
//        visible = visible,
//        enter = slideInVertically(
//            initialOffsetY = { it / 2 },
//            animationSpec = tween(500, easing = FastOutSlowInEasing)
//        ) + fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.9f)
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .clickable { onToggle() }
//                .shadow(
//                    elevation = if (isExpanded) 20.dp else 12.dp,
//                    shape = RoundedCornerShape(24.dp),
//                    ambientColor = color.copy(alpha = 0.4f)
//                )
//                .blur(1.0.dp) // Enhanced iOS blur
//                .background(
//                    brush = Brush.linearGradient(
//                        colors = listOf(
//                            Color.White.copy(alpha = 0.15f),
//                            Color.White.copy(alpha = 0.08f)
//                        )
//                    ),
//                    shape = RoundedCornerShape(24.dp)
//                ),
//            shape = RoundedCornerShape(24.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = Color.Transparent // iOS glass effect
//            ),
//            border = BorderStroke(
//                width = 1.5.dp,
//                brush = Brush.linearGradient(
//                    colors = listOf(
//                        Color.White.copy(alpha = if (isExpanded) 0.6f else 0.3f),
//                        color.copy(alpha = if (isExpanded) 0.8f else 0.5f),
//                        Color.White.copy(alpha = if (isExpanded) 0.4f else 0.2f)
//                    )
//                )
//            )
//        ) {
//            Column {
//                // Header section with iOS glass effect
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .background(
//                            brush = Brush.verticalGradient(
//                                colors = listOf(
//                                    Color.White.copy(alpha = 0.12f),
//                                    Color.White.copy(alpha = 0.06f)
//                                )
//                            )
//                        )
//                        .blur(0.6.dp)
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(24.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(48.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    brush = Brush.radialGradient(
//                                        colors = listOf(
//                                            Color.White.copy(alpha = 0.25f),
//                                            color.copy(alpha = 0.2f),
//                                            Color.White.copy(alpha = 0.1f)
//                                        )
//                                    )
//                                )
//                                .blur(0.4.dp), // iOS glass button
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = icon,
//                                contentDescription = null,
//                                tint = color.copy(alpha = 0.9f),
//                                modifier = Modifier.size(24.dp)
//                            )
//                        }
//
//                        Spacer(modifier = Modifier.width(20.dp))
//
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(
//                                text = title,
//                                style = MaterialTheme.typography.titleMedium.copy(
//                                    fontWeight = FontWeight.Bold,
//                                    fontSize = 18.sp
//                                ),
//                                color = Color.White.copy(alpha = 0.95f)
//                            )
//                            Text(
//                                text = subtitle,
//                                style = MaterialTheme.typography.bodySmall,
//                                color = Color.White.copy(alpha = 0.7f)
//                            )
//                        }
//
//                        // Animated arrow with iOS style
//                        val rotationAngle by animateFloatAsState(
//                            targetValue = if (isExpanded) 180f else 0f,
//                            animationSpec = spring(
//                                dampingRatio = Spring.DampingRatioMediumBouncy,
//                                stiffness = Spring.StiffnessLow
//                            ),
//                            label = "arrow_rotation"
//                        )
//
//                        Box(
//                            modifier = Modifier
//                                .size(32.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    brush = Brush.radialGradient(
//                                        colors = listOf(
//                                            Color.White.copy(alpha = 0.2f),
//                                            Color.White.copy(alpha = 0.05f)
//                                        )
//                                    )
//                                )
//                                .blur(0.3.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Filled.KeyboardArrowDown,
//                                contentDescription = if (isExpanded) "Collapse" else "Expand",
//                                tint = color.copy(alpha = 0.8f),
//                                modifier = Modifier.rotate(rotationAngle)
//                            )
//                        }
//                    }
//                }
//
//                // Expandable content with enhanced iOS blur
//                AnimatedVisibility(
//                    visible = isExpanded,
//                    enter = slideInVertically(
//                        initialOffsetY = { -it / 2 },
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioLowBouncy,
//                            stiffness = Spring.StiffnessMedium
//                        )
//                    ) + fadeIn(
//                        animationSpec = tween(400, easing = EaseOutCubic)
//                    ) + expandVertically(
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioLowBouncy
//                        )
//                    ),
//                    exit = slideOutVertically(
//                        targetOffsetY = { -it / 2 },
//                        animationSpec = tween(300, easing = FastOutSlowInEasing)
//                    ) + fadeOut(tween(300)) + shrinkVertically(tween(300))
//                ) {
//                    Column(
//                        modifier = Modifier
//                            .background(
//                                brush = Brush.verticalGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.08f),
//                                        Color.White.copy(alpha = 0.04f)
//                                    )
//                                )
//                            )
//                            .padding(24.dp)
//                            .blur(0.4.dp) // Content area blur
//                    ) {
//                        HorizontalDivider(
//                            color = Color.White.copy(alpha = 0.2f),
//                            thickness = 1.dp,
//                            modifier = Modifier.padding(bottom = 20.dp)
//                        )
//                        content()
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedCategorySelector(
//    categories: List<CategoryItem>,
//    selectedCategory: String,
//    onCategorySelected: (String) -> Unit
//) {
//    LazyColumn(
//        verticalArrangement = Arrangement.spacedBy(16.dp)
//    ) {
//        itemsIndexed(categories.chunked(2)) { _, rowCategories ->
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                rowCategories.forEach { category ->
//                    val isSelected = category.name == selectedCategory
//                    val scale by animateFloatAsState(
//                        targetValue = if (isSelected) 1.08f else 1f,
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                            stiffness = Spring.StiffnessMedium
//                        ),
//                        label = "category_scale"
//                    )
//
//                    Card(
//                        modifier = Modifier
//                            .weight(1f)
//                            .clickable { onCategorySelected(category.name) }
//                            .scale(scale)
//                            .shadow(
//                                elevation = if (isSelected) 16.dp else 8.dp,
//                                shape = RoundedCornerShape(20.dp),
//                                ambientColor = category.color.copy(alpha = 0.4f)
//                            )
//                            .blur(0.8.dp)
//                            .background(
//                                brush = Brush.linearGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.15f),
//                                        Color.White.copy(alpha = 0.08f)
//                                    )
//                                ),
//                                shape = RoundedCornerShape(20.dp)
//                            ),
//                        shape = RoundedCornerShape(20.dp),
//                        colors = CardDefaults.cardColors(
//                            containerColor = if (isSelected)
//                                category.color.copy(alpha = 0.15f)
//                            else
//                                Color.Transparent
//                        ),
//                        border = BorderStroke(
//                            width = if (isSelected) 2.dp else 1.dp,
//                            brush = if (isSelected)
//                                Brush.linearGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.6f),
//                                        category.color.copy(alpha = 0.8f)
//                                    )
//                                )
//                            else
//                                Brush.linearGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.3f),
//                                        category.color.copy(alpha = 0.4f)
//                                    )
//                                )
//                        )
//                    ) {
//                        Column(
//                            modifier = Modifier.padding(20.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .size(40.dp)
//                                    .clip(CircleShape)
//                                    .background(
//                                        brush = Brush.radialGradient(
//                                            colors = listOf(
//                                                Color.White.copy(alpha = 0.25f),
//                                                category.color.copy(alpha = 0.2f),
//                                                Color.White.copy(alpha = 0.1f)
//                                            )
//                                        )
//                                    )
//                                    .blur(0.4.dp),
//                                contentAlignment = Alignment.Center
//                            ) {
//                                Icon(
//                                    imageVector = category.icon,
//                                    contentDescription = null,
//                                    tint = category.color.copy(alpha = 0.9f),
//                                    modifier = Modifier.size(22.dp)
//                                )
//                            }
//
//                            Spacer(modifier = Modifier.height(12.dp))
//
//                            Text(
//                                text = category.name,
//                                style = MaterialTheme.typography.bodyMedium.copy(
//                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
//                                    fontSize = 14.sp
//                                ),
//                                color = if (isSelected)
//                                    category.color.copy(alpha = 0.95f)
//                                else
//                                    Color.White.copy(alpha = 0.85f)
//                            )
//                        }
//                    }
//                }
//                if (rowCategories.size == 1) {
//                    Spacer(modifier = Modifier.weight(1f))
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedUrgencySelector(
//    urgencyLevels: List<UrgencyItem>,
//    selectedUrgency: String,
//    onUrgencySelected: (String) -> Unit
//) {
//    Column(
//        verticalArrangement = Arrangement.spacedBy(16.dp)
//    ) {
//        urgencyLevels.forEach { urgency ->
//            val isSelected = urgency.name == selectedUrgency
//            val scale by animateFloatAsState(
//                targetValue = if (isSelected) 1.03f else 1f,
//                animationSpec = spring(
//                    dampingRatio = Spring.DampingRatioMediumBouncy,
//                    stiffness = Spring.StiffnessMedium
//                ),
//                label = "urgency_scale"
//            )
//
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .clickable { onUrgencySelected(urgency.name) }
//                    .scale(scale)
//                    .shadow(
//                        elevation = if (isSelected) 12.dp else 6.dp,
//                        shape = RoundedCornerShape(20.dp),
//                        ambientColor = urgency.color.copy(alpha = 0.4f)
//                    )
//                    .blur(0.6.dp) // iOS glass effect
//                    .background(
//                        brush = Brush.linearGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.12f),
//                                Color.White.copy(alpha = 0.06f)
//                            )
//                        ),
//                        shape = RoundedCornerShape(20.dp)
//                    ),
//                shape = RoundedCornerShape(20.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = if (isSelected)
//                        urgency.color.copy(alpha = 0.12f)
//                    else
//                        Color.Transparent
//                ),
//                border = BorderStroke(
//                    width = if (isSelected) 2.dp else 1.dp,
//                    brush = if (isSelected)
//                        Brush.linearGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.5f),
//                                urgency.color.copy(alpha = 0.8f)
//                            )
//                        )
//                    else
//                        Brush.linearGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.2f),
//                                urgency.color.copy(alpha = 0.4f)
//                            )
//                        )
//                )
//            ) {
//                Row(
//                    modifier = Modifier.padding(20.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .size(36.dp)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.radialGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.25f),
//                                        urgency.color.copy(alpha = 0.2f),
//                                        Color.White.copy(alpha = 0.1f)
//                                    )
//                                )
//                            )
//                            .blur(0.3.dp), // iOS glass button
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            imageVector = urgency.icon,
//                            contentDescription = null,
//                            tint = urgency.color.copy(alpha = 0.9f),
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.width(20.dp))
//
//                    Text(
//                        text = urgency.name,
//                        style = MaterialTheme.typography.bodyLarge.copy(
//                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
//                            fontSize = 16.sp
//                        ),
//                        color = if (isSelected)
//                            urgency.color.copy(alpha = 0.95f)
//                        else
//                            Color.White.copy(alpha = 0.85f),
//                        modifier = Modifier.weight(1f)
//                    )
//
//                    if (isSelected) {
//                        Box(
//                            modifier = Modifier
//                                .size(28.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    brush = Brush.radialGradient(
//                                        colors = listOf(
//                                            urgency.color.copy(alpha = 0.3f),
//                                            urgency.color.copy(alpha = 0.1f)
//                                        )
//                                    )
//                                )
//                                .blur(0.2.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Filled.CheckCircle,
//                                contentDescription = null,
//                                tint = urgency.color.copy(alpha = 0.9f),
//                                modifier = Modifier.size(20.dp)
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
//fun EnhancedAttachmentCard(
//    hasAttachment: Boolean,
//    onToggleAttachment: () -> Unit
//) {
//    val scale by animateFloatAsState(
//        targetValue = if (hasAttachment) 1.03f else 1f,
//        animationSpec = spring(
//            dampingRatio = Spring.DampingRatioMediumBouncy,
//            stiffness = Spring.StiffnessMedium
//        ),
//        label = "attachment_scale"
//    )
//
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clickable { onToggleAttachment() }
//            .scale(scale)
//            .shadow(
//                elevation = if (hasAttachment) 12.dp else 8.dp,
//                shape = RoundedCornerShape(20.dp),
//                ambientColor = if (hasAttachment)
//                    Color.Green.copy(alpha = 0.4f)
//                else
//                    Color.Gray.copy(alpha = 0.3f)
//            )
//            .blur(0.6.dp)
//            .background(
//                brush = Brush.linearGradient(
//                    colors = listOf(
//                        Color.White.copy(alpha = 0.12f),
//                        Color.White.copy(alpha = 0.06f)
//                    )
//                ),
//                shape = RoundedCornerShape(20.dp)
//            ),
//        shape = RoundedCornerShape(20.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = if (hasAttachment)
//                Color.Green.copy(alpha = 0.1f)
//            else
//                Color.Transparent
//        ),
//        border = BorderStroke(
//            width = if (hasAttachment) 2.dp else 1.dp,
//            brush = if (hasAttachment)
//                Brush.linearGradient(
//                    colors = listOf(
//                        Color.White.copy(alpha = 0.5f),
//                        Color.Green.copy(alpha = 0.8f)
//                    )
//                )
//            else
//                Brush.linearGradient(
//                    colors = listOf(
//                        Color.White.copy(alpha = 0.2f),
//                        Color.Gray.copy(alpha = 0.4f)
//                    )
//                )
//        )
//    ) {
//        Row(
//            modifier = Modifier.padding(20.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Box(
//                modifier = Modifier
//                    .size(36.dp)
//                    .clip(CircleShape)
//                    .background(
//                        brush = Brush.radialGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.25f),
//                                if (hasAttachment) Color.Green.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
//                                Color.White.copy(alpha = 0.1f)
//                            )
//                        )
//                    )
//                    .blur(0.3.dp),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    imageVector = if (hasAttachment) Icons.Filled.CheckCircle else Icons.Filled.AttachFile,
//                    contentDescription = null,
//                    tint = if (hasAttachment) Color.Green.copy(alpha = 0.9f) else Color.Gray.copy(alpha = 0.7f),
//                    modifier = Modifier.size(20.dp)
//                )
//            }
//
//            Spacer(modifier = Modifier.width(20.dp))
//
//            Column(modifier = Modifier.weight(1f)) {
//                Text(
//                    text = if (hasAttachment) "Attachment Added" else "Add Attachment",
//                    style = MaterialTheme.typography.bodyLarge.copy(
//                        fontWeight = if (hasAttachment) FontWeight.Bold else FontWeight.Medium,
//                        fontSize = 16.sp
//                    ),
//                    color = if (hasAttachment)
//                        Color.Green.copy(alpha = 0.95f)
//                    else
//                        Color.White.copy(alpha = 0.85f)
//                )
//                Text(
//                    text = if (hasAttachment) "File ready for upload" else "Tap to add files or images",
//                    style = MaterialTheme.typography.bodySmall,
//                    color = if (hasAttachment)
//                        Color.Green.copy(alpha = 0.7f)
//                    else
//                        Color.White.copy(alpha = 0.6f)
//                )
//            }
//
//            if (hasAttachment) {
//                Box(
//                    modifier = Modifier
//                        .size(28.dp)
//                        .clip(CircleShape)
//                        .background(
//                            brush = Brush.radialGradient(
//                                colors = listOf(
//                                    Color.Green.copy(alpha = 0.3f),
//                                    Color.Green.copy(alpha = 0.1f)
//                                )
//                            )
//                        )
//                        .blur(0.2.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Icon(
//                        imageVector = Icons.Filled.Done,
//                        contentDescription = null,
//                        tint = Color.Green.copy(alpha = 0.9f),
//                        modifier = Modifier.size(16.dp)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedActionButtons(
//    onReset: () -> Unit,
//    onSubmit: () -> Unit,
//    animationDelay: Int
//) {
//    var visible by remember { mutableStateOf(false) }
//
//    LaunchedEffect(Unit) {
//        delay(animationDelay.toLong())
//        visible = true
//    }
//
//    AnimatedVisibility(
//        visible = visible,
//        enter = slideInVertically(
//            initialOffsetY = { it },
//            animationSpec = tween(600, easing = FastOutSlowInEasing)
//        ) + fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.8f)
//    ) {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            // Reset Button
//            Card(
//                modifier = Modifier
//                    .weight(1f)
//                    .clickable { onReset() }
//                    .shadow(
//                        elevation = 12.dp,
//                        shape = RoundedCornerShape(24.dp),
//                        ambientColor = Color.Red.copy(alpha = 0.3f)
//                    )
//                    .blur(0.6.dp)
//                    .background(
//                        brush = Brush.linearGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.12f),
//                                Color.White.copy(alpha = 0.06f)
//                            )
//                        ),
//                        shape = RoundedCornerShape(24.dp)
//                    ),
//                shape = RoundedCornerShape(24.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = Color.Transparent
//                ),
//                border = BorderStroke(
//                    width = 1.5.dp,
//                    brush = Brush.linearGradient(
//                        colors = listOf(
//                            Color.White.copy(alpha = 0.3f),
//                            Color.Red.copy(alpha = 0.5f)
//                        )
//                    )
//                )
//            ) {
//                Row(
//                    modifier = Modifier.padding(20.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .size(32.dp)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.radialGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.25f),
//                                        Color.Red.copy(alpha = 0.15f)
//                                    )
//                                )
//                            )
//                            .blur(0.3.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            imageVector = Icons.Filled.Refresh,
//                            contentDescription = null,
//                            tint = Color.Red.copy(alpha = 0.8f),
//                            modifier = Modifier.size(18.dp)
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.width(12.dp))
//
//                    Text(
//                        text = "Reset",
//                        style = MaterialTheme.typography.bodyLarge.copy(
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 16.sp
//                        ),
//                        color = Color.Red.copy(alpha = 0.9f)
//                    )
//                }
//            }
//
//            // Submit Button
//            Card(
//                modifier = Modifier
//                    .weight(1f)
//                    .clickable { onSubmit() }
//                    .shadow(
//                        elevation = 16.dp,
//                        shape = RoundedCornerShape(24.dp),
//                        ambientColor = Color.Cyan.copy(alpha = 0.4f)
//                    )
//                    .blur(0.6.dp)
//                    .background(
//                        brush = Brush.linearGradient(
//                            colors = listOf(
//                                Color.Cyan.copy(alpha = 0.2f),
//                                Color.Blue.copy(alpha = 0.15f)
//                            )
//                        ),
//                        shape = RoundedCornerShape(24.dp)
//                    ),
//                shape = RoundedCornerShape(24.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = Color.Transparent
//                ),
//                border = BorderStroke(
//                    width = 2.dp,
//                    brush = Brush.linearGradient(
//                        colors = listOf(
//                            Color.White.copy(alpha = 0.5f),
//                            Color.Cyan.copy(alpha = 0.8f)
//                        )
//                    )
//                )
//            ) {
//                Row(
//                    modifier = Modifier.padding(20.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .size(32.dp)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.radialGradient(
//                                    colors = listOf(
//                                        Color.White.copy(alpha = 0.3f),
//                                        Color.Cyan.copy(alpha = 0.2f)
//                                    )
//                                )
//                            )
//                            .blur(0.3.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            imageVector = Icons.Filled.Send,
//                            contentDescription = null,
//                            tint = Color.Cyan.copy(alpha = 0.9f),
//                            modifier = Modifier.size(18.dp)
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.width(12.dp))
//
//                    Text(
//                        text = "Submit",
//                        style = MaterialTheme.typography.bodyLarge.copy(
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 16.sp
//                        ),
//                        color = Color.Cyan.copy(alpha = 0.95f)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun EnhancedConfirmationDialog(
//    title: String,
//    category: String,
//    urgency: String,
//    urgencyColor: Color,
//    onConfirm: () -> Unit,
//    onDismiss: () -> Unit
//) {
//    var showDialog by remember { mutableStateOf(true) }
//
//    if (showDialog) {
//        Dialog(
//            onDismissRequest = {
//                showDialog = false
//                onDismiss()
//            },
//            properties = DialogProperties(
//                dismissOnBackPress = true,
//                dismissOnClickOutside = true
//            )
//        ) {
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
//                    .shadow(
//                        elevation = 24.dp,
//                        shape = RoundedCornerShape(28.dp),
//                        ambientColor = Color.Cyan.copy(alpha = 0.4f)
//                    )
//                    .blur(1.0.dp)
//                    .background(
//                        brush = Brush.verticalGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.15f),
//                                Color.White.copy(alpha = 0.08f)
//                            )
//                        ),
//                        shape = RoundedCornerShape(28.dp)
//                    ),
//                shape = RoundedCornerShape(28.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = Color.Black.copy(alpha = 0.8f)
//                ),
//                border = BorderStroke(
//                    width = 1.5.dp,
//                    brush = Brush.linearGradient(
//                        colors = listOf(
//                            Color.White.copy(alpha = 0.5f),
//                            Color.Cyan.copy(alpha = 0.6f)
//                        )
//                    )
//                )
//            ) {
//                Column(
//                    modifier = Modifier.padding(28.dp)
//                ) {
//                    // Header
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .size(48.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    brush = Brush.radialGradient(
//                                        colors = listOf(
//                                            Color.White.copy(alpha = 0.25f),
//                                            Color.Cyan.copy(alpha = 0.15f)
//                                        )
//                                    )
//                                )
//                                .blur(0.4.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Filled.Help,
//                                contentDescription = null,
//                                tint = Color.Cyan.copy(alpha = 0.9f),
//                                modifier = Modifier.size(24.dp)
//                            )
//                        }
//
//                        Spacer(modifier = Modifier.width(16.dp))
//
//                        Text(
//                            text = "Confirm Submission",
//                            style = MaterialTheme.typography.titleLarge.copy(
//                                fontWeight = FontWeight.Bold,
//                                fontSize = 22.sp
//                            ),
//                            color = Color.White.copy(alpha = 0.95f)
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.height(24.dp))
//
//                    HorizontalDivider(
//                        color = Color.White.copy(alpha = 0.2f),
//                        thickness = 1.dp
//                    )
//
//                    Spacer(modifier = Modifier.height(20.dp))
//
//                    // Details
//                    Column(
//                        verticalArrangement = Arrangement.spacedBy(16.dp)
//                    ) {
//                        Row {
//                            Text(
//                                text = "Title: ",
//                                style = MaterialTheme.typography.bodyMedium.copy(
//                                    fontWeight = FontWeight.Bold
//                                ),
//                                color = Color.White.copy(alpha = 0.8f)
//                            )
//                            Text(
//                                text = title,
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = Color.White.copy(alpha = 0.9f)
//                            )
//                        }
//
//                        Row {
//                            Text(
//                                text = "Category: ",
//                                style = MaterialTheme.typography.bodyMedium.copy(
//                                    fontWeight = FontWeight.Bold
//                                ),
//                                color = Color.White.copy(alpha = 0.8f)
//                            )
//                            Text(
//                                text = category,
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = Color.Cyan.copy(alpha = 0.9f)
//                            )
//                        }
//
//                        Row {
//                            Text(
//                                text = "Urgency: ",
//                                style = MaterialTheme.typography.bodyMedium.copy(
//                                    fontWeight = FontWeight.Bold
//                                ),
//                                color = Color.White.copy(alpha = 0.8f)
//                            )
//                            Text(
//                                text = urgency,
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = urgencyColor.copy(alpha = 0.9f)
//                            )
//                        }
//                    }
//
//                    Spacer(modifier = Modifier.height(32.dp))
//
//                    // Action Buttons
//                    Row(
//                        horizontalArrangement = Arrangement.spacedBy(12.dp)
//                    ) {
//                        // Cancel Button
//                        TextButton(
//                            onClick = {
//                                showDialog = false
//                                onDismiss()
//                            },
//                            modifier = Modifier
//                                .weight(1f)
//                                .background(
//                                    brush = Brush.linearGradient(
//                                        colors = listOf(
//                                            Color.White.copy(alpha = 0.1f),
//                                            Color.White.copy(alpha = 0.05f)
//                                        )
//                                    ),
//                                    shape = RoundedCornerShape(16.dp)
//                                )
//                                .blur(0.3.dp),
//                            shape = RoundedCornerShape(16.dp)
//                        ) {
//                            Text(
//                                text = "Cancel",
//                                color = Color.White.copy(alpha = 0.8f),
//                                style = MaterialTheme.typography.bodyLarge.copy(
//                                    fontWeight = FontWeight.Medium
//                                )
//                            )
//                        }
//
//                        // Confirm Button
//                        Button(
//                            onClick = {
//                                showDialog = false
//                                onConfirm()
//                            },
//                            modifier = Modifier
//                                .weight(1f)
//                                .shadow(
//                                    elevation = 8.dp,
//                                    shape = RoundedCornerShape(16.dp)
//                                )
//                                .blur(0.3.dp),
//                            shape = RoundedCornerShape(16.dp),
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = Color.Cyan.copy(alpha = 0.8f)
//                            )
//                        ) {
//                            Text(
//                                text = "Confirm",
//                                color = Color.White,
//                                style = MaterialTheme.typography.bodyLarge.copy(
//                                    fontWeight = FontWeight.Bold
//                                )
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
//fun EnhancedSuccessOverlay(
//    visible: Boolean,
//    onDismiss: () -> Unit
//) {
//    AnimatedVisibility(
//        visible = visible,
//        enter = fadeIn(animationSpec = tween(800)) + scaleIn(
//            animationSpec = spring(
//                dampingRatio = Spring.DampingRatioMediumBouncy,
//                stiffness = Spring.StiffnessLow
//            ),
//            initialScale = 0.8f
//        ),
//        exit = fadeOut(animationSpec = tween(600)) + scaleOut(
//            animationSpec = tween(400),
//            targetScale = 1.2f
//        )
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(Color.Black.copy(alpha = 0.7f))
//                .clickable { onDismiss() },
//            contentAlignment = Alignment.Center
//        ) {
//            Card(
//                modifier = Modifier
//                    .size(300.dp)
//                    .shadow(
//                        elevation = 32.dp,
//                        shape = CircleShape,
//                        ambientColor = Color.Green.copy(alpha = 0.5f)
//                    )
//                    .blur(1.2.dp)
//                    .background(
//                        brush = Brush.radialGradient(
//                            colors = listOf(
//                                Color.White.copy(alpha = 0.2f),
//                                Color.Green.copy(alpha = 0.15f),
//                                Color.White.copy(alpha = 0.05f)
//                            )
//                        ),
//                        shape = CircleShape
//                    ),
//                shape = CircleShape,
//                colors = CardDefaults.cardColors(
//                    containerColor = Color.Black.copy(alpha = 0.8f)
//                ),
//                border = BorderStroke(
//                    width = 3.dp,
//                    brush = Brush.linearGradient(
//                        colors = listOf(
//                            Color.White.copy(alpha = 0.6f),
//                            Color.Green.copy(alpha = 0.8f)
//                        )
//                    )
//                )
//            ) {
//                Column(
//                    modifier = Modifier.fillMaxSize(),
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    verticalArrangement = Arrangement.Center
//                ) {
//                    // Animated checkmark
//                    val scale by animateFloatAsState(
//                        targetValue = if (visible) 1f else 0f,
//                        animationSpec = spring(
//                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                            stiffness = Spring.StiffnessLow
//                        ),
//                        label = "checkmark_scale"
//                    )
//
//                    Box(
//                        modifier = Modifier
//                            .size(80.dp)
//                            .scale(scale)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.radialGradient(
//                                    colors = listOf(
//                                        Color.Green.copy(alpha = 0.3f),
//                                        Color.Green.copy(alpha = 0.1f)
//                                    )
//                                )
//                            )
//                            .blur(0.6.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            imageVector = Icons.Filled.CheckCircle,
//                            contentDescription = null,
//                            tint = Color.Green.copy(alpha = 0.9f),
//                            modifier = Modifier.size(48.dp)
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.height(24.dp))
//
//                    Text(
//                        text = "Success!",
//                        style = MaterialTheme.typography.headlineMedium.copy(
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 28.sp
//                        ),
//                        color = Color.Green.copy(alpha = 0.95f)
//                    )
//
//                    Spacer(modifier = Modifier.height(12.dp))
//
//                    Text(
//                        text = "Your complaint has been\nsuccessfully submitted",
//                        style = MaterialTheme.typography.bodyLarge,
//                        color = Color.White.copy(alpha = 0.8f),
//                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
//                    )
//                }
//            }
//        }
//    }
//}