package com.amyrmo.damavand.domain.config

import kotlinx.serialization.Serializable

/**
 * One adjustable Aether profile — mirrors PattNG's Aether config screen.
 * Every field maps 1:1 onto a flag of the patterniha/Aether core binary.
 * Lives in shared so profiles, links and the app-side engine all speak it.
 */
@Serializable
data class AetherProfile(
    val remarks: String = "",
    /** wg | masque | mim | gool | tls | quic | hybrid */
    val protocol: String = "wg",
    /** off | balanced | thorough | turbo | perf */
    val scanMode: String = "balanced",
    /** automatic | none | ironclad | noize | http */
    val obfuscation: String = "automatic",
    /** freedom (default) or an exit-node location */
    val exitNode: String = "freedom",
    /** raw FinalMaskObject JSON for the exit node (optional) */
    val exitMaskJson: String = "",
    /** dial mode for the exit node (v4 / v6 / auto) */
    val exitDialMode: String = "",
    /** off | on | cdn | frontend | only | region code */
    val psiphon: String = "off",
    val psiphonRegion: String = "",
    /** off | on | bridges-only */
    val tor: String = "off",
    /** custom endpoint (blank = WARP defaults) */
    val address: String = "",
    val port: String = "",
    /** v4 | v6 | dual */
    val ipVersion: String = "v4",
    /** extra raw flags appended verbatim (advanced) */
    val extraArgs: String = "",
) {
    /** Flatten into core command-line flags (excluding --bind/--log-level/--psiphon-bin). */
    fun toFlags(): List<String> {
        val flags = mutableListOf<String>()
        flags += listOf("--protocol", protocol)
        if (scanMode != "off") flags += listOf("--scan", scanMode)
        when (obfuscation) {
            "ironclad" -> flags += "--ironclad"
            "noize" -> flags += "--noize"
            "http" -> flags += "--http"
            else -> {}
        }
        if (exitNode.isNotBlank() && exitNode != "freedom") {
            flags += listOf("--exit-loc", exitNode)
            if (exitDialMode.isNotBlank()) flags += listOf("--dial-mode", exitDialMode)
        }
        if (exitMaskJson.isNotBlank()) flags += listOf("--final-mask", exitMaskJson)
        if (address.isNotBlank()) {
            val endpoint = if (port.isNotBlank()) "$address:$port" else address
            flags += listOf("--endpoint", endpoint)
        }
        when (ipVersion) {
            "v4" -> flags += listOf("--ip", "v4")
            "v6" -> flags += listOf("--ip", "v6")
            "dual" -> flags += listOf("--ip", "dual")
            else -> {}
        }
        when (psiphon) {
            "on", "auto" -> flags += "--psiphon"
            "cdn" -> flags += listOf("--psiphon", "cdn")
            "frontend" -> flags += listOf("--psiphon", "fronted")
            "only" -> flags += "--psiphon-only"
            "", "off" -> {}
            else -> flags += listOf("--psiphon-region", psiphon)
        }
        if (psiphonRegion.isNotBlank()) flags += listOf("--psiphon-region", psiphonRegion)
        if (tor == "on") flags += "--tor"
        if (tor == "bridges-only") flags += "--tor-only"
        extraArgs.trim().takeIf { it.isNotEmpty() }?.let { flags += it.split(Regex("\\s+")) }
        return flags
    }

    /** The "Aether command" preview line, like PattNG shows. */
    fun commandPreview(bindPort: Int): String =
        (listOf("aether", "--bind", "127.0.0.1:$bindPort") + toFlags()).joinToString(" ")

    companion object {
        /** Accepts a pasted raw `aether --flag value ...` command line. */
        fun fromCommandLine(text: String): AetherProfile {
            val parts = text.trim().split(Regex("\\s+"))
            var cfg = AetherProfile()
            var i = 0
            val extras = mutableListOf<String>()
            fun pick(n: Int): String? = parts.getOrNull(n + 1)?.takeIf { !it.startsWith("--") }
            while (i < parts.size) {
                val p = parts[i]
                when (p) {
                    "--protocol" -> pick(i)?.let { cfg = cfg.copy(protocol = it); i++ }
                    "--scan" -> pick(i)?.let { cfg = cfg.copy(scanMode = it); i++ }
                    "--ironclad" -> cfg = cfg.copy(obfuscation = "ironclad")
                    "--noize" -> cfg = cfg.copy(obfuscation = "noize")
                    "--http" -> cfg = cfg.copy(obfuscation = "http")
                    "--exit-loc" -> pick(i)?.let { cfg = cfg.copy(exitNode = it); i++ }
                    "--dial-mode" -> pick(i)?.let { cfg = cfg.copy(exitDialMode = it); i++ }
                    "--final-mask" -> pick(i)?.let { cfg = cfg.copy(exitMaskJson = it); i++ }
                    "--endpoint" -> pick(i)?.let {
                        cfg = cfg.copy(
                            address = it.substringBeforeLast(':'),
                            port = it.substringAfterLast(':', ""),
                        )
                        i++
                    }
                    "--ip" -> pick(i)?.let { cfg = cfg.copy(ipVersion = it.removePrefix("v")); i++ }
                    "--psiphon-only" -> cfg = cfg.copy(psiphon = "only")
                    "--psiphon" -> {
                        val nxt = pick(i)
                        cfg = if (nxt != null) {
                            i++
                            cfg.copy(psiphon = nxt)
                        } else {
                            cfg.copy(psiphon = "on")
                        }
                    }
                    "--psiphon-region" -> pick(i)?.let { cfg = cfg.copy(psiphonRegion = it); i++ }
                    "--tor" -> cfg = cfg.copy(tor = "on")
                    "--tor-only" -> cfg = cfg.copy(tor = "bridges-only")
                    "--bind", "--log-level", "--psiphon-bin", "--config" -> i++
                    else -> if (p.startsWith("--")) extras += p
                }
                i++
            }
            if (extras.isNotEmpty()) cfg = cfg.copy(extraArgs = extras.joinToString(" "))
            return cfg
        }
    }
}
