package com.example.ritik_2.administrator.reports

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel   : ReportsViewModel,
    onBack      : () -> Unit,
    onExport    : (ExportFormat) -> Unit,
    onShareFile : (Uri) -> Unit
) {
    val state        by viewModel.state.collectAsState()
    val snack         = remember { SnackbarHostState() }
    var showExportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar("⚠ $it") }
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(0.8f)
                    )))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Reports & Export", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(state.companyName, fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }
                    // Export button
                    Box {
                        IconButton(onClick = { showExportMenu = true },
                            enabled = !state.isExporting && state.sections.isNotEmpty()) {
                            if (state.isExporting)
                                CircularProgressIndicator(Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.FileDownload, "Export", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded         = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            ExportFormat.values().forEach { fmt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(exportIcon(fmt), null, Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                            Text(fmt.label)
                                        }
                                    },
                                    onClick = { showExportMenu = false; onExport(fmt) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Building reports…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Summary stats ─────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryPill(state.totalUsers.toString(),  "Total Users",  Icons.Default.People,      Color(0xFF1976D2))
                    SummaryPill(state.activeUsers.toString(), "Active",       Icons.Default.CheckCircle, Color(0xFF2E7D32))
                    SummaryPill(state.totalDepts.toString(),  "Departments",  Icons.Default.AccountTree, Color(0xFFF57C00))
                    SummaryPill(state.totalRoles.toString(),  "Roles",        Icons.Default.AdminPanelSettings, Color(0xFF7B1FA2))
                }
            }

            // ── Section tabs ──────────────────────────────────────────────────
            LazyRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.sections) { idx, section ->
                    FilterChip(
                        selected  = state.selectedSection == idx,
                        onClick   = { viewModel.selectSection(idx) },
                        label     = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(section.title)
                                Surface(
                                    color = if (state.selectedSection == idx)
                                        Color.White.copy(0.25f)
                                    else MaterialTheme.colorScheme.primary.copy(0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("${section.rows.size}",
                                        Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 10.sp,
                                        color = if (state.selectedSection == idx)
                                            Color.White
                                        else MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = MaterialTheme.colorScheme.primary,
                            selectedLabelColor       = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Report table ──────────────────────────────────────────────────
            val section = state.sections.getOrNull(state.selectedSection)
            if (section == null || section.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Assessment, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("No data for this report",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Horizontal scroll for wide tables
                val headers = section.rows.first().keys.toList()
                Column(Modifier.fillMaxSize()) {
                    // Sticky header row
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.primary.copy(0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("#", fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.width(28.dp),
                            color = MaterialTheme.colorScheme.primary)
                        headers.forEach { h ->
                            Text(h, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                modifier = Modifier.width(140.dp),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(0.2f))

                    val listScrollState = rememberScrollState()
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(section.rows) { idx, row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(listScrollState)
                                    .background(
                                        if (idx % 2 == 0) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}", fontSize = 11.sp,
                                    modifier = Modifier.width(28.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                headers.forEach { h ->
                                    val v = row[h] ?: "—"
                                    // Color-code status
                                    val color = when {
                                        h == "Status" && v == "Active"   -> Color(0xFF2E7D32)
                                        h == "Status" && v == "Inactive" -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(v, fontSize = 11.sp, modifier = Modifier.width(140.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = color)
                                }
                            }
                            if (idx < section.rows.lastIndex)
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SummaryPill(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun exportIcon(fmt: ExportFormat) = when (fmt) {
    ExportFormat.CSV  -> Icons.Default.GridOn
    ExportFormat.JSON -> Icons.Default.Code
    ExportFormat.TXT  -> Icons.Default.Description
}