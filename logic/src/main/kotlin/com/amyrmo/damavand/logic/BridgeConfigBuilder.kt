package com.amyrmo.damavand.logic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The sing-box bridge (architecture §2.4 / §3.4): the component that owns
 * the TUN in every session shape except standalone Xray and standalone full
 * sing-box configs. Ported from v1's SingBoxConfigs (aetherBridge +
 * prepareForDevice); v1's desktop variant is not ported (Android-only, D1).
 *
 * Behaviour preserved verbatim from v1 where it was load-bearing:
 *  - the advertised local port IS the port in the emitted `mixed` inbound
 *    (the build-#75 phantom-port lesson, §2.4 rule 2) — [bridge] returns
 *    the port it actually used;
 *  - the DNS section is the typed format sing-box 1.14+ requires
 *    (`type: "https"`, server + path split), detoured through the exit
 *    hop's outbound; the profile-DNS string is parsed exactly like v1
 *    (https URL → host+path, bare host → server, unparseable → 1.1.1.1).
 */
object BridgeConfigBuilder {

    /** v1's loopback SOCKS port for standalone sing-box configs. */
    const val STANDALONE_SOCKS_PORT = 2080

    /** Tag of the bridge's single proxy outbound (the exit hop). */
    const val EXIT_TAG = "proxy"

    /** Tag of the bridge DNS server. */
    const val DNS_TAG = "bridge-dns"

    data class DeviceOptions(
        val tunAddress: String = "172.19.0.1/30",
        val mtu: Int = 1400,
        val stack: String = "gvisor",
        val autoRoute: Boolean = true,
        val strictRoute: Boolean = false,
    )

    /** What a [bridge] call actually produced. */
    data class BridgeResult(
        /** The bridge config JSON (pretty-printed, deterministic). */
        val json: String,
        /** The local `mixed` inbound port — guaranteed to be in [json]. */
        val localPort: Int,
    )

    /** Parsed profile DNS choice (shared with the planner, §4.6). */
    data class BridgeDnsChoice(val host: String, val path: String)

    /**
     * v1's profile-DNS parse, unchanged: an `https://host/path` URL splits
     * into server + path; a bare host/IP is the server with the default
     * path; anything else falls back to Cloudflare.
     */
    fun dnsChoiceOf(dns: String): BridgeDnsChoice {
        val trimmed = dns.trim()
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            val rest = trimmed.removePrefix("https://").removePrefix("HTTPS://")
            val slash = rest.indexOf('/')
            val host = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash) else "/dns-query"
            if (host.isNotBlank()) return BridgeDnsChoice(host, path.ifBlank { "/dns-query" })
        }
        val host = trimmed.substringBefore('/').substringBefore(':')
        return if (host.isNotBlank() && !trimmed.contains("://")) {
            BridgeDnsChoice(host, "/dns-query")
        } else {
            BridgeDnsChoice("1.1.1.1", "/dns-query")
        }
    }

    /**
     * The bridge config: TUN inbound (unless [tun] is false — proxy-only
     * mode) + loopback `mixed` inbound on [localPort] + one `socks`
     * outbound to the exit hop's listener on [exitSocksPort] + the typed
     * DoH DNS section detoured through that outbound.
     *
     * [extraRouteRules] are inserted before the final route rule (the
     * planner uses this for custom routing that must outrank the catch-all).
     */
    fun bridge(
        exitSocksPort: Int,
        localPort: Int,
        device: DeviceOptions = DeviceOptions(),
        dns: String = "",
        ipv6: Boolean = true,
        tun: Boolean = true,
        extraRouteRules: List<JsonObject> = emptyList(),
    ): BridgeResult {
        val choice = dnsChoiceOf(dns)
        val root = buildJsonObject {
            put("log", buildJsonObject { put("level", "warn"); put("timestamp", true) })
            put("dns", buildJsonObject {
                putJsonArrayOfObjects("servers") {
                    add(buildJsonObject {
                        put("tag", DNS_TAG)
                        put("type", "https")
                        put("server", choice.host)
                        put("path", choice.path)
                        put("detour", EXIT_TAG)
                    })
                }
                put("final", DNS_TAG)
                put("strategy", if (ipv6) "prefer_ipv4" else "ipv4_only")
                put("independent_cache", true)
            })
            putJsonArrayOfObjects("inbounds") {
                if (tun) {
                    add(buildJsonObject {
                        put("type", "tun")
                        put("tag", "tun-in")
                        put("address", buildJsonArray { add(JsonPrimitive(device.tunAddress)) })
                        put("mtu", device.mtu)
                        put("auto_route", device.autoRoute)
                        put("strict_route", device.strictRoute)
                        put("stack", device.stack)
                        put("sniff", true)
                        put("sniff_override_destination", true)
                    })
                }
                // The loopback listener the prepared shape advertises (§2.4
                // rule 2): the plan's local port is THIS port.
                add(buildJsonObject {
                    put("type", "mixed")
                    put("tag", "mixed-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", localPort)
                    put("sniff", true)
                    put("sniff_override_destination", true)
                })
            }
            putJsonArrayOfObjects("outbounds") {
                add(buildJsonObject {
                    put("type", "socks")
                    put("tag", EXIT_TAG)
                    put("version", "5")
                    put("server", "127.0.0.1")
                    put("server_port", exitSocksPort)
                    put("network", "tcp")
                })
                add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
            }
            put("route", buildJsonObject {
                putJsonArrayOfObjects("rules") {
                    // The bridge itself must reach the exit hop directly.
                    add(buildJsonObject {
                        put("port", buildJsonArray { add(JsonPrimitive(exitSocksPort)) })
                        put("outbound", "direct")
                    })
                    add(buildJsonObject { put("protocol", "dns"); put("outbound", "direct") })
                    extraRouteRules.forEach { add(it) }
                }
                put("final", EXIT_TAG)
                put("auto_detect_interface", true)
            })
        }
        return BridgeResult(SingBoxJson.encode(root), localPort)
    }

    /**
     * A standalone full sing-box config, prepared for the TUN service
     * (v1 semantics, unchanged): loopback-only listeners, global routing,
     * and a TUN inbound synthesised when the config has none. Returns the
     * config plus the SOCKS port an integration may advertise — read back
     * from the prepared shape itself, so it can never drift (§2.4 rule 2).
     */
    data class Prepared(val configJson: String, val socksPort: Int, val dnsProxyWorks: Boolean)

    fun prepareForDevice(rawJson: String, device: DeviceOptions = DeviceOptions()): Prepared {
        val root = SingBoxJson.parse(rawJson) as? JsonObject ?: return Prepared(rawJson, STANDALONE_SOCKS_PORT, false)
        val inbounds = (root["inbounds"] as? JsonArray).orEmptyElements()
        val outbounds = (root["outbounds"] as? JsonArray).orEmptyElements()
        val route = root["route"] as? JsonObject

        var hasTun = false
        var socksPort: Int? = null
        val newInbounds = buildJsonArray {
            inbounds.forEach { el ->
                val obj = el as? JsonObject
                if (obj != null && (obj["type"] as? JsonPrimitive)?.content == "tun") { hasTun = true; add(el) }
            }
            inbounds.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                when ((obj["type"] as? JsonPrimitive)?.content) {
                    "tun" -> Unit
                    "socks", "mixed" -> {
                        socksPort = socksPort ?: (obj["listen_port"] as? JsonPrimitive)?.content?.toIntOrNull()
                        add(loopbackOnly(obj))
                    }
                    "http" -> add(loopbackOnly(obj))
                    else -> add(el)
                }
            }
            if (!hasTun) {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("address", buildJsonArray { add(JsonPrimitive(device.tunAddress)) })
                    put("mtu", device.mtu)
                    put("auto_route", device.autoRoute)
                    put("strict_route", device.strictRoute)
                    put("stack", device.stack)
                })
            }
        }

        val finalTag = (route?.get("final") as? JsonPrimitive)?.content
        val dnsProxyWorks = finalTag != null && outbounds.any {
            ((it as? JsonObject)?.get("tag") as? JsonPrimitive)?.content == finalTag
        }

        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k != "inbounds" && k != "route") put(k, v) }
            put("inbounds", newInbounds)
            // Force global routing: drop rule lists, keep only `final`.
            put("route", buildJsonObject {
                put("auto_detect_interface", true)
                if (finalTag != null) put("final", finalTag)
            })
        }
        return Prepared(SingBoxJson.encode(newRoot), socksPort ?: STANDALONE_SOCKS_PORT, dnsProxyWorks)
    }

    private fun loopbackOnly(obj: JsonObject): JsonObject = buildJsonObject {
        obj.forEach { (k, v) -> if (k != "listen") put(k, v) }
        put("listen", "127.0.0.1")
    }
}

/** Small DSL helper: `put("key", buildJsonArray { ... })` without the noise. */
internal inline fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayOfObjects(
    key: String,
    block: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit,
) {
    put(key, buildJsonArray(block))
}

internal fun JsonArray?.orEmptyElements(): List<JsonElement> = this ?: emptyList()
