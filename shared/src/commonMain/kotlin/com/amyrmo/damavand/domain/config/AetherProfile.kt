package com.amyrmo.damavand.domain.config

import kotlinx.serialization.Serializable

/**
 * One adjustable Aether profile — field-for-field PattNG's Aether profile
 * (ProfileItem's aether* columns), so links, forms and the core agree.
 */
@Serializable
data class AetherProfile(
    val remarks: String = "",
    /** AetherProtocol.type */
    val protocol: String = AetherProtocol.WIREGUARD.type,
    /** AetherTransport.type (MASQUE only) */
    val transport: String = AetherTransport.HTTP3.type,
    /** AetherScanMode.type */
    val scanMode: String = AetherScanMode.BALANCED.type,
    /** AetherObfuscation.type */
    val obfuscation: String = AetherObfuscation.AUTO.type,
    /** AetherIpVersion.type */
    val ipVersion: String = AetherIpVersion.V4.type,
    /** custom DNS of the tunnel (blank = core default) */
    val dns: String = "",
    /** exit-node location (blank = core default) */
    val exitLoc: String = "",
    /** MASQUE ClientHello SNI (blank = default) */
    val masqueSni: String = "",
    /** fragment the MASQUE-over-HTTP/2 ClientHello */
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    /** Encrypted Client Hello on the MASQUE handshakes */
    val ech: Boolean = false,
    val echDns: String = "",
    val echDomain: String = "",
    /** AetherFingerprint.type (over-MASQUE only) */
    val fingerprint: String = AetherFingerprint.CHROME.type,
    /** outer/inner hop endpoints of the two-hop protocols (blank = scan) */
    val wiwOuter: String = "",
    val wiwInner: String = "",
    /** single-endpoint protocols: host and port (blank = scan) */
    val address: String = "",
    val port: String = "",
    /** AetherPsiphon.type */
    val psiphon: String = AetherPsiphon.OFF.type,
    /** AetherPsiphonMode.type */
    val psiphonMode: String = AetherPsiphonMode.AUTO.type,
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: String = "",
    val psiphonRegion: String = "",
    /** false = Psiphon fetches a fresh server list before dialing */
    val psiphonBundledList: Boolean = true,
    /** AetherTor.type */
    val tor: String = AetherTor.OFF.type,
    /** AetherTorBridges.type */
    val torBridges: String = AetherTorBridges.AUTO.type,
    /** AetherTorRelays.type */
    val torRelays: String = AetherTorRelays.AUTO.type,
    /** one bridge line per line */
    val torBridgeLines: String = "",
    /** the exit-node's finalMask JSON and dialMode (newer cores) */
    val finalMask: String = "",
    val dialMode: String = "",
    /** extra raw flags appended verbatim (advanced) */
    val extraArgs: String = "",
) {
    /** Whether the core should scan instead of dialing a named endpoint. */
    val scanning: Boolean
        get() = if (AetherProtocol.fromString(protocol).twoHops) {
            wiwOuter.isBlank() || wiwInner.isBlank()
        } else {
            address.isBlank()
        }

    /**
     * The core's arguments, following PattNG's AetherCoreManager.buildArguments
     * flag-for-flag (minus the multi-port loopback plumbing, which Damavand's
     * single --bind bridge replaces).
     */
    fun toFlags(): List<String> {
        val proto = AetherProtocol.fromString(protocol)
        val transportEnum = AetherTransport.fromString(transport)
        val torEnum = AetherTor.fromString(tor)
        val psiphonEnum = AetherPsiphon.fromString(psiphon)
        return buildList {
            if (psiphonEnum != AetherPsiphon.ONLY && torEnum != AetherTor.ONLY) {
                addAll(listOf("--protocol", proto.core))
                if (proto == AetherProtocol.GOOL) add("--gool-classic")
                addAll(listOf("--scan", AetherScanMode.fromString(scanMode).type))
                val overHttp2 = masqueOverHttp2(proto, transportEnum, torEnum, psiphonEnum)
                AetherObfuscation.fromString(obfuscation)
                    .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
                    ?.let { addAll(listOf("--noize", it.type)) }
                addAll(listOf("--ip", AetherIpVersion.fromString(ipVersion).type))
                dns.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--dns", it)) }
                exitLoc.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--exit-loc", it)) }

                if (proto.overMasque) {
                    addAll(listOf("--masque-sni", masqueSni.trim().ifEmpty { AETHER_MASQUE_SNI }))
                }
                if (proto.overMasque && transportEnum == AetherTransport.HTTP2) {
                    add("--h2")
                    if (fragment) {
                        add("--fragment")
                        AetherRange.parse(fragmentSize, AetherRange.FRAGMENT_SIZE)
                            ?.let { addAll(listOf("--fragment-size", it.toString())) }
                        AetherRange.parse(fragmentDelay, AetherRange.FRAGMENT_DELAY)
                            ?.let { addAll(listOf("--fragment-delay", it.toString())) }
                    }
                }
                if (proto.overMasque && ech) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", echDns.trim().ifEmpty { AETHER_ECH_DNS }))
                    addAll(listOf("--ech-domain", echDomain.trim().ifEmpty { AETHER_ECH_DOMAIN }))
                }
                if (proto.overMasque) addAll(AetherFingerprint.fromString(fingerprint).arguments)

                if (proto == AetherProtocol.WG_OVER_MASQUE) {
                    AetherEndpoint.parse(wiwOuter).takeUnless { scanning }?.let { addAll(listOf("--peer", it.toString())) }
                    AetherEndpoint.parse(wiwInner)?.let { addAll(listOf("--gool-peer", it.toString())) }
                } else if (proto.twoHops) {
                    val hop = if (proto == AetherProtocol.MIM) "--mim" else "--wiw"
                    val outer = AetherEndpoint.parse(wiwOuter).takeUnless { scanning }
                    val inner = AetherEndpoint.parse(wiwInner).takeUnless { scanning }
                    outer?.let { addAll(listOf("$hop-outer", it.toString())) }
                    inner?.let { addAll(listOf("$hop-inner", it.toString())) }
                    if (outer == null && inner == null) add("$hop-scan")
                } else if (!scanning) {
                    AetherEndpoint.of(address, port)?.let { addAll(listOf("--peer", it.toString())) }
                }

                add(if (scanning) "--no-quick-reconnect" else "--quick-reconnect")
            }

            when (torEnum) {
                AetherTor.OFF -> {}
                AetherTor.CHAIN -> add("--tor")
                AetherTor.REVERSE -> add("--tor-reverse")
                AetherTor.ONLY -> add("--tor-only")
            }
            if (torEnum != AetherTor.OFF) {
                when (AetherTorBridges.fromString(torBridges)) {
                    AetherTorBridges.AUTO -> {}
                    AetherTorBridges.FIRST -> add("--tor-bridges")
                    AetherTorBridges.NEVER -> add("--no-tor-bridges")
                    AetherTorBridges.OWN ->
                        torBridgeLines.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                val bridges = AetherTorBridges.fromString(torBridges)
                if (bridges == AetherTorBridges.AUTO || bridges == AetherTorBridges.FIRST) {
                    AetherTorRelays.fromString(torRelays).takeUnless { it == AetherTorRelays.AUTO }
                        ?.let { addAll(listOf("--tor-relays", it.type)) }
                }
            }

            when (psiphonEnum) {
                AetherPsiphon.OFF -> {}
                AetherPsiphon.CHAIN -> add("--psiphon")
                AetherPsiphon.REVERSE -> add("--psiphon-reverse")
                AetherPsiphon.ONLY -> add("--psiphon-only")
            }
            if (psiphonEnum != AetherPsiphon.OFF) {
                val shape = AetherPsiphonMode.fromString(psiphonMode)
                addAll(listOf("--psiphon-mode", shape.type))
                val cdnIps = psiphonCdnIps.trim().takeIf { it.isNotEmpty() && shape != AetherPsiphonMode.DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                if (cdnIps != null) psiphonCdnSni.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                if (shape != AetherPsiphonMode.DIRECT) {
                    AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(psiphonCdnSets))
                        ?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                psiphonRegion.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--psiphon-region", it)) }
            }
            extraArgs.trim().takeIf { it.isNotEmpty() }?.let { addAll(it.split(Regex("\\s+"))) }
        }
    }

    /** The "Aether command" preview line, like PattNG shows. */
    fun commandPreview(bindPort: Int): String =
        (listOf("aether", "--bind", "127.0.0.1:$bindPort") + toFlags() + listOf("--log-level", "warn")).joinToString(" ")

    /** PattNG-style `aether://host:port?query#label` share link (AetherFmt.toUri). */
    fun toShareLink(): String {
        val proto = AetherProtocol.fromString(protocol)
        val transportEnum = AetherTransport.fromString(transport)
        val torEnum = AetherTor.fromString(tor)
        val psiphonEnum = AetherPsiphon.fromString(psiphon)
        val query = linkedMapOf<String, String>(
            "protocol" to proto.type,
            "scan" to AetherScanMode.fromString(scanMode).type,
        )
        val overHttp2 = masqueOverHttp2(proto, transportEnum, torEnum, psiphonEnum)
        AetherObfuscation.fromString(obfuscation)
            .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
            ?.let { query["noize"] = it.type }
        query["ip"] = AetherIpVersion.fromString(ipVersion).type
        dns.trim().takeIf { it.isNotEmpty() }?.let { query["dns"] = it }
        exitLoc.trim().takeIf { it.isNotEmpty() }?.let { query["exit_loc"] = it }
        if (proto.overMasque) {
            query["transport"] = transportEnum.type
            masqueSni.trim().takeIf { it != AETHER_MASQUE_SNI && it.isNotEmpty() }?.let { query["sni"] = it }
            if (fragment) {
                query["fragment"] = "1"
                AetherRange.parse(fragmentSize, AetherRange.FRAGMENT_SIZE)?.let { query["fragment_size"] = it.toString() }
                AetherRange.parse(fragmentDelay, AetherRange.FRAGMENT_DELAY)?.let { query["fragment_delay"] = it.toString() }
            }
            val warpUsed = psiphonEnum != AetherPsiphon.ONLY && torEnum != AetherTor.ONLY
            if (ech && warpUsed) {
                query["ech"] = "1"
                echDns.trim().takeIf { it.isNotEmpty() }?.let { query["ech_dns"] = it }
                echDomain.trim().takeIf { it.isNotEmpty() }?.let { query["ech_domain"] = it }
            }
            AetherFingerprint.fromString(fingerprint)
                .takeUnless { it == AetherFingerprint.CHROME }
                ?.let { query["fingerprint"] = it.type }
        }
        if (proto.twoHops) {
            AetherEndpoint.parse(wiwOuter)?.let { query["outer"] = it.toString() }
            AetherEndpoint.parse(wiwInner)?.let { query["inner"] = it.toString() }
        }
        if (psiphonEnum != AetherPsiphon.OFF) {
            query["psiphon"] = psiphonEnum.type
            query["psiphon_mode"] = AetherPsiphonMode.fromString(psiphonMode).type
            psiphonCdnIps.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_ips"] = it }
            psiphonCdnSni.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_sni"] = it }
            psiphonCdnSets.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_sets"] = it }
            psiphonRegion.trim().takeIf { it.isNotEmpty() }?.let { query["region"] = it }
            if (!psiphonBundledList) query["psiphon_bundled"] = "0"
        }
        if (torEnum != AetherTor.OFF) {
            query["tor"] = torEnum.type
            query["tor_bridges"] = AetherTorBridges.fromString(torBridges).type
            query["tor_relays"] = AetherTorRelays.fromString(torRelays).type
            val lines = torBridgeLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) query["bridges"] = lines.joinToString(";")
        }
        finalMask.trim().takeIf { it.isNotEmpty() }?.let { query["fm"] = it }
        dialMode.trim().takeIf { it.isNotEmpty() }?.let { query["dialMode"] = it }

        val endpoint = if (!proto.twoHops) {
            AetherEndpoint.of(address, port)?.let { "aether://" + it.toString() } ?: "aether://"
        } else {
            "aether://"
        }
        val queryText = query.entries.joinToString("&") { "${it.key}=${uriEncode(it.value)}" }
        val label = remarks.takeIf { it.isNotBlank() }?.let { "#" + uriEncode(it) } ?: ""
        return "$endpoint?$queryText$label"
    }

    companion object {
        /** Accepts a pasted raw `aether --flag value ...` command line. */
        fun fromCommandLine(text: String): AetherProfile {
            val parts = text.trim().split(Regex("\\s+"))
            var cfg = AetherProfile()
            val extras = mutableListOf<String>()
            var i = 0
            fun pick(n: Int): String? = parts.getOrNull(n + 1)?.takeIf { !it.startsWith("--") }
            while (i < parts.size) {
                val p = parts[i]
                when (p) {
                    "--protocol" -> pick(i)?.let { cfg = cfg.copy(protocol = AetherProtocol.fromString(it).type); i++ }
                    "--masque" -> cfg = cfg.copy(protocol = AetherProtocol.MASQUE.type)
                    "--wg", "--wireguard", "--warp" -> cfg = cfg.copy(protocol = AetherProtocol.WIREGUARD.type)
                    "--gool-classic", "--wiw" -> cfg = cfg.copy(protocol = AetherProtocol.GOOL.type)
                    "--gool" -> cfg = cfg.copy(protocol = AetherProtocol.WG_OVER_MASQUE.type)
                    "--mim", "--masque-in-masque" -> cfg = cfg.copy(protocol = AetherProtocol.MIM.type)
                    "--h2", "--http2" -> cfg = cfg.copy(transport = AetherTransport.HTTP2.type)
                    "--h3", "--quic" -> cfg = cfg.copy(transport = AetherTransport.HTTP3.type)
                    "--scan" -> pick(i)?.let { cfg = cfg.copy(scanMode = AetherScanMode.fromString(it).type); i++ }
                    "--noize" -> pick(i)?.let { cfg = cfg.copy(obfuscation = AetherObfuscation.fromString(it).type); i++ }
                    "--ip" -> pick(i)?.let { cfg = cfg.copy(ipVersion = AetherIpVersion.fromString(it).type); i++ }
                    "-4" -> cfg = cfg.copy(ipVersion = AetherIpVersion.V4.type)
                    "-6" -> cfg = cfg.copy(ipVersion = AetherIpVersion.V6.type)
                    "--dual" -> cfg = cfg.copy(ipVersion = AetherIpVersion.DUAL.type)
                    "--dns" -> pick(i)?.let { cfg = cfg.copy(dns = it); i++ }
                    "--exit-loc" -> pick(i)?.let { cfg = cfg.copy(exitLoc = it); i++ }
                    "--masque-sni" -> pick(i)?.let { cfg = cfg.copy(masqueSni = it); i++ }
                    "--fragment" -> cfg = cfg.copy(fragment = true)
                    "--fragment-size" -> pick(i)?.let { cfg = cfg.copy(fragmentSize = it); i++ }
                    "--fragment-delay" -> pick(i)?.let { cfg = cfg.copy(fragmentDelay = it); i++ }
                    "--ech" -> cfg = cfg.copy(ech = true)
                    "--ech-dns" -> pick(i)?.let { cfg = cfg.copy(echDns = it); i++ }
                    "--ech-domain" -> pick(i)?.let { cfg = cfg.copy(echDomain = it); i++ }
                    "--peer", "--endpoint" -> pick(i)?.let {
                        val ep = AetherEndpoint.parse(it)
                        if (ep != null) cfg = cfg.copy(address = ep.host, port = ep.port.toString())
                        i++
                    }
                    "--wiw-outer", "--mim-outer" -> pick(i)?.let { cfg = cfg.copy(wiwOuter = it); i++ }
                    "--wiw-inner", "--mim-inner", "--gool-peer" -> pick(i)?.let { cfg = cfg.copy(wiwInner = it); i++ }
                    "--psiphon" -> cfg = cfg.copy(psiphon = AetherPsiphon.CHAIN.type)
                    "--psiphon-reverse" -> cfg = cfg.copy(psiphon = AetherPsiphon.REVERSE.type)
                    "--psiphon-only" -> cfg = cfg.copy(psiphon = AetherPsiphon.ONLY.type)
                    "--psiphon-mode" -> pick(i)?.let { cfg = cfg.copy(psiphonMode = AetherPsiphonMode.fromString(it).type); i++ }
                    "--psiphon-cdn-ips" -> pick(i)?.let { cfg = cfg.copy(psiphonCdnIps = it); i++ }
                    "--psiphon-cdn-sni" -> pick(i)?.let { cfg = cfg.copy(psiphonCdnSni = it); i++ }
                    "--psiphon-cdn-sets" -> pick(i)?.let { cfg = cfg.copy(psiphonCdnSets = it); i++ }
                    "--psiphon-region" -> pick(i)?.let { cfg = cfg.copy(psiphonRegion = it); i++ }
                    "--tor" -> cfg = cfg.copy(tor = AetherTor.CHAIN.type)
                    "--tor-reverse" -> cfg = cfg.copy(tor = AetherTor.REVERSE.type)
                    "--tor-only" -> cfg = cfg.copy(tor = AetherTor.ONLY.type)
                    "--tor-bridges" -> cfg = cfg.copy(torBridges = AetherTorBridges.FIRST.type)
                    "--no-tor-bridges" -> cfg = cfg.copy(torBridges = AetherTorBridges.NEVER.type)
                    "--tor-bridge" -> pick(i)?.let {
                        cfg = cfg.copy(torBridges = AetherTorBridges.OWN.type, torBridgeLines = (cfg.torBridgeLines + "\n" + it).trim())
                        i++
                    }
                    "--tor-relays" -> pick(i)?.let { cfg = cfg.copy(torRelays = AetherTorRelays.fromString(it).type); i++ }
                    "--register" -> { i++ } // a key-registration command carries no session flags
                    else -> if (p.startsWith("--")) extras += p
                }
                i++
            }
            if (extras.isNotEmpty()) cfg = cfg.copy(extraArgs = extras.joinToString(" "))
            return cfg
        }

        /** PattNG-style `aether://host:port?query#label` link (AetherFmt.parse). */
        fun fromShareLink(link: String): AetherProfile? {
            val trimmed = link.trim()
            if (!trimmed.startsWith("aether://")) return null
            val rest = trimmed.removePrefix("aether://")
            val hostPart: String
            val queryPart: String
            val qIdx = rest.indexOf('?')
            if (qIdx >= 0) {
                hostPart = rest.substring(0, qIdx)
                queryPart = rest.substring(qIdx + 1)
            } else {
                hostPart = rest
                queryPart = ""
            }
            val query: String
            val label: String
            val hIdx = queryPart.indexOf('#')
            if (hIdx >= 0) {
                query = queryPart.substring(0, hIdx)
                label = uriDecode(queryPart.substring(hIdx + 1))
            } else {
                query = queryPart
                label = ""
            }
            val q = buildMap {
                for (pair in query.split('&')) {
                    if (pair.isBlank()) continue
                    val idx = pair.indexOf('=')
                    val key = if (idx >= 0) pair.substring(0, idx) else pair
                    val value = if (idx >= 0) uriDecode(pair.substring(idx + 1)) else ""
                    put(key, value)
                }
            }
            val proto = AetherProtocol.fromString(q["protocol"])
            var cfg = AetherProfile(
                remarks = label.ifBlank { "Aether" },
                protocol = proto.type,
                transport = AetherTransport.fromString(q["transport"]).type,
                masqueSni = q["sni"]?.trim().orEmpty(),
                scanMode = AetherScanMode.fromString(q["scan"]).type,
                obfuscation = AetherObfuscation.fromString(q["noize"]).type,
                ipVersion = AetherIpVersion.fromString(q["ip"]).type,
                fragment = q["fragment"] == "1",
                fragmentSize = q["fragment_size"].orEmpty(),
                fragmentDelay = q["fragment_delay"].orEmpty(),
                ech = q["ech"] == "1",
                echDns = q["ech_dns"]?.trim().orEmpty(),
                echDomain = q["ech_domain"]?.trim().orEmpty(),
                fingerprint = q["fingerprint"]?.let { AetherFingerprint.fromString(it).type }
                    ?: AetherFingerprint.CHROME.type,
                dns = q["dns"].orEmpty(),
                exitLoc = q["exit_loc"].orEmpty(),
                psiphon = AetherPsiphon.fromString(q["psiphon"]).type,
                psiphonMode = q["psiphon_mode"]?.let { AetherPsiphonMode.fromString(it).type }
                    ?: AetherPsiphonMode.AUTO.type,
                psiphonCdnIps = q["cdn_ips"].orEmpty(),
                psiphonCdnSni = q["cdn_sni"].orEmpty(),
                psiphonCdnSets = q["cdn_sets"].orEmpty(),
                psiphonRegion = q["region"].orEmpty(),
                psiphonBundledList = q["psiphon_bundled"] != "0",
                tor = AetherTor.fromString(q["tor"]).type,
                torBridges = q["tor_bridges"]?.let { AetherTorBridges.fromString(it).type }
                    ?: AetherTorBridges.AUTO.type,
                torRelays = q["tor_relays"]?.let { AetherTorRelays.fromString(it).type }
                    ?: AetherTorRelays.AUTO.type,
                torBridgeLines = q["bridges"]?.split(';')?.joinToString("\n").orEmpty(),
                finalMask = q["fm"].orEmpty(),
                dialMode = q["dialMode"].orEmpty(),
            )
            if (proto.twoHops) {
                cfg = cfg.copy(wiwOuter = q["outer"].orEmpty(), wiwInner = q["inner"].orEmpty())
            } else if (hostPart.isNotBlank()) {
                val endpoint = AetherEndpoint.parse(hostPart.removePrefix("aether://"))
                if (endpoint != null) {
                    cfg = cfg.copy(address = endpoint.host, port = endpoint.port.toString())
                }
            }
            return cfg
        }

        private fun uriEncode(v: String): String =
            buildString {
                for (b in v.encodeToByteArray()) {
                    val c = b.toInt() and 0xff
                    if (c in '0'.code..'9'.code || c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code ||
                        c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code || c == ','.code ||
                        c == ';'.code || c == ':'.code || c == '['.code || c == ']'.code || c == '@'.code
                    ) {
                        append(c.toChar())
                    } else {
                        append('%')
                        append(c.toString(16).uppercase().padStart(2, '0'))
                    }
                }
            }

        private fun uriDecode(v: String): String {
            val bytes = ArrayList<Byte>(v.length)
            var i = 0
            while (i < v.length) {
                val c = v[i]
                if (c == '%' && i + 2 < v.length) {
                    val b = v.substring(i + 1, i + 3).toIntOrNull(16)
                    if (b != null) {
                        bytes.add(b.toByte())
                        i += 3
                        continue
                    }
                }
                bytes.add(c.code.toByte())
                i++
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
        }
    }
}
