package com.amyrmo.damavand.core.adapters

import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.PreparedHop
import com.amyrmo.damavand.core.StageTracker
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.TrafficCounters
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.logic.XrayChainWiring
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The Xray adapter (architecture §2.5): wraps an instance [XrayController]
 * per started hop. Readiness per the §2.4 table: loop running, then
 * `measureDelay` against the test URL succeeds → Verifying, inside
 * [ProcessSupervisor.deadlineFor] for XRAY (120 s).
 */
class XrayAdapter : CoreAdapter {

    override val kind: CoreKind = CoreKind.XRAY

    private class PreparedXrayHop(
        override val hop: PlannedHop,
        override val ctx: HopContext,
        val json: String,
        val listenPort: Int,
    ) : PreparedHop

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        val (json, carrierSplicePending) = when (val config = hop.config) {
            is HopConfig.XrayJson -> config.json to config.carrierSplicePending
            is HopConfig.XrayRunStart -> config.json to config.carrierSplicePending
            // XrayRunMember hops never reach the adapter — the runner skips
            // them (the run's first hop starts the whole run).
            else -> throw IllegalArgumentException(
                "XrayAdapter cannot prepare ${config::class.simpleName} for hop ${hop.index}",
            )
        }
        val listenPort = hop.listenPort
            ?: throw IllegalArgumentException("xray hop ${hop.index} has no listenPort in the plan")
        val finalJson = if (carrierSplicePending && upstream != null) {
            XrayChainWiring.withCarrierProxy(json, upstream.port)
        } else {
            json
        }
        return PreparedXrayHop(hop, ctx, finalJson, listenPort)
    }

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val prep = prepared as? PreparedXrayHop
            ?: throw IllegalArgumentException("XrayAdapter cannot start ${prepared::class.simpleName}")
        val ctx = prep.ctx
        val hop = prep.hop
        return withContext(Dispatchers.IO) {
            val tracker = StageTracker(ctx)
            val stopped = CompletableDeferred<StopReason>()
            val stopRequested = AtomicBoolean(false)

            val controller = XrayController(ctx.appContext)
            controller.onCoreShutdown = {
                if (!stopped.isCompleted) {
                    stopped.complete(
                        if (stopRequested.get()) StopReason.Requested
                        else StopReason.Died("xray core shut down"),
                    )
                }
            }

            tracker.set(ConnectionStage.Starting)
            if (!controller.start(prep.json)) {
                throw HopFailure(
                    hop.index, CoreKind.XRAY, hop.profile.name, ConnectionStage.Starting,
                    controller.lastError ?: "xray loop failed to start",
                )
            }
            tracker.set(ConnectionStage.Authenticating)

            val deadlineNanos = System.nanoTime() +
                ProcessSupervisor.deadlineFor(CoreKind.XRAY) * NANOS_PER_MS
            var delayMs = -1L
            try {
                delayMs = controller.measureDelay(DELAY_TEST_URL)
                while (delayMs <= 0L && System.nanoTime() < deadlineNanos) {
                    if (!controller.isRunning) break
                    delay(DELAY_POLL_MS)
                    delayMs = controller.measureDelay(DELAY_TEST_URL)
                }
            } catch (e: CancellationException) {
                runCatching { controller.stop() }
                throw e
            }
            if (delayMs <= 0L) {
                val reason = if (!controller.isRunning) {
                    "xray core stopped during start"
                } else {
                    "xray did not answer a delay test in time"
                }
                runCatching { controller.stop() }
                throw HopFailure(
                    hop.index, CoreKind.XRAY, hop.profile.name, ConnectionStage.Authenticating, reason,
                )
            }
            tracker.set(ConnectionStage.Verifying)

            // The controller reports per-query deltas; the handle's traffic
            // is the accumulated session total.
            val downTotal = AtomicLong(0)
            val upTotal = AtomicLong(0)

            HopHandle(
                socks = SocksEndpoint(port = prep.listenPort),
                stages = tracker.flow,
                traffic = {
                    val (down, up) = controller.readTrafficDelta()
                    TrafficCounters(
                        downBytes = downTotal.addAndGet(down),
                        upBytes = upTotal.addAndGet(up),
                    )
                },
                stopped = stopped,
                stop = {
                    stopRequested.set(true)
                    if (!stopped.isCompleted) stopped.complete(StopReason.Requested)
                    withContext(Dispatchers.IO) { controller.stop() }
                },
            )
        }
    }

    private companion object {
        /** The core's delay-test URL (architecture §2.4 readiness table). */
        const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DELAY_POLL_MS = 500L
        const val NANOS_PER_MS = 1_000_000L
    }
}
