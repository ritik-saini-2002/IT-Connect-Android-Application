package com.saini.ritik.windowscontrol.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saini.ritik.windowscontrol.network.PcControlApiClient
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Master-key-gated UI for the agent self-update endpoints (Phase 1.4).
 *
 * Workflow:
 *   1. Check Version — fetch current sha256 + backup inventory from agent.
 *   2. Pick File — SAF document picker; caller provides a `.py` from
 *      Downloads/Music/anywhere.
 *   3. Upload & Apply — POSTs the bytes; agent verifies sha256, rotates
 *      current into .bak1, writes new, self-exits for supervisor restart.
 *   4. Rollback — restore previous version from .bak1.
 *
 * The agent takes ~10–20s to come back online after update/rollback.
 */
@Composable
fun AgentUpdatePanel(
    api       : PcControlApiClient,
    masterKey : String,
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val cs      = MaterialTheme.colorScheme

    var versionInfo by remember { mutableStateOf<String?>(null) }
    var statusMsg   by remember { mutableStateOf("") }
    var statusOk    by remember { mutableStateOf(false) }
    var busy        by remember { mutableStateOf(false) }
    var picked      by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickedName  by remember { mutableStateOf("") }
    var pickedBytes by remember { mutableStateOf(0) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            picked = uri
            pickedName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment.orEmpty()
                } ?: uri.lastPathSegment.orEmpty()
            }.getOrElse { uri.lastPathSegment.orEmpty() }
            pickedBytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.available() } ?: 0
            }.getOrElse { 0 }
            statusMsg = ""
        }
    }

    Surface(
        color  = cs.surfaceVariant,
        shape  = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, cs.outline.copy(0.25f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Agent Self-Update", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true; statusMsg = ""
                            val r = api.getAgentVersion(masterKey.trim())
                            versionInfo = if (r.success) r.data else null
                            if (!r.success) { statusOk = false; statusMsg = "Version check failed: ${r.error}" }
                            busy = false
                        }
                    }
                ) { Text("Check Version", style = MaterialTheme.typography.labelSmall) }
            }

            versionInfo?.let { raw ->
                val (sha, backups) = remember(raw) { parseVersion(raw) }
                Surface(
                    color = cs.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, cs.outline.copy(0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "sha256: ${sha.take(16)}…",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant
                        )
                        if (backups.isEmpty()) {
                            Text("No backups yet", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        } else {
                            backups.forEach { b ->
                                Text(
                                    ".${b.slot}  ${b.sizeKb} KB  •  ${b.modified.take(16)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = cs.outline.copy(0.15f))

            // ── File picker + upload ─────────────────────────────────
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("text/x-python", "text/*", "*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (pickedName.isBlank()) "Pick new agent_vN.py"
                    else "$pickedName  (${pickedBytes / 1024} KB)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                enabled = !busy && picked != null && masterKey.isNotBlank(),
                onClick = {
                    val uri = picked ?: return@Button
                    scope.launch {
                        busy = true
                        statusMsg = "Reading file…"
                        val bytes = runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                        if (bytes == null || bytes.isEmpty()) {
                            statusOk = false; statusMsg = "Could not read the picked file"
                            busy = false; return@launch
                        }
                        if (bytes.size < 2048 || !bytes.asSequence().windowed(5).any { w ->
                                // cheap client-side "contains Flask" sanity — mirrors agent check
                                w == listOf('F'.code.toByte(), 'l'.code.toByte(),
                                            'a'.code.toByte(), 's'.code.toByte(), 'k'.code.toByte())
                            }) {
                            statusOk = false
                            statusMsg = "File doesn't look like an agent script (no 'Flask' marker)"
                            busy = false; return@launch
                        }
                        statusMsg = "Uploading ${bytes.size / 1024} KB…"
                        val r = api.uploadAgentCode(masterKey.trim(), bytes)
                        if (r.success) {
                            statusOk = true
                            statusMsg = "✓ Uploaded. Agent restarting — may be offline ~10–20s."
                            picked = null; pickedName = ""; pickedBytes = 0
                            versionInfo = null  // stale after restart
                        } else {
                            statusOk = false; statusMsg = "Upload failed: ${r.error}"
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
            ) {
                Icon(Icons.Default.CloudUpload, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Upload & Apply", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                enabled = !busy && masterKey.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true; statusMsg = "Rolling back…"
                        val r = api.rollbackAgent(masterKey.trim())
                        if (r.success) {
                            statusOk = true
                            statusMsg = "✓ Rolled back. Agent restarting — may be offline ~10–20s."
                            versionInfo = null
                        } else {
                            statusOk = false; statusMsg = "Rollback failed: ${r.error}"
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
            ) {
                Icon(Icons.Default.Undo, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rollback to previous", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (statusMsg.isNotEmpty()) {
                Text(
                    statusMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusOk) Color(0xFF22C55E) else cs.error
                )
            }
        }
    }
}

private data class AgentBackupRow(val slot: String, val sizeKb: Int, val modified: String)

private fun parseVersion(raw: String?): Pair<String, List<AgentBackupRow>> {
    if (raw.isNullOrBlank()) return "" to emptyList()
    return try {
        val j = JSONObject(raw)
        val sha = j.optString("sha256", "")
        val arr = j.optJSONArray("backups")
        val list = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        AgentBackupRow(
                            slot     = o.optString("slot", "bak?"),
                            sizeKb   = (o.optLong("size", 0L) / 1024).toInt(),
                            modified = o.optString("modified", "")
                        )
                    )
                }
            }
        }
        sha to list
    } catch (_: Exception) { "" to emptyList() }
}
