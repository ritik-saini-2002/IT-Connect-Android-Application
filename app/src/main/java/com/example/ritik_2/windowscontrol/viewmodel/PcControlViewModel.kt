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

    // Safe init — if PcControlMain.init() was not called, initialize it now
    // This prevents crashes when Application class is not set up correctly
    init {
        if (!PcControlMain.isInitialized) {
            android.util.Log.w("PcControl", "PcControlMain not initialized — auto-initializing now")
            PcControlMain.init(context)
        }
    }

    private val repo   get() = PcControlMain.repository   ?: run { PcControlMain.init(context); PcControlMain.repository!! }
    private val api    get() = PcControlMain.apiClient    ?: run { PcControlMain.init(context); PcControlMain.apiClient!! }
    private val browse get() = PcControlMain.browseClient ?: run { PcControlMain.init(context); PcControlMain.browseClient!! }
    private val input  get() = PcControlInputClient(PcControlMain.getSettings())

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

    // ── Live Screen ──────────────────────────────────────
    private val _liveScreenB64 = MutableStateFlow<String?>(null)
    val liveScreenB64: StateFlow<String?> = _liveScreenB64
    private val _liveScreenActive = MutableStateFlow(false)
    val liveScreenActive: StateFlow<Boolean> = _liveScreenActive
    private var liveScreenJob: Job? = null

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
            try {
                val settings = PcControlMain.getSettings()
                if (settings.pcIpAddress.isBlank()) {
                    _connectionStatus.value = PcConnectionStatus.UNKNOWN
                    return@launch
                }
                _connectionStatus.value = PcConnectionStatus.CHECKING
                val r = api.ping()
                _connectionStatus.value = if (r.success) PcConnectionStatus.ONLINE
                else PcConnectionStatus.OFFLINE
            } catch (e: Exception) {
                _connectionStatus.value = PcConnectionStatus.OFFLINE
                android.util.Log.e("PcControl", "pingPc error: ${e.message}")
            }
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
            try {
                _uiState.value = PcUiState.Loading
                val settings = PcControlMain.getSettings()
                if (settings.pcIpAddress.isBlank()) {
                    _uiState.value = PcUiState.Error("❌ No PC IP set. Go to Settings tab and enter your PC's IP address.")
                    return@launch
                }
                val r = api.executePlan(plan)
                _uiState.value = if (r.success)
                    PcUiState.Success("✅ '${plan.planName}' sent to PC!")
                else
                    PcUiState.Error("❌ Cannot reach PC at ${settings.pcIpAddress}:${settings.port}\nMake sure agent is running on PC.")
            } catch (e: Exception) {
                _uiState.value = PcUiState.Error("❌ Error: ${e.message}")
                android.util.Log.e("PcControl", "executePlan crashed", e)
            }
        }
    }

    fun executeQuickStep(step: PcStep) {
        viewModelScope.launch {
            try {
                val settings = PcControlMain.getSettings()
                if (settings.pcIpAddress.isBlank()) return@launch // silent skip if no IP
                val r = api.executeQuickStep(step)
                if (!r.success) _uiState.value = PcUiState.Error("Step failed: ${r.error}")
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "executeQuickStep error: ${e.message}")
            }
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
            // REPLACE strategy — saved permanently in Room DB until manually deleted
            repo.insertPlan(plan)
            _editingPlan.value = null
            navigateTo(PcScreen.PLANS)
            _uiState.value = PcUiState.Success("✅ '${plan.planName}' saved!")
            android.util.Log.d("PcControl", "Plan saved: ${plan.planName} (${plan.steps.size} steps)")
        }
    }

    fun deletePlanById(planId: String) {
        viewModelScope.launch {
            val plan = repo.getPlanById(planId) ?: return@launch
            repo.deletePlan(plan)
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

    /**
     * Load ONLY currently running processes from PC taskbar.
     * Shows what is open right now — not installed apps.
     */
    fun loadRunningApps() {
        viewModelScope.launch {
            _browseLoading.value = true
            try {
                val r = api.getProcesses()
                if (r.success) {
                    val running = r.data ?: emptyList()
                    _installedApps.value = running
                        .filter { it.isNotBlank() && !it.startsWith("[") }
                        .map { procName ->
                            val n = procName.lowercase()
                            val icon = when {
                                "chrome"   in n -> "🌐"
                                "firefox"  in n -> "🦊"
                                "vlc"      in n -> "🎬"
                                "spotify"  in n -> "🎵"
                                "code"     in n -> "💻"
                                "word"     in n -> "📝"
                                "excel"    in n -> "📗"
                                "powerpnt" in n -> "📊"
                                "teams"    in n -> "💬"
                                "zoom"     in n -> "📹"
                                "notepad"  in n -> "📄"
                                "explorer" in n -> "📁"
                                "studio"   in n -> "🤖"
                                else            -> "⚙️"
                            }
                            PcInstalledApp(
                                name      = procName.replace(".exe", "", ignoreCase = true),
                                exePath   = procName, // process name used for kill
                                icon      = icon,
                                isRunning = true
                            )
                        }
                        .distinctBy { it.name.lowercase() }
                        .sortedBy   { it.name.lowercase() }
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "loadRunningApps: ${e.message}")
            } finally {
                _browseLoading.value = false
            }
        }
    }

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
            try {
                _browseLoading.value = true
                // If navigating to a drive root (e.g. C:/ D:/) reset the stack
                val isDriveRoot = path.matches(Regex("[A-Za-z]:[/\\\\]?"))
                if (isDriveRoot) {
                    pathStack.clear()
                } else if (_currentPath.value.isNotEmpty()) {
                    pathStack.addLast(_currentPath.value)
                }
                _currentPath.value = path
                _dirItems.value = emptyList() // clear immediately so UI doesn't show stale
                val r = browse.browseDir(path, filter)
                if (r.success) {
                    _dirItems.value = r.data ?: emptyList()
                } else {
                    _uiState.value = PcUiState.Error("Cannot open: ${r.error}")
                    // Go back if error
                    if (pathStack.isNotEmpty()) {
                        _currentPath.value = pathStack.removeLast()
                    } else {
                        _currentPath.value = ""
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "browseDir crash: ${e.message}", e)
                _uiState.value = PcUiState.Error("Error: ${e.message}")
                _currentPath.value = ""
                pathStack.clear()
            } finally {
                _browseLoading.value = false
            }
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
            try {
                val r = browse.getRecentPaths()
                if (r.success) {
                    // Filter out empty or invalid paths to prevent crash
                    _recentPaths.value = (r.data ?: emptyList())
                        .filter { it.path.isNotBlank() && it.label.isNotBlank() }
                        .take(15)
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "loadRecentPaths error: ${e.message}")
                _recentPaths.value = emptyList()
            }
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

    // ── Live Screen capture polling ───────────────────────
    fun startLiveScreen(intervalMs: Long = 1500L) {
        if (_liveScreenActive.value) return
        _liveScreenActive.value = true
        liveScreenJob = viewModelScope.launch {
            while (_liveScreenActive.value) {
                try {
                    val r = api.captureScreen(quality = 25, scale = 4)
                    if (r.success) _liveScreenB64.value = r.data
                } catch (_: Exception) {}
                delay(intervalMs)
            }
        }
    }

    fun stopLiveScreen() {
        _liveScreenActive.value = false
        liveScreenJob?.cancel()
        liveScreenJob = null
        _liveScreenB64.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeRefresh()
        stopLiveScreen()
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