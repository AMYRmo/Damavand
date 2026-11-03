package com.amyrmo.damavand.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Golden tests for the sing-box bridge + prepareForDevice (§3.4). */
class BridgeConfigBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun golden(name: String): JsonObject =
        json.parseToJsonElement(
            requireNotNull(javaClass.getResource("/golden/$name")) { "missing golden $name" }.readText(),
        ).jsonObject

    private fun parsed(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    @Test
    fun `bridge golden - typed DoH DNS detoured through the exit hop`() {
        val r = BridgeConfigBuilder.bridge(exitSocksPort = 18200, localPort = 10808)
        assertEquals(golden("bridge-default.json"), parsed(r.json))
        assertEquals(10808, r.localPort)
        val dns = parsed(r.json).getValue("dns").jsonObject
        val server = dns.getValue("servers").jsonArray.single().jsonObject
        assertEquals("https", server.getValue("type").jsonPrimitive.content)
        assertEquals("proxy", server.getValue("detour").jsonPrimitive.content)
        assertEquals("bridge-dns", dns.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun `bridge golden - profile DNS URL splits host and path`() {
        val r = BridgeConfigBuilder.bridge(exitSocksPort = 18200, localPort = 10808, dns = "https://dns.google/dns-query")
        assertEquals(golden("bridge-dns-url.json"), parsed(r.json))
        val server = parsed(r.json).getValue("dns").jsonObject
            .getValue("servers").jsonArray.single().jsonObject
        assertEquals("dns.google", server.getValue("server").jsonPrimitive.content)
        assertEquals("/dns-query", server.getValue("path").jsonPrimitive.content)
    }

    @Test
    fun `dns choice parsing matches the v1 rules`() {
        assertEquals(
            BridgeConfigBuilder.BridgeDnsChoice("1.1.1.1", "/dns-query"),
            BridgeConfigBuilder.dnsChoiceOf(""),
        )
        assertEquals(
            BridgeConfigBuilder.BridgeDnsChoice("dns.quad9.net", "/dns-query"),
            BridgeConfigBuilder.dnsChoiceOf("dns.quad9.net"),
        )
        assertEquals(
            BridgeConfigBuilder.BridgeDnsChoice("8.8.8.8", "/dns-query"),
            BridgeConfigBuilder.dnsChoiceOf("https://8.8.8.8"),
        )
        assertEquals(
            BridgeConfigBuilder.BridgeDnsChoice("1.1.1.1", "/dns-query"),
            BridgeConfigBuilder.dnsChoiceOf("udp://9.9.9.9"),
        )
    }

    @Test
    fun `proxy-only bridge has no tun inbound but keeps the mixed listener`() {
        val r = BridgeConfigBuilder.bridge(exitSocksPort = 18200, localPort = 10808, tun = false)
        val inbounds = parsed(r.json).getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals(listOf("mixed"), inbounds.map { it.getValue("type").jsonPrimitive.content })
        assertEquals(10808, inbounds.single().getValue("listen_port").jsonPrimitive.int)
    }

    @Test
    fun `prepareForDevice adds a TUN inbound and flattens routing`() {
        val raw = """{"log":{"level":"info"},"inbounds":[{"type":"socks","tag":"in","listen":"0.0.0.0","listen_port":2080}],"outbounds":[{"type":"vless","tag":"proxy","server":"vl.example.com","server_port":443}],"route":{"final":"proxy","rules":[{"domain_suffix":[".ir"],"outbound":"direct"}]}}"""
        val p = BridgeConfigBuilder.prepareForDevice(raw)
        assertEquals(golden("singbox-prepare-add-tun.json"), parsed(p.configJson))
        assertEquals(2080, p.socksPort)
        assertTrue(p.dnsProxyWorks)
        val route = parsed(p.configJson).getValue("route").jsonObject
        assertFalse(route.containsKey("rules"), "routing is forced global")
    }

    @Test
    fun `prepareForDevice keeps an existing TUN inbound`() {
        val raw = """{"inbounds":[{"type":"tun","tag":"tun-in","address":["172.19.0.1/30"],"auto_route":true},{"type":"mixed","tag":"mixed-in","listen":"0.0.0.0","listen_port":7890}],"outbounds":[{"type":"direct","tag":"direct"}],"route":{"final":"direct"}}"""
        val p = BridgeConfigBuilder.prepareForDevice(raw)
        assertEquals(golden("singbox-prepare-existing-tun.json"), parsed(p.configJson))
        assertEquals(7890, p.socksPort)
        val tuns = parsed(p.configJson).getValue("inbounds").jsonArray
            .count { it.jsonObject["type"]?.jsonPrimitive?.content == "tun" }
        assertEquals(1, tuns, "no second TUN inbound is synthesised")
    }

    @Test
    fun `prepareForDevice passes unparseable input through unchanged`() {
        val p = BridgeConfigBuilder.prepareForDevice("not json at all")
        assertEquals("not json at all", p.configJson)
        assertEquals(BridgeConfigBuilder.STANDALONE_SOCKS_PORT, p.socksPort)
        assertFalse(p.dnsProxyWorks)
    }
}
