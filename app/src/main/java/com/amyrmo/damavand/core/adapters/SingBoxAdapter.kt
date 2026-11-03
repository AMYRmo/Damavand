package com.amyrmo.damavand.core.adapters

import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.PreparedHop
import com.amyrmo.damavand.core.SocksAnswers
import com.amyrmo.damavand.core.StageTracker
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.TrafficCounters
import com.amyrmo.damavand.core.bridge.PreparedBridge
import com.amyrmo.damavand.core.bridge.SingBoxEngine
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The sing-box adapter (architecture §2.5), two roles over one engine:
 *
 *  - **Hop role** — a [HopConfig.SingBoxJson] hop (standalone full-config
 *    profile or fragment hop, §3.2). The planner already produced the
 *    final JSON; there is no start-time splice for sing-box hops, so
 *    `upstream` is ignored.
 *  - **Bridge role** — the TUN-owning bridge, started from a
 *    [PreparedBridge] artefact via [prepareBridge]. The readiness port
 *    and the advertised `HopHandle.socks` come ONLY from the artefact
 *    (§2.4 rule 2, the phantom-10808 rule).
 *
 * The adapter is constructed per session by the session layer, which is
 * where [perApp] comes from (the plan's per-app selection is session
 * data, not hop data).
 *
 * Every [start] creates its OWN [SingBoxEngine] instance: a sing-box hop
 * and the bridge can coexist in one session, and engines are not
 * singletons.
 *
 * Readiness: the started listener answers a SOCKS greeting
 * ([SocksAnswers]), polled every 300 ms for up to 30 s. Engine death
 * before ready fails the hop at Authenticating.
 */
class SingBoxAdapter(
    private val perApp: PerAppSelection = PerAppSelection.All,
    private val postNotification: (String, String) -> Unit = { _, _ -> },
) : CoreAdapter {

    override val kind: CoreKind = CoreKind.SINGBOX

    /**
     * One prepared start. [hopOrNull] is null exactly for the bridge role:
     * a bridge artefact has no PlannedHop, so [hop] throws if read — the
     * runner must treat bridge prepared hops as start-only.
     */
    private class PreparedSingBoxHop(
        override val ctx: HopContext,
        val json: String,
        val socksPort: Int,
        val sessionName: String,
        val failureName: String,
        private val hopOrNull: PlannedHop?,
    ) : PreparedHop {
        override val hop: PlannedHop
            get() = hopOrNull
                ?: throw IllegalStateException("a bridge artefact has no PlannedHop")
    }

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        val config = hop.config as? HopConfig.SingBoxJson
            ?: throw IllegalArgumentException(
                "SingBoxAdapter cannot prepare ${hop.config::class.simpleName} for hop ${hop.index}",
            )
        val listenPort = hop.listenPort
            ?: throw IllegalArgumentException("sing-box hop ${hop.index} has no listenPort in the plan")
        return PreparedSingBoxHop(
            ctx = ctx,
            json = config.json,
            socksPort = listenPort,
            sessionName = hop.profile.name,
            failureName = hop.profile.name,
            hopOrNull = hop,
        )
    }

    /** Bridge role entry point: prepare a start from the bridge artefact alone. */
    fun prepareBridge(ctx: HopContext, artefact: PreparedBridge): PreparedHop =
        PreparedSingBoxHop(
            ctx = ctx,
            json = artefact.json,
            socksPort = artefact.localPort,
            sessionName = BRIDGE_SESSION_NAME,
            failureName = BRIDGE_FAILURE_NAME,
            hopOrNull = null,
        )

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val prep = prepared as? PreparedSingBoxHop
            ?: throw IllegalArgumentException("SingBoxAdapter cannot start ${prepared::class.simpleName}")
        val ctx = prep.ctx
        val tracker = StageTracker(ctx)
        val stopped = CompletableDeferred<StopReason>()
        val stopRequested = AtomicBoolean(false)

        val engine = SingBoxEngine(
            service = ctx.vpnService,
            session = prep.sessionName,
            perApp = perApp,
            postNotification = postNotification,
            onLog = { line -> ctx.log(line) },
            onStopped = { reason ->
                if (!stopped.isCompleted) {
                    stopped.complete(
                        if (stopRequested.get()) StopReason.Requested
                        else StopReason.Died(reason),
                    )
                }
            },
        )

        tracker.set(ConnectionStage.Starting)
        try {
            withContext(Dispatchers.IO) { engine.start(prep.json) }
        } catch (e: Exception) {
            throw HopFailure(
                ctx.hopIndex, CoreKind.SINGBOX, prep.failureName, ConnectionStage.Starting,
                e.message ?: "sing-box failed to start", e,
            )
        }
        tracker.set(ConnectionStage.Authenticating)

        // Readiness: the listener this start opened answers a SOCKS
        // greeting. The engine's own death (onStopped → stopped) cuts
        // the wait short.
        val answers = SocksAnswers(READY_WAIT_MS)
        val deadlineNanos = System.nanoTime() + READY_DEADLINE_MS * NANOS_PER_MS
        var ready = false
        while (System.nanoTime() < deadlineNanos) {
            if (stopped.isCompleted) break
            if (withContext(Dispatchers.IO) { answers.answers(prep.socksPort) }) {
                ready = true
                break
            }
            delay(READY_POLL_MS)
        }
        if (!ready) {
            val died = stopped.isCompleted
            runCatching { withContext(Dispatchers.IO) { engine.stop() } }
            throw HopFailure(
                ctx.hopIndex, CoreKind.SINGBOX, prep.failureName, ConnectionStage.Authenticating,
                if (died) {
                    "sing-box stopped before its listener answered"
                } else {
                    "sing-box listener did not answer in time"
                },
            )
        }
        tracker.set(ConnectionStage.Verifying)

        return HopHandle(
            socks = SocksEndpoint(port = prep.socksPort),
            stages = tracker.flow,
            traffic = {
                TrafficCounters(downBytes = engine.downlinkTotal, upBytes = engine.uplinkTotal)
            },
            stopped = stopped,
            stop = {
                stopRequested.set(true)
                if (!stopped.isCompleted) stopped.complete(StopReason.Requested)
                withContext(Dispatchers.IO) { engine.stop() }
            },
        )
    }

    private companion object {
        /** The TUN session label for a bridge engine (no profile owns it). */
        const val BRIDGE_SESSION_NAME = "Damavand"
        const val BRIDGE_FAILURE_NAME = "sing-box bridge"

        const val READY_WAIT_MS = 1_000
        const val READY_POLL_MS = 300L
        const val READY_DEADLINE_MS = 30_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
