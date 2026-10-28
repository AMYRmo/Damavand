package com.amyrmo.damavand.core

import android.content.Context
import com.amyrmo.damavand.domain.config.MihomoProfile
import java.io.File
import kotlin.random.Random

/**
 * The mihomo core (MetaCubeX/mihomo, the FlClash core) as a child process with
 * a mixed (HTTP+SOCKS) listener. The VPN layer bridges that listener into the
 * TUN via a sing-box socks outbound, so mihomo behaves like any other engine.
 */
class MihomoEngine(
    private val context: Context,
    val config: MihomoProfile = MihomoProfile(),
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    @Volatile private var process: Process? = null
    @Volatile private var watcher: Thread? = null
    private val active = java.util.concurrent.atomic.AtomicBoolean(false)

    val mixedPort: Int = config.mixedPort.takeIf { it in 1024..65535 }
        ?: 17890 + Random.nextInt(1, 2000)

    val isRunning: Boolean get() = active.get() && process?.isAlive == true

    fun start() {
        check(active.compareAndSet(false, true)) { "mihomo already running" }
        try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so")
            if (!bin.exists()) {
                onStopped("mihomo core binary missing")
                active.set(false)
                return
            }

            val workDir = File(context.filesDir, "mihomo").apply { mkdirs() }
            val configFile = File(workDir, "config.yaml")
            configFile.writeText(
                config.yaml +
                    "\n\nmixed-port: $mixedPort" +
                    "\nallow-lan: false" +
                    "\nbind-address: 127.0.0.1" +
                    "\nmode: rule" +
                    "\nlog-level: ${config.logLevel.ifBlank { "warning" }}" +
                    "\nexternal-controller: \"\"" +
                    "\nipv6: true" +
                    "\n",
            )

            val cmd = listOf(bin.absolutePath, "-d", workDir.absolutePath, "-f", configFile.absolutePath)
            val proc = ProcessBuilder(cmd)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            process = proc
            onLog("mihomo core started (mixed=127.0.0.1:$mixedPort)")

            watcher = Thread({
                val out = proc.inputStream.bufferedReader()
                try {
                    var line = out.readLine()
                    while (line != null && active.get()) {
                        onLog("[mihomo] $line")
                        line = out.readLine()
                    }
                } catch (_: Exception) {
                }
                val code = runCatching { proc.waitFor() }.getOrDefault(-1)
                if (active.getAndSet(false) && code != 0) {
                    onStopped("mihomo core exited ($code)")
                }
            }, "mihomo-core").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            active.set(false)
            onStopped("mihomo start failed: ${e.message}")
        }
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
            File(context.applicationInfo.nativeLibraryDir, "libmihomo.so").exists()
    }
}
