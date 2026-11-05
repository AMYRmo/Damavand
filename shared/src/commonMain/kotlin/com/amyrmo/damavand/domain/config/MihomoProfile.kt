package com.amyrmo.damavand.domain.config

import kotlinx.serialization.Serializable

/**
 * A mihomo (Clash.Meta / FlClash core) profile: raw YAML kept verbatim, with a
 * remarks label. The engine writes the YAML into its work directory, starts
 * libmihomo with it, and bridges the mixed-port listener into the TUN.
 */
@Serializable
data class MihomoProfile(
    val remarks: String = "",
    /** The Clash YAML document (proxies, proxy-groups, rules). */
    val yaml: String = "",
    /** Mixed (HTTP+SOCKS) listen port on 127.0.0.1; 0 = engine picks one. */
    val mixedPort: Int = 0,
    /** Override log-level: silent/error/warning/info/debug */
    val logLevel: String = "warning",
    /** Clash subscription URL; when set and [yaml] blank the engine builds a
     *  provider-based config pulling proxies from this URL. */
    val subUrl: String = "",
) {
    companion object {
        /** Free public mihomo subscription used when adding Mihomo with nothing filled. */
        const val DEFAULT_SUB = "https://raw.githubusercontent.com/iampedii/whitedns-sub/refs/heads/main/mihomo.yaml"

        /** True when the text looks like a Clash/mihomo YAML document. */
        fun looksLikeMihomo(text: String): Boolean {
            val t = text.trim()
            if (!t.startsWith("port:") && !t.startsWith("mixed-port:") && !t.startsWith("proxies:") &&
                !t.startsWith("proxy-groups:") && !t.startsWith("rules:") && !t.startsWith("---")
            ) {
                return false
            }
            return t.contains("proxies") || t.contains("proxy-groups") || t.contains("rules")
        }
    }
}
