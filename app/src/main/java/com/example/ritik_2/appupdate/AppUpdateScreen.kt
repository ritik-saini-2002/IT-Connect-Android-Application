package com.example.ritik_2.appupdate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val Purple700 = Color(0xFF6200EA)
private val Purple500 = Color(0xFF9C27B0)
private val Teal500   = Color(0xFF009688)
private val Amber600  = Color(0xFFFFB300)
private val Red600    = Color(0xFFE53935)
private val Green600  = Color(0xFF43A047)
private val Surface2  = Color(0xFF1E1E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    viewModel  : AppUpdateViewModel,
    canActivate: Boolean,
    onBack     : () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showUploadSheet by remember { mutableStateOf(false) }
    var deleteTarget    by remember { mutableStateOf<AppUpdateRecord?>(null) }

    // APK file picker
    val apkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)
                ?.use { c -> c.moveToFirst(); c.getString(c.getColumnIndexOrThrow("_display_name")) }
                ?: "app-release.apk"
            viewModel.onApkSelected(it, name)
        }
    }

    // Messages
    state.error?.let { msg ->
        LaunchedEffect(msg) {
            delay(4000)
            viewModel.clearMessages()
        }
    }
    state.successMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Update Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp)
                        Text("Push updates to all user devices",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRecords() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    if (canActivate) {
                        FloatingActionButton(
                            onClick = { showUploadSheet = true; viewModel.resetForm() },
                            modifier = Modifier.padding(end = 8.dp).size(40.dp),
                            containerColor = Purple700,
                            contentColor = Color.White,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Upload", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(Modifier.fillMaxSize().padding(padding)) {

            // ── Status snackbar strip ─────────────────────────────────────
            Column(Modifier.fillMaxSize()) {

                AnimatedVisibility(visible = state.error != null) {
                    state.error?.let { msg ->
                        Surface(
                            color = Red600,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = state.successMessage != null) {
                    state.successMessage?.let { msg ->
                        Surface(
                            color = Green600,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Stats strip ──────────────────────────────────────────
                if (state.records.isNotEmpty()) {
                    val activeCount = state.records.count { it.isActive }
                    val totalCount  = state.records.size
                    val latestCode  = state.records.maxOfOrNull { it.versionCode } ?: 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatChip("$totalCount Records",  Icons.Outlined.Inventory2,    Purple700, Modifier.weight(1f))
                        StatChip("$activeCount Active",  Icons.Outlined.CheckCircle,   Green600,  Modifier.weight(1f))
                        StatChip("v$latestCode Latest",  Icons.Outlined.NewReleases,   Amber600,  Modifier.weight(1f))
                    }
                }

                // ── Loading ───────────────────────────────────────────────
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Purple700
                    )
                }

                // ── Records list ──────────────────────────────────────────
                if (!state.isLoading && state.records.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text("No update records yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                            if (canActivate)
                                Text("Tap + to upload the first update",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.records, key = { it.id }) { record ->
                            UpdateRecordCard(
                                record      = record,
                                canActivate = canActivate,
                                onActivate  = { viewModel.setActive(record.id, true) },
                                onDeactivate = { viewModel.setActive(record.id, false) },
                                onDelete    = { deleteTarget = record }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // ── Upload bottom sheet ───────────────────────────────────────────────────
    if (showUploadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showUploadSheet = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = MaterialTheme.colorScheme.surface
        ) {
            UploadFormContent(
                state      = state,
                onVersionCodeChange  = viewModel::onVersionCodeChange,
                onVersionNameChange  = viewModel::onVersionNameChange,
                onReleaseNotesChange = viewModel::onReleaseNotesChange,
                onIsActiveChange     = viewModel::onIsActiveChange,
                onPickApk   = { apkLauncher.launch("application/vnd.android.package-archive") },
                onSubmit    = {
                    viewModel.uploadUpdate()
                    if (state.error == null) showUploadSheet = false
                },
                onDismiss   = { showUploadSheet = false }
            )
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = Red600) },
            title = { Text("Delete Update Record?") },
            text  = {
                Text("Version ${target.versionName} (code ${target.versionCode}) will be permanently deleted. " +
                        "Devices that haven't updated yet will no longer receive this version.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteRecord(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Record card ───────────────────────────────────────────────────────────────

@Composable
private fun UpdateRecordCard(
    record      : AppUpdateRecord,
    canActivate : Boolean,
    onActivate  : () -> Unit,
    onDeactivate: () -> Unit,
    onDelete    : () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isActive = record.isActive

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                cs.surface
            else
                cs.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = if (isActive) BorderStroke(
            2.dp,
            Brush.horizontalGradient(listOf(Purple700, Teal500))
        ) else null
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Version badge
                Surface(
                    color = if (isActive) Purple700 else cs.onSurface.copy(0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "v${record.versionName}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else cs.onSurface.copy(0.7f)
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        "Build ${record.versionCode}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Uploaded ${record.created}",
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant
                    )
                }

                // Active badge
                if (isActive) {
                    Surface(
                        color = Green600.copy(0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Green600)
                            )
                            Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green600)
                        }
                    }
                }
            }

            // APK file name
            if (record.apkFileName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Android, null,
                        modifier = Modifier.size(14.dp),
                        tint = cs.onSurfaceVariant)
                    Text(
                        record.apkFileName,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Release notes
            if (record.releaseNotes.isNotBlank()) {
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        record.releaseNotes,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions (only for canActivate users)
            if (canActivate) {
                HorizontalDivider(color = cs.outlineVariant.copy(0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isActive) {
                        TextButton(onClick = onActivate) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = Green600)
                            Spacer(Modifier.width(4.dp))
                            Text("Activate", color = Green600, fontSize = 13.sp)
                        }
                    } else {
                        TextButton(onClick = onDeactivate) {
                            Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(16.dp), tint = Amber600)
                            Spacer(Modifier.width(4.dp))
                            Text("Deactivate", color = Amber600, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = Red600)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = Red600, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Upload form ───────────────────────────────────────────────────────────────

@Composable
private fun UploadFormContent(
    state               : AppUpdateUiState,
    onVersionCodeChange : (String) -> Unit,
    onVersionNameChange : (String) -> Unit,
    onReleaseNotesChange: (String) -> Unit,
    onIsActiveChange    : (Boolean) -> Unit,
    onPickApk           : () -> Unit,
    onSubmit            : () -> Unit,
    onDismiss           : () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Upload New Version", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Push update to all connected devices",
                    fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = cs.onSurfaceVariant)
            }
        }

        HorizontalDivider(color = cs.outlineVariant.copy(0.5f))

        // Version fields row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.formVersionCode,
                onValueChange = onVersionCodeChange,
                label = { Text("Version Code *") },
                placeholder = { Text("e.g. 12") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Numbers, null, modifier = Modifier.size(18.dp)) }
            )
            OutlinedTextField(
                value = state.formVersionName,
                onValueChange = onVersionNameChange,
                label = { Text("Version Name *") },
                placeholder = { Text("e.g. 1.2.0") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Outlined.Label, null, modifier = Modifier.size(18.dp)) }
            )
        }

        // APK file picker
        Surface(
            onClick = onPickApk,
            color = if (state.formApkUri != null)
                Purple700.copy(0.1f)
            else
                cs.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (state.formApkUri != null) Purple700.copy(0.5f) else cs.outline.copy(0.4f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (state.formApkUri != null) Icons.Default.CheckCircle else Icons.Outlined.FileUpload,
                    null,
                    tint = if (state.formApkUri != null) Purple700 else cs.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.formApkUri != null) state.formApkName else "Select APK File *",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (state.formApkUri != null) cs.onSurface else cs.onSurfaceVariant
                    )
                    Text(
                        if (state.formApkUri != null) "Tap to change" else "Signed release APK only",
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant.copy(0.7f)
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
        }

        // Release notes
        OutlinedTextField(
            value = state.formReleaseNotes,
            onValueChange = onReleaseNotesChange,
            label = { Text("Release Notes") },
            placeholder = { Text("What's new in this version?") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Outlined.Notes, null,
                    modifier = Modifier.size(18.dp).padding(top = 0.dp))
            }
        )

        // Activate toggle
        Surface(
            color = cs.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Bolt, null,
                    tint = if (state.formIsActive) Green600 else cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Activate Immediately",
                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Devices will be prompted to update on next app start",
                        fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
                Switch(
                    checked = state.formIsActive,
                    onCheckedChange = onIsActiveChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Green600
                    )
                )
            }
        }

        // Upload progress bar
        AnimatedVisibility(visible = state.isUploading) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Uploading…", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Text("${(state.uploadProgress * 100).toInt()}%",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Purple700)
                }
                LinearProgressIndicator(
                    progress = { state.uploadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Purple700,
                    trackColor = Purple700.copy(0.2f)
                )
            }
        }

        // Submit button
        Button(
            onClick = onSubmit,
            enabled = !state.isUploading && state.formApkUri != null
                    && state.formVersionCode.isNotBlank() && state.formVersionName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple700)
        ) {
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Uploading…", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload Update", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Warning note
        Surface(
            color = Amber600.copy(0.1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = Amber600, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(
                    "APK must be signed with the same keystore as the installed app. " +
                    "Version code must be strictly greater than the current installed version.",
                    fontSize = 11.sp, color = Amber600.copy(0.9f), lineHeight = 16.sp
                )
            }
        }
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(
    label   : String,
    icon    : ImageVector,
    color   : Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color  = color.copy(0.1f),
        shape  = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
