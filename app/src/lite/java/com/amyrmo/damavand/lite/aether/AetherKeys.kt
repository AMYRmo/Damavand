package com.amyrmo.damavand.lite.aether

import android.content.Context
import com.amyrmo.damavand.lite.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The WARP identity one key file holds: the device id WARP knows the
 * app by, and the addresses WARP gave it. (PattNG AetherIdentityManager,
 * 3.2.11.)
 */
data class AetherIdentity(
    val deviceId: String,
    val ipv4: String,
    val ipv6: String,
)

data class AetherIdentityStatus(
    val protocol: AetherProtocol,
    val primary: AetherIdentity?,
    val secondary: AetherIdentity? = null,
)

/** A key file of the identity folder, one of [AetherIdentityManager.KEY_FILES], with the identity it holds; null without one. */
data class AetherKey(
    val file: String,
    val identity: AetherIdentity?,
)

/**
 * The settings of the page that gets new WARP keys. They are that page's alone: no profile's settings reach
 * them, and they reach no profile. Text is kept as written and read when a run is built. [command] is blank unless it
 * was edited by hand, and then it runs as written, see [AetherKeys.isCustom]. Stored as one JSON string in
 * SettingsStore (PattNG keeps them in MMKV; Lite has no MMKV).
 */
@Serializable
data class AetherKeysSettings(
    val kind: AetherKeyKind = AetherKeyKind.ALL,
    /** --api-address, blank for the core's own: the server name's address. */
    val enrollAddress: String = "",
    /** --api-sni, blank for the core's own: the host. */
    val apiSni: String = "",
    /**
     * --api-host, blank for the core's own: api.devices.cloudflare.com, and with [apiSni] blank too,
     * api.cloudflareclient.com when that one gets no answer.
     */
    val apiHost: String = "",
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    val ech: Boolean = false,
    val echDns: String = AetherArgs.ECH_DNS,
    val echDomain: String = AetherArgs.ECH_DOMAIN,
    val fingerprint: AetherFingerprint = AetherFingerprint.CHROME,
    val exitNode: String = "",
    val finalMask: String = "",
    val dialMode: String = "",
    val command: String = "",
) {
    /**
     * The exit-node the run dials out through: the profile [exitNode] names by its name, see [AetherExit.node], or
     * else a plain one, with the finalMask and the dialMode set here.
     */
    val exit: AetherExit
        get() = exitNode.ifBlank { null }?.let { AetherExit(node = it.trim()) }
            ?: AetherExit(finalMask.ifBlank { null }, dialMode.ifBlank { null })

    fun toJson(): String = aetherKeysJson.encodeToString(this)

    companion object {
        fun fromJson(text: String?): AetherKeysSettings? = try {
            text?.takeIf { it.isNotBlank() }?.let { aetherKeysJson.decodeFromString(serializer(), it) }
        } catch (_: Exception) {
            null
        }
    }
}

private val aetherKeysJson = Json { ignoreUnknownKeys = true }

/**
 * The run of the core that gets new WARP keys: its `--register`, which registers the keys asked for and
 * ends without scanning or opening a tunnel, with where the calls to the WARP API go, whether they hide their server
 * name with Encrypted Client Hello, and the cipher suites their TLS handshakes offer. See
 * [AetherIdentityManager.renew], which keeps the keys in use until the new ones are ready.
 * (PattNG core/AetherKeys.kt, 3.2.11.)
 */
object AetherKeys {

    /** The option that has the core register keys and end, with the word naming which. */
    internal const val REGISTER = "--register"

    /** Why settings cannot run. */
    enum class Problem {
        INVALID_ENROLL_ADDRESS,
        INVALID_API_SNI,
        INVALID_API_HOST,
        INVALID_FRAGMENT,
        INVALID_ECH_DNS,
        INVALID_ECH_DOMAIN,
        INVALID_FINAL_MASK,
        INVALID_COMMAND,
    }

    /**
     * The arguments [settings] give: the keys to register first, the address the calls to the WARP API go to, the
     * server name of their ClientHello and their HTTP host, each unless it is left blank, the ClientHello of those calls
     * in pieces when fragmenting is on (--fragment, which a run that only registers keys applies to those calls alone),
     * sized and spaced as set or as the core has them by default, and whole without the flag, Encrypted Client Hello
     * on those calls with the key of the ECH domain, asked of the ECH DNS, and the TLS 1.2 cipher suites of the
     * fingerprint, Chrome's as its rule, and its groups, with GREASE left out where the fingerprint has none.
     */
    fun arguments(settings: AetherKeysSettings): List<String> = buildList {
        addAll(listOf(REGISTER, settings.kind.register))
        settings.enrollAddress.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--api-address", it)) }
        settings.apiSni.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--api-sni", it)) }
        settings.apiHost.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--api-host", it)) }
        if (settings.fragment) {
            add("--fragment")
            AetherRange.parse(settings.fragmentSize, AetherRange.FRAGMENT_SIZE)?.let { addAll(listOf("--fragment-size", it.toString())) }
            AetherRange.parse(settings.fragmentDelay, AetherRange.FRAGMENT_DELAY)?.let { addAll(listOf("--fragment-delay", it.toString())) }
        }
        if (settings.ech) {
            addAll(listOf("--ech", "auto"))
            addAll(listOf("--ech-dns", settings.echDns.trim().ifEmpty { AetherArgs.ECH_DNS }))
            addAll(listOf("--ech-domain", settings.echDomain.trim().ifEmpty { AetherArgs.ECH_DOMAIN }))
        }
        addAll(settings.fingerprint.arguments)
    }

    /** The command line [arguments] makes of [settings], as the page shows it. */
    fun builtCommand(settings: AetherKeysSettings): String = AetherCore(arguments(settings)).command

    /** Whether [settings] carry a command written by hand: one that is not blank and not the one the settings give. */
    fun isCustom(settings: AetherKeysSettings): Boolean =
        settings.command.isNotBlank() && settings.command.trim() != builtCommand(settings)

    /** The arguments a run of [settings] starts the core with: those of the command written by hand, or the settings'. */
    fun runArguments(settings: AetherKeysSettings): List<String> =
        if (isCustom(settings)) AetherCore.argumentsOf(settings.command) else arguments(settings)

    /** The keys [arguments] register, read as the core reads them: the word after the last --register; null without one it takes. */
    fun kindOf(arguments: List<String>): AetherKeyKind? =
        arguments.lastIndexOf(REGISTER).takeIf { it >= 0 }?.let { AetherKeyKind.ofRegister(arguments.getOrNull(it + 1)) }

    /**
     * Why [settings] cannot run, or null when they can. A command written by hand is only checked for what it
     * registers: the settings it replaces do not count, and the core names what else it does not take. The
     * finalMask counts either way, since the run dials out through it, and is checked as well while a profile is
     * the exit-node, which leaves it out of use, as the Aether page checks it: no broken one is kept for freedom.
     * Whether one profile, and one only, has that name still is for the run to look up.
     */
    fun problem(settings: AetherKeysSettings): Problem? {
        if (!takesFinalMask(settings.finalMask)) return Problem.INVALID_FINAL_MASK
        if (isCustom(settings)) return Problem.INVALID_COMMAND.takeIf { kindOf(runArguments(settings)) == null }
        val address = settings.enrollAddress.trim()
        if (address.isNotEmpty() && !isEnrollAddress(address)) return Problem.INVALID_ENROLL_ADDRESS
        // The core checks --api-sni and --api-host as it checks --masque-sni.
        val sni = settings.apiSni.trim()
        if (sni.isNotEmpty() && !AetherFmt.isMasqueSni(sni)) return Problem.INVALID_API_SNI
        val host = settings.apiHost.trim()
        if (host.isNotEmpty() && !AetherFmt.isMasqueSni(host)) return Problem.INVALID_API_HOST
        if (settings.fragment && !(takesRange(settings.fragmentSize, AetherRange.FRAGMENT_SIZE) && takesRange(settings.fragmentDelay, AetherRange.FRAGMENT_DELAY))) {
            return Problem.INVALID_FRAGMENT
        }
        if (settings.ech) {
            val dns = settings.echDns.trim()
            if (dns.isNotEmpty() && !AetherFmt.isEchDns(dns)) return Problem.INVALID_ECH_DNS
            val domain = settings.echDomain.trim()
            if (domain.isNotEmpty() && !AetherFmt.isEchDomain(domain)) return Problem.INVALID_ECH_DOMAIN
        }
        return null
    }

    /** Whether [text] is blank, the core's own default, or a number or range within [bounds]. */
    private fun takesRange(text: String, bounds: IntRange): Boolean = text.isBlank() || AetherRange.parse(text, bounds) != null

    /**
     * Whether [value] is an address the core sends the calls to the WARP API to, as its --api-address takes it: an
     * IP address, an IPv6 one with or without brackets, or a domain name, alone or followed by :port, an IPv6 address
     * then in brackets. A label of the name cannot start or end with '-', so that no value reads as an option.
     */
    internal fun isEnrollAddress(value: String): Boolean {
        if (isEnrollHost(value)) return true
        val separator = value.lastIndexOf(':')
        if (separator <= 0) return false
        val host = value.substring(0, separator)
        if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) return false
        val port = value.substring(separator + 1)
        return port.length in 1..5 && port.all { it in '0'..'9' } && port.toInt() in 1..65535 && isEnrollHost(host)
    }

    /** An IP address, an IPv6 one with or without brackets, or a domain name. */
    private fun isEnrollHost(host: String): Boolean = AetherEndpoint.of(host, "443") != null || AetherFmt.isEchDomain(host)

    /**
     * Whether [finalMask] is blank or a JSON object: the final mask a run dials out through is a JSON object
     * of the exit's last hop, as the core reads it. (PattNG AetherExit.takesFinalMask, which reads it
     * with Gson; Lite has no Gson, so org.json reads it instead.)
     */
    private fun takesFinalMask(finalMask: String?): Boolean = finalMask.isNullOrBlank() || try {
        JSONObject(finalMask)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * The WARP keys in use and the renewals that get new ones, ported from
 * PattNG's AetherIdentityManager (3.2.11).
 *
 * Lite adaptations:
 * - The core's identity files live at <filesDir>/aether/{aether,masque,wg}.toml (see AetherEngine), so the
 *   key files are the siblings the core derives from those paths — wg.toml, wg-secondary.toml, masque.toml,
 *   masque-secondary.toml, masque-gool.toml — not PattNG's aether-wg.toml / aether-masque.toml names.
 * - The renewal run is a one-shot core run through [AetherEngine.runOneShot], not PattNG's withProcess:
 *   Lite has no second Xray instance, so a renewal that dials out through an exit-node profile runs the
 *   exit through Lite's own XrayController (the ViewModel starts it on a spare SOCKS port and stops it
 *   after), and a renewal while a tunnel is connected is refused outright — starting that Xray would
 *   end the tunnel.
 * - No LogUtil: AppLog instead.
 */
object AetherIdentityManager {

    const val BASE_FILE = "aether.toml"
    const val MASQUE_FILE = "masque.toml"
    const val MASQUE_INNER_FILE = "masque-secondary.toml"
    const val WIREGUARD_FILE = "wg.toml"
    const val WIREGUARD_INNER_FILE = "wg-secondary.toml"

    /**
     * The WireGuard key of WireGuard over MASQUE, beside [MASQUE_FILE] as the core names it. --register gool writes it
     * as it writes the other keys; a scan or a connection without it has the core register one through the MASQUE
     * tunnel, from inside WARP.
     */
    const val MASQUE_GOOL_FILE = "masque-gool.toml"

    /** The key files of every kind, in the order the core registers them. */
    internal val KEY_FILES = listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE, MASQUE_FILE, MASQUE_INNER_FILE, MASQUE_GOOL_FILE)

    private const val WORK_DIR = "aether"

    /**
     * Where a renewal gathers its new keys. The keys in use stay in [WORK_DIR], untouched, until
     * every new key it registers is here; only then does each new key take the place of the old one.
     */
    private const val RENEWAL_DIR = "aether-renewal"

    /**
     * Written into [RENEWAL_DIR] once every new key of a renewal is there. From then on the new keys
     * are the keys: whatever stops the moving of them, [settle] finishes it.
     */
    internal const val READY_MARK = "ready"

    /**
     * Where the renewal before [RENEWAL_DIR] moved the keys in use while it registered new ones.
     * An app killed during such a renewal left it behind, holding keys nothing uses any more;
     * [settleIn] removes it the first time the app uses Aether.
     */
    private const val PREVIOUS_DIR = "aether-previous"

    private const val RENEW_TIMEOUT_MS = 4 * 60_000L

    /**
     * A renewal through Tor or Psiphon, which a command written by hand may ask for, waits for the
     * carrier to come up first, which takes what a scan may take.
     */
    private const val RENEW_THROUGH_CARRIER_TIMEOUT_MS = 10 * 60_000L

    private val identityField = Regex("""^(device_id|ipv4|ipv6)\s*=\s*"([^"]*)"$""")
    private val multilineDelimiter = Regex("\"\"\"|'''")

    /** The core's last word in a registration, once every key it was asked for is saved. */
    private val keysRegistered = Regex("""identities ready: \S""")

    /** One renewal at a time: they share [RENEWAL_DIR]. */
    private val renewal = Mutex()

    fun workDir(context: Context): File = File(context.filesDir, WORK_DIR)

    private fun renewalDir(context: Context): File = File(context.filesDir, RENEWAL_DIR)

    suspend fun status(context: Context, protocol: AetherProtocol): AetherIdentityStatus =
        withContext(Dispatchers.IO) {
            settle(context)
            status(workDir(context), protocol)
        }

    /** Every key of the identity folder, each of [KEY_FILES] with the identity it holds. */
    suspend fun keys(context: Context): List<AetherKey> =
        withContext(Dispatchers.IO) {
            settle(context)
            keys(workDir(context), KEY_FILES)
        }

    /**
     * Registers new keys of [kind] by running the core on [arguments], which register them, see
     * [AetherKeys], and puts them in place of the keys in use once all of them are there. Until then
     * the keys in use stay where they are, untouched, so a failure, a cancellation or the app being
     * killed leaves them in use; the keys of another kind are never touched. The core dials out
     * through the exit-node [exit]; [exitPort] is the SOCKS port of the exit Xray the caller started
     * for it, or null when the run dials out directly. Returns the new keys, or null when the keys in use stay.
     */
    suspend fun renew(
        context: Context,
        kind: AetherKeyKind,
        arguments: List<String>,
        exit: AetherExit,
        exitPort: Int? = null,
        onOutput: (String) -> Unit,
    ): List<AetherKey>? = renewal.withLock {
        val workDir = workDir(context)
        val renewalDir = renewalDir(context)
        val files = filesOf(kind)
        if (!renew(workDir, renewalDir, files) { register(context, arguments, exit, exitPort, renewalDir, onOutput) }) return@withLock null
        withContext(Dispatchers.IO) {
            if (!settle(workDir, renewalDir)) {
                AppLog.w("AetherIdentity", "the new keys are ready but not all in place yet; the next core start moves the rest")
            }
            keys(workDir, files)
        }
    }

    /** [settleIn] the app's files folder; whatever reads the keys calls it first. */
    fun settle(context: Context): Boolean = settleIn(context.filesDir)

    /**
     * Removes [PREVIOUS_DIR] from [filesDir], the app's files folder, with the keys an interrupted
     * renewal of an older version left there, and then does what [settle] does on the identity
     * folder and the renewal folder in it; the keys in use stay as they are. A folder that cannot
     * all go now is tried again at the next use.
     */
    internal fun settleIn(filesDir: File): Boolean {
        if (!File(filesDir, PREVIOUS_DIR).deleteRecursively()) {
            AppLog.w("AetherIdentity", "the keys an interrupted renewal of an older version left could not all be removed; the next use tries again")
        }
        return settle(File(filesDir, WORK_DIR), File(filesDir, RENEWAL_DIR))
    }

    /**
     * Runs the core once on [arguments] to register the keys they ask for into [renewalDir]: it
     * registers them, through a carrier if the arguments name one, and ends without scanning or
     * opening a tunnel. The run is over at its word that the keys are saved, which it writes at the
     * info level.
     */
    private suspend fun register(
        context: Context,
        arguments: List<String>,
        exit: AetherExit,
        exitPort: Int?,
        renewalDir: File,
        onOutput: (String) -> Unit,
    ): Boolean {
        val withExit = if (exitPort != null) AetherCore(arguments).through(exitPort).arguments else arguments
        val withInfo = withInfoLines(withExit)
        val timeoutMs = if (reachesWarpThroughCarrier(withInfo)) RENEW_THROUGH_CARRIER_TIMEOUT_MS else RENEW_TIMEOUT_MS
        // runOneShot returns the matched line (String?) — the keys
        // renewal succeeded iff a line matched, so the Boolean is
        // the null check, not the lambda's own result. (The lambda
        // must return String? — the line when registered, else null —
        // because that is what runOneShot's match takes.)
        // 3.2.17 (#60): the renewal runs on the main thread (the
        // ViewModel launches it there) and the run blocks for up to
        // 4 minutes (10 through a carrier) — so the whole call moves
        // to IO. The wait itself is cancellable (see runOneShot), so
        // Cancel ends the core at once instead of waiting it out.
        return withContext(Dispatchers.IO) {
            AetherEngine.runOneShot(
                context = context,
                arguments = withInfo,
                identityDir = renewalDir,
                exitKey = exit.key,
                timeoutMs = timeoutMs,
                onOutput = onOutput,
            ) { line -> line.takeIf(::isRegistered) } != null
        }
    }

    /** Whether the run dials WARP through Tor or Psiphon in reverse, which takes what a scan may take. */
    private fun reachesWarpThroughCarrier(arguments: List<String>): Boolean =
        AetherArgs.torModeOf(arguments) == AetherTor.REVERSE || AetherArgs.psiphonModeOf(arguments) == AetherPsiphon.REVERSE

    /** The arguments with info lines: the ready word is an info line, so the run must write them. */
    private fun withInfoLines(arguments: List<String>): List<String> =
        if (showsInfo(arguments)) arguments else AetherArgs.withoutOption(arguments, "--log-level") + listOf("--log-level", "info")

    /** Whether [arguments] already ask the core for info lines or quieter ones. */
    private fun showsInfo(arguments: List<String>): Boolean {
        val at = arguments.indexOf("--log-level")
        val level = arguments.getOrNull(at + 1)?.takeIf { at >= 0 } ?: "info"
        return level in setOf("info", "debug", "trace")
    }

    internal fun status(workDir: File, protocol: AetherProtocol): AetherIdentityStatus = when (protocol) {
        AetherProtocol.MASQUE -> AetherIdentityStatus(protocol, read(File(workDir, MASQUE_FILE)))
        AetherProtocol.WIREGUARD -> AetherIdentityStatus(protocol, read(File(workDir, WIREGUARD_FILE)))
        AetherProtocol.GOOL -> AetherIdentityStatus(
            protocol,
            read(File(workDir, WIREGUARD_FILE)),
            read(File(workDir, WIREGUARD_INNER_FILE)),
        )

        AetherProtocol.MIM -> AetherIdentityStatus(
            protocol,
            read(File(workDir, MASQUE_FILE)),
            read(File(workDir, MASQUE_INNER_FILE)),
        )

        AetherProtocol.WG_OVER_MASQUE -> AetherIdentityStatus(
            protocol,
            read(File(workDir, MASQUE_FILE)),
            read(File(workDir, MASQUE_GOOL_FILE)),
        )
    }

    /** The key files a registration of [kind] writes, in the order of [KEY_FILES]. */
    fun filesOf(kind: AetherKeyKind): List<String> = when (kind) {
        AetherKeyKind.ALL -> KEY_FILES
        AetherKeyKind.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherKeyKind.MASQUE -> listOf(MASQUE_FILE)
        AetherKeyKind.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherKeyKind.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherKeyKind.WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
    }

    /** The key files a tunnel of [protocol] uses: its own, and the inner hop's for a two-hop one. */
    fun filesOf(protocol: AetherProtocol): List<String> = when (protocol) {
        AetherProtocol.MASQUE -> listOf(MASQUE_FILE)
        AetherProtocol.WIREGUARD -> listOf(WIREGUARD_FILE)
        AetherProtocol.GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherProtocol.MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherProtocol.WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
    }

    /**
     * The key files a core on [arguments] needs, which the WARP keys page gets: those of the protocol they run, read
     * as the core reads it, and none when they run Psiphon or Tor alone, with no WARP tunnel.
     */
    fun filesNeededBy(arguments: List<String>): List<String> =
        if (AetherArgs.psiphonModeOf(arguments) == AetherPsiphon.ONLY || AetherArgs.torModeOf(arguments) == AetherTor.ONLY) {
            emptyList()
        } else {
            filesOf(AetherArgs.protocolOf(arguments))
        }

    /** Which of [files] the identity folder lacks: not there, or not readable as a key. */
    suspend fun missing(context: Context, files: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            settle(context)
            missing(workDir(context), files)
        }

    internal fun missing(dir: File, files: List<String>): List<String> = files.filter { read(File(dir, it)) == null }

    /** Each of [files] in [dir] with the identity it holds. */
    internal fun keys(dir: File, files: List<String>): List<AetherKey> = files.map { AetherKey(it, read(File(dir, it))) }

    internal fun parse(text: String): AetherIdentity? {
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
        val deviceId = fields["device_id"]?.takeIf { it.isNotBlank() } ?: return null
        return AetherIdentity(deviceId, fields["ipv4"].orEmpty(), fields["ipv6"].orEmpty())
    }

    /**
     * The tunnels over MASQUE share the MASQUE key and the others the WireGuard key; a two-hop tunnel adds a second key, of its
     * kind or, for WireGuard over MASQUE, a WireGuard key of its own, which no other tunnel uses.
     */
    fun sharesIdentity(first: AetherProtocol, second: AetherProtocol): Boolean =
        first.overMasque == second.overMasque

    internal fun isRegistered(line: String): Boolean = keysRegistered.containsMatchIn(line)

    /**
     * Gathers new keys in [renewalDir] through [register] and moves them over the keys in [workDir]
     * once every one of [files] is there and reads as a key. Nothing in [workDir] is touched before
     * that moment, so whatever stops a renewal earlier leaves the keys in use as they were; a stop
     * after it is finished by [settle]. A key not among [files] stays as it is. True when the new
     * keys took the place of the old ones. The steps on the files run on [io].
     */
    internal suspend fun renew(
        workDir: File,
        renewalDir: File,
        files: List<String>,
        io: CoroutineDispatcher = Dispatchers.IO,
        register: suspend () -> Boolean,
    ): Boolean {
        val cleared = withContext(io) {
            // A renewal whose keys were all ready is finished first; what one stopped earlier left is no key anyone uses.
            settle(workDir, renewalDir) && renewalDir.deleteRecursively() && renewalDir.mkdirs()
        }
        if (!cleared) return false
        // A cancellation can be delivered as the result of a blocking step comes back, after the step
        // itself has run; whether the new keys were marked ready is therefore recorded inside that
        // step, and the finally block goes by the record rather than by a value the step returned.
        val ready = AtomicBoolean(false)
        try {
            if (register()) {
                withContext(io) {
                    // A key the run left beside the ones asked for goes with the folder rather than into place.
                    (KEY_FILES - files.toSet()).forEach { File(renewalDir, it).delete() }
                    ready.set(isComplete(renewalDir, files) && markReady(renewalDir))
                }
            }
        } finally {
            withContext(NonCancellable + io) {
                if (ready.get()) settle(workDir, renewalDir) else renewalDir.deleteRecursively()
            }
        }
        return ready.get()
    }

    /**
     * Moves the new keys of a renewal whose keys were all ready over the keys in [workDir], one
     * after another, and then removes [renewalDir]. Without the ready mark there is nothing to move:
     * the new keys are incomplete, or a renewal is still gathering them, and the keys in use stay.
     * Another process may be finishing the same renewal at the same time; a key it moved first is
     * simply gone here. True when no renewal with ready keys waits any longer.
     */
    internal fun settle(workDir: File, renewalDir: File): Boolean {
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
     * Moves [source] over [target] in one step that replaces the key in use, or leaves it as it
     * was; true once [source] is gone, moved by this call or by another process finishing the same
     * renewal.
     */
    private fun moveOver(source: File, target: File): Boolean = try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    } catch (_: IOException) {
        !source.isFile
    }

    /** Whether every one of [files] is in [dir] and reads as a key. */
    internal fun isComplete(dir: File, files: List<String>): Boolean = files.all { read(File(dir, it)) != null }

    private fun markReady(dir: File): Boolean = try {
        File(dir, READY_MARK).createNewFile()
    } catch (_: IOException) {
        false
    }

    private fun read(file: File): AetherIdentity? = try {
        if (file.isFile) parse(file.readText()) else null
    } catch (_: IOException) {
        null
    }
}
