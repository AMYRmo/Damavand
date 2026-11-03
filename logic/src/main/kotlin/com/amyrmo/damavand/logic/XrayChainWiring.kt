package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.Protocol
import com.amyrmo.damavand.model.ServerConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class UdpHopUnsupportedException(val member: String, val previous: String) :
    Exception("$member cannot be chained after $previous, which does not carry UDP")

/**
 * Chain surgery for Xray configs (architecture §3.4): collapsing a run of
 * consecutive Xray link profiles into ONE Xray instance whose outbounds are
 * linked by `dialerProxy` (§4.4), and splicing an already-built config onto
 * a carrier SOCKS port. Ported from v1's XrayJsonBuilder chain half.
 *
 * v2 narrows v1's ChainHop to Xray link profiles only: sing-box fragments
 * and raw-JSON configs are real hops of their own adapters in v2 (§3.2),
 * spliced over loopback SOCKS, never compiled into an Xray config.
 *
 * One extension vs v1: [ChainHop.Xray.needsUdp] also counts KCP transports,
 * per the normative validation rule §4.7 V5 ("WG/Hysteria/KCP").
 */
object XrayChainWiring {

    /** Transports whose traffic is UDP datagrams (validation rule V5). */
    private val UDP_NETWORKS = setOf("kcp", "mkcp")

    sealed interface ChainHop {
        val label: String
        val needsUdp: Boolean
        val carriesUdp: Boolean

        data class Xray(val server: ServerConfig) : ChainHop {
            override val label: String get() = server.protocol.label
            override val needsUdp: Boolean
                get() = server.protocol.isWireguardFamily || server.protocol == Protocol.HYSTERIA ||
                    server.transport.network.lowercase() in UDP_NETWORKS
            override val carriesUdp: Boolean get() = server.protocol.carriesUdp
        }
    }

    fun buildChain(
        servers: List<ServerConfig>,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        options: XrayConfigBuilder.BuildOptions = XrayConfigBuilder.BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String = buildChainHops(servers.map { ChainHop.Xray(it) }, socksPort, options, forSpeedtest)

    /**
     * One Xray config for the whole run. Outbounds are emitted exit-first;
     * each hop's outbound dials through the previous hop's outbound via
     * `dialerProxy`, and the exit hop carries the tag `proxy` (§4.4).
     */
    fun buildChainHops(
        hops: List<ChainHop>,
        socksPort: Int = LocalProxy.SOCKS_PORT,
        options: XrayConfigBuilder.BuildOptions = XrayConfigBuilder.BuildOptions(),
        forSpeedtest: Boolean = false,
    ): String {
        require(hops.isNotEmpty()) { "a proxy chain needs at least one member" }
        val servers = hops.map { hop ->
            when (hop) {
                is ChainHop.Xray -> hop.server
            }
        }
        servers.forEach { server ->
            val net = server.transport.network.lowercase()
            if (net in XrayConfigBuilder.REMOVED_NETWORKS) throw UnsupportedTransportException(net)
        }
        hops.forEachIndexed { i, hop ->
            if (hop.needsUdp && i > 0 && !hops[i - 1].carriesUdp) {
                throw UdpHopUnsupportedException(member = hop.label, previous = hops[i - 1].label)
            }
        }
        val last = hops.lastIndex
        fun tagOf(i: Int) = if (i == last) "proxy" else "proxy-h$i"
        return XrayConfigBuilder.assemble(socksPort, options, forSpeedtest) {
            for (i in last downTo 0) {
                val dialer = when {
                    i > 0 -> tagOf(i - 1)
                    else -> XrayConfigBuilder.entryDialer(options)
                }
                add(
                    XrayConfigBuilder.proxyOutbound(
                        servers[i], options, tag = tagOf(i), dialerProxy = dialer,
                    ),
                )
            }
        }
    }

    fun withSocksPort(config: String, from: Int, to: Int): String {
        if (from == to) return config
        val root = runCatching { XrayConfigBuilder.leanJson.parseToJsonElement(Jsonc.strip(config)) as? JsonObject }.getOrNull() ?: return config
        val inbounds = root["inbounds"] as? JsonArray ?: return config
        val moved = JsonArray(
            inbounds.map { element ->
                val inbound = element as? JsonObject ?: return@map element
                if ((inbound["port"] as? JsonPrimitive)?.content?.toIntOrNull() == from) {
                    JsonObject(inbound + ("port" to JsonPrimitive(to)))
                } else {
                    inbound
                }
            },
        )
        return JsonObject(root + ("inbounds" to moved)).toString()
    }

    fun withLocalPorts(config: String, from: Int, to: Int): String =
        if (from == to) config else withSocksPort(withSocksPort(config, from + 1, to + 1), from, to)

    fun withCarrierProxy(configJson: String, carrierPort: Int): String {
        val root = XrayConfigBuilder.leanJson.parseToJsonElement(Jsonc.strip(configJson)) as JsonObject

        val outbounds = root["outbounds"] as? JsonArray
            ?: throw IllegalArgumentException("config has no outbounds; cannot attach a carrier")
        if (outbounds.isEmpty()) {
            throw IllegalArgumentException("config has an empty outbounds list; cannot attach a carrier")
        }
        val byTag = outbounds.mapNotNull { ob ->
            val o = ob as? JsonObject ?: return@mapNotNull null
            val t = o["tag"]?.jsonPrimitiveOrNull()?.content ?: return@mapNotNull null
            t to o
        }.toMap()
        fun dialerTagOf(o: JsonObject): String? =
            (o["streamSettings"] as? JsonObject)?.let { it["sockopt"] as? JsonObject }
                ?.get("dialerProxy")?.jsonPrimitiveOrNull()?.content
        var entry = outbounds.first() as? JsonObject ?: return configJson

        var steps = 0
        while (steps++ < outbounds.size) {
            val next = dialerTagOf(entry)?.takeIf { it != "fragment" }?.let { byTag[it] } ?: break
            entry = next
        }

        val patched = buildJsonObject {
            root.forEach { (k, v) ->
                if (k != "outbounds") { put(k, v); return@forEach }
                putJsonArray("outbounds") {
                    outbounds.forEach { element ->
                        val ob = element as? JsonObject
                        val tag = ob?.get("tag")?.jsonPrimitiveOrNull()?.content

                        if (tag == "fragment") return@forEach

                        if (ob == null || ob !== entry) { add(element); return@forEach }
                        add(withCarrierDialer(ob))
                    }
                    add(XrayConfigBuilder.chainOutOutbound(carrierPort))
                }
            }
        }
        return XrayConfigBuilder.prettyJson.encodeToString(JsonObject.serializer(), patched)
    }

    private fun withCarrierDialer(outbound: JsonObject): JsonObject {
        val stream = outbound["streamSettings"] as? JsonObject
        val sockopt = stream?.get("sockopt") as? JsonObject
        val newSockopt = buildJsonObject {
            var replaced = false
            sockopt?.forEach { (k, v) ->
                if (k == "dialerProxy") { put(k, JsonPrimitive(XrayConfigBuilder.CHAIN_OUT_TAG)); replaced = true } else put(k, v)
            }
            if (!replaced) put("dialerProxy", XrayConfigBuilder.CHAIN_OUT_TAG)
        }
        val newStream = buildJsonObject {
            var replaced = false
            stream?.forEach { (k, v) ->
                if (k == "sockopt") { put(k, newSockopt); replaced = true } else put(k, v)
            }
            if (!replaced) put("sockopt", newSockopt)
        }
        return buildJsonObject {
            var replaced = false
            outbound.forEach { (k, v) ->
                if (k == "streamSettings") { put(k, newStream); replaced = true } else put(k, v)
            }
            if (!replaced) put("streamSettings", newStream)
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? =
        this as? JsonPrimitive
}
