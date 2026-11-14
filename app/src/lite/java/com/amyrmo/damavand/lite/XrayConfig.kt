package com.amyrmo.damavand.lite

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Parses vless/vmess/trojan share links and builds the minimal Xray JSON
 * the app needs: one SOCKS inbound on localhost + one proxy outbound.
 * No chains, no rulesets, no geo files.
 */
object XrayConfig {

    data class Parsed(
        val proto: String,
        val addr: String,
        val port: Int,
        val id: String,
        val net: String,
        val tls: String,
        val sni: String,
        val path: String,
        val host: String,
        val flow: String,
        val serviceName: String,
        val alpn: String,
    )

    fun parse(link: String): Parsed? {
        return try {
            val proto = link.substringBefore("://")
            if (proto !in setOf("vless", "vmess", "trojan")) return null
            val rest = link.substringAfter("://").substringBefore("#")
            if (proto == "vmess") {
                val padded = rest + "=".repeat((4 - rest.length % 4) % 4)
                val j = JSONObject(String(Base64.decode(padded, Base64.URL_SAFE)))
                return Parsed(
                    proto = "vmess",
                    addr = j.optString("add"),
                    port = j.optInt("port"),
                    id = j.optString("id"),
                    net = j.optString("net", "tcp"),
                    tls = j.optString("tls"),
                    sni = j.optString("sni").ifEmpty { j.optString("host") },
                    path = j.optString("path"),
                    host = j.optString("host"),
                    flow = "",
                    serviceName = "",
                    alpn = "",
                )
            }
            val atIdx = rest.lastIndexOf('@')
            if (atIdx < 0) return null
            val user = rest.substring(0, atIdx)
            val afterAt = rest.substring(atIdx + 1)
            val hostPort = afterAt.substringBefore("?").substringBefore("/")
            val query = afterAt.substringAfter("?", "")
            val addr = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "0").toIntOrNull() ?: 0
            if (addr.isEmpty() || port == 0) return null
            val q = query.split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()
            val type = q["type"] ?: "tcp"
            Parsed(
                proto = proto,
                addr = addr,
                port = port,
                id = user,
                net = type,
                tls = q["security"] ?: "",
                sni = q["sni"] ?: q["host"] ?: "",
                path = q["path"] ?: "",
                host = q["host"] ?: "",
                flow = q["flow"] ?: "",
                serviceName = q["serviceName"] ?: "",
                alpn = q["alpn"] ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun streamSettings(p: Parsed): JsonObject {
        val net = p.net.lowercase()
        return buildJsonObject {
            put("network", net)
            if (p.tls == "tls") {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    put("serverName", p.sni.ifEmpty { p.addr })
                    put("fingerprint", "chrome")
                    put("allowInsecure", false)
                    if (p.alpn.isNotEmpty()) {
                        putJsonArray("alpn") { p.alpn.split(",").forEach { add(JsonPrimitive(it.trim())) } }
                    }
                }
            }
            when (net) {
                "ws" -> putJsonObject("wsSettings") {
                    put("path", p.path.ifEmpty { "/" })
                    putJsonObject("headers") {
                        put("Host", p.host.ifEmpty { p.sni.ifEmpty { p.addr } })
                    }
                }
                "xhttp" -> putJsonObject("xhttpSettings") {
                    put("path", p.path.ifEmpty { "/" })
                    put("host", p.host.ifEmpty { p.sni.ifEmpty { p.addr } })
                    put("mode", "auto")
                }
                "grpc" -> putJsonObject("grpcSettings") {
                    put("serviceName", p.serviceName)
                    put("multiMode", true)
                }
            }
        }
    }

    private fun outbound(p: Parsed): JsonObject? {
        val stream = streamSettings(p)
        return when (p.proto) {
            "vless" -> buildJsonObject {
                put("tag", "proxy")
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.id)
                                    put("encryption", "none")
                                    put("flow", p.flow)
                                })
                            }
                        })
                    }
                }
                put("streamSettings", stream)
            }
            "vmess" -> buildJsonObject {
                put("tag", "proxy")
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.id)
                                    put("alterId", 0)
                                    put("security", "auto")
                                })
                            }
                        })
                    }
                }
                put("streamSettings", stream)
            }
            "trojan" -> buildJsonObject {
                put("tag", "proxy")
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            put("password", p.id)
                        })
                    }
                }
                put("streamSettings", stream)
            }
            else -> null
        }
    }

    /**
     * Full Xray config JSON for one link, with a SOCKS inbound on [socksPort].
     * When [geoAvailable] is true (geoip.dat + geosite.dat downloaded into
     * the core asset dir), a geo-based rule is added (block known ad
     * domains); when geo is missing the config stays minimal and the core
     * runs fine without it — connecting is never blocked on geo.
     */
    fun build(link: String, socksPort: Int, geoAvailable: Boolean = false): String? {
        val p = parse(link) ?: return null
        val ob = outbound(p) ?: return null
        val json = buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("dns") {
                putJsonArray("servers") { add(JsonPrimitive("8.8.8.8")); add(JsonPrimitive("1.1.1.1")) }
            }
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("tag", "socks")
                    put("port", socksPort)
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                })
            }
            putJsonArray("outbounds") {
                add(ob)
                add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
                add(buildJsonObject { put("tag", "block"); put("protocol", "blackhole") })
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    add(buildJsonObject {
                        put("type", "field")
                        putJsonArray("ip") { add(JsonPrimitive("10.0.0.0/8")); add(JsonPrimitive("172.16.0.0/12")); add(JsonPrimitive("192.168.0.0/16")) }
                        put("outboundTag", "direct")
                    })
                    add(buildJsonObject {
                        put("type", "field")
                        putJsonArray("protocol") { add(JsonPrimitive("bittorrent")) }
                        put("outboundTag", "block")
                    })
                    if (geoAvailable) {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("domain") { add(JsonPrimitive("geosite:category-ads-all")) }
                            put("outboundTag", "block")
                        })
                    }
                }
            }
        }
        return Json.encodeToString(JsonObject.serializer(), json)
    }
}
