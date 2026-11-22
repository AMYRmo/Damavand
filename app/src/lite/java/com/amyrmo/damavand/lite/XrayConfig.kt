package com.amyrmo.damavand.lite

import android.util.Base64
import com.amyrmo.damavand.lite.aether.AetherArgs
import com.amyrmo.damavand.lite.aether.AetherCore
import com.amyrmo.damavand.lite.aether.AetherExit
import com.amyrmo.damavand.lite.aether.AetherSettings
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
        // 3.2.11 (PattNG full parity): IPv6 on by default — gates the
        // WireGuard IPv6 addresses/allowed-IPs and the tun's IPv6 side.
        val ipv6Enabled: Boolean = true,
        // 3.2.9 (PattNG, the rest): Xray-tun mode (no HEV — the core
        // reads the VPN fd itself) and the local-proxy switch. The
        // SOCKS/HTTP inbounds exist only when localProxyEnabled.
        val tunMode: Boolean = false,
        val localProxyEnabled: Boolean = true,
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
        // 3.2.16 (#52): TCP header type (e.g. "http") from the link.
        val headerType: String = "",
        // 1.0.1.2 (#109): XHTTP mode + extra from the link (PattNG's
        // xhttpMode/xhttpExtra). The old code hardcoded mode "auto"
        // and dropped extra entirely.
        val xhttpMode: String = "",
        val xhttpExtra: String = "",
        // 1.0.1.2 (#110): gRPC mode from the link (PattNG's mode) —
        // multiMode is true only when it is "multi".
        val grpcMode: String = "",
        // 3.2.7 (PattNG parity): TLS/Reality extras, read from the link
        // exactly like PattNG's FmtBase (fp, insecure/allowInsecure,
        // cs, pbk, sid, spx) and honoured in streamSettings below.
        val fp: String = "",
        val allowInsecure: Boolean = false,
        val cipherSuites: String = "",
        val realityPublicKey: String = "",
        val realityShortId: String = "",
        val spiderX: String = "",
        // 3.2.9 (PattNG parity, the rest): ECH + pinning extras, read
        // exactly like PattNG's FmtBase (ech, echOutbound, vcn, pcs,
        // pqv) and honoured in streamSettings below.
        val echConfigList: String = "",
        val echOutbound: String = "",
        val verifyPeerCertByName: String = "",
        val pinnedCA256: String = "",
        val mldsa65Verify: String = "",
        // 3.2.0 (PattNG): WireGuard / Hysteria2 / custom-JSON extras.
        val wgPublicKey: String = "",
        val wgPresharedKey: String = "",
        val wgAllowedIps: String = "",
        val wgAddress: String = "",
        val wgMtu: String = "",
        val wgReserved: String = "",
        val hyInsecure: String = "",
        // 1.0.2 (PattNG Hysteria2Fmt): the salamander obfs password
        // (obfs-password query param). Without it the obfs layer of
        // a Hysteria2 link can never authenticate.
        val obfsPassword: String = "",
        // 1.0.1.18 (M7): VLESS Encryption value from the link (e.g.
        // the mlkem768 strings 3x-ui writes after Generate).
        val encryption: String = "",
        val customJson: String = "",
    )

    fun parse(link: String): Parsed? {
        return try {
            // 1.0.1.18 (M6): schemes are case-insensitive, and the two
            // short/standard aliases most panels emit — hy2:// for
            // Hysteria2, wireguard:// for WireGuard — are accepted too.
            val proto = when (val raw = link.substringBefore("://").lowercase()) {
                "hy2" -> "hysteria2"
                "wireguard" -> "wg"
                else -> raw
            }
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
                // 3.2.16 (#48): v2rayN emits STANDARD base64; the old
                // URL_SAFE-only decode silently dropped every '+' and
                // '/' (android.util.Base64 skips out-of-alphabet chars
                // instead of throwing), so links with a flag emoji in
                // `ps` — and ~3% of ASCII-named ones — never imported.
                // b64decode() normalises both alphabets first.
                val decoded = b64decode(rest) ?: return null
                val j = JSONObject(decoded)
                val vmessNet = j.optString("net", "tcp")
                // 1.0.1.2 (#101): a vmess link needs a real address and
                // port — optInt("port") returns 0 for a STRING port
                // ("443"), which used to import as a ":0" profile that
                // could never connect (and could kill an Auto group).
                // Same gate as the generic branch below.
                val vmessAddr = j.optString("add")
                val vmessPort = j.optInt("port", 0).takeIf { it != 0 }
                    ?: j.optString("port").toIntOrNull() ?: 0
                if (vmessAddr.isEmpty() || vmessPort == 0) return null
                return Parsed(
                    proto = "vmess",
                    addr = vmessAddr,
                    port = vmessPort,
                    id = j.optString("id"),
                    net = vmessNet,
                    tls = j.optString("tls"),
                    sni = j.optString("sni").ifEmpty { j.optString("host") },
                    path = j.optString("path"),
                    host = j.optString("host"),
                    flow = "",
                    // 1.0.1.18 (M12): v2rayN carries the TCP header type
                    // in "type" (e.g. "http") — the old code never read
                    // it, so the obfuscation the server expects was
                    // silently dropped.
                    headerType = j.optString("type"),
                    // 3.2.16 (#52): v2rayN carries the gRPC service
                    // name in the JSON `path` (PattNG VmessFmt) — the
                    // old code always sent "" and grpc never connected.
                    serviceName = if (vmessNet == "grpc") j.optString("path") else "",
                    alpn = j.optString("alpn"),
                    fp = j.optString("fp"),
                    echConfigList = j.optString("ech"),
                    echOutbound = j.optString("echOutbound"),
                    verifyPeerCertByName = j.optString("vcn"),
                    pinnedCA256 = j.optString("pcs"),
                    mldsa65Verify = j.optString("pqv"),
                    spiderX = j.optString("spx"),
                )
            }
            // SOCKS / HTTP may arrive as base64(user:pass@host:port).
            var effectiveRest = rest
            if ((proto == "socks" || proto == "http") && '@' !in effectiveRest) {
                // 1.0.1.18 (M8): only take the decode when it really is
                // a userinfo@host:port — android.util.Base64 skips
                // out-of-alphabet chars instead of throwing, so decoding
                // a bare "1.2.3.4:1080" "succeeds" with garbage. A
                // credential-less link keeps its rest as-is.
                val decoded = b64decode(effectiveRest)
                if (decoded != null && '@' in decoded &&
                    decoded.substringAfterLast('@').contains(':')
                ) effectiveRest = decoded
            }
            val atIdx = effectiveRest.lastIndexOf('@')
            if (atIdx < 0 && proto != "socks" && proto != "http") return null
            // RFC 3986 userinfo is percent-encoded (a trojan/hysteria2
            // password with '@', ':' or '+', a WireGuard key's '/', '=').
            // 1.0.1.18 (M8): a credential-less socks://host:port has no
            // '@' at all — parse it with an empty user instead of
            // rejecting it. A base64 userinfo segment (no ':') is
            // decoded too, or the whole blob would be sent as the
            // SOCKS username.
            val user = if (atIdx < 0) "" else {
                val rawUser = effectiveRest.substring(0, atIdx)
                if (':' !in rawUser) {
                    b64decode(rawUser)?.takeIf { ':' in it } ?: rawUser
                } else rawUser
            }.let { percentDecode(it) }
            val afterAt = if (atIdx < 0) effectiveRest else effectiveRest.substring(atIdx + 1)
            val hostPort = afterAt.substringBefore("?").substringBefore("/")
            val query = afterAt.substringAfter("?", "")
            val (addr, port) = splitHostPort(hostPort)
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
                // 1.0.1.18 (M5): trojan:// links in the common
                // trojan-gfw shape omit security= — but a Trojan server
                // IS a TLS server, so a missing value must mean TLS,
                // not plaintext.
                tls = q["security"] ?: if (proto == "trojan") "tls" else "",
                sni = q["sni"] ?: q["host"] ?: "",
                path = q["path"] ?: "",
                host = q["host"] ?: "",
                flow = q["flow"] ?: "",
                serviceName = q["serviceName"] ?: "",
                alpn = q["alpn"] ?: "",
                // 1.0.1.18 (M7): VLESS Encryption (e.g. the mlkem768
                // strings 3x-ui writes) — the outbound used to hardcode
                // "none", so encrypted servers rejected the client.
                encryption = q["encryption"] ?: "",
                // 3.2.16 (#52): TCP+HTTP-header obfuscation
                // (headerType=http) — parsed like PattNG's FmtBase.
                headerType = q["headerType"] ?: "",
                // 1.0.1.2 (#109): XHTTP mode (auto/packet-up/...) and
                // extra, from the link — PattNG's xhttpMode/xhttpExtra.
                xhttpMode = q["mode"] ?: "",
                xhttpExtra = q["extra"] ?: "",
                // 1.0.1.2 (#110): the same `mode` param is the gRPC
                // mode in PattNG (mode == "multi" → multiMode).
                grpcMode = q["mode"] ?: "",
                // 3.2.7 (PattNG FmtBase): fingerprint, allow-insecure
                // (insecure / allowInsecure / allow_insecure), cipher
                // suites (cs), Reality public key (pbk) + short id
                // (sid) + spiderX (spx). 3.2.9 (the rest): ECH config
                // list (ech) + ECH outbound JSON (echOutbound), verify
                // peer cert by name (vcn), pinned CA SHA-256 (pcs),
                // ML-DSA-65 verify key (pqv).
                fp = q["fp"] ?: "",
                allowInsecure = listOf("insecure", "allowInsecure", "allow_insecure")
                    .any { q[it] == "1" || q[it].equals("true", ignoreCase = true) },
                cipherSuites = q["cs"] ?: "",
                realityPublicKey = q["pbk"] ?: "",
                realityShortId = q["sid"] ?: "",
                spiderX = q["spx"] ?: "",
                echConfigList = q["ech"] ?: "",
                echOutbound = q["echOutbound"] ?: "",
                verifyPeerCertByName = q["vcn"] ?: "",
                // 1.0.1.18 (L6): pinSHA256 is the same pinned-CA hash
                // under its other name; without it a self-signed hy2
                // server has no working trust path at all.
                pinnedCA256 = q["pcs"] ?: q["pinSHA256"] ?: "",
                mldsa65Verify = q["pqv"] ?: "",
                // 3.2.0: WireGuard + Hysteria2 query params.
                // 1.0.1.18 (L6): some panels emit the WireGuard keys
                // lowercase — read both casings, or the peer key is
                // silently empty and the config is rejected by the core.
                wgPublicKey = q["publicKey"] ?: q["publickey"] ?: "",
                wgPresharedKey = q["presharedKey"] ?: q["presharedkey"] ?: "",
                wgAllowedIps = q["allowedIPs"] ?: q["allowedips"] ?: "",
                wgAddress = q["address"] ?: "",
                wgMtu = q["mtu"] ?: "",
                wgReserved = q["reserved"] ?: "",
                hyInsecure = q["insecure"] ?: "",
                // 1.0.2: salamander obfs password (obfs-password).
                obfsPassword = q["obfs-password"] ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * "host:port" or "[ipv6]:port" -> host (brackets removed) and port, 0
     * when missing/invalid. A bracketed IPv6 literal used to be split at
     * its first ':' ("[2001" / "db8::1]:443"), so every IPv6 link was
     * rejected.
     */
    internal fun splitHostPort(hostPort: String): Pair<String, Int> {
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end < 0) return "" to 0
            val host = hostPort.substring(1, end)
            val rest = hostPort.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: 0 else 0
            return host to (port.takeIf { it in 1..65535 } ?: 0)
        }
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "0").toIntOrNull() ?: 0
        return host to (port.takeIf { it in 1..65535 } ?: 0)
    }

    /**
     * Percent-decoding for URI userinfo. Unlike URLDecoder it keeps '+'
     * (a literal in base64 keys and passwords, not a space) and leaves a
     * malformed '%' sequence as is instead of throwing.
     */
    internal fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = java.io.ByteArrayOutputStream()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                bytes.write(plain.toString().toByteArray(Charsets.UTF_8))
                plain.setLength(0)
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 2 < text.length &&
                Character.digit(text[i + 1], 16) >= 0 && Character.digit(text[i + 2], 16) >= 0
            ) {
                flush()
                bytes.write(Character.digit(text[i + 1], 16) * 16 + Character.digit(text[i + 2], 16))
                i += 3
            } else {
                plain.append(c)
                i++
            }
        }
        flush()
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    /** Base64 (URL-safe first, then standard) with padding repaired. */
    private fun b64decode(text: String): String? {
        val clean = text.trim().replace(Regex("\\s"), "")
        if (clean.isEmpty()) return null
        // 3.2.16 (#48): both alphabets in ONE pass. A standard-base64
        // string never contains '-' or '_', so translate the URL-safe
        // pair and decode DEFAULT. The old code tried URL_SAFE first
        // and never reached DEFAULT: android.util.Base64 SKIPS
        // out-of-alphabet chars instead of throwing, so it "succeeded"
        // with garbage and the fallback never ran.
        val normalized = clean.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
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
            // SIP002: the userinfo is percent-encoded (a plain AEAD-2022
            // key's '+', '/', '=' / ':' arrive as %2B, %2F, %3D / %3A, and
            // base64 padding as %3D).
            val rawUser = percentDecode(noQuery.substringBeforeLast('@'))
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
        val (addr, port) = splitHostPort(hostPort.substringBefore("/"))
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
            // before) and cipher suites; REALITY builds realitySettings
            // instead of tlsSettings (publicKey/shortId from pbk/sid).
            // A link without either stays plain.
            // 3.2.9: allowInsecure is REMOVED in this Xray-core
            // (infra/conf TLSConfig.Build fails the whole config when
            // it is true, pointing at pcs/vcn instead) — it is parsed
            // and kept for the editor/round-trip, but NEVER emitted.
            // Pinning replaces it: pcs pins a cert hash, vcn pins the
            // name to verify.
            if (p.tls == "tls") {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    put("serverName", p.sni.ifEmpty { p.addr })
                    put("fingerprint", p.fp.ifEmpty { "chrome" })
                    if (p.alpn.isNotEmpty()) {
                        putJsonArray("alpn") { p.alpn.split(",").forEach { add(JsonPrimitive(it.trim())) } }
                    }
                    // 3.2.21: cipherSuites is a STRING, not an array — the
                    // fork's TLSConfig splits it on ":" (transport/
                    // internet/tls/config.go:460) and PattNG carries it
                    // as a String too (V2rayConfig.kt:227). The array
                    // this emitted before made the core reject the
                    // whole config ("cannot unmarshal array into Go
                    // struct field ... cipherSuites of type string"),
                    // so any profile, chain or Auto group with a `cs`
                    // link could not connect at all.
                    val suites = p.cipherSuites.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (suites.isNotEmpty()) {
                        put("cipherSuites", suites.joinToString(":"))
                    }
                    // 3.2.9 (PattNG FmtBase/CoreOutboundBuilder): ECH +
                    // pinning. echConfigList is the pinned base64 ECH
                    // config list; echOutbound is the raw JSON of the
                    // outbound the ECH query goes through — it is NOT
                    // emitted as-is; attachEchOutbounds (assemble)
                    // checks it, appends it and points echSockopt at
                    // its tag.
                    if (p.echConfigList.isNotEmpty()) put("echConfigList", p.echConfigList)
                    if (p.verifyPeerCertByName.isNotEmpty()) put("verifyPeerCertByName", p.verifyPeerCertByName)
                    if (p.pinnedCA256.isNotEmpty()) put("pinnedPeerCertSha256", p.pinnedCA256)
                    if (p.echOutbound.isNotEmpty()) put("echOutbound", p.echOutbound)
                }
            } else if (p.tls == "reality") {
                put("security", "reality")
                putJsonObject("realitySettings") {
                    put("serverName", p.sni.ifEmpty { p.addr })
                    put("fingerprint", p.fp.ifEmpty { "chrome" })
                    put("publicKey", p.realityPublicKey)
                    put("shortId", p.realityShortId)
                    if (p.spiderX.isNotEmpty()) put("spiderX", p.spiderX)
                    if (p.mldsa65Verify.isNotEmpty()) put("mldsa65Verify", p.mldsa65Verify)
                }
            }
            // 3.2.16 (#49): Fragment (3.1.0) split the TLS hello so DPI
            // sees noise — but the pinned core has NO streamSettings
            // .fragment key (its StreamConfig carries `finalmask`,
            // not `fragment`), so the old object was silently ignored
            // and Fragment did nothing. Fragmentation here is a TCP
            // finalmask: packets "tlshello", length/delay ranges
            // (infra/conf/transport_finalmask.go, FragmentMask).
            if (settings.fragmentEnabled) {
                putJsonObject("finalmask") {
                    putJsonArray("tcp") {
                        add(buildJsonObject {
                            put("type", "fragment")
                            putJsonObject("settings") {
                                put("packets", "tlshello")
                                put("length", "100-200")
                                put("delay", "10-20")
                            }
                        })
                    }
                }
            }
            when (net) {
                // 3.2.16 (#52): TCP+HTTP-header obfuscation — the
                // headerType was never parsed, so the header was lost.
                // Shape mirrors PattNG's createTcpHttpRequest.
                "tcp" -> if (p.headerType == "http") {
                    putJsonObject("tcpSettings") {
                        putJsonObject("header") {
                            put("type", "http")
                            putJsonObject("request") {
                                put("version", "1.1")
                                put("method", "GET")
                                putJsonArray("path") {
                                    val paths = p.path.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (paths.isEmpty()) add(JsonPrimitive("/")) else paths.forEach { add(JsonPrimitive(it)) }
                                }
                                putJsonObject("headers") {
                                    val hosts = p.host.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (hosts.isNotEmpty()) {
                                        putJsonArray("Host") { hosts.forEach { add(JsonPrimitive(it)) } }
                                    }
                                    putJsonArray("User-Agent") { add(JsonPrimitive("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")) }
                                    putJsonArray("Accept-Encoding") { add(JsonPrimitive("gzip, deflate")) }
                                    putJsonArray("Connection") { add(JsonPrimitive("keep-alive")) }
                                    put("Pragma", "no-cache")
                                }
                            }
                        }
                    }
                }
                "ws" -> putJsonObject("wsSettings") {
                    put("path", p.path.ifEmpty { "/" })
                    putJsonObject("headers") {
                        put("Host", p.host.ifEmpty { p.sni.ifEmpty { p.addr } })
                    }
                }
                // 3.2.16 (#52): httpupgrade links imported but lost
                // path/host (the core fell back to "/" and the
                // address).
                "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                    put("path", p.path.ifEmpty { "/" })
                    put("host", p.host.ifEmpty { p.sni.ifEmpty { p.addr } })
                }
                // 1.0.1.3: ZedSecure XrayJsonBuilder.streamSettingsBase
                // verbatim — ONE branch for "xhttp" + "splithttp", the
                // settings key is always "xhttpSettings", mode defaults
                // to "auto", and extra is parsed into a JSON OBJECT and
                // emitted only when it parses (PattNG does the same via
                // JsonUtil.parseString). 1.0.1.2 had two separate
                // branches (splithttpSettings is not a real settings
                // key) and emitted extra as a raw STRING — the fork's
                // loader unmarshals "extra" into conf.SplitHTTPConfig,
                // so the string killed the WHOLE core config and Auto
                // died on proxy@14.
                "xhttp", "splithttp" -> putJsonObject("xhttpSettings") {
                    put("path", p.path.ifEmpty { "/" })
                    put("host", p.host.ifEmpty { p.sni.ifEmpty { p.addr } })
                    put("mode", p.xhttpMode.ifEmpty { "auto" })
                    p.xhttpExtra.takeIf { it.isNotBlank() }?.let { raw ->
                        (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)
                            ?.let { put("extra", it) }
                    }
                }
                "grpc" -> putJsonObject("grpcSettings") {
                    put("serviceName", p.serviceName)
                    // 1.0.1.2 (#110): PattNG sends multiMode only when
                    // the link's mode is "multi" — the old code
                    // hardcoded true, so a link with no mode (like the
                    // live :8446 gRPC link) got multiMode=true. Whether
                    // that link behaves differently needs an on-device
                    // check — see the 1.0.1.2 notes.
                    put("multiMode", p.grpcMode == "multi")
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
     * Internal (not private) so the Aether exit-node outbound can be
     * built the same way a chain hop is (3.2.10).
     */
    internal fun outbound(
        p: Parsed,
        tag: String,
        dialerProxy: String? = null,
        settings: XraySettings = XraySettings(),
    ): JsonObject? {
        // Chain hop: Xray reads dialerProxy ONLY from
        // streamSettings.sockopt — the outbound object itself has no
        // "sockopt" field, and an unknown key there is silently ignored
        // (the hop would then dial its server directly).
        val sockopt: JsonObject? = dialerProxy?.let { dp -> buildJsonObject { put("dialerProxy", dp) } }
        val stream = streamSettings(p, settings).let { base ->
            if (sockopt != null) JsonObject(base + ("sockopt" to sockopt)) else base
        }
        fun withProxy(body: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
            put("tag", tag)
            body()
            put("streamSettings", stream)
            // MUX (3.1.0): several streams over one connection.
            // 3.2.0: concurrency + XUDP concurrency from settings.
            // 1.0.1.18 (H5): never on a VLESS Vision flow — the server
            // drops muxed Vision with "common/mux: unexpected network
            // TCP" (3x-ui #6418), so the tunnel comes up with zero
            // traffic — and never on SOCKS/HTTP outbounds, where mux
            // is meaningless.
            if (settings.muxEnabled && p.proto !in setOf("socks", "http") &&
                p.flow !in setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")
            ) {
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
                                    put("encryption", p.encryption.ifBlank { "none" })
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
            // 3.2.30 fixed the stream network ("hysteria", not
            // "udp") — but Hysteria2 still never connected, and
            // Auto (whose first member is the Hysteria2 link) died
            // with it. 1.0.2, root-caused against PattNG + the fork
            // (CluvexStudio/Xray-core @ 7b4c3ceb1534):
            //  1. The fork has NO "hysteria2" protocol at all:
            //     infra/conf/xray.go registers only "hysteria" ->
            //     HysteriaClientConfig{version, address, port}
            //     (version MUST be 2), and proxy/ has no hysteria2
            //     package. "hysteria2" made the core reject the
            //     whole config, so every Hysteria2 single connect
            //     AND every Auto group failed at build time.
            //     PattNG does exactly this: protocol "hysteria",
            //     settings {version: 2, address, port}.
            //  2. That client config has NO password field: the
            //     password lives only in the transport config as
            //     streamSettings.hysteriaSettings.auth
            //     (transport/internet/hysteria Config.Auth).
            //     PattNG: HysteriaSettingsBean(version = 2, auth).
            //  3. No ALPN: the fork's hysteria dialer sets no ALPN
            //     itself (no NextProtos/h3 in dialer.go), so the
            //     QUIC handshake needs tlsSettings.alpn ["h3"] —
            //     PattNG forces alpn = "h3" in toOutboundHysteria2.
            //  4. Salamander obfs: PattNG puts it in
            //     streamSettings.finalmask.udp as a mask
            //     {type: "salamander", settings: {password}}; the
            //     fork's finalmask has a salamander mask
            //     (transport/internet/finalmask/salamander).
            "hysteria2" -> buildJsonObject {
                put("tag", tag)
                put("protocol", "hysteria")
                putJsonObject("settings") {
                    put("version", 2)
                    put("address", p.addr)
                    put("port", p.port)
                }
                putJsonObject("streamSettings") {
                    put("network", "hysteria")
                    put("security", "tls")
                    putJsonObject("tlsSettings") {
                        put("serverName", p.sni.ifEmpty { p.addr })
                        putJsonArray("alpn") {
                            val alpn = p.alpn.ifEmpty { "h3" }
                            alpn.split(",").forEach { add(JsonPrimitive(it.trim())) }
                        }
                        // 3.2.9: allowInsecure is removed in this core —
                        // never emitted (see streamSettings). Pinning and
                        // ECH fields are honoured when the link carries
                        // them (PattNG applies its ECH outbound to
                        // Hysteria2 too, whose blank security is TLS).
                        if (p.pinnedCA256.isNotEmpty()) put("pinnedPeerCertSha256", p.pinnedCA256)
                        if (p.verifyPeerCertByName.isNotEmpty()) put("verifyPeerCertByName", p.verifyPeerCertByName)
                        if (p.echConfigList.isNotEmpty()) put("echConfigList", p.echConfigList)
                        if (p.echOutbound.isNotEmpty()) put("echOutbound", p.echOutbound)
                    }
                    putJsonObject("hysteriaSettings") {
                        put("version", 2)
                        put("auth", p.id)
                    }
                    if (p.obfsPassword.isNotEmpty()) {
                        putJsonObject("finalmask") {
                            putJsonArray("udp") {
                                add(buildJsonObject {
                                    put("type", "salamander")
                                    putJsonObject("settings") {
                                        put("password", p.obfsPassword)
                                    }
                                })
                            }
                        }
                    }
                    if (sockopt != null) put("sockopt", sockopt)
                }
            }
            // 3.2.0 (PattNG): WireGuard (no streamSettings, no mux).
            // 3.2.11 (PattNG CoreOutboundBuilder:426-436): with IPv6
            // off, IPv6 addresses and ::/0 are dropped from the
            // WireGuard addresses + allowed IPs.
            "wg" -> buildJsonObject {
                put("tag", tag)
                put("protocol", "wireguard")
                putJsonObject("settings") {
                    put("secretKey", p.id)
                    putJsonArray("address") {
                        val addrs = p.wgAddress.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        // With IPv6 off and an IPv6-only address list,
                        // keep the original list: an empty array is an
                        // invalid config, the connect itself then fails
                        // instead of the build.
                        val kept = addrs.filter { settings.ipv6Enabled || !it.contains(":") }
                        (kept.ifEmpty { addrs }).forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("peers") {
                        add(buildJsonObject {
                            put("publicKey", p.wgPublicKey)
                            // An IPv6 endpoint needs brackets: "[addr]:port".
                            val host = if (':' in p.addr) "[${p.addr}]" else p.addr
                            put("endpoint", "$host:${p.port}")
                            putJsonArray("allowedIPs") {
                                val allowed = p.wgAllowedIps.ifEmpty { "0.0.0.0/0,::/0" }
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                val kept = allowed.filter { settings.ipv6Enabled || !it.contains(":") }
                                (kept.ifEmpty { allowed }).forEach { add(JsonPrimitive(it)) }
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
                if (sockopt != null) {
                    putJsonObject("streamSettings") { put("sockopt", sockopt) }
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
        // 1.0.1.18 (L5): that requirement was only a comment — a JSON
        // without the inbound connected the tunnel into a port nothing
        // listens on (zero traffic, no error). Refuse it here instead.
        if (p.proto == "custom") {
            if (!hasSocksInboundOnPort(p.customJson, socksPort)) return null
            return p.customJson
        }
        val ob = outbound(p, "proxy", settings = settings) ?: return null
        return assemble(listOf(ob), socksPort, geoAvailable, settings)
    }

    /**
     * True when the custom JSON has a SOCKS (or mixed) inbound listening
     * on [port] — the inbound the tun bridge forwards into (L5).
     */
    private fun hasSocksInboundOnPort(json: String, port: Int): Boolean = try {
        val inbounds = JSONObject(json).optJSONArray("inbounds") ?: return false
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val protocol = inbound.optString("protocol")
            if (protocol != "socks" && protocol != "mixed") continue
            if (inbound.optInt("port", -1) == port) return true
        }
        false
    } catch (_: Exception) {
        false
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
     * Auto-select config (3.2.8, Damavand/ZedSecure-faithful) — a port
     * of Damavand's XrayJsonBuilder.buildAutoSelect/autoSelectGroup:
     * one outbound per member link (tags proxy@0..N) + one group
     * outbound, tag "proxy", protocol "autoselect", with the tuning
     * (probe URL + fallback, probe timeout, active/sweep intervals,
     * switch ratio, retry, initial member tag). The group is FIRST in
     * the outbounds list, so unmatched traffic defaults to it — the
     * same trick Damavand uses (its GroupBuild is listOf(group) +
     * memberOutbounds). Routing rules keep pointing at "proxy" and
     * never learn a group exists. Returns null when fewer than two
     * members (the caller falls back to a direct connect) or when a
     * member link does not parse.
     */
    fun buildAutoSelect(
        links: List<String>,
        tuning: AutoSelectTuning,
        initialTag: String? = null,
        socksPort: Int,
        geoAvailable: Boolean = false,
        settings: XraySettings = XraySettings(),
    ): String? {
        if (links.size < 2) return null
        // One bad member must not kill the whole Auto group (issue
        // #86): skip members that fail to parse or build, and refuse
        // only when nothing remains. Tags keep their original index
        // so a surviving member's "proxy@N" tag stays stable; an
        // initial tag naming a skipped member is dropped.
        val members = links.mapIndexedNotNull { i, link ->
            val p = parse(link) ?: run {
                AppLog.w("XrayConfig", "Auto: skipping unparseable member #$i")
                return@mapIndexedNotNull null
            }
            val ob = outbound(p, "proxy@$i", null, settings) ?: run {
                AppLog.w("XrayConfig", "Auto: skipping unbuildable member #$i")
                return@mapIndexedNotNull null
            }
            "proxy@$i" to ob
        }
        if (members.isEmpty()) return null
        val tags = members.map { it.first }
        val memberOutbounds = members.map { it.second }
        val initial = initialTag?.takeIf { it in tags }
        val group = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "autoselect")
            putJsonObject("settings") {
                putJsonArray("outbounds") { tags.forEach { add(JsonPrimitive(it)) } }
                val probe = tuning.probeUrl.ifBlank { "https://www.gstatic.com/generate_204" }
                put("probeURL", probe)
                if (probe != "https://cp.cloudflare.com/generate_204") {
                    put("fallbackProbeURL", "https://cp.cloudflare.com/generate_204")
                }
                put("probeTimeout", "${tuning.probeTimeoutSec.coerceIn(2, 20)}s")
                put("activeInterval", "${tuning.activeIntervalSec.coerceIn(10, 600)}s")
                put("sweepInterval", "${tuning.sweepIntervalMin.coerceIn(1, 120)}m")
                put("switchRatio", tuning.switchMarginPercent.coerceIn(5, 90) / 100.0)
                put("retry", tuning.retry)
                if (initial != null) put("initial", initial)
            }
        }
        return assemble(listOf(group) + memberOutbounds, socksPort, geoAvailable, settings)
    }

    /**
     * The streamSettings of an outbound that dials the Aether core
     * (3.2.27, PattNG CoreOutboundBuilder.applyDialMode): the
     * profile's dialMode goes into streamSettings.sockopt.dialMode.
     * Null when the core names no dialMode — the field is omitted.
     */
    private fun aetherDialStream(dialMode: String?): JsonObject? {
        val mode = dialMode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return buildJsonObject {
            putJsonObject("sockopt") { put("dialMode", mode) }
        }
    }

    /**
     * A freedom exit-node for an Aether core (3.2.27, PattNG
     * CoreOutboundBuilder.toOutboundAetherExit): the core's finalMask
     * as streamSettings.finalmask (no network — a freedom outbound
     * has no transport) and its dialMode in sockopt. An invalid
     * finalMask is skipped, as PattNG keeps the previous one.
     */
    private fun freedomExitNode(core: AetherCore): JsonObject = buildJsonObject {
        put("tag", AetherArgs.TAG_EXIT_NODE)
        put("protocol", "freedom")
        val stream = buildJsonObject {
            core.exit.finalMask?.let { mask ->
                runCatching { Json.parseToJsonElement(mask) }.getOrNull()?.let { put("finalmask", it) }
            }
            core.exit.dialMode?.trim()?.takeIf { it.isNotEmpty() }?.let { mode ->
                putJsonObject("sockopt") { put("dialMode", mode) }
            }
        }
        if (stream.isNotEmpty()) put("streamSettings", stream)
    }

    /**
     * Aether config (3.2.10) — a port of PattNG's AetherDependency.of /
     * routeThroughXray, adapted to Lite's [assemble] skeleton:
     *
     * - the "proxy" outbound is a SOCKS outbound dialling the Aether
     *   core on 127.0.0.1:[aetherPort] (the core's listener);
     * - the "exit-node" outbound is [exitOutbound] when the profile
     *   names an exit-node profile, else a plain freedom outbound;
     * - a "secondary-socks" mixed inbound on aetherPort+3 (the port
     *   the core dials out through, via `--upstream
     *   socks5://127.0.0.1:<aetherPort+3>`);
     * - a rule PREPENDED ahead of all other rules: inboundTag
     *   secondary-socks → exit-node.
     *
     * Returns the config JSON plus the core routed through Xray, or
     * null when the core's listeners cannot be read / reached (an
     * invalid or unreachable listener, an out-of-range port, or the
     * secondary port colliding with the local SOCKS/HTTP inbounds).
     * The caller starts Xray first, then the core with the returned
     * core's arguments.
     */
    fun buildAether(
        settings: AetherSettings,
        aetherPort: Int = AetherArgs.SOCKS_PORT,
        socksPort: Int,
        geoAvailable: Boolean = false,
        xraySettings: XraySettings = XraySettings(),
        exitOutbound: JsonObject? = null,
        // The exit-node profile's link digest, which fills the
        // node content of the core's exit key (PattNG's AetherExit).
        exitNodeContent: String? = null,
    ): Pair<String, AetherCore>? {
        val base = AetherCore.of(settings, aetherPort)
        val core = if (exitNodeContent != null) {
            base.copy(exit = base.exit.copy(nodeContent = exitNodeContent))
        } else {
            base
        }
        if (core.invalidListener != null || core.unreachableListener != null) return null
        if (AetherArgs.invalidPort(core) != null) return null
        // 1.0.1.2 (#107): the Aether listener and the local SOCKS
        // inbound would bind the same port — refuse the config
        // instead of failing the connect with a bind error.
        if (aetherPort == socksPort) return null
        val secondaryPort = aetherPort + AetherArgs.SECONDARY_SOCKS_OFFSET
        if (secondaryPort == socksPort || secondaryPort == socksPort + 1) return null
        val proxy = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "socks")
            // 3.2.27 (PattNG applyDialMode): the profile's dialMode
            // goes into streamSettings.sockopt.dialMode.
            aetherDialStream(core.exit.dialMode)?.let { put("streamSettings", it) }
            putJsonObject("settings") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("address", AetherArgs.LOOPBACK)
                        put("port", aetherPort)
                    })
                }
            }
        }
        val exit = exitOutbound ?: freedomExitNode(core)
        val secondarySocks = buildJsonObject {
            put("tag", AetherArgs.TAG_SECONDARY_SOCKS)
            put("port", secondaryPort)
            put("listen", AetherArgs.LOOPBACK)
            put("protocol", "mixed")
            putJsonObject("settings") { put("udp", true) }
        }
        val exitRule = buildJsonObject {
            putJsonArray("inboundTag") { add(JsonPrimitive(AetherArgs.TAG_SECONDARY_SOCKS)) }
            put("outboundTag", AetherArgs.TAG_EXIT_NODE)
        }
        val config = assemble(
            listOf(proxy, exit),
            socksPort,
            geoAvailable,
            xraySettings,
            extraInbounds = listOf(secondarySocks),
            extraRulesFirst = listOf(exitRule),
        ) ?: return null
        return config to core.through(secondaryPort)
    }

    /**
     * A chain with exactly one Aether hop (3.2.13) — a port of PattNG's
     * AetherDependency.of / routeThroughXray for chains:
     *
     * - the Aether hop's outbound is a SOCKS outbound dialling the core
     *   on 127.0.0.1:[aetherPort] directly — PattNG skips the Aether hop
     *   for dialerProxy (its listener is loopback; the core exits through
     *   secondary-socks, below), never a top-level "sockopt";
     * - the hops on its exit side dial through its outbound, as they
     *   dial through any hop;
     * - the core dials out through Xray's secondary-socks inbound on
     *   aetherPort+3 (via `--upstream`), which the prepended rule sends
     *   to the "exit-node" outbound — [exitOutbound] when the Aether
     *   hop is the entry hop and names an exit-node profile, else a
     *   plain freedom outbound (PattNG's routeThroughXray);
     * - the core's exit is the digest of the links on its entry side
     *   (AetherExit.through) when there is an entry side, else its own
     *   exit (with [exitNodeContent] when it names one) — as PattNG
     *   replaces the exit only for a chained hop with an entry side.
     *
     * Returns the config JSON plus the core routed through Xray, or
     * null when the members hold no (or more than one) Aether profile,
     * a member link does not parse, or the core fails the same guards
     * as [buildAether]. The caller starts Xray first, then the core.
     */
    fun buildChainWithAether(
        members: List<Profile>,
        aetherPort: Int = AetherArgs.SOCKS_PORT,
        socksPort: Int,
        geoAvailable: Boolean = false,
        xraySettings: XraySettings = XraySettings(),
        exitOutbound: JsonObject? = null,
        exitNodeContent: String? = null,
    ): Pair<String, AetherCore>? {
        val aetherIdx = members.indexOfFirst { it.isAether }
        if (aetherIdx < 0 || members.count { it.isAether } != 1) return null
        val aetherProfile = members[aetherIdx]
        val aetherSettings = AetherSettings.fromJson(aetherProfile.aetherJson)
        val entrySide = members.subList(0, aetherIdx)
        val base = AetherCore.of(aetherSettings, aetherPort)
        val core = when {
            entrySide.isNotEmpty() ->
                base.copy(exit = AetherExit.through(entrySide.map { it.link }))
            exitNodeContent != null ->
                base.copy(exit = base.exit.copy(nodeContent = exitNodeContent))
            else -> base
        }
        if (core.invalidListener != null || core.unreachableListener != null) return null
        if (AetherArgs.invalidPort(core) != null) return null
        // 1.0.1.18 (#107, chain path): the same guard as buildAether —
        // the Aether listener and the local SOCKS inbound must not
        // share a port, or the connect fails with a bind error.
        if (aetherPort == socksPort) return null
        val secondaryPort = aetherPort + AetherArgs.SECONDARY_SOCKS_OFFSET
        if (secondaryPort == socksPort || secondaryPort == socksPort + 1) return null
        // Resolved exit-first, tags like buildChain: the exit hop is
        // "proxy", the rest "proxy-chain-<i>"; hop i dials through
        // hop i+1, toward the entry, and the entry hop dials directly.
        // 3.2.27 (PattNG handleProxyChainResolvedOutbound): the hop
        // AFTER the Aether hop in this order is the core's exit-node —
        // it carries that tag, and the core dials out through it
        // (secondary-socks → exit-node), so no separate freedom
        // exit-node outbound is added when such a hop exists.
        val resolved = members.reversed()
        val aetherResolvedIdx = resolved.indexOfFirst { it.isAether }
        val exitNodeHopIdx = aetherResolvedIdx + 1
        val outbounds = resolved.mapIndexed { i, profile ->
            val tag = when {
                i == 0 -> "proxy"
                i == exitNodeHopIdx -> AetherArgs.TAG_EXIT_NODE
                else -> "proxy-chain-$i"
            }
            val dialerProxy = if (i < resolved.size - 1) "proxy-chain-${i + 1}" else null
            if (profile.isAether) {
                // PattNG skips the Aether hop for dialerProxy: this
                // SOCKS outbound dials the core on loopback, and the core
                // exits through secondary-socks (see below), so no
                // dialerProxy here — also never a top-level "sockopt":
                // Xray reads it only from streamSettings.sockopt.
                buildJsonObject {
                    put("tag", tag)
                    put("protocol", "socks")
                    // 3.2.27 (PattNG applyDialMode): the profile's
                    // dialMode goes into streamSettings.sockopt.
                    aetherDialStream(core.exit.dialMode)?.let { put("streamSettings", it) }
                    putJsonObject("settings") {
                        putJsonArray("servers") {
                            add(buildJsonObject {
                                put("address", AetherArgs.LOOPBACK)
                                put("port", aetherPort)
                            })
                        }
                    }
                }
            } else {
                val parsed = parse(profile.link) ?: return null
                outbound(parsed, tag, dialerProxy, xraySettings) ?: return null
            }
        }
        // PattNG lacksChainHop: the core's exit has hops (an entry
        // side) but no exit-node outbound exists — refuse the config,
        // or the core would reach the internet without its exit.
        val exit = if (exitNodeHopIdx < resolved.size) null
            else exitOutbound ?: freedomExitNode(core)
        val hasExitNode = exit != null ||
            outbounds.any { (it["tag"] as? JsonPrimitive)?.content == AetherArgs.TAG_EXIT_NODE }
        if (core.exit.hops != null && !hasExitNode) return null
        val secondarySocks = buildJsonObject {
            put("tag", AetherArgs.TAG_SECONDARY_SOCKS)
            put("port", secondaryPort)
            put("listen", AetherArgs.LOOPBACK)
            put("protocol", "mixed")
            putJsonObject("settings") { put("udp", true) }
        }
        val exitRule = buildJsonObject {
            putJsonArray("inboundTag") { add(JsonPrimitive(AetherArgs.TAG_SECONDARY_SOCKS)) }
            put("outboundTag", AetherArgs.TAG_EXIT_NODE)
        }
        val config = assemble(
            outbounds + listOfNotNull(exit),
            socksPort,
            geoAvailable,
            xraySettings,
            extraInbounds = listOf(secondarySocks),
            extraRulesFirst = listOf(exitRule),
        ) ?: return null
        return config to core.through(secondaryPort)
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
        // 3.2.10 (Aether): extra inbounds (the secondary-socks the
        // core dials out through) and rules PREPENDED ahead of the
        // custom rules (the exit-node rule).
        extraInbounds: List<JsonObject> = emptyList(),
        extraRulesFirst: List<JsonObject> = emptyList(),
    ): String? {
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
                // Xray-tun mode (3.2.9, PattNG): the core reads the VPN
                // interface itself from the "xray.tun.fd" env flag the
                // Go side sets — no HEV bridge in between.
                if (settings.tunMode) {
                    add(buildJsonObject {
                        put("tag", "tun")
                        put("protocol", "tun")
                        putJsonObject("settings") {
                            put("name", "xray0")
                            put("MTU", settings.mtu)
                        }
                        putJsonObject("sniffing") {
                            put("enabled", true)
                            putJsonArray("destOverride") {
                                add(JsonPrimitive("http"))
                                add(JsonPrimitive("tls"))
                            }
                        }
                    })
                }
                // Local proxy (3.2.9, PattNG): the SOCKS inbound — and
                // the HTTP one below it — exist only when the local
                // proxy is on. In VPN+HEV mode it is forced on by the
                // service (HEV forwards into it); in proxy-only mode
                // it is the whole point of the service.
                if (settings.localProxyEnabled) {
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
                // 3.2.10 (Aether): the secondary-socks inbound the core
                // dials out through — independent of the local proxy.
                extraInbounds.forEach { add(it) }
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
                    // 3.2.10 (Aether): the exit-node rule first — it
                    // wins over every rule below it.
                    extraRulesFirst.forEach { add(it) }
                    // Custom rules first — they win over the defaults.
                    if (settings.routingMode == "custom") {
                        settings.customRules.filter { it.enabled }.forEach {
                            // Geo patterns need the geo files: without
                            // them the core fails to start (issue #81).
                            // Drop them — the built-in ads rule below
                            // is gated on geoAvailable the same way.
                            val p = it.pattern.trim()
                            if (!geoAvailable && (p.startsWith("geosite:") || p.startsWith("geoip:"))) return@forEach
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
                    // Never in Global mode: there the catch-all would
                    // send EVERYTHING direct (issue #80) — Global means
                    // all traffic through the proxy by definition.
                    if (settings.routeOnly && settings.routingMode != "global") {
                        add(buildJsonObject {
                            put("type", "field")
                            put("outboundTag", "direct")
                        })
                    }
                    // Anything else falls through to the first outbound
                    // ("proxy") — for Auto that is the autoselect group.
                }
            }
        }
        val text = Json.encodeToString(JsonObject.serializer(), json)
        // 3.2.9 (PattNG EchOutbound.serialize): link the ECH outbounds.
        return attachEchOutbounds(text)
    }

    /**
     * ECH outbounds (3.2.9) — a port of PattNG's EchOutbound.serialize:
     * each outbound whose tlsSettings carries an "echOutbound" marker
     * (the raw JSON of the outbound the ECH config query goes through,
     * as written in the link/editor) is checked — a JSON object, next
     * to an echConfigList, with a tag that is not empty, not direct or
     * block, and does not start with "proxy" — then appended once to
     * the config after every other outbound, and the TLS settings get
     * echSockopt { dialerProxy: <tag> } pointing at it. Outbounds with
     * the same ECH outbound share it; a different one under a tag an
     * earlier one has gets a numbered tag ("ech-2"). A tag as written
     * that another outbound already has is a conflict, and an unchecked
     * ECH outbound fails the configuration rather than letting the
     * query go direct — so this returns null and the caller refuses
     * the connect, exactly like PattNG.
     */
    private fun attachEchOutbounds(jsonText: String): String? {
        val root = try { JSONObject(jsonText) } catch (_: Exception) { return null }
        val outbounds = root.optJSONArray("outbounds") ?: return jsonText
        val usedTags = HashSet<String>()
        for (i in 0 until outbounds.length()) {
            outbounds.optJSONObject(i)?.optString("tag")?.takeIf { it.isNotEmpty() }?.let { usedTags += it }
        }
        val linked = ArrayList<Pair<JSONObject, String>>()
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.optJSONObject(i) ?: continue
            val tls = ob.optJSONObject("streamSettings")?.optJSONObject("tlsSettings") ?: continue
            val marker = tls.optString("echOutbound", "")
            if (marker.isEmpty()) continue
            val ech = try { JSONObject(marker) } catch (_: Exception) { return null }
            if (tls.optString("echConfigList", "").isBlank()) return null
            val rawTag = ech.optString("tag", "")
            if (rawTag.isBlank() || rawTag == "direct" || rawTag == "block" || rawTag.startsWith("proxy")) return null
            val existing = linked.firstOrNull { it.first.toString() == ech.toString() }?.second
            val tag = existing ?: freeEchTag(rawTag, usedTags, linked.map { it.second })
            if (existing == null) linked.add(ech to tag)
            tls.put("echSockopt", JSONObject().put("dialerProxy", tag))
            tls.remove("echOutbound")
        }
        if (linked.any { it.second in usedTags }) return null
        for ((ech, tag) in linked) {
            ech.put("tag", tag)
            outbounds.put(ech)
        }
        return root.toString()
    }

    /** [tag] as written, or, when an earlier ECH outbound has it, its first number that no outbound has. */
    private fun freeEchTag(tag: String, usedTags: Set<String>, linkedTags: List<String>): String {
        if (tag !in linkedTags) return tag
        var number = 2
        while ("$tag-$number" in linkedTags || "$tag-$number" in usedTags) number++
        return "$tag-$number"
    }
}
