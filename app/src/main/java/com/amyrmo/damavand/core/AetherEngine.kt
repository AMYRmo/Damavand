package com.amyrmo.damavand.core

import android.content.Context
import com.amyrmo.damavand.domain.config.AetherProfile
import java.io.File
import kotlin.random.Random

/**
 * The Aether core (patterniha/Aether, Rust) as a child process exposing a local
 * SOCKS5 listener. The VPN layer bridges that listener into the TUN via a
 * sing-box socks outbound, so Aether behaves like any other engine.
 *
 * Process plumbing follows PattNG's AetherCoreManager: a work directory holding
 * the WARP identity (aether.toml and friends) is pointed at through the
 * AETHER_* environment variables, the Psiphon tunnel core is handed over with
 * --psiphon-bin, and the arguments come from the profile's PattNG flag set.
 */
class AetherEngine(
    private val context: Context,
    val config: AetherProfile = AetherProfile(),
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    @Volatile private var process: Process? = null
    @Volatile private var watcher: Thread? = null
    private val active = java.util.concurrent.atomic.AtomicBoolean(false)

    val socksPort: Int = 18190 + Random.nextInt(1, 2000)

    val isRunning: Boolean get() = active.get() && process?.isAlive == true

    fun start() {
        check(active.compareAndSet(false, true)) { "aether already running" }
        try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
            val psi = File(context.applicationInfo.nativeLibraryDir, "libpsiphon-tunnel-core.so")
            if (!bin.exists()) {
                onStopped("aether core binary missing")
                active.set(false)
                return
            }

            val workDir = File(context.filesDir, "aether").apply { mkdirs() }
            val cmd = mutableListOf(bin.absolutePath, "--bind", "127.0.0.1:$socksPort")
            cmd += config.toFlags()
            cmd += listOf("--log-level", "warn")
            if (psi.exists()) cmd += listOf("--psiphon-bin", psi.absolutePath)

            val builder = ProcessBuilder(cmd)
                .directory(workDir)
                .redirectErrorStream(true)
            builder.environment().apply {
                put("HOME", workDir.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
                put("AETHER_CONFIG", File(workDir, "aether.toml").absolutePath)
                put("AETHER_MASQUE_CONFIG", File(workDir, "masque.toml").absolutePath)
                put("AETHER_WG_CONFIG", File(workDir, "wireguard.toml").absolutePath)
                if (psi.exists()) put("PSIPHON_BINARY", psi.absolutePath)
            }
            val proc = builder.start()
            process = proc
            onLog("aether core started (socks=127.0.0.1:$socksPort)")
            onLog(config.commandPreview(socksPort))

            watcher = Thread({
                val out = proc.inputStream.bufferedReader()
                try {
                    var line = out.readLine()
                    while (line != null && active.get()) {
                        onLog("[aether] $line")
                        line = out.readLine()
                    }
                } catch (_: Exception) {
                }
                val code = runCatching { proc.waitFor() }.getOrDefault(-1)
                if (active.getAndSet(false) && code != 0) {
                    onStopped("aether core exited ($code)")
                }
            }, "aether-core").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            active.set(false)
            onStopped("aether start failed: ${e.message}")
        }
    }

    /**
     * Runs the core once with `--register <kind>` to fetch fresh WARP keys into
     * [workDir]; PattNG's "Get new WARP keys" flow. Returns when the process ends.
     */
    fun registerKeys(kind: String, onDone: (Boolean, String?) -> Unit) {
        val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
        if (!bin.exists()) {
            onDone(false, "aether core binary missing")
            return
        }
        val workDir = File(context.filesDir, "aether").apply { mkdirs() }
        val cmd = mutableListOf(bin.absolutePath, "--register", kind, "--log-level", "info")
        Thread({
            var ok = false
            var error: String? = null
            try {
                val builder = ProcessBuilder(cmd)
                    .directory(workDir)
                    .redirectErrorStream(true)
                builder.environment().apply {
                    put("HOME", workDir.absolutePath)
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("AETHER_CONFIG", File(workDir, "aether.toml").absolutePath)
                    put("AETHER_MASQUE_CONFIG", File(workDir, "masque.toml").absolutePath)
                    put("AETHER_WG_CONFIG", File(workDir, "wireguard.toml").absolutePath)
                }
                val proc = builder.start()
                val out = proc.inputStream.bufferedReader()
                var line = out.readLine()
                while (line != null) {
                    onLog("[aether-keys] $line")
                    if (line.contains("registered", ignoreCase = true) || line.contains("saved", ignoreCase = true)) ok = true
                    line = out.readLine()
                }
                val code = proc.waitFor()
                ok = ok || code == 0
                if (code != 0) error = "register exited ($code)"
            } catch (e: Exception) {
                error = e.message
            }
            onDone(ok, error)
        }, "aether-register").apply { isDaemon = true; start() }
    }

    fun stop() {
        active.set(false)
        runCatching { process?.destroy() }
        watcher = null
        process = null
    }

    companion object {
        /** True when the current ABI ships the core. */
        fun available(context: Context): Boolean =
            File(context.applicationInfo.nativeLibraryDir, "libaether.so").exists()
    }
}
