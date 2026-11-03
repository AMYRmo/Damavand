package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherProtocol
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.AetherTor
import com.amyrmo.damavand.model.AetherTorBridges

/**
 * The verbatim-command machinery for the Aether core, ported from Narcic
 * Gateway's AetherCommands (which follows PattNG's editor model).
 *
 * A profile either runs the flags its fields generate (`AetherFlags.toFlags`,
 * v2's equivalent of Narcic's buildArguments) or carries a custom
 * command line ([AetherProfile.command]) that runs **as written**. There is
 * deliberately no command→fields parser: the previous one (fromCommandLine)
 * round-tripped a pasted command through the field model and broke real
 * PattNG commands — Apply reset every unmentioned setting to defaults, the
 * pasted `--bind`/`--log-level` were duplicated against the engine's own,
 * whitespace tokenization shredded quoted values and multi-token Tor bridge
 * lines, a WireGuard-over-MASQUE `--peer` landed in fields that protocol
 * never reads, and a pasted `--psiphon-server-entries` collided with the
 * app's own flag.
 *
 * What a custom command still gets from the app is surgical: a listener on
 * the port the bridge dials when it names none (its own listener is kept,
 * and the engine dials the port it names), and a log level that writes the
 * info lines a Psiphon session's readiness check waits on.
 */
object AetherCommands {

    const val BIND = "--bind"
    const val TOR_BIND = "--tor-bind"
    const val PSIPHON_BIND = "--psiphon-bind"
    const val LOG_LEVEL = "--log-level"
    const val DEFAULT_LOG_LEVEL = "info"

    /** True when [profile] carries a custom command that runs verbatim. */
    fun isCustom(profile: AetherProfile): Boolean = profile.command.isNotBlank()

    /** [command] split into words; single/double quotes group, whitespace separates. */
    fun words(command: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var open = false
        for (c in command) {
            when {
                quote != null -> if (c == quote) quote = null else word.append(c)
                c == '"' || c == '\'' -> {
                    quote = c
                    open = true
                }
                c.isWhitespace() -> if (open) {
                    words.add(word.toString())
                    word.setLength(0)
                    open = false
                }
                else -> {
                    word.append(c)
                    open = true
                }
            }
        }
        if (open) words.add(word.toString())
        return words
    }

    /** The words of [command] as arguments: a leading program name (`aether …`) is dropped. */
    fun argumentsOf(command: String): List<String> {
        val words = words(command)
        return if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
    }

    /**
     * The arguments a custom-command session runs on: the command's own words,
     * repaired only where the app depends on them — a listener for the bridge
     * to dial (Psiphon's or Tor's when one of them runs inside the tunnel, see
     * [listenerFlagOf]) on [port] when it names none, and a log level; when
     * Psiphon readiness depends on the core's ready word, the level is raised
     * to one that writes info lines.
     */
    fun customArguments(profile: AetherProfile, port: Int, logLevel: String = DEFAULT_LOG_LEVEL): List<String> {
        var arguments = argumentsOf(profile.command)
        val listener = listenerFlagOf(arguments)
        if (listener !in arguments) arguments = withListener(arguments, listener, port)
        if (LOG_LEVEL !in arguments && "--verbose" !in arguments) {
            arguments = arguments + listOf(LOG_LEVEL, logLevel)
        }
        if (readyNeedsWord(arguments)) arguments = withInfoLines(arguments)
        return arguments
    }

    /** Where Tor stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    fun torModeOf(argv: List<String>): AetherTor = argv.fold(AetherTor.OFF) { mode, word ->
        when (word) {
            "--tor" -> AetherTor.CHAIN
            "--tor-reverse" -> AetherTor.REVERSE
            "--tor-only" -> AetherTor.ONLY
            else -> mode
        }
    }

    /** Where Psiphon stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    fun psiphonModeOf(argv: List<String>): AetherPsiphon = argv.fold(AetherPsiphon.OFF) { mode, word ->
        when (word) {
            "--psiphon" -> AetherPsiphon.CHAIN
            "--psiphon-reverse" -> AetherPsiphon.REVERSE
            "--psiphon-only" -> AetherPsiphon.ONLY
            else -> mode
        }
    }

    /** The option naming the listener the app dials: Psiphon's when Psiphon runs inside the tunnel, Tor's when Tor does, else the core's own. */
    fun listenerFlagOf(argv: List<String>): String = when {
        psiphonModeOf(argv) == AetherPsiphon.CHAIN -> PSIPHON_BIND
        torModeOf(argv) == AetherTor.CHAIN -> TOR_BIND
        else -> BIND
    }

    /** The loopback port the app dials on a core started with [argv]; null when the listener names none that reads as a port. */
    fun listenerPortOf(argv: List<String>): Int? =
        valueAfter(argv, listenerFlagOf(argv))?.substringAfterLast(':', "")?.toIntOrNull()

    /** [arguments] without every [flag] and the value after it. */
    fun withoutOption(arguments: List<String>, flag: String): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            if (arguments[index] == flag) index += 2 else kept.add(arguments[index++])
        }
        return kept
    }

    /** [arguments] with the listener [flag] naming the loopback [port], in place of whatever it named for it. */
    fun withListener(arguments: List<String>, flag: String, port: Int): List<String> =
        withoutOption(arguments, flag) + listOf(flag, "127.0.0.1:$port")

    private fun valueAfter(argv: List<String>, flag: String): String? =
        argv.lastIndexOf(flag).takeIf { it >= 0 }?.let { argv.getOrNull(it + 1) }

    /**
     * True when the listener the app dials is Psiphon's, inside the tunnel or
     * alone. It answers the SOCKS greeting as soon as it is bound and closes
     * every connection until Psiphon has a tunnel, so the greeting alone says
     * nothing and readiness needs the core's word as well.
     */
    fun readyNeedsWord(argv: List<String>): Boolean =
        psiphonModeOf(argv).let { it == AetherPsiphon.CHAIN || it == AetherPsiphon.ONLY }

    /** True when a core started with [argv] writes its info lines, the ready word among them. */
    fun showsInfo(argv: List<String>): Boolean =
        "--verbose" in argv || (valueAfter(argv, LOG_LEVEL) ?: DEFAULT_LOG_LEVEL) in setOf("info", "debug", "trace")

    /** [argv] at a level that writes the info lines: as they are, unless they name a quieter level, which gives way to the default. */
    fun withInfoLines(argv: List<String>): List<String> =
        if (showsInfo(argv)) argv else withoutOption(argv, LOG_LEVEL) + listOf(LOG_LEVEL, DEFAULT_LOG_LEVEL)

    /** Why [profile] cannot run, as the rules of PattNG's editor say; null when it can. */
    enum class Problem { CARRIER_NEEDS_MASQUE, TOR_PSIPHON_CONFLICT, TOR_BRIDGES_MISSING }

    fun problemOf(profile: AetherProfile): Problem? {
        val tor = AetherTor.fromString(profile.tor)
        val psiphon = AetherPsiphon.fromString(profile.psiphon)
        val proto = AetherProtocol.fromString(profile.protocol)
        // Tor and Psiphon carry TCP alone and WARP's WireGuard endpoints answer on UDP: the core refuses the pair.
        // WireGuard over MASQUE goes, as its WireGuard rides inside the MASQUE tunnel.
        if ((tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE) && !proto.overMasque) {
            return Problem.CARRIER_NEEDS_MASQUE
        }
        // Both go together only nested, one inside the tunnel and the other around it.
        val nested = tor == AetherTor.OFF || psiphon == AetherPsiphon.OFF ||
            (tor == AetherTor.CHAIN && psiphon == AetherPsiphon.REVERSE) ||
            (tor == AetherTor.REVERSE && psiphon == AetherPsiphon.CHAIN)
        if (!nested) return Problem.TOR_PSIPHON_CONFLICT
        if (tor != AetherTor.OFF && AetherTorBridges.fromString(profile.torBridges) == AetherTorBridges.OWN &&
            profile.torBridgeLines.lines().none { it.isNotBlank() }
        ) {
            return Problem.TOR_BRIDGES_MISSING
        }
        return null
    }
}
