package com.amyrmo.damavand.lite.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.amyrmo.damavand.lite.GeoDownloader
import com.amyrmo.damavand.lite.LiteVpnService
import com.amyrmo.damavand.lite.PingTester
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.ProfileRepository
import com.amyrmo.damavand.lite.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Connection states (design spec §4). */
enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ConnectionUiState(
    val state: ConnState = ConnState.DISCONNECTED,
    val detail: String = "",
    val location: String = "",
    val countryCode: String = "",
    val pingMs: Long = -1,
    val downSpeed: Long = 0,
    val upSpeed: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
)

/**
 * LiteViewModel — the single state holder for all four Summit tabs.
 * It owns the service broadcasts, the profile list, the settings, the
 * geo download state and the 60-second live chart buffer.
 */
class LiteViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val repo = ProfileRepository(context)
    private val settings = SettingsStore(context)
    private val geo = GeoDownloader(context)

    // ---- connection ----
    private val _connection = MutableStateFlow(ConnectionUiState())
    val connection: StateFlow<ConnectionUiState> = _connection.asStateFlow()

    // ---- profiles ----
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    /** The single fastest profile by last test (lowest ping > 0). */
    val fastestProfile: Profile?
        get() = _profiles.value.filter { it.lastPingMs > 0 }.minByOrNull { it.lastPingMs }

    val selectedProfile: Profile?
        get() = _profiles.value.firstOrNull { it.id == _selectedId.value }
            ?: _profiles.value.firstOrNull()

    // ---- settings ----
    val killSwitch = settings.killSwitch
    val autoConnect = settings.autoConnect
    val batterySaver = settings.batterySaver
    val subAutoUpdate = settings.subAutoUpdate
    val preferredCore = settings.preferredCore

    // ---- geo ----
    val geoState = geo.state

    // ---- chart (60 s download history, bytes/sec, sampled 1 Hz while the
    // Stats tab is visible and connected; gaps are not back-filled). ----
    private val _chart = MutableStateFlow<List<Long>>(emptyList())
    val chart: StateFlow<List<Long>> = _chart.asStateFlow()
    private var statsVisible = false
    private var chartJob: Job? = null

    // ---- test-all ----
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()
    private val _testProgress = MutableStateFlow(0 to 0)
    val testProgress: StateFlow<Pair<Int, Int>> = _testProgress.asStateFlow()
    /** Profile ids currently being ping-tested (rows show «…»). */
    private val _testingIds = MutableStateFlow<Set<String>>(emptySet())
    val testingIds: StateFlow<Set<String>> = _testingIds.asStateFlow()

    // ---- subscription import ----
    private val _subImport = MutableStateFlow<SubImportState>(SubImportState.Idle)
    val subImport: StateFlow<SubImportState> = _subImport.asStateFlow()

    enum class SubImportState { Idle, Adding, Added, Failed }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != LiteVpnService.ACTION_STATUS) return
            val state = when (intent.getStringExtra("state")) {
                LiteVpnService.STATE_CONNECTED -> ConnState.CONNECTED
                LiteVpnService.STATE_CONNECTING -> ConnState.CONNECTING
                else -> ConnState.DISCONNECTED
            }
            val ping = intent.getLongExtra("ping", -1)
            val detail = intent.getStringExtra("detail") ?: ""
            _connection.update {
                it.copy(
                    state = if (state == ConnState.DISCONNECTED && detail.isNotBlank()) ConnState.ERROR else state,
                    detail = detail,
                    location = intent.getStringExtra("location") ?: "",
                    countryCode = intent.getStringExtra("countryCode") ?: "",
                    pingMs = ping,
                    downSpeed = intent.getLongExtra("downSpeed", 0),
                    upSpeed = intent.getLongExtra("upSpeed", 0),
                    totalDown = intent.getLongExtra("totalDown", 0),
                    totalUp = intent.getLongExtra("totalUp", 0),
                )
            }
            if (state == ConnState.CONNECTED && ping > 0) {
                val best = _bestSessionPing.value
                if (best < 0 || ping < best) _bestSessionPing.value = ping
            } else if (state == ConnState.DISCONNECTED) {
                _bestSessionPing.value = -1
            }
            updateChartSampling()
        }
    }

    init {
        // 1.1.1: every step is guarded — a failure in any one of them
        // (receiver, DataStore, WorkManager, broadcast) must never kill
        // the launch. Each is best-effort; the UI works without it.
        runCatching {
            val filter = IntentFilter(LiteVpnService.ACTION_STATUS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(statusReceiver, filter)
            }
        }
        viewModelScope.launch {
            runCatching { _selectedId.value = settings.selectedProfileIdNow() }
        }
        runCatching { refreshProfiles() }
        runCatching { geo.refreshState() }
        viewModelScope.launch {
            runCatching {
                if (settings.subAutoUpdateNow()) scheduleSubRefresh()
            }
        }
        // Ask the service for its current state (in case it is already running).
        runCatching {
            context.sendBroadcast(Intent(LiteVpnService.ACTION_QUERY).setPackage(context.packageName))
        }
    }

    override fun onCleared() {
        try { context.unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        chartJob?.cancel()
        super.onCleared()
    }

    // ---- profiles ----

    fun refreshProfiles() {
        _profiles.value = repo.loadProfiles()
    }

    fun selectProfile(id: String) {
        _selectedId.value = id
        viewModelScope.launch { settings.setSelectedProfileId(id) }
    }

    /** Connect to the selected profile (or the fastest tested one). */
    fun connect() {
        val profile = selectedProfile ?: fastestProfile
        val link = profile?.link
        viewModelScope.launch {
            val kill = settings.killSwitchNow()
            val intent = Intent(context, LiteVpnService::class.java).apply {
                action = LiteVpnService.ACTION_START
                putExtra(LiteVpnService.EXTRA_LINK, link)
                putExtra(LiteVpnService.EXTRA_KILL_SWITCH, kill)
            }
            context.startService(intent)
            _connection.update { it.copy(state = ConnState.CONNECTING, detail = "") }
        }
    }

    /** Connect to one specific profile (used by the Servers tab). */
    fun connectTo(profile: Profile) {
        selectProfile(profile.id)
        viewModelScope.launch {
            val kill = settings.killSwitchNow()
            val intent = Intent(context, LiteVpnService::class.java).apply {
                action = LiteVpnService.ACTION_START
                putExtra(LiteVpnService.EXTRA_LINK, profile.link)
                putExtra(LiteVpnService.EXTRA_KILL_SWITCH, kill)
            }
            context.startService(intent)
            _connection.update { it.copy(state = ConnState.CONNECTING, detail = "") }
        }
    }

    fun disconnect() {
        val intent = Intent(context, LiteVpnService::class.java).apply {
            action = LiteVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /** True when the system VPN permission is already granted. */
    fun vpnPrepared(): Boolean = VpnService.prepare(context) == null

    /** One auto-connect attempt on launch (spec §7.1): once, then stop on failure. */
    fun autoConnectOnce() {
        viewModelScope.launch {
            if (!settings.autoConnectNow()) return@launch
            if (_profiles.value.isEmpty()) return@launch
            if (_connection.value.state != ConnState.DISCONNECTED) return@launch
            connect()
        }
    }

    // ---- test all ----

    fun testAll() {
        if (_isTesting.value) return
        val list = _profiles.value
        if (list.isEmpty()) return
        _isTesting.value = true
        _testProgress.value = 0 to list.size
        _testingIds.value = list.map { it.id }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            val jobs = list.map { profile ->
                launch {
                    val ping = PingTester.ping(profile)
                    repo.updatePing(profile.id, ping)
                    withContext(Dispatchers.Main) {
                        refreshProfiles()
                        _testingIds.update { it - profile.id }
                        val (done, total) = _testProgress.value
                        _testProgress.value = (done + 1) to total
                    }
                }
            }
            jobs.forEach { it.join() }
            withContext(Dispatchers.Main) {
                _testingIds.value = emptySet()
                _isTesting.value = false
            }
        }
    }

    // ---- subscriptions ----

    fun addSubscription(url: String) {
        if (_subImport.value == SubImportState.Adding) return
        _subImport.value = SubImportState.Adding
        viewModelScope.launch(Dispatchers.IO) {
            val added = repo.addSubscription(url)
            withContext(Dispatchers.Main) {
                _subImport.value = if (added >= 0) SubImportState.Added else SubImportState.Failed
                if (added >= 0) refreshProfiles()
            }
        }
    }

    fun resetSubImport() {
        _subImport.value = SubImportState.Idle
    }

    fun refreshSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.refreshAll()
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    // ---- settings ----

    fun setKillSwitch(v: Boolean) = viewModelScope.launch { settings.setKillSwitch(v) }
    fun setAutoConnect(v: Boolean) = viewModelScope.launch { settings.setAutoConnect(v) }
    fun setBatterySaver(v: Boolean) = viewModelScope.launch { settings.setBatterySaver(v) }
    fun setSubAutoUpdate(v: Boolean) {
        viewModelScope.launch {
            settings.setSubAutoUpdate(v)
            if (v) scheduleSubRefresh() else cancelSubRefresh()
        }
    }

    fun setPreferredCore(v: String) = viewModelScope.launch { settings.setPreferredCore(v) }

    private fun scheduleSubRefresh() {
        // WorkManager.getInstance() throws if the startup initializer did
        // not run; never let that kill the launch.
        runCatching {
            val request = PeriodicWorkRequestBuilder<SubRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "lite-sub-refresh", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }

    private fun cancelSubRefresh() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork("lite-sub-refresh")
        }
    }

    // ---- geo ----

    fun downloadGeo() {
        viewModelScope.launch {
            geo.download { percent ->
                // Progress is reflected in geo.state (Downloading).
            }
        }
    }

    fun cancelGeoDownload() {
        geo.cancel()
    }

    // ---- chart ----

    fun setStatsVisible(visible: Boolean) {
        statsVisible = visible
        updateChartSampling()
    }

    private fun updateChartSampling() {
        val shouldSample = statsVisible && _connection.value.state == ConnState.CONNECTED
        if (shouldSample && chartJob == null) {
            chartJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val down = _connection.value.downSpeed
                    val next = (_chart.value + down).takeLast(60)
                    _chart.value = next
                }
            }
        } else if (!shouldSample) {
            chartJob?.cancel()
            chartJob = null
        }
    }

    /** Best (lowest) ping of the current session. */
    private val _bestSessionPing = MutableStateFlow(-1L)
    val bestSessionPing: StateFlow<Long> = _bestSessionPing.asStateFlow()
}
