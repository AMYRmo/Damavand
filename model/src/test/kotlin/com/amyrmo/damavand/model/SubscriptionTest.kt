package com.amyrmo.damavand.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Subscription surface (§3.5/§6.2): header info parsing, body link
 * extraction (plain and base64 bodies, mixed link families), and the
 * model's own serialization round-trip.
 */
class SubscriptionTest {

    @Test
    fun `userinfo header parses usage and expiry`() {
        val meta = SubscriptionHeaders.parse(
            mapOf(
                "subscription-userinfo" to "upload=1024; download=2048; total=10737418240; expire=1893456000",
                "profile-update-interval" to "12",
                "support-url" to "https://support.example.com",
            ),
        )
        assertEquals(1024L, meta.upload)
        assertEquals(2048L, meta.download)
        assertEquals(10737418240L, meta.total)
        assertEquals(1893456000000L, meta.expireAt) // seconds → ms
        assertEquals(12, meta.updateIntervalHours)
        assertEquals("https://support.example.com", meta.supportUrl)
    }

    @Test
    fun `base64 profile title decodes`() {
        val meta = SubscriptionHeaders.parse(mapOf("profile-title" to "base64:SGVsbG8gU3Vi"))
        assertEquals("Hello Sub", meta.title)
    }

    @Test
    fun `millisecond expiry is not rescaled`() {
        val info = SubscriptionHeaders.parseUserInfo("upload=0; download=0; total=100; expire=1893456000000")
        assertEquals(1893456000000L, info.expireAt)
    }

    @Test
    fun `empty headers give empty meta`() {
        assertTrue(SubscriptionHeaders.parse(emptyMap()).isEmpty)
    }

    @Test
    fun `plain body links are extracted, junk dropped`() {
        val body = """
            vless://u@a.example.com:443#A
            this is not a link
            trojan://p@b.example.com:443#B
        """.trimIndent()
        assertEquals(
            listOf("vless://u@a.example.com:443#A", "trojan://p@b.example.com:443#B"),
            SubscriptionParser.extractLinks(body),
        )
    }

    @Test
    fun `base64 body is decoded before extraction`() {
        val inner = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@h.example.com:8388#S"
        val body = Base64.getEncoder().encodeToString(inner.encodeToByteArray())
        assertEquals(listOf(inner), SubscriptionParser.extractLinks(body))
    }

    @Test
    fun `mixed families are all supported`() {
        assertTrue(LinkSupport.isSupportedLink("vless://u@h:443"))
        assertTrue(LinkSupport.isSupportedLink("aether://1.2.3.4:443?protocol=wg#x"))
        assertTrue(LinkSupport.isSupportedLink("cottendns://AAAA"))
        assertTrue(LinkSupport.isSupportedLink("masterdns://AAAA"))
        assertFalse(LinkSupport.isSupportedLink("ftp://example.com/file"))
        assertFalse(LinkSupport.isSupportedLink("just some text"))
        // Note: an https:// line counts as "supported" — the scheme check
        // is v1's, where http/https links are HTTP-proxy candidates and
        // ConfigParser.parse is what rejects bare web addresses.
        assertTrue(LinkSupport.isSupportedLink("https://user:pass@proxy.example.com:8443"))
    }

    @Test
    fun `subscription model round-trips`() {
        val sub = Subscription(
            id = "s1", name = "Main", url = "https://subs.example.com/feed",
            lastUpdated = 1793600000000L, serverCount = 42,
            upload = 10, download = 20, total = 1000, expireAt = 1893456000000L,
            title = "Main sub", updateIntervalHours = 6,
        )
        val encoded = ProfileJson.json.encodeToString(Subscription.serializer(), sub)
        assertEquals(sub, ProfileJson.json.decodeFromString(Subscription.serializer(), encoded))
        assertEquals(30L, sub.used)
        val bare = Subscription(id = "s2", name = "Bare", url = "https://x.example")
        assertNull(bare.used)
    }
}
