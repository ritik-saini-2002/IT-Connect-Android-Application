package com.example.ritik_2.administrator.databasemanager

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ritik_2.administrator.databasemanager.models.DBRecord
import com.example.ritik_2.administrator.databasemanager.models.DBTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    vm          : DatabaseManagerViewModel,
    onShowToast : (String) -> Unit
) {
    val state by vm.state.collectAsState()
    val snack  = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<DBRecord?>(null) }
    var detailTarget by remember { mutableStateOf<DBRecord?>(null) }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let { snack.showSnackbar(it); vm.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar("⚠ $it"); vm.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Database Manager", fontWeight = FontWeight.Bold)
                        Text("PocketBase · ${state.adminCompany}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Tab row ───────────────────────────────────────────────────────
            LazyRow(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DBTab.values()) { tab ->
                    FilterChip(
                        selected  = state.currentTab == tab,
                        onClick   = { vm.switchTab(tab) },
                        label     = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(tabIcon(tab), null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary)
                    )
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = vm::search,
                placeholder   = { Text("Search ${state.currentTab.name.lowercase()}…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { vm.search("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                },
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp)
            )

            // ── Count chip ────────────────────────────────────────────────────
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${state.records.size} records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.isLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }

            // ── Records list ──────────────────────────────────────────────────
            if (state.records.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("No records found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.records, key = { it.id }) { rec ->
                        DBRecordCard(
                            record   = rec,
                            tab      = state.currentTab,
                            onClick  = { detailTarget = rec },
                            onDelete = { deleteTarget = rec }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Delete confirm
    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete Record?") },
            text    = { Text("Permanently delete '${rec.title}'?") },
            confirmButton = {
                Button(onClick = { vm.deleteRecord(rec); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Detail dialog
    detailTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(rec.title, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rec.sub1, style = MaterialTheme.typography.bodySmall)
                    Text(rec.sub2, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    rec.extra.forEach { (k, v) ->
                        if (v.isNotBlank()) Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$k:", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    Text("ID: ${rec.id}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTarget = null }) { Text("Close") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DBRecordCard(
    record   : DBRecord,
    tab      : DBTab,
    onClick  : () -> Unit,
    onDelete : () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon box
            Box(
                Modifier.size(42.dp)
                    .background(tabColor(tab).copy(0.12f),
                        RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tabIcon(tab), null,
                    tint = tabColor(tab), modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(record.title, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.sub1, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.sub2, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.width(8.dp))

            // Badge
            Surface(
                color = tabColor(tab).copy(0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(record.badge,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = tabColor(tab),
                    fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(4.dp))

            if (tab != DBTab.COLLECTIONS) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

private fun tabIcon(tab: DBTab): ImageVector = when (tab) {
    DBTab.USERS       -> Icons.Default.People
    DBTab.DEPARTMENTS -> Icons.Default.AccountTree
    DBTab.COMPANIES   -> Icons.Default.Business
    DBTab.COLLECTIONS -> Icons.Default.Storage
}

private fun tabColor(tab: DBTab): Color = when (tab) {
    DBTab.USERS       -> Color(0xFF2196F3)
    DBTab.DEPARTMENTS -> Color(0xFF4CAF50)
    DBTab.COMPANIES   -> Color(0xFFFF9800)
    DBTab.COLLECTIONS -> Color(0xFF9C27B0)
}