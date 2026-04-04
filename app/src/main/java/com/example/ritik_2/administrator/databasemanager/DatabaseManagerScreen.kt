package com.example.ritik_2.administrator.databasemanager

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ritik_2.administrator.databasemanager.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    vm         : DatabaseManagerViewModel,
    onShowToast: (String) -> Unit
) {
    val state         by vm.state.collectAsState()
    val snack          = remember { SnackbarHostState() }
    var deleteTarget  by remember { mutableStateOf<DBRecord?>(null) }
    var detailTarget  by remember { mutableStateOf<DBRecord?>(null) }
    var rulesTarget   by remember { mutableStateOf<DBRecord?>(null) }
    var indexTarget   by remember { mutableStateOf<DBRecord?>(null) }
    var showCreateCol by remember { mutableStateOf(false) }

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
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("PocketBase · ${state.adminCompany}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            if (state.isOffline)
                                Surface(color = Color(0xFFE57373).copy(0.15f),
                                    shape = RoundedCornerShape(6.dp)) {
                                    Text("Offline", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                }
                        }
                    }
                },
                actions = {
                    if (state.currentTab == DBTab.COLLECTIONS) {
                        IconButton(onClick = { showCreateCol = true }) {
                            Icon(Icons.Default.Add, "Create collection",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        selected    = state.currentTab == tab,
                        onClick     = { vm.switchTab(tab) },
                        label       = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = { Icon(tabIcon(tab), null, Modifier.size(16.dp)) },
                        colors      = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = MaterialTheme.colorScheme.primary,
                            selectedLabelColor       = MaterialTheme.colorScheme.onPrimary,
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
                        IconButton(onClick = { vm.search("") }) { Icon(Icons.Default.Clear, null) }
                },
                modifier   = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                shape      = RoundedCornerShape(12.dp)
            )

            // ── Count + loading ───────────────────────────────────────────────
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
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
                Box(Modifier.fillMaxSize(), Alignment.Center) {
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
                            onDelete = { deleteTarget = rec },
                            onEditRules = if (state.currentTab == DBTab.COLLECTIONS)
                                ({ rulesTarget = rec }) else null,
                            onAddIndex  = if (state.currentTab == DBTab.COLLECTIONS)
                                ({ indexTarget = rec }) else null
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
            text    = { Text("Permanently delete '${rec.title}'? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { vm.deleteRecord(rec); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // Detail dialog — shows all extra fields
    detailTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(rec.title, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(rec.sub1, style = MaterialTheme.typography.bodySmall)
                    Text(rec.sub2, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    rec.extra.forEach { (k, v) ->
                        if (v.isNotBlank()) Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$k:", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.4f))
                            Text(v, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("ID: ${rec.id}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            },
            confirmButton = { TextButton(onClick = { detailTarget = null }) { Text("Close") } }
        )
    }

    // Edit rules dialog
    rulesTarget?.let { rec ->
        EditRulesDialog(
            current  = CollectionRules(
                listRule   = rec.extra["List Rule"]?.takeIf { it != "null (open)" } ?: "",
                viewRule   = rec.extra["View Rule"]?.takeIf { it != "null (open)" } ?: "",
                createRule = rec.extra["Create Rule"]?.takeIf { it != "null (open)" } ?: "",
                updateRule = rec.extra["Update Rule"]?.takeIf { it != "null (open)" } ?: "",
                deleteRule = rec.extra["Delete Rule"]?.takeIf { it != "null (open)" } ?: ""
            ),
            collectionName = rec.title,
            onDismiss  = { rulesTarget = null },
            onSave     = { rules ->
                vm.updateCollectionRules(rec.collectionId, rules)
                rulesTarget = null
            }
        )
    }

    // Add index dialog
    indexTarget?.let { rec ->
        AddIndexDialog(
            onDismiss = { indexTarget = null },
            onAdd     = { idx ->
                vm.createIndex(rec.collectionId, idx)
                indexTarget = null
            }
        )
    }

    // Create collection dialog
    if (showCreateCol) {
        CreateCollectionDialog(
            onDismiss = { showCreateCol = false },
            onCreate  = { name, type, fields ->
                vm.createCollection(name, type, fields)
                showCreateCol = false
            }
        )
    }
}

// ── Record card ───────────────────────────────────────────────────────────────

@Composable
private fun DBRecordCard(
    record      : DBRecord,
    tab         : DBTab,
    onClick     : () -> Unit,
    onDelete    : () -> Unit,
    onEditRules : (() -> Unit)?,
    onAddIndex  : (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(tabColor(tab).copy(0.12f),
                    RoundedCornerShape(10.dp)), Alignment.Center) {
                Icon(tabIcon(tab), null, tint = tabColor(tab), modifier = Modifier.size(22.dp))
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
            Surface(color = tabColor(tab).copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(record.badge,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = tabColor(tab), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { showMenu = true }, Modifier.size(30.dp)) {
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("View Details") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick     = { showMenu = false; onClick() }
                    )
                    if (onEditRules != null) {
                        DropdownMenuItem(
                            text        = { Text("Edit API Rules") },
                            leadingIcon = { Icon(Icons.Default.Security, null,
                                tint = Color(0xFF1976D2)) },
                            onClick     = { showMenu = false; onEditRules() }
                        )
                        DropdownMenuItem(
                            text        = { Text("Add Index") },
                            leadingIcon = { Icon(Icons.Default.List, null,
                                tint = Color(0xFF388E3C)) },
                            onClick     = { showMenu = false; onAddIndex?.invoke() }
                        )
                    }
                    if (tab != DBTab.COLLECTIONS) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ── Edit rules dialog ─────────────────────────────────────────────────────────

@Composable
private fun EditRulesDialog(
    current        : CollectionRules,
    collectionName : String,
    onDismiss      : () -> Unit,
    onSave         : (CollectionRules) -> Unit
) {
    var listRule   by remember { mutableStateOf(current.listRule) }
    var viewRule   by remember { mutableStateOf(current.viewRule) }
    var createRule by remember { mutableStateOf(current.createRule) }
    var updateRule by remember { mutableStateOf(current.updateRule) }
    var deleteRule by remember { mutableStateOf(current.deleteRule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Rules · $collectionName", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Leave blank = null (admin only). Use @request.auth.id != '' for authenticated users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                RuleField("List Rule",   listRule)   { listRule   = it }
                RuleField("View Rule",   viewRule)   { viewRule   = it }
                RuleField("Create Rule", createRule) { createRule = it }
                RuleField("Update Rule", updateRule) { updateRule = it }
                RuleField("Delete Rule", deleteRule) { deleteRule = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(CollectionRules(listRule, viewRule, createRule, updateRule, deleteRule))
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RuleField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, fontSize = 12.sp) },
        placeholder   = { Text("null (admin only)", fontSize = 11.sp) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(8.dp)
    )
}

// ── Add index dialog ──────────────────────────────────────────────────────────

@Composable
private fun AddIndexDialog(onDismiss: () -> Unit, onAdd: (DBIndex) -> Unit) {
    var name    by remember { mutableStateOf("") }
    var fields  by remember { mutableStateOf("") }
    var unique  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Index", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Index Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp))
                OutlinedTextField(
                    value = fields, onValueChange = { fields = it },
                    label = { Text("Fields (comma-separated)") },
                    placeholder = { Text("e.g. name, email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unique, onCheckedChange = { unique = it })
                    Text("Unique index")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fieldList = fields.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (name.isNotBlank() && fieldList.isNotEmpty())
                        onAdd(DBIndex(name, if (unique) "unique" else "index", fieldList, unique))
                },
                enabled = name.isNotBlank() && fields.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Create collection dialog ──────────────────────────────────────────────────

@Composable
private fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate : (String, String, List<DBField>) -> Unit
) {
    var colName  by remember { mutableStateOf("") }
    var colType  by remember { mutableStateOf("base") }
    var fields   by remember { mutableStateOf(listOf<DBField>()) }
    var addField by remember { mutableStateOf(false) }
    var newFName by remember { mutableStateOf("") }
    var newFType by remember { mutableStateOf("text") }
    var newFReq  by remember { mutableStateOf(false) }

    val typeOptions = listOf("base", "auth")
    val fldTypes    = listOf("text", "number", "bool", "email", "url", "json", "date", "file", "relation")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Collection", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = colName, onValueChange = { colName = it },
                    label = { Text("Collection Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeOptions.forEach { t ->
                        FilterChip(selected = colType == t, onClick = { colType = t },
                            label = { Text(t) })
                    }
                }

                Text("Fields", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)

                fields.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${f.name} (${f.type})${if (f.required) " *" else ""}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { fields = fields - f }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (addField) {
                    OutlinedTextField(value = newFName, onValueChange = { newFName = it },
                        label = { Text("Field Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(fldTypes) { t ->
                            FilterChip(selected = newFType == t, onClick = { newFType = t },
                                label = { Text(t, fontSize = 11.sp) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newFReq, onCheckedChange = { newFReq = it })
                        Text("Required", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (newFName.isNotBlank()) {
                                fields = fields + DBField(newFName, newFType, newFReq)
                                newFName = ""; newFType = "text"; newFReq = false; addField = false
                            }
                        }) { Text("Add") }
                    }
                } else {
                    OutlinedButton(onClick = { addField = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (colName.isNotBlank()) onCreate(colName, colType, fields) },
                enabled  = colName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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