package com.example.ritik_2.windowscontrol.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.example.ritik_2.windowscontrol.PcControlMain
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.PcControlRepository
import com.example.ritik_2.windowscontrol.data.PcDrive
import com.example.ritik_2.windowscontrol.data.PcFileFilter
import com.example.ritik_2.windowscontrol.data.PcFileItem
import com.example.ritik_2.windowscontrol.data.PcInstalledApp
import com.example.ritik_2.windowscontrol.data.PcPlan
import com.example.ritik_2.windowscontrol.data.PcRecentPath
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.data.PcStepSerializer
import com.example.ritik_2.windowscontrol.network.PcControlApiClient
import com.example.ritik_2.windowscontrol.network.PcControlBrowseClient
import com.example.ritik_2.windowscontrol.network.PcControlInputClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────
//  UI STATE
// ─────────────────────────────────────────────────────────────

sealed class PcUiState {
    object Idle : PcUiState()
    object Loading : PcUiState()
    data class Success(val message: String) : PcUiState()
    data class Error(val message: String) : PcUiState()
}

enum class PcConnectionStatus { UNKNOWN, CHECKING, ONLINE, OFFLINE }

enum class PcScreen { PLANS, APP_DIRECTORY, FILE_BROWSER, TOUCHPAD, KEYBOARD, SETTINGS }

// ─────────────────────────────────────────────────────────────
//  VIEWMODEL
// ─────────────────────────────────────────────────────────────

class PcControlViewModel(private val context: Context) : ViewModel() {

    private val repo = PcControlMain.repository
        ?: throw IllegalStateException("PcControlMain.init() not called")

    private val api = PcControlMain.apiClient
        ?: throw IllegalStateException("PcControlMain.init() not called")

    private val browse = PcControlMain.browseClient
        ?: throw IllegalStateException("PcControlMain.init() not called")

    private val input = PcControlInputClient(PcControlMain.getSettings())

    // ── Real-time refresh job ─────────────────────────────
    private var realTimeRefreshJob: Job? = null

    // ── Plans ─────────────────────────────
    val plans: LiveData<List<PcPlan>> = repo.allPlans.asLiveData()

    // ── Navigation ────────────────────────
    private val _currentScreen = MutableStateFlow(PcScreen.PLANS)
    val currentScreen: StateFlow<PcScreen> = _currentScreen

    fun navigateTo(screen: PcScreen) { _currentScreen.value = screen }

    // ── Connection ────────────────────────
    private val _connectionStatus = MutableStateFlow(PcConnectionStatus.UNKNOWN)
    val connectionStatus: StateFlow<PcConnectionStatus> = _connectionStatus

    private val _settings = MutableStateFlow(PcControlMain.getSettings())
    val settings: StateFlow<PcControlSettings> = _settings

    // ── UI State ──────────────────────────
    private val _uiState = MutableStateFlow<PcUiState>(PcUiState.Idle)
    val uiState: StateFlow<PcUiState> = _uiState

    // ── Plan Editor ───────────────────────
    private val _editingPlan = MutableStateFlow<PcPlan?>(null)
    val editingPlan: StateFlow<PcPlan?> = _editingPlan

    // ── Browse State ──────────────────────
    private val _drives = MutableStateFlow<List<PcDrive>>(emptyList())
    val drives: StateFlow<List<PcDrive>> = _drives

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    private val _dirItems = MutableStateFlow<List<PcFileItem>>(emptyList())
    val dirItems: StateFlow<List<PcFileItem>> = _dirItems

    private val _installedApps = MutableStateFlow<List<PcInstalledApp>>(emptyList())
    val installedApps: StateFlow<List<PcInstalledApp>> = _installedApps

    private val _recentPaths = MutableStateFlow<List<PcRecentPath>>(emptyList())
    val recentPaths: StateFlow<List<PcRecentPath>> = _recentPaths

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery

    val filteredApps: StateFlow<List<PcInstalledApp>> = combine(
        _installedApps, _appSearchQuery
    ) { apps, query ->
        if (query.isEmpty()) apps
        else apps.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Path navigation stack (back button support)
    private val pathStack = ArrayDeque<String>()

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
        pingPc()
    }

    // ─────────────────────────────────────
    //  CONNECTION
    // ─────────────────────────────────────

    fun pingPc() {
        viewModelScope.launch {
            _connectionStatus.value = PcConnectionStatus.CHECKING
            val r = api.ping()
            _connectionStatus.value = if (r.success) PcConnectionStatus.ONLINE
            else PcConnectionStatus.OFFLINE
        }
    }

    fun updateSettings(ip: String, port: Int = 5000, secretKey: String = "my_secret_123") {
        PcControlMain.updateConnection(ip, port, secretKey)
        _settings.value = PcControlMain.getSettings()
        pingPc()
    }

    fun resetUiState() { _uiState.value = PcUiState.Idle }

    // ─────────────────────────────────────
    //  PLAN EXECUTION
    // ─────────────────────────────────────

    fun executePlan(plan: PcPlan) {
        viewModelScope.launch {
            _uiState.value = PcUiState.Loading
            val r = api.executePlan(plan)
            _uiState.value = if (r.success)
                PcUiState.Success("✅ '${plan.planName}' sent to PC!")
            else
                PcUiState.Error("❌ ${r.error ?: "Execution failed"}")
        }
    }

    fun executeQuickStep(step: PcStep) {
        viewModelScope.launch {
            val r = api.executeQuickStep(step)
            if (!r.success) _uiState.value = PcUiState.Error("Step failed: ${r.error}")
        }
    }

    // ─────────────────────────────────────
    //  PLAN CRUD
    // ─────────────────────────────────────

    fun startNewPlan() {
        // Just set editingPlan — MainUI shows editor when editingPlan != null
        _editingPlan.value = PcPlan(
            planId   = UUID.randomUUID().toString(),
            planName = "",
            icon     = "⚡",
            stepsJson = "[]"
        )
        // Do NOT navigate — editor overlay is shown by MainUI when editingPlan != null
    }

    fun startEditPlan(plan: PcPlan) {
        _editingPlan.value = plan.copy()
        navigateTo(PcScreen.PLANS) // Ensure we're on plans tab so editor overlay shows
    }

    fun cancelEdit() {
        _editingPlan.value = null
        navigateTo(PcScreen.PLANS)
    }

    fun updateEditingPlan(plan: PcPlan) { _editingPlan.value = plan }

    fun savePlan() {
        val plan = _editingPlan.value ?: return
        if (plan.planName.isBlank()) {
            _uiState.value = PcUiState.Error("Please enter a plan name")
            return
        }
        if (plan.steps.isEmpty()) {
            _uiState.value = PcUiState.Error("Add at least one step")
            return
        }
        viewModelScope.launch {
            repo.insertPlan(plan)
            _editingPlan.value = null
            navigateTo(PcScreen.PLANS)
            _uiState.value = PcUiState.Success("✅ '${plan.planName}' saved!")
        }
    }

    fun deletePlan(plan: PcPlan) {
        viewModelScope.launch { repo.deletePlan(plan) }
    }

    fun addStep(step: PcStep) {
        val current = _editingPlan.value ?: return
        val newSteps = current.steps + step
        _editingPlan.value = current.copy(
            stepsJson = PcStepSerializer.toJson(newSteps)
        )
    }

    fun removeStep(index: Int) {
        val current = _editingPlan.value ?: return
        if (index < 0 || index >= current.steps.size) return
        val newSteps = current.steps.toMutableList().also { it.removeAt(index) }
        _editingPlan.value = current.copy(
            stepsJson = PcStepSerializer.toJson(newSteps)
        )
    }

    fun reorderSteps(fromIndex: Int, toIndex: Int) {
        val current = _editingPlan.value ?: return
        val steps = current.steps.toMutableList()
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= steps.size || toIndex >= steps.size) return
        val step = steps.removeAt(fromIndex)
        steps.add(toIndex, step)
        _editingPlan.value = current.copy(
            stepsJson = PcStepSerializer.toJson(steps)
        )
    }

    fun duplicateStep(index: Int) {
        val current = _editingPlan.value ?: return
        if (index < 0 || index >= current.steps.size) return
        val steps = current.steps.toMutableList()
        steps.add(index + 1, steps[index].copy())
        _editingPlan.value = current.copy(
            stepsJson = PcStepSerializer.toJson(steps)
        )
    }

    // ─────────────────────────────────────
    //  BROWSE — APPS
    // ─────────────────────────────────────

    fun loadInstalledApps() {
        viewModelScope.launch {
            _browseLoading.value = true
            val r = browse.getInstalledApps()
            if (r.success) _installedApps.value = r.data ?: emptyList()
            else _uiState.value = PcUiState.Error("Cannot load apps: ${r.error}")
            _browseLoading.value = false
        }
        loadRecentPaths()
    }

    fun setAppSearchQuery(q: String) { _appSearchQuery.value = q }

    // ─────────────────────────────────────
    //  BROWSE — FILES
    // ─────────────────────────────────────

    fun loadDrives() {
        viewModelScope.launch {
            _browseLoading.value = true
            val r = browse.getDrives()
            if (r.success) _drives.value = r.data ?: emptyList()
            else _uiState.value = PcUiState.Error("Cannot load drives: ${r.error}")
            _browseLoading.value = false
        }
        loadRecentPaths()
    }

    fun browseDir(path: String, filter: PcFileFilter = PcFileFilter.ALL) {
        viewModelScope.launch {
            _browseLoading.value = true
            if (_currentPath.value.isNotEmpty()) pathStack.addLast(_currentPath.value)
            _currentPath.value = path
            val r = browse.browseDir(path, filter)
            if (r.success) _dirItems.value = r.data ?: emptyList()
            else _uiState.value = PcUiState.Error("Cannot open: ${r.error}")
            _browseLoading.value = false
        }
    }

    fun navigateUp(): Boolean {
        if (pathStack.isEmpty()) return false
        val prev = pathStack.removeLast()
        viewModelScope.launch {
            _browseLoading.value = true
            _currentPath.value = prev
            val r = browse.browseDir(prev)
            if (r.success) _dirItems.value = r.data ?: emptyList()
            _browseLoading.value = false
        }
        return true
    }

    fun loadRecentPaths() {
        viewModelScope.launch {
            val r = browse.getRecentPaths()
            if (r.success) _recentPaths.value = r.data ?: emptyList()
        }
    }

    // ─────────────────────────────────────
    //  INPUT — Touchpad / Keyboard
    // ─────────────────────────────────────

    fun sendMouseDelta(dx: Float, dy: Float) {
        viewModelScope.launch { input.moveMouse(dx, dy) }
    }

    fun sendMouseClick(button: String = "left", double: Boolean = false) {
        viewModelScope.launch { input.clickMouse(button, double) }
    }

    fun sendMouseScroll(amount: Int) {
        viewModelScope.launch { input.scrollMouse(amount) }
    }

    fun sendKey(key: String) {
        viewModelScope.launch { input.pressKey(key) }
    }

    fun sendText(text: String) {
        viewModelScope.launch { input.typeText(text) }
    }

    fun sendMouseButtonDown(button: String = "left") {
        viewModelScope.launch { input.mouseButtonDown(button) }
    }

    fun sendMouseButtonUp() {
        viewModelScope.launch { input.mouseButtonUp() }
    }

    fun sendWinR(command: String = "") {
        viewModelScope.launch {
            if (command.isEmpty()) input.pressKey("WIN+R")
            else api.executeQuickStep(
                com.example.ritik_2.windowscontrol.data.PcStep("SYSTEM_CMD", "WIN_R", args = listOf(command))
            )
        }
    }

    // ─────────────────────────────────────
    //  REAL-TIME REFRESH
    // ─────────────────────────────────────

    fun startRealTimeRefresh(intervalMs: Long = 3000L) {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = viewModelScope.launch {
            while (true) {
                delay(intervalMs)
                if (_connectionStatus.value == PcConnectionStatus.ONLINE) {
                    // Silently refresh running process state in app list
                    try {
                        val procs = api.getProcesses()
                        if (procs.success) {
                            val running = procs.data?.map { it.lowercase() }?.toSet() ?: emptySet()
                            _installedApps.value = _installedApps.value.map { app ->
                                app.copy(isRunning = running.any { r -> r in app.exePath.lowercase() })
                            }
                        }
                    } catch (_: Exception) { /* ignore silent refresh errors */ }
                }
            }
        }
    }

    fun stopRealTimeRefresh() {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeRefresh()
    }
}

// ─────────────────────────────────────────────────────────────
//  FACTORY
// ─────────────────────────────────────────────────────────────

class PcControlViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PcControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PcControlViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}