package com.amyrmo.damavand.lite.aether

import android.content.Context
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Delay test for an Aether profile (3.2.13) — a Lite-simplified port
 * of PattNG's AetherDelayTester: one core on the Aether listen port,
 * a GET of the delay-test URL through its SOCKS listener, the round
 * trip in milliseconds, or -1 when the tunnel cannot come up within
 * the budget. No Xray is involved — the core dials out on its own, so
 * a profile that names an exit-node is measured without it, as the
 * core alone runs it; the exit-node only steers traffic once Xray is
 * beside the core.
 *
 * One test at a time (the listen port is single, like PattNG's
 * `tunnels` mutex), and NEVER beside a live Aether session — the
 * caller refuses that case, since AetherEngine.start stops any
 * running session first and would kill the live tunnel.
 */
object AetherDelayTester {

    /** The whole test, tunnel start included (PattNG's TEST_BUDGET_MS). */
    private const val TEST_BUDGET_MS = 12_000L

    private val tunnels = Mutex()

    suspend fun measure(
        context: Context,
        settings: AetherSettings,
        aetherPort: Int,
        testUrl: String,
    ): Long = tunnels.withLock {
        if (!AetherEngine.isSupported(context)) return@withLock -1L
        val core = AetherCore.of(settings, aetherPort)
        if (core.invalidListener != null || core.unreachableListener != null) return@withLock -1L
        if (AetherArgs.invalidPort(core) != null) return@withLock -1L
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + TEST_BUDGET_MS
            AetherEngine.start(context, core) {}
            try {
                val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                if (!AetherEngine.awaitListening(remaining)) return@withContext -1L
                val left = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1000)
                val t0 = System.currentTimeMillis()
                val ok = try {
                    val proxy = Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress(AetherArgs.LOOPBACK, core.port),
                    )
                    val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
                    conn.connectTimeout = left
                    conn.readTimeout = left
                    conn.instanceFollowRedirects = false
                    conn.connect()
                    val code = conn.responseCode
                    runCatching { conn.inputStream.use { it.read() } }
                    conn.disconnect()
                    code == 204 || code == 200
                } catch (_: Exception) {
                    false
                }
                if (!ok) -1L else System.currentTimeMillis() - t0
            } finally {
                AetherEngine.stop()
            }
        }
    }
}
