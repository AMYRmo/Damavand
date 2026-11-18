package com.amyrmo.damavand.lite.psiphon

import android.content.Context
import com.amyrmo.damavand.lite.AppLog
import com.amyrmo.damavand.lite.aether.PsiphonServerList
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the Psiphon core as a standalone child process (1.0.1.6) —
 * the psiphon-tunnel-core console client the app ships as
 * `libpsiphon-tunnel-core.so`, spawned the way Aether spawns it
 * (aether/psiphon.rs `start`): `-config` + `-dataRootDirectory`,
 * `-serverList` for the bundled entries, JSON notices on stderr,
 * plain log lines on stdout.
 *
 * No Aether is involved: this engine IS the Psiphon core. The
 * client opens a local SOCKS listener once it has a tunnel
 * (`ListeningSocksProxyPort` notice + `Tunnels` count > 0), and the
 * service bridges that listener through the tun ([awaitReady] also
 * asks the listener a SOCKS greeting, so a port that answers before
 * it carries anything cannot pass as ready).
 *
 * Structure mirrors [com.amyrmo.damavand.lite.aether.AetherEngine]:
 * one session at a time, a single-thread lifecycle executor, the
 * old core stopped and waited for before a new one starts (3.2.23),
 * a null process meaning "not started yet" rather than "dead"
 * (3.2.24), SIGTERM then force on stop, stale/orphan reaping by a
 * /proc scan.
 */
object PsiphonEngine {

    private const val TAG = "PsiphonEngine"

    const val BINARY_NAME = "libpsiphon-tunnel-core.so"

    const val OWNER_ENV = "LITE_PSIPHON_OWNER"
    const val SESSION_ENV = "LITE_PSIPHON_SESSION"

    private const val CONFIG_FILE = "psiphon.json"

    private val androidCertificateDirectories = listOf(
        "/apex/com.android.conscrypt/cacerts",
        "/system/etc/security/cacerts",
    )

    private const val READY_POLL_MS = 500L
    private const val EXIT_WAIT_MS = 2_000L
    private const val PROBE_TIMEOUT_MS = 500L

    private val lifecycle = Executors.newSingleThreadExecutor { task ->
        Thread(task, "psiphon-core").apply { isDaemon = true }
    }

    /** One session at a time. */
    @Volatile
    private var session: Session? = null

    /** The last error notice the session core wrote, for the failure detail. */
    @Volatile
    private var lastError: String? = null

    val isRunning: Boolean get() = session != null

    /** What to tell the user of the session core that ended on its own last. */
    fun stoppedMessage(): String? = lastError

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun isSupported(context: Context): Boolean = binary(context).canExecute()

    /** The work dir: the config, the server entries and the client's data root live here. */
    fun workDir(context: Context): File = File(context.filesDir, "psiphon")

    /**
     * Starts the core on [socksPort] (the service's SOCKS port — the
     * tun bridge dials it), leaving from [region] (a 2-letter ISO
     * code, or null for "Any country"). Any running session is
     * stopped first — on the core's executor, and waited for — so a
     * start that follows comes up only once the old core is gone.
     * [onExit] runs when the process ends on its own (not when
     * [stop] ends it — the caller already knows then).
     */
    @Synchronized
    fun start(context: Context, socksPort: Int, region: String?, onExit: () -> Unit) {
        val old = session
        session = null
        if (old != null) {
            val done = lifecycle.submit { old.process?.let { end(it, old.context) } }
            try {
                done.get()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
            }
        }
        val appContext = context.applicationContext
        val next = Session(
            port = socksPort,
            region = region,
            context = appContext,
            onExit = onExit,
        )
        session = next
        lastError = null
        lifecycle.execute { open(next, appContext) }
    }

    /**
     * Stops the session core. The work runs on the core's executor,
     * so a start that follows queues behind it and comes up only
     * once the old core is gone, with its listener and the client's
     * datastore lock.
     */
    @Synchronized
    fun stop() {
        val current = session ?: return
        session = null
        lifecycle.execute { current.process?.let { end(it, current.context) } }
    }

    /**
     * Waits until the core has a tunnel AND its SOCKS listener
     * answers a greeting, or [timeoutMs] passes, or the core exits.
     * Call from the service's worker thread.
     */
    fun awaitReady(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val current = session
            if (current == null) {
                AppLog.i(TAG, "Psiphon: the session ended while waiting for the tunnel")
                return false
            }
            if (current.tunnelled && current.socksSeen && answersSocks(current.noticePort ?: current.port)) return true
            // 3.2.24: the process is assigned by open() on the
            // lifecycle executor, which runs AFTER start() returns —
            // a null process here means "not started yet", not "dead".
            // Only give up once a process exists AND has exited.
            val proc = current.process
            if (proc != null && !proc.isAlive && session === current) {
                val code = runCatching { proc.exitValue() }.getOrNull()
                AppLog.e(TAG, "Psiphon: the core exited before connecting (code $code)")
                return false
            }
            try {
                Thread.sleep(READY_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        AppLog.e(TAG, "Psiphon: timed out waiting for the tunnel")
        return false
    }

    /** A SOCKS5 greeting to [port] on the loopback address: true when it answers. */
    private fun answersSocks(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS.toInt())
                socket.soTimeout = PROBE_TIMEOUT_MS.toInt()
                socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                socket.getOutputStream().flush()
                val reply = ByteArray(2)
                var read = 0
                while (read < 2) {
                    val n = socket.getInputStream().read(reply, read, 2 - read)
                    if (n < 0) return false
                    read += n
                }
                reply[0] == 0x05.toByte() && reply[1] == 0x00.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun open(target: Session, context: Context) {
        if (session !== target) return
        val workDir = workDir(context).apply { mkdirs() }
        val config = PsiphonConfig.build(workDir, target.port, target.region)
        val configFile = File(workDir, CONFIG_FILE)
        try {
            configFile.writeText(config)
        } catch (e: IOException) {
            AppLog.e(TAG, "Psiphon: the config could not be written", e)
            if (release(target)) target.onExit()
            return
        }
        val entries = PsiphonServerList.entriesFile(File(context.filesDir, "assets"), workDir) { problem ->
            AppLog.w(TAG, "psiphon_servers.dat is not a usable Psiphon list; the client fetches the remote list: $problem")
        }
        AppLog.i(TAG, "Psiphon: starting: ${BINARY_NAME} -config ${configFile.absolutePath} -dataRootDirectory ${workDir.absolutePath}" +
            (entries?.let { " -serverList ${it.absolutePath}" } ?: ""))
        reapStale(context)
        val process = try {
            startProcess(context, configFile, workDir, entries)
        } catch (e: IOException) {
            AppLog.e(TAG, "Psiphon: failed to launch the core", e)
            if (release(target)) target.onExit()
            return
        }
        target.process = process
        // Plain log lines on stdout, JSON notices on stderr — the
        // notice stream is the readiness signal, so it stays separate.
        thread(name = "psiphon-core-log", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val text = line.trim()
                    if (text.isNotEmpty()) AppLog.i(TAG, "[psiphon] $text")
                }
            } catch (_: IOException) {
            }
        }
        thread(name = "psiphon-core-notices", isDaemon = true) { watch(target, process) }
    }

    private fun startProcess(
        context: Context,
        configFile: File,
        workDir: File,
        entries: File?,
    ): Process {
        val arguments = ArrayList<String>()
        arguments += binary(context).absolutePath
        arguments += "-config"
        arguments += configFile.absolutePath
        arguments += "-dataRootDirectory"
        arguments += workDir.absolutePath
        if (entries != null) {
            arguments += "-serverList"
            arguments += entries.absolutePath
        }
        val builder = ProcessBuilder(arguments)
            .directory(workDir)
            .redirectErrorStream(false)
        builder.environment().apply {
            put(OWNER_ENV, android.os.Process.myPid().toString())
            put(SESSION_ENV, "1")
            // A Linux-built Go program cannot read Android's CA store
            // on its own — point it at the system cert dirs, as
            // AetherEngine does for the core it spawns.
            androidCertificateDirectories.filter { File(it).isDirectory }
                .takeIf { it.isNotEmpty() }?.joinToString(":")?.let { put("SSL_CERT_DIR", it) }
            put("HOME", workDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
        }
        return builder.start()
    }

    /**
     * The notice stream: JSON lines on stderr, one object per line
     * (`noticeType` + `data`). The tunnel is up once a `Tunnels`
     * notice counts more than zero AND the SOCKS listener has been
     * announced; error notices are kept for the failure detail.
     */
    private fun watch(target: Session, process: Process) {
        try {
            process.errorStream.bufferedReader().forEachLine { line ->
                val text = line.trim()
                if (text.isEmpty()) return@forEachLine
                val notice = runCatching { org.json.JSONObject(text) }.getOrNull()
                if (notice == null) {
                    AppLog.i(TAG, "[psiphon] $text")
                    return@forEachLine
                }
                val kind = notice.optString("noticeType")
                val data = notice.optJSONObject("data")
                when (kind) {
                    "ListeningSocksProxyPort" -> {
                        val port = data?.optInt("port", 0) ?: 0
                        if (port > 0) target.noticePort = port
                        target.socksSeen = true
                        AppLog.i(TAG, "[psiphon] listening SOCKS on $port")
                    }
                    "Tunnels" -> {
                        val count = data?.optLong("count", 0) ?: 0
                        if (count > 0) {
                            if (!target.tunnelled) AppLog.i(TAG, "[psiphon] tunnel is up ($count)")
                            target.tunnelled = true
                        } else if (target.tunnelled) {
                            AppLog.w(TAG, "[psiphon] lost its tunnel; it is reconnecting")
                            target.tunnelled = false
                        }
                    }
                    "ConnectedServer" -> {
                        val address = data?.optString("ipAddress").orEmpty()
                        if (address.isNotEmpty()) AppLog.i(TAG, "[psiphon] reached a server at $address")
                    }
                    "AvailableEgressRegions" -> {
                        val regions = data?.optJSONArray("regions")?.let { array ->
                            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotEmpty() } }
                        }.orEmpty()
                        if (regions.isNotEmpty()) {
                            AppLog.i(TAG, "[psiphon] can leave from: ${regions.joinToString(" ")}")
                            PsiphonServerList.remember(target.context, regions)
                        }
                    }
                    "Warning", "Alert", "Error", "InternalError" -> {
                        val message = data?.optString("message").orEmpty()
                            .takeIf { it.isNotEmpty() } ?: data?.toString().orEmpty()
                        if (message.isNotEmpty()) {
                            if (kind == "Error" || kind == "InternalError") {
                                AppLog.e(TAG, "[psiphon] $message")
                                lastError = message
                            } else {
                                AppLog.w(TAG, "[psiphon] $message")
                            }
                        }
                    }
                    else -> {
                        AppLog.i(TAG, "[psiphon] $text")
                    }
                }
            }
        } catch (_: IOException) {
            AppLog.i(TAG, "Psiphon: notice stream closed")
        }
        val exitCode = process.waitFor()
        AppLog.e(TAG, "Psiphon: the core exited on its own with code $exitCode")
        if (!release(target)) return
        reapOrphans(target.context)
        target.onExit()
    }

    @Synchronized
    private fun release(target: Session): Boolean {
        if (session !== target) return false
        session = null
        return true
    }

    /** Ends [process] and whatever it started: SIGTERM, then force after [EXIT_WAIT_MS]. */
    private fun end(process: Process, context: Context) {
        if (process.isAlive) AppLog.i(TAG, "Psiphon: stopping the live session core")
        try {
            process.destroy()
            if (!process.waitFor(EXIT_WAIT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(EXIT_WAIT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {
        }
        reapOrphans(context)
    }

    /**
     * A core process is stale when its owner is known to be dead.
     * Runs before a start, so a core left behind by a crashed app
     * cannot keep the listen port. A live owner's core is ended by
     * its own session's stop/start instead.
     */
    private fun reapStale(context: Context) {
        val binary = binary(context).absolutePath
        val procDir = File("/proc")
        val entries = procDir.listFiles() ?: return
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid == android.os.Process.myPid()) continue
            val argv = readNulSeparated(File(entry, "cmdline")) ?: continue
            if (argv.firstOrNull() != binary) continue
            val environ = readNulSeparated(File(entry, "environ"))
            val ownerAlive = environ?.firstOrNull { it.startsWith("$OWNER_ENV=") }
                ?.substringAfter('=')?.toIntOrNull()?.let { File(procDir, it.toString()).isDirectory }
            if (ownerAlive == false) {
                runCatching { android.os.Process.killProcess(pid) }
                AppLog.w(TAG, "Psiphon: reaped stale core pid $pid")
            }
        }
    }

    /** Ends cores of this app whose owner is dead (after a session core ends on its own). */
    private fun reapOrphans(context: Context) {
        val binary = binary(context).absolutePath
        val procDir = File("/proc")
        val entries = procDir.listFiles() ?: return
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid == android.os.Process.myPid()) continue
            val argv = readNulSeparated(File(entry, "cmdline")) ?: continue
            if (argv.firstOrNull() != binary) continue
            val environ = readNulSeparated(File(entry, "environ"))
            val ownerAlive = environ?.firstOrNull { it.startsWith("$OWNER_ENV=") }
                ?.substringAfter('=')?.toIntOrNull()?.let { File(procDir, it.toString()).isDirectory }
            val sessionMarked = environ?.any { it.startsWith("$SESSION_ENV=") } == true
            if (ownerAlive == false && sessionMarked) {
                runCatching { android.os.Process.killProcess(pid) }
            }
        }
    }

    private fun readNulSeparated(file: File): List<String>? = try {
        file.readBytes().toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** A session core. [ready] needs no word — the notices are the signal. */
    private class Session(
        val port: Int,
        val region: String?,
        val context: Context,
        val onExit: () -> Unit,
    ) {
        @Volatile
        var process: Process? = null

        /** The tunnel is up (a `Tunnels` notice counted more than zero). */
        @Volatile
        var tunnelled: Boolean = false

        /** The SOCKS listener has been announced. */
        @Volatile
        var socksSeen: Boolean = false

        /** The port the notice says the listener bound (the configured one, normally). */
        @Volatile
        var noticePort: Int? = null
    }
}
