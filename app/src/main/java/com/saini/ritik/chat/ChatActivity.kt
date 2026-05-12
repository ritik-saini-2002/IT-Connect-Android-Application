package com.saini.ritik.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.saini.ritik.theme.ITConnectTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val listVm: ChatListViewModel by viewModels()
    private val roomVm: ChatRoomViewModel by viewModels()

    // ── File pickers ──────────────────────────────────────────────────────────

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        handleFilePicked(uri)
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        handleFilePicked(uri)
    }

    private var cameraUri: Uri? = null
    private val cameraPicker = registerForActivityResult(
        ActivityResultContracts.TakePicture()) { saved: Boolean ->
        if (!saved) return@registerForActivityResult
        val uri = cameraUri ?: return@registerForActivityResult
        handleFilePicked(uri)
    }

    // ── MemberPicker result ───────────────────────────────────────────────────

    private val memberPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data      = result.data ?: return@registerForActivityResult
        val ids       = data.getStringArrayListExtra(MemberPickerActivity.RESULT_IDS)
            ?: return@registerForActivityResult
        val names     = data.getStringArrayListExtra(MemberPickerActivity.RESULT_NAMES) ?: arrayListOf()
        val groupName = data.getStringExtra(MemberPickerActivity.RESULT_GROUP_NAME) ?: "New Group"
        val isGroup   = data.getBooleanExtra(MemberPickerActivity.RESULT_IS_GROUP, false)

        lifecycleScope.launch {
            if (isGroup) {
                val room = listVm.createGroup(groupName, ids, names, null)
                if (room != null) openRoom(room)
                else Toast.makeText(this@ChatActivity, "Failed to create group", Toast.LENGTH_SHORT).show()
            } else if (ids.size == 1) {
                val room = listVm.getOrCreateDM(ids[0], names.firstOrNull() ?: "")
                if (room != null) openRoom(room)
            }
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val _currentRoom = mutableStateOf<ChatRoom?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KEY FIX: let the window resize when the keyboard appears
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val directRoomId = intent.getStringExtra(EXTRA_ROOM_ID)

        setContent {
            ITConnectTheme {
                val currentRoom by _currentRoom

                LaunchedEffect(directRoomId) {
                    if (directRoomId != null && currentRoom == null) {
                        val target = listVm.state.value.rooms.find { it.id == directRoomId }
                        if (target != null) openRoom(target)
                    }
                }

                // ── FIX: Proper back navigation ──────────────────────────────
                // When in a room → go back to list (don't finish activity)
                // When in list → finish activity
                // This prevents the "instant close" bug where pressing back
                // in a chat room would kill the entire ChatActivity.
                BackHandler(enabled = currentRoom != null) {
                    _currentRoom.value = null
                }

                if (currentRoom != null) {
                    ChatRoomScreen(
                        room          = currentRoom!!,
                        viewModel     = roomVm,
                        onBack        = { _currentRoom.value = null },
                        onPickFile    = { filePicker.launch("*/*") },
                        onPickImage   = { imagePicker.launch("image/*") },
                        onPickCamera  = { launchCamera() },
                        onViewProfile = { userId -> openMemberProfile(userId) }
                    )
                } else {
                    // When on the list screen, system back finishes the activity
                    BackHandler(enabled = true) {
                        finish()
                    }

                    ChatListScreen(
                        viewModel  = listVm,
                        onOpenRoom = { room -> openRoom(room) },
                        onNewGroup = {
                            memberPickerLauncher.launch(
                                MemberPickerActivity.newIntent(
                                    this@ChatActivity,
                                    sanitizedCompany = listVm.getSanitizedCompany(),
                                    isGroupMode      = true
                                )
                            )
                        },
                        onNewDM = {
                            memberPickerLauncher.launch(
                                MemberPickerActivity.newIntent(
                                    this@ChatActivity,
                                    sanitizedCompany = listVm.getSanitizedCompany(),
                                    isGroupMode      = false
                                )
                            )
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun openRoom(room: ChatRoom) {
        _currentRoom.value = room
        roomVm.init(room)
    }

    private fun openMemberProfile(userId: String) {
        startActivity(
            android.content.Intent(this, com.saini.ritik.profile.ProfileActivity::class.java)
                .putExtra("userId", userId)
        )
    }

    private fun launchCamera() {
        val file = java.io.File(cacheDir, "chat_photo_${System.currentTimeMillis()}.jpg")
        val uri  = androidx.core.content.FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file)
        cameraUri = uri
        cameraPicker.launch(uri)
    }

    private fun handleFilePicked(uri: Uri) {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val name = getFileNameFromUri(uri)
        roomVm.setSelectedFile(uri, name, mime)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) name = cursor.getString(col)
        }
        return name
    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        fun newIntent(context: Context, roomId: String? = null): Intent =
            Intent(context, ChatActivity::class.java).apply {
                roomId?.let { putExtra(EXTRA_ROOM_ID, it) }
            }
    }
}