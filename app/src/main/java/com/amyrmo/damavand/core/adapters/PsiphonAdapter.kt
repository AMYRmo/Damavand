package com.amyrmo.damavand.core.adapters

import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.PreparedHop
import com.amyrmo.damavand.core.StageTracker
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.PsiphonConfigBuilder
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Psiphon adapter (architecture §2.5): v1's [PsiphonController] plus
 * the 120 s deadline it lacked, with the SOCKS port adopted strictly from
 * `onListeningSocksProxyPort` — never from the config, never assumed.
 *
 * Prepare-time work (the plan could not do it):
 *  - the carrier splice, when pending: the previous hop's listener is
 *    wired in via [PsiphonConfigBuilder.withUpstreamProxy];
 *  - `DataRootDirectory` is rewritten to this hop's real session
 *    directory (the planner embedded the relative sessionDirName; the
 *    core needs an absolute path), via a kotlinx.serialization.json
 *    parse/rebuild that preserves every other key;
 *  - the final JSON is written to `psiphon-config.json` in the session
 *    directory for debugging.
 *
 * The stored profile is never mutated: the hop config is a runtime copy
 * from :logic, and all edits happen on its JSON string.
 */
class PsiphonAdapter : CoreAdapter {

    override val kind: CoreKind = CoreKind.PSIPHON

    private class PreparedPsiphonHop(
        override val hop: PlannedHop,
        override val ctx: HopContext,
        val json: String,
    ) : PreparedHop

    /** The controller reported a stop before the start completed. */
    private class EarlyStop(val reason: String?) : Exception(reason)

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        val config = hop.config as? HopConfig.PsiphonJson
            ?: throw IllegalArgumentException(
                "PsiphonAdapter cannot prepare ${hop.config::class.simpleName} for hop ${hop.index}",
            )
        var json = config.json
        if (config.carrierSplicePending && upstream != null) {
            json = PsiphonConfigBuilder.withUpstreamProxy(json, upstream.port)
        }
        json = withDataRootDirectory(json, ctx.sessionDir.absolutePath)
        ctx.sessionDir.mkdirs()
        File(ctx.sessionDir, CONFIG_FILE_NAME).writeText(json)
        return PreparedPsiphonHop(hop, ctx, json)
    }

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val prep = prepared as? PreparedPsiphonHop
            ?: throw IllegalArgumentException("PsiphonAdapter cannot start ${prepared::class.simpleName}")
        val ctx = prep.ctx
        val hop = prep.hop
        val tracker = StageTracker(ctx)
        val stopped = CompletableDeferred<StopReason>()
        val stopRequested = AtomicBoolean(false)
        val socksPort = CompletableDeferred<Int>()
        val established = CompletableDeferred<Unit>()
        val earlyStop = CompletableDeferred<String?>()

        val controller = PsiphonController(
            service = ctx.vpnService,
            configJson = prep.json,
            onSocksPort = { port -> socksPort.complete(port) },
            onEstablished = { established.complete(Unit) },
            onStopped = { reason ->
                if (!earlyStop.isCompleted) earlyStop.complete(reason)
                if (!stopped.isCompleted) {
                    stopped.complete(
                        if (stopRequested.get()) StopReason.Requested
                        else StopReason.Died(reason),
                    )
                }
            },
        )

        return withContext(Dispatchers.IO) {
            tracker.set(ConnectionStage.Starting)
            if (!controller.start()) {
                // v1's controller fires onStopped with the reason on a
                // failed start; fall back to a generic reason if it didn't.
                val reason = if (earlyStop.isCompleted) {
                    earlyStop.await() ?: "psiphon failed to start"
                } else {
                    "psiphon failed to start"
                }
                throw HopFailure(
                    hop.index, CoreKind.PSIPHON, hop.profile.name, ConnectionStage.Starting, reason,
                )
            }
            tracker.set(ConnectionStage.Authenticating)

            // ONE overall deadline for the whole callback set (§2.4 rule 1:
            // Psiphon-callback 120 s), shared by the port wait and the
            // established wait — not 120 s each.
            val deadlineNanos = System.nanoTime() +
                ProcessSupervisor.deadlineFor(CoreKind.PSIPHON) * NANOS_PER_MS
            fun remainingMs(): Long =
                ((deadlineNanos - System.nanoTime()) / NANOS_PER_MS).coerceAtLeast(1L)

            val port = try {
                withTimeoutOrNull(remainingMs()) {
                    select<Int> {
                        socksPort.onAwait { it }
                        earlyStop.onAwait { throw EarlyStop(it) }
                    }
                }
            } catch (e: EarlyStop) {
                throw HopFailure(
                    hop.index, CoreKind.PSIPHON, hop.profile.name, ConnectionStage.Authenticating,
                    e.reason ?: "psiphon stopped before reporting a listening port", e,
                )
            } ?: run {
                runCatching { controller.stop() }
                throw HopFailure(
                    hop.index, CoreKind.PSIPHON, hop.profile.name, ConnectionStage.Authenticating,
                    "psiphon did not report a listening port",
                )
            }

            val tunnelUp = try {
                withTimeoutOrNull(remainingMs()) {
                    select<Boolean> {
                        established.onAwait { true }
                        earlyStop.onAwait { throw EarlyStop(it) }
                    }
                }
            } catch (e: EarlyStop) {
                throw HopFailure(
                    hop.index, CoreKind.PSIPHON, hop.profile.name, ConnectionStage.Authenticating,
                    e.reason ?: "psiphon stopped before the tunnel established", e,
                )
            }
            if (tunnelUp != true) {
                runCatching { controller.stop() }
                throw HopFailure(
                    hop.index, CoreKind.PSIPHON, hop.profile.name, ConnectionStage.Authenticating,
                    "psiphon tunnel did not establish in time",
                )
            }

            tracker.set(ConnectionStage.Verifying)
            HopHandle(
                socks = SocksEndpoint(port = port),
                stages = tracker.flow,
                traffic = { null },
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
        const val CONFIG_FILE_NAME = "psiphon-config.json"
        const val NANOS_PER_MS = 1_000_000L

        /**
         * Rewrite `DataRootDirectory` to [dataRoot], preserving all other
         * keys (parse/rebuild, so key order and value shapes survive).
         */
        fun withDataRootDirectory(json: String, dataRoot: String): String {
            val root = Json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("psiphon config is not a JSON object")
            return buildJsonObject {
                root.forEach { (key, value) ->
                    if (key != "DataRootDirectory") put(key, value)
                }
                put("DataRootDirectory", dataRoot)
            }.toString()
        }
    }
}
