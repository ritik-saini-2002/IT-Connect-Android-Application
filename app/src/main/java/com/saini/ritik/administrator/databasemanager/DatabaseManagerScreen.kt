package com.saini.ritik.administrator.databasemanager

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
import androidx.compose.ui.draw.clip                          // ← explicit import, no custom ext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saini.ritik.administrator.databasemanager.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    vm         : DatabaseManagerViewModel,
    onShowToast: (String) -> Unit
) {
    val state        by vm.state.collectAsState()
    val snack         = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<DBRecord?>(null) }
    var detailTarget by remember { mutableStateOf<DBRecord?>(null) }
    var rulesTarget  by remember { mutableStateOf<CollectionFolder?>(null) }
    var indexTarget  by remember { mutableStateOf<CollectionFolder?>(null) }
    var showCreate   by remember { mutableStateOf(false) }

    LaunchedEffect(state.successMsg) {
        state.successMsg?.let { snack.showSnackbar(it); vm.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar("⚠ $it"); vm.clearMessages() }
    }

    // ── Access denied ─────────────────────────────────────────────────────────
    if (state.accessDenied) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Access Denied",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(
                    "You need the 'database_manager' permission or DB admin credentials.",
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Database Manager", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("PocketBase · ${state.adminCompany}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            if (state.isDbAdmin) DbAdminBadge()
                            if (state.isOffline)
                                Surface(color = Color(0xFFE57373).copy(0.15f),
                                    shape = RoundedCornerShape(6.dp)) {
                                    Text("Offline",
                                        Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold)
                                }
                        }
                    }
                },
                actions = {
                    if (state.currentTab == DBTab.COLLECTIONS && state.isDbAdmin) {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, "Create collection",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
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
                        selected    = state.currentTab == tab,
                        onClick     = { vm.switchTab(tab) },
                        label       = {
                            Text(tab.name.lowercase()
                                .replaceFirstChar { it.uppercase() })
                        },
                        leadingIcon = {
                            Icon(tabIcon(tab), null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = MaterialTheme.colorScheme.primary,
                            selectedLabelColor       = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // ── Search ────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = state.searchQuery,
                onValueChange = {
                    if (state.currentTab == DBTab.COLLECTIONS) vm.searchCollections(it)
                    else vm.search(it)
                },
                placeholder   = { Text("Search…") },
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

            Spacer(Modifier.height(4.dp))

            // ── Content ───────────────────────────────────────────────────────
            AnimatedContent(targetState = state.currentTab, label = "dbTab") { tab ->
                when (tab) {
                    DBTab.COLLECTIONS -> CollectionFolderView(
                        folders        = state.collections.filter {
                            state.searchQuery.isBlank() ||
                                    it.name.contains(state.searchQuery, true)
                        },
                        expandedId     = state.expandedCollId,
                        isDbAdmin      = state.isDbAdmin,
                        isLoading      = state.isLoading,
                        onToggleExpand = { vm.toggleCollectionExpand(it) },
                        onEditRules    = { rulesTarget = it },
                        onAddIndex     = { indexTarget = it },
                        onViewRecord   = { detailTarget = it }
                    )
                    else -> RecordListView(
                        records   = state.records,
                        tab       = tab,
                        isLoading = state.isLoading,
                        isDbAdmin = state.isDbAdmin,
                        onClick   = { detailTarget = it },
                        onDelete  = { deleteTarget = it }
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete Record?") },
            text    = { Text("Permanently delete '${rec.title}'? Cannot be undone.") },
            confirmButton = {
                Button(onClick = { vm.deleteRecord(rec); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    detailTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(rec.title, fontWeight = FontWeight.Bold) },
            text  = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rec.sub1, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    if (rec.rawJson.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)) {
                            Text(rec.rawJson,
                                Modifier
                                    .padding(10.dp)
                                    .horizontalScroll(rememberScrollState()),
                                style      = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        rec.extra.forEach { (k, v) ->
                            if (v.isNotBlank()) Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$k:", style = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.4f))
                                Text(v, style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier   = Modifier.weight(0.6f),
                                    maxLines   = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text("ID: ${rec.id}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            },
            confirmButton = { TextButton(onClick = { detailTarget = null }) { Text("Close") } }
        )
    }

    rulesTarget?.let { folder ->
        EditRulesDialog(
            current        = CollectionRules(
                listRule   = folder.listRule,
                viewRule   = folder.viewRule,
                createRule = folder.createRule,
                updateRule = folder.updateRule,
                deleteRule = folder.deleteRule
            ),
            collectionName = folder.name,
            onDismiss      = { rulesTarget = null },
            onSave         = { rules ->
                vm.updateCollectionRules(folder.id, rules)
                rulesTarget = null
            }
        )
    }

    indexTarget?.let { folder ->
        AddIndexDialog(
            onDismiss = { indexTarget = null },
            onAdd     = { idx -> vm.createIndex(folder.id, idx); indexTarget = null }
        )
    }

    if (showCreate) {
        CreateCollectionDialog(
            onDismiss = { showCreate = false },
            onCreate  = { name, type, fields ->
                vm.createCollection(name, type, fields)
                showCreate = false
            }
        )
    }
}

// ── Collection folder view ────────────────────────────────────────────────────

@Composable
private fun CollectionFolderView(
    folders       : List<CollectionFolder>,
    expandedId    : String?,
    isDbAdmin     : Boolean,
    isLoading     : Boolean,
    onToggleExpand: (String) -> Unit,
    onEditRules   : (CollectionFolder) -> Unit,
    onAddIndex    : (CollectionFolder) -> Unit,
    onViewRecord  : (DBRecord) -> Unit
) {
    if (isLoading && folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Storage, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                Spacer(Modifier.height(12.dp))
                Text("No collections found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${folders.size} collections",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        items(folders, key = { it.id }) { folder ->
            CollectionFolderCard(
                folder        = folder,
                isExpanded    = expandedId == folder.id,
                isDbAdmin     = isDbAdmin,
                onToggle      = { onToggleExpand(folder.id) },
                onEditRules   = { onEditRules(folder) },
                onAddIndex    = { onAddIndex(folder) },
                onViewRecord  = onViewRecord
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun CollectionFolderCard(
    folder      : CollectionFolder,
    isExpanded  : Boolean,
    isDbAdmin   : Boolean,
    onToggle    : () -> Unit,
    onEditRules : () -> Unit,
    onAddIndex  : () -> Unit,
    onViewRecord: (DBRecord) -> Unit
) {
    val typeColor = collectionTypeColor(folder.type)

    Card(
        Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (isExpanded) 6.dp else 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    null, tint = typeColor, modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(color = typeColor.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(folder.type,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp, color = typeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip("${folder.fieldCount} fields",  typeColor)
                        MiniChip("${folder.indexCount} indexes", typeColor.copy(0.7f))
                    }
                }
                if (isDbAdmin) {
                    IconButton(onClick = onEditRules, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Security, null,
                            tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onAddIndex, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.List, null,
                            tint = Color(0xFF388E3C), modifier = Modifier.size(16.dp))
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                // API rules
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("API Rules",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        "List"   to folder.listRule,
                        "View"   to folder.viewRule,
                        "Create" to folder.createRule,
                        "Update" to folder.updateRule,
                        "Delete" to folder.deleteRule
                    ).forEach { (label, rule) ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$label:", fontSize = 10.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp))
                            Surface(
                                color    = if (rule.isBlank()) Color(0xFFC62828).copy(0.1f)
                                else Color(0xFF2E7D32).copy(0.1f),
                                shape    = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (rule.isBlank()) "null (admin only)" else rule,
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize   = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color      = if (rule.isBlank()) Color(0xFFC62828)
                                    else Color(0xFF2E7D32),
                                    maxLines   = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Records
                if (folder.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Loading records…", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (folder.records.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Records (${folder.records.size})",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier   = Modifier.padding(bottom = 4.dp))
                        folder.records.take(10).forEach { rec ->
                            RecordRow(rec = rec, onClick = { onViewRecord(rec) })
                        }
                        if (folder.records.size > 10) {
                            Text("… ${folder.records.size - 10} more records",
                                fontSize = 10.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                        }
                    }
                } else if (isDbAdmin) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                        Text("Empty — tap folder to reload",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(rec: DBRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))          // ← uses androidx.compose.ui.draw.clip
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Article, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            modifier = Modifier.size(14.dp))
        Text(rec.title.take(20), fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.weight(1f),
            maxLines   = 1, overflow = TextOverflow.Ellipsis)
        Text(rec.sub1.take(40), fontSize = 10.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
            modifier = Modifier.size(12.dp))
    }
}

// ── Record list (non-collection tabs) ─────────────────────────────────────────

@Composable
private fun RecordListView(
    records  : List<DBRecord>,
    tab      : DBTab,
    isLoading: Boolean,
    isDbAdmin: Boolean,
    onClick  : (DBRecord) -> Unit,
    onDelete : (DBRecord) -> Unit
) {
    if (isLoading && records.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No records", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${records.size} records",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(records, key = { it.id }) { rec ->
            DBRecordCard(rec = rec, tab = tab, isDbAdmin = isDbAdmin,
                onClick = { onClick(rec) }, onDelete = { onDelete(rec) })
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DBRecordCard(
    rec     : DBRecord,
    tab     : DBTab,
    isDbAdmin: Boolean,
    onClick  : () -> Unit,
    onDelete : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))   // ← draw.clip
                    .background(tabColor(tab).copy(0.12f)),
                Alignment.Center
            ) {
                Icon(tabIcon(tab), null, tint = tabColor(tab), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.title, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rec.sub1, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (rec.sub2.isNotBlank())
                    Text(rec.sub2, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = tabColor(tab).copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(rec.badge, Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tabColor(tab), fontWeight = FontWeight.SemiBold)
            }
            if (isDbAdmin) {
                Box {
                    IconButton(onClick = { showMenu = true }, Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("View Details") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick     = { showMenu = false; onClick() })
                        DropdownMenuItem(
                            text = {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

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
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Leave blank = null (admin only). " +
                            "@request.auth.id != '' = authenticated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    Triple("List",   listRule)   { v: String -> listRule   = v },
                    Triple("View",   viewRule)   { v: String -> viewRule   = v },
                    Triple("Create", createRule) { v: String -> createRule = v },
                    Triple("Update", updateRule) { v: String -> updateRule = v },
                    Triple("Delete", deleteRule) { v: String -> deleteRule = v }
                ).forEach { (label, value, setter) ->
                    OutlinedTextField(
                        value         = value,
                        onValueChange = setter,
                        label         = { Text("$label Rule", fontSize = 12.sp) },
                        placeholder   = { Text("null (admin only)", fontSize = 11.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(8.dp)
                    )
                }
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
private fun AddIndexDialog(onDismiss: () -> Unit, onAdd: (DBIndex) -> Unit) {
    var name   by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf("") }
    var unique by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Index", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it },
                    label = { Text("Index Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(fields, { fields = it },
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
                    val fl = fields.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (name.isNotBlank() && fl.isNotEmpty())
                        onAdd(DBIndex(name, if (unique) "unique" else "index", fl, unique))
                },
                enabled = name.isNotBlank() && fields.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
    val fldTypes = listOf("text","number","bool","email","url","json","date","file","relation")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Collection", fontWeight = FontWeight.Bold) },
        text  = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(colName, { colName = it },
                    label = { Text("Collection Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("base","auth").forEach { t ->
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
                            style    = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { fields = fields - f }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (addField) {
                    OutlinedTextField(newFName, { newFName = it },
                        label = { Text("Field Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(fldTypes) { t ->
                            FilterChip(selected = newFType == t, onClick = { newFType = t },
                                label = { Text(t, fontSize = 11.sp) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(newFReq, { newFReq = it })
                        Text("Required", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (newFName.isNotBlank()) {
                                fields   = fields + DBField(newFName, newFType, newFReq)
                                newFName = ""; newFType = "text"; newFReq = false
                                addField = false
                            }
                        }) { Text("Add") }
                    }
                } else {
                    OutlinedButton(onClick = { addField = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Add Field")
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

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun DbAdminBadge() {
    Surface(color = Color(0xFFFFD700).copy(0.2f), shape = RoundedCornerShape(6.dp)) {
        Text("DB Admin",
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 9.sp, color = Color(0xFFB8860B), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(label,
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun collectionTypeColor(type: String): Color = when (type) {
    "auth" -> Color(0xFF1976D2)
    "view" -> Color(0xFF7B1FA2)
    else   -> Color(0xFF388E3C)
}

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