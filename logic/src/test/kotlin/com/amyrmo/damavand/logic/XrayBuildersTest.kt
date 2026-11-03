package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.ConfigParseException
import com.amyrmo.damavand.model.ConfigParser
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
import org.junit.jupiter.api.assertThrows
import java.util.Base64

/** Golden + behaviour tests for the ported Xray builders (§3.4). JSON is
 * compared structurally: both sides parsed to JsonElement. */
class XrayBuildersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun golden(name: String): JsonObject =
        json.parseToJsonElement(
            requireNotNull(javaClass.getResource("/golden/$name")) { "missing golden $name" }.readText(),
        ).jsonObject

    private fun built(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    private val vlessLink =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@vl.example.com:443" +
            "?encryption=none&security=tls&sni=vl.example.com&type=ws&host=cdn.example.com&path=%2Fws#VL-WS"

    @Test
    fun `vless ws tls golden`() {
        assertEquals(golden("xray-vless-ws.json"), built(XrayConfigBuilder.build(ConfigParser.parse(vlessLink))))
    }

    @Test
    fun `vmess golden`() {
        val inner = """{"v":"2","ps":"VMess TCP","add":"vm.example.com","port":"8443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto","net":"tcp","type":"none","host":"","path":"","tls":"tls","sni":"vm.example.com"}"""
        val cfg = ConfigParser.parse("vmess://" + b64(inner))
        assertEquals(golden("xray-vmess.json"), built(XrayConfigBuilder.build(cfg)))
    }

    @Test
    fun `wireguard golden`() {
        val cfg = ConfigParser.parse(
            "wireguard://yAnz5TF%2BlXXJte14tji3zlMNq%2Bhd2rYUIgJBgB3fBmk%3D@wg.example.com:51820" +
                "?publickey=TrMvSoPULSEHXZqi724FtoSsCgu8F%2B9esj%2F2oPhCY%3D&address=10.0.0.2%2F32&dns=1.1.1.1&mtu=1280#WG",
        )
        assertEquals(golden("xray-wireguard.json"), built(XrayConfigBuilder.build(cfg)))
    }

    @Test
    fun `removed transport is rejected`() {
        // v1's removed set is {h2, h3, http, quic}; gRPC is NOT removed.
        val cfg = ConfigParser.parse("vless://uuid-1@h.example.com:443?type=h2#x")
        assertThrows<UnsupportedTransportException> { XrayConfigBuilder.build(cfg) }
    }

    @Test
    fun `shadowsocks none cipher is rejected with the v1 typed error`() {
        // The v2 parser refuses it at parse time with the typed error…
        assertThrows<ConfigParseException> {
            ConfigParser.parse("ss://${b64("none:secret")}@ss.example.com:8388#x")
        }
        // …and a raw JSON config carrying one is refused by the outbound
        // sanitiser (v1's UNSUPPORTED_SS_CIPHERS = {none, plain}).
        val raw = """{"outbounds":[{"protocol":"shadowsocks","tag":"proxy","settings":{"servers":[{"address":"ss.example.com","port":8388,"method":"none","password":"secret"}]}}]}"""
        val ex = assertThrows<ConfigParseException> { XrayConfigBuilder.normalizeRawJson(raw) }
        assertEquals(ConfigParseException.Reason.UnsupportedSsCipher, ex.reason)
    }

    @Test
    fun `two hop chain golden - dialerProxy links exit to entry`() {
        val trojan = ConfigParser.parse("trojan://s3cret@tj.example.com:443?sni=tj.example.com&alpn=h2#Trojan%20One")
        val out = XrayChainWiring.buildChain(listOf(ConfigParser.parse(vlessLink), trojan))
        assertEquals(golden("xray-chain-2hop.json"), built(out))
        val outbounds = built(out).getValue("outbounds").jsonArray.map { it.jsonObject }
        assertEquals("proxy", outbounds[0].getValue("tag").jsonPrimitive.content)
        val sockopt = outbounds[0].getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject
        assertEquals("proxy-h0", sockopt.getValue("dialerProxy").jsonPrimitive.content)
        assertEquals("proxy-h0", outbounds[1].getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun `udp hop after a tcp-only carrier is refused`() {
        val http = ConfigParser.parse("http://user:pass@px.example.com:8080#h")
        val wg = ConfigParser.parse(
            "wireguard://yAnz5TF%2BlXXJte14tji3zlMNq%2Bhd2rYUIgJBgB3fBmk%3D@wg.example.com:51820" +
                "?publickey=TrMvSoPULSEHXZqi724FtoSsCgu8F%2B9esj%2F2oPhCY%3D&address=10.0.0.2%2F32#WG",
        )
        assertThrows<UdpHopUnsupportedException> { XrayChainWiring.buildChain(listOf(http, wg)) }
    }

    @Test
    fun `withCarrierProxy golden`() {
        val base = XrayConfigBuilder.build(ConfigParser.parse(vlessLink))
        assertEquals(golden("xray-carrier.json"), built(XrayChainWiring.withCarrierProxy(base, 19999)))
    }

    @Test
    fun `withSocksPort moves only the socks inbound`() {
        val base = XrayConfigBuilder.build(ConfigParser.parse(vlessLink))
        val moved = built(XrayChainWiring.withSocksPort(base, 10808, 19000))
        val inbound = moved.getValue("inbounds").jsonArray.single().jsonObject
        assertEquals(19000, inbound.getValue("port").jsonPrimitive.int)
    }

    @Test
    fun `normalizeRawJson golden`() {
        val raw = """{"inbounds":[{"tag":"socks-in","port":10808,"protocol":"socks","listen":"127.0.0.1","settings":{"udp":true}}],"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"vl.example.com","port":443,"users":[{"id":"b831381d-6324-4d53-ad4f-8cda48b30811","encryption":"none"}]}]}},{"protocol":"freedom","tag":"direct"}]}"""
        val out = XrayConfigBuilder.normalizeRawJson(raw)
        assertEquals(golden("xray-raw-normalized.json"), built(out))
        val root = built(out)
        // v1 semantics: user sections are preserved/patched, stats+policy
        // are guaranteed, but no DNS/routing section is invented.
        assertTrue(root.containsKey("stats"))
        assertTrue(root.containsKey("policy"))
        assertFalse(root.containsKey("dns"))
    }

    @Test
    fun `normalizeRawJson moves the socks inbound to the requested port`() {
        val raw = """{"inbounds":[{"tag":"socks-in","port":10808,"protocol":"socks","listen":"0.0.0.0"}],"outbounds":[{"protocol":"freedom","tag":"direct"}]}"""
        val root = built(XrayConfigBuilder.normalizeRawJson(raw, socksPort = 19001))
        val inbound = root.getValue("inbounds").jsonArray.single().jsonObject
        assertEquals(19001, inbound.getValue("port").jsonPrimitive.int)
        assertEquals("127.0.0.1", inbound.getValue("listen").jsonPrimitive.content)
    }
}
