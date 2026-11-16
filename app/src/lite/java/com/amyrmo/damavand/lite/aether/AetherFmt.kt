package com.amyrmo.damavand.lite.aether

/**
 * The one piece of PattNG's AetherFmt that Lite's argument builder needs
 * (3.2.10): reading Tor bridge lines the way the core reads a bridge file.
 */
object AetherFmt {

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
}
