package com.amyrmo.damavand.core.tor

import com.amyrmo.damavand.platform.httpJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Fresh Tor bridges from the Tor Project's Moat API (BPB port:
 * lib/services/tor_bridges.dart). Asks for the transports Damavand can use
 * from Moat for country "ir"; the bundled bridges_default.lst stays as the
 * offline fallback when a fetch fails.
 */
object MoatBridges {
    private const val ENDPOINT = "https://bridges.torproject.org/moat/circumvention/settings"
    private const val COUNTRY = "ir"

    val TRANSPORTS = listOf("webtunnel", "obfs4", "snowflake")

    /**
     * Returns fresh bridge lines (no leading "Bridge "), deduplicated.
     * Throws on any network or response error so the UI can show a clear
     * failure state and keep the bundled list.
     */
    suspend fun fetch(socksPort: Int?): List<String> {
        val body = buildJsonObject {
            put("country", COUNTRY)
            putJsonArray("transports") { TRANSPORTS.forEach { add(it) } }
        }
        val response = httpJson(
            "POST",
            ENDPOINT,
            mapOf("Content-Type" to "application/vnd.api+json"),
            body.toString(),
            socksPort = socksPort,
        )
        if (response.code !in 200..299) error("Moat answered HTTP ${response.code}")
        val root = runCatching { Json.parseToJsonElement(response.body) }.getOrNull() as? JsonObject
            ?: error("Moat returned an unreadable response")
        val settings = root["settings"] as? JsonArray ?: error("Moat response has no settings")
        val lines = mutableListOf<String>()
        for (entry in settings) {
            val bridges = (entry as? JsonObject)?.get("bridges") as? JsonObject ?: continue
            val strings = bridges["bridge_strings"] as? JsonArray ?: continue
            for (item in strings) {
                val raw = runCatching { item.jsonPrimitive.content }.getOrNull() ?: continue
                val line = raw.trim().removePrefix("Bridge ").trim()
                if (TRANSPORTS.any { line.startsWith("$it ") }) lines += line
            }
        }
        return lines.distinct().also {
            if (it.isEmpty()) error("Moat returned no bridges for $COUNTRY")
        }
    }
}
