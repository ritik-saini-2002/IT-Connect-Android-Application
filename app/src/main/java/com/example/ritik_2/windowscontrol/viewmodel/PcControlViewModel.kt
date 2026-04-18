package com.example.ritik_2.windowscontrol.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.example.ritik_2.data.model.AuthSession
import com.example.ritik_2.windowscontrol.PcControlMain
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.*
import com.example.ritik_2.windowscontrol.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

// ─────────────────────────────────────────────────────────────
//  STATE TYPES
// ─────────────────────────────────────────────────────────────

sealed class PcUiState {
    object Idle    : PcUiState()
    object Loading : PcUiState()
    data class Success(val message: String) : PcUiState()
    data class Error  (val message: String) : PcUiState()
}

enum class PcConnectionStatus { UNKNOWN, CHECKING, ONLINE, OFFLINE }

enum class PcScreen { PLANS, APP_DIRECTORY, FILE_BROWSER, TOUCHPAD, KEYBOARD, SETTINGS, DEVICES }

enum class FileBrowserMode { EXECUTE, TRANSFER }

// ─────────────────────────────────────────────────────────────
//  VIEWMODEL
// ─────────────────────────────────────────────────────────────

class PcControlViewModel(private val context: Context) : ViewModel() {

    init {
        if (!PcControlMain.isInitialized) {
            android.util.Log.w("PcControl", "PcControlMain not initialized — auto-init")
            PcControlMain.init(context)
        }
    }

    private val repo   get() = PcControlMain.repository   ?: run { PcControlMain.init(context); PcControlMain.repository!! }
    private val api    get() = PcControlMain.apiClient    ?: run { PcControlMain.init(context); PcControlMain.apiClient!! }
    private val browse get() = PcControlMain.browseClient ?: run { PcControlMain.init(context); PcControlMain.browseClient!! }

    // Fresh InputClient per call — always picks up latest settings
    private val input  get() = PcControlInputClient(PcControlMain.getSettings())

    // ── Current user session ───────────────────────────────
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    fun setSession(session: AuthSession?) { _session.value = session }

    val currentUserId: String? get() = _session.value?.userId
    val currentRole: String get() = _session.value?.role.orEmpty()
    val currentPermissions: List<String> get() = _session.value?.permissions.orEmpty()

    // ── Plans ──────────────────────────────────────────────
    val plans: LiveData<List<PcPlan>> = repo.allPlans.asLiveData()

    // ── Navigation ─────────────────────────────────────────
    private val _currentScreen = MutableStateFlow(PcScreen.TOUCHPAD)
    val currentScreen: StateFlow<PcScreen> = _currentScreen

    /** Screens accessible without login */
    val guestScreens: Set<PcScreen> = setOf(PcScreen.SETTINGS, PcScreen.TOUCHPAD, PcScreen.DEVICES)

    private val _showLoginRequired = MutableStateFlow(false)
    val showLoginRequired: StateFlow<Boolean> = _showLoginRequired.asStateFlow()

    /** Navigate to [screen], guarded by [isLoggedIn]. Guest screens always accessible. */
    fun navigateTo(screen: PcScreen, isLoggedIn: Boolean = true) {
        if (!isLoggedIn && screen !in guestScreens) {
            _showLoginRequired.value = true
            return
        }
        _currentScreen.value = screen
    }

    fun dismissLoginRequired() { _showLoginRequired.value = false }

    // ── Connection ─────────────────────────────────────────
    private val _connectionStatus = MutableStateFlow(PcConnectionStatus.UNKNOWN)
    val connectionStatus: StateFlow<PcConnectionStatus> = _connectionStatus

    private val _settings = MutableStateFlow(PcControlMain.getSettings())
    val settings: StateFlow<PcControlSettings> = _settings

    // ── UI State ───────────────────────────────────────────
    private val _uiState = MutableStateFlow<PcUiState>(PcUiState.Idle)
    val uiState: StateFlow<PcUiState> = _uiState

    // ── Plan Editor ────────────────────────────────────────
    private val _editingPlan = MutableStateFlow<PcPlan?>(null)
    val editingPlan: StateFlow<PcPlan?> = _editingPlan

    // ── Browse State ───────────────────────────────────────
    private val _drives        = MutableStateFlow<List<PcDrive>>(emptyList())
    val drives: StateFlow<List<PcDrive>> = _drives

    private val _currentPath   = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    private val _dirItems      = MutableStateFlow<List<PcFileItem>>(emptyList())
    val dirItems: StateFlow<List<PcFileItem>> = _dirItems

    private val _installedApps = MutableStateFlow<List<PcInstalledApp>>(emptyList())
    val installedApps: StateFlow<List<PcInstalledApp>> = _installedApps

    private val _recentPaths   = MutableStateFlow<List<PcRecentPath>>(emptyList())
    val recentPaths: StateFlow<List<PcRecentPath>> = _recentPaths

    data class SpecialFolder(val name: String, val path: String, val icon: String)
    private val _specialFolders = MutableStateFlow<List<SpecialFolder>>(emptyList())
    val specialFolders: StateFlow<List<SpecialFolder>> = _specialFolders

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery

    // ── File Browser UI State (survives tab switching) ─────
    private val _browserLevel   = MutableStateFlow<BrowserLevelState>(BrowserLevelState.Root)
    val browserLevel: StateFlow<BrowserLevelState> = _browserLevel

    private val _selectedFilter = MutableStateFlow(PcFileFilter.ALL)
    val selectedFilter: StateFlow<PcFileFilter> = _selectedFilter

    private val _searchQuery    = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ── File Browser Mode ──────────────────────────────────
    private val _fileBrowserMode = MutableStateFlow(FileBrowserMode.EXECUTE)
    val fileBrowserMode: StateFlow<FileBrowserMode> = _fileBrowserMode

    fun setBrowserLevel(level: BrowserLevelState) { _browserLevel.value = level }
    fun setSelectedFilter(f: PcFileFilter)         { _selectedFilter.value = f }
    fun setSearchQuery(q: String)                  { _searchQuery.value = q }

    fun toggleFileBrowserMode() {
        _fileBrowserMode.value = if (_fileBrowserMode.value == FileBrowserMode.TRANSFER)
            FileBrowserMode.EXECUTE else FileBrowserMode.TRANSFER
    }

    /** Serialisable form of BrowserLevel stored in ViewModel */
    sealed class BrowserLevelState {
        object Root                                                : BrowserLevelState()
        data class Drive(val letter: String, val label: String)   : BrowserLevelState()
        data class Directory(val path: String, val label: String) : BrowserLevelState()
    }

    // ── Transfer Progress ──────────────────────────────────
    private val _transferProgress = MutableStateFlow<PcTransferProgress?>(null)
    val transferProgress: StateFlow<PcTransferProgress?> = _transferProgress

    // ── Scroll position memory ─────────────────────────────
    private val scrollPositions = mutableMapOf<String, Int>()

    fun saveScrollPosition(path: String, index: Int) { scrollPositions[path] = index }
    fun getScrollPosition(path: String): Int          = scrollPositions[path] ?: 0

    // ── Open With Dialog ───────────────────────────────────
    private val _openWithDialog = MutableStateFlow<PcOpenWithDialog?>(null)
    val openWithDialog: StateFlow<PcOpenWithDialog?> = _openWithDialog
    private var openWithPollJob: Job? = null

    // ── Live Screen ────────────────────────────────────────
    private val _liveScreenB64    = MutableStateFlow<String?>(null)
    val liveScreenB64: StateFlow<String?> = _liveScreenB64
    private val _liveScreenActive = MutableStateFlow(false)
    val liveScreenActive: StateFlow<Boolean> = _liveScreenActive
    private var liveScreenJob: Job? = null

    // ── Real-time refresh ──────────────────────────────────
    private var realTimeRefreshJob: Job? = null

    // ── Search debounce job ────────────────────────────────
    private var searchJob: Job? = null

    val filteredApps: StateFlow<List<PcInstalledApp>> = combine(
        _installedApps, _appSearchQuery
    ) { apps, query ->
        if (query.isEmpty()) apps
        else apps.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Path navigation stack
    private val pathStack = ArrayDeque<String>()

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
        pingPc()
    }

    // ─────────────────────────────────────────────────────
    //  CONNECTION
    // ─────────────────────────────────────────────────────

    fun pingPc() {
        viewModelScope.launch {
            try {
                val currentSettings = PcControlMain.getSettings()
                android.util.Log.d("PcControl",
                    "pingPc → IP=${currentSettings.pcIpAddress} Port=${currentSettings.port}")
                if (currentSettings.pcIpAddress.isBlank()) {
                    _connectionStatus.value = PcConnectionStatus.UNKNOWN
                    return@launch
                }
                _connectionStatus.value = PcConnectionStatus.CHECKING
                val freshApi = PcControlApiClient(currentSettings)
                val r        = freshApi.ping()
                _connectionStatus.value = if (r.success) PcConnectionStatus.ONLINE
                else PcConnectionStatus.OFFLINE
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "pingPc exception: ${e.message}", e)
                _connectionStatus.value = PcConnectionStatus.OFFLINE
            }
        }
    }

    fun updateSettings(ip: String, port: Int = 5000, secretKey: String = "Ritik@2002") {
        PcControlMain.updateConnection(ip, port, secretKey)
        _settings.value = PcControlMain.getSettings()
        pingPc()
    }

    fun resetUiState() { _uiState.value = PcUiState.Idle }

    // ─────────────────────────────────────────────────────
    //  SAVED DEVICES + LAN DISCOVERY
    // ─────────────────────────────────────────────────────

    val savedDevices: StateFlow<List<PcSavedDevice>> = repo.savedDevices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<com.example.ritik_2.windowscontrol.network.PcLanScanner.DiscoveredAgent>>(emptyList())
    val scanResults: StateFlow<List<com.example.ritik_2.windowscontrol.network.PcLanScanner.DiscoveredAgent>> = _scanResults.asStateFlow()

    private var scanJob: Job? = null

    fun startLanScan(durationMs: Long = 4_000L) {
        scanJob?.cancel()
        _scanResults.value = emptyList()
        _scanning.value    = true
        scanJob = viewModelScope.launch {
            try {
                val scanner = com.example.ritik_2.windowscontrol.network.PcLanScanner(context)
                scanner.scan(durationMs).collect { agent ->
                    _scanResults.value = _scanResults.value + agent
                }
            } catch (e: Exception) {
                _uiState.value = PcUiState.Error("Scan failed: ${e.message}")
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopLanScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    /** Save a device (new or update) then optionally mark as last-used. */
    fun saveDevice(device: PcSavedDevice) {
        viewModelScope.launch {
            repo.saveDevice(device)
            _uiState.value = PcUiState.Success("Saved '${device.label.ifBlank { device.host }}'")
        }
    }

    fun deleteDevice(device: PcSavedDevice) {
        viewModelScope.launch {
            repo.deleteDevice(device)
            com.example.ritik_2.windowscontrol.network.PcThumbnailFetcher
                .deleteCached(context, device.id)
        }
    }

    // ── Thumbnails ───────────────────────────────────────
    private val THUMB_TTL_MS = 5 * 60_000L

    /** Refresh a single device's thumbnail. Silent on failure. */
    fun refreshThumbnail(device: PcSavedDevice) {
        viewModelScope.launch {
            val path = com.example.ritik_2.windowscontrol.network.PcThumbnailFetcher
                .fetch(device, context) ?: return@launch
            repo.setDeviceThumbnail(device.id, path)
        }
    }

    /** Refresh thumbnails for any saved device whose cache is stale (> 5 min). */
    fun refreshStaleThumbnails() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            savedDevices.value
                .filter { now - it.thumbnailUpdatedAt > THUMB_TTL_MS }
                .forEach { refreshThumbnail(it) }
        }
    }

    // ── Schedules ────────────────────────────────────────
    fun schedulesFor(deviceId: String): StateFlow<List<PcSchedule>> =
        repo.schedulesForDevice(deviceId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upsertSchedule(s: PcSchedule) {
        viewModelScope.launch { repo.upsertSchedule(s) }
    }

    fun deleteSchedule(s: PcSchedule) {
        viewModelScope.launch { repo.deleteSchedule(s) }
    }

    fun toggleSchedule(s: PcSchedule) {
        viewModelScope.launch { repo.setScheduleEnabled(s.id, !s.enabled) }
    }

    // ── Wake-on-LAN ──────────────────────────────────────
    fun wakePc(device: PcSavedDevice) {
        val mac = device.macAddress
        if (!com.example.ritik_2.windowscontrol.network.WakeOnLan.isValidMac(mac)) {
            _uiState.value = PcUiState.Error("Set a valid MAC address first")
            return
        }
        viewModelScope.launch {
            val result = com.example.ritik_2.windowscontrol.network.WakeOnLan
                .wake(mac!!, device.broadcastAddress, device.wolPort)
            _uiState.value = result.fold(
                onSuccess = { PcUiState.Success("Wake packet sent to ${device.label.ifBlank { device.host }}") },
                onFailure = { PcUiState.Error("WoL failed: ${it.message}") }
            )
        }
    }

    /** Save a scan result. If one already exists for host:port, update its label/pcName. */
    fun saveFromScan(
        agent    : com.example.ritik_2.windowscontrol.network.PcLanScanner.DiscoveredAgent,
        label    : String,
        secretKey: String,
        isMaster : Boolean
    ) {
        viewModelScope.launch {
            val existing = repo.findDeviceByAddress(agent.ip, agent.port)
            val device = (existing ?: PcSavedDevice()).copy(
                label      = label.ifBlank { agent.pcName.ifBlank { agent.ip } },
                host       = agent.ip,
                port       = agent.port,
                streamPort = agent.streamPort,
                secretKey  = secretKey,
                isMaster   = isMaster,
                pcName     = agent.pcName,
                lastSeenOnline = System.currentTimeMillis()
            )
            repo.saveDevice(device)
            _uiState.value = PcUiState.Success("Saved '${device.label}'")
        }
    }

    /** Switch active connection to the selected saved device. */
    fun connectToSaved(device: PcSavedDevice) {
        viewModelScope.launch {
            repo.touchDevice(device.id)
            PcControlMain.updateConnection(
                pcIp            = device.host,
                port            = device.port,
                secretKey       = device.secretKey,
                certFingerprint = device.certFingerprint,
                streamPort      = device.streamPort,
            )
            _settings.value = PcControlMain.getSettings()
            _uiState.value  = PcUiState.Success("Connected to ${device.label.ifBlank { device.host }}")
            pingPc()
        }
    }

    // ─────────────────────────────────────────────────────
    //  PLAN EXECUTION
    // ─────────────────────────────────────────────────────

    fun executePlan(plan: PcPlan) {
        viewModelScope.launch {
            try {
                _uiState.value = PcUiState.Loading
                val ip = PcControlMain.getSettings().pcIpAddress
                if (ip.isBlank()) {
                    _uiState.value = PcUiState.Error("No PC IP set. Go to Settings.")
                    return@launch
                }
                val r = api.executePlan(plan)
                _uiState.value = if (r.success)
                    PcUiState.Success("'${plan.planName}' sent to PC!")
                else
                    PcUiState.Error("Cannot reach PC at $ip\nMake sure agent is running.")
            } catch (e: Exception) {
                _uiState.value = PcUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun executeQuickStep(step: PcStep) {
        viewModelScope.launch {
            try {
                if (PcControlMain.getSettings().pcIpAddress.isBlank()) return@launch
                val r = api.executeQuickStep(step)
                if (!r.success) _uiState.value = PcUiState.Error("Step failed: ${r.error}")
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "executeQuickStep: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  PLAN CRUD
    // ─────────────────────────────────────────────────────

    fun startNewPlan() {
        _editingPlan.value = PcPlan(
            planId    = UUID.randomUUID().toString(),
            planName  = "",
            icon      = "⚡",
            stepsJson = "[]"
        )
    }

    fun startEditPlan(plan: PcPlan) {
        _editingPlan.value = plan.copy()
        navigateTo(PcScreen.PLANS)
    }

    fun cancelEdit() {
        _editingPlan.value = null
        navigateTo(PcScreen.PLANS)
    }

    fun updateEditingPlan(plan: PcPlan) { _editingPlan.value = plan }

    fun savePlan() {
        val plan = _editingPlan.value ?: return
        if (plan.planName.isBlank()) {
            _uiState.value = PcUiState.Error("Please enter a plan name"); return
        }
        if (plan.steps.isEmpty()) {
            _uiState.value = PcUiState.Error("Add at least one step"); return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.insertPlan(plan) }
                _editingPlan.value = null
                navigateTo(PcScreen.PLANS)
                _uiState.value = PcUiState.Success("'${plan.planName}' saved!")
            } catch (e: Throwable) {
                android.util.Log.e("PcControl", "savePlan failed", e)
                _uiState.value = PcUiState.Error("Save failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun savePlanDirectly(plan: PcPlan) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.insertPlan(plan) }
                _uiState.value = PcUiState.Success("File added to \"${plan.planName}\"")
            } catch (e: Throwable) {
                android.util.Log.e("PcControl", "savePlanDirectly failed", e)
                _uiState.value = PcUiState.Error("Failed to save: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun deletePlan(plan: PcPlan) {
        viewModelScope.launch { repo.deletePlan(plan) }
    }

    fun addStep(step: PcStep) {
        val cur = _editingPlan.value ?: return
        _editingPlan.value = cur.copy(
            stepsJson = PcStepSerializer.toJson(cur.steps + step))
    }

    fun removeStep(index: Int) {
        val cur = _editingPlan.value ?: return
        if (index < 0 || index >= cur.steps.size) return
        val steps = cur.steps.toMutableList().also { it.removeAt(index) }
        _editingPlan.value = cur.copy(stepsJson = PcStepSerializer.toJson(steps))
    }

    fun reorderSteps(fromIndex: Int, toIndex: Int) {
        val cur = _editingPlan.value ?: return
        val steps = cur.steps.toMutableList()
        if (fromIndex !in steps.indices || toIndex !in steps.indices) return
        val step = steps.removeAt(fromIndex)
        steps.add(toIndex, step)
        _editingPlan.value = cur.copy(stepsJson = PcStepSerializer.toJson(steps))
    }

    // ─────────────────────────────────────────────────────
    //  BROWSE — APPS
    // ─────────────────────────────────────────────────────

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

    fun loadRunningApps() {
        viewModelScope.launch {
            _browseLoading.value = true
            try {
                val r = api.getProcesses()
                if (r.success) {
                    _installedApps.value = (r.data ?: emptyList())
                        .filter { it.isNotBlank() && !it.startsWith("[") }
                        .map { proc ->
                            val n = proc.lowercase()
                            val icon = when {
                                "chrome"    in n -> "🌐"; "firefox"  in n -> "🦊"
                                "vlc"       in n -> "🎬"; "spotify"  in n -> "🎵"
                                "code"      in n -> "💻"; "word"     in n -> "📝"
                                "excel"     in n -> "📗"; "powerpnt" in n -> "📊"
                                "teams"     in n -> "💬"; "zoom"     in n -> "📹"
                                "notepad"   in n -> "📄"; "explorer" in n -> "📁"
                                "studio"    in n -> "🤖"
                                else             -> "⚙️"
                            }
                            PcInstalledApp(
                                name      = proc.replace(".exe","", ignoreCase = true),
                                exePath   = proc,
                                icon      = icon,
                                isRunning = true
                            )
                        }
                        .distinctBy { it.name.lowercase() }
                        .sortedBy   { it.name.lowercase() }
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl","loadRunningApps: ${e.message}")
            } finally {
                _browseLoading.value = false
            }
        }
    }

    // ── App minimize / restore — ViewModel-backed state ───────
    // isMinimized state per exePath so it persists across scroll/recomposition

    private val _appMinimizeState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val appMinimizeState: StateFlow<Map<String, Boolean>> = _appMinimizeState.asStateFlow()

    fun setAppMinimized(exePath: String, minimized: Boolean) {
        _appMinimizeState.value = _appMinimizeState.value + (exePath to minimized)
    }

    fun minimizeApp(exePath: String) {
        viewModelScope.launch {
            try {
                input.post("/app/minimize", mapOf("name" to exePath))
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "minimizeApp: ${e.message}")
            }
        }
    }

    fun restoreApp(exePath: String) {
        viewModelScope.launch {
            try {
                input.post("/app/restore", mapOf("name" to exePath))
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "restoreApp: ${e.message}")
            }
        }
    }

    fun killApp(exePath: String) {
        viewModelScope.launch {
            try {
                input.post("/app/kill", mapOf("name" to exePath, "force" to false))
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "killApp: ${e.message}")
            }
        }
    }

    fun forceKillApp(exePath: String) {
        viewModelScope.launch {
            try {
                input.post("/app/kill", mapOf("name" to exePath, "force" to true))
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "forceKillApp: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  BROWSE — FILES
    // ─────────────────────────────────────────────────────

    fun loadDrives() {
        viewModelScope.launch {
            _browseLoading.value = true
            val r = browse.getDrives()
            if (r.success) _drives.value = r.data ?: emptyList()
            else _uiState.value = PcUiState.Error("Cannot load drives: ${r.error}")
            _browseLoading.value = false
        }
        loadRecentPaths()
        loadSpecialFolders()
    }

    fun loadSpecialFolders() {
        viewModelScope.launch {
            try {
                val r = browse.getSpecialFolders()
                if (r.success) {
                    _specialFolders.value = r.data?.map { map ->
                        SpecialFolder(
                            name = map["name"] as? String ?: "",
                            path = map["path"] as? String ?: "",
                            icon = map["icon"] as? String ?: "📁"
                        )
                    }?.filter { it.name.isNotBlank() } ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl","loadSpecialFolders: ${e.message}")
            }
        }
    }

    fun browseDir(
        path      : String,
        filter    : PcFileFilter = PcFileFilter.ALL,
        isRefresh : Boolean      = false
    ) {
        viewModelScope.launch {
            try {
                _browseLoading.value = true
                val isDriveRoot = path.matches(Regex("[A-Za-z]:[/\\\\]?"))

                if (!isRefresh) {
                    if (isDriveRoot) pathStack.clear()
                    else if (_currentPath.value.isNotEmpty() && _currentPath.value != path)
                        pathStack.addLast(_currentPath.value)
                }

                _currentPath.value = path
                _dirItems.value    = emptyList()

                val r = browse.browseDir(path, filter)
                if (r.success) {
                    _dirItems.value = r.data ?: emptyList()
                } else {
                    _uiState.value = PcUiState.Error("Cannot open: ${r.error}")
                    if (!isRefresh) {
                        if (pathStack.isNotEmpty()) _currentPath.value = pathStack.removeLast()
                        else _currentPath.value = ""
                    }
                }
            } catch (e: Exception) {
                _uiState.value = PcUiState.Error("Error: ${e.message}")
                if (!isRefresh) { _currentPath.value = ""; pathStack.clear() }
            } finally {
                _browseLoading.value = false
            }
        }
    }

    fun searchFiles(rootPath: String, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (query.isBlank() || rootPath.isBlank()) return@launch
            try {
                _browseLoading.value = true
                val r = browse.searchFiles(rootPath, query)
                if (r.success) {
                    _dirItems.value = r.data ?: emptyList()
                } else {
                    android.util.Log.w("PcControl", "searchFiles error: ${r.error}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "searchFiles: ${e.message}")
            } finally {
                _browseLoading.value = false
            }
        }
    }

    suspend fun browsePickerDir(path: String): List<PcFileItem> {
        return try {
            val r = browse.browseDir(path, PcFileFilter.ALL)
            if (r.success) r.data ?: emptyList() else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("PcControl","browsePickerDir: ${e.message}")
            emptyList()
        }
    }

    fun navigateUp(): Boolean {
        if (pathStack.isEmpty()) {
            if (_currentPath.value.isNotEmpty()) {
                _currentPath.value = ""
                _dirItems.value    = emptyList()
                return true
            }
            return false
        }
        val prev = pathStack.removeLast()
        viewModelScope.launch {
            _browseLoading.value = true
            _currentPath.value   = prev
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
                    _recentPaths.value = (r.data ?: emptyList())
                        .filter { it.path.isNotBlank() && it.label.isNotBlank() }
                        .take(15)
                }
            } catch (e: Exception) {
                _recentPaths.value = emptyList()
            }
        }
    }

    // ── File Transfer ──────────────────────────────────────

    fun downloadFile(
        remotePath      : String,
        saveToUri       : android.net.Uri,
        contentResolver : android.content.ContentResolver
    ) {
        viewModelScope.launch {
            val fileName = remotePath.substringAfterLast('/').substringAfterLast('\\')
            _transferProgress.value = PcTransferProgress(fileName, 0L, 0L, 0L, isUpload = false)
            try {
                val outputStream = contentResolver.openOutputStream(saveToUri)
                    ?: run {
                        _uiState.value = PcUiState.Error("Cannot open output stream")
                        return@launch
                    }
                val result = browse.downloadFile(
                    remotePath   = remotePath,
                    outputStream = outputStream
                ) { done, total, speed ->
                    _transferProgress.value = PcTransferProgress(fileName, total, done, speed, isUpload = false)
                }
                if (result.success) {
                    _transferProgress.value = _transferProgress.value?.copy(isDone = true)
                    _uiState.value = PcUiState.Success("Downloaded: $fileName")
                } else {
                    _transferProgress.value = _transferProgress.value?.copy(error = result.error, isDone = true)
                    _uiState.value = PcUiState.Error("Download failed: ${result.error}")
                }
            } catch (e: Exception) {
                _transferProgress.value = _transferProgress.value?.copy(error = e.message, isDone = true)
                _uiState.value = PcUiState.Error("Download error: ${e.message}")
            }
        }
    }

    fun uploadFileStream(
        contentResolver : android.content.ContentResolver,
        uri             : android.net.Uri,
        fileSize        : Long,
        fileName        : String,
        remotePath      : String
    ) {
        viewModelScope.launch {
            _transferProgress.value = PcTransferProgress(fileName, fileSize.coerceAtLeast(0L), 0L, 0L, isUpload = true)
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: run {
                        _uiState.value = PcUiState.Error("Cannot open input stream")
                        return@launch
                    }
                val result = browse.uploadFile(
                    inputStream  = inputStream,
                    fileSize     = fileSize,
                    fileName     = fileName,
                    remotePath   = remotePath
                ) { done, total, speed ->
                    _transferProgress.value = PcTransferProgress(fileName, total, done, speed, isUpload = true)
                }
                if (result.success) {
                    _transferProgress.value = _transferProgress.value?.copy(isDone = true)
                    _uiState.value = PcUiState.Success("Uploaded: $fileName")
                } else {
                    _transferProgress.value = _transferProgress.value?.copy(error = result.error, isDone = true)
                    _uiState.value = PcUiState.Error("Upload failed: ${result.error}")
                }
            } catch (e: Exception) {
                _transferProgress.value = _transferProgress.value?.copy(error = e.message, isDone = true)
                _uiState.value = PcUiState.Error("Upload error: ${e.message}")
            }
        }
    }

    fun clearTransferProgress() { _transferProgress.value = null }

    // ── Open With dialog ───────────────────────────────────

    fun startOpenWithPolling() {
        openWithPollJob?.cancel()
        openWithPollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val r = api.pollOpenWithDialog()
                    if (r.success && r.data != null) {
                        _openWithDialog.value = r.data
                        openWithPollJob?.cancel()
                    }
                } catch (_: Exception) {}
                delay(1500)
            }
        }
    }

    fun dismissOpenWithDialog() { _openWithDialog.value = null }

    fun resolveOpenWith(exePath: String) {
        viewModelScope.launch {
            api.resolveOpenWithDialog(exePath)
            _openWithDialog.value = null
        }
    }

    // ─────────────────────────────────────────────────────
    //  INPUT — Touchpad / Keyboard
    // ─────────────────────────────────────────────────────

    // ── Mouse-delta coalescer ────────────────────────────────────
    // A touch screen generates finger samples at 60–120 Hz. Naïvely firing
    // a fresh HTTP POST per sample saturates OkHttp's per-host dispatcher
    // (5 concurrent) and each subsequent sample queues behind the last —
    // especially bad when the live-view MJPEG stream is also consuming
    // Wi-Fi bandwidth. Visible symptom: cursor lags behind the finger by
    // hundreds of milliseconds.
    //
    // Instead we keep at most ONE move request in flight. New deltas just
    // accumulate into [pendingDx]/[pendingDy] while a POST is outstanding,
    // and the next POST flushes whatever has been accrued since. End result:
    // finger motion is translated to cursor motion at network RTT, not
    // RTT × (samples in burst).
    @Volatile private var pendingDx: Float = 0f
    @Volatile private var pendingDy: Float = 0f
    private val mouseFlushLock = Any()
    private var mouseFlushJob: Job? = null

    fun sendMouseDelta(dx: Float, dy: Float) {
        synchronized(mouseFlushLock) {
            pendingDx += dx
            pendingDy += dy
            if (mouseFlushJob?.isActive == true) return@synchronized
            mouseFlushJob = viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val (sendDx, sendDy) = synchronized(mouseFlushLock) {
                        val sx = pendingDx; val sy = pendingDy
                        pendingDx = 0f; pendingDy = 0f
                        if (sx == 0f && sy == 0f) return@launch
                        sx to sy
                    }
                    try { input.moveMouse(sendDx, sendDy) }
                    catch (e: Exception) { android.util.Log.e("PcControl","sendMouseDelta: ${e.message}") }
                }
            }
        }
    }

    fun sendMouseClick(button: String = "left", double: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try { input.clickMouse(button, double) }
            catch (e: Exception) { android.util.Log.e("PcControl","sendMouseClick: ${e.message}") }
        }
    }

    fun sendMouseScroll(amount: Int, horizontal: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (horizontal) {
                    input.scrollMouse(amount, horizontal = true)
                } else {
                    input.scrollMouse(amount)
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl","sendMouseScroll: ${e.message}")
            }
        }
    }

    fun mouseButtonDown(button: String = "left") {
        viewModelScope.launch(Dispatchers.IO) {
            try { input.mouseButtonDown(button) }
            catch (e: Exception) { android.util.Log.e("PcControl","mouseButtonDown: ${e.message}") }
        }
    }

    fun mouseButtonUp(button: String = "left") {
        viewModelScope.launch(Dispatchers.IO) {
            try { input.mouseButtonUp(button) }
            catch (e: Exception) { android.util.Log.e("PcControl","mouseButtonUp: ${e.message}") }
        }
    }

    fun sendKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { input.pressKey(key) }
            catch (e: Exception) { android.util.Log.e("PcControl","sendKey: ${e.message}") }
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { input.typeText(text) }
            catch (e: Exception) { android.util.Log.e("PcControl","sendText: ${e.message}") }
        }
    }

    // ── NEW: Key hold / release for functional keyboard bar ──
    // These call the agent v10 /input/keyboard/hold and /input/keyboard/release endpoints.
    // Used by PcControlKeyboardUI for modifier keys (Shift, Ctrl, Alt, Win, AltGr)
    // that stay pressed until explicitly released.

    fun holdKey(keyName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                input.holdKey(keyName)
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "holdKey: ${e.message}")
            }
        }
    }

    fun releaseHeldKey(keyName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                input.releaseKey(keyName)
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "releaseHeldKey: ${e.message}")
            }
        }
    }

    fun releaseAllHeldKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                input.releaseKey("ALL")
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "releaseAllHeldKeys: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  VOLUME & BRIGHTNESS — slider control
    // ─────────────────────────────────────────────────────

    private val _volumeLevel    = MutableStateFlow(-1)
    val volumeLevel: StateFlow<Int> = _volumeLevel
    private val _volumeMuted    = MutableStateFlow(false)
    val volumeMuted: StateFlow<Boolean> = _volumeMuted
    private val _brightnessLevel = MutableStateFlow(-1)
    val brightnessLevel: StateFlow<Int> = _brightnessLevel

    fun fetchVolume() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = input.getVolume()
                if (r.success && r.data != null) {
                    val json = org.json.JSONObject(r.data)
                    _volumeLevel.value = json.optInt("volume", -1)
                    _volumeMuted.value = json.optBoolean("muted", false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "fetchVolume: ${e.message}")
            }
        }
    }

    fun setVolume(level: Int) {
        _volumeLevel.value = level
        viewModelScope.launch(Dispatchers.IO) {
            try { input.setVolume(level) }
            catch (e: Exception) { android.util.Log.e("PcControl", "setVolume: ${e.message}") }
        }
    }

    fun fetchBrightness() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = input.getBrightness()
                if (r.success && r.data != null) {
                    val json = org.json.JSONObject(r.data)
                    _brightnessLevel.value = json.optInt("brightness", -1)
                }
            } catch (e: Exception) {
                android.util.Log.e("PcControl", "fetchBrightness: ${e.message}")
            }
        }
    }

    fun setBrightness(level: Int) {
        _brightnessLevel.value = level
        viewModelScope.launch(Dispatchers.IO) {
            try { input.setBrightness(level) }
            catch (e: Exception) { android.util.Log.e("PcControl", "setBrightness: ${e.message}") }
        }
    }

    // ─────────────────────────────────────────────────────
    //  REAL-TIME REFRESH
    // ─────────────────────────────────────────────────────

    fun startRealTimeRefresh(intervalMs: Long = 3000L) {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (_connectionStatus.value == PcConnectionStatus.ONLINE) {
                    try {
                        val procs = api.getProcesses()
                        if (procs.success) {
                            val running = procs.data?.map { it.lowercase() }?.toSet() ?: emptySet()
                            _installedApps.value = _installedApps.value.map { app ->
                                app.copy(isRunning = running.any { r -> r in app.exePath.lowercase() })
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun stopRealTimeRefresh() {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = null
    }

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
        liveScreenJob        = null
        _liveScreenB64.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeRefresh()
        stopLiveScreen()
        openWithPollJob?.cancel()
        searchJob?.cancel()
        scanJob?.cancel()
        // Release any held keys when leaving
        releaseAllHeldKeys()
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