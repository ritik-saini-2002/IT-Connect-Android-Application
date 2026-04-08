package com.example.ritik_2.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberPickerViewModel @Inject constructor(
    private val repo    : ChatRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberPickerUiState())
    val state: StateFlow<MemberPickerUiState> = _state.asStateFlow()

    private var allMembers = emptyList<ChatMember>()

    fun load(sc: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val members = repo.getCompanyMembers(sc)
                .filter { it.userId != authRepo.getSession()?.userId }
            allMembers = members
            _state.update {
                it.copy(
                    isLoading   = false,
                    members     = members,
                    filtered    = members,
                    departments = members.map { m -> m.department }.distinct().sorted(),
                    roles       = members.map { m -> m.role }.distinct().sorted()
                )
            }
        }
    }

    fun search(q: String) {
        _state.update { it.copy(searchQuery = q) }
        applyFilter()
    }

    fun filterDept(dept: String) {
        _state.update { it.copy(filterDept = if (it.filterDept == dept) "" else dept) }
        applyFilter()
    }

    fun filterRole(role: String) {
        _state.update { it.copy(filterRole = if (it.filterRole == role) "" else role) }
        applyFilter()
    }

    fun toggleSelect(userId: String) {
        val cur = _state.value.selected.toMutableSet()
        if (!cur.remove(userId)) cur.add(userId)
        _state.update { it.copy(selected = cur) }
    }

    private fun applyFilter() {
        val s = _state.value
        val result = allMembers.filter { m ->
            (s.searchQuery.isBlank() ||
                    m.name.contains(s.searchQuery, true) ||
                    m.role.contains(s.searchQuery, true) ||
                    m.department.contains(s.searchQuery, true)) &&
                    (s.filterDept.isBlank() || m.department == s.filterDept) &&
                    (s.filterRole.isBlank() || m.role == s.filterRole)
        }
        _state.update { it.copy(filtered = result) }
    }
}