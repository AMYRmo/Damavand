package com.amyrmo.damavand.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * whitedns.profile v1 round-trips (§6.2): import → model → export is the
 * identity for canonical WhiteDNS links, so existing WhiteDNS
 * subscriptions keep working unchanged; `soar://` is rejected cleanly.
 */
class WhitednsLinkTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun linkOf(payloadJson: String, scheme: String = "cottendns"): String =
        "$scheme://" + Base64.getEncoder().encodeToString(payloadJson.encodeToByteArray())

    private fun payloadOf(link: String): String =
        Base64.getDecoder().decode(link.substringAfter("://")).decodeToString()

    private val canonicalPayload = """{"schema":"whitedns.profile","version":1,"profile":{"name":"Test DNS","server":{"domain":"v.example.com","encryption_key":"key123","encryption_method":3},"resolvers":["1.1.1.1","8.8.8.8:53"]}}"""

    @Test
    fun `canonical cottendns link parses`() {
        val p = WhitednsLink.parse(linkOf(canonicalPayload), id = "w1")
        assertEquals("Test DNS", p.name)
        assertEquals(WhitednsEngine.COTTEN, p.engine)
        assertEquals(listOf("v.example.com"), p.domains)
        assertEquals("key123", p.encryptionKey)
        assertEquals(3, p.encryptionMethod)
        assertEquals(listOf("1.1.1.1", "8.8.8.8:53"), p.resolvers)
        assertEquals(WhitednsMode.LEGACY, p.tunnelMode)
        assertEquals("auto", p.resolverTransport)
        assertEquals(3, p.balancingStrategy)
    }

    @Test
    fun `import then export is the identity for a canonical link`() {
        val link = linkOf(canonicalPayload)
        val exported = WhitednsLink.build(WhitednsLink.parse(link, id = "w2"))
        // Compare parsed JSON (key order is not part of the schema).
        assertEquals(
            json.parseToJsonElement(canonicalPayload).jsonObject,
            json.parseToJsonElement(payloadOf(exported)).jsonObject,
        )
        assertTrue(exported.startsWith("cottendns://"))
    }

    @Test
    fun `scheme selects the engine and export uses it back`() {
        val storm = WhitednsLink.parse(linkOf(canonicalPayload, "stormdns"), id = "w3")
        assertEquals(WhitednsEngine.STORM, storm.engine)
        assertTrue(WhitednsLink.build(storm).startsWith("stormdns://"))

        val master = WhitednsLink.parse(linkOf(canonicalPayload, "masterdns"), id = "w4")
        assertEquals(WhitednsEngine.MASTER, master.engine)
        assertTrue(WhitednsLink.build(master).startsWith("masterdns://"))
    }

    @Test
    fun `model round trip with cotten settings and several domains`() {
        val p = WhitednsProfile(
            id = "w5", name = "Max", engine = WhitednsEngine.COTTEN,
            domains = listOf("a.example.com", "b.example.com"),
            encryptionKey = "k", encryptionMethod = 5,
            resolvers = listOf("9.9.9.9"),
            tunnelMode = WhitednsMode.COTTENMAX, resolverTransport = "doh",
            balancingStrategy = 1, dns = "1.0.0.1",
        )
        assertEquals(p, WhitednsLink.parse(WhitednsLink.build(p), id = "w5"))
    }

    @Test
    fun `soar links are rejected with a clear message`() {
        val ex = assertThrows(WhitednsLinkException::class.java) {
            WhitednsLink.parse(linkOf(canonicalPayload, "soar"), id = "w6")
        }
        assertTrue(ex.message!!.contains("not supported"), ex.message)
    }

    @Test
    fun `foreign payloads are rejected`() {
        assertThrows(WhitednsLinkException::class.java) {
            WhitednsLink.parse(linkOf("""{"schema":"other","version":1,"profile":{}}"""), id = "w7")
        }
        assertThrows(WhitednsLinkException::class.java) {
            WhitednsLink.parse("cottendns://!!!not-base64!!!", id = "w8")
        }
        assertThrows(WhitednsLinkException::class.java) {
            WhitednsLink.parse(linkOf("""{"schema":"whitedns.profile","version":2,"profile":{}}"""), id = "w9")
        }
    }

    @Test
    fun `resolver literals are validated`() {
        assertTrue(WhitednsLink.isResolverLiteral("1.1.1.1"))
        assertTrue(WhitednsLink.isResolverLiteral("1.1.1.1:5353"))
        assertTrue(WhitednsLink.isResolverLiteral("[2001:4860:4860::8888]:53"))
        assertTrue(WhitednsLink.isResolverLiteral("192.168.1.0/30"))
        assertFalse(WhitednsLink.isResolverLiteral("dns.google"))
        assertFalse(WhitednsLink.isResolverLiteral("example.com:53"))
        assertFalse(WhitednsLink.isResolverLiteral(""))
    }

    @Test
    fun `handles recognises all four schemes`() {
        assertTrue(WhitednsLink.handles("stormdns://x"))
        assertTrue(WhitednsLink.handles("masterdns://x"))
        assertTrue(WhitednsLink.handles("cottendns://x"))
        assertTrue(WhitednsLink.handles("soar://x"))
        assertFalse(WhitednsLink.handles("vless://x"))
    }
}
