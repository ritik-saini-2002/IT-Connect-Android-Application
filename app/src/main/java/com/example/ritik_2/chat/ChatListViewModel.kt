package com.example.ritik_2.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.data.source.AppDataSource
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
    private var sanitizedCompany = ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val session     = authRepository.getSession() ?: error("Not logged in")
                currentUserId   = session.userId
                currentUserName = session.name

                val profile = dataSource.getUserProfile(session.userId).getOrThrow()
                sanitizedCompany = profile.sanitizedCompany

                val rooms = repo.getRooms(currentUserId)
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

    fun getSanitizedCompany() = sanitizedCompany
    fun getCurrentUserId()    = currentUserId
    fun getCurrentUserName()  = currentUserName

    suspend fun getOrCreateDM(otherId: String, otherName: String): ChatRoom? {
        val profile = try {
            dataSource.getUserProfile(currentUserId).getOrNull()
        } catch (_: Exception) { null }

        return repo.getOrCreateDirectRoom(
            myId        = currentUserId,
            myName      = currentUserName,
            otherId     = otherId,
            otherName   = otherName,
            companyName = profile?.companyName ?: ""
        ).also { load() }
    }

    suspend fun createGroup(
        name       : String,
        memberIds  : List<String>,
        memberNames: List<String>,
        avatarBytes: ByteArray?
    ): ChatRoom? {
        val profile = dataSource.getUserProfile(currentUserId).getOrNull()
        return repo.createGroupRoom(
            name        = name,
            creatorId   = currentUserId,
            memberIds   = memberIds,
            memberNames = memberNames,
            companyName = profile?.companyName ?: "",
            avatarBytes = avatarBytes
        ).also { load() }
    }
}