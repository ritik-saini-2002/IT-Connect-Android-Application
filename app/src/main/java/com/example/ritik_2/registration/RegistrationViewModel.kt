package com.example.ritik_2.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ritik_2.core.StringUtils
import com.example.ritik_2.data.model.RegistrationRequest
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import com.example.ritik_2.pocketbase.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RegistrationState {
    object Idle    : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val userId: String) : RegistrationState()
    data class Error(val message: String)  : RegistrationState()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val dataSource    : AppDataSource,
    private val pbDataSource  : PocketBaseDataSource,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    // Company name the user is typing — debounced to avoid too many requests
    private val _companyInput = MutableStateFlow("")
    private val _similarCompanies = MutableStateFlow<List<String>>(emptyList())
    val similarCompanies: StateFlow<List<String>> = _similarCompanies.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _companyInput
                .debounce(500)
                .filter { it.length >= 3 }
                .distinctUntilChanged()
                .collect { name ->
                    val sc      = StringUtils.sanitize(name)
                    val similar = try {
                        pbDataSource.findSimilarCompanies(sc)
                    } catch (_: Exception) { emptyList() }
                    _similarCompanies.value = similar
                }
        }
    }

    fun onCompanyNameChanged(name: String) { _companyInput.value = name }

    fun register(request: RegistrationRequest) {
        viewModelScope.launch {
            _state.value = RegistrationState.Loading
            dataSource.registerUser(request)
                .onSuccess { userId ->
                    // The error code COMPANY_EXISTS:Name means duplicate — surface it cleanly
                    try {
                        val session = dataSource.login(request.email, request.password)
                        sessionManager.save(session)
                    } catch (_: Exception) {}
                    _similarCompanies.value = emptyList()
                    _state.value = RegistrationState.Success(userId)
                }
                .onFailure { e ->
                    val msg = e.message ?: "Registration failed"
                    // If the server returned COMPANY_EXISTS, surface it specially
                    if (msg.startsWith("COMPANY_EXISTS:")) {
                        val name = msg.removePrefix("COMPANY_EXISTS:")
                        _similarCompanies.value = listOf(name)
                        _state.value = RegistrationState.Error(
                            "Company \"$name\" already exists. Please use a different name or contact that company's admin.")
                    } else {
                        _state.value = RegistrationState.Error(msg)
                    }
                }
        }
    }

    fun resetState() { _state.value = RegistrationState.Idle }
}