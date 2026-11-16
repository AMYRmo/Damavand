package com.amyrmo.damavand.lite

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Parses vless/vmess/trojan/ss share links and builds the minimal Xray
 * JSON the app needs: one SOCKS inbound on localhost + proxy
 * outbound(s). Chains link the hops with sockopt.dialerProxy, exactly
 * the way PattNG does (CoreConfigManager.handleProxyChainResolvedOutbound):
 * the first outbound in the array carries the traffic (the exit), and
 * each hop dials its server through the next hop toward the entry.
 * No rulesets beyond the private-range / torrent / ads-geo rules.
 *
 * 3.1.0 (PattNG): [XraySettings] carries the connection settings the
 * user can change in Settings — MUX, sniffing, fragment, DNS servers
 * and the routing mode + custom rules.
 */
object XrayConfig {

    /** Connection settings snapshot (defaults = the 3.0.0 behaviour). */
    data class XraySettings(
        val muxEnabled: Boolean = false,
        val sniffingEnabled: Boolean = true,
        val fragmentEnabled: Boolean = false,
        val dnsServers: List<String> = listOf("8.8.8.8", "1.1.1.1"),
        val routingMode: String = "bypass_lan", // global | bypass_lan | custom
        val customRules: List<RoutingRule> = emptyList(),
        // 3.2.0 (PattNG parity).
        val socksPort: Int = 1080,
        val socksUser: String = "",
        val socksPass: String = "",
        val socksUdp: Boolean = true,
        val socksShareLan: Boolean = false,
        val httpProxyEnabled: Boolean = false,
        val fakeDnsEnabled: Boolean = false,
        val routeOnly: Boolean = false,
        val coreLogLevel: String = "warning",
        val domainStrategy: String = "IPIfNonMatch",
        val muxConcurrency: Int = 8,
        val muxXudpConcurrency: Int = 8,
        val muxXudpQuic: Boolean = false,
        val domesticDns: String = "",
        val dnsHosts: Map<String, String> = emptyMap(),
        val mtu: Int = 1500,
        val ifaceAddr: String = "10.8.0.2",
        // 3.2.0: Xray writes its error log here (shown in Logs).
        val errorLogPath: String = "",
    )

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
        // 3.2.7 (PattNG parity): TLS/Reality extras, read from the link
        // exactly like PattNG's FmtBase (fp, insecure/allowInsecure,
        // cs, pbk, sid, spx) and honoured in streamSettings below.
        val fp: String = "",
        val allowInsecure: Boolean = false,
        val cipherSuites: String = "",
        val realityPublicKey: String = "",
        val realityShortId: String = "",
        val spiderX: String = "",
        // 3.2.0 (PattNG): WireGuard / Hysteria2 / custom-JSON extras.
        val wgPublicKey: String = "",
        val wgPresharedKey: String = "",
        val wgAllowedIps: String = "",
        val wgAddress: String = "",
        val wgMtu: String = "",
        val wgReserved: String = "",
        val hyInsecure: String = "",
        val customJson: String = "",
    )

    fun parse(link: String): Parsed? {
        return try {
            val proto = link.substringBefore("://")
            if (proto !in setOf(
                    "vless", "vmess", "trojan", "ss",
                    "socks", "http", "hysteria2", "wg", "custom"
                )
            ) return null
            val rest = link.substringAfter("://").substringBefore("#")
            if (proto == "ss") return parseSs(rest)
            if (proto == "custom") {
                // custom://<base64url(full Xray config JSON)>
                val json = b64decode(rest) ?: return null
                if (!json.trimStart().startsWith("{")) return null
                return Parsed(
                    proto = "custom",
                    addr = "",
                    port = 0,
                    id = "",
                    net = "",
                    tls = "",
                    sni = "",
                    path = "",
                    host = "",
                    flow = "",
                    serviceName = "",
                    alpn = "",
                    customJson = json,
                )
            }
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
                    alpn = j.optString("alpn"),
                    fp = j.optString("fp"),
                )
            }
            // SOCKS / HTTP may arrive as base64(user:pass@host:port).
            var effectiveRest = rest
            if ((proto == "socks" || proto == "http") && '@' !in effectiveRest) {
                effectiveRest = b64decode(effectiveRest) ?: return null
            }
            val atIdx = effectiveRest.lastIndexOf('@')
            if (atIdx < 0) return null
            val user = effectiveRest.substring(0, atIdx)
            val afterAt = effectiveRest.substring(atIdx + 1)
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
                // 3.2.7 (PattNG FmtBase): fingerprint, allow-insecure
                // (insecure / allowInsecure / allow_insecure), cipher
                // suites (cs), Reality public key (pbk) + short id
                // (sid) + spiderX (spx).
                fp = q["fp"] ?: "",
                allowInsecure = listOf("insecure", "allowInsecure", "allow_insecure")
                    .any { q[it] == "1" || q[it].equals("true", ignoreCase = true) },
                cipherSuites = q["cs"] ?: "",
                realityPublicKey = q["pbk"] ?: "",
                realityShortId = q["sid"] ?: "",
                spiderX = q["spx"] ?: "",
                // 3.2.0: WireGuard + Hysteria2 query params.
                wgPublicKey = q["publicKey"] ?: "",
                wgPresharedKey = q["presharedKey"] ?: "",
                wgAllowedIps = q["allowedIPs"] ?: "",
                wgAddress = q["address"] ?: "",
                wgMtu = q["mtu"] ?: "",
                wgReserved = q["reserved"] ?: "",
                hyInsecure = q["insecure"] ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Base64 (URL-safe first, then standard) with padding repaired. */
    private fun b64decode(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        for (flags in listOf(Base64.URL_SAFE, Base64.DEFAULT)) {
            try {
                return String(Base64.decode(padded, flags), Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Shadowsocks share links, three shapes:
     * - ss://<b64(method:password@host:port)>
     * - ss://<b64(method:password)>@host:port
     * - ss://method:password@host:port
     * A plugin= query means an obfs plugin Xray does not run, so those
     * links are rejected instead of connecting broken. The method and
     * password travel in [Parsed.id] as "method:password".
     */
    private fun parseSs(rest: String): Parsed? {
        val noFrag = rest.substringBefore("#")
        if (noFrag.contains("plugin=")) return null
        val noQuery = noFrag.substringBefore("?")
        val userInfo: String
        val hostPort: String
        if ('@' in noQuery) {
            val rawUser = noQuery.substringBeforeLast('@')
            hostPort = noQuery.substringAfterLast('@')
            // Plain "method:password" already contains ':'; a base64
            // userinfo never does, so decode only in that case.
            userInfo = if (':' in rawUser) rawUser else b64decode(rawUser) ?: return null
        } else {
            val decoded = b64decode(noQuery) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            userInfo = decoded.substring(0, at)
            hostPort = decoded.substring(at + 1)
        }
        val method = userInfo.substringBefore(":")
        val password = userInfo.substringAfter(":", "")
        val addr = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "0").toIntOrNull() ?: 0
        if (method.isEmpty() || password.isEmpty() || addr.isEmpty() || port == 0) return null
        return Parsed(
            proto = "ss",
            addr = addr,
            port = port,
            id = "$method:$password",
            net = "tcp",
            tls = "",
            sni = "",
            path = "",
            host = "",
            flow = "",
            serviceName = "",
            alpn = "",
        )
    }

    private fun streamSettings(p: Parsed, settings: XraySettings): JsonObject {
        val net = p.net.lowercase()
        return buildJsonObject {
            put("network", net)
            // 3.2.7 (PattNG CoreOutboundBuilder.populateTlsSettings):
            // TLS honours the link's fingerprint (default chrome, like
            // before), allowInsecure and cipher suites; REALITY builds
            // realitySettings instead of tlsSettings (publicKey/shortId
            // from pbk/sid). A link without either stays plain.
            if (p.tls == "tls") {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    put("serverName", p.sni.ifEmpty { p.addr })
                    put("fingerprint", p.fp.ifEmpty { "chrome" })
                    put("allowInsecure", p.allowInsecure)
                    if (p.alpn.isNotEmpty()) {
                        putJsonArray("alpn") { p.alpn.split(",").forEach { add(JsonPrimitive(it.trim())) } }
                    }
                    val suites = p.cipherSuites.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (suites.isNotEmpty()) {
                        putJsonArray("cipherSuites") { suites.forEach { add(JsonPrimitive(it)) } }
                    }
                }
            } else if (p.tls == "reality") {
                put("security", "reality")
                putJsonObject("realitySettings") {
                    put("serverName", p.sni.ifEmpty { p.addr })
                    put("fingerprint", p.fp.ifEmpty { "chrome" })
                    put("publicKey", p.realityPublicKey)
                    put("shortId", p.realityShortId)
                    if (p.spiderX.isNotEmpty()) put("spiderX", p.spiderX)
                }
            }
            // Fragment (3.1.0): split the TLS hello so DPI sees noise.
            if (settings.fragmentEnabled) {
                putJsonObject("fragment") {
                    put("packets", "tlshello")
                    put("length", "100-200")
                    put("interval", "10-20")
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

    /**
     * One proxy outbound for [p]. [tag] is "proxy" for a single hop and
     * "proxy-chain-<i>" for chain hops (PattNG tags the exit "proxy" and
     * the rest "proxy-<main>-<i>"). [dialerProxy] names the outbound
     * this hop's connection to its server goes through — the next hop
     * toward the entry; null for the entry hop, which dials directly.
     */
    private fun outbound(
        p: Parsed,
        tag: String,
        dialerProxy: String? = null,
        settings: XraySettings = XraySettings(),
    ): JsonObject? {
        val stream = streamSettings(p, settings)
        fun withProxy(body: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
            put("tag", tag)
            body()
            put("streamSettings", stream)
            if (dialerProxy != null) {
                putJsonObject("sockopt") { put("dialerProxy", dialerProxy) }
            }
            // MUX (3.1.0): several streams over one connection.
            // 3.2.0: concurrency + XUDP concurrency from settings.
            if (settings.muxEnabled) {
                putJsonObject("mux") {
                    put("enabled", true)
                    put("concurrency", settings.muxConcurrency)
                    put("xudpConcurrency", settings.muxXudpConcurrency)
                }
            }
        }
        return when (p.proto) {
            "vless" -> withProxy {
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
                                    // 3.2.0 (PattNG mux XUDP QUIC): Xray's
                                    // MuxConfig has NO quic field (verified
                                    // against xray-core infra/conf/xray.go:
                                    // only enabled/concurrency/
                                    // xudpConcurrency/xudpProxyUDP443).
                                    // The real QUIC-style XUDP switch is
                                    // the VLESS user's packetEncoding.
                                    if (settings.muxXudpQuic) {
                                        put("packetEncoding", "xudp")
                                    }
                                })
                            }
                        })
                    }
                }
            }
            "vmess" -> withProxy {
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
            }
            "trojan" -> withProxy {
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
            }
            "ss" -> withProxy {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            put("method", p.id.substringBefore(":"))
                            put("password", p.id.substringAfter(":", ""))
                        })
                    }
                }
            }
            // 3.2.0 (PattNG): SOCKS / HTTP proxy outbounds.
            "socks", "http" -> withProxy {
                put("protocol", p.proto)
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            val user = p.id.substringBefore(":")
                            val pass = p.id.substringAfter(":", "")
                            if (user.isNotEmpty()) {
                                putJsonArray("users") {
                                    add(buildJsonObject {
                                        put("user", user)
                                        put("pass", pass)
                                    })
                                }
                            }
                        })
                    }
                }
            }
            // 3.2.0 (PattNG): Hysteria2 (UDP + TLS; no mux).
            "hysteria2" -> buildJsonObject {
                put("tag", tag)
                put("protocol", "hysteria2")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.addr)
                            put("port", p.port)
                            put("password", p.id)
                        })
                    }
                }
                putJsonObject("streamSettings") {
                    put("network", "udp")
                    put("security", "tls")
                    putJsonObject("tlsSettings") {
                        put("serverName", p.sni.ifEmpty { p.addr })
                        put(
                            "allowInsecure",
                            p.hyInsecure == "1" || p.hyInsecure.lowercase() == "true"
                        )
                    }
                }
                if (dialerProxy != null) {
                    putJsonObject("sockopt") { put("dialerProxy", dialerProxy) }
                }
            }
            // 3.2.0 (PattNG): WireGuard (no streamSettings, no mux).
            "wg" -> buildJsonObject {
                put("tag", tag)
                put("protocol", "wireguard")
                putJsonObject("settings") {
                    put("secretKey", p.id)
                    putJsonArray("address") {
                        p.wgAddress.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("peers") {
                        add(buildJsonObject {
                            put("publicKey", p.wgPublicKey)
                            put("endpoint", "${p.addr}:${p.port}")
                            putJsonArray("allowedIPs") {
                                p.wgAllowedIps.ifEmpty { "0.0.0.0/0,::/0" }
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach { add(JsonPrimitive(it)) }
                            }
                            if (p.wgPresharedKey.isNotEmpty()) {
                                put("preSharedKey", p.wgPresharedKey)
                            }
                        })
                    }
                    p.wgMtu.toIntOrNull()?.let { put("mtu", it) }
                    if (p.wgReserved.isNotEmpty()) {
                        putJsonArray("reserved") {
                            p.wgReserved.split(",")
                                .mapNotNull { it.trim().toIntOrNull() }
                                .forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
                if (dialerProxy != null) {
                    putJsonObject("sockopt") { put("dialerProxy", dialerProxy) }
                }
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
    fun build(
        link: String,
        socksPort: Int,
        geoAvailable: Boolean = false,
        settings: XraySettings = XraySettings(),
    ): String? {
        val p = parse(link) ?: return null
        // 3.2.0 (PattNG): a custom-JSON profile IS the whole config.
        // The user supplies the full Xray JSON (it must include a SOCKS
        // inbound on the connect port for the tun bridge to work).
        if (p.proto == "custom") return p.customJson
        val ob = outbound(p, "proxy", settings = settings) ?: return null
        return assemble(listOf(ob), socksPort, geoAvailable, settings)
    }

    /**
     * Full Xray config JSON for a proxy chain (PattNG PROXYCHAIN).
     * [links] are in the order the user picked them: entry first, exit
     * last. The outbounds are built exit-first (PattNG resolves a chain
     * exit-to-entry): the exit carries the traffic under tag "proxy"
     * (so the traffic counters and the default route keep working), and
     * each hop dials its server through the next hop toward the entry
     * (sockopt.dialerProxy). A chain is built whole or not at all: one
     * unparseable hop refuses the whole chain, so traffic never takes a
     * shorter path than the chain names.
     */
    fun buildChain(
        links: List<String>,
        socksPort: Int,
        geoAvailable: Boolean = false,
        settings: XraySettings = XraySettings(),
    ): String? {
        if (links.size < 2) return links.firstOrNull()?.let { build(it, socksPort, geoAvailable, settings) }
        val parsed = links.map { parse(it) ?: return null }
        val resolved = parsed.reversed() // exit first, entry last
        val outbounds = resolved.mapIndexed { i, p ->
            val tag = if (i == 0) "proxy" else "proxy-chain-$i"
            // Hop i dials its server through hop i+1 (toward the entry);
            // the entry hop (last) dials directly.
            val dialerProxy = if (i < resolved.size - 1) "proxy-chain-${i + 1}" else null
            outbound(p, tag, dialerProxy, settings) ?: return null
        }
        return assemble(outbounds, socksPort, geoAvailable, settings)
    }

    /** One link → [build]; several → [buildChain]. */
    fun buildLinks(
        links: List<String>,
        socksPort: Int,
        geoAvailable: Boolean = false,
        settings: XraySettings = XraySettings(),
    ): String? {
        return when {
            links.isEmpty() -> null
            links.size == 1 -> build(links[0], socksPort, geoAvailable, settings)
            else -> buildChain(links, socksPort, geoAvailable, settings)
        }
    }

    /**
     * Balancer config (3.2.0, PattNG policy group): one outbound per
     * profile (tags proxy-0..N) + a leastPing balancer + an observatory
     * that probes them every minute. The catch-all route sends traffic
     * to the balancer; the busiest hop's counters feed the stats
     * (see XrayController.readTrafficDelta's fallback).
     */
    fun buildBalancer(
        links: List<String>,
        socksPort: Int,
        geoAvailable: Boolean = false,
        settings: XraySettings = XraySettings(),
    ): String? {
        if (links.size < 2) return links.firstOrNull()
            ?.let { build(it, socksPort, geoAvailable, settings) }
        val parsed = links.map { parse(it) ?: return null }
        val tags = parsed.indices.map { "proxy-$it" }
        val outbounds = parsed.mapIndexed { i, p ->
            outbound(p, tags[i], null, settings) ?: return null
        }
        return assemble(outbounds, socksPort, geoAvailable, settings, balancerTags = tags)
    }

    /** One custom rule → its Xray routing rule object. */
    private fun customRuleJson(rule: RoutingRule): JsonObject {
        val pattern = rule.pattern.trim()
        val isIp = pattern.startsWith("geoip:") ||
            Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+(/\\d+)?$").matches(pattern)
        val tag = when (rule.action) {
            "direct" -> "direct"
            "block" -> "block"
            else -> "proxy"
        }
        return buildJsonObject {
            put("type", "field")
            if (isIp) {
                putJsonArray("ip") { add(JsonPrimitive(pattern)) }
            } else {
                putJsonArray("domain") { add(JsonPrimitive(pattern)) }
            }
            put("outboundTag", tag)
        }
    }

    /** Shared skeleton: log + DNS + SOCKS inbound + outbounds + routing. */
    private fun assemble(
        proxyOutbounds: List<JsonObject>,
        socksPort: Int,
        geoAvailable: Boolean,
        settings: XraySettings = XraySettings(),
        // 3.2.0 (PattNG): when set, a leastPing balancer over these tags.
        balancerTags: List<String>? = null,
    ): String {
        val json = buildJsonObject {
            val logLevel = settings.coreLogLevel.takeIf {
                it in setOf("debug", "info", "warning", "error", "none")
            } ?: "warning"
            putJsonObject("log") {
                put("loglevel", logLevel)
                // 3.2.0: the core error log, shown in the Logs screen.
                settings.errorLogPath.takeIf { it.isNotEmpty() }?.let {
                    put("error", it)
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    // Fake DNS (3.2.0): Xray answers every query with a
                    // fake 198.18.x.x address; routing still works because
                    // it uses the sniffed domain, not the fake IP.
                    if (settings.fakeDnsEnabled) {
                        add(buildJsonObject {
                            put("address", "fakedns")
                            put("pool", "198.18.0.0/15")
                        })
                    }
                    settings.dnsServers.forEach { add(JsonPrimitive(it)) }
                    settings.domesticDns.takeIf { it.isNotEmpty() }?.let {
                        add(JsonPrimitive(it))
                    }
                }
                if (settings.dnsHosts.isNotEmpty()) {
                    putJsonObject("hosts") {
                        settings.dnsHosts.forEach { (domain, ip) ->
                            put(domain, JsonPrimitive(ip))
                        }
                    }
                }
            }
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("tag", "socks")
                    put("port", socksPort)
                    put("listen", if (settings.socksShareLan) "0.0.0.0" else "127.0.0.1")
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        if (settings.socksUser.isNotEmpty()) {
                            put("auth", "password")
                            putJsonArray("accounts") {
                                add(buildJsonObject {
                                    put("user", settings.socksUser)
                                    put("pass", settings.socksPass)
                                })
                            }
                        } else {
                            put("auth", "noauth")
                        }
                        put("udp", settings.socksUdp)
                    }
                    // Sniffing (3.1.0): route by the real destination.
                    if (settings.sniffingEnabled) {
                        putJsonObject("sniffing") {
                            put("enabled", true)
                            putJsonArray("destOverride") {
                                add(JsonPrimitive("http"))
                                add(JsonPrimitive("tls"))
                            }
                        }
                    }
                })
                // HTTP proxy inbound (3.2.0): a plain HTTP proxy on
                // socksPort+1, for apps that cannot speak SOCKS.
                if (settings.httpProxyEnabled) {
                    add(buildJsonObject {
                        put("tag", "http")
                        put("port", socksPort + 1)
                        put("listen", if (settings.socksShareLan) "0.0.0.0" else "127.0.0.1")
                        put("protocol", "http")
                        putJsonObject("settings") { put("allowTransparent", false) }
                    })
                }
            }
            putJsonArray("outbounds") {
                proxyOutbounds.forEach { add(it) }
                add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
                add(buildJsonObject { put("tag", "block"); put("protocol", "blackhole") })
            }
            putJsonObject("routing") {
                val strategy = settings.domainStrategy.takeIf {
                    it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand")
                } ?: "IPIfNonMatch"
                put("domainStrategy", strategy)
                putJsonArray("rules") {
                    // Custom rules first — they win over the defaults.
                    if (settings.routingMode == "custom") {
                        settings.customRules.filter { it.enabled }.forEach {
                            add(customRuleJson(it))
                        }
                    }
                    // Global mode: everything goes through the proxy
                    // (only the torrent block stays); the other modes
                    // keep private ranges direct.
                    if (settings.routingMode != "global") {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("ip") { add(JsonPrimitive("10.0.0.0/8")); add(JsonPrimitive("172.16.0.0/12")); add(JsonPrimitive("192.168.0.0/16")) }
                            put("outboundTag", "direct")
                        })
                    }
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
                    // Route-only mode (3.2.0): traffic that matches no
                    // rule goes direct instead of through the proxy.
                    if (settings.routeOnly) {
                        add(buildJsonObject {
                            put("type", "field")
                            put("outboundTag", "direct")
                        })
                    } else if (balancerTags != null) {
                        // 3.2.0 (PattNG): the balancer is the default
                        // route — everything not matched above goes to
                        // the least-ping hop.
                        add(buildJsonObject {
                            put("type", "field")
                            put("outboundTag", "balancer")
                        })
                    }
                }
                // 3.2.0 (PattNG): the leastPing balancer itself.
                if (balancerTags != null) {
                    putJsonArray("balancers") {
                        add(buildJsonObject {
                            put("tag", "balancer")
                            putJsonArray("selector") {
                                balancerTags.forEach { add(JsonPrimitive(it)) }
                            }
                            putJsonObject("strategy") {
                                put("type", "leastPing")
                            }
                        })
                    }
                }
            }
            // 3.2.0 (PattNG): the observatory probes the balancer's
            // outbounds so leastPing has live data.
            if (balancerTags != null) {
                putJsonObject("observatory") {
                    putJsonArray("subjectSelector") {
                        balancerTags.forEach { add(JsonPrimitive(it)) }
                    }
                    put("probeUrl", "https://www.google.com/generate_204")
                    put("probeInterval", "1m")
                }
            }
        }
        return Json.encodeToString(JsonObject.serializer(), json)
    }
}
