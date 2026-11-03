package com.amyrmo.damavand.model

/**
 * The `aether://host:port?query#label` share-link codec (PattNG's
 * AetherFmt), ported from v1's `AetherProfile.toShareLink/fromShareLink`.
 * Split out of the profile in v2: the model is data only; codecs live
 * beside it. Round-trip tested, including the two historical traps:
 * a `#label` with no `?` query (the label used to leak into the endpoint
 * and both were lost) and hostname endpoints (never canonicalised through
 * an IP grammar — see [AetherEndpoint]).
 */
object AetherLink {

    /** The core's flag naming the file of server entries the Psiphon client starts with. */
    const val PSIPHON_SERVER_ENTRIES = "--psiphon-server-entries"

    /** The word a command names the app's own list by; the real file takes its place when the core starts. */
    const val SHIPPED_LIST = "shipped-list"

    fun toShareLink(profile: AetherProfile): String {
        val proto = AetherProtocol.fromString(profile.protocol)
        val transportEnum = AetherTransport.fromString(profile.transport)
        val torEnum = AetherTor.fromString(profile.tor)
        val psiphonEnum = AetherPsiphon.fromString(profile.psiphon)
        val query = linkedMapOf<String, String>(
            "protocol" to proto.type,
            "scan" to AetherScanMode.fromString(profile.scanMode).type,
        )
        val overHttp2 = masqueOverHttp2(proto, transportEnum, torEnum, psiphonEnum)
        AetherObfuscation.fromString(profile.obfuscation)
            .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
            ?.let { query["noize"] = it.type }
        query["ip"] = AetherIpVersion.fromString(profile.ipVersion).type
        profile.dns.trim().takeIf { it.isNotEmpty() }?.let { query["dns"] = it }
        profile.exitLoc.trim().takeIf { it.isNotEmpty() }?.let { query["exit_loc"] = it }
        if (proto.overMasque) {
            query["transport"] = transportEnum.type
            profile.masqueSni.trim().takeIf { it != AETHER_MASQUE_SNI && it.isNotEmpty() }?.let { query["sni"] = it }
            if (profile.fragment) {
                query["fragment"] = "1"
                AetherRange.parse(profile.fragmentSize, AetherRange.FRAGMENT_SIZE)?.let { query["fragment_size"] = it.toString() }
                AetherRange.parse(profile.fragmentDelay, AetherRange.FRAGMENT_DELAY)?.let { query["fragment_delay"] = it.toString() }
            }
            val warpUsed = psiphonEnum != AetherPsiphon.ONLY && torEnum != AetherTor.ONLY
            if (profile.ech && warpUsed) {
                query["ech"] = "1"
                profile.echDns.trim().takeIf { it.isNotEmpty() }?.let { query["ech_dns"] = it }
                profile.echDomain.trim().takeIf { it.isNotEmpty() }?.let { query["ech_domain"] = it }
            }
            AetherFingerprint.fromString(profile.fingerprint)
                .takeUnless { it == AetherFingerprint.CHROME }
                ?.let { query["fingerprint"] = it.type }
        }
        if (proto.twoHops) {
            AetherEndpoint.parse(profile.wiwOuter)?.let { query["outer"] = it.toString() }
            AetherEndpoint.parse(profile.wiwInner)?.let { query["inner"] = it.toString() }
        }
        if (psiphonEnum != AetherPsiphon.OFF) {
            query["psiphon"] = psiphonEnum.type
            query["psiphon_mode"] = AetherPsiphonMode.fromString(profile.psiphonMode).type
            profile.psiphonCdnIps.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_ips"] = it }
            profile.psiphonCdnSni.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_sni"] = it }
            profile.psiphonCdnSets.trim().takeIf { it.isNotEmpty() }?.let { query["cdn_sets"] = it }
            profile.psiphonRegion.trim().takeIf { it.isNotEmpty() }?.let { query["region"] = it }
            if (!profile.psiphonBundledList) query["psiphon_bundled"] = "0"
        }
        if (torEnum != AetherTor.OFF) {
            query["tor"] = torEnum.type
            query["tor_bridges"] = AetherTorBridges.fromString(profile.torBridges).type
            query["tor_relays"] = AetherTorRelays.fromString(profile.torRelays).type
            val lines = profile.torBridgeLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) query["bridges"] = lines.joinToString(";")
        }
        profile.finalMask.trim().takeIf { it.isNotEmpty() }?.let { query["fm"] = it }
        profile.dialMode.trim().takeIf { it.isNotEmpty() }?.let { query["dialMode"] = it }
        profile.command.trim().takeIf { it.isNotEmpty() }?.let { query["cmd"] = it }

        val endpoint = if (!proto.twoHops) {
            AetherEndpoint.of(profile.address, profile.port)?.let { "aether://" + it.toString() } ?: "aether://"
        } else {
            "aether://"
        }
        val queryText = query.entries.joinToString("&") { "${it.key}=${uriEncode(it.value)}" }
        val label = profile.name.takeIf { it.isNotBlank() }?.let { "#" + uriEncode(it) } ?: ""
        return "$endpoint?$queryText$label"
    }

    fun fromShareLink(link: String, id: String, subscriptionId: String? = null): AetherProfile? {
        val trimmed = link.trim()
        if (!trimmed.startsWith("aether://")) return null
        val rest = trimmed.removePrefix("aether://")
        // The label is the URI fragment — everything after the first '#',
        // whether or not a query precedes it (PattNG takes remarks from
        // uri.fragment). Taking it off first keeps it out of the endpoint
        // when the link has no query: aether://host:port#Name used to leave
        // '#Name' inside the host part, losing the label and the endpoint.
        val hIdx = rest.indexOf('#')
        val label = if (hIdx >= 0) uriDecode(rest.substring(hIdx + 1)) else ""
        val beforeFragment = if (hIdx >= 0) rest.substring(0, hIdx) else rest
        val qIdx = beforeFragment.indexOf('?')
        val hostPart = if (qIdx >= 0) beforeFragment.substring(0, qIdx) else beforeFragment
        val query = if (qIdx >= 0) beforeFragment.substring(qIdx + 1) else ""
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
            id = id,
            name = label.ifBlank { "Aether" },
            subscriptionId = subscriptionId,
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
            command = q["cmd"].orEmpty(),
        )
        if (proto.twoHops) {
            cfg = cfg.copy(wiwOuter = q["outer"].orEmpty(), wiwInner = q["inner"].orEmpty())
        } else if (hostPart.isNotBlank()) {
            val endpoint = AetherEndpoint.parse(hostPart)
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
