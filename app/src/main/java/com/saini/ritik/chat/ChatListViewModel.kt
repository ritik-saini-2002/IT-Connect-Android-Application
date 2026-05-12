package com.saini.ritik.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.data.source.AppDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo          : ChatRepository,
    private val authRepository: AuthRepository,
    private val dataSource    : AppDataSource,
    private val monitor       : ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    private var currentUserId    = ""
    private var currentUserName  = ""
    private var currentUserAvatar = ""
    private var sanitizedCompany = ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session = authRepository.getSession() ?: error("Not logged in")
                currentUserId   = session.userId
                currentUserName = session.name

                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany  = profile.sanitizedCompany
                currentUserAvatar = profile.imageUrl

                // Ensure a company-wide broadcast room exists so all users can see messages
                repo.getOrCreateBroadcastRoom(
                    companyName      = profile.companyName,
                    sanitizedCompany = sanitizedCompany,
                    creatorId        = currentUserId,
                    creatorName      = currentUserName
                )

                // Fetch rooms including broadcast rooms for the company
                val rooms = repo.getRooms(currentUserId, sanitizedCompany)
                _state.update {
                    it.copy(
                        isLoading       = false,
                        rooms           = rooms,
                        currentUserId   = currentUserId,
                        currentUserName = currentUserName
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun getSanitizedCompany()  = sanitizedCompany
    fun getCurrentUserId()     = currentUserId
    fun getCurrentUserName()   = currentUserName
    fun getCurrentUserAvatar() = currentUserAvatar

    suspend fun getOrCreateDM(
        otherId    : String,
        otherName  : String,
        otherAvatar: String = ""
    ): ChatRoom? {
        val profile = try {
            dataSource.getUserProfile(currentUserId).getOrNull()
        } catch (_: Exception) { null }

        return repo.getOrCreateDirectRoom(
            myId          = currentUserId,
            myName        = currentUserName,
            myAvatarUrl   = currentUserAvatar,
            otherId       = otherId,
            otherName     = otherName,
            otherAvatarUrl= otherAvatar,
            companyName   = profile?.companyName ?: ""
        ).also { load() }
    }

    suspend fun createGroup(
        name        : String,
        memberIds   : List<String>,
        memberNames : List<String>,
        avatarBytes : ByteArray?,
        memberAvatars: List<String> = List(memberIds.size) { "" }
    ): ChatRoom? {
        val profile = dataSource.getUserProfile(currentUserId).getOrNull()
        return repo.createGroupRoom(
            name         = name,
            creatorId    = currentUserId,
            creatorName  = currentUserName,
            memberIds    = memberIds,
            memberNames  = memberNames,
            memberAvatars= memberAvatars,
            companyName  = profile?.companyName ?: "",
            avatarBytes  = avatarBytes
        ).also { load() }
    }

    /**
     * Called after a user leaves a group — refreshes the room list
     * so the left group disappears from the list.
     */
    fun refreshAfterLeave() { load() }
}