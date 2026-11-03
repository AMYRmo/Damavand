package com.amyrmo.damavand.core.adapters

import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.PreparedHop
import com.amyrmo.damavand.core.StageTracker
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.SocksAnswers
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.MihomoConfigEditor
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Mihomo hop adapter (architecture §2.5), rewritten on the
 * [ProcessSupervisor] + [MihomoConfigEditor] template — v1's engine, which
 * built its config by appending override lines to the user's YAML, is not
 * carried over: the config here is the planner's structured edit, or the
 * same edit applied at start time when the previous hop's port was only
 * known then (the splice).
 *
 * Readiness: the mixed listener answering the SOCKS greeting, then a real
 * HTTP fetch through the mixed port's HTTP side (mihomo's
 * external-controller is disabled in prepared configs, so there is no
 * controller delay API to ask — the fetch IS the delay test).
 */
class MihomoAdapter(private val supervisor: ProcessSupervisor) : CoreAdapter {

    override val kind: CoreKind = CoreKind.MIHOMO

    private class MihomoPrepared(
        override val hop: PlannedHop,
        override val ctx: HopContext,
        val yaml: String,
        val mixedPort: Int,
    ) : PreparedHop

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        val config = hop.config as? HopConfig.MihomoYaml
            ?: throw IllegalStateException(
                "mihomo hop ${hop.index} carries ${hop.config::class.simpleName}, expected MihomoYaml"
            )
        val mixedPort = hop.listenPort
            ?: throw IllegalStateException("mihomo hop ${hop.index} has no allocated listen port")
        val yaml = if (config.splicePending) {
            // The previous hop's listener is known only now; the structured
            // edit runs here, on the hop's runtime profile copy.
            val profile = hop.profile as? MihomoProfile
                ?: throw IllegalStateException("mihomo hop ${hop.index} profile is not a MihomoProfile")
            MihomoConfigEditor.prepare(profile, mixedPort, upstream?.port)
        } else {
            // Fully wired at plan time. An upstream arriving anyway would be
            // a planner contradiction; the plan's yaml stands.
            config.yaml
                ?: throw IllegalStateException("mihomo hop ${hop.index} has neither a prepared yaml nor a pending splice")
        }
        if (!MihomoConfigEditor.hasDialableProxy(yaml)) {
            throw IllegalStateException("mihomo config has no dialable proxy")
        }
        return MihomoPrepared(hop, ctx, yaml, mixedPort)
    }

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val p = prepared as? MihomoPrepared
            ?: throw IllegalStateException("MihomoAdapter.start got a hop it did not prepare")
        val ctx = p.ctx
        val hop = p.hop
        return withContext(Dispatchers.IO) {
            val tracker = StageTracker(ctx, ConnectionStage.Starting)
            val stopped = CompletableDeferred<StopReason>()
            val stopRequested = AtomicBoolean(false)
            val exitCode = AtomicReference<Int?>(null)
            val exitLatch = CountDownLatch(1)
            val handleRef = AtomicReference<ProcessSupervisor.Handle?>(null)

            try {
                val configFile = File(ctx.sessionDir, "config.yaml")
                configFile.writeText(p.yaml)

                val bin = File(ctx.appContext.applicationInfo.nativeLibraryDir, "libmihomo.so")
                if (!bin.exists()) {
                    throw HopFailure(
                        hop.index, kind, hop.profile.name, ConnectionStage.Starting,
                        "mihomo core binary missing",
                    )
                }

                tracker.set(ConnectionStage.Starting)
                val handle = supervisor.spawn(
                    ProcessSupervisor.Spec(
                        kind = CoreKind.MIHOMO,
                        argv = listOf(bin.absolutePath, "-d", ctx.sessionDir.absolutePath, "-f", configFile.absolutePath),
                        workDir = ctx.sessionDir,
                    ),
                    onLine = { line -> ctx.log("[mihomo] $line") },
                    onExit = { code ->
                        exitCode.set(code)
                        exitLatch.countDown()
                        if (stopRequested.get()) {
                            stopped.complete(StopReason.Requested)
                        } else {
                            stopped.complete(StopReason.Died("mihomo exited ($code)"))
                        }
                    },
                )
                handleRef.set(handle)
                ctx.log("[mihomo] core started (mixed=127.0.0.1:${p.mixedPort})")

                // Readiness: the mixed listener answers the SOCKS greeting.
                val deadlineMs = ProcessSupervisor.deadlineFor(CoreKind.MIHOMO)
                val deadline = System.nanoTime() + deadlineMs * 1_000_000
                val answers = SocksAnswers(1000)
                var listening = false
                while (System.nanoTime() < deadline) {
                    exitCode.get()?.let { code ->
                        throw HopFailure(
                            hop.index, kind, hop.profile.name, tracker.current,
                            "mihomo exited ($code) before becoming ready",
                        )
                    }
                    if (answers.answers(p.mixedPort)) {
                        listening = true
                        break
                    }
                }
                if (!listening) {
                    throw HopFailure(
                        hop.index, kind, hop.profile.name, tracker.current,
                        "mihomo core did not become ready",
                    )
                }
                tracker.set(ConnectionStage.Authenticating)

                // Delay test: an HTTP fetch through the mixed port's HTTP
                // proxy side; any HTTP response proves a proxy path works.
                var answered = false
                while (System.nanoTime() < deadline) {
                    exitCode.get()?.let { code ->
                        throw HopFailure(
                            hop.index, kind, hop.profile.name, tracker.current,
                            "mihomo exited ($code) before the delay test passed",
                        )
                    }
                    if (httpProxyAnswers(p.mixedPort)) {
                        answered = true
                        break
                    }
                    Thread.sleep(DELAY_RETRY_MS)
                }
                if (!answered) {
                    throw HopFailure(
                        hop.index, kind, hop.profile.name, ConnectionStage.Authenticating,
                        "mihomo delay test got no answer through the mixed port",
                    )
                }
                tracker.set(ConnectionStage.Verifying)

                HopHandle(
                    socks = SocksEndpoint(port = p.mixedPort),
                    stages = tracker.flow,
                    traffic = { null },
                    stopped = stopped,
                    stop = {
                        stopRequested.set(true)
                        withContext(Dispatchers.IO) { handleRef.get()?.stop() }
                        stopped.complete(StopReason.Requested)
                    },
                )
            } catch (failure: HopFailure) {
                // No handle escapes a failed start; the process must not
                // outlive it. The stop is marked requested first so the
                // exit callback cannot race a Died into a dead handle.
                stopRequested.set(true)
                runCatching { handleRef.get()?.stop() }
                throw failure
            } catch (e: Exception) {
                stopRequested.set(true)
                runCatching { handleRef.get()?.stop() }
                throw HopFailure(
                    hop.index, kind, hop.profile.name, tracker.current,
                    e.message ?: "mihomo failed to start", e,
                )
            }
        }
    }

    /**
     * One delay-test attempt: an HTTPS fetch of the generate_204 endpoint
     * through the HTTP proxy on [mixedPort], 5 s timeouts. Any HTTP
     * response status (204/200/3xx) counts as an answer.
     */
    private fun httpProxyAnswers(mixedPort: Int): Boolean = try {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", mixedPort))
        val connection = URI("https://www.gstatic.com/generate_204").toURL().openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = DELAY_TIMEOUT_MS
        connection.readTimeout = DELAY_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        try {
            connection.responseCode in 200..399
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val DELAY_TIMEOUT_MS = 5_000
        const val DELAY_RETRY_MS = 1_000L
    }
}
