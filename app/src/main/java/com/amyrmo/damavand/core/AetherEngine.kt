package com.amyrmo.damavand.core

import android.content.Context
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.random.Random

/**
 * The Aether core (patterniha/Aether, Rust) as a child process exposing a local
 * SOCKS5 listener. The VPN layer bridges that listener into the TUN via a
 * sing-box socks outbound, so Aether behaves like any other engine.
 *
 * Modes map 1:1 onto the core's own flags:
 *  - [Mode.Tunnel]     plain tunnel (VMess/any protocol the core speaks)
 *  - [Mode.Psiphon]    --psiphon-only: the SOCKS listener is plain Psiphon
 *  - [Mode.Tor]        --tor-only:   the SOCKS listener is plain Tor
 */
class AetherEngine(
    private val context: Context,
    private val mode: Mode = Mode.Tunnel,
    private val accessKey: String = "",
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    enum class Mode { Tunnel, Psiphon, Tor }

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

            val stateDir = File(context.filesDir, "aether").apply { mkdirs() }
            val cmd = mutableListOf(bin.absolutePath, "--bind", "127.0.0.1:$socksPort", "--log-level", "warn")
            when (mode) {
                Mode.Psiphon -> {
                    cmd += "--psiphon-only"
                    if (psi.exists()) cmd += listOf("--psiphon-bin", psi.absolutePath)
                    if (accessKey.isNotBlank()) cmd += listOf("--psiphon-server-entries", accessKey)
                }
                Mode.Tor -> {
                    cmd += "--tor-only"
                    if (accessKey.isNotBlank()) cmd += listOf("--tor-bridge", accessKey)
                }
                Mode.Tunnel -> {
                    if (accessKey.isNotBlank()) cmd += listOf("--config", writeConfig(stateDir, accessKey))
                }
            }

            val proc = ProcessBuilder(cmd)
                .directory(stateDir)
                .redirectErrorStream(true)
                .start()
            process = proc
            onLog("aether core started (mode=$mode, socks=127.0.0.1:$socksPort)")

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

    fun stop() {
        active.set(false)
        runCatching { process?.destroy() }
        watcher = null
        process = null
    }

    /** A --config file with an outbound def the core understands (URL form). */
    private fun writeConfig(dir: File, key: String): String {
        val f = File(dir, "outbound.txt")
        f.writeText(key.trim())
        return f.absolutePath
    }

    companion object {
        /** True when the current ABI ships the core (x86 is intentionally uncovered). */
        fun available(context: Context): Boolean =
            File(context.applicationInfo.nativeLibraryDir, "libaether.so").exists()
    }
}
