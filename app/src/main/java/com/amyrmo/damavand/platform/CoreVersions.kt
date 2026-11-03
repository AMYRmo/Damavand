package com.amyrmo.damavand.platform

/**
 * The core versions baked into the build, for the Settings → Cores page.
 * These are the pins the fetch scripts under `tools/` resolve
 * (`tools/cores.txt` and the `*_TAG` defaults in the get-* scripts); they
 * are compile-time facts of the APK, not runtime probes.
 */
data class CoreVersion(val name: String, val version: String, val source: String)

object CoreVersions {
    val all: List<CoreVersion> = listOf(
        CoreVersion("Xray / sing-box / Psiphon (zedcore)", "zedcore-r2", "CluvexStudio/ZedSecure-Cores"),
        CoreVersion("Aether", "v2.4.0", "patterniha/Aether"),
        CoreVersion("Mihomo", "v1.19.33", "MetaCubeX/mihomo"),
        CoreVersion("CottenDNS (Whitedns engine)", "77bf87db", "WhiteDNS/CottenDNS"),
    )
}
