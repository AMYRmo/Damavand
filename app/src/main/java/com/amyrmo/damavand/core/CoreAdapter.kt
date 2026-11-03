package com.amyrmo.damavand.core

import android.content.Context
import android.net.VpnService
import com.amyrmo.damavand.logic.CoreCapabilities
import com.amyrmo.damavand.logic.HopCapabilities
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The CoreAdapter contract (architecture §2.3), aligned to the implemented
 * :logic plan types (which govern over the §2.1/§2.3 sketches):
 *
 *  - the unit of work is a [PlannedHop] — profile by value, sealed [CoreKind],
 *    a per-hop `HopConfig` payload whose config generation already happened
 *    in :logic (ChainPlanner);
 *  - [CoreAdapter.prepare] resolves only what the plan could not: the
 *    start-time splice when the previous hop's listener port is
 *    runtime-dynamic (a `*Pending` flag on the hop config). [upstream] is
 *    that resolved previous-hop endpoint, or null when the hop's config is
 *    already fully wired at plan time;
 *  - [CoreAdapter.start] returns only after THIS hop's own readiness probe
 *    passes, and the returned [HopHandle.socks] is the listener the start
 *    actually opened — the only port anything may advertise (§2.4 rule 2).
 */

/** Events an adapter reports while preparing/starting/running a hop. */
sealed interface CoreEvent {
    /** A raw core log line (also routed into the session LogBus, tagged). */
    data class LogLine(val text: String) : CoreEvent

    /** The hop's staged progress (§5.5); [subStatus] is a live detail line. */
    data class StageChanged(val stage: ConnectionStage, val subStatus: String? = null) : CoreEvent
}

/** A hop failed to start (or the session step around it did). */
class HopFailure(
    val hopIndex: Int,
    val kind: CoreKind,
    val profileName: String,
    val stage: ConnectionStage,
    val reason: String,
    cause: Throwable? = null,
) : Exception("hop $hopIndex ($kind, $profileName) failed at $stage: $reason", cause)

/** Cumulative byte counters a core can report; null where it cannot. */
data class TrafficCounters(val downBytes: Long, val upBytes: Long)

/** Why a hop's handle stopped. */
sealed interface StopReason {
    /** [HopHandle.stop] was called; not a failure. */
    data object Requested : StopReason

    /** The core died on its own; the session must fail (§2.4). */
    data class Died(val message: String?) : StopReason
}

/**
 * Everything an adapter needs about the hop it is starting. [emit] feeds
 * both the session's stage stream and the LogBus (the session layer tags
 * log lines with hop index + kind before they reach the bus).
 */
class HopContext(
    val appContext: Context,
    val vpnService: VpnService,
    /** This hop's isolated directory (already created): `<filesDir>/<sessionDirName>`. */
    val sessionDir: File,
    val hopIndex: Int,
    val emit: (CoreEvent) -> Unit,
) {
    fun log(text: String) = emit(CoreEvent.LogLine(text))
    fun stage(stage: ConnectionStage, subStatus: String? = null) =
        emit(CoreEvent.StageChanged(stage, subStatus))
}

/** The resolved, ready-to-start form of a hop; adapter-specific payload. */
interface PreparedHop {
    val hop: PlannedHop
    val ctx: HopContext
}

/**
 * A running hop. [socks] is the listener THIS start actually opened
 * (adopted from the process/callback where the core chooses its own port —
 * never re-derived from a config). [stopped] completes [StopReason.Died]
 * on unexpected death; a deliberate [stop] completes it [StopReason.Requested]
 * first, so the session's death watcher can tell the two apart.
 */
data class HopHandle(
    val socks: SocksEndpoint,
    val stages: Flow<ConnectionStage>,
    val traffic: () -> TrafficCounters?,
    val stopped: Deferred<StopReason>,
    val stop: suspend () -> Unit,
)

/**
 * Tracks one hop's current stage: keeps a [MutableStateFlow] for
 * [HopHandle.stages] and mirrors every change into [HopContext.emit] so
 * the session layer sees stages without collecting per-hop flows.
 */
class StageTracker(
    private val ctx: HopContext,
    initial: ConnectionStage = ConnectionStage.Starting,
) {
    private val _stage = MutableStateFlow(initial)
    val flow: Flow<ConnectionStage> = _stage
    val current: ConnectionStage get() = _stage.value

    fun set(stage: ConnectionStage, subStatus: String? = null) {
        _stage.value = stage
        ctx.stage(stage, subStatus)
    }
}

interface CoreAdapter {
    val kind: CoreKind

    /**
     * The hop's declared capabilities. The single declaration lives in
     * :logic ([CoreCapabilities], §2.4 rule 5); adapters delegate so the
     * planner, the UI and the runner can never drift apart.
     */
    fun capabilities(profile: Profile): HopCapabilities = CoreCapabilities.of(profile)

    /**
     * Resolve [hop] into its startable form. Must not mutate the stored
     * profile (hop configs already carry runtime copies where :logic
     * needed one). [upstream] is non-null exactly when the hop's config
     * carries a pending splice and the previous hop has now reported its
     * listener.
     */
    fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop

    /**
     * Start the hop; returns only after its readiness probe passes.
     * Throws [HopFailure] on timeout/death.
     */
    suspend fun start(prepared: PreparedHop): HopHandle
}
