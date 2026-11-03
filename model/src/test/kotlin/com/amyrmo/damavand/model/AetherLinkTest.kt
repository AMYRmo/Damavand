package com.amyrmo.damavand.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * aether:// round-trips (§6.2), including the cases that broke in v1:
 * hostname endpoints, a `#label` with no query, IPv6 endpoints bracketed
 * exactly once, and the verbatim-command `cmd` key.
 */
class AetherLinkTest {

    @Test
    fun `the pattng masque link parses fully`() {
        val p = AetherLink.fromShareLink(
            "aether://162.159.199.42:443?protocol=masque&scan=balanced&ip=both&transport=h2#amir",
            id = "a1",
        )
        assertNotNull(p)
        p!!
        assertEquals("amir", p.name)
        assertEquals("masque", p.protocol)
        assertEquals("h2", p.transport)
        assertEquals("both", p.ipVersion)
        assertEquals("balanced", p.scanMode)
        assertEquals("162.159.199.42", p.address)
        assertEquals("443", p.port)
    }

    @Test
    fun `hostname endpoint survives import`() {
        val p = AetherLink.fromShareLink(
            "aether://engage.cloudflareclient.com:2408?protocol=wg#dialme", id = "a2",
        )!!
        assertEquals("engage.cloudflareclient.com", p.address)
        assertEquals("2408", p.port)
        assertEquals("dialme", p.name)
    }

    @Test
    fun `label without query keeps both label and endpoint`() {
        val p = AetherLink.fromShareLink("aether://1.2.3.4:443#MyName", id = "a3")!!
        assertEquals("MyName", p.name)
        assertEquals("1.2.3.4", p.address)
        assertEquals("443", p.port)
    }

    @Test
    fun `ipv6 endpoint is bracketed exactly once on export`() {
        val p = AetherLink.fromShareLink("aether://[2001:db8::1]:443?protocol=wg#v6", id = "a4")!!
        assertEquals("2001:db8::1", p.address)
        val link = AetherLink.toShareLink(p)
        assertTrue(link.startsWith("aether://[2001:db8::1]:443"), link)
        assertTrue(!link.contains("[["), link)
        assertEquals(p, AetherLink.fromShareLink(link, id = "a4"))
    }

    @Test
    fun `round trip masque profile`() {
        val p = AetherProfile(
            id = "a5", name = "M", protocol = "masque", transport = "h2",
            scanMode = "turbo", ipVersion = "both",
            address = "162.159.199.42", port = "443",
            fragment = true, fragmentSize = "100-200", fragmentDelay = "10-20",
        )
        assertEquals(p, AetherLink.fromShareLink(AetherLink.toShareLink(p), id = "a5"))
    }

    @Test
    fun `round trip wg with psiphon carrier`() {
        val p = AetherProfile(
            id = "a6", name = "WG + Psiphon", protocol = "wg",
            address = "188.114.96.1", port = "1701",
            psiphon = "chain", psiphonMode = "cdn",
            psiphonCdnIps = "104.16.0.1", psiphonCdnSni = "cdn.example.com",
            psiphonRegion = "CA", psiphonBundledList = false,
        )
        assertEquals(p, AetherLink.fromShareLink(AetherLink.toShareLink(p), id = "a6"))
    }

    @Test
    fun `round trip two-hop with outer and inner endpoints`() {
        val p = AetherProfile(
            id = "a7", name = "WiW", protocol = "gool",
            wiwOuter = "outer.example.com:443", wiwInner = "198.51.100.7:8443",
        )
        val link = AetherLink.toShareLink(p)
        assertTrue(link.contains("outer=outer.example.com:443"), link)
        assertTrue(link.contains("inner=198.51.100.7:8443"), link)
        assertEquals(p, AetherLink.fromShareLink(link, id = "a7"))
    }

    @Test
    fun `verbatim command rides the cmd key`() {
        val command = "aether --bind 127.0.0.1:18190 --protocol wg --peer 1.2.3.4:443 --finalmask \"a b\""
        val p = AetherProfile(id = "a8", name = "Cmd", protocol = "wg", command = command)
        val parsed = AetherLink.fromShareLink(AetherLink.toShareLink(p), id = "a8")
        assertEquals(command, parsed!!.command)
    }

    @Test
    fun `non aether links are rejected`() {
        assertEquals(null, AetherLink.fromShareLink("vless://u@h:443", id = "x"))
    }
}
