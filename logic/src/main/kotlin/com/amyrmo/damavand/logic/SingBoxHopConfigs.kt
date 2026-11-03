package com.amyrmo.damavand.logic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SingBoxHopException(message: String) : Exception(message)

/**
 * sing-box hop configs for chains (architecture §3.2/§4.4): in v2 a
 * sing-box profile inside a chain runs on its own sing-box instance,
 * spliced over loopback SOCKS like every other hop — it is never compiled
 * into an Xray config (the v1 trap).
 *
 *  - a **fragment** (one outbound object) is wrapped: loopback `mixed`
 *    inbound at the allocated port, the fragment as the exit outbound, and
 *    — when the hop has an upstream — a generated `prev-hop` SOCKS
 *    outbound the fragment detours through;
 *  - a **full config** used as a hop keeps its outbounds but loses any TUN
 *    inbound (only the session bridge owns TUN) and gains the loopback
 *    listener the plan advertises; its final outbound detours through
 *    `prev-hop` when chained. Best-effort by design: exotic configs that
 *    resist the surgery fail loudly instead of half-starting.
 */
object SingBoxHopConfigs {

    const val PREV_HOP_TAG = "prev-hop"

    fun wrapFragment(fragmentJson: String, listenPort: Int, upstreamPort: Int?): String {
        val fragment = SingBoxJson.parse(fragmentJson) as? JsonObject
            ?: throw SingBoxHopException("sing-box fragment is not a JSON object")
        val type = (fragment["type"] as? JsonPrimitive)?.content
            ?: throw SingBoxHopException("sing-box fragment has no outbound type")
        val tag = (fragment["tag"] as? JsonPrimitive)?.content ?: "proxy"

        val outbound = buildJsonObject {
            fragment.forEach { (k, v) -> if (k != "detour") put(k, v) }
            if (!fragment.containsKey("tag")) put("tag", tag)
            if (upstreamPort != null) put("detour", PREV_HOP_TAG)
        }
        val root = buildJsonObject {
            put("log", buildJsonObject { put("level", "warn"); put("timestamp", true) })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "mixed")
                    put("tag", "mixed-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", listenPort)
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                if (upstreamPort != null) {
                    add(buildJsonObject {
                        put("type", "socks")
                        put("tag", PREV_HOP_TAG)
                        put("version", "5")
                        put("server", "127.0.0.1")
                        put("server_port", upstreamPort)
                        put("network", "tcp")
                    })
                }
                add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
            })
            put("route", buildJsonObject { put("final", tag) })
        }
        return SingBoxJson.encode(root)
    }

    fun wrapFullConfig(rawJson: String, listenPort: Int, upstreamPort: Int?): String {
        val root = SingBoxJson.parse(rawJson) as? JsonObject
            ?: throw SingBoxHopException("sing-box config is not a JSON object")
        val inbounds = (root["inbounds"] as? JsonArray)?.filter {
            ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.content != "tun"
        } ?: emptyList()
        val hasListener = inbounds.any {
            val o = it as? JsonObject
            ((o?.get("listen_port") as? JsonPrimitive)?.content?.toIntOrNull()) == listenPort
        }
        val route = root["route"] as? JsonObject
        val finalTag = (route?.get("final") as? JsonPrimitive)?.content
        val outbounds = (root["outbounds"] as? JsonArray)?.map { el ->
            val o = el as? JsonObject ?: return@map el
            if (upstreamPort != null && finalTag != null &&
                (o["tag"] as? JsonPrimitive)?.content == finalTag && !o.containsKey("detour")
            ) {
                buildJsonObject {
                    o.forEach { (k, v) -> put(k, v) }
                    put("detour", PREV_HOP_TAG)
                }
            } else {
                el
            }
        } ?: emptyList()
        val hasPrevHop = outbounds.any {
            ((it as? JsonObject)?.get("tag") as? JsonPrimitive)?.content == PREV_HOP_TAG
        }

        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k != "inbounds" && k != "outbounds") put(k, v) }
            put("inbounds", buildJsonArray {
                inbounds.forEach { add(it) }
                if (!hasListener) {
                    add(buildJsonObject {
                        put("type", "mixed")
                        put("tag", "mixed-in")
                        put("listen", "127.0.0.1")
                        put("listen_port", listenPort)
                    })
                }
            })
            put("outbounds", buildJsonArray {
                outbounds.forEach { add(it) }
                if (upstreamPort != null && !hasPrevHop) {
                    add(buildJsonObject {
                        put("type", "socks")
                        put("tag", PREV_HOP_TAG)
                        put("version", "5")
                        put("server", "127.0.0.1")
                        put("server_port", upstreamPort)
                        put("network", "tcp")
                    })
                }
            })
        }
        return SingBoxJson.encode(newRoot)
    }
}
