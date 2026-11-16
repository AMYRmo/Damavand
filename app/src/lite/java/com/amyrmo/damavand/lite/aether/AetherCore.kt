package com.amyrmo.damavand.lite.aether

import java.security.MessageDigest

/**
 * The Aether core a configuration runs on, as the arguments its process is started with, the
 * listeners among them. The core of a profile is built from the profile's settings, or is the
 * command line the profile carries in their place. A command line is read as written and run as written,
 * so that what the profile says is what runs. Ported from PattNG's AetherCore (3.2.10);
 * the profile is Lite's [AetherSettings] and the exit-node lookup is Lite's (by profile name).
 */
data class AetherCore(val arguments: List<String>, val exit: AetherExit = AetherExit.PLAIN) {

    /**
     * The loopback port the app dials: Psiphon's or Tor's listener when one of them runs inside the
     * tunnel and is what the app reaches, the tunnel's own listener otherwise.
     */
    val port: Int get() = AetherArgs.listenerPortOf(arguments) ?: AetherArgs.SOCKS_PORT

    /**
     * Every port the core listens on as its modes run, its HTTP proxies' too, see [boundListeners]: the ports it is told,
     * and its defaults for the listeners it is not told about; an inbound of the configuration cannot share one.
     */
    val ports: List<Int>
        get() = portsOf(boundListeners).distinct()

    /**
     * The first port two of the listeners the core holds at one time share, as a command written by hand may name;
     * null when each has a port of its own. The core would bind one of them and fail on the other. Ports are told
     * apart by number, as everywhere else in the app. PattNG: Tor or Psiphon alone lets the core's own HTTP proxy go
     * before that carrier's is bound, so those two may share a port, see [listenerGroups].
     */
    val sharedPort: Int?
        get() = listenerGroups.firstNotNullOfOrNull { group ->
            val seen = mutableSetOf<Int>()
            portsOf(group).firstOrNull { !seen.add(it) }
        }

    /**
     * PattNG: the first option of a listener the core binds whose address the core does not take; null when it takes
     * each. The core reads each as an IP address and a port, and where it cannot read one it quietly listens on its
     * default, for its own, Tor's or Psiphon's listener, or not at all, for an HTTP proxy, while the app would go by the
     * address written (aether lib.rs, tor.rs and psiphon.rs). A blank address, or "off", turns an HTTP proxy off. A
     * message names the option, not the address, which is free text the log must not carry.
     */
    val invalidListener: String?
        get() = boundListeners.firstOrNull { flag ->
            val address = AetherArgs.listenerAddressAfter(arguments, flag) ?: return@firstOrNull false
            !AetherArgs.isSocketAddress(address) && !(flag in HTTP_LISTENERS && (address.isEmpty() || address == "off"))
        }

    /**
     * PattNG: the option of the listener the app dials, see [AetherArgs.listenerFlagOf], when the core takes its
     * address but the app does not reach it there; null when it does, or when the command names none and the app gives it
     * one. The app dials the core on 127.0.0.1 (the Xray outbound, the readiness probe, the port checks), which a listener
     * on 0.0.0.0 or :: answers as well, see [AetherArgs.reachesLoopback], but one on 127.0.0.2, ::1 or a network's
     * address does not: the session would stay connecting.
     */
    val unreachableListener: String?
        get() {
            val flag = AetherArgs.listenerFlagOf(arguments)
            val address = AetherArgs.listenerAddressAfter(arguments, flag) ?: return null
            if (!AetherArgs.isSocketAddress(address)) return null
            return flag.takeUnless { AetherArgs.reachesLoopback(address.substringBeforeLast(':')) }
        }

    /**
     * The ports of [listeners], in their order, but 0, which asks for any port: the port the arguments name, or the
     * core's default for a listener they leave out, see [AetherArgs.defaultListenerPorts]. A command written by
     * hand that names only Tor's listener still has the core's own on 1819.
     */
    private fun portsOf(listeners: List<String>): List<Int> =
        listeners.mapNotNull { flag ->
            if (flag in arguments) AetherArgs.portAfter(arguments, flag) else AetherArgs.defaultListenerPorts[flag]
        }.filter { it != 0 }

    /** Every listener option the core binds as [arguments] run it, in the order of [LISTENERS], see [listenerGroups]. */
    private val boundListeners: List<String>
        get() {
            val bound = listenerGroups.flatten().toSet()
            return LISTENERS.filter { it in bound }
        }

    /**
     * The listener options the core binds as [arguments] run it, in the groups it holds at one time: its own and its
     * HTTP proxy always, Tor's listener with Tor inside or around the tunnel and Tor's HTTP proxy with Tor run in any
     * way, and the same for Psiphon, all of them for the whole run. An option of a mode that does not run binds nothing
     * (aether lib.rs, tor.rs and psiphon.rs). Tor or Psiphon alone, see [AetherArgs.aloneOf]: the core binds its own
     * listener and its own HTTP proxy as it starts, then lets that HTTP proxy go and serves its own listener with
     * Tor or Psiphon, which binds that carrier's HTTP proxy (lib.rs, Listeners::bind and into_socks).
     */
    private val listenerGroups: List<List<String>>
        get() {
            val tor = AetherArgs.torModeOf(arguments)
            val psiphon = AetherArgs.psiphonModeOf(arguments)
            if (AetherArgs.REGISTER in arguments) {
                return listOf(
                    LISTENERS.filter { flag ->
                        (tor == AetherTor.REVERSE && (flag == AetherArgs.TOR_BIND || flag == AetherArgs.TOR_HTTP)) ||
                            (psiphon == AetherPsiphon.REVERSE && (flag == AetherArgs.PSIPHON_BIND || flag == AetherArgs.PSIPHON_HTTP))
                    }
                )
            }
            if (AetherArgs.aloneOf(arguments) != null) {
                val aloneHttp = if (tor == AetherTor.ONLY) AetherArgs.TOR_HTTP else AetherArgs.PSIPHON_HTTP
                return listOf(listOf("--bind", AetherArgs.HTTP_PROXY), listOf("--bind", aloneHttp))
            }
            return listOf(LISTENERS.filter { flag ->
                when (flag) {
                    AetherArgs.TOR_BIND -> tor == AetherTor.CHAIN || tor == AetherTor.REVERSE
                    AetherArgs.TOR_HTTP -> tor != AetherTor.OFF
                    AetherArgs.PSIPHON_BIND -> psiphon == AetherPsiphon.CHAIN || psiphon == AetherPsiphon.REVERSE
                    AetherArgs.PSIPHON_HTTP -> psiphon != AetherPsiphon.OFF
                    else -> true
                }
            })
        }

    /** The protocol, which tells whose identity files the core uses. */
    val protocol: AetherProtocol get() = AetherArgs.protocolOf(arguments)

    /** The tunnel as the names of its parts from the outside in, carriers included; see [AetherArgs.pathOf]. */
    val path: List<String> get() = AetherArgs.pathOf(arguments)

    /** [path], with the WARP protocol named by [label], as a screen names it. */
    fun path(label: (AetherProtocol) -> String): List<String> = AetherArgs.pathOf(arguments, label)

    /** The command line a profile carries for this core; [ofCommand] reads it back. */
    val command: String get() = (listOf(COMMAND_NAME) + arguments).joinToString(" ", transform = ::quoted)

    /** True when a process started with [processArguments] runs this core, on whatever SOCKS ports and at whatever log level. */
    fun runsAs(processArguments: List<String>): Boolean =
        AetherArgs.tunnelArguments(processArguments) == AetherArgs.tunnelArguments(arguments)

    /** True when the core is told which proxy to dial out through, as a command written by hand may be. */
    val hasUpstream: Boolean get() = AetherArgs.UPSTREAM in arguments

    /**
     * This core dialling out through the SOCKS inbound on [port] of the loopback address, which Xray
     * serves so that what the core sends leaves through Xray. A core told an upstream of its own keeps it.
     */
    fun through(port: Int): AetherCore =
        if (hasUpstream) this else copy(arguments = arguments + listOf(AetherArgs.UPSTREAM, "socks5://${AetherArgs.LOOPBACK}:$port"))

    companion object {

        /** The name a command line starts with; the app runs its own copy of the core whatever the name says. */
        const val COMMAND_NAME = "aether"

        /**
         * The listeners a core may be told to bind: the core's own, Tor's, Psiphon's, and the HTTP proxies a command
         * written by hand may open beside them.
         */
        private val LISTENERS = listOf(
            "--bind",
            AetherArgs.TOR_BIND,
            AetherArgs.PSIPHON_BIND,
            AetherArgs.HTTP_PROXY,
            AetherArgs.TOR_HTTP,
            AetherArgs.PSIPHON_HTTP,
        )

        /** PattNG: the HTTP proxies among [LISTENERS], which a blank address or "off" turns off. */
        private val HTTP_LISTENERS = setOf(AetherArgs.HTTP_PROXY, AetherArgs.TOR_HTTP, AetherArgs.PSIPHON_HTTP)

        /**
         * The core of [settings]: the command line it carries, or its settings as arguments on
         * [listenPort], the Aether listen port of the app, dialling out through the exit-node of its
         * settings. The log level is the session's to add. A command the app cannot read is left aside
         * for the settings; the profile editor refuses to store one. A screen passes the port it holds,
         * since the setting is read from storage; a command naming no listener gets one there as well.
         */
        fun of(settings: AetherSettings, listenPort: Int = AetherArgs.SOCKS_PORT): AetherCore {
            val core = settings.command?.takeIf { it.isNotBlank() }?.let { ofCommand(it, listenPort) }
                ?: AetherCore(
                    AetherArgs.withoutOption(
                        AetherArgs.buildArguments(settings, listenPort),
                        "--log-level",
                    )
                )
            return core.copy(exit = AetherExit.of(settings))
        }

        /**
         * The core [command] describes, or null when it names nothing the app can run: no argument
         * at all, or a listener whose port cannot be read. Words are split on whitespace, quotes keep a
         * word together, and a program name in front is dropped. A command that names no listener for
         * the app to dial gets one on [listenPort], [AetherArgs.SOCKS_PORT] unless a screen passes
         * the port it holds, the port the Aether outbounds of the app dial unless told otherwise; the
         * core's own defaults are other ports, which nothing in the app dials.
         */
        fun ofCommand(command: String, listenPort: Int = AetherArgs.SOCKS_PORT): AetherCore? {
            val arguments = readableArguments(command) ?: return null
            val listener = AetherArgs.listenerFlagOf(arguments)
            if (listener !in arguments) return AetherCore(AetherArgs.withListener(arguments, listener, listenPort))
            return AetherCore(arguments)
        }

        /**
         * The arguments of [command] when it names something the app can run, see [ofCommand]: not no argument at all,
         * nor a listener whose port cannot be read. Null otherwise.
         */
        private fun readableArguments(command: String): List<String>? {
            val arguments = argumentsOf(command)
            if (arguments.isEmpty()) return null
            val listener = AetherArgs.listenerFlagOf(arguments)
            return arguments.takeIf { listener !in arguments || AetherArgs.portAfter(arguments, listener) != null }
        }

        /**
         * PattNG: whether what the app sends through the core of [settings] leaves it through WARP, rather than through
         * Tor or Psiphon, see [AetherArgs.leavesThroughWarp]: as the command line the profile carries says, or
         * else, as for [of], as its settings say.
         */
        fun leavesThroughWarp(settings: AetherSettings): Boolean {
            val arguments = settings.command?.takeIf { it.isNotBlank() }?.let(::readableArguments)
            return if (arguments != null) {
                AetherArgs.leavesThroughWarp(arguments)
            } else {
                AetherArgs.leavesThroughWarp(AetherTor.fromString(settings.tor), AetherPsiphon.fromString(settings.psiphon))
            }
        }

        /** The arguments of [command]: its [words], without a program name in front. */
        internal fun argumentsOf(command: String): List<String> {
            val words = words(command)
            return if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
        }

        /** The words of a command line: split on whitespace, with single or double quotes keeping a word together. */
        internal fun words(command: String): List<String> {
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

        /** [word] as a command line carries it: quoted when whitespace would split it. */
        private fun quoted(word: String): String = if (word.isEmpty() || word.any(Char::isWhitespace)) "\"$word\"" else word
    }
}

/**
 * PattNG: the exit-node of an Aether core, the outbound that what the core dials out through leaves
 * Xray by. As a rule it is a plain freedom outbound with the finalMask and the dialMode of the core's
 * Aether profile, as an ordinary profile sets them on its own outbound: an Aether profile's outbound
 * only reaches the core on the loopback address, where they would do nothing.
 *
 * Without a chain, the profile can name a profile of its own as the exit-node, one a chain takes for a hop:
 * [node], by its name. That profile's outbound, as a chain builds it, is the exit-node then.
 * Lite 3.2.10: [nodeContent] is the SHA-256 digest of the resolved profile's link, which the service
 * fills in when it resolves the name.
 */
data class AetherExit(
    val finalMask: String? = null,
    val dialMode: String? = null,
    val node: String? = null,
    /**
     * A digest of the profile [node] names, where it was looked up: it tells the profile the core dials
     * out through from the one that has the name by now, which an update of its subscription may have changed.
     */
    val nodeContent: String? = null,
) {

    /**
     * What tells this exit-node from another in another process, without what it is made of: a digest
     * of it, which the session's core carries in its environment, see [AetherEngine.EXIT_ENV]. A
     * node and its content join only where there are some, so that the key of any other exit-node stays what it was.
     */
    val key: String
        get() = digest((listOf(finalMask, dialMode) + listOfNotNull(node, nodeContent)).joinToString("\u0000") { it.orEmpty() })

    companion object {
        /** An exit-node with nothing set, as the core of a custom configuration dials out through. */
        val PLAIN = AetherExit()

        /** The exit-node of the core of [settings]: the profile it names as its node, or else a plain one. */
        fun of(settings: AetherSettings): AetherExit =
            settings.exitNode?.trim()?.takeIf { it.isNotEmpty() }?.let { AetherExit(node = it) }
                ?: AetherExit()

        /** The digest of [text]: the exit key, and the node content. */
        fun digest(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
