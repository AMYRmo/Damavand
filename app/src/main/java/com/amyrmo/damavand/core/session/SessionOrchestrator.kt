package com.amyrmo.damavand.core.session

import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.LogBus
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.adapters.AetherAdapter
import com.amyrmo.damavand.core.adapters.MihomoAdapter
import com.amyrmo.damavand.core.adapters.PsiphonAdapter
import com.amyrmo.damavand.core.adapters.SingBoxAdapter
import com.amyrmo.damavand.core.adapters.WhitednsAdapter
import com.amyrmo.damavand.core.adapters.XrayAdapter
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.data.profiles.ProfileRepository
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.logic.ChainException
import com.amyrmo.damavand.logic.ChainPlanner
import com.amyrmo.damavand.logic.PortAllocator
import com.amyrmo.damavand.logic.ProfileLookup
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.platform.GeoAssets
import com.amyrmo.damavand.ui.components.ConnectionStage
import com.amyrmo.damavand.ui.components.StageInfo
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the current session (architecture §2.1): builds the plan from the
 * tapped profile via [ChainPlanner], runs it through [SessionRunner],
 * watches the running hops for mid-session death, and applies the chain
 * restart policy (§4.3 step 6). A plain class — the VpnService holds one
 * and the UI observes [state]; adapters are fakes in tests.
 */
class SessionOrchestrator(
    private val env: SessionEnvironment,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val supervisor: ProcessSupervisor = ProcessSupervisor(),
    private val postNotification: (String, String) -> Unit = { _, _ -> },
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    private var sessionJob: Job? = null
    private var deathWatchers: List<Job> = emptyList()
    private var currentRun: SessionRun? = null

    /** The plan of the live/last session (restart reuses it verbatim). */
    private var currentPlan: com.amyrmo.damavand.logic.SessionPlan? = null
    private var currentTargetName: String = ""

    /** The settings the live plan was built with (restart reuses them). */
    private var lastPlanSettings: com.amyrmo.damavand.logic.PlanSettings =
        com.amyrmo.damavand.logic.PlanSettings()

    val isActive: Boolean get() = sessionJob?.isActive == true || currentRun != null

    /**
     * Start [target] (a standalone profile or a chain). Replaces any live
     * session. Planning/validation failures land in [SessionState.Failed]
     * with the chain errors as the reason — no partial start.
     */
    fun connect(target: Profile) {
        disconnectInternal(keepState = true)
        val sessionId = UUID.randomUUID().toString()
        currentTargetName = target.name
        _state.value = SessionState.Connecting(
            sessionId, target.name, StageInfo(ConnectionStage.Starting, hopLabel = target.name),
        )
        sessionJob = env.scope.launch {
            val planSettings = settings.planSettings().let { base ->
                val geo = GeoAssets.ensure(env.appContext)
                base.copy(xrayOptions = base.xrayOptions.copy(geoAssetsAvailable = geo))
            }
            val plan = try {
                ChainPlanner.plan(
                    target = target,
                    sessionId = sessionId,
                    settings = planSettings,
                    lookup = ProfileLookup { id -> profiles.profile(id) },
                    allocator = PortAllocator(),
                )
            } catch (e: ChainException) {
                _state.value = SessionState.Failed(
                    sessionId, target.name,
                    HopFailure(0, target.kind, target.name, ConnectionStage.Starting,
                        e.errors.joinToString("; ") { it.toString() }, e),
                )
                return@launch
            } catch (e: Exception) {
                _state.value = SessionState.Failed(
                    sessionId, target.name,
                    HopFailure(0, target.kind, target.name, ConnectionStage.Starting, e.message ?: "planning failed", e),
                )
                return@launch
            }
            currentPlan = plan
            lastPlanSettings = planSettings
            runSession(plan, planSettings, attempt = 0)
        }
    }

    /** Stop the live session (if any) and go Idle. */
    fun disconnect() {
        disconnectInternal(keepState = false)
        _state.value = SessionState.Idle
    }

    private fun disconnectInternal(keepState: Boolean) {
        sessionJob?.cancel()
        sessionJob = null
        deathWatchers.forEach { it.cancel() }
        deathWatchers = emptyList()
        val run = currentRun
        currentRun = null
        currentPlan = null
        if (run != null) {
            env.scope.launch { runCatching { run.stop() } }
        }
        if (!keepState) {
            // state reset is the caller's job (disconnect() sets Idle).
        }
    }

    private suspend fun runSession(
        plan: com.amyrmo.damavand.logic.SessionPlan,
        planSettings: com.amyrmo.damavand.logic.PlanSettings,
        attempt: Int,
    ) {
        val singBoxAdapter = SingBoxAdapter(perApp = plan.perApp, postNotification = postNotification)
        val runner = SessionRunner(
            env = env,
            adapters = mapOf(
                CoreKind.XRAY to XrayAdapter(),
                CoreKind.SINGBOX to singBoxAdapter,
                CoreKind.PSIPHON to PsiphonAdapter(),
                CoreKind.AETHER to AetherAdapter(supervisor),
                CoreKind.MIHOMO to MihomoAdapter(supervisor),
                CoreKind.WHITEDNS to WhitednsAdapter(supervisor),
            ),
            singBoxAdapter = singBoxAdapter,
        )
        val run = try {
            runner.run(plan, planSettings.device) { stageInfo ->
                val current = _state.value
                if (current is SessionState.Connecting && stageInfo.stage != ConnectionStage.Connected) {
                    _state.value = current.copy(stage = stageInfo)
                }
            }
        } catch (failure: HopFailure) {
            onSessionFailure(plan, failure, attempt)
            return
        }
        currentRun = run
        _state.value = SessionState.Connected(
            sessionId = plan.sessionId,
            targetName = currentTargetName,
            localSocksPort = run.localSocksPort,
            startedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
        LogBus.append("I/session: connected via ${plan.hops.size} hop(s), local proxy 127.0.0.1:${run.localSocksPort}")
        watchForDeath(plan, run, attempt)
    }

    /**
     * Mid-session death (§2.4/§4.3 step 6): the first handle that dies
     * unexpectedly fails the session; chains restart with backoff
     * (5 s → 15 s → 45 s, max 3 attempts), standalone sessions surface the
     * failure for a manual retry.
     */
    private fun watchForDeath(plan: com.amyrmo.damavand.logic.SessionPlan, run: SessionRun, attempt: Int) {
        deathWatchers.forEach { it.cancel() }
        // Attribute a death to the hop whose handle died (the bridge is
        // attributed to the exit hop — its failure is a session failure).
        val watched = mutableListOf<Triple<com.amyrmo.damavand.core.HopHandle, Int, com.amyrmo.damavand.model.CoreKind>>()
        run.hopHandlesByIndex.forEachIndexed { index, handle ->
            if (watched.none { it.first === handle }) watched += Triple(handle, index, plan.hops[index].kind)
        }
        run.bridgeHandle?.let { watched += Triple(it, plan.hops.lastIndex, plan.hops.last().kind) }
        deathWatchers = watched.map { (handle, hopIndex, kind) ->
            env.scope.launch {
                val reason = handle.stopped.await()
                if (reason is StopReason.Died && currentRun === run) {
                    currentRun = null
                    runCatching { run.stop() }
                    val failure = HopFailure(
                        hopIndex = hopIndex,
                        kind = kind,
                        profileName = plan.hops[hopIndex].profile.name,
                        stage = ConnectionStage.Connected,
                        reason = reason.message ?: "a core stopped unexpectedly",
                    )
                    onSessionFailure(plan, failure, attempt)
                }
            }
        } + env.scope.launch {
            // A late TUN-forwarder failure (the zeptun→hev proof switch)
            // is a death of the exit path, attributed to the exit hop.
            val reason = run.tunFailure.await()
            if (currentRun === run) {
                currentRun = null
                runCatching { run.stop() }
                val exit = plan.hops.last()
                onSessionFailure(
                    plan,
                    HopFailure(
                        hopIndex = plan.hops.lastIndex,
                        kind = exit.kind,
                        profileName = exit.profile.name,
                        stage = ConnectionStage.Connected,
                        reason = "the TUN forwarder failed: $reason",
                    ),
                    attempt,
                )
            }
        }
    }

    private suspend fun onSessionFailure(
        plan: com.amyrmo.damavand.logic.SessionPlan,
        failure: HopFailure,
        attempt: Int,
    ) {
        LogBus.append("E/session: ${failure.message}")
        val isChain = plan.hops.size > 1
        if (isChain && attempt < RESTART_BACKOFF_MS.size) {
            val backoff = RESTART_BACKOFF_MS[attempt]
            _state.value = SessionState.Connecting(
                plan.sessionId, currentTargetName,
                StageInfo(ConnectionStage.Retrying, attempt = attempt + 1, subStatus = failure.reason),
            )
            delay(backoff)
            runSession(plan, lastPlanSettings, attempt + 1)
        } else {
            _state.value = SessionState.Failed(plan.sessionId, currentTargetName, failure)
        }
    }

    companion object {
        /** Chain restart backoff ladder (§4.3 step 6): max 3 attempts. */
        private val RESTART_BACKOFF_MS = listOf(5_000L, 15_000L, 45_000L)
    }
}
