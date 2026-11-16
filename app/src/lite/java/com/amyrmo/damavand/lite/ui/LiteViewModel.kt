package com.amyrmo.damavand.lite.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.amyrmo.damavand.lite.AppLog
import com.amyrmo.damavand.lite.AutoSelect
import com.amyrmo.damavand.lite.AutoSelectIds
import com.amyrmo.damavand.lite.aether.AetherArgs
import com.amyrmo.damavand.lite.aether.AetherDelayTester
import com.amyrmo.damavand.lite.aether.AetherIdentityManager
import com.amyrmo.damavand.lite.aether.AetherKey
import com.amyrmo.damavand.lite.aether.AetherKeys
import com.amyrmo.damavand.lite.aether.AetherKeysSettings
import com.amyrmo.damavand.lite.aether.AetherSettings
import com.amyrmo.damavand.lite.BackupManager
import com.amyrmo.damavand.lite.GeoDownloader
import com.amyrmo.damavand.lite.LiteVpnService
import com.amyrmo.damavand.lite.LocaleHelper
import com.amyrmo.damavand.lite.PingTester
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.Chain
import com.amyrmo.damavand.lite.ProfileRepository
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.RoutingRule
import com.amyrmo.damavand.lite.RoutingStore
import com.amyrmo.damavand.lite.SettingsStore
import com.amyrmo.damavand.lite.Subscription
import com.amyrmo.damavand.lite.XrayConfig
import com.amyrmo.damavand.lite.XrayController
import com.amyrmo.damavand.lite.ui.screens.SortMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
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
    // Status/error text in the in-app language, not the system one.
    private val strings: Context get() = LocaleHelper.localized(context)
    private val repo = ProfileRepository(context)
    private val settings = SettingsStore(context)
    private val routingStore = RoutingStore(context)
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
        get() {
            // A chain or Auto selection is not a profile selection.
            if (_selectedId.value?.startsWith("chain:") == true) return null
            if (AutoSelectIds.isAuto(_selectedId.value)) return null
            return _profiles.value.firstOrNull { it.id == _selectedId.value }
                ?: _profiles.value.firstOrNull()
        }

    /** The selected Auto entry ("auto:all" / "auto:sub:<url>" / "auto:manual"), if any. */
    val selectedAutoId: String?
        get() = _selectedId.value?.takeIf { AutoSelectIds.isAuto(it) }

    /** The live Auto session (the core's measured members + switches). */
    val autoSession: StateFlow<AutoSelect.Session?> = AutoSelect.session

    fun selectAuto(id: String) {
        _selectedId.value = id
        viewModelScope.launch { settings.setSelectedProfileId(id) }
    }

    /** Pin one Auto member as the exit ("" = back to automatic). */
    fun pinAutoMember(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { AutoSelect.pin(tag) }
    }

    fun unpinAutoMember() {
        viewModelScope.launch(Dispatchers.IO) { AutoSelect.pin("") }
    }

    // ---- chains (3.0.0, PattNG PROXYCHAIN) ----
    private val _chains = MutableStateFlow<List<Chain>>(emptyList())
    val chains: StateFlow<List<Chain>> = _chains.asStateFlow()

    /** The selected chain, when the selection is "chain:<id>". */
    val selectedChain: Chain?
        get() {
            val raw = _selectedId.value ?: return null
            if (!raw.startsWith("chain:")) return null
            val id = raw.removePrefix("chain:")
            return _chains.value.firstOrNull { it.id == id }
        }

    fun refreshChains() {
        _chains.value = repo.loadChains()
    }

    /** Profiles picked for a new chain, in tap order (entry first, exit last). */
    private val _chainPick = MutableStateFlow<List<String>>(emptyList())
    val chainPick: StateFlow<List<String>> = _chainPick.asStateFlow()

    fun toggleChainPick(id: String) {
        _chainPick.update { pick ->
            if (id in pick) pick - id else pick + id
        }
    }

    fun cancelChainPick() {
        _chainPick.value = emptyList()
    }

    /** Servers tab search + sort, kept across tab switches. */
    val serversSearchQuery = mutableStateOf("")
    internal val serversSortMode = mutableStateOf(SortMode.FASTEST)

    fun createChainFromPick() {
        val pick = _chainPick.value
        if (pick.size < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.addChain(pick)
            withContext(Dispatchers.Main) {
                _chainPick.value = emptyList()
                refreshChains()
            }
        }
    }

    // ---- settings ----
    val killSwitch = settings.killSwitch
    val autoConnect = settings.autoConnect
    val batterySaver = settings.batterySaver
    val subAutoUpdate = settings.subAutoUpdate
    // 3.1.0 (PattNG): connection settings.
    val testUrl = settings.testUrl
    val mux = settings.mux
    val sniffing = settings.sniffing
    val fragment = settings.fragment
    val dnsServers = settings.dnsServers
    val routingMode = settings.routingMode
    val perAppMode = settings.perAppMode
    // 3.2.0 (PattNG parity).
    val socksPort = settings.socksPort
    val socksUser = settings.socksUser
    val socksPass = settings.socksPass
    val socksUdp = settings.socksUdp
    val socksShareLan = settings.socksShareLan
    val socksDynamicPort = settings.socksDynamicPort
    val mtu = settings.mtu
    val ifaceAddr = settings.ifaceAddr
    val httpProxy = settings.httpProxy
    val fakeDns = settings.fakeDns
    val localDns = settings.localDns
    val routeOnly = settings.routeOnly
    val coreLogLevel = settings.coreLogLevel
    val domainStrategy = settings.domainStrategy
    val muxConcurrency = settings.muxConcurrency
    val muxXudpConcurrency = settings.muxXudpConcurrency
    val muxXudpQuic = settings.muxXudpQuic
    val domesticDns = settings.domesticDns
    val dnsHosts = settings.dnsHosts
    val realPingConcurrency = settings.realPingConcurrency
    val ipApiUrl = settings.ipApiUrl
    val proxyOnly = settings.proxyOnly
    // 3.2.10 (PattNG root mode).
    val rootMode = settings.rootMode
    val rootLanShare = settings.rootLanShare
    // 3.2.11 (PattNG full parity): IPv6 + prefer-IPv6 + Aether listen port.
    val ipv6Enabled = settings.ipv6Enabled
    val preferIpv6 = settings.preferIpv6
    val aetherListenPort = settings.aetherListenPort
    // 3.2.11 (PattNG full parity): the WARP keys page's state — its
    // settings (loaded from SettingsStore), the keys in use, the log of
    // the renewal run, and whether one is running.
    private val _aetherKeysSettings = MutableStateFlow(AetherKeysSettings())
    val aetherKeysSettings = _aetherKeysSettings.asStateFlow()
    private val _aetherKeys = MutableStateFlow<List<AetherKey>>(emptyList())
    val aetherKeys = _aetherKeys.asStateFlow()
    private val _aetherKeysLog = MutableStateFlow<List<String>>(emptyList())
    val aetherKeysLog = _aetherKeysLog.asStateFlow()
    private val _aetherKeysRenewing = MutableStateFlow(false)
    val aetherKeysRenewing = _aetherKeysRenewing.asStateFlow()
    private val _aetherKeysNotice = MutableStateFlow<String?>(null)
    val aetherKeysNotice = _aetherKeysNotice.asStateFlow()
    private var aetherKeysRenewJob: Job? = null
    val showSpeedNotif = settings.showSpeedNotif
    val confirmDelete = settings.confirmDelete
    val showAllGroups = settings.showAllGroups
    // 3.2.7 (PattNG): two-column server list.
    val doubleColumn = settings.doubleColumn
    // 3.2.0: app language — "auto" (system), "fa" or "en".
    val language = settings.language
    // 3.2.5: day/night theme — "system", "dark" or "light".
    val themeMode = settings.themeMode
    // 3.2.8 (Auto): the selected group (Servers tab ↔ Home, shared so
    // fastest-connect tests that group's configs), + Auto tuning.
    val selectedGroupKey: StateFlow<String?> =
        settings.selectedGroup.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val autoProbeUrl = settings.autoProbeUrl
    val autoCheckSeconds = settings.autoCheckSeconds
    val autoSweepMinutes = settings.autoSweepMinutes
    val autoSwitchMargin = settings.autoSwitchMargin
    val autoRetry = settings.autoRetry

    // 3.2.9 (PattNG, the rest): dynamic color, local proxy, HEV tun.
    val dynamicColor = settings.dynamicColor
    val localProxy = settings.localProxy
    val useHev = settings.useHev

    fun selectGroup(key: String?) =
        viewModelScope.launch { settings.setSelectedGroup(key) }

    /**
     * The group fastest-connect (and Auto) works on: the stored group
     * when the user picked one, else the selected profile's
     * subscription, else every config ("__all__").
     */
    fun effectiveGroupKey(): String {
        selectedGroupKey.value?.let { return it }
        val id = _selectedId.value
        val profile = _profiles.value.firstOrNull { it.id == id }
        if (profile != null) return profile.subscriptionUrl
        return "__all__"
    }

    /** The profiles of one group ("__all__" = every config). */
    fun profilesForGroup(groupKey: String): List<Profile> =
        if (groupKey == "__all__") _profiles.value
        else _profiles.value.filter { it.subscriptionUrl == groupKey }

    fun setAutoProbeUrl(v: String) = viewModelScope.launch { settings.setAutoProbeUrl(v) }
    fun setAutoCheckSeconds(v: String) = viewModelScope.launch { settings.setAutoCheckSeconds(v) }
    fun setAutoSweepMinutes(v: String) = viewModelScope.launch { settings.setAutoSweepMinutes(v) }
    fun setAutoSwitchMargin(v: String) = viewModelScope.launch { settings.setAutoSwitchMargin(v) }
    fun setAutoRetry(v: Boolean) = viewModelScope.launch { settings.setAutoRetry(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }
    fun setLocalProxy(v: Boolean) = viewModelScope.launch { settings.setLocalProxy(v) }
    fun setUseHev(v: Boolean) = viewModelScope.launch { settings.setUseHev(v) }
    val autoStartBoot = settings.autoStartBoot
    val subAutoTest = settings.subAutoTest
    val subAutoSort = settings.subAutoSort
    val subAutoRemoveInvalid = settings.subAutoRemoveInvalid
    val hevLogLevel = settings.hevLogLevel
    val hevRwTimeout = settings.hevRwTimeout

    // ---- subscriptions (3.1.0, PattNG) ----
    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    fun refreshSubscriptions() {
        _subscriptions.value = repo.loadSubscriptions()
    }

    // ---- routing + per-app + usage (3.1.0, PattNG) ----
    private val _routingRules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val routingRules: StateFlow<List<RoutingRule>> = _routingRules.asStateFlow()

    private val _perAppPackages = MutableStateFlow<Set<String>>(emptySet())
    val perAppPackages: StateFlow<Set<String>> = _perAppPackages.asStateFlow()

    private val _usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usage: StateFlow<Map<String, Long>> = _usage.asStateFlow()

    fun refreshRouting() {
        _routingRules.value = routingStore.loadRouting().rules
    }

    fun refreshPerApp() {
        _perAppPackages.value = routingStore.loadPerApp().packages
    }

    fun refreshUsage() {
        _usage.value = routingStore.loadUsage()
    }

    // ---- logs (3.1.0, PattNG) ----
    val logLines = AppLog.lines

    fun clearLogs() = AppLog.clear()

    // ---- core error log (3.2.0): the Xray error log file, written by
    // the core itself (log.error in the generated config). ----
    private val _coreLogLines = MutableStateFlow<List<String>>(emptyList())
    val coreLogLines: StateFlow<List<String>> = _coreLogLines.asStateFlow()

    fun refreshCoreLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val lines = try {
                val f = java.io.File(context.filesDir, "xray-error.log")
                if (!f.exists()) emptyList()
                else f.readLines().takeLast(300)
            } catch (_: Exception) {
                emptyList()
            }
            _coreLogLines.value = lines
        }
    }

    fun clearCoreLog() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                java.io.File(context.filesDir, "xray-error.log").writeText("")
            } catch (_: Exception) {
            }
            _coreLogLines.value = emptyList()
        }
    }

    // ---- update check (3.2.8): compare with the newest v* release
    // of AMYRmo/DamavandLite — the app's own repo since the 2026-11-16
    // move (the old Damavand repo + lite-v* tags never match again). ----
    sealed interface UpdateCheckState {
        data object Idle : UpdateCheckState
        data object Checking : UpdateCheckState
        data class Result(
            val updateAvailable: Boolean,
            val tag: String,
            val url: String
        ) : UpdateCheckState
        data class Failed(val message: String) : UpdateCheckState
    }

    private val _updateCheck =
        MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    fun checkForUpdate() {
        if (_updateCheck.value is UpdateCheckState.Checking) return
        _updateCheck.value = UpdateCheckState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL(
                    "https://api.github.com/repos/AMYRmo/DamavandLite/releases?per_page=20"
                )
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                // Releases come newest-first; take the first v* tag.
                val tag = Regex("\"tag_name\"\\s*:\\s*\"(v[0-9.]+)\"")
                    .find(body)?.groupValues?.getOrNull(1)
                if (tag == null) {
                    _updateCheck.value = UpdateCheckState.Failed(strings.getString(R.string.update_not_found))
                } else {
                    val latest = tag.removePrefix("v")
                    val current = com.amyrmo.damavand.lite.BuildConfig.VERSION_NAME
                    val available = compareVersions(latest, current) > 0
                    _updateCheck.value = UpdateCheckState.Result(
                        updateAvailable = available,
                        tag = tag,
                        url = "https://github.com/AMYRmo/DamavandLite/releases/tag/$tag"
                    )
                }
            } catch (e: Exception) {
                _updateCheck.value =
                    UpdateCheckState.Failed(e.message ?: strings.getString(R.string.update_error))
            }
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }

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
    // 3.2.18 (#62): the running test-all job, so the user can cancel
    // it — before this the sweep kept no handle at all and the pill
    // was disabled while testing, so a long real test could not be
    // stopped. Set on the main thread (all entry points run there).
    private var testJob: Job? = null

    /** Cancel a running test-all / real-test-all (3.2.18, #62). */
    fun cancelTestAll() {
        testJob?.cancel()
    }

    // ---- subscription import ----
    private val _subImport = MutableStateFlow<SubImportState>(SubImportState.Idle)
    val subImport: StateFlow<SubImportState> = _subImport.asStateFlow()

    /**
     * 2.0.1: the failure carries its reason (a strings.xml message;
     * [Failed.count] fills %1$d when > 0) so the dialog can say WHY
     * the import failed instead of one generic error.
     */
    sealed interface SubImportState {
        data object Idle : SubImportState
        data object Adding : SubImportState
        data object Added : SubImportState
        data class Failed(val resId: Int, val count: Int = 0) : SubImportState
    }

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
        // 2.0.0: every step is guarded — a failure in any one of them
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
        runCatching { refreshChains() }
        runCatching { refreshSubscriptions() }
        runCatching { refreshRouting() }
        runCatching { refreshPerApp() }
        runCatching { refreshUsage() }
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

    fun selectChain(id: String) {
        _selectedId.value = "chain:$id"
        viewModelScope.launch { settings.setSelectedProfileId("chain:$id") }
    }

    /** Start the service with one link, a chain, fastest candidates, or an Auto group. */
    private fun startService(
        links: List<String>,
        candidates: List<String> = emptyList(),
        autoSelect: Boolean = false,
        autoId: String? = null,
        displayName: String? = null,
        // 3.2.10 (Aether): the single link is an Aether profile.
        aether: Boolean = false,
    ) {
        viewModelScope.launch {
            val kill = settings.killSwitchNow()
            val testUrl = settings.testUrlNow()
            val intent = Intent(context, LiteVpnService::class.java).apply {
                action = LiteVpnService.ACTION_START
                if (links.size == 1) {
                    putExtra(LiteVpnService.EXTRA_LINK, links[0])
                } else if (links.isNotEmpty()) {
                    putStringArrayListExtra(LiteVpnService.EXTRA_LINKS, ArrayList(links))
                } else if (candidates.isNotEmpty()) {
                    putStringArrayListExtra(LiteVpnService.EXTRA_CANDIDATES, ArrayList(candidates))
                }
                putExtra(LiteVpnService.EXTRA_TEST_URL, testUrl)
                putExtra(LiteVpnService.EXTRA_KILL_SWITCH, kill)
                // 3.2.8 (Auto): the service builds the autoselect
                // group from these links + the auto id.
                if (autoSelect) {
                    putExtra(LiteVpnService.EXTRA_AUTO_SELECT, true)
                    autoId?.let { putExtra(LiteVpnService.EXTRA_AUTO_ID, it) }
                }
                // 3.2.10 (Aether): the service builds the Aether
                // config and starts the core beside Xray.
                if (aether) putExtra(LiteVpnService.EXTRA_AETHER, true)
                displayName?.let { putExtra(LiteVpnService.EXTRA_NAME, it) }
            }
            context.startService(intent)
            _connection.update { it.copy(state = ConnState.CONNECTING, detail = "") }
        }
    }

    /**
     * Connect to the SELECTED entry (the ring's job): an Auto entry
     * connects through its group, a chain through its members, a
     * profile directly. Only when nothing is selected does it fall
     * back to the fastest tested candidates.
     */
    fun connect() {
        val autoId = selectedAutoId
        if (autoId != null) {
            connectToAuto(autoId)
            return
        }
        val chain = selectedChain
        if (chain != null) {
            connectToChain(chain)
            return
        }
        val profile = selectedProfile
        if (profile != null && _selectedId.value == profile.id) {
            // 3.2.10 (Aether): no pre-test, no ping — the core
            // measures its own endpoints. Root mode and Aether are
            // not combined in 3.2.10: refuse with a plain message.
            if (profile.isAether) {
                if (isRootModeNow()) {
                    _connection.update {
                        it.copy(state = ConnState.ERROR, detail = strings.getString(R.string.aether_root_unsupported))
                    }
                    return
                }
                startService(listOf(profile.link), aether = true)
                return
            }
            startService(listOf(profile.link))
            return
        }
        val candidates = _profiles.value
            .filter { !it.isAether && it.lastPingMs > 0 }
            .sortedBy { it.lastPingMs }
            .take(5)
            .map { it.link }
        if (candidates.isNotEmpty()) {
            startService(emptyList(), candidates)
        } else {
            // 3.2.10 (Aether): never the fallback pick — it has no
            // ping and no server to test.
            val link = profile?.takeIf { !it.isAether }?.link ?: fastestProfile?.link ?: return
            startService(listOf(link))
        }
    }

    /**
     * REAL fastest-connect (3.2.0 fix, 3.2.8 scope). The old fastest
     * path trusted stale lastPingMs — and worse, the Home "fastest"
     * button called connect(), which prefers the SELECTED profile, so
     * it never picked the fastest at all. This runs a FRESH parallel
     * ping sweep, saves the fresh pings once (batched, atomic — the
     * 3.1.1 fix), then sends the top 5 by FRESH ping as candidates
     * for the service's real tunnel test. Live progress shows on Home
     * while it sweeps.
     *
     * 3.2.8: the sweep covers ONLY the selected subscription's
     * configs (the group the user is looking at — Home's card /
     * the Servers tab's group), never every config in the app.
     * "All" sweeps everything.
     */
    fun connectFastest() {
        if (_isTesting.value) return
        if (_connection.value.state == ConnState.CONNECTING) return
        if (_connection.value.state == ConnState.CONNECTED) return
        // 3.2.10 (Aether): Aether profiles have no server to ping —
        // they never join the sweep.
        val list = profilesForGroup(effectiveGroupKey()).filter { !it.isAether }
        if (list.isEmpty()) {
            _connection.update {
                it.copy(state = ConnState.ERROR, detail = strings.getString(R.string.fastest_none))
            }
            return
        }
        _isTesting.value = true
        _testProgress.value = 0 to list.size
        _testingIds.value = list.map { it.id }.toSet()
        _connection.update {
            it.copy(
                state = ConnState.CONNECTING,
                detail = strings.getString(R.string.fastest_sweep, 0, list.size),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val concurrency = try {
                settings.realPingConcurrencyNow()
            } catch (_: Exception) {
                5
            }
            // 3.2.11 (PattNG prefer-IPv6): resolved once per sweep.
            val preferIpv6 = try {
                settings.preferIpv6Now()
            } catch (_: Exception) {
                false
            }
            val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)
            val results = java.util.concurrent.ConcurrentHashMap<String, Long>()
            val jobs = list.map { profile ->
                launch {
                    semaphore.withPermit {
                        val ping = PingTester.ping(profile, preferIpv6)
                        results[profile.id] = ping
                        withContext(Dispatchers.Main) {
                            _testingIds.update { it - profile.id }
                            val (done, total) = _testProgress.value
                            val next = (done + 1) to total
                            _testProgress.value = next
                            _connection.update {
                                it.copy(detail = strings.getString(R.string.fastest_sweep, next.first, next.second))
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            repo.updatePings(results)
            withContext(Dispatchers.Main) {
                refreshProfiles()
                _testingIds.value = emptySet()
                _isTesting.value = false
                // 3.2.8: rank only the swept scope, never every profile.
                val sweptIds = list.map { it.id }.toSet()
                val ranked = _profiles.value
                    .filter { it.id in sweptIds && it.lastPingMs > 0 }
                    .sortedBy { it.lastPingMs }
                if (ranked.isEmpty()) {
                    _connection.update {
                        it.copy(state = ConnState.ERROR, detail = strings.getString(R.string.fastest_none))
                    }
                    return@withContext
                }
                startService(emptyList(), ranked.take(5).map { it.link })
            }
        }
    }

    // ---- Auto (3.2.8, Damavand-faithful) ----
    /** The display label of one group (for the Auto card + notification). */
    fun groupLabel(groupKey: String): String = when (groupKey) {
        "__all__" -> strings.getString(R.string.servers_group_all)
        "" -> strings.getString(R.string.servers_group_manual)
        else -> _subscriptions.value.firstOrNull { it.url == groupKey }?.displayName
            ?: groupKey.substringAfter("://").substringBefore("/")
    }

    /**
     * Connect through an Auto entry: the core measures the group's
     * members itself and keeps switching to the healthiest one. One
     * member is just a direct connect (no group to measure).
     */
    fun connectToAuto(autoId: String) {
        val groupKey = try {
            AutoSelectIds.groupKeyOf(autoId)
        } catch (_: IllegalArgumentException) {
            return
        }
        // Only profiles that can be an autoselect member outbound: an
        // Aether profile (aether://) or a full custom JSON config has no
        // single outbound, and buildAutoSelect refuses the WHOLE group
        // when one member does not build — so one Aether/custom profile
        // in "All" (or a sub) made every Auto connect fail.
        val members = profilesForGroup(groupKey).filter { !it.isAether && it.proto != "custom" }
        if (members.isEmpty()) {
            _connection.update {
                it.copy(state = ConnState.ERROR, detail = strings.getString(R.string.fastest_none))
            }
            return
        }
        selectAuto(autoId)
        if (members.size == 1) {
            startService(listOf(members[0].link))
            return
        }
        startService(
            members.map { it.link },
            autoSelect = true,
            autoId = autoId,
            displayName = strings.getString(R.string.auto_card_title, groupLabel(groupKey)),
        )
    }

    /** Connect to one specific profile (used by the Servers tab). */
    fun connectTo(profile: Profile) {
        selectProfile(profile.id)
        startService(listOf(profile.link))
    }

    /** Connect to a chain: its member links, in entry-first order. */
    fun connectToChain(chain: Chain) {
        selectChain(chain.id)
        val links = chain.memberIds.mapNotNull { id ->
            _profiles.value.firstOrNull { it.id == id }?.link
        }
        if (links.size < 2) return // pruned below two members: not a chain
        startService(links)
    }

    fun disconnect() {
        val intent = Intent(context, LiteVpnService::class.java).apply {
            action = LiteVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /** True when the system VPN permission is already granted. */
    fun vpnPrepared(): Boolean = VpnService.prepare(context) == null

    // autoConnectOnce() ran already for this ViewModel (it outlives
    // activity recreation).
    private var autoConnectTried = false

    /** One auto-connect attempt on launch (spec §7.1): once, then stop on failure. */
    fun autoConnectOnce() {
        if (autoConnectTried) return
        autoConnectTried = true
        viewModelScope.launch {
            if (!settings.autoConnectNow()) return@launch
            if (_profiles.value.isEmpty()) return@launch
            if (_connection.value.state != ConnState.DISCONNECTED) return@launch
            connectFastest()
        }
    }

    // ---- test all ----

    fun testAll(onDone: (() -> Unit)? = null) {
        if (_isTesting.value) return
        // 3.2.12 (Amir): test ONLY the selected group's configs — the
        // subscription (or Manual) the user is looking at — not every
        // config in the app. ("__all__" still means every config.)
        val list = profilesForGroup(effectiveGroupKey())
        if (list.isEmpty()) return
        _isTesting.value = true
        _testProgress.value = 0 to list.size
        _testingIds.value = list.map { it.id }.toSet()
        // 3.2.18 (#62): the job is kept so the user can cancel the
        // sweep — see cancelTestAll().
        testJob = viewModelScope.launch(Dispatchers.IO) {
            // 3.2.0: cap the parallel tests (PattNG real-ping concurrency).
            val concurrency = try {
                settings.realPingConcurrencyNow()
            } catch (_: Exception) {
                5
            }
            val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)
            // 3.2.11 (PattNG prefer-IPv6): resolved once per test-all run.
            val preferIpv6 = try {
                settings.preferIpv6Now()
            } catch (_: Exception) {
                false
            }
            // 3.2.13 (PattNG AetherDelayTester): the Aether listen port
            // and the delay-test URL, once per run, for the Aether
            // profiles the sweep now measures through a test tunnel.
            val aetherPort = try {
                settings.aetherListenPortNow()
            } catch (_: Exception) {
                AetherArgs.SOCKS_PORT
            }
            val testUrl = try {
                settings.testUrlNow()
            } catch (_: Exception) {
                "https://www.google.com/generate_204"
            }
            // 3.1.1 — ping in parallel, but collect the results and
            // save ONCE at the end: per-profile updatePing from parallel
            // coroutines raced on profiles.json and wiped every config
            // on-device (3.1.0). The file itself is also locked +
            // atomically written now (ProfileRepository).
            val results = java.util.concurrent.ConcurrentHashMap<String, Long>()
            try {
                val jobs = list.map { profile ->
                    launch {
                        semaphore.withPermit {
                            val ping = if (profile.isAether) {
                                // 3.2.13 (PattNG AetherDelayTester): measured
                                // through a test tunnel, not the server ping
                                // — the core has no server to ping. Refused
                                // beside a live Aether session; the tester
                                // serialises the Aether profiles itself.
                                if (aetherSessionLive()) -1L
                                else AetherDelayTester.measure(
                                    context,
                                    AetherSettings.fromJson(profile.aetherJson),
                                    aetherPort,
                                    testUrl,
                                )
                            } else {
                                PingTester.ping(profile, preferIpv6)
                            }
                            results[profile.id] = ping
                            withContext(Dispatchers.Main) {
                                _testingIds.update { it - profile.id }
                                val (done, total) = _testProgress.value
                                _testProgress.value = (done + 1) to total
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
            } finally {
                // 3.2.18 (#62): results measured so far are kept even
                // when cancelled, and the testing state always resets
                // — without this a cancel would leave the pill stuck
                // on "Testing…". The Main switch is NonCancellable so
                // the reset itself is not skipped by the cancel.
                if (results.isNotEmpty()) repo.updatePings(results)
                withContext(NonCancellable + Dispatchers.Main) {
                    refreshProfiles()
                    _testingIds.value = emptySet()
                    _isTesting.value = false
                    testJob = null
                }
            }
            // Reached only on a full run — a cancel throws out of the
            // try above, so post-test actions (onDone) are skipped.
            withContext(Dispatchers.Main) { onDone?.invoke() }
        }
    }

    /**
     * Test all with REAL ping (3.2.0, PattNG TestAllRealPing): a real
     * tunnel test per profile — the delay-test URL fetched through the
     * profile's own Xray instance. Sequential (the core is a singleton)
     * and refused while connected, because starting a test instance
     * would stop the live tunnel.
     */
    fun testAllRealPing() {
        if (_isTesting.value) return
        if (_connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        ) return
        // 3.2.10 (Aether): Aether profiles have no server to test.
        // 3.2.12 (Amir): same scoping as testAll — only the selected
        // group's configs, not every config in the app.
        val list = profilesForGroup(effectiveGroupKey()).filter { !it.isAether }
        if (list.isEmpty()) return
        _isTesting.value = true
        _testProgress.value = 0 to list.size
        _testingIds.value = list.map { it.id }.toSet()
        // 3.2.18 (#62): the job is kept so the user can cancel the
        // sweep — see cancelTestAll().
        testJob = viewModelScope.launch(Dispatchers.IO) {
            val geoReady = GeoDownloader(context).isReady()
            val settingsSnapshot = try {
                settings.xraySettingsNow(RoutingStore(context))
            } catch (_: Exception) {
                XrayConfig.XraySettings()
            }
            val testUrl = try { settings.testUrlNow() } catch (_: Exception) {
                "https://www.google.com/generate_204"
            }
            val results = mutableMapOf<String, Long>()
            try {
                for (profile in list) {
                    // 3.2.18 (#62): stop between profiles when cancelled.
                    currentCoroutineContext().ensureActive()
                    val ping = realPingOne(profile, geoReady, settingsSnapshot, testUrl)
                    results[profile.id] = ping
                    withContext(Dispatchers.Main) {
                        _testingIds.update { it - profile.id }
                        val (done, total) = _testProgress.value
                        _testProgress.value = (done + 1) to total
                    }
                }
            } finally {
                // 3.2.18 (#62): results measured so far are kept even
                // when cancelled, and the testing state always resets
                // (same shape as testAll above).
                if (results.isNotEmpty()) repo.updatePings(results)
                withContext(NonCancellable + Dispatchers.Main) {
                    refreshProfiles()
                    _testingIds.value = emptySet()
                    _isTesting.value = false
                    testJob = null
                }
            }
        }
    }

    /** One real tunnel test: fetch the test URL through this profile. */
    // 3.2.18 (#62): this is now a SUSPEND function and cooperative —
    // a cancel ends the SOCKS wait and aborts the in-flight fetch
    // instead of blocking up to the 8 s read timeout. The finally
    // below still stops the test Xray core on every path.
    private suspend fun realPingOne(
        profile: Profile,
        geoReady: Boolean,
        settingsSnapshot: XrayConfig.XraySettings,
        testUrl: String,
    ): Long {
        currentCoroutineContext().ensureActive()
        // The service is not running here (testAllRealPing refuses while
        // connected), so the fixed test port 1180 cannot collide.
        val cfg = XrayConfig.buildLinks(listOf(profile.link), 1180, geoReady, settingsSnapshot)
            ?: return -1
        if (!XrayController.start(cfg)) return -1
        return try {
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 3000) {
                try {
                    java.net.Socket().use {
                        it.connect(java.net.InetSocketAddress("127.0.0.1", 1180), 300)
                    }
                    break
                } catch (_: Exception) {
                    delay(200)
                }
            }
            currentCoroutineContext().ensureActive()
            val fetchStart = System.currentTimeMillis()
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.SOCKS,
                java.net.InetSocketAddress("127.0.0.1", 1180)
            )
            val conn = java.net.URL(testUrl).openConnection(proxy) as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = false
            // 3.2.18 (#62): responseCode is a blocking call, so the
            // fetch runs inside suspendCancellableCoroutine — cancelling
            // the test disconnects the connection at once instead of
            // waiting out the read timeout. (resume/resumeWithException
            // are the CancellableContinuation members — the
            // kotlinx.coroutines.resume import resolves to an internal
            // overload instead, so no import for them.)
            val code = suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation {
                    try { conn.disconnect() } catch (_: Exception) { }
                }
                try {
                    val c = conn.responseCode
                    if (cont.isActive) cont.resume(c, null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
            conn.disconnect()
            if (code == 204 || code == 200) System.currentTimeMillis() - fetchStart else -1
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            -1
        } finally {
            XrayController.stop()
        }
    }

    // ---- 3.2.0 (PattNG more menu) ----

    /** Restart the service with the current selection (PattNG RestartService). */
    fun restartService() {
        val wasActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        if (!wasActive) {
            connect()
            return
        }
        disconnect()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            if (_connection.value.state == ConnState.DISCONNECTED) connect()
        }
    }

    /** Delete every profile and chain (PattNG DeleteAll). */
    fun deleteAllProfiles() {
        val ids = _profiles.value.map { it.id }.toSet()
        if (ids.isEmpty()) return
        val isActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        if (isActive) disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.deleteProfile(it) }
            repo.loadChains().forEach { repo.deleteChain(it.id) }
            withContext(Dispatchers.Main) {
                _selectedId.value = null
                viewModelScope.launch { settings.setSelectedProfileId(null) }
                refreshProfiles()
                refreshChains()
            }
        }
    }

    /** Delete duplicate profiles, keeping the first of each link (PattNG DeleteDuplicate). */
    fun deleteDuplicateProfiles() {
        val seen = mutableSetOf<String>()
        val dupIds = _profiles.value.filter { !seen.add(it.link) }.map { it.id }.toSet()
        deleteProfiles(dupIds)
    }

    /**
     * Delete invalid profiles (PattNG DeleteInvalid): unparseable links,
     * or profiles that were tested and failed (ping -1 after a test).
     * Never-tested profiles are kept.
     */
    fun deleteInvalidProfiles() {
        val badIds = _profiles.value.filter {
            // 3.2.10 (Aether): an Aether profile's link is not a
            // share link — it is never invalid by the parser.
            !it.isAether && (XrayConfig.parse(it.link) == null ||
                (it.lastPingMs == -1L && it.lastTestedAt > 0))
        }.map { it.id }.toSet()
        deleteProfiles(badIds)
    }

    /** All profile links, one per line, for the share sheet (PattNG ExportAll). */
    fun exportAllText(): String = _profiles.value.joinToString("\n") { it.link }

    /** Persist the stored order fastest-first (PattNG auto-sort after test). */
    fun sortProfilesByPing() {
        viewModelScope.launch(Dispatchers.IO) {
            // 3.2.16 (#53): one atomic load-transform-save, so a
            // subscription refresh landing mid-sort is not lost.
            repo.updateProfiles { profiles ->
                profiles.sortedWith(
                    compareBy<Profile> { it.lastPingMs <= 0 }.thenBy { it.lastPingMs }
                )
            }
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    /**
     * True while the live session runs on an Aether core (3.2.13): an
     * Aether delay test must not start then — AetherEngine.start stops
     * any running session first and would kill the tunnel.
     */
    private fun aetherSessionLive(): Boolean {
        val state = _connection.value.state
        if (state != ConnState.CONNECTED && state != ConnState.CONNECTING) return false
        if (selectedProfile?.isAether == true) return true
        val chain = selectedChain ?: return false
        return chain.memberIds.any { id ->
            _profiles.value.firstOrNull { it.id == id }?.isAether == true
        }
    }

    /** Ping one profile (3.0.0, PattNG per-config test). */
    fun testOne(profile: Profile) {
        if (profile.id in _testingIds.value) return
        _testingIds.update { it + profile.id }
        viewModelScope.launch(Dispatchers.IO) {
            val ping = if (profile.isAether) {
                // 3.2.13 (PattNG AetherDelayTester): a test tunnel on
                // the Aether listen port; refused beside a live Aether
                // session, whose core holds that port.
                if (aetherSessionLive()) -1L
                else AetherDelayTester.measure(
                    context,
                    AetherSettings.fromJson(profile.aetherJson),
                    try { settings.aetherListenPortNow() } catch (_: Exception) { AetherArgs.SOCKS_PORT },
                    try { settings.testUrlNow() } catch (_: Exception) { "https://www.google.com/generate_204" },
                )
            } else {
                // 3.2.11 (PattNG prefer-IPv6).
                val preferIpv6 = try {
                    settings.preferIpv6Now()
                } catch (_: Exception) {
                    false
                }
                PingTester.ping(profile, preferIpv6)
            }
            repo.updatePing(profile.id, ping)
            withContext(Dispatchers.Main) {
                refreshProfiles()
                _testingIds.update { it - profile.id }
            }
        }
    }

    /** Delete one profile (3.0.0, PattNG delete): disconnect first when
     *  it is the active selection, then delete + fix the selection. */
    fun deleteProfile(profile: Profile) {
        val isSelected = _selectedId.value == profile.id
        val isActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        if (isSelected && isActive) disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteProfile(profile.id)
            withContext(Dispatchers.Main) {
                if (isSelected) {
                    val next = repo.loadProfiles().firstOrNull()?.id
                    _selectedId.value = next
                    viewModelScope.launch { settings.setSelectedProfileId(next) }
                }
                refreshProfiles()
                refreshChains()
                // The selected chain may have lost a member (or been
                // deleted by the prune); clear a stale chain selection.
                if (_selectedId.value?.startsWith("chain:") == true && selectedChain == null) {
                    val next = _profiles.value.firstOrNull()?.id
                    _selectedId.value = next
                    viewModelScope.launch { settings.setSelectedProfileId(next) }
                }
            }
        }
    }

    /** Delete a chain (3.0.0, PattNG delete). */
    fun deleteChain(id: String) {
        val isSelected = _selectedId.value == "chain:$id"
        val isActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        if (isSelected && isActive) disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteChain(id)
            withContext(Dispatchers.Main) {
                if (isSelected) {
                    _selectedId.value = _profiles.value.firstOrNull()?.id
                    viewModelScope.launch { settings.setSelectedProfileId(_selectedId.value) }
                }
                refreshChains()
            }
        }
    }

    // ---- subscriptions ----

    fun addSubscription(url: String) {
        if (_subImport.value == SubImportState.Adding) return
        _subImport.value = SubImportState.Adding
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.addSubscription(url)
            withContext(Dispatchers.Main) {
                _subImport.value = when (result) {
                    is ProfileRepository.FetchResult.Ok -> SubImportState.Added
                    ProfileRepository.FetchResult.NetworkError ->
                        SubImportState.Failed(R.string.sub_import_failed_network)
                    is ProfileRepository.FetchResult.NoUsableLinks ->
                        if (result.skippedUnsupported > 0) {
                            SubImportState.Failed(
                                R.string.sub_import_failed_unsupported,
                                result.skippedUnsupported
                            )
                        } else {
                            SubImportState.Failed(R.string.sub_import_failed_empty)
                        }
                    ProfileRepository.FetchResult.SingleLinkInvalid ->
                        SubImportState.Failed(R.string.sub_import_failed_single)
                }
                if (result is ProfileRepository.FetchResult.Ok) {
                    refreshProfiles()
                    refreshSubscriptions()
                }
            }
        }
    }

    fun resetSubImport() {
        _subImport.value = SubImportState.Idle
    }

    /** Add one hand-built config link (3.2.0 manual editor): goes
     * straight to the link parser, never through the subscription
     * fetcher — so http:// proxy links are not mistaken for subs. */
    fun addManualLink(link: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.addSingleLinks(link.trim())
            val ok = result is ProfileRepository.FetchResult.Ok &&
                (result as ProfileRepository.FetchResult.Ok).profiles.isNotEmpty()
            if (ok) refreshProfiles()
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** Add a standalone Aether profile (3.2.10): its link is
     * "aether://<uuid>" and its settings ride in aetherJson — the
     * link parser never sees it. */
    fun addAetherProfile(name: String, settings: AetherSettings, onResult: (Profile?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = Profile.aether(
                name.trim().ifEmpty { "Aether" },
                AetherSettings.toJson(settings),
            )
            repo.updateProfiles { it + profile }
            withContext(Dispatchers.Main) {
                refreshProfiles()
                onResult(profile)
            }
        }
    }

    /** Update an Aether profile's name and settings (3.2.10). */
    fun updateAetherProfile(id: String, name: String, settings: AetherSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = repo.loadProfiles().firstOrNull { it.id == id } ?: return@launch
            repo.updateProfile(
                old.copy(
                    name = name.trim().ifEmpty { old.name },
                    aetherJson = AetherSettings.toJson(settings),
                )
            )
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    // -- WARP keys (3.2.11, PattNG ServerAetherKeysActivity) ------------

    /** The SOCKS port of the Xray the keys renewal dials out through, when it names an exit-node profile. */
    private val renewExitPort = 10825

    /** Loads the keys page's settings and the keys in use (call when the keys dialog opens). */
    fun loadAetherKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            _aetherKeysSettings.value = settings.aetherKeysSettingsNow()
            _aetherKeys.value = AetherIdentityManager.keys(context)
        }
    }

    /** Saves one change of the keys page's settings (the dialog calls this on every edit). */
    fun setAetherKeysSettings(next: AetherKeysSettings) {
        if (next == _aetherKeysSettings.value) return
        _aetherKeysSettings.value = next
        viewModelScope.launch { settings.setAetherKeysSettings(next.toJson()) }
    }

    /** Clears the keys page's notice (the dialog calls this once shown). */
    fun clearAetherKeysNotice() {
        _aetherKeysNotice.value = null
    }

    private fun aetherKeysAppendLog(line: String) {
        _aetherKeysLog.update { (it + line).takeLast(200) }
    }

    /**
     * Gets new keys as the settings say: registers them with a run of the core, which dials out through the exit-node
     * of the settings, and puts them in place of the keys in use once all of them are ready.
     *
     * Lite adaptations (PattNG renews through a second Xray instance; Lite has none):
     * - While a tunnel is connected the renewal is refused: the exit Xray the run needs would end the tunnel, and
     *   the keys in use belong to the running session.
     * - With an exit-node profile named, the exit runs on Lite's own XrayController on [renewExitPort] and is
     *   stopped again after the run, whatever the outcome.
     */
    fun renewAetherKeys() {
        if (_aetherKeysRenewing.value) return
        val current = _aetherKeysSettings.value
        AetherKeys.problem(current)?.let { problem ->
            _aetherKeysNotice.value = strings.getString(
                when (problem) {
                    AetherKeys.Problem.INVALID_ENROLL_ADDRESS -> R.string.aether_keys_invalid_enroll_address
                    AetherKeys.Problem.INVALID_API_SNI -> R.string.aether_keys_invalid_api_sni
                    AetherKeys.Problem.INVALID_API_HOST -> R.string.aether_keys_invalid_api_host
                    AetherKeys.Problem.INVALID_FRAGMENT -> R.string.aether_keys_invalid_fragment
                    AetherKeys.Problem.INVALID_ECH_DNS -> R.string.aether_keys_invalid_ech_dns
                    AetherKeys.Problem.INVALID_ECH_DOMAIN -> R.string.aether_keys_invalid_ech_domain
                    AetherKeys.Problem.INVALID_FINAL_MASK -> R.string.aether_keys_invalid_final_mask
                    AetherKeys.Problem.INVALID_COMMAND -> R.string.aether_keys_invalid_command
                }
            )
            return
        }
        val arguments = AetherKeys.runArguments(current)
        val kind = AetherKeys.kindOf(arguments) ?: return
        _aetherKeysRenewing.value = true
        aetherKeysRenewJob = viewModelScope.launch {
            var exitStarted = false
            try {
                if (_connection.value.state == ConnState.CONNECTED || _connection.value.state == ConnState.CONNECTING) {
                    _aetherKeysNotice.value = strings.getString(R.string.aether_keys_renew_blocked)
                    return@launch
                }
                // The profile chosen as the exit-node may be renamed or gone since the page opened, or share its name
                // with another by now; the run would reach WARP without it.
                val node = current.exitNode.trim()
                var exitPort: Int? = null
                if (node.isNotEmpty()) {
                    val matches = profiles.value.filter { it.name == node }
                    val exitProfile = matches.singleOrNull()
                    if (exitProfile == null || exitProfile.isAether) {
                        _aetherKeysNotice.value = strings.getString(R.string.aether_keys_exit_missing, node)
                        return@launch
                    }
                    val cfg = XrayConfig.build(
                        exitProfile.link,
                        renewExitPort,
                        geoAvailable = false,
                        settings = settings.xraySettingsNow(RoutingStore(context)),
                    )
                    if (cfg == null || !XrayController.start(cfg)) {
                        _aetherKeysNotice.value = strings.getString(R.string.aether_keys_exit_failed, node)
                        return@launch
                    }
                    exitStarted = true
                    exitPort = renewExitPort
                }
                aetherKeysAppendLog(strings.getString(R.string.aether_keys_renewing))
                val keys = AetherIdentityManager.renew(
                    context = context,
                    kind = kind,
                    arguments = arguments,
                    exit = current.exit,
                    exitPort = exitPort,
                    onOutput = ::aetherKeysAppendLog,
                )
                if (keys == null) {
                    aetherKeysAppendLog(strings.getString(R.string.aether_keys_renew_failed))
                } else {
                    // By now the core and the exit it dialled out through have ended, and the new keys are in place.
                    aetherKeysAppendLog(strings.getString(R.string.aether_keys_renewed))
                    _aetherKeys.value = AetherIdentityManager.keys(context)
                    _aetherKeysNotice.value = strings.getString(R.string.aether_keys_renewed)
                }
            } catch (e: CancellationException) {
                // By now the core and what it started have ended. A run cancelled once every new key was ready has put
                // them in place all the same, so the keys in use are shown where they changed.
                withContext(NonCancellable) { _aetherKeys.value = AetherIdentityManager.keys(context) }
                throw e
            } finally {
                if (exitStarted) XrayController.stop()
                _aetherKeysRenewing.value = false
            }
        }
    }

    /**
     * Stops getting new keys: the core that registers them ends, with whatever it started, and until then the run
     * shows as going on. The keys in use stay, unless every new key was ready already.
     */
    fun cancelAetherKeysRenewal() {
        val job = aetherKeysRenewJob ?: return
        if (!_aetherKeysRenewing.value || job.isCancelled) return
        aetherKeysAppendLog(strings.getString(R.string.aether_keys_renew_cancelled))
        job.cancel()
    }

    /** Re-fetch every auto-update subscription from the network. */
    fun refreshAllSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.refreshAll()
            withContext(Dispatchers.Main) {
                refreshProfiles()
                refreshSubscriptions()
                runSubscriptionAutoActions()
            }
        }
    }

    /** Re-fetch ONE subscription from the network. */
    fun updateSubscription(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.refreshOne(url)
            withContext(Dispatchers.Main) {
                refreshProfiles()
                refreshSubscriptions()
                runSubscriptionAutoActions()
            }
        }
    }

    /**
     * PattNG subscription post-update actions (3.2.0): auto-test after
     * an update, then auto-sort and auto-remove-invalid once the test
     * finishes. Each toggle is independent.
     */
    private fun runSubscriptionAutoActions() {
        viewModelScope.launch {
            val autoTest = try { settings.subAutoTestNow() } catch (_: Exception) { true }
            val autoSort = try { settings.subAutoSortNow() } catch (_: Exception) { false }
            val autoRemove = try { settings.subAutoRemoveInvalidNow() } catch (_: Exception) { false }
            if (!autoTest && !autoSort && !autoRemove) return@launch
            if (autoTest) {
                testAll {
                    if (autoSort) sortProfilesByPing()
                    if (autoRemove) deleteInvalidProfiles()
                }
            } else {
                if (autoSort) sortProfilesByPing()
                if (autoRemove) deleteInvalidProfiles()
            }
        }
    }

    fun renameSubscription(url: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.renameSubscription(url, name)
            withContext(Dispatchers.Main) { refreshSubscriptions() }
        }
    }

    fun setSubscriptionAutoUpdate(url: String, autoUpdate: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setSubscriptionAutoUpdate(url, autoUpdate)
            withContext(Dispatchers.Main) { refreshSubscriptions() }
        }
    }

    /** Delete a subscription and every profile that came from it. */
    fun deleteSubscription(url: String) {
        val isActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        val selected = _selectedId.value
        val selectedIsGone = selected != null &&
            _profiles.value.firstOrNull { it.id == selected }?.subscriptionUrl == url
        if (isActive && selectedIsGone) disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteSubscription(url)
            withContext(Dispatchers.Main) {
                refreshProfiles()
                refreshChains()
                refreshSubscriptions()
                if (selectedIsGone) {
                    _selectedId.value = _profiles.value.firstOrNull()?.id
                    viewModelScope.launch { settings.setSelectedProfileId(_selectedId.value) }
                }
            }
        }
    }

    // ---- profile management (3.1.0, PattNG) ----

    fun duplicateProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.duplicateProfile(profile)
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.renameProfile(id, name)
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    /** Replace a profile's link; returns false when it does not parse. */
    fun updateProfileLink(id: String, newLink: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repo.updateProfileLink(id, newLink)
            withContext(Dispatchers.Main) {
                if (ok) refreshProfiles()
                onResult(ok)
            }
        }
    }

    fun moveProfile(id: String, subscriptionUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.moveProfile(id, subscriptionUrl)
            withContext(Dispatchers.Main) { refreshProfiles() }
        }
    }

    /** Delete several profiles at once (multi-select). */
    fun deleteProfiles(ids: Set<String>) {
        if (ids.isEmpty()) return
        val isActive = _connection.value.state == ConnState.CONNECTED ||
            _connection.value.state == ConnState.CONNECTING
        if (isActive && _selectedId.value in ids) disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.deleteProfile(it) }
            withContext(Dispatchers.Main) {
                if (_selectedId.value in ids) {
                    _selectedId.value = _profiles.value.firstOrNull()?.id
                    viewModelScope.launch { settings.setSelectedProfileId(_selectedId.value) }
                }
                refreshProfiles()
                refreshChains()
            }
        }
    }

    // ---- routing (3.1.0, PattNG) ----

    fun setRoutingMode(mode: String) {
        viewModelScope.launch {
            settings.setRoutingMode(mode)
            routingStore.setRoutingMode(mode)
        }
    }

    fun addRoutingRule(name: String, pattern: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            routingStore.addRule(
                RoutingRule(
                    id = "rule-" + System.currentTimeMillis().toString(16),
                    name = name.trim().ifEmpty { pattern.trim() },
                    pattern = pattern.trim(),
                    action = action,
                )
            )
            withContext(Dispatchers.Main) { refreshRouting() }
        }
    }

    fun toggleRoutingRule(rule: RoutingRule) {
        viewModelScope.launch(Dispatchers.IO) {
            routingStore.updateRule(rule.copy(enabled = !rule.enabled))
            withContext(Dispatchers.Main) { refreshRouting() }
        }
    }

    fun deleteRoutingRule(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            routingStore.deleteRule(id)
            withContext(Dispatchers.Main) { refreshRouting() }
        }
    }

    /**
     * Import a predefined routing ruleset (3.2.0, PattNG): bypass-Iran
     * (Iranian domains + IPs direct) or block-ads. Existing patterns
     * are not duplicated.
     */
    fun importRoutingPreset(preset: String) {
        val rules: List<Triple<String, String, String>> = when (preset) {
            "bypass_ir" -> listOf(
                Triple(strings.getString(R.string.routing_preset_iran_domains), "geosite:geolocation-ir", "direct"),
                Triple(strings.getString(R.string.routing_preset_iran_ips), "geoip:ir", "direct"),
            )
            "block_ads" -> listOf(
                Triple(strings.getString(R.string.routing_preset_ads), "geosite:category-ads-all", "block"),
            )
            else -> return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _routingRules.value.map { it.pattern }.toSet()
            rules.filter { (_, pattern, _) -> pattern !in existing }.forEach { (name, pattern, action) ->
                routingStore.addRule(
                    RoutingRule(
                        id = "rule-" + System.currentTimeMillis().toString(16) +
                            "-" + pattern.hashCode().toString(16),
                        name = name,
                        pattern = pattern,
                        action = action,
                    )
                )
            }
            withContext(Dispatchers.Main) { refreshRouting() }
        }
    }

    // ---- per-app proxy (3.1.0, PattNG) ----

    fun setPerAppMode(mode: String) {
        viewModelScope.launch {
            settings.setPerAppMode(mode)
            routingStore.setPerAppMode(mode)
        }
    }

    fun togglePerAppPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            routingStore.togglePerAppPackage(packageName)
            withContext(Dispatchers.Main) { refreshPerApp() }
        }
    }

    /** Launchable apps, sorted by label, for the per-app picker. */
    fun installedApps(): List<Pair<String, String>> {
        return try {
            val pm = context.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolve = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launchIntent, 0)
            }
            resolve.mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString() ?: pkg
                label to pkg
            }
                // One row per package: queryIntentActivities returns one
                // entry per launcher ACTIVITY (Phone + Contacts, OEM
                // suites), and the picker's LazyColumn keys on the package.
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- backup & restore (3.1.0, PattNG) ----

    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val text = BackupManager.exportJson(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    // 3.2.11 (PattNG ImportConfigLocal): import configs from a local
    // file — share links (one per line, plain or base64) or a full raw
    // Xray JSON config (stored as a custom:// profile, like the manual
    // editor). onResult gets the number of newly added profiles, or -1
    // when nothing usable was found.
    fun importFile(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                val trimmed = text.trim()
                val links = when {
                    trimmed.isEmpty() -> ""
                    trimmed.startsWith("{") -> {
                        val b64 = android.util.Base64.encodeToString(
                            trimmed.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        "custom://$b64"
                    }
                    trimmed.contains("://") -> trimmed
                    else -> {
                        // A base64 subscription body (standard, then URL-safe).
                        var decoded = trimmed
                        for (flags in listOf(android.util.Base64.DEFAULT, android.util.Base64.URL_SAFE)) {
                            try {
                                val d = String(android.util.Base64.decode(trimmed, flags), Charsets.UTF_8)
                                if (d.contains("://")) { decoded = d; break }
                            } catch (_: Exception) {
                            }
                        }
                        decoded
                    }
                }
                if (links.isEmpty()) -1 else when (val r = repo.addSingleLinks(links)) {
                    is ProfileRepository.FetchResult.Ok -> r.profiles.size
                    else -> -1
                }
            } catch (_: Exception) {
                -1
            }
            withContext(Dispatchers.Main) {
                if (added > 0) refreshProfiles()
                onResult(added)
            }
        }
    }

    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@launch withContext(Dispatchers.Main) { onResult(false) }
                BackupManager.importJson(context, text)
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    refreshProfiles()
                    refreshChains()
                    refreshSubscriptions()
                    refreshRouting()
                    refreshPerApp()
                }
                onResult(ok)
            }
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

    // 3.1.0 (PattNG): connection settings.
    fun setTestUrl(v: String) = viewModelScope.launch { settings.setTestUrl(v) }
    fun setMux(v: Boolean) = viewModelScope.launch { settings.setMux(v) }
    fun setSniffing(v: Boolean) = viewModelScope.launch { settings.setSniffing(v) }
    fun setFragment(v: Boolean) = viewModelScope.launch { settings.setFragment(v) }
    fun setDnsServers(v: String) = viewModelScope.launch { settings.setDnsServers(v) }

    // 3.2.0 (PattNG parity).
    fun setSocksPort(v: String) = viewModelScope.launch { settings.setSocksPort(v) }
    fun setSocksUser(v: String) = viewModelScope.launch { settings.setSocksUser(v) }
    fun setSocksPass(v: String) = viewModelScope.launch { settings.setSocksPass(v) }
    fun setSocksUdp(v: Boolean) = viewModelScope.launch { settings.setSocksUdp(v) }
    fun setSocksShareLan(v: Boolean) = viewModelScope.launch { settings.setSocksShareLan(v) }
    fun setSocksDynamicPort(v: Boolean) = viewModelScope.launch { settings.setSocksDynamicPort(v) }
    fun setMtu(v: String) = viewModelScope.launch { settings.setMtu(v) }
    fun setIfaceAddr(v: String) = viewModelScope.launch { settings.setIfaceAddr(v) }
    fun setHttpProxy(v: Boolean) = viewModelScope.launch { settings.setHttpProxy(v) }
    fun setFakeDns(v: Boolean) = viewModelScope.launch { settings.setFakeDns(v) }
    fun setLocalDns(v: Boolean) = viewModelScope.launch { settings.setLocalDns(v) }
    fun setRouteOnly(v: Boolean) = viewModelScope.launch { settings.setRouteOnly(v) }
    fun setCoreLogLevel(v: String) = viewModelScope.launch { settings.setCoreLogLevel(v) }
    fun setDomainStrategy(v: String) = viewModelScope.launch { settings.setDomainStrategy(v) }
    fun setMuxConcurrency(v: String) = viewModelScope.launch { settings.setMuxConcurrency(v) }
    fun setMuxXudpConcurrency(v: String) = viewModelScope.launch { settings.setMuxXudpConcurrency(v) }
    fun setMuxXudpQuic(v: Boolean) = viewModelScope.launch { settings.setMuxXudpQuic(v) }
    fun setDomesticDns(v: String) = viewModelScope.launch { settings.setDomesticDns(v) }
    fun setDnsHosts(v: String) = viewModelScope.launch { settings.setDnsHosts(v) }
    fun setRealPingConcurrency(v: String) = viewModelScope.launch { settings.setRealPingConcurrency(v) }
    fun setIpApiUrl(v: String) = viewModelScope.launch { settings.setIpApiUrl(v) }
    fun setProxyOnly(v: Boolean) = viewModelScope.launch { settings.setProxyOnly(v) }
    // 3.2.10 (PattNG root mode): turning it ON probes for su first;
    // without root the setting stays off and the caller shows the toast.
    fun setRootMode(v: Boolean, onDenied: () -> Unit = {}) = viewModelScope.launch {
        if (v) {
            if (com.amyrmo.damavand.lite.root.RootManager.refresh()) {
                settings.setRootMode(true)
            } else {
                onDenied()
            }
        } else {
            settings.setRootMode(false)
        }
    }
    fun setRootLanShare(v: Boolean) = viewModelScope.launch { settings.setRootLanShare(v) }
    // 3.2.11 (PattNG full parity).
    fun setIpv6Enabled(v: Boolean) = viewModelScope.launch { settings.setIpv6Enabled(v) }
    fun setPreferIpv6(v: Boolean) = viewModelScope.launch { settings.setPreferIpv6(v) }
    fun setAetherListenPort(v: String) = viewModelScope.launch { settings.setAetherListenPort(v) }
    /** Synchronous read for MainActivity's connect gate (DataStore is cached). */
    fun isRootModeNow(): Boolean = kotlinx.coroutines.runBlocking { settings.rootModeNow() }
    fun setShowSpeedNotif(v: Boolean) = viewModelScope.launch { settings.setShowSpeedNotif(v) }
    fun setConfirmDelete(v: Boolean) = viewModelScope.launch { settings.setConfirmDelete(v) }
    fun setShowAllGroups(v: Boolean) = viewModelScope.launch { settings.setShowAllGroups(v) }
    fun setDoubleColumn(v: Boolean) = viewModelScope.launch { settings.setDoubleColumn(v) }
    fun setLanguage(v: String) = viewModelScope.launch { settings.setLanguage(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settings.setThemeMode(v) }
    fun setAutoStartBoot(v: Boolean) = viewModelScope.launch { settings.setAutoStartBoot(v) }
    fun setSubAutoTest(v: Boolean) = viewModelScope.launch { settings.setSubAutoTest(v) }
    fun setSubAutoSort(v: Boolean) = viewModelScope.launch { settings.setSubAutoSort(v) }
    fun setSubAutoRemoveInvalid(v: Boolean) = viewModelScope.launch { settings.setSubAutoRemoveInvalid(v) }
    fun setHevLogLevel(v: String) = viewModelScope.launch { settings.setHevLogLevel(v) }
    fun setHevRwTimeout(v: String) = viewModelScope.launch { settings.setHevRwTimeout(v) }

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

    // 3.2.0 (PattNG): import a user-supplied geoip.dat / geosite.dat.
    fun importGeoFile(uri: android.net.Uri, kind: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = geo.importFile(uri, kind)
            onDone(ok)
        }
    }

    // ---- asset URLs (3.2.11, PattNG UserAssetUrlActivity) ----

    /** The URL each asset file downloads from, or its built-in one when the user set none. */
    val assetGeoipUrl = settings.assetGeoipUrl
    val assetGeositeUrl = settings.assetGeositeUrl
    val assetPsiphonUrl = settings.assetPsiphonUrl

    /** The built-in URL an asset downloads from when the user set none. */
    fun defaultAssetUrl(kind: String): String = when (kind) {
        "geoip" -> GeoDownloader.DEFAULT_GEOIP_URL
        "geosite" -> GeoDownloader.DEFAULT_GEOSITE_URL
        "psiphon" -> com.amyrmo.damavand.lite.aether.PsiphonServerList.PSIPHON_SERVERS_URL
        else -> ""
    }

    /** Saves the URL an asset file downloads from (blank restores the built-in one). */
    fun setAssetUrl(kind: String, url: String) {
        viewModelScope.launch {
            when (kind) {
                "geoip" -> settings.setAssetGeoipUrl(url)
                "geosite" -> settings.setAssetGeositeUrl(url)
                "psiphon" -> settings.setAssetPsiphonUrl(url)
            }
        }
    }

    /** Downloads one asset file from [url], and reports whether the new file verified. */
    fun downloadAssetUrl(kind: String, url: String, onDone: (Boolean) -> Unit) {
        if (url.isBlank()) {
            onDone(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = geo.downloadFromUrl(url, kind)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
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
