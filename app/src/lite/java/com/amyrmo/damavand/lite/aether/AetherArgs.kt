package com.amyrmo.damavand.lite.aether

import java.util.Locale

/**
 * The Aether core's command line, ported from PattNG's AetherCoreManager
 * (3.2.10): [buildArguments] turns a profile's settings into the arguments
 * its process starts with, and the listener helpers read those arguments
 * back the way the core does, so the app and the core agree on which
 * listener the app dials and which ports the core holds.
 *
 * Lite adaptations: the settings come from [AetherSettings] instead of
 * PattNG's ProfileItem; AppConfig constants are inlined here.
 */
object AetherArgs {

    const val LOOPBACK = "127.0.0.1"

    /** The tag of the secondary-socks inbound the core dials out through (PattNG's AppConfig). */
    const val TAG_SECONDARY_SOCKS = "secondary-socks"

    /** The tag of the exit-node outbound what the core dials out through leaves Xray by (PattNG's AppConfig). */
    const val TAG_EXIT_NODE = "exit-node"

    /** The Aether listen port of the app (PattNG's PORT_AETHER_SOCKS). */
    const val SOCKS_PORT = 10819

    /** The secondary-socks inbound sits this many ports above the Aether listen port. */
    const val SECONDARY_SOCKS_OFFSET = 3

    /** The port Xray's secondary-socks inbound takes for an Aether core on [SOCKS_PORT]. */
    val secondarySocksPort: Int get() = SOCKS_PORT + SECONDARY_SOCKS_OFFSET

    const val DEFAULT_LOG_LEVEL = "info"

    const val TOR_BIND = "--tor-bind"
    const val PSIPHON_BIND = "--psiphon-bind"
    const val HTTP_PROXY = "--http-proxy"
    const val TOR_HTTP = "--tor-http"
    const val PSIPHON_HTTP = "--psiphon-http"
    const val UPSTREAM = "--upstream"

    /** The core's flag naming the file of server entries the Psiphon client starts with. */
    const val PSIPHON_SERVER_ENTRIES = "--psiphon-server-entries"

    /** The word the core's --register takes; a registration binds only its carrier's listeners. */
    const val REGISTER = "--register"

    /** The word a command names the app's own list by; the real file takes its place when the core starts. */
    const val SHIPPED_LIST = "shipped-list"

    const val MASQUE_SNI = "www.cloudflare.com"
    const val ECH_DNS = "udp://1.1.1.1"
    const val ECH_DOMAIN = "cloudflare-ech.com"

    val defaultListenerPorts = mapOf("--bind" to 1819, TOR_BIND to 1820, PSIPHON_BIND to 1821)

    private const val TOR_NAME = "TOR"
    private const val PSIPHON_NAME = "PSIPHON"

    /** The values of --wiw-peers and --mim-peers that ask for a scan instead of naming hops, as the core reads them. */
    private val scanKeywords = setOf("auto", "scan", "none", "off", "0")

    private enum class HopSetting { WIW_OUTER, WIW_INNER, WIW_PEERS, MIM_OUTER, MIM_INNER, MIM_PEERS }

    /**
     * The text of a setting as the value of an option of the core: trimmed, and null when it is blank or
     * starts with '-'. No value of these settings starts so, and one that did would read as an option of its
     * own wherever the app looks options up word by word: a link carrying dns=--upstream would leave the core
     * without the upstream the app gives it, so that it dialled out around Xray.
     */
    fun settingValue(value: String?): String? = value?.trim()?.takeUnless { it.isEmpty() || it.startsWith('-') }

    fun masqueOverHttp2(protocol: AetherProtocol, transport: AetherTransport, tor: AetherTor, psiphon: AetherPsiphon): Boolean =
        protocol.overMasque && (transport == AetherTransport.HTTP2 || tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE)

    fun buildArguments(
        settings: AetherSettings,
        port: Int,
        scan: Boolean = false,
        logLevel: String = DEFAULT_LOG_LEVEL,
    ): List<String> {
        val protocol = AetherProtocol.fromString(settings.protocol)
        // A scan looks for WARP endpoints from where the session will look: a carrier around the tunnel
        // stays, since the session reaches WARP from its exit, while one inside the tunnel has no part in it.
        val tor = AetherTor.fromString(settings.tor).takeUnless { scan && it != AetherTor.REVERSE } ?: AetherTor.OFF
        val psiphon = AetherPsiphon.fromString(settings.psiphon).takeUnless { scan && it != AetherPsiphon.REVERSE } ?: AetherPsiphon.OFF
        // The listener the app dials takes [port]: Psiphon's or Tor's when one of them runs inside the
        // tunnel and is what the app reaches, the tunnel's own otherwise. Every other listener takes the
        // ports after it, in this order: the tunnel's own, then Tor's, then Psiphon's. Psiphon around the
        // tunnel is the exception: nothing of the app dials its listener and the core takes the port
        // Psiphon reports, so an ephemeral port keeps a scan core from colliding with the session's.
        // Tor around the tunnel gets a real port: Aether before 2.3.5 made the address Tor was told to
        // listen on the tunnel's upstream, and with port 0 there set it aside and dialled around Tor.
        // The core the app ships dials the port Tor bound, so an ephemeral one would do now; the ports
        // stay as the app has always handed them out.
        val dialsPsiphon = psiphon == AetherPsiphon.CHAIN
        val dialsTor = tor == AetherTor.CHAIN && !dialsPsiphon
        var next = port + 1
        val own = if (dialsPsiphon || dialsTor) next++ else port
        val torBind = when (tor) {
            AetherTor.CHAIN -> if (dialsTor) port else next++
            AetherTor.REVERSE -> next++
            AetherTor.OFF, AetherTor.ONLY -> null
        }
        val psiphonBind = when (psiphon) {
            AetherPsiphon.CHAIN -> if (dialsPsiphon) port else next++
            AetherPsiphon.REVERSE -> 0
            AetherPsiphon.OFF, AetherPsiphon.ONLY -> null
        }
        return buildList {
            addAll(listOf("--bind", "$LOOPBACK:$own"))
            if (psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY) {
                addAll(listOf("--protocol", protocol.core))
                // gool has meant WireGuard over MASQUE since aether 2.3.0; WARP-in-WARP is the classic gool, asked for
                // by name rather than left to the hop settings that select it as well.
                if (protocol == AetherProtocol.GOOL) add("--gool-classic")
                addAll(listOf("--scan", AetherScanMode.fromString(settings.scanMode).type))
                // Automatic obfuscation is the core's own choice per protocol, so nothing is said about it; MASQUE over
                // HTTP/2 takes none at all, since obfuscation shapes the UDP of WireGuard and HTTP/3 alone.
                val transport = AetherTransport.fromString(settings.transport)
                AetherObfuscation.fromString(settings.obfuscation)
                    .takeUnless { it == AetherObfuscation.AUTO || masqueOverHttp2(protocol, transport, tor, psiphon) }
                    ?.let { addAll(listOf("--noize", it.type)) }
                addAll(listOf("--ip", AetherIpVersion.fromString(settings.ipVersion).type))
                settingValue(settings.dns)?.let { addAll(listOf("--dns", it)) }
                // A scan keeps the exit rule as well, so that it ends on an endpoint the session will accept.
                settingValue(settings.exitLoc)?.let { addAll(listOf("--exit-loc", it)) }

                // The server name the MASQUE handshakes put in their ClientHello, on either carrier and both hops, the
                // default named as well, so that the command shows what is sent; the HTTP host stays the core's.
                if (protocol.overMasque) {
                    addAll(listOf("--masque-sni", settingValue(settings.masqueSni) ?: MASQUE_SNI))
                }
                if (protocol.overMasque && transport == AetherTransport.HTTP2) {
                    add("--h2")
                    // The ClientHello of the MASQUE handshake, and of the calls to the WARP API the core makes for a
                    // key it does not have; without the flag the core sends both whole.
                    if (settings.fragment == true) {
                        add("--fragment")
                        AetherRange.parse(settings.fragmentSize, AetherRange.FRAGMENT_SIZE)
                            ?.let { addAll(listOf("--fragment-size", it.toString())) }
                        AetherRange.parse(settings.fragmentDelay, AetherRange.FRAGMENT_DELAY)
                            ?.let { addAll(listOf("--fragment-delay", it.toString())) }
                    }
                }
                // Encrypted Client Hello hides the server name of the MASQUE handshake, on either carrier and both hops,
                // with the key of the HTTPS record of the ECH domain, asked of the ECH resolver.
                if (protocol.overMasque && settings.ech == true) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", settingValue(settings.echDns) ?: ECH_DNS))
                    addAll(listOf("--ech-domain", settingValue(settings.echDomain) ?: ECH_DOMAIN))
                }
                // The ClientHello of the MASQUE handshakes, and of the WARP API calls and the ECH key lookup the core makes:
                // over HTTP/3, which carries TLS 1.3 alone, all of it shows but its TLS 1.2 suites and the extensions only
                // TLS 1.2 uses.
                if (protocol.overMasque) addAll(AetherFingerprint.fromString(settings.fingerprint).arguments)

                if (protocol == AetherProtocol.WG_OVER_MASQUE) {
                    // The outer hop is a MASQUE gateway, which a scan looks for afresh. The inner one is the WireGuard
                    // endpoint dialled inside the tunnel, the one WARP assigned the key unless one is named; no scan looks
                    // for it, so a scan keeps the profile's own and finds a gateway that works with it.
                    AetherEndpoint.parse(settings.wiwOuter).takeUnless { scan }?.let { addAll(listOf("--peer", it.toString())) }
                    AetherEndpoint.parse(settings.wiwInner)?.let { addAll(listOf("--gool-peer", it.toString())) }
                } else if (protocol.twoHops) {
                    val hop = if (protocol == AetherProtocol.MIM) "--mim" else "--wiw"
                    val outer = AetherEndpoint.parse(settings.wiwOuter).takeUnless { scan }
                    val inner = AetherEndpoint.parse(settings.wiwInner).takeUnless { scan }
                    outer?.let { addAll(listOf("$hop-outer", it.toString())) }
                    inner?.let { addAll(listOf("$hop-inner", it.toString())) }
                    if (outer == null && inner == null) add("$hop-scan")
                } else if (!scan) {
                    AetherEndpoint.of(settings.server, settings.serverPort)?.let { addAll(listOf("--peer", it.toString())) }
                }

                add(if (scan) "--no-quick-reconnect" else "--quick-reconnect")
            }

            when (tor) {
                AetherTor.OFF -> Unit
                AetherTor.CHAIN -> add("--tor")
                AetherTor.REVERSE -> add("--tor-reverse")
                AetherTor.ONLY -> add("--tor-only")
            }
            torBind?.let { addAll(listOf(TOR_BIND, "$LOOPBACK:$it")) }
            if (tor != AetherTor.OFF) {
                // Told nothing, the core tries Tor plainly and turns to fetched bridges where Tor is blocked.
                val bridges = AetherTorBridges.fromString(settings.torBridges)
                when (bridges) {
                    AetherTorBridges.AUTO -> Unit
                    AetherTorBridges.FIRST -> add("--tor-bridges")
                    AetherTorBridges.NEVER -> add("--no-tor-bridges")
                    AetherTorBridges.OWN -> AetherFmt.bridgeLines(settings.torBridgeLines)
                        .mapNotNull { settingValue(it) }
                        .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                // Where fetched bridges come from; with the profile's own lines, or none at all, nothing is fetched.
                if (bridges == AetherTorBridges.AUTO || bridges == AetherTorBridges.FIRST) {
                    AetherTorRelays.fromString(settings.torRelays).takeUnless { it == AetherTorRelays.AUTO }
                        ?.let { addAll(listOf("--tor-relays", it.type)) }
                }
            }

            when (psiphon) {
                AetherPsiphon.OFF -> Unit
                AetherPsiphon.CHAIN -> add("--psiphon")
                AetherPsiphon.REVERSE -> add("--psiphon-reverse")
                AetherPsiphon.ONLY -> add("--psiphon-only")
            }
            psiphonBind?.let { addAll(listOf(PSIPHON_BIND, "$LOOPBACK:$it")) }
            if (psiphon != AetherPsiphon.OFF) {
                val shape = AetherPsiphonMode.fromString(settings.psiphonMode)
                addAll(listOf("--psiphon-mode", shape.type))
                // The CDN lists feed the fronted transports alone, which the direct shape never uses; the
                // server names count only beside an IP list of one's own, since the built-in list comes whole.
                val cdnIps = settingValue(settings.psiphonCdnIps)?.takeIf { shape != AetherPsiphonMode.DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                if (cdnIps != null) settingValue(settings.psiphonCdnSni)?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                // Which of the edge lists built into Psiphon the fronting scan tries; beside addresses of one's own, after them.
                if (shape != AetherPsiphonMode.DIRECT) {
                    AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(settings.psiphonCdnSets))?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                settingValue(settings.psiphonRegion)?.let { addAll(listOf("--psiphon-region", it)) }
                // The bundled list, unless the profile wants Psiphon to fetch a fresh one before it dials anything.
                if (settings.psiphonBundledList != false) addAll(listOf(PSIPHON_SERVER_ENTRIES, SHIPPED_LIST))
            }
            addAll(listOf("--log-level", logLevel))
        }
    }

    // ------------------------------------------------------- listener helpers

    /** The value after the last [flag] in [argv]; the last one is the one the core keeps. */
    fun valueAfter(argv: List<String>, flag: String): String? =
        argv.lastIndexOf(flag).takeIf { it >= 0 }?.let { argv.getOrNull(it + 1) }

    /** The port of the address after the listener option [flag], as the core reads it; null when there is none or it cannot be read. */
    fun portAfter(argv: List<String>, flag: String): Int? =
        listenerAddressAfter(argv, flag)?.substringAfterLast(':', "")?.toIntOrNull()

    /**
     * PattNG: the address after the listener option [flag] in [argv], as the core reads it: its own listener's (--bind) as
     * written, Tor's, Psiphon's and the HTTP proxies' trimmed (aether lib.rs, tor.rs and psiphon.rs); null when there is
     * none. Every reader of a listener's address or port goes by it, so that the app and the core agree on it.
     */
    fun listenerAddressAfter(argv: List<String>, flag: String): String? =
        valueAfter(argv, flag)?.let { if (flag == "--bind") it else it.trim(::isRustWhitespace) }

    /** The address of the listener the app dials, as the core reads it. */
    fun listenerAddressOf(argv: List<String>): String? = listenerAddressAfter(argv, listenerFlagOf(argv))

    /** The port of the listener the app dials, null when [argv] names none. */
    fun listenerPortOf(argv: List<String>): Int? =
        listenerAddressOf(argv)?.substringAfterLast(':', "")?.toIntOrNull()

    /**
     * The option naming the listener the app dials: Psiphon's when Psiphon runs inside the tunnel,
     * Tor's when Tor does, the core's own otherwise. Both inside at once is refused by the editor; a
     * command written that way is dialled on Psiphon's.
     */
    fun listenerFlagOf(arguments: List<String>): String = when {
        // Alone, Tor or Psiphon serves the core's own listener, and the core starts nothing else (aether lib.rs).
        aloneOf(arguments) != null -> "--bind"
        psiphonModeOf(arguments) == AetherPsiphon.CHAIN -> PSIPHON_BIND
        torModeOf(arguments) == AetherTor.CHAIN -> TOR_BIND
        else -> "--bind"
    }

    fun aloneOf(argv: List<String>): String? = when {
        torModeOf(argv) == AetherTor.ONLY -> TOR_NAME
        psiphonModeOf(argv) == AetherPsiphon.ONLY -> PSIPHON_NAME
        else -> null
    }

    fun torModeOf(argv: List<String>): AetherTor = argv.fold(AetherTor.OFF) { mode, word ->
        when (word) {
            "--tor" -> AetherTor.CHAIN
            "--tor-reverse" -> AetherTor.REVERSE
            "--tor-only" -> AetherTor.ONLY
            else -> mode
        }
    }

    fun psiphonModeOf(argv: List<String>): AetherPsiphon = argv.fold(AetherPsiphon.OFF) { mode, word ->
        when (word) {
            "--psiphon" -> AetherPsiphon.CHAIN
            "--psiphon-reverse" -> AetherPsiphon.REVERSE
            "--psiphon-only" -> AetherPsiphon.ONLY
            else -> mode
        }
    }

    fun protocolOf(argv: List<String>): AetherProtocol {
        var chosen: AetherProtocol? = null
        var classicMode = false
        // Each hop setting is a variable of the core's environment, so the last value given counts, a blank one included.
        val hops = HashMap<HopSetting, String>()
        for ((index, word) in argv.withIndex()) {
            val value = argv.getOrNull(index + 1)
            when (word) {
                "--protocol" -> chosen = value?.let(::protocolNamed) ?: chosen
                "--masque" -> chosen = AetherProtocol.MASQUE
                "--wg", "--wireguard", "--warp" -> chosen = AetherProtocol.WIREGUARD
                "--gool", "--wiw", "--gool-peer" -> chosen = AetherProtocol.WG_OVER_MASQUE
                "--gool-classic" -> {
                    chosen = AetherProtocol.WG_OVER_MASQUE
                    classicMode = true
                }
                "--mim", "--masque-in-masque" -> chosen = AetherProtocol.MIM
                "--wiw-outer", "--gool-outer", "--outer-peer" -> value?.let { hops[HopSetting.WIW_OUTER] = it }
                "--wiw-inner", "--gool-inner", "--inner-peer" -> value?.let { hops[HopSetting.WIW_INNER] = it }
                "--wiw-peers", "--gool-peers" -> value?.let { hops[HopSetting.WIW_PEERS] = it }
                "--wiw-scan", "--gool-scan" -> hops[HopSetting.WIW_PEERS] = "auto"
                "--mim-outer" -> value?.let { hops[HopSetting.MIM_OUTER] = it }
                "--mim-inner" -> value?.let { hops[HopSetting.MIM_INNER] = it }
                "--mim-peers" -> value?.let { hops[HopSetting.MIM_PEERS] = it }
                "--mim-scan" -> hops[HopSetting.MIM_PEERS] = "auto"
            }
        }
        // A blank value sets nothing for the core; a list of peers names hops unless it asks for a scan.
        fun given(setting: HopSetting) = hops[setting]?.trim()?.takeIf { it.isNotEmpty() }
        fun pinned(outer: HopSetting, inner: HopSetting, peers: HopSetting) =
            given(outer) != null || given(inner) != null || namesHops(given(peers))
        val classicGool = classicMode || given(HopSetting.WIW_OUTER) != null || given(HopSetting.WIW_INNER) != null || given(HopSetting.WIW_PEERS) != null
        return when (chosen) {
            AetherProtocol.WG_OVER_MASQUE -> if (classicGool) AetherProtocol.GOOL else AetherProtocol.WG_OVER_MASQUE
            null -> when {
                pinned(HopSetting.WIW_OUTER, HopSetting.WIW_INNER, HopSetting.WIW_PEERS) -> AetherProtocol.GOOL
                pinned(HopSetting.MIM_OUTER, HopSetting.MIM_INNER, HopSetting.MIM_PEERS) -> AetherProtocol.MIM
                else -> AetherProtocol.MASQUE
            }

            else -> chosen
        }
    }

    private fun protocolNamed(name: String): AetherProtocol = when (name.trim().lowercase(Locale.US)) {
        "wg", "wireguard" -> AetherProtocol.WIREGUARD
        "gool", "wiw", "warp-in-warp", "warpinwarp" -> AetherProtocol.WG_OVER_MASQUE
        "mim", "m2", "masque-in-masque", "masqueinmasque" -> AetherProtocol.MIM
        else -> AetherProtocol.MASQUE
    }

    private fun namesHops(value: String?): Boolean = value != null && value.lowercase(Locale.US) !in scanKeywords

    fun isSocketAddress(address: String): Boolean {
        val colon = address.lastIndexOf(':')
        if (colon <= 0 || !isDecimalUpTo(address.substring(colon + 1), 65_535)) return false
        val host = address.substring(0, colon)
        return if (host.startsWith('[') && host.endsWith(']')) isIpv6Literal(host.substring(1, host.length - 1)) else isIpv4Literal(host)
    }

    /**
     * PattNG: whether a listener bound on [host], an IPv4 address, or an IPv6 one in brackets, as [isSocketAddress] takes
     * it, answers a connection to 127.0.0.1, where the app dials the core: 127.0.0.1 itself and every IPv4 address,
     * 0.0.0.0; and, since Android, as Linux, opens an IPv6 socket to IPv4 as well unless told otherwise, which the core
     * never does (tokio and mio set no IPV6_V6ONLY), every address of IPv6, ::, and the IPv4-mapped spellings of those two.
     */
    fun reachesLoopback(host: String): Boolean {
        if (!host.startsWith('[')) return host == LOOPBACK || host == "0.0.0.0"
        val groups = ipv6Groups(host.removePrefix("[").removeSuffix("]").substringBefore('%')) ?: return false
        if (groups.all { it == 0 }) return true
        val mapped = (0..4).all { groups[it] == 0 } && groups[5] == 0xFFFF
        return mapped && ((groups[6] == 0x7F00 && groups[7] == 0x0001) || (groups[6] == 0 && groups[7] == 0))
    }

    fun isIpv4Literal(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } && (part == "0" || !part.startsWith('0')) &&
                part.toInt() <= 255
        }
    }

    fun isIpv6Literal(text: String): Boolean {
        val address = text.substringBefore('%')
        if ('%' in text && !isDecimalUpTo(text.substringAfter('%'), 4_294_967_295)) return false
        val halves = address.split("::")
        if (halves.size > 2) return false
        val head = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
        val tail = if (halves.size < 2 || halves[1].isEmpty()) emptyList() else halves[1].split(':')
        val groups = head + tail
        var count = 0
        for ((index, group) in groups.withIndex()) {
            count += when {
                '.' in group -> if (index == groups.lastIndex && isIpv4Literal(group) && (halves.size == 1 || tail.isNotEmpty())) 2 else return false
                group.isNotEmpty() && group.length <= 4 && group.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> 1
                else -> return false
            }
        }
        return if (halves.size == 2) count <= 7 else count == 8
    }

    fun ipv6Groups(text: String): IntArray? {
        if ('%' in text || !isIpv6Literal(text)) return null
        fun groupsOf(part: String): List<Int> = if (part.isEmpty()) emptyList() else part.split(':').flatMap { group ->
            if ('.' in group) {
                val octets = group.split('.').map { it.toInt() }
                listOf((octets[0] shl 8) or octets[1], (octets[2] shl 8) or octets[3])
            } else {
                listOf(group.toInt(16))
            }
        }
        val halves = text.split("::")
        val head = groupsOf(halves[0])
        val tail = if (halves.size == 2) groupsOf(halves[1]) else emptyList()
        val zeros = if (halves.size == 2) 8 - head.size - tail.size else 0
        return (head + List(zeros) { 0 } + tail).toIntArray()
    }

    private fun isDecimalUpTo(text: String, max: Long): Boolean {
        if (text.isEmpty() || !text.all { it in '0'..'9' }) return false
        val significant = text.trimStart('0')
        return significant.length <= 10 && (significant.isEmpty() || significant.toLong() <= max)
    }

    /**
     * PattNG: whitespace as the core trims it, Rust's char::is_whitespace, the Unicode White_Space property. Kotlin's
     * trim() takes U+001C to U+001F as well and leaves U+0085 out, so an address it trims could be one the core does not.
     */
    fun isRustWhitespace(c: Char): Boolean =
        c in '\u0009'..'\u000D' || c == ' ' || c == '\u0085' || c == '\u00A0' || c == '\u1680' || c in '\u2000'..'\u200A' ||
            c == '\u2028' || c == '\u2029' || c == '\u202F' || c == '\u205F' || c == '\u3000'

    /** [arguments] without every [flag] and the value after it. */
    fun withoutOption(arguments: List<String>, flag: String): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            if (arguments[index] == flag) index += 2 else kept.add(arguments[index++])
        }
        return kept
    }

    fun withListener(arguments: List<String>, flag: String, port: Int): List<String> =
        withoutOption(arguments, flag) + listOf(flag, "$LOOPBACK:$port")

    fun tunnelArguments(arguments: List<String>): List<String> =
        listOf("--log-level", "--bind", TOR_BIND, PSIPHON_BIND, UPSTREAM).fold(arguments, ::withoutOption)

    /** Whether what the app sends through the tunnel [argv] runs leaves it through WARP, the last part of [pathOf]. */
    fun leavesThroughWarp(argv: List<String>): Boolean = leavesThroughWarp(torModeOf(argv), psiphonModeOf(argv))

    /** [leavesThroughWarp] with Tor standing at [tor] and Psiphon at [psiphon]. */
    fun leavesThroughWarp(tor: AetherTor, psiphon: AetherPsiphon): Boolean =
        (tor == AetherTor.OFF || tor == AetherTor.REVERSE) && (psiphon == AetherPsiphon.OFF || psiphon == AetherPsiphon.REVERSE)

    fun pathOf(argv: List<String>, label: (AetherProtocol) -> String = AetherProtocol::name): List<String> {
        val tor = torModeOf(argv)
        val psiphon = psiphonModeOf(argv)
        if (tor == AetherTor.ONLY) return listOf(TOR_NAME)
        if (psiphon == AetherPsiphon.ONLY) return listOf(PSIPHON_NAME)
        return buildList {
            if (tor == AetherTor.REVERSE) add(TOR_NAME)
            if (psiphon == AetherPsiphon.REVERSE) add(PSIPHON_NAME)
            add(label(protocolOf(argv)))
            if (psiphon == AetherPsiphon.CHAIN) add(PSIPHON_NAME)
            if (tor == AetherTor.CHAIN) add(TOR_NAME)
        }
    }

    /** The first port that is no port, of the port the app dials or of any port the core holds; null when all are ports. */
    fun invalidPort(core: AetherCore): Int? =
        core.port.takeIf { it !in 1..65535 } ?: core.ports.firstOrNull { it !in 1..65535 }
}
