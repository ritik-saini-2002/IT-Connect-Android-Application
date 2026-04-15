package com.example.ritik_2.nagios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ─── Data Models ─────────────────────────────────────────────────────────────

data class NagiosHost(
    val name: String,
    val address: String,
    val status: String,         // "UP", "DOWN", "UNREACHABLE"
    val lastCheck: String,
    val pluginOutput: String,
    val serviceCount: Int = 0
)

data class NagiosService(
    val hostName: String,
    val serviceName: String,
    val status: String,         // "OK", "WARNING", "CRITICAL", "UNKNOWN"
    val lastCheck: String,
    val pluginOutput: String
)

data class NagiosSummary(
    val hostsUp: Int,
    val hostsDown: Int,
    val hostsUnreachable: Int,
    val servicesOk: Int,
    val servicesWarning: Int,
    val servicesCritical: Int,
    val servicesUnknown: Int
)

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// ─── Nagios JSON API Response Models ─────────────────────────────────────────
// Nagios statusjson.cgi responses

data class StatusJsonResponse(
    @SerializedName("result") val result: ApiResult?,
    @SerializedName("data") val data: ApiData?
)

data class ApiResult(
    @SerializedName("type_code") val typeCode: Int?,
    @SerializedName("message") val message: String?
)

data class ApiData(
    @SerializedName("hostlist") val hostList: Map<String, HostDetail>?,
    @SerializedName("servicelist") val serviceList: Map<String, Map<String, ServiceDetail>>?
)

data class HostDetail(
    @SerializedName("name") val name: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("status") val status: Int?,           // 2=UP, 4=DOWN, 8=UNREACHABLE
    @SerializedName("last_check") val lastCheck: String?,
    @SerializedName("plugin_output") val pluginOutput: String?
)

data class ServiceDetail(
    @SerializedName("host_name") val hostName: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("status") val status: Int?,           // 2=OK, 4=WARNING, 8=UNKNOWN, 16=CRITICAL
    @SerializedName("last_check") val lastCheck: String?,
    @SerializedName("plugin_output") val pluginOutput: String?
)

// ─── Status code helpers ──────────────────────────────────────────────────────

fun Int?.toHostStatus(): String = when (this) {
    2    -> "UP"
    4    -> "DOWN"
    8    -> "UNREACHABLE"
    else -> "UNKNOWN"
}

fun Int?.toServiceStatus(): String = when (this) {
    2    -> "OK"
    4    -> "WARNING"
    8    -> "UNKNOWN"
    16   -> "CRITICAL"
    else -> "UNKNOWN"
}

// ─── Retrofit Interface ───────────────────────────────────────────────────────

interface NagiosApi {
    @GET("nagios/cgi-bin/statusjson.cgi")
    suspend fun getHostList(
        @Query("query") query: String = "hostlist",
        @Query("details") details: Boolean = true
    ): StatusJsonResponse

    @GET("nagios/cgi-bin/statusjson.cgi")
    suspend fun getServiceList(
        @Query("query") query: String = "servicelist",
        @Query("details") details: Boolean = true
    ): StatusJsonResponse
}

// ─── Repository ───────────────────────────────────────────────────────────────

class NagiosRepository(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val api: NagiosApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NagiosApi::class.java)
    }

    suspend fun fetchHosts(): List<NagiosHost> {
        val response = api.getHostList()
        return response.data?.hostList?.values?.map { h ->
            NagiosHost(
                name         = h.name ?: "Unknown",
                address      = h.address ?: "",
                status       = h.status.toHostStatus(),
                lastCheck    = h.lastCheck ?: "-",
                pluginOutput = h.pluginOutput ?: ""
            )
        } ?: emptyList()
    }

    suspend fun fetchServices(): List<NagiosService> {
        val response = api.getServiceList()
        val services = mutableListOf<NagiosService>()
        response.data?.serviceList?.forEach { (_, serviceMap) ->
            serviceMap.forEach { (_, s) ->
                services.add(
                    NagiosService(
                        hostName     = s.hostName ?: "Unknown",
                        serviceName  = s.description ?: "Unknown",
                        status       = s.status.toServiceStatus(),
                        lastCheck    = s.lastCheck ?: "-",
                        pluginOutput = s.pluginOutput ?: ""
                    )
                )
            }
        }
        return services
    }

    suspend fun fetchSummary(): NagiosSummary {
        val hosts    = fetchHosts()
        val services = fetchServices()
        return NagiosSummary(
            hostsUp           = hosts.count { it.status == "UP" },
            hostsDown         = hosts.count { it.status == "DOWN" },
            hostsUnreachable  = hosts.count { it.status == "UNREACHABLE" },
            servicesOk        = services.count { it.status == "OK" },
            servicesWarning   = services.count { it.status == "WARNING" },
            servicesCritical  = services.count { it.status == "CRITICAL" },
            servicesUnknown   = services.count { it.status == "UNKNOWN" }
        )
    }

    fun alerts(hosts: List<NagiosHost>, services: List<NagiosService>): List<String> {
        val list = mutableListOf<String>()
        hosts.filter { it.status != "UP" }.forEach {
            list.add("HOST:${it.name}:${it.status}:${it.pluginOutput}")
        }
        services.filter { it.status != "OK" }.forEach {
            list.add("SVC:${it.hostName}:${it.serviceName}:${it.status}:${it.pluginOutput}")
        }
        return list
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class NagiosViewModel(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : ViewModel() {

    private val repo = NagiosRepository(baseUrl, username, password)

    private val _hosts    = MutableStateFlow<UiState<List<NagiosHost>>>(UiState.Loading)
    private val _services = MutableStateFlow<UiState<List<NagiosService>>>(UiState.Loading)
    private val _summary  = MutableStateFlow<UiState<NagiosSummary>>(UiState.Loading)
    private val _filterQuery = MutableStateFlow("")

    val hosts:    StateFlow<UiState<List<NagiosHost>>>    = _hosts.asStateFlow()
    val services: StateFlow<UiState<List<NagiosService>>> = _services.asStateFlow()
    val summary:  StateFlow<UiState<NagiosSummary>>       = _summary.asStateFlow()
    val filterQuery: StateFlow<String>                    = _filterQuery.asStateFlow()

    // Filtered hosts derived from hosts + filterQuery
    val filteredHosts: StateFlow<UiState<List<NagiosHost>>> = combine(_hosts, _filterQuery) { state, query ->
        when (state) {
            is UiState.Success -> {
                val filtered = if (query.isBlank()) state.data
                else state.data.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true)
                }
                UiState.Success(filtered)
            }
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val alerts: StateFlow<List<NagiosService>> = _services.map { state ->
        when (state) {
            is UiState.Success -> state.data.filter { it.status != "OK" }
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected host for service drill-down
    private val _selectedHost = MutableStateFlow<String?>(null)
    val selectedHost: StateFlow<String?> = _selectedHost.asStateFlow()

    val servicesForSelectedHost: StateFlow<List<NagiosService>> = combine(_services, _selectedHost) { state, host ->
        when (state) {
            is UiState.Success -> if (host == null) emptyList()
                                  else state.data.filter { it.hostName == host }
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(30_000L) // refresh every 30 seconds
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _summary.value = UiState.Loading
                val hosts    = repo.fetchHosts()
                val services = repo.fetchServices()
                _hosts.value    = UiState.Success(hosts)
                _services.value = UiState.Success(services)
                _summary.value  = UiState.Success(repo.fetchSummary())
            } catch (e: Exception) {
                val msg = e.message ?: "Connection failed"
                _hosts.value    = UiState.Error(msg)
                _services.value = UiState.Error(msg)
                _summary.value  = UiState.Error(msg)
            }
        }
    }

    fun setFilter(query: String) {
        _filterQuery.value = query
    }

    fun selectHost(hostName: String?) {
        _selectedHost.value = hostName
    }

    // Factory
    class Factory(
        private val baseUrl: String,
        private val username: String,
        private val password: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NagiosViewModel(baseUrl, username, password) as T
        }
    }
}
