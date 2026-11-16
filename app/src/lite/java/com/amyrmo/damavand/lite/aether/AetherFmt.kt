package com.amyrmo.damavand.lite.aether

import java.util.Locale

/**
 * The one piece of PattNG's AetherFmt that Lite's argument builder needs
 * (3.2.10): reading Tor bridge lines the way the core reads a bridge file.
 *
 * 3.2.11 (PattNG full parity): the validators the WARP keys page checks
 * its settings with — the ECH DNS and domain and the MASQUE server name
 * the core takes.
 */
object AetherFmt {

    // ---- aether:// share links (3.2.13, PattNG AetherFmt.parse verbatim) ----

    /**
     * Parses an `aether://` share link the way PattNG's AetherFmt.parse
     * does, into the settings and the name it carries (the #fragment,
     * or "Aether"). Every query key PattNG reads is read here — protocol,
     * transport, sni, scan, noize, ip, fragment (+ size/delay), ech (+
     * dns/domain), fingerprint, dns, exit_loc, psiphon (+ mode, cdn_ips,
     * cdn_sni, cdn_sets, region, psiphon_bundled), tor (+ bridges,
     * tor_bridges, tor_relays), fm, dialMode, and the outer/inner
     * endpoints of a two-hop protocol or the server of a one-hop one.
     * Null when the link is not an aether:// link at all, or when any
     * part of it cannot be read — so one bad line never stops a whole
     * subscription import.
     */
    fun parse(link: String): Pair<AetherSettings, String>? {
        return try {
            val uri = java.net.URI(fixIllegalUrl(link.trim()))
            if (uri.scheme?.lowercase(java.util.Locale.ROOT) != "aether") return null
            val query = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
            val protocol = AetherProtocol.fromString(query["protocol"])
            val name = decodeURIComponent(uri.fragment.orEmpty()).ifEmpty { "Aether" }
            val ech = query["ech"] == "1"
            val base = AetherSettings(
                protocol = protocol.type,
                transport = AetherTransport.fromString(query["transport"]).type,
                masqueSni = query["sni"]?.trim()?.takeIf { protocol.overMasque && isMasqueSni(it) },
                scanMode = AetherScanMode.fromString(query["scan"]).type,
                obfuscation = AetherObfuscation.fromString(query["noize"]).type,
                ipVersion = AetherIpVersion.fromString(query["ip"]).type,
                fragment = query["fragment"] == "1",
                fragmentSize = AetherRange.parse(query["fragment_size"], AetherRange.FRAGMENT_SIZE)?.toString(),
                fragmentDelay = AetherRange.parse(query["fragment_delay"], AetherRange.FRAGMENT_DELAY)?.toString(),
                ech = ech,
                echDns = query["ech_dns"]?.trim()?.takeIf { ech && isEchDns(it) },
                echDomain = query["ech_domain"]?.trim()?.takeIf { ech && isEchDomain(it) },
                fingerprint = query["fingerprint"]?.let { AetherFingerprint.fromString(it).type },
                dns = query["dns"],
                exitLoc = query["exit_loc"],
                psiphon = AetherPsiphon.fromString(query["psiphon"]).type
                    .takeUnless { it == AetherPsiphon.OFF.type },
                psiphonMode = query["psiphon_mode"]?.let { AetherPsiphonMode.fromString(it).type },
                psiphonCdnIps = query["cdn_ips"],
                psiphonCdnSni = query["cdn_sni"],
                psiphonCdnSets = query["cdn_sets"],
                psiphonRegion = query["region"],
                psiphonBundledList = if (query["psiphon_bundled"] == "0") false else null,
                tor = AetherTor.fromString(query["tor"]).type
                    .takeUnless { it == AetherTor.OFF.type },
                torBridges = query["tor_bridges"]?.let { AetherTorBridges.fromString(it).type },
                torBridgeLines = query["bridges"]?.split(';')?.joinToString("\n"),
                torRelays = query["tor_relays"]?.let { AetherTorRelays.fromString(it).type },
                finalMask = query["fm"],
                dialMode = query["dialMode"],
            )
            val settings = if (protocol.twoHops) {
                val outer = AetherEndpoint.parse(query["outer"])
                val inner = AetherEndpoint.parse(query["inner"])
                    ?.takeUnless { protocol.distinctHops && it.host == outer?.host }
                base.copy(wiwOuter = outer?.toString(), wiwInner = inner?.toString())
            } else {
                val endpoint = AetherEndpoint.of(uri.idnHost, uri.port.takeIf { it > 0 }?.toString())
                base.copy(server = endpoint?.host, serverPort = endpoint?.port?.toString())
            }
            settings to name
        } catch (_: Exception) {
            null
        }
    }

    /** PattNG Utils.fixIllegalUrl: the two characters a pasted link may carry raw. */
    private fun fixIllegalUrl(str: String): String =
        str.replace(" ", "%20").replace("|", "%7C")

    /** PattNG Utils.decodeURIComponent: '+' is a literal plus, never a space. */
    private fun decodeURIComponent(url: String): String = try {
        java.net.URLDecoder.decode(url.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        url
    }

    /** PattNG FmtBase.getQueryParam: the raw query, split and decoded. */
    private fun getQueryParam(uri: java.net.URI): Map<String, String> =
        uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to decodeURIComponent(v) } }

    /** PattNG's URI.idnHost: the host with its brackets taken off. */
    private val java.net.URI.idnHost: String
        get() = host?.replace("[", "")?.replace("]", "").orEmpty()

    /**
     * The bridge lines in [text], one per line the way torrc writes them, read as the core reads a
     * bridge file: blank lines and comments dropped, a leading Bridge keyword taken off.
     */
    fun bridgeLines(text: String?): List<String> =
        text.orEmpty().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("Bridge ").removePrefix("bridge ").trim() }
            .filter { it.isNotEmpty() }

    /**
     * Whether [value] names a resolver the core asks for the ECH key, as its --ech-dns takes it: udp:// or tcp:// and
     * an IP address, on port 53 unless one is given, or the https:// URL of a DNS-over-HTTPS server, on port 443
     * unless it names one, with @address= and @sni= after it if need be, see [isDohEndpoint].
     */
    fun isEchDns(value: String): Boolean {
        // Quotes would not come back from the command line the editor shows, which is split into words.
        if (value.any { it.isWhitespace() || it == '"' || it == '\'' }) return false
        val scheme = value.substringBefore("://", "").lowercase(Locale.ROOT)
        val rest = value.substringAfter("://", "")
        return when (scheme) {
            "https" -> isDohEndpoint(rest)
            "udp", "tcp" -> rest.trimEnd('/').let { address ->
                // As the core reads an address: ASCII digits only, and brackets around an IPv6 address alone.
                address.all { it.code < 0x80 } &&
                    !(address.startsWith('[') && ':' !in address.substringBefore(']')) &&
                    (AetherEndpoint.parse(address) ?: AetherEndpoint.of(address, "53")) != null
            }
            else -> false
        }
    }

    /**
     * Whether [rest], what follows https://, names a DNS-over-HTTPS server as the core reads one: a URL with a host,
     * then @address= an IP address or a domain name, where the connection goes on the URL's port, and @sni= a domain
     * name, which the ClientHello names, each at most once and in either order. Left out, the connection goes to the
     * URL's host and the ClientHello names it, or names nothing when it is an IP address.
     */
    private fun isDohEndpoint(rest: String): Boolean {
        val pieces = rest.split('@')
        if (pieces.first().takeWhile { it !in "/?#" }.isEmpty()) return false
        val named = mutableSetOf<String>()
        for (piece in pieces.drop(1)) {
            if ('=' !in piece) return false
            val name = piece.substringBefore('=').lowercase(Locale.ROOT)
            val setting = piece.substringAfter('=')
            val isAddress = AetherEndpoint.of(setting, "443") != null
            val fits = when (name) {
                "address" -> isAddress || isEchDomain(setting)
                "sni" -> !isAddress && isEchDomain(setting)
                else -> false
            }
            if (!fits || !named.add(name)) return false
        }
        return true
    }

    /**
     * Whether [value] is a domain whose HTTPS record can be asked for, as the core's --ech-domain takes it. A label
     * cannot start or end with '-' either, so that no value reads as an option of the core's command line.
     */
    fun isEchDomain(value: String): Boolean {
        val name = value.removeSuffix(".")
        return name.isNotEmpty() && name.length <= 253 && name.split('.').all { echDomainLabel.matches(it) }
    }

    private val echDomainLabel = Regex("[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?")

    /**
     * Whether [value] is a server name the core's --masque-sni takes, as do its --api-sni and --api-host, which it checks
     * as it starts: a domain name, with a trailing dot or without, and no IP address, see
     * [isNetipAddress], which tells one as the core's Rust parser does for every text a domain name
     * could be. A label cannot start or end with '-' either, as for [isEchDomain], which the core would take.
     */
    fun isMasqueSni(value: String): Boolean {
        val name = value.removeSuffix(".")
        return !name.endsWith('.') && !isNetipAddress(name) && isEchDomain(name)
    }

    /** Whether [value] is an IP address literal, as the core's Rust parser reads one. */
    private fun isNetipAddress(value: String): Boolean =
        AetherArgs.isIpv4Literal(value) || AetherArgs.isIpv6Literal(value)
}
