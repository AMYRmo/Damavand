package com.amyrmo.damavand.core.adapters

import android.content.Context
import android.os.Build
import com.amyrmo.damavand.core.SocksAnswers
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.logic.AetherCommands
import com.amyrmo.damavand.logic.AetherFlags
import com.amyrmo.damavand.model.AetherKeyKind
import com.amyrmo.damavand.model.AetherLink
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherProtocol
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.AetherTor
import com.amyrmo.damavand.model.CoreKind
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * The Aether core (patterniha/Aether, Rust) as a child process exposing a local
 * SOCKS5 listener. The VPN layer bridges that listener into the TUN via a
 * sing-box socks outbound, so Aether behaves like any other engine.
 *
 * Ported from v1's hardened engine (branch fix/bugs-battery) with the v2
 * substitutions:
 *  - all process spawning goes through [ProcessSupervisor] (architecture
 *    §2.4 rule 1): the supervisor's line/exit callbacks replace the v1
 *    watcher thread, and its destroy → 2 s → destroyForcibly ladder
 *    replaces v1's `endProcess` for the main process (deleted — one ladder
 *    only). The supervisor also stamps the owner env itself, so
 *    [coreEnvironment] no longer puts [OWNER_ENV] (v1 put it there); the
 *    constant stays because [reapStale]/[coreProcesses] still recognise
 *    leftover cores by reading that stamp out of /proc;
 *  - the plan's argv is authoritative: [AetherAdapter] resolves
 *    `HopConfig.AetherRuntime.argv` (plus the start-time upstream splice)
 *    and hands it in via [preparedArgv]; only when that is null does the
 *    engine derive the arguments from the profile exactly as v1 did
 *    (`AetherFlags.toFlags` / `AetherCommands.customArguments`);
 *  - the bind port comes from the plan ([initialPort]); the v1 random port
 *    survives only as the fallback for a caller that passes no valid port
 *    (the keys-management throwaway engines). A custom command still
 *    adopts the listener port it names, as in v1.
 *
 * Process plumbing otherwise follows PattNG's AetherCoreManager: a work
 * directory holding the WARP identity (aether.toml and friends) is pointed
 * at through the AETHER_* environment variables, the Psiphon tunnel core is
 * handed over with --psiphon-bin, and the arguments come from the profile's
 * PattNG flag set.
 */
class AetherEngine(
    private val context: Context,
    private val supervisor: ProcessSupervisor,
    val config: AetherProfile,
    initialPort: Int,
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    @Volatile private var handle: ProcessSupervisor.Handle? = null
    private val active = AtomicBoolean(false)

    /** Set by [stop]; the supervisor's exit callback reports a death only when this is false. */
    @Volatile private var stopRequested = false

    /** Whether the core has said its Psiphon word yet; set by the output callback. */
    @Volatile private var psiphonReadySeen = false

    /** Asks the loopback SOCKS listener whether it answers, over one connection at a time. */
    private val socksAnswers = SocksAnswers(PROBE_TIMEOUT_MS)

    /**
     * The plan-resolved session arguments (see the class doc), set by
     * [AetherAdapter] before [start]. Null = derive from [config] as v1 did.
     */
    @Volatile var preparedArgv: List<String>? = null

    /**
     * True when the listener the bridge dials needs the core's word as well
     * as its greeting's answer: the listener is Psiphon's, alone or inside
     * the tunnel, and Psiphon's own listener answers the greeting before it
     * has a tunnel. PattNG: AetherCoreManager.readyNeedsWord, read off the
     * profile instead of the arguments.
     */
    @Volatile private var needsReadyWord: Boolean = when {
        AetherTor.fromString(config.tor) == AetherTor.ONLY -> false
        AetherPsiphon.fromString(config.psiphon) == AetherPsiphon.ONLY -> true
        else -> AetherPsiphon.fromString(config.psiphon) == AetherPsiphon.CHAIN
    }

    /**
     * The loopback port the bridge dials. [initialPort] is the plan's bind
     * port; the random fallback exists only for port-less callers. A custom
     * command may name its own listener, and [start] then adopts the port
     * it names (Narcic's listenerPortOf model).
     */
    @Volatile var socksPort: Int =
        if (initialPort in 1..65535) initialPort else 18190 + Random.nextInt(1, 2000)
        private set

    val isRunning: Boolean get() = active.get() && handle?.isAlive == true

    fun start() {
        check(active.compareAndSet(false, true)) { "aether already running" }
        stopRequested = false
        try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
            val psi = File(context.applicationInfo.nativeLibraryDir, "libpsiphon-tunnel-core.so")
            if (!bin.exists()) {
                onStopped("aether core binary missing")
                active.set(false)
                return
            }

            val workDir = File(context.filesDir, WORK_DIR).apply { mkdirs() }
            // A renewal stopped while it moved its new keys into place is finished before a core reads them.
            settle()
            val custom = AetherCommands.isCustom(config)
            val fromPlan = preparedArgv != null
            var arguments = preparedArgv ?: if (custom) {
                // The profile's command runs as written; the engine adds no
                // --bind or --log-level of its own (they are repaired inside
                // customArguments), and adopts the listener port it names.
                AetherCommands.customArguments(config, socksPort)
            } else {
                AetherFlags.toFlags(config)
            }
            arguments = normalizeShippedWord(arguments)
            if (custom || fromPlan) {
                AetherCommands.listenerPortOf(arguments)?.let { socksPort = it }
                needsReadyWord = AetherCommands.readyNeedsWord(arguments)
            }
            reapStale(bin, "127.0.0.1:$socksPort")
            val cmd = mutableListOf(bin.absolutePath)
            if (custom || fromPlan) {
                // A custom command carries its own --bind/--log-level
                // (repaired inside customArguments), and the plan's argv
                // already begins with --bind and ends with --log-level:
                // neither is added again.
                cmd += withShippedList(arguments, shippedEntries(arguments, workDir))
            } else {
                cmd += listOf("--bind", "127.0.0.1:$socksPort")
                cmd += withShippedList(arguments, shippedEntries(arguments, workDir))
                // Sessions run at info, as in PattNG/Narcic: the ready word
                // a Psiphon session waits on is an info line, and at warn a
                // scanning session is silent — no hunt progress, no chosen
                // endpoint, nothing to diagnose when it never comes up.
                cmd += listOf("--log-level", "info")
            }
            if (psi.exists() && "--psiphon-bin" !in cmd) cmd += listOf("--psiphon-bin", psi.absolutePath)

            handle = supervisor.spawn(
                ProcessSupervisor.Spec(
                    kind = CoreKind.AETHER,
                    argv = cmd,
                    workDir = workDir,
                    extraEnv = coreEnvironment(workDir, File(workDir, MASQUE_FILE), File(workDir, WG_FILE), psiphonStateDir(workDir)),
                ),
                onLine = { line ->
                    if (!psiphonReadySeen && PSIPHON_READY.containsMatchIn(line)) psiphonReadySeen = true
                    onLog("[aether] $line")
                },
                onExit = { code ->
                    // The supervisor reports deliberate stops here too; only
                    // an unrequested exit is a death (v1 reported only
                    // code != 0 — a core that ends cleanly on its own is
                    // just as dead to the session, so every unrequested
                    // exit is reported now).
                    if (!stopRequested && active.getAndSet(false)) {
                        onStopped("aether core exited ($code)")
                    } else {
                        active.set(false)
                    }
                },
            )
            onLog("aether core started (socks=127.0.0.1:$socksPort)")
            onLog(
                if (custom || fromPlan) (listOf("aether") + cmd.drop(1)).joinToString(" ")
                else AetherFlags.commandPreview(config, socksPort)
            )
        } catch (e: Exception) {
            active.set(false)
            onStopped("aether start failed: ${e.message}")
        }
    }

    /**
     * Runs the core once with `--register <kind>` to fetch fresh WARP keys,
     * PattNG's renewal flow (AetherIdentityManager.renew): the run writes
     * into the staging folder [RENEWAL_DIR], never over the keys in use —
     * the core keeps an identity file that is already there, so new keys
     * need new paths — and only once the core has said the identities are
     * ready, and every file it was asked for is there and reads as a key,
     * are they marked ready and settled over the keys in use. A failure, a
     * timeout or the app being killed leaves the keys in use untouched.
     * [onDone] runs on the registration thread when the process ends.
     */
    fun registerKeys(kind: String, onDone: (Boolean, String?) -> Unit) {
        val keyKind = AetherKeyKind.ofRegister(kind)
        if (keyKind == null) {
            onDone(false, "unknown register kind: $kind")
            return
        }
        val bin = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
        if (!bin.exists()) {
            onDone(false, "aether core binary missing")
            return
        }
        val workDir = File(context.filesDir, WORK_DIR).apply { mkdirs() }
        val renewalDir = File(context.filesDir, RENEWAL_DIR)
        val files = keyFilesOf(keyKind)
        Thread({
            var ready = false
            var error: String? = null
            val timedOut = AtomicBoolean(false)
            val sawReady = AtomicBoolean(false)
            try {
                // A renewal whose keys were all ready is finished first; what one stopped earlier left is no key anyone uses.
                settle()
                if (renewalDir.exists() && !renewalDir.deleteRecursively()) {
                    throw IOException("could not clear the key staging folder")
                }
                if (!renewalDir.mkdirs() && !renewalDir.isDirectory) {
                    throw IOException("could not create the key staging folder")
                }
                val cmd = listOf(bin.absolutePath, "--register", keyKind.register, "--log-level", "info")
                val exitLatch = CountDownLatch(1)
                val exitCode = AtomicInteger(-1)
                val regHandle = AtomicReference<ProcessSupervisor.Handle?>(null)
                val spawned = supervisor.spawn(
                    ProcessSupervisor.Spec(
                        kind = CoreKind.AETHER,
                        argv = cmd,
                        workDir = workDir,
                        extraEnv = coreEnvironment(workDir, File(renewalDir, MASQUE_FILE), File(renewalDir, WG_FILE), File(context.filesDir, PSIPHON_PROBE_DIR)),
                    ),
                    onLine = { line ->
                        onLog("[aether-keys] $line")
                        if (!sawReady.get() && KEYS_REGISTERED.containsMatchIn(line)) {
                            sawReady.set(true)
                            // The run is over at its word that the keys are saved; give it a moment to end on its own.
                            Thread({
                                val h = regHandle.get()
                                if (h != null && !awaitUntil(REGISTER_EXIT_GRACE_MS, EXIT_POLL_MS) { !h.isAlive }) {
                                    h.destroy()
                                }
                            }, "aether-register-grace").apply { isDaemon = true; start() }
                        }
                    },
                    onExit = { code ->
                        exitCode.set(code)
                        exitLatch.countDown()
                    },
                )
                regHandle.set(spawned)
                // A registration that wedges must not wedge the connect flow with it.
                Thread({
                    Thread.sleep(RENEW_TIMEOUT_MS)
                    if (spawned.isAlive) {
                        timedOut.set(true)
                        spawned.destroyForcibly()
                    }
                }, "aether-register-watchdog").apply { isDaemon = true; start() }
                exitLatch.await()
                val code = exitCode.get()
                if (sawReady.get()) {
                    // A key the run left beside the ones asked for goes with the folder rather than into place.
                    (KEY_FILES - files.toSet()).forEach { File(renewalDir, it).delete() }
                    ready = isComplete(renewalDir, files) && markReady(renewalDir)
                    if (!ready) error = "the registered keys are incomplete"
                } else {
                    error = when {
                        timedOut.get() -> "register timed out"
                        code != 0 -> "register exited ($code)"
                        else -> "the core did not confirm the keys"
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
            // A cancellation or failure keeps the keys in use as they were; a finished renewal settles.
            if (ready) settle() else renewalDir.deleteRecursively()
            onDone(ready, error)
        }, "aether-register").apply { isDaemon = true; start() }
    }

    /** The registration kind this engine's profile renews: the one of its protocol. */
    fun registrationKind(): AetherKeyKind = when (AetherProtocol.fromString(config.protocol)) {
        AetherProtocol.MASQUE -> AetherKeyKind.MASQUE
        AetherProtocol.WIREGUARD -> AetherKeyKind.WIREGUARD
        AetherProtocol.GOOL -> AetherKeyKind.GOOL
        AetherProtocol.MIM -> AetherKeyKind.MIM
        AetherProtocol.WG_OVER_MASQUE -> AetherKeyKind.WG_OVER_MASQUE
    }

    /**
     * Which of the key files this engine's profile needs the identity folder
     * lacks: not there, or not readable as a key. None when the profile runs
     * Psiphon or Tor alone, with no WARP tunnel. PattNG:
     * AetherIdentityManager.missing over filesNeededBy, with Damavand's file
     * names (the core derives the inner hops' files from the primaries':
     * wireguard-secondary.toml, masque-secondary.toml, masque-gool.toml).
     */
    fun missingKeyFiles(): List<String> {
        settle()
        if (AetherPsiphon.fromString(config.psiphon) == AetherPsiphon.ONLY ||
            AetherTor.fromString(config.tor) == AetherTor.ONLY
        ) return emptyList()
        val workDir = File(context.filesDir, WORK_DIR)
        return keyFilesOf(registrationKind()).filter { !readIdentity(File(workDir, it)) }
    }

    /** The key files a registration of [kind] writes, in the order of [KEY_FILES]. */
    private fun keyFilesOf(kind: AetherKeyKind): List<String> = when (kind) {
        AetherKeyKind.ALL -> KEY_FILES
        AetherKeyKind.WIREGUARD -> listOf(WG_FILE)
        AetherKeyKind.MASQUE -> listOf(MASQUE_FILE)
        AetherKeyKind.GOOL -> listOf(WG_FILE, WG_SECONDARY_FILE)
        AetherKeyKind.MIM -> listOf(MASQUE_FILE, MASQUE_SECONDARY_FILE)
        AetherKeyKind.WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
    }

    /** [settle] on this app's identity and staging folders; whatever reads the keys calls it first. */
    private fun settle(): Boolean = settle(File(context.filesDir, WORK_DIR), File(context.filesDir, RENEWAL_DIR))

    /**
     * Moves the new keys of a renewal whose keys were all ready over the
     * keys in [workDir], one after another, and then removes [renewalDir].
     * Without the ready mark there is nothing to move: the new keys are
     * incomplete, or a renewal is still gathering them, and the keys in use
     * stay. Another process may be finishing the same renewal at the same
     * time; a key it moved first is simply gone here. True when no renewal
     * with ready keys waits any longer.
     */
    private fun settle(workDir: File, renewalDir: File): Boolean {
        if (!File(renewalDir, READY_MARK).isFile) return true
        workDir.mkdirs()
        for (name in KEY_FILES) {
            val renewed = File(renewalDir, name)
            if (renewed.isFile && !moveOver(renewed, File(workDir, name))) return false
        }
        // The mark goes with the folder, after every key: until then it tells that keys are left to move.
        return renewalDir.deleteRecursively()
    }

    /**
     * Moves [source] over [target] in one step that replaces the key in use,
     * or leaves it as it was; true once [source] is gone, moved by this call
     * or by another process finishing the same renewal.
     */
    private fun moveOver(source: File, target: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return true
            } catch (_: IOException) {
                return !source.isFile
            }
        }
        // Before API 26 there is no Files.move; renameTo is the same rename(2)
        // underneath, and staging and identity folders are siblings, so it
        // stays within one filesystem and replaces atomically there too.
        // (v2's minSdk is 26, so this branch is unreachable; kept from v1
        // because the rename fallback costs nothing.)
        return source.renameTo(target) || !source.isFile
    }

    private fun markReady(dir: File): Boolean = try {
        File(dir, READY_MARK).createNewFile()
    } catch (_: IOException) {
        false
    }

    /** Whether every one of [files] is in [dir] and reads as a key. */
    private fun isComplete(dir: File, files: List<String>): Boolean = files.all { readIdentity(File(dir, it)) }

    /** Whether [file] holds an identity: it is there and reads as one. */
    private fun readIdentity(file: File): Boolean = try {
        file.isFile && parseIdentity(file.readText())
    } catch (_: IOException) {
        false
    }

    /**
     * Whether [text] reads as an identity file: its device_id is there and
     * not blank. PattNG's AetherIdentityManager.parse, reduced to the
     * presence check the missing-keys test needs.
     */
    private fun parseIdentity(text: String): Boolean {
        val fields = mutableMapOf<String, String>()
        var insideMultiline = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!insideMultiline) {
                identityField.matchEntire(line)?.destructured?.let { (key, value) -> fields.putIfAbsent(key, value) }
            }
            if (multilineDelimiter.findAll(line).count() % 2 == 1) {
                insideMultiline = !insideMultiline
            }
        }
        return !fields["device_id"].isNullOrBlank()
    }

    /**
     * PattNG-style commands name the bundled Psiphon list "shipped"; Damavand's
     * word is [AetherLink.SHIPPED_LIST]. Normalized before [withShippedList]
     * so a pasted PattNG command gets the app's unpacked list, not a file
     * literally called "shipped".
     */
    private fun normalizeShippedWord(arguments: List<String>): List<String> {
        val at = arguments.indexOf(AetherLink.PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != "shipped") return arguments
        return arguments.toMutableList().also { it[at + 1] = AetherLink.SHIPPED_LIST }
    }

    /**
     * [arguments] as the core is started with them: the word
     * [AetherLink.SHIPPED_LIST] after [AetherLink.PSIPHON_SERVER_ENTRIES]
     * gives way to [entries], the app's unpacked list, or the flag goes when
     * there is no such file. A file of the command's own is left as written.
     * PattNG: AetherCoreManager.withShippedList.
     */
    private fun withShippedList(arguments: List<String>, entries: File?): List<String> {
        val at = arguments.indexOf(AetherLink.PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != AetherLink.SHIPPED_LIST) return arguments
        return if (entries == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = entries.absolutePath }
        }
    }

    /**
     * The bundled Psiphon server list, unpacked into [workDir] beside the
     * identity files, when [arguments] ask for it; null when they do not, or
     * when the shipped list cannot be used and no earlier unpacking is kept.
     */
    private fun shippedEntries(arguments: List<String>, workDir: File): File? {
        if (AetherLink.PSIPHON_SERVER_ENTRIES !in arguments) return null
        val packed = runCatching {
            context.assets.open(PsiphonServerList.PSIPHON_SERVERS_DAT).use { it.readBytes() }
        }.getOrNull() ?: return null
        return PsiphonServerList.entriesFile(packed, workDir) { problem ->
            onLog("aether: ${PsiphonServerList.PSIPHON_SERVERS_DAT} is not a usable Psiphon list; the entries kept from before stay (${problem.message})")
        }
    }

    /**
     * The environment every core run starts with, PattNG's
     * AetherCoreManager.startProcess: the identity layout (the base file in
     * [workDir], the MASQUE and WireGuard keys at [masqueConfig] /
     * [wgConfig], which a registration points into its staging folder), the
     * Psiphon client and the state directory it keeps, and the roots the Go
     * programs verify certificates against.
     *
     * Deviation from v1: v1 also put [OWNER_ENV] here. In v2 the
     * [ProcessSupervisor] stamps `DAMAVAND_AETHER_OWNER=<myPid>` itself on
     * every spawn, so the stamp is not repeated (the supervisor's value and
     * this constant name the same variable).
     */
    private fun coreEnvironment(workDir: File, masqueConfig: File, wgConfig: File, psiphonDir: File): Map<String, String> =
        buildMap {
            put("HOME", workDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_CONFIG", File(workDir, BASE_FILE).absolutePath)
            put("AETHER_MASQUE_CONFIG", masqueConfig.absolutePath)
            put("AETHER_WG_CONFIG", wgConfig.absolutePath)
            File(context.applicationInfo.nativeLibraryDir, "libpsiphon-tunnel-core.so")
                .takeIf { it.exists() }
                ?.let { put("AETHER_PSIPHON_BIN", it.absolutePath) }
            File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME)
                .takeIf { it.canExecute() }
                ?.let { transport ->
                    put("AETHER_TOR_PT", TOR_TRANSPORTS.joinToString(";") { name -> "$name=${transport.absolutePath}" })
                }
            certificateDirectories()?.let { put("SSL_CERT_DIR", it) }
            psiphonOverlay(workDir)?.let { put("AETHER_PSIPHON_CONFIG", it.absolutePath) }
            put("AETHER_PSIPHON_DIR", psiphonDir.absolutePath)
        }

    /** The certificate directories of this device that exist, joined the way Go reads them; null when there is none. */
    private fun certificateDirectories(): String? =
        CERTIFICATE_DIRECTORIES.filter { File(it).isDirectory }.takeIf { it.isNotEmpty() }?.joinToString(":")

    /**
     * Where the Psiphon client keeps its datastore: the servers it was given,
     * fetched and discovered. That state is no part of the WARP identity, so
     * it lives beside the identity directory rather than inside it, which a
     * renewal of the identity replaces. Left to itself the core derives the
     * place from the identity file, inside that directory; a datastore still
     * there moves out once. PattNG: AetherCoreManager.psiphonStateDir.
     */
    private fun psiphonStateDir(workDir: File): File {
        val dir = File(context.filesDir, PSIPHON_STATE_DIR)
        val inside = File(workDir, "$BASE_FILE-psiphon")
        if (!dir.exists() && inside.isDirectory && !inside.renameTo(dir)) {
            onLog("aether: the Psiphon datastore could not leave the identity directory; the client starts over")
        }
        return dir
    }

    /** The Psiphon overlay in [workDir], written when it is missing or says something else; null when it cannot be written. */
    private fun psiphonOverlay(workDir: File): File? = try {
        File(workDir, PSIPHON_OVERLAY_FILE).apply { if (!isFile || readText() != PSIPHON_OVERLAY) writeText(PSIPHON_OVERLAY) }
    } catch (e: IOException) {
        onLog("aether: the Psiphon overlay could not be written: ${e.message}")
        null
    }

    /**
     * Waits until the core actually serves the tunnel: its SOCKS listener
     * answers the greeting and, where [needsReadyWord], the core has said
     * Psiphon is ready. False when the core ends first or [timeoutMs] runs
     * out. Blocks the calling thread; PattNG's awaitReady / awaitListening,
     * on threads instead of coroutines.
     */
    fun awaitReady(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (isRunning) {
            if ((!needsReadyWord || psiphonReadySeen) && socksAnswers.answers(socksPort)) return true
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(READY_POLL_MS)
        }
        return false
    }

    /**
     * Waits until [done] holds, looking every [pollMs], for at most
     * [timeoutMs]; true when it held in time. A bounded wait, not a watch:
     * it ends with the condition or the deadline.
     */
    private fun awaitUntil(timeoutMs: Long, pollMs: Long, done: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!done()) {
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(pollMs)
        }
        return true
    }

    /**
     * Ends the core and whatever it started. The main process ends through
     * the supervisor's stop ladder (destroy → 2 s → destroyForcibly); the
     * core has no signal handling, so the term that ends it takes nothing
     * with it: Psiphon's client and the pluggable transport would live on,
     * holding their listeners and, Psiphon, its datastore lock, until they
     * noticed their pipes gone, and a core started meanwhile would fail on
     * them — [reapOrphans] ends those helpers, as in v1.
     */
    fun stop() {
        stopRequested = true
        active.set(false)
        val h = handle
        handle = null
        h?.stop()
        reapOrphans()
    }

    /**
     * Kills leftover core processes of this app: any whose owning app
     * process is gone (see [OWNER_ENV]) and any still holding the listener
     * address this start is about to bind. Android can kill the app without
     * its child processes following, and a survivor would otherwise hold
     * the identity directory — and keep scanning and reconnecting — against
     * the next core. Then ends the helper processes the killed cores left.
     *
     * The supervisor's spawn reaps owner-stale cores on its own; this
     * pass's remaining job is the bind-address holder, and it must run
     * BEFORE the spawn with the final bind address, as in v1.
     */
    private fun reapStale(binary: File, bindAddress: String) {
        val killed = mutableSetOf<Int>()
        for (core in coreProcesses(binary)) {
            if (!isStale(core, bindAddress)) continue
            onLog("aether: killing a leftover core process, pid=${core.pid} bind=${valueAfter(core.argv, "--bind")} ownerAlive=${core.ownerAlive}")
            android.os.Process.killProcess(core.pid)
            killed += core.pid
        }
        if (killed.isNotEmpty()) {
            awaitUntil(EXIT_WAIT_MS, EXIT_POLL_MS) { killed.none { File(PROC_DIR, it.toString()).isDirectory } }
        }
        reapOrphans(killed)
    }

    /** A core process is stale when its owner is known to be dead or it holds the address we are about to bind. */
    private fun isStale(core: CoreProcess, bindAddress: String?): Boolean =
        core.ownerAlive == false || (bindAddress != null && valueAfter(core.argv, "--bind") == bindAddress)

    /**
     * Ends the helper processes of the app, Psiphon's client and the
     * pluggable transport, that no living core has as a parent, [gone]
     * cores not counting as living: a term first, for Psiphon to close its
     * tunnel and datastore, and a kill for one that has not gone in time.
     */
    private fun reapOrphans(gone: Set<Int> = emptySet()) {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
        val live = coreProcesses(binary).map { it.pid }.toSet() - gone
        val orphans = orphanedHelpers(helperProcesses(), live)
        if (orphans.isEmpty()) return
        onLog("aether: ending ${orphans.size} helper process(es) left behind by a core, pids=$orphans")
        orphans.forEach { android.os.Process.sendSignal(it, SIGTERM) }
        awaitUntil(EXIT_WAIT_MS, EXIT_POLL_MS) { orphans.none { File(PROC_DIR, it.toString()).isDirectory } }
        orphans.filter { File(PROC_DIR, it.toString()).isDirectory }
            .forEach { android.os.Process.killProcess(it) }
    }

    /** The pids among [helpers], each with its parent pid, whose parent is none of [liveCores]; a parent that could not be read counts as none. */
    private fun orphanedHelpers(helpers: List<Pair<Int, Int?>>, liveCores: Set<Int>): List<Int> =
        helpers.filter { (_, parent) -> parent == null || parent !in liveCores }.map { it.first }

    /** The helper processes of the app found in /proc, each with its parent pid when it could be read. */
    private fun helperProcesses(): List<Pair<Int, Int?>> {
        val helpers = setOf(
            File(context.applicationInfo.nativeLibraryDir, "libpsiphon-tunnel-core.so").absolutePath,
            File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME).absolutePath,
        )
        val entries = PROC_DIR.listFiles() ?: return emptyList()
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            val argv = readNulSeparated(File(entry, "cmdline")) ?: return@mapNotNull null
            if (argv.firstOrNull() !in helpers) return@mapNotNull null
            pid to runCatching { File(entry, "stat").readText() }.getOrNull()?.let(::parentPidOf)
        }
    }

    /** The parent pid in [stat], the text of /proc/<pid>/stat: the second field after the command name in parentheses. */
    private fun parentPidOf(stat: String): Int? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        return stat.substring(close + 1).trim().split(' ').getOrNull(1)?.toIntOrNull()
    }

    /**
     * A core process of this app found in /proc; [CoreProcess.ownerAlive] is
     * null when its owner could not be read.
     */
    private fun coreProcesses(binary: File): List<CoreProcess> {
        val path = binary.absolutePath
        val entries = PROC_DIR.listFiles() ?: return emptyList()
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            val argv = readNulSeparated(File(entry, "cmdline")) ?: return@mapNotNull null
            if (argv.firstOrNull() != path) return@mapNotNull null
            val environ = readNulSeparated(File(entry, "environ"))
            val ownerAlive = ownerPid(environ)?.let { File(PROC_DIR, it.toString()).isDirectory }
            CoreProcess(pid, argv, ownerAlive)
        }
    }

    private fun ownerPid(environ: List<String>?): Int? =
        environ?.firstOrNull { it.startsWith("$OWNER_ENV=") }?.substringAfter('=')?.toIntOrNull()

    private fun readNulSeparated(file: File): List<String>? = try {
        nulSeparated(file.readBytes().toString(Charsets.UTF_8))
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /**
     * The words of [text], a command line or an environment as /proc gives
     * them, each ended by a NUL: an empty word stays, as an empty argument
     * does in the command, which the word after a flag may be.
     */
    private fun nulSeparated(text: String): List<String> =
        text.split('\u0000').let { words -> if (words.last().isEmpty()) words.dropLast(1) else words }

    /** The word after [flag] in [argv], or null when the flag names none. */
    private fun valueAfter(argv: List<String>, flag: String): String? =
        argv.indexOf(flag).takeIf { it >= 0 }?.let { argv.getOrNull(it + 1) }

    /** A core process found in /proc: its pid, its command line, and whether the app process that spawned it lives. */
    private class CoreProcess(val pid: Int, val argv: List<String>, val ownerAlive: Boolean?)

    companion object {
        /**
         * Environment variable naming the app process that spawned a core
         * process. Rust ignores SIGPIPE and the core has no parent-death
         * handling, so a core whose owner was killed keeps running until
         * something else kills it; [reapStale] recognises such orphans by
         * this value. PattNG: PATTNG_AETHER_OWNER, under Damavand's name.
         * In v2 the [ProcessSupervisor] stamps it on every spawn
         * (`ProcessSupervisor.ownerEnvName(CoreKind.AETHER)` — the same
         * name); the engine only reads it back from /proc.
         */
        const val OWNER_ENV = "DAMAVAND_AETHER_OWNER"

        const val WORK_DIR = "aether"
        const val BASE_FILE = "aether.toml"
        const val MASQUE_FILE = "masque.toml"
        const val WG_FILE = "wireguard.toml"

        /** The inner WireGuard hop's key, named by the core beside [WG_FILE]: <config>-secondary.toml. */
        private const val WG_SECONDARY_FILE = "wireguard-secondary.toml"

        /** The inner MASQUE hop's key, named by the core beside [MASQUE_FILE]: <config>-secondary.toml. */
        private const val MASQUE_SECONDARY_FILE = "masque-secondary.toml"

        /** The WireGuard key WireGuard over MASQUE carries, named by the core beside [MASQUE_FILE]: <config>-gool.toml. */
        private const val MASQUE_GOOL_FILE = "masque-gool.toml"

        /** The key files of every kind, in the order the core registers them. */
        private val KEY_FILES = listOf(WG_FILE, WG_SECONDARY_FILE, MASQUE_FILE, MASQUE_SECONDARY_FILE, MASQUE_GOOL_FILE)

        /**
         * Where a registration gathers its new keys. The keys in use stay in
         * [WORK_DIR], untouched, until every new key it registers is here;
         * only then does each new key take the place of the old one.
         */
        const val RENEWAL_DIR = "aether-renewal"

        /**
         * Written into [RENEWAL_DIR] once every new key of a registration is
         * there. From then on the new keys are the keys: whatever stops the
         * moving of them, [settle] finishes it.
         */
        const val READY_MARK = "ready"

        /** A registration's time budget; PattNG's RENEW_TIMEOUT_MS for a direct renewal. */
        private const val RENEW_TIMEOUT_MS = 4 * 60_000L

        /** How long a registration run that has said its word is given to end on its own before it is ended. */
        private const val REGISTER_EXIT_GRACE_MS = 5_000L

        /** The core's last word in a registration, once every key it was asked for is saved. */
        private val KEYS_REGISTERED = Regex("""identities ready: \S""")

        private val identityField = Regex("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)"$""")
        private val multilineDelimiter = Regex("\"\"\"|'''")

        /** How long a stop waits for a core, and then for the programs it started, to be gone before force is used. */
        private const val EXIT_WAIT_MS = 2_000L
        private const val EXIT_POLL_MS = 50L
        private const val SIGTERM = 15

        private const val PROBE_TIMEOUT_MS = 1000
        private const val READY_POLL_MS = 500L

        private val PROC_DIR = File("/proc")

        /**
         * The core's word that Psiphon has a tunnel: "psiphon is ready". The
         * app waits for it as well as for the greeting's answer where it
         * dials Psiphon: Psiphon's own listener answers the greeting before
         * it has a tunnel.
         */
        private val PSIPHON_READY = Regex("""psiphon is ready""")

        /**
         * The pluggable transport Tor's bridges run through, shipped beside
         * the core as a library. It is lyrebird, which speaks every transport
         * the core asks bridges for; the core is told so by name, since it
         * recognises the program by a file name a library cannot have.
         */
        private const val TRANSPORT_BINARY_NAME = "liblyrebird.so"

        /** The transports lyrebird speaks, as the core names them: the list the core itself assumes for a lyrebird it finds by name. */
        private val TOR_TRANSPORTS = listOf("obfs4", "snowflake", "webtunnel", "meek_lite", "obfs3", "scramblesuit")

        private const val PSIPHON_STATE_DIR = "psiphon"
        private const val PSIPHON_PROBE_DIR = "psiphon-probe"
        private const val PSIPHON_OVERLAY_FILE = "psiphon-overlay.json"

        /**
         * The settings laid over the core's built-in Psiphon configuration:
         * Android has no resolver configuration a Linux-built program could
         * read, so Psiphon is given resolvers of its own. PattNG's overlay,
         * verbatim.
         */
        private const val PSIPHON_OVERLAY = """{"DNSResolverAlternateServers": ["1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4"]}"""

        /**
         * The directories a Go program reads the system's root certificates
         * from. Android keeps the roots in the Conscrypt module since
         * Android 14 and on the system image before that; without this the
         * Psiphon client cannot verify any certificate, its server list
         * download first of all.
         */
        private val CERTIFICATE_DIRECTORIES = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")

        /** True when the current ABI ships the core. */
        fun available(context: Context): Boolean =
            File(context.applicationInfo.nativeLibraryDir, "libaether.so").exists()
    }
}
