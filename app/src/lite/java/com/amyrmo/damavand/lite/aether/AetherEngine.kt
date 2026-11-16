package com.amyrmo.damavand.lite.aether

import android.content.Context
import com.amyrmo.damavand.lite.AppLog
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Runs the Aether core as a standalone child process, ported from PattNG's
 * AetherCoreManager session path (3.2.10): [start] spawns the binary with
 * the environment PattNG gives it, [watch] streams its output to the log
 * and watches for the ready word / the ECH-key stop / a carrier ending,
 * [awaitListening] waits for the listener the app dials to answer a SOCKS
 * greeting, and [stop] ends the process and whatever it started.
 *
 * Lite adaptations:
 * - One session at a time (a singleton, like the service's single core);
 *   no scans or standalone test cores — those are PattNG's test/scan
 *   features, not the session path. Key renewals DO run here now
 *   (3.2.11): [runOneShot] is the one-shot run a renewal uses.
 * - No AetherIdentityManager: the WARP identity files live at
 *   <filesDir>/aether/{aether,masque,wg}.toml, as Damavand's engine did;
 *   the core creates and reads them itself.
 * - 3.2.11 (PattNG full parity): the Psiphon server list IS shipped
 *   now (psiphon_servers.dat in assets, unpacked by PsiphonServerList
 *   into the work dir), and libpsiphon-tunnel-core.so + liblyrebird.so
 *   are packaged again — so Psiphon modes and Tor bridges work.
 * - The readiness probe is a plain SOCKS5 greeting (PattNG's SocksAnswers
 *   does the same over one connection per port at a time).
 */
object AetherEngine {

    private const val TAG = "AetherEngine"

    const val BINARY_NAME = "libaether.so"
    const val PSIPHON_BINARY_NAME = "libpsiphon-tunnel-core.so"
    const val TRANSPORT_BINARY_NAME = "liblyrebird.so"

    const val OWNER_ENV = "PATTNG_AETHER_OWNER"
    const val SESSION_ENV = "PATTNG_AETHER_SESSION"
    const val EXIT_ENV = "PATTNG_AETHER_EXIT"
    const val PSIPHON_BIN_ENV = "AETHER_PSIPHON_BIN"
    const val TOR_PT_ENV = "AETHER_TOR_PT"
    const val CERT_DIR_ENV = "SSL_CERT_DIR"
    const val PSIPHON_CONFIG_ENV = "AETHER_PSIPHON_CONFIG"
    const val PSIPHON_DIR_ENV = "AETHER_PSIPHON_DIR"

    /** The transports lyrebird speaks, as the core names them. */
    private val torTransports = listOf("obfs4", "snowflake", "webtunnel", "meek_lite", "obfs3", "scramblesuit")

    private val androidCertificateDirectories = listOf(
        "/apex/com.android.conscrypt/cacerts",
        "/system/etc/security/cacerts",
    )

    /** The Psiphon overlay: resolvers of its own, since Android has no resolver config a Linux-built program could read. */
    private const val PSIPHON_OVERLAY_FILE = "psiphon-overlay.json"
    private const val PSIPHON_OVERLAY = """{"DNSResolverAlternateServers": ["1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4"]}"""

    private const val READY_POLL_MS = 500L
    private const val EXIT_WAIT_MS = 2_000L
    private const val PROBE_TIMEOUT_MS = 500L

    private val psiphonReady = Regex("""psiphon is ready""")
    private val noEchKey = Regex("""ECH is on but there is no ECH key to offer""")
    private val errorLabel = Regex("""^(?:io|quic|h3|tls|ech|masque|capsule|api|other|identity refused): """)

    /** The levels at which the core writes its info lines, the ready word among them. */
    private val infoLevels = setOf("info", "debug", "trace")

    private val lifecycle = Executors.newSingleThreadExecutor { task ->
        Thread(task, "aether-core").apply { isDaemon = true }
    }

    /** One session at a time. */
    @Volatile
    private var session: Session? = null

    /** Whether the session core that ended on its own last stopped for want of an ECH key. */
    @Volatile
    private var stoppedForEchKey = false

    /** The core's words that Tor or Psiphon inside the tunnel had ended, when that is why the session ended. */
    @Volatile
    private var stoppedChainEnd: String? = null

    val isRunning: Boolean get() = session != null

    /** What to tell the user of the session core that ended on its own last. */
    fun stoppedMessage(): String? = stoppedChainEnd ?: if (stoppedForEchKey) {
        "ECH is on but there is no ECH key to offer"
    } else {
        null
    }

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun isSupported(context: Context): Boolean = binary(context).canExecute()

    /** Whether this build ships the Psiphon client; without it a profile with Psiphon cannot connect. */
    fun isPsiphonSupported(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, PSIPHON_BINARY_NAME).canExecute()

    /** Whether this build ships the pluggable transport; without it Tor has no bridges where it is blocked. */
    fun isTorTransportsSupported(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME).canExecute()

    /** The work dir: the core's identity files and the Psiphon state live here. */
    fun workDir(context: Context): File = File(context.filesDir, "aether")

    /**
     * Starts [core]'s process. Any running session is stopped first; the
     * work runs on the core's executor, so a start that follows queues
     * behind the stop and comes up only once the old core is gone.
     * [onExit] runs when the process ends on its own (not when [stop]
     * ends it — the caller already knows then).
     */
    fun start(context: Context, core: AetherCore, onExit: () -> Unit) {
        stop()
        val appContext = context.applicationContext
        var logLevel = coreLogLevel()
        // The ready word is an info line; a quieter setting must not leave a Psiphon session waiting for it.
        if (readyNeedsWord(core.arguments) && logLevel !in infoLevels) logLevel = AetherArgs.DEFAULT_LOG_LEVEL
        val arguments = withLogLevel(core.arguments, logLevel)
        val next = Session(
            core.port,
            arguments,
            needsWord = readyNeedsWord(arguments) && showsInfo(arguments),
            context = appContext,
            onExit = onExit,
        )
        session = next
        stoppedForEchKey = false
        stoppedChainEnd = null
        lifecycle.execute { open(next, appContext, arguments, core.exit.key) }
    }

    /** True when the listener the app dials is Psiphon's, inside the tunnel or alone: it answers the greeting before it carries anything, so readiness needs the core's word as well. */
    private fun readyNeedsWord(arguments: List<String>): Boolean = when (AetherArgs.aloneOf(arguments)) {
        "PSIPHON" -> true
        "TOR" -> false
        else -> AetherArgs.psiphonModeOf(arguments) == AetherPsiphon.CHAIN
    }

    /** True when [line] is the core's word that the listener the app dials carries traffic now. */
    private fun isReadyWord(line: String): Boolean = psiphonReady.containsMatchIn(line)

    /** True when [line] is the core's word that it stopped for want of an ECH key. */
    private fun isNoEchKeyWord(line: String): Boolean = noEchKey.containsMatchIn(line)

    /**
     * PattNG: the core's words, as "tor: ..." or "psiphon: ...", when [line] says that Tor or Psiphon inside the
     * tunnel, whose listener a session started with [arguments] is dialled on, has ended or could not start; null
     * for any other line, and for a session dialled on the core's own listener.
     */
    private fun chainEndWords(line: String, arguments: List<String>): String? {
        val header = logHeader(line.trim()) ?: return null
        if (header[1] != "ERROR" || header[2] != "aether") return null
        val carrier = when (AetherArgs.listenerFlagOf(arguments)) {
            AetherArgs.TOR_BIND -> "tor"
            AetherArgs.PSIPHON_BIND -> "psiphon"
            else -> return null
        }
        val message = outputMessage(line)
        val prefix = "[-] $carrier: "
        if (!message.startsWith(prefix)) return null
        return "$carrier: " + message.removePrefix(prefix).replaceFirst(errorLabel, "")
    }

    /** The log header of [line]: its time, level and target, as the core writes them. */
    private fun logHeader(line: String): List<String>? {
        // The core's lines start with "2026-11-16T11:00:00.000Z  INFO aether: ..." — split on whitespace.
        val parts = line.split(' ').filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val level = parts[1]
        if (level !in setOf("ERROR", "WARN", "INFO", "DEBUG", "TRACE")) return null
        return listOf(parts[0], level, parts[2].removeSuffix(":"))
    }

    /** The message of [line], after its header. */
    private fun outputMessage(line: String): String {
        val parts = line.split(' ').filter { it.isNotEmpty() }
        return if (parts.size > 3) parts.drop(3).joinToString(" ") else line
    }

    /** True when a core started with [arguments] writes its info lines, at the level named or at the default. */
    private fun showsInfo(arguments: List<String>): Boolean =
        "--verbose" in arguments || (AetherArgs.valueAfter(arguments, "--log-level") ?: AetherArgs.DEFAULT_LOG_LEVEL) in infoLevels

    /** The core's log level: the app's core log level, mapped onto the levels the core accepts. */
    private fun coreLogLevel(): String = AetherArgs.DEFAULT_LOG_LEVEL

    private fun withLogLevel(arguments: List<String>, logLevel: String): List<String> =
        if (showsInfo(arguments)) arguments else AetherArgs.withoutOption(arguments, "--log-level") + listOf("--log-level", logLevel)

    /**
     * Stops the session core and whatever it started. The work runs on the core's executor, so a
     * start that follows queues behind it and comes up only once the old core and its helpers are
     * gone, with their listeners and Psiphon's datastore lock.
     */
    @Synchronized
    fun stop() {
        val current = session ?: return
        session = null
        lifecycle.execute { current.process?.let { end(it, current.context) } }
    }

    /**
     * Waits until the session's listener answers a SOCKS greeting (and the
     * core has said its ready word, where one is needed), or [timeoutMs]
     * passes, or the core exits. Call from the service's worker thread.
     */
    fun awaitListening(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val current = session ?: return false
            if (current.wordSeen && answersSocks(current.port)) return true
            if (current.process?.isAlive != true && session === current) return false
            try {
                Thread.sleep(READY_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
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

    private fun open(target: Session, context: Context, arguments: List<String>, exitKey: String) {
        if (session !== target) return
        reapStale(context, AetherArgs.listenerAddressOf(arguments))
        val process = try {
            startProcess(context, arguments, exitKey = exitKey)
        } catch (e: IOException) {
            AppLog.e(TAG, "AetherCore: failed to launch the core", e)
            if (release(target)) target.onExit()
            return
        }
        target.process = process
        thread(name = "aether-core-output", isDaemon = true) { watch(target, process) }
    }

    private fun startProcess(
        context: Context,
        arguments: List<String>,
        exitKey: String?,
        identityDir: File? = null,
        session: Boolean = true,
    ): Process {
        val workDir = workDir(context).apply { mkdirs() }
        // A one-shot run (a key renewal) writes its identity files into
        // its own folder, not the folder in use; a session writes them
        // into the work dir.
        val identityDir = (identityDir ?: workDir).apply { mkdirs() }
        // 3.2.11 (PattNG AetherCoreManager.startProcess): the word
        // "shipped-list" after --psiphon-server-entries gives way to the
        // app's unpacked list, or the flag goes when there is no such
        // file. A file of the command's own is left as written.
        val shippedList = if (AetherArgs.PSIPHON_SERVER_ENTRIES in arguments) {
            PsiphonServerList.entriesFile(File(context.filesDir, "assets"), workDir) { problem ->
                AppLog.w(TAG, "psiphon_servers.dat is not a usable Psiphon list; the entries kept from before stay: $problem")
            }
        } else {
            null
        }
        val builder = ProcessBuilder(listOf(binary(context).absolutePath) + withShippedList(arguments, shippedList))
            .directory(workDir)
            .redirectErrorStream(true)
        builder.environment().apply {
            if (session) {
                put(OWNER_ENV, android.os.Process.myPid().toString())
                put(SESSION_ENV, "1")
            }
            exitKey?.let { put(EXIT_ENV, it) }
            File(context.applicationInfo.nativeLibraryDir, PSIPHON_BINARY_NAME)
                .takeIf { it.canExecute() }?.let { put(PSIPHON_BIN_ENV, it.absolutePath) }
            File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME)
                .takeIf { it.canExecute() }?.let { transport ->
                    put(TOR_PT_ENV, torTransports.joinToString(";") { "$it=${transport.absolutePath}" })
                }
            androidCertificateDirectories.filter { File(it).isDirectory }
                .takeIf { it.isNotEmpty() }?.joinToString(":")?.let { put(CERT_DIR_ENV, it) }
            psiphonOverlay(workDir)?.let { put(PSIPHON_CONFIG_ENV, it.absolutePath) }
            put(PSIPHON_DIR_ENV, File(workDir, "psiphon").absolutePath)
            put("HOME", workDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_CONFIG", File(identityDir, "aether.toml").absolutePath)
            put("AETHER_MASQUE_CONFIG", File(identityDir, "masque.toml").absolutePath)
            put("AETHER_WG_CONFIG", File(identityDir, "wg.toml").absolutePath)
        }
        return builder.start()
    }

    /**
     * Runs the core once on [arguments] and ends it: the one-shot runs PattNG uses for scans, tests and key
     * registrations (3.2.11: the WARP keys renewal). Unlike the session path it is not owned by this process
     * and writes its identity files into [identityDir]. Returns the line [match] picks, or null when the core
     * ends or [timeoutMs] passes first; [onOutput] sees every line. The process is ended on every path out.
     * Blocking: call it off the main thread.
     */
    internal fun runOneShot(
        context: Context,
        arguments: List<String>,
        identityDir: File,
        exitKey: String?,
        timeoutMs: Long,
        onOutput: (String) -> Unit,
        match: (String) -> String?,
    ): String? {
        val process = try {
            startProcess(context, arguments, exitKey, identityDir = identityDir, session = false)
        } catch (e: IOException) {
            AppLog.e(TAG, "AetherCore: failed to launch the one-shot core", e)
            return null
        }
        val result = AtomicReference<String?>(null)
        val done = CountDownLatch(1)
        thread(name = "aether-one-shot-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val text = line.trim()
                    if (text.isNotEmpty()) {
                        AppLog.i(TAG, "[aether] $text")
                        onOutput(text)
                    }
                    match(text)?.let { if (result.compareAndSet(null, it)) done.countDown() }
                }
            } catch (_: IOException) {
                // The process ended under the reader; the latch below settles it.
            } finally {
                done.countDown()
            }
        }
        val matched = done.await(timeoutMs, TimeUnit.MILLISECONDS)
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (_: Exception) {
        }
        return if (matched) result.get() else null
    }

    /**
     * [arguments] as the core is started with them: the word [AetherArgs.SHIPPED_LIST]
     * after [AetherArgs.PSIPHON_SERVER_ENTRIES] gives way to [entries], the app's unpacked
     * list, or the flag goes when there is no such file. A file of the command's own is
     * left as written. (PattNG AetherCoreManager.withShippedList.)
     */
    internal fun withShippedList(arguments: List<String>, entries: File?): List<String> {
        val at = arguments.indexOf(AetherArgs.PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != AetherArgs.SHIPPED_LIST) return arguments
        return if (entries == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = entries.absolutePath }
        }
    }

    /** The Psiphon overlay in [workDir], written when it is missing or says something else; null when it cannot be written. */
    private fun psiphonOverlay(workDir: File): File? = try {
        File(workDir, PSIPHON_OVERLAY_FILE).apply { if (!isFile || readText() != PSIPHON_OVERLAY) writeText(PSIPHON_OVERLAY) }
    } catch (e: IOException) {
        AppLog.w(TAG, "AetherCore: the Psiphon overlay could not be written")
        null
    }

    private fun watch(target: Session, process: Process) {
        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                val text = line.trim()
                if (text.isNotEmpty()) AppLog.i(TAG, "[aether] $text")
                if (!target.wordSeen && isReadyWord(line)) target.wordSeen = true
                if (isNoEchKeyWord(line)) target.noEchKey = true
                // A core the app stopped, or one a new start replaced, is no longer the session's to end.
                if (target.chainEnd == null && session === target) {
                    chainEndWords(line, target.arguments)?.let { words ->
                        target.chainEnd = words
                        // The core ends on this line itself; ending it here as well does not wait for that, and its
                        // exit stops the session as an exit on its own does.
                        AppLog.w(TAG, "AetherCore: ending the session core, Tor or Psiphon inside the tunnel has ended")
                        process.destroy()
                    }
                }
            }
        } catch (e: IOException) {
            AppLog.i(TAG, "AetherCore: output closed: ${e.message}")
        }
        val exitCode = process.waitFor()
        if (!release(target)) return
        reapOrphans(target.context)
        if (target.chainEnd != null) {
            AppLog.e(TAG, "AetherCore: the core was ended with code $exitCode, Tor or Psiphon inside the tunnel had ended")
        } else {
            AppLog.e(TAG, "AetherCore: the core exited on its own with code $exitCode")
        }
        target.onExit()
    }

    @Synchronized
    private fun release(target: Session): Boolean {
        if (session !== target) return false
        // Set before the session goes and the exit is reported, which reads them: the warm-up wait reports the exit
        // too, as soon as it sees no session. The core has written its last line by now.
        stoppedForEchKey = target.noEchKey
        stoppedChainEnd = target.chainEnd
        session = null
        return true
    }

    /** Ends [process] and whatever it started: SIGTERM, then force after [EXIT_WAIT_MS]. */
    private fun end(process: Process, context: Context) {
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
     * A core process is stale when its owner is known to be dead or it holds the address we are about to bind.
     * Runs before a start, so a core left behind by a crashed app cannot keep the listen port.
     */
    private fun reapStale(context: Context, bindAddress: String?) {
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
            val stale = ownerAlive == false ||
                (bindAddress != null && AetherArgs.listenerAddressOf(argv) == bindAddress)
            if (stale) {
                runCatching { android.os.Process.killProcess(pid) }
                AppLog.w(TAG, "AetherCore: reaped stale core pid $pid")
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

    /**
     * A session core started with [arguments]. [wordSeen] starts true where no word is needed, so the listener alone
     * decides there.
     */
    private class Session(
        val port: Int,
        val arguments: List<String>,
        needsWord: Boolean,
        val context: Context,
        val onExit: () -> Unit,
    ) {
        var process: Process? = null

        @Volatile
        var wordSeen: Boolean = !needsWord

        /** Whether the core said it stopped for want of an ECH key. */
        @Volatile
        var noEchKey: Boolean = false

        /** The core's words that Tor or Psiphon inside the tunnel had ended, for which the app ended it. */
        @Volatile
        var chainEnd: String? = null
    }
}
