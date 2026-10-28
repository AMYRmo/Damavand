package com.amyrmo.damavand.domain.config

import kotlinx.serialization.Serializable

/**
 * One adjustable Aether profile — architecture mirrors PattNG's Aether screen.
 * Every field maps 1:1 onto a flag of the patterniha/Aether core binary.
 * Lives in shared so profiles, links and the app-side engine all speak it.
 */
@Serializable
data class AetherProfile(
    val remarks: String = "",
    /** masque | wg | gool | mim */
    val protocol: String = "masque",
    /** turbo | balanced | thorough | verified | ironclad */
    val scanMode: String = "balanced",
    /** off | light | firewall | balanced | gfw | aggressive */
    val noize: String = "",
    /** v4 | v6 | both */
    val ipVersion: String = "v4",
    /** off | chain | reverse | only */
    val psiphon: String = "off",
    /** auto | cdn | direct */
    val psiphonMode: String = "auto",
    /** comma list of built-in fronting sets (cloudflare, fastly, ...) */
    val psiphonCdnSets: String = "",
    /** psiphon exit country code, e.g. US */
    val psiphonRegion: String = "",
    /** off | chain | reverse | only */
    val tor: String = "off",
    /** custom endpoint ip:port (blank = scan) */
    val address: String = "",
    val port: String = "",
    /** MASQUE transport: blank(auto) | h2 | h3 */
    val masqueTransport: String = "",
    /** exit-node location spec, e.g. freedom / !IR,DE (blank = core default) */
    val exitNode: String = "",
    /** raw finalMask JSON for the exit node (optional, newer cores) */
    val exitMaskJson: String = "",
    /** dial mode for the exit node (optional) */
    val exitDialMode: String = "",
    /** extra raw flags appended verbatim (advanced) */
    val extraArgs: String = "",
) {
    /** Flatten into core command-line flags (excluding --bind/--log-level/--psiphon-bin). */
    fun toFlags(): List<String> {
        val flags = mutableListOf<String>()
        flags += when (protocol) {
            "wg" -> "--wg"
            "gool" -> "--gool"
            "mim" -> "--mim"
            else -> "--masque"
        }
        if (scanMode != "balanced") flags += listOf("--scan", scanMode)
        when (noize) {
            "off" -> flags += listOf("--noize", "off")
            "light", "firewall", "balanced", "gfw", "aggressive" -> flags += listOf("--noize", noize)
            else -> {}
        }
        when (ipVersion) {
            "v6" -> flags += "-6"
            "both" -> flags += "--dual"
            else -> flags += "-4"
        }
        if (address.isNotBlank()) {
            val endpoint = if (port.isNotBlank()) "$address:$port" else address
            flags += listOf("--peer", endpoint)
        }
        when (masqueTransport) {
            "h2" -> flags += "--h2"
            "h3" -> flags += "--h3"
            else -> {}
        }
        when (psiphon) {
            "chain" -> flags += "--psiphon"
            "reverse" -> flags += "--psiphon-reverse"
            "only" -> flags += "--psiphon-only"
            else -> {}
        }
        if (psiphon != "off") {
            if (psiphonMode == "cdn") flags += listOf("--psiphon-mode", "cdn")
            if (psiphonMode == "direct") flags += listOf("--psiphon-mode", "direct")
            if (psiphonCdnSets.isNotBlank()) flags += listOf("--psiphon-cdn-sets", psiphonCdnSets)
            if (psiphonRegion.isNotBlank()) flags += listOf("--psiphon-region", psiphonRegion)
        }
        when (tor) {
            "chain" -> flags += "--tor"
            "reverse" -> flags += "--tor-reverse"
            "only" -> flags += "--tor-only"
            else -> {}
        }
        if (exitNode.isNotBlank()) flags += listOf("--exit-loc", exitNode)
        // finalMask/dialMode are newer-core fields; v2.4.0 does not know them,
        // so they travel in the profile/link but are not passed to this core.
        extraArgs.trim().takeIf { it.isNotEmpty() }?.let { flags += it.split(Regex("\\s+")) }
        return flags
    }

    /** The "Aether command" preview line, like PattNG shows. */
    fun commandPreview(bindPort: Int): String =
        (listOf("aether", "--bind", "127.0.0.1:$bindPort") + toFlags()).joinToString(" ")

    /** PattNG-style `aether://host:port?query` share link. */
    fun toShareLink(): String {
        val hostPart = if (address.isNotBlank()) {
            if (port.isNotBlank()) "$address:$port" else address
        } else {
            "scan"
        }
        val q = buildList {
            add("protocol=$protocol")
            add("scan=$scanMode")
            if (noize.isNotBlank()) add("noize=$noize")
            add("ip=$ipVersion")
            if (psiphon != "off") {
                add("psiphon=$psiphon")
                add("psiphon_mode=$psiphonMode")
                if (psiphonCdnSets.isNotBlank()) add("cdn_sets=${urlEncode(psiphonCdnSets)}")
                if (psiphonRegion.isNotBlank()) add("region=${urlEncode(psiphonRegion)}")
            }
            if (tor != "off") add("tor=$tor")
            if (exitNode.isNotBlank()) add("exit=${urlEncode(exitNode)}")
            if (exitMaskJson.isNotBlank()) add("final_mask=${urlEncode(exitMaskJson)}")
            if (exitDialMode.isNotBlank()) add("dial=${urlEncode(exitDialMode)}")
        }.joinToString("&")
        val label = remarks.takeIf { it.isNotBlank() }?.let { "#" + urlEncode(it) } ?: ""
        return "aether://$hostPart?$q$label"
    }

    companion object {
        private fun urlEncode(v: String): String =
            buildString {
                for (b in v.encodeToByteArray()) {
                    val c = b.toInt() and 0xff
                    if (c in '0'.code..'9'.code || c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code ||
                        c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code || c == ','.code
                    ) {
                        append(c.toChar())
                    } else {
                        append('%')
                        append(c.toString(16).uppercase().padStart(2, '0'))
                    }
                }
            }

        private fun urlDecode(v: String): String {
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
                    "--protocol" -> pick(i)?.let { cfg = cfg.copy(protocol = normalizeProtocol(it)); i++ }
                    "--masque" -> cfg = cfg.copy(protocol = "masque")
                    "--wg", "--wireguard", "--warp" -> cfg = cfg.copy(protocol = "wg")
                    "--gool", "--wiw" -> cfg = cfg.copy(protocol = "gool")
                    "--mim", "--masque-in-masque" -> cfg = cfg.copy(protocol = "mim")
                    "--scan" -> pick(i)?.let { cfg = cfg.copy(scanMode = it); i++ }
                    "--turbo" -> cfg = cfg.copy(scanMode = "turbo")
                    "--balanced" -> cfg = cfg.copy(scanMode = "balanced")
                    "--thorough" -> cfg = cfg.copy(scanMode = "thorough")
                    "--verified" -> cfg = cfg.copy(scanMode = "verified")
                    "--ironclad" -> cfg = cfg.copy(scanMode = "ironclad")
                    "--noize" -> pick(i)?.let { cfg = cfg.copy(noize = it); i++ }
                    "-4", "--ip" -> { if (p == "--ip") { pick(i)?.let { cfg = cfg.copy(ipVersion = normalizeIp(it)) ; i++ } } else cfg = cfg.copy(ipVersion = "v4") }
                    "-6" -> cfg = cfg.copy(ipVersion = "v6")
                    "--dual" -> cfg = cfg.copy(ipVersion = "both")
                    "--peer", "--endpoint" -> pick(i)?.let {
                        cfg = cfg.copy(
                            address = it.substringBeforeLast(':'),
                            port = it.substringAfterLast(':', ""),
                        )
                        i++
                    }
                    "--h2", "--http2" -> cfg = cfg.copy(masqueTransport = "h2")
                    "--h3", "--quic" -> cfg = cfg.copy(masqueTransport = "h3")
                    "--psiphon" -> cfg = cfg.copy(psiphon = "chain")
                    "--psiphon-reverse" -> cfg = cfg.copy(psiphon = "reverse")
                    "--psiphon-only" -> cfg = cfg.copy(psiphon = "only")
                    "--psiphon-mode" -> pick(i)?.let { cfg = cfg.copy(psiphonMode = it); i++ }
                    "--psiphon-cdn-sets" -> pick(i)?.let { cfg = cfg.copy(psiphonCdnSets = it); i++ }
                    "--psiphon-region" -> pick(i)?.let { cfg = cfg.copy(psiphonRegion = it); i++ }
                    "--tor" -> cfg = cfg.copy(tor = "chain")
                    "--tor-reverse" -> cfg = cfg.copy(tor = "reverse")
                    "--tor-only" -> cfg = cfg.copy(tor = "only")
                    "--exit-loc" -> pick(i)?.let { cfg = cfg.copy(exitNode = it); i++ }
                    "--final-mask" -> pick(i)?.let { cfg = cfg.copy(exitMaskJson = it); i++ }
                    "--dial-mode" -> pick(i)?.let { cfg = cfg.copy(exitDialMode = it); i++ }
                    "--bind", "--log-level", "--psiphon-bin", "--config", "--quick-reconnect", "--no-quick-reconnect" -> i++
                    else -> if (p.startsWith("--")) extras += p
                }
                i++
            }
            if (extras.isNotEmpty()) cfg = cfg.copy(extraArgs = extras.joinToString(" "))
            return cfg
        }

        /** PattNG-style `aether://host:port?query#label` link. */
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
                label = urlDecode(queryPart.substring(hIdx + 1))
            } else {
                query = queryPart
                label = ""
            }
            var cfg = AetherProfile(remarks = label)
            for (pair in query.split("&")) {
                if (pair.isBlank()) continue
                val idx = pair.indexOf('=')
                val key = if (idx >= 0) pair.substring(0, idx) else pair
                val value = if (idx >= 0) urlDecode(pair.substring(idx + 1)) else ""
                cfg = when (key) {
                    "protocol" -> cfg.copy(protocol = normalizeProtocol(value))
                    "scan" -> cfg.copy(scanMode = value.ifBlank { "balanced" })
                    "noize" -> cfg.copy(noize = value)
                    "obfuscation" -> cfg.copy(noize = value)
                    "ip" -> cfg.copy(ipVersion = normalizeIp(value))
                    "psiphon" -> cfg.copy(psiphon = value.ifBlank { "off" })
                    "psiphon_mode" -> cfg.copy(psiphonMode = value.ifBlank { "auto" })
                    "cdn_sets" -> cfg.copy(psiphonCdnSets = value)
                    "region" -> cfg.copy(psiphonRegion = value)
                    "tor" -> cfg.copy(tor = value.ifBlank { "off" })
                    "exit" -> cfg.copy(exitNode = value)
                    "final_mask" -> cfg.copy(exitMaskJson = value)
                    "dial" -> cfg.copy(exitDialMode = value)
                    else -> cfg
                }
            }
            if (hostPart != "scan" && hostPart.isNotBlank()) {
                cfg = cfg.copy(
                    address = hostPart.substringBeforeLast(':'),
                    port = hostPart.substringAfterLast(':', ""),
                )
            }
            return cfg
        }

        private fun normalizeProtocol(v: String): String = when (v.lowercase()) {
            "wg", "wireguard", "warp" -> "wg"
            "gool", "wiw", "warp-in-warp" -> "gool"
            "mim", "masque-in-masque" -> "mim"
            "masque" -> "masque"
            else -> v.lowercase().ifBlank { "masque" }
        }

        private fun normalizeIp(v: String): String = when (v.lowercase()) {
            "v4", "4", "ipv4" -> "v4"
            "v6", "6", "ipv6" -> "v6"
            "both", "dual" -> "both"
            else -> "v4"
        }
    }
}
