package com.amyrmo.damavand.core.supervisor

import com.amyrmo.damavand.model.CoreKind
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * The ONLY place a core process may be spawned (architecture §2.4 rule 1).
 * Adapters never touch `ProcessBuilder`: they describe a [Spec] and get a
 * [Handle] with log streaming, exit observation and the one stop ladder.
 *
 * What lives here, once:
 *  - the owner stamp: every child gets `DAMAVAND_<KIND>_OWNER=<myPid>` in
 *    its environment, so a later start can recognise leftovers of a dead
 *    app process in /proc;
 *  - orphan reaping: before every spawn, processes whose argv[0] is the
 *    same binary and whose owner stamp names a pid that no longer exists
 *    are killed (Android can kill the app without its children following);
 *  - the stop ladder: destroy → wait 2 s → destroyForcibly → wait 2 s.
 *
 * Connect deadlines are per core ([deadlineFor]); the supervisor cannot
 * know when a core is "connected", so adapters enforce the deadline
 * against their own readiness probe — the supervisor just owns the number.
 */
class ProcessSupervisor {

    data class Spec(
        val kind: CoreKind,
        val argv: List<String>,
        val workDir: File,
        val extraEnv: Map<String, String> = emptyMap(),
    )

    class Handle internal constructor(
        val process: Process,
        /** Completes with the exit code when the process ends. */
        val exited: Deferred<Int>,
    ) {
        val isAlive: Boolean get() = process.isAlive

        /**
         * The one stop ladder: destroy, wait [STOP_WAIT_MS], destroy
         * forcibly, wait again. (Deliberate-stop vs death is distinguished
         * by the adapters' own requested flags, not here.)
         */
        fun stop() {
            if (!process.isAlive) return
            process.destroy()
            if (awaitExit(STOP_WAIT_MS)) return
            process.destroyForcibly()
            awaitExit(STOP_WAIT_MS)
        }

        /** Immediate destroy (callers with their own ladder, e.g. a registration watchdog). */
        fun destroy() {
            process.destroy()
        }

        /** Immediate forcible kill (watchdogs that have already waited). */
        fun destroyForcibly() {
            process.destroyForcibly()
        }

        private fun awaitExit(ms: Long): Boolean = try {
            process.waitFor(ms, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            !process.isAlive
        }
    }

    /**
     * Reap owner-stamped orphans of [binary], then spawn [spec].
     * [onLine] receives merged stdout/stderr lines; [onExit] runs once on
     * the watcher thread with the exit code (also for deliberate stops —
     * callers distinguish via their own requested flag / [Handle] state).
     */
    fun spawn(
        spec: Spec,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
    ): Handle {
        val binary = File(spec.argv.first())
        reapOrphans(spec.kind, binary)

        val builder = ProcessBuilder(spec.argv)
            .directory(spec.workDir.apply { mkdirs() })
            .redirectErrorStream(true)
        builder.environment()[ownerEnvName(spec.kind)] = android.os.Process.myPid().toString()
        builder.environment().putAll(spec.extraEnv)

        val process = builder.start()
        val exited = CompletableDeferred<Int>()
        val handle = Handle(process, exited)

        Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    runCatching { onLine(line) }
                }
            } catch (_: Exception) {
                // Stream closed by the process ending; the watcher reports it.
            }
        }, "supervisor-${spec.kind.name.lowercase()}-out").apply { isDaemon = true; start() }

        Thread({
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            exited.complete(code)
            runCatching { onExit(code) }
        }, "supervisor-${spec.kind.name.lowercase()}-wait").apply { isDaemon = true; start() }

        return handle
    }

    /**
     * Kill leftover processes of [binary] whose owner stamp names a dead
     * app process. Returns the killed pids. A process whose owner cannot
     * be read is left alone (it may be this live app's own core).
     */
    fun reapOrphans(kind: CoreKind, binary: File): List<Int> {
        val path = binary.absolutePath
        val envName = ownerEnvName(kind)
        val entries = PROC_DIR.listFiles() ?: return emptyList()
        val killed = mutableListOf<Int>()
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid == android.os.Process.myPid()) continue
            val argv = readNulSeparated(File(entry, "cmdline")) ?: continue
            if (argv.firstOrNull() != path) continue
            val environ = readNulSeparated(File(entry, "environ")) ?: continue
            val owner = environ.firstOrNull { it.startsWith("$envName=") }
                ?.substringAfter('=')?.toIntOrNull() ?: continue
            val ownerAlive = File(PROC_DIR, owner.toString()).isDirectory
            if (!ownerAlive) {
                runCatching { android.os.Process.killProcess(pid) }
                killed += pid
            }
        }
        return killed
    }

    companion object {
        /** How long each rung of the stop ladder waits (§2.4 rule 1). */
        const val STOP_WAIT_MS = 2_000L

        private val PROC_DIR = File("/proc")

        /** The owner-stamp environment variable for [kind], e.g. `DAMAVAND_AETHER_OWNER`. */
        fun ownerEnvName(kind: CoreKind): String = "DAMAVAND_${kind.name}_OWNER"

        /**
         * The connect deadline per core (§2.4 rule 1): how long a start
         * may take before the adapter must fail the hop. Psiphon's is for
         * its callback set (it is an in-process core with the same
         * contract); Xray/sing-box use the default.
         */
        fun deadlineFor(kind: CoreKind): Long = when (kind) {
            CoreKind.AETHER -> 120_000L
            CoreKind.MIHOMO -> 60_000L
            CoreKind.WHITEDNS -> 180_000L
            CoreKind.PSIPHON -> 120_000L
            else -> 120_000L
        }

        private fun readNulSeparated(file: File): List<String>? = try {
            val text = file.readBytes().toString(Charsets.UTF_8)
            text.split('\u0000').let { words -> if (words.last().isEmpty()) words.dropLast(1) else words }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
