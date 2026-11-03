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
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The WhiteDNS hop adapter (architecture §2.5): the CottenDNS client
 * (`libcottendns.so`, D6 exec route) run under the [ProcessSupervisor]
 * over the two files the planner rendered — the adapter only writes them
 * out, under the exact filenames the Phase-0 spike used, and starts the
 * engine with its `-config`/`-resolvers` flags.
 *
 * The engine's `WD_PROGRESS` telemetry drives the staged indicator, and
 * readiness (§2.4) is two-step: the SOCKS listener opens only after a
 * resolver path validates, and then a real SOCKS5 CONNECT through it must
 * be granted — a listener that answers the greeting but refuses every
 * CONNECT is not a tunnel.
 */
class WhitednsAdapter(private val supervisor: ProcessSupervisor) : CoreAdapter {

    override val kind: CoreKind = CoreKind.WHITEDNS

    private class WhitednsPrepared(
        override val hop: PlannedHop,
        override val ctx: HopContext,
        val configFile: File,
        val resolversFile: File,
        val listenPort: Int,
    ) : PreparedHop

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        val config = hop.config as? HopConfig.WhitednsFiles
            ?: throw IllegalStateException(
                "whitedns hop ${hop.index} carries ${hop.config::class.simpleName}, expected WhitednsFiles"
            )
        val listenPort = hop.listenPort
            ?: throw IllegalStateException("whitedns hop ${hop.index} has no allocated listen port")
        // Whitedns is a chain's entry hop only (the DNS engine has no
        // upstream-proxy capability); an upstream here is ignored.
        val configFile = File(ctx.sessionDir, "client_config.toml").apply { writeText(config.configToml) }
        val resolversFile = File(ctx.sessionDir, "client_resolvers.txt").apply { writeText(config.resolversTxt) }
        return WhitednsPrepared(hop, ctx, configFile, resolversFile, listenPort)
    }

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val p = prepared as? WhitednsPrepared
            ?: throw IllegalStateException("WhitednsAdapter.start got a hop it did not prepare")
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
                val bin = File(ctx.appContext.applicationInfo.nativeLibraryDir, "libcottendns.so")
                if (!bin.exists()) {
                    throw HopFailure(
                        hop.index, kind, hop.profile.name, ConnectionStage.Starting,
                        "cottendns binary missing",
                    )
                }

                tracker.set(ConnectionStage.Starting)
                val handle = supervisor.spawn(
                    ProcessSupervisor.Spec(
                        kind = CoreKind.WHITEDNS,
                        argv = listOf(
                            bin.absolutePath,
                            "-config", p.configFile.absolutePath,
                            "-resolvers", p.resolversFile.absolutePath,
                        ),
                        workDir = ctx.sessionDir,
                    ),
                    onLine = { line ->
                        ctx.log("[whitedns] $line")
                        forwardProgress(line, tracker)
                    },
                    onExit = { code ->
                        exitCode.set(code)
                        exitLatch.countDown()
                        if (stopRequested.get()) {
                            stopped.complete(StopReason.Requested)
                        } else {
                            stopped.complete(StopReason.Died("cottendns exited ($code)"))
                        }
                    },
                )
                handleRef.set(handle)
                ctx.log("[whitedns] cottendns started (socks=127.0.0.1:${p.listenPort})")

                // Readiness: the listener opens after a resolver path
                // validates; a granted CONNECT through it proves transfer.
                val deadlineMs = ProcessSupervisor.deadlineFor(CoreKind.WHITEDNS)
                val deadline = System.nanoTime() + deadlineMs * 1_000_000
                val answers = SocksAnswers(1000)
                var listenerSeen = false
                var verified = false
                while (System.nanoTime() < deadline) {
                    exitCode.get()?.let { code ->
                        throw HopFailure(
                            hop.index, kind, hop.profile.name, tracker.current,
                            "cottendns exited ($code) before becoming ready",
                        )
                    }
                    if (answers.answers(p.listenPort)) {
                        listenerSeen = true
                        if (socksConnectCheck(p.listenPort, CHECK_HOST, CHECK_PORT)) {
                            verified = true
                            break
                        }
                    }
                }
                if (!verified) {
                    throw HopFailure(
                        hop.index, kind, hop.profile.name, tracker.current,
                        if (listenerSeen) {
                            "cottendns listener opened but the transfer check was never granted"
                        } else {
                            "cottendns SOCKS listener did not open (no resolver path validated)"
                        },
                    )
                }
                tracker.set(ConnectionStage.Verifying)

                HopHandle(
                    socks = SocksEndpoint(port = p.listenPort),
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
                stopRequested.set(true)
                runCatching { handleRef.get()?.stop() }
                throw failure
            } catch (e: Exception) {
                stopRequested.set(true)
                runCatching { handleRef.get()?.stop() }
                throw HopFailure(
                    hop.index, kind, hop.profile.name, tracker.current,
                    e.message ?: "cottendns failed to start", e,
                )
            }
        }
    }

    /**
     * Maps one `WD_PROGRESS phase=<…> percent=<n>` telemetry line onto the
     * hop's stage (§5.5). Lines without the marker, and phases Damavand has
     * no mapping for, are only logged (the caller already forwarded them).
     */
    private fun forwardProgress(line: String, tracker: StageTracker) {
        val marker = "WD_PROGRESS"
        val at = line.indexOf(marker)
        if (at < 0) return
        val fields = line.substring(at + marker.length).trim()
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val eq = token.indexOf('=')
                if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
            }
            .toMap()
        val phase = fields["phase"] ?: return
        // The mtu phase reports a fraction (percent=56/80 in the Phase-0
        // spike); the others report a plain percent.
        val progress = fields["percent"]?.let { if ('/' in it) it else "$it%" }
        when (phase) {
            "starting" -> tracker.set(
                ConnectionStage.Starting,
                if (progress != null) "starting ($progress)" else "starting",
            )
            "mtu" -> tracker.set(
                ConnectionStage.Starting,
                if (progress != null) "probing resolvers ($progress)" else "probing resolvers",
            )
            "retry" -> tracker.set(
                ConnectionStage.Authenticating,
                if (progress != null) "retrying ($progress)" else "retrying",
            )
        }
    }

    /**
     * A SOCKS5 handshake + CONNECT to [host]:[hostPort] through the
     * listener on [port]; true only when the proxy grants the CONNECT
     * (reply code 0). Plain [Socket] I/O, bounded by [CHECK_TIMEOUT_MS].
     */
    private fun socksConnectCheck(port: Int, host: String, hostPort: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), CHECK_TIMEOUT_MS)
                socket.soTimeout = CHECK_TIMEOUT_MS
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // Greeting: version 5, one method, no-auth.
                out.write(byteArrayOf(5, 1, 0))
                out.flush()
                val greeting = ByteArray(2)
                if (!readFully(input, greeting)) return false
                if (greeting[0].toInt() != 5 || greeting[1].toInt() != 0) return false

                // CONNECT by IPv4 address (ATYP=1).
                val address = InetAddress.getByName(host).address
                if (address.size != 4) return false
                val request = ByteArray(10)
                request[0] = 5 // VER
                request[1] = 1 // CONNECT
                request[2] = 0 // RSV
                request[3] = 1 // IPv4
                address.copyInto(request, 4)
                request[8] = (hostPort ushr 8).toByte()
                request[9] = (hostPort and 0xFF).toByte()
                out.write(request)
                out.flush()

                val header = ByteArray(4)
                if (!readFully(input, header)) return false
                if (header[0].toInt() != 5 || header[1].toInt() != 0) return false
                // Drain the bound address per ATYP so the socket closes clean.
                val drain = when (header[3].toInt()) {
                    1 -> 6
                    3 -> {
                        val len = input.read()
                        if (len < 0) return false
                        len + 2
                    }
                    4 -> 18
                    else -> return false
                }
                val rest = ByteArray(drain)
                readFully(input, rest)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Reads exactly `buffer.size` bytes; false when the stream ends first. */
    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private companion object {
        /** The well-known anycast target of the transfer check: one.one.one.one:443. */
        const val CHECK_HOST = "1.1.1.1"
        const val CHECK_PORT = 443
        const val CHECK_TIMEOUT_MS = 5_000
    }
}
