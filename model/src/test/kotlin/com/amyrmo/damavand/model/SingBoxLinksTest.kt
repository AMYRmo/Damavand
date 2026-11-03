package com.amyrmo.damavand.model

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The sing-box-only schemes (§3.5): tuic, anytls, naive, hysteria-1 and
 * socks4/4a are intercepted here and become sing-box outbound fragments;
 * hysteria2 stays with the Xray family and must NOT be claimed.
 */
class SingBoxLinksTest {

    private fun outboundOf(link: String) = SingBoxLinks.parse(link).outbound

    private fun typeOf(link: String): String? =
        outboundOf(link)["type"]?.jsonPrimitive?.content

    @Test
    fun `handles only its own schemes`() {
        assertTrue(SingBoxLinks.handles("tuic://u:p@h:443"))
        assertTrue(SingBoxLinks.handles("anytls://p@h:443"))
        assertTrue(SingBoxLinks.handles("naive+https://u:p@h:443"))
        assertTrue(SingBoxLinks.handles("naive+quic://u:p@h:443"))
        assertTrue(SingBoxLinks.handles("socks4://h:1080"))
        assertTrue(SingBoxLinks.handles("socks4a://h:1080"))
        assertFalse(SingBoxLinks.handles("hysteria2://p@h:443"))
        assertFalse(SingBoxLinks.handles("vless://u@h:443"))
        assertFalse(SingBoxLinks.handles("socks://u:p@h:1080"))
    }

    @Test
    fun `hysteria1 is distinguished from hysteria2`() {
        // hysteria-1: no userinfo, auth + peer query params.
        assertTrue(SingBoxLinks.handles("hysteria://hy1.example.com:443?auth=secret&peer=sni.example.com&upmbps=10&downmbps=50"))
        // hysteria-2: userinfo present, no v1 markers.
        assertFalse(SingBoxLinks.handles("hysteria://authpass@hy2.example.com:443?sni=hy2.example.com"))
        assertFalse(SingBoxLinks.handles("hysteria2://authpass@hy2.example.com:443"))
    }

    @Test
    fun `tuic link becomes a tuic outbound fragment`() {
        val parsed = SingBoxLinks.parse(
            "tuic://b831381d-6324-4d53-ad4f-8cda48b30811:tuicpass@tuic.example.com:443" +
                "?congestion_control=bbr&udp_relay_mode=native&sni=tuic.example.com#TUIC",
        )
        assertEquals("tuic", parsed.outbound["type"]?.jsonPrimitive?.content)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", parsed.outbound["uuid"]?.jsonPrimitive?.content)
        assertEquals("tuicpass", parsed.outbound["password"]?.jsonPrimitive?.content)
        assertEquals("bbr", parsed.outbound["congestion_control"]?.jsonPrimitive?.content)
        assertTrue(parsed.fragment.contains("\"outbounds\""))
        assertTrue(parsed.fragment.contains("\"tuic\""))
    }

    @Test
    fun `anytls link becomes an anytls outbound`() {
        assertEquals("anytls", typeOf("anytls://anypass@at.example.com:8443?sni=at.example.com#AT"))
        val out = outboundOf("anytls://anypass@at.example.com:8443?sni=at.example.com#AT")
        assertEquals("anypass", out["password"]?.jsonPrimitive?.content)
        assertEquals(8443, out["server_port"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `naive links`() {
        val https = outboundOf("naive+https://user:npass@nv.example.com:443#NV")
        assertEquals("naive", https["type"]?.jsonPrimitive?.content)
        assertEquals("user", https["username"]?.jsonPrimitive?.content)
        val quic = outboundOf("naive+quic://user:npass@nv.example.com:443#NVQ")
        assertEquals("true", quic["quic"]?.jsonPrimitive?.content)
    }

    @Test
    fun `hysteria1 link becomes a hysteria outbound`() {
        val out = outboundOf(
            "hysteria://hy1.example.com:443?auth=secret&peer=sni.example.com&upmbps=10&downmbps=50#HY1",
        )
        assertEquals("hysteria", out["type"]?.jsonPrimitive?.content)
        assertEquals("secret", out["auth_str"]?.jsonPrimitive?.content)
        assertEquals("sni.example.com", out["tls"]?.jsonObject?.get("server_name")?.jsonPrimitive?.content)
    }

    @Test
    fun `socks4a link becomes a versioned socks outbound`() {
        val out = outboundOf("socks4a://s4.example.com:1080#S4")
        assertEquals("socks", out["type"]?.jsonPrimitive?.content)
        assertEquals("4a", out["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun `build exports a tuic outbound back to a tuic link`() {
        val parsed = SingBoxLinks.parse("tuic://uuid-1:pass@tuic.example.com:443?congestion_control=bbr#TUIC")
        val link = SingBoxLinks.build(parsed.outbound, parsed.name)
        assertNotNull(link)
        assertTrue(link!!.startsWith("tuic://"))
        val reparsed = SingBoxLinks.parse(link)
        assertEquals(parsed.outbound["uuid"], reparsed.outbound["uuid"])
        assertEquals(parsed.outbound["server"], reparsed.outbound["server"])
    }
}
