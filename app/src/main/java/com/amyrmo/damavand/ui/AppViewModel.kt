package com.amyrmo.damavand.ui

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.service.SessionController
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.data.profiles.ProfileRepository
import com.amyrmo.damavand.data.settings.AppSettings
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.data.settings.UiSettings
import com.amyrmo.damavand.data.subscriptions.SubscriptionRepository
import com.amyrmo.damavand.data.subscriptions.SubscriptionService
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.ProfileJson
import com.amyrmo.damavand.model.Subscription
import com.amyrmo.damavand.platform.GeoInfo
import com.amyrmo.damavand.platform.GeoLookup
import com.amyrmo.damavand.platform.NetProbes
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A one-shot UI message (snackbar), rendered by the shell with the app locale. */
data class UiNote(@StringRes val res: Int, val args: List<Any> = emptyList())

/** The outcome of a profile delay test — always a real measurement. */
sealed interface PingOutcome {
    data object Unknown : PingOutcome
    data object Measuring : PingOutcome
    data class Ms(val value: Int) : PingOutcome
    data object Unreachable : PingOutcome

    /** The profile has no fixed endpoint to probe (scan-mode, Psiphon, chains…). */
    data object NotApplicable : PingOutcome
}

/** Live session traffic, device-measured for this app's UID (see [AppViewModel]). */
data class TrafficInfo(
    val downRateBps: Long = 0,
    val upRateBps: Long = 0,
    val downTotal: Long = 0,
    val upTotal: Long = 0,
)

/**
 * The app-level view model (§5.1): owns the three repositories, bridges
 * the service-owned [SessionController] state into Compose, and carries
 * every screen-level action (connect/disconnect with AutoSelect
 * expansion, ping sweeps, subscription refresh, backup/restore).
 *
 * Traffic note: the runtime does not publish per-hop counters to the UI,
 * so the dashboard measures this app's UID traffic via [TrafficStats]
 * from the moment the session reports Connected — real device numbers,
 * labelled as app traffic in the UI.
 */
class AppViewModel(private val appContext: Context) : ViewModel() {

    val profileRepo = ProfileRepository(appContext)
    val subscriptionRepo = SubscriptionRepository(appContext)
    val settingsRepo = SettingsRepository(appContext)
    private val subscriptionService = SubscriptionService(profileRepo, subscriptionRepo)

    val profiles: StateFlow<List<Profile>> = profileRepo.profiles
    val subscriptions: StateFlow<List<Subscription>> = subscriptionRepo.subscriptions
    val storeFault: StateFlow<String?> = profileRepo.loadFault

    val appSettings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val uiSettings: StateFlow<UiSettings> = settingsRepo.uiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiSettings())

    // SessionController.state() swaps its flow when the service attaches
    // its orchestrator; watch for the swap and re-collect.
    private val sessionSource = MutableStateFlow(SessionController.state())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessionState: StateFlow<SessionState> = sessionSource
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Idle)

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId

    private val _pingResults = MutableStateFlow<Map<String, PingOutcome>>(emptyMap())
    val pingResults: StateFlow<Map<String, PingOutcome>> = _pingResults

    private val _geo = MutableStateFlow<GeoInfo?>(null)
    val geo: StateFlow<GeoInfo?> = _geo

    private val _traffic = MutableStateFlow(TrafficInfo())
    val traffic: StateFlow<TrafficInfo> = _traffic

    private val _notes = MutableSharedFlow<UiNote>(extraBufferCapacity = 8)
    val notes: SharedFlow<UiNote> = _notes

    private var sessionWatcher: Job? = null

    init {
        viewModelScope.launch {
            profileRepo.load()
            subscriptionRepo.load()
            val ui = settingsRepo.currentUi()
            // No seeded starter profiles (owner directive, ZedSecure
            // revision): a fresh install starts with zero profiles.
            _selectedProfileId.value = ui.lastProfileId
                ?.takeIf { id -> profiles.value.any { it.id == id } }
                ?: profiles.value.firstOrNull()?.id
        }
        viewModelScope.launch {
            while (true) {
                val flow = SessionController.state()
                if (flow !== sessionSource.value) sessionSource.value = flow
                delay(750)
            }
        }
        viewModelScope.launch {
            var handledSession: String? = null
            sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        if (handledSession != state.sessionId) {
                            handledSession = state.sessionId
                            watchSession(state)
                        }
                    }
                    SessionState.Idle -> {
                        handledSession = null
                        sessionWatcher?.cancel()
                        _geo.value = null
                        _traffic.value = TrafficInfo()
                    }
                    else -> Unit
                }
            }
        }
    }

    // -- selection + connection -------------------------------------------------

    fun selectProfile(id: String) {
        _selectedProfileId.value = id
    }

    fun selectedProfile(): Profile? =
        profiles.value.firstOrNull { it.id == _selectedProfileId.value }
            ?: profiles.value.firstOrNull()

    /**
     * Connect [profile]. An [AutoSelectProfile] is expanded HERE, in the
     * app layer (the planner rejects it by design, §4.2): the fastest
     * measured member of its subscription wins, after a quick parallel
     * sweep when nothing has been measured yet.
     */
    fun connect(profile: Profile) {
        viewModelScope.launch {
            val target = if (profile is AutoSelectProfile) {
                expandAutoSelect(profile) ?: run {
                    _notes.emit(UiNote(R.string.auto_no_members))
                    return@launch
                }
            } else {
                profile
            }
            _selectedProfileId.value = target.id
            settingsRepo.updateUi { it.copy(lastProfileId = target.id) }
            SessionController.connect(appContext, target)
        }
    }

    fun disconnect() {
        SessionController.disconnect(appContext)
    }

    private suspend fun expandAutoSelect(auto: AutoSelectProfile): Profile? {
        val members = profiles.value.filter { it.subscriptionId == auto.targetSubscriptionId }
        if (members.isEmpty()) return null
        val measured = members.mapNotNull { member ->
            (_pingResults.value[member.id] as? PingOutcome.Ms)?.let { member to it.value }
        }
        if (measured.isNotEmpty()) return measured.minByOrNull { it.second }?.first
        // Nothing measured: sweep the endpoints in parallel, then pick.
        coroutineScope {
            members.map { member -> async { member to pingInternal(member) } }.awaitAll()
        }
        val after = members.mapNotNull { member ->
            (_pingResults.value[member.id] as? PingOutcome.Ms)?.let { member to it.value }
        }
        return after.minByOrNull { it.second }?.first ?: members.first()
    }

    // -- ping -------------------------------------------------------------------

    fun pingProfile(profile: Profile) {
        viewModelScope.launch { pingInternal(profile) }
    }

    fun pingProfiles(targets: List<Profile>) {
        viewModelScope.launch {
            coroutineScope {
                targets.map { async { pingInternal(it) } }.awaitAll()
            }
        }
    }

    private suspend fun pingInternal(profile: Profile): PingOutcome {
        val endpoint = ProfileFactory.endpointOf(profile)
        val outcome = if (endpoint == null) {
            PingOutcome.NotApplicable
        } else {
            _pingResults.value = _pingResults.value + (profile.id to PingOutcome.Measuring)
            val ms = NetProbes.tcpPing(endpoint.first, endpoint.second)
            if (ms != null) PingOutcome.Ms(ms) else PingOutcome.Unreachable
        }
        _pingResults.value = _pingResults.value + (profile.id to outcome)
        return outcome
    }

    // -- session dashboard --------------------------------------------------------

    private fun watchSession(state: SessionState.Connected) {
        sessionWatcher?.cancel()
        sessionWatcher = viewModelScope.launch {
            launch { _geo.value = GeoLookup.fetch(state.localSocksPort) }
            val uid = Process.myUid()
            var prevRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
            var prevTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
            var prevAt = System.nanoTime()
            var downTotal = 0L
            var upTotal = 0L
            while (true) {
                delay(2_000)
                val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
                val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
                val now = System.nanoTime()
                val dtSeconds = ((now - prevAt) / 1_000_000_000.0).coerceAtLeast(0.1)
                val dRx = (rx - prevRx).coerceAtLeast(0)
                val dTx = (tx - prevTx).coerceAtLeast(0)
                downTotal += dRx
                upTotal += dTx
                _traffic.value = TrafficInfo(
                    downRateBps = (dRx / dtSeconds).toLong(),
                    upRateBps = (dTx / dtSeconds).toLong(),
                    downTotal = downTotal,
                    upTotal = upTotal,
                )
                prevRx = rx
                prevTx = tx
                prevAt = now
            }
        }
    }

    // -- profiles -----------------------------------------------------------------

    fun upsertProfile(profile: Profile) = profileRepo.upsert(profile)

    fun removeProfile(id: String) {
        profileRepo.remove(id)
        if (_selectedProfileId.value == id) {
            _selectedProfileId.value = profiles.value.firstOrNull { it.id != id }?.id
        }
    }

    fun duplicateProfile(profile: Profile) {
        val copy = ProfileFactory.duplicate(
            profile,
            newId = UUID.randomUUID().toString(),
            newName = profile.name,
        )
        profileRepo.upsert(copy)
    }

    fun importText(text: String): Boolean {
        val profile = ProfileFactory.fromText(text, UUID.randomUUID().toString()) ?: return false
        val named = if (profile.name.isBlank()) {
            ProfileFactory.renamed(profile, fallbackName(profile))
        } else {
            profile
        }
        profileRepo.upsert(named)
        _selectedProfileId.value = named.id
        return true
    }

    private fun fallbackName(profile: Profile): String {
        val endpoint = ProfileFactory.endpointOf(profile)
        return when {
            endpoint != null -> "${endpoint.first}:${endpoint.second}"
            profile is MihomoProfile -> "Clash config"
            else -> ProfileFactory.protocolLabel(profile)
        }
    }

    // -- subscriptions --------------------------------------------------------------

    /** Adds + first-fetches a subscription; true when it landed. Callers launch. */
    suspend fun addSubscription(url: String, name: String?): Boolean =
        subscriptionService.add(url, name)
            .onSuccess { _notes.emit(UiNote(R.string.sub_added, listOf(it.name))) }
            .onFailure {
                _notes.emit(UiNote(R.string.sub_refresh_failed, listOf(it.message ?: "")))
            }
            .isSuccess

    fun refreshSubscription(subscription: Subscription) {
        viewModelScope.launch {
            subscriptionService.refresh(subscription)
                .onSuccess { _notes.emit(UiNote(R.string.sub_refreshed, listOf(it.name))) }
                .onFailure { _notes.emit(UiNote(R.string.sub_refresh_failed, listOf(it.message ?: ""))) }
        }
    }

    fun updateSubscription(subscription: Subscription) = subscriptionRepo.upsert(subscription)

    fun deleteSubscription(subscription: Subscription, deleteMembers: Boolean) {
        val members = profiles.value.filter { it.subscriptionId == subscription.id }
        if (deleteMembers) {
            members.forEach { profileRepo.remove(it.id) }
        } else {
            members.forEach { profileRepo.upsert(ProfileFactory.detached(it)) }
        }
        subscriptionRepo.remove(subscription.id)
    }

    // -- settings -------------------------------------------------------------------

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun updateUiSettings(transform: (UiSettings) -> UiSettings) {
        viewModelScope.launch { settingsRepo.updateUi(transform) }
    }

    /** Settings → Cores: wipe the Aether core's data dir (keys included). */
    fun clearAetherData() {
        viewModelScope.launch {
            runCatching {
                File(appContext.filesDir, "aether").deleteRecursively()
                File(appContext.filesDir, "aether-renewal").deleteRecursively()
            }
            _notes.emit(UiNote(R.string.aether_data_cleared))
        }
    }

    // -- backup -----------------------------------------------------------------------

    fun exportBackup(): String {
        val obj = buildJsonObject {
            put("schema", 2)
            put(
                "profiles",
                ProfileJson.json.encodeToJsonElement(
                    ListSerializer(Profile.serializer()),
                    profiles.value,
                ),
            )
            put(
                "subscriptions",
                ProfileJson.json.encodeToJsonElement(
                    ListSerializer(Subscription.serializer()),
                    subscriptions.value,
                ),
            )
        }
        return ProfileJson.json.encodeToString(JsonObject.serializer(), obj)
    }

    fun importBackup(text: String) {
        viewModelScope.launch {
            runCatching {
                val root = ProfileJson.json.parseToJsonElement(text).jsonObject
                val importedProfiles = ProfileJson.json.decodeFromJsonElement(
                    ListSerializer(Profile.serializer()),
                    root["profiles"] ?: error("no profiles in backup"),
                )
                val importedSubs = root["subscriptions"]?.let {
                    ProfileJson.json.decodeFromJsonElement(ListSerializer(Subscription.serializer()), it)
                } ?: emptyList()
                profileRepo.replaceAll(importedProfiles)
                subscriptionRepo.replaceAll(importedSubs)
                _selectedProfileId.value = importedProfiles.firstOrNull()?.id
                importedProfiles.size to importedSubs.size
            }.onSuccess { (p, s) ->
                _notes.emit(UiNote(R.string.backup_imported, listOf(p, s)))
            }.onFailure {
                _notes.emit(UiNote(R.string.backup_failed))
            }
        }
    }
}

class AppViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AppViewModel(appContext) as T
}
