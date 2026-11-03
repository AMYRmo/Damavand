package com.amyrmo.damavand.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Parser goldens (§6.2): every supported scheme, both vmess forms,
 * WireGuard .conf import, and the historical traps — hostname endpoints
 * kept verbatim, IPv6 bracketed exactly once, `#label` never lost.
 * Fixtures are ordinary real-world-shaped links.
 */
class ConfigParserTest {

    private fun b64(text: String): String =
        Base64.getEncoder().encodeToString(text.encodeToByteArray())

    // ---------- vless ----------

    @Test
    fun `vless reality link`() {
        val c = ConfigParser.parse(
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@vl.example.com:443" +
                "?security=reality&encryption=none&pbk=pubkey123&flow=xtls-rprx-vision" +
                "&type=tcp&sni=www.microsoft.com&fp=chrome&sid=ab12#VLESS%20Reality",
        )
        assertEquals(Protocol.VLESS, c.protocol)
        assertEquals("VLESS Reality", c.remark)
        assertEquals("vl.example.com", c.address)
        assertEquals(443, c.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", c.userId)
        assertEquals("xtls-rprx-vision", c.flow)
        assertEquals("reality", c.tls.security)
        assertEquals("pubkey123", c.tls.publicKey)
        assertEquals("www.microsoft.com", c.tls.sni)
    }

    @Test
    fun `vless ipv6 endpoint is unbracketed in the model`() {
        val c = ConfigParser.parse("vless://uuid-1@[2001:db8::1]:8443?type=tcp&security=tls#v6")
        assertEquals("2001:db8::1", c.address)
        assertEquals(8443, c.port)
    }

    @Test
    fun `vless hostname endpoint is kept verbatim`() {
        val c = ConfigParser.parse("vless://uuid-1@engage.cloudflareclient.com:2408?type=ws&path=%2F#host")
        assertEquals("engage.cloudflareclient.com", c.address)
        assertEquals(2408, c.port)
        assertEquals("ws", c.transport.network)
        assertEquals("/", c.transport.path)
    }

    // ---------- vmess ----------

    @Test
    fun `vmess json form`() {
        val json = """{"v":"2","ps":"VMess WS","add":"vm.example.com","port":"443",""" +
            """"id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto","net":"ws",""" +
            """"type":"none","host":"cdn.example.com","path":"/ws","tls":"tls","sni":"sni.example.com"}"""
        val c = ConfigParser.parse("vmess://" + b64(json))
        assertEquals(Protocol.VMESS, c.protocol)
        assertEquals("VMess WS", c.remark)
        assertEquals("vm.example.com", c.address)
        assertEquals(443, c.port)
        assertEquals("ws", c.transport.network)
        assertEquals("cdn.example.com", c.transport.host)
        assertEquals("/ws", c.transport.path)
        assertEquals("tls", c.tls.security)
        assertEquals("sni.example.com", c.tls.sni)
    }

    @Test
    fun `vmess standard url form`() {
        val c = ConfigParser.parse(
            "vmess://b831381d-6324-4d53-ad4f-8cda48b30811@vm2.example.com:8443" +
                "?type=ws&security=tls&path=%2Fray&host=cdn2.example.com#std",
        )
        assertEquals(Protocol.VMESS, c.protocol)
        assertEquals("std", c.remark)
        assertEquals("vm2.example.com", c.address)
        assertEquals(8443, c.port)
        assertEquals("/ray", c.transport.path)
    }

    // ---------- trojan / ss / socks / http ----------

    @Test
    fun `trojan link`() {
        val c = ConfigParser.parse("trojan://s3cret@tj.example.com:443?sni=tj.example.com&alpn=h2#Trojan%20One")
        assertEquals(Protocol.TROJAN, c.protocol)
        assertEquals("s3cret", c.userId)
        assertEquals("tj.example.com", c.address)
        assertEquals("tls", c.tls.security)
        assertEquals("Trojan One", c.remark)
    }

    @Test
    fun `shadowsocks sip002 link`() {
        val creds = b64("aes-256-gcm:password123")
        val c = ConfigParser.parse("ss://$creds@ss.example.com:8388#SS%20Node")
        assertEquals(Protocol.SHADOWSOCKS, c.protocol)
        assertEquals("aes-256-gcm", c.shadowsocksMethod)
        assertEquals("password123", c.userId)
        assertEquals("ss.example.com", c.address)
        assertEquals(8388, c.port)
        assertEquals("SS Node", c.remark)
    }

    @Test
    fun `shadowsocks legacy whole-base64 link`() {
        val inner = "chacha20-ietf-poly1305:pw@legacy.example.com:1080"
        val c = ConfigParser.parse("ss://" + b64(inner) + "#legacy")
        assertEquals("chacha20-ietf-poly1305", c.shadowsocksMethod)
        assertEquals("pw", c.userId)
        assertEquals("legacy.example.com", c.address)
        assertEquals(1080, c.port)
    }

    @Test
    fun `shadowsocks without cipher is rejected with reason`() {
        val ex = assertThrows(ConfigParseException::class.java) {
            ConfigParser.parse("ss://@host.example.com:8388#x")
        }
        assertEquals(ConfigParseException.Reason.MissingSsCipher, ex.reason)
    }

    @Test
    fun `socks link with credentials`() {
        val c = ConfigParser.parse("socks://user:pass@127.0.0.1:1080#local")
        assertEquals(Protocol.SOCKS, c.protocol)
        assertEquals("user", c.username)
        assertEquals("pass", c.userId)
        assertEquals(1080, c.port)
    }

    // ---------- hysteria2 ----------

    @Test
    fun `hysteria2 link`() {
        val c = ConfigParser.parse(
            "hysteria2://authpass@hy.example.com:443?obfs=salamander&obfs-password=obfspw" +
                "&sni=hy.example.com&mport=20000-30000#HY2",
        )
        assertEquals(Protocol.HYSTERIA, c.protocol)
        assertEquals("authpass", c.secretKey)
        assertEquals("obfspw", c.obfsPassword)
        assertEquals("20000-30000", c.portHopping)
    }

    // ---------- wireguard ----------

    @Test
    fun `wireguard link`() {
        val c = ConfigParser.parse(
            "wireguard://yAnz5TF%2BlXXJte14tji3zlMNq%2Bhd2rYUIgJBgB3fBmk%3D@wg.example.com:51820" +
                "?publickey=TrMvSoPULSEHXZqi724FtoSsCgu8F%2B9esj%2F2oPhCY%3D" +
                "&address=10.0.0.2%2F32&dns=1.1.1.1&mtu=1280#WG",
        )
        assertEquals(Protocol.WIREGUARD, c.protocol)
        assertEquals("wg.example.com", c.address)
        assertEquals(51820, c.port)
        assertEquals(listOf("10.0.0.2/32"), c.localAddresses)
        assertEquals(listOf("1.1.1.1"), c.dnsServers)
        assertEquals(1280, c.wireguardMtu)
    }

    @Test
    fun `wireguard conf import`() {
        val conf = """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.0.0.2/32, fd00::2/128
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = TrMvSoPULSEHXZqi724FtoSsCgu8F+9esj/2oPhCY=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = engage.cloudflareclient.com:2408
            PersistentKeepalive = 25
        """.trimIndent()
        assertTrue(ConfigParser.looksLikeWireguardConf(conf))
        val c = ConfigParser.parseWireguardConf(conf, "My WG")
        assertEquals(Protocol.WIREGUARD, c.protocol)
        assertEquals("engage.cloudflareclient.com", c.address)
        assertEquals(2408, c.port)
        assertEquals("My WG", c.remark)
        assertEquals(25, c.wireguardKeepalive)
        assertEquals(listOf("0.0.0.0/0", "::/0"), c.allowedIps)
    }

    @Test
    fun `wireguard conf ipv6 endpoint`() {
        val conf = """
            [Interface]
            PrivateKey = abc=
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = def=
            Endpoint = [2001:db8::5]:51820
        """.trimIndent()
        val c = ConfigParser.parseWireguardConf(conf)
        assertEquals("2001:db8::5", c.address)
        assertEquals(51820, c.port)
    }

    // ---------- support surface ----------

    @Test
    fun `supported link detection`() {
        assertTrue(ConfigParser.isSupportedLink("vless://u@h:443"))
        assertTrue(ConfigParser.isSupportedLink("tuic://u:p@h:443"))
        assertFalse(ConfigParser.isSupportedLink("ftp://h/file"))
        assertFalse(ConfigParser.isSupportedLink("not a link"))
        assertThrows(ConfigParseException::class.java) { ConfigParser.parse("ftp://h") }
    }

    // ---------- share-link export round-trips ----------

    @Test
    fun `share link round-trip vless`() {
        val link = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@vl.example.com:443" +
            "?security=reality&encryption=none&pbk=pubkey123&flow=xtls-rprx-vision&type=tcp&sni=www.microsoft.com#RT"
        val parsed = ConfigParser.parse(link)
        assertEquals(parsed, ConfigParser.parse(ShareLink.build(parsed)))
    }

    @Test
    fun `share link round-trip trojan`() {
        val parsed = ConfigParser.parse("trojan://s3cret@tj.example.com:443?sni=tj.example.com#T")
        assertEquals(parsed, ConfigParser.parse(ShareLink.build(parsed)))
    }

    @Test
    fun `share link round-trip shadowsocks`() {
        val parsed = ConfigParser.parse("ss://${b64("aes-256-gcm:password123")}@ss.example.com:8388#S")
        assertEquals(parsed, ConfigParser.parse(ShareLink.build(parsed)))
    }

    @Test
    fun `share link round-trip wireguard`() {
        val parsed = ConfigParser.parse(
            "wireguard://yAnz5TF%2BlXXJte14tji3zlMNq%2Bhd2rYUIgJBgB3fBmk%3D@wg.example.com:51820" +
                "?publickey=TrMvSoPULSEHXZqi724FtoSsCgu8F%2B9esj%2F2oPhCY%3D&address=10.0.0.2%2F32&dns=1.1.1.1#WG",
        )
        assertEquals(parsed, ConfigParser.parse(ShareLink.build(parsed)))
    }
}
