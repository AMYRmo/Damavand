package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Aether flag generation (§6.2). The headline invariant, from build #78:
 * a session ALWAYS carries `--quick-reconnect` and NEVER
 * `--no-quick-reconnect` — even for scan-mode (endpoint-less) profiles.
 */
class AetherFlagsTest {

    @Test
    fun `scan mode session still quick-reconnects`() {
        val flags = AetherFlags.toFlags(AetherProfile(id = "1", name = "scan"))
        assertTrue("--quick-reconnect" in flags, flags.toString())
        assertFalse("--no-quick-reconnect" in flags, flags.toString())
        assertFalse("--peer" in flags, flags.toString())
    }

    @Test
    fun `wg profile with endpoint dials the peer`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(id = "2", name = "wg", protocol = "wg", address = "188.114.96.1", port = "1701"),
        )
        assertEquals("wg", flags[flags.indexOf("--protocol") + 1])
        assertEquals("188.114.96.1:1701", flags[flags.indexOf("--peer") + 1])
        assertTrue("--quick-reconnect" in flags)
    }

    @Test
    fun `hostname endpoint is emitted as-is`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "3", name = "host", protocol = "wg",
                address = "engage.cloudflareclient.com", port = "2408",
            ),
        )
        assertEquals("engage.cloudflareclient.com:2408", flags[flags.indexOf("--peer") + 1])
    }

    @Test
    fun `masque h2 profile carries h2 and default sni`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "4", name = "m", protocol = "masque", transport = "h2",
                ipVersion = "both", address = "162.159.199.42", port = "443",
            ),
        )
        assertEquals("masque", flags[flags.indexOf("--protocol") + 1])
        assertTrue("--h2" in flags)
        assertEquals("zapu.dev", flags[flags.indexOf("--masque-sni") + 1])
        assertEquals("both", flags[flags.indexOf("--ip") + 1])
        assertEquals("162.159.199.42:443", flags[flags.indexOf("--peer") + 1])
    }

    @Test
    fun `psiphon carrier adds psiphon flags and the shipped list word`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "5", name = "p", protocol = "wg",
                address = "188.114.96.1", port = "1701",
                psiphon = "chain", psiphonMode = "auto", psiphonRegion = "US",
            ),
        )
        assertTrue("--psiphon" in flags)
        assertEquals("auto", flags[flags.indexOf("--psiphon-mode") + 1])
        assertEquals("US", flags[flags.indexOf("--psiphon-region") + 1])
        val entries = flags.indexOf("--psiphon-server-entries")
        assertTrue(entries >= 0)
        assertEquals("shipped-list", flags[entries + 1])
    }

    @Test
    fun `psiphon only skips the warp protocol block`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(id = "6", name = "po", psiphon = "only"),
        )
        assertTrue("--psiphon-only" in flags)
        assertFalse("--protocol" in flags)
    }

    @Test
    fun `tor chain with own bridges emits bridge lines`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "7", name = "t", tor = "chain", torBridges = "own",
                torBridgeLines = "obfs4 1.2.3.4:443 AA cert=x\n\nobfs4 5.6.7.8:443 BB cert=y",
            ),
        )
        assertTrue("--tor" in flags)
        val bridges = flags.withIndex().filter { it.value == "--tor-bridge" }.map { flags[it.index + 1] }
        assertEquals(
            listOf("obfs4 1.2.3.4:443 AA cert=x", "obfs4 5.6.7.8:443 BB cert=y"),
            bridges,
        )
    }

    @Test
    fun `two-hop hops are gated per hop`() {
        // Only the outer hop set: it must be emitted, and no scan flag
        // may swallow it (the v1 shared-scanning bug).
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "8", name = "wiw", protocol = "gool",
                wiwOuter = "outer.example.com:443",
            ),
        )
        assertEquals("outer.example.com:443", flags[flags.indexOf("--wiw-outer") + 1])
        assertFalse("--wiw-inner" in flags)
        assertFalse("--wiw-scan" in flags)
        assertTrue("--gool-classic" in flags)
    }

    @Test
    fun `two-hop with neither hop scans`() {
        val flags = AetherFlags.toFlags(AetherProfile(id = "9", name = "wiw2", protocol = "gool"))
        assertTrue("--wiw-scan" in flags)
    }

    @Test
    fun `wg over masque maps outer to peer and inner to gool-peer`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "10", name = "wom", protocol = "wg-over-masque",
                wiwOuter = "o.example.com:443", wiwInner = "i.example.com:443",
            ),
        )
        assertEquals("gool", flags[flags.indexOf("--protocol") + 1])
        assertEquals("o.example.com:443", flags[flags.indexOf("--peer") + 1])
        assertEquals("i.example.com:443", flags[flags.indexOf("--gool-peer") + 1])
    }

    @Test
    fun `upstream and extra args are appended`() {
        val flags = AetherFlags.toFlags(
            AetherProfile(
                id = "11", name = "u", protocol = "wg",
                upstream = "socks5://127.0.0.1:19000", extraArgs = "--foo bar",
            ),
        )
        assertEquals("socks5://127.0.0.1:19000", flags[flags.indexOf("--upstream") + 1])
        assertEquals(listOf("--foo", "bar"), flags.takeLast(2))
    }

    @Test
    fun `command preview is the generated argv on a bind`() {
        val preview = AetherFlags.commandPreview(
            AetherProfile(id = "12", name = "x", protocol = "wg", address = "1.2.3.4", port = "443"),
            bindPort = 18190,
        )
        assertTrue(preview.startsWith("aether --bind 127.0.0.1:18190 "), preview)
        assertTrue(preview.endsWith("--log-level info"), preview)
        assertTrue(preview.contains("--peer 1.2.3.4:443"), preview)
    }
}
