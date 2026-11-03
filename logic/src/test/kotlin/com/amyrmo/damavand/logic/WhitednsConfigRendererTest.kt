package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.WhitednsEngine
import com.amyrmo.damavand.model.WhitednsMode
import com.amyrmo.damavand.model.WhitednsProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Golden tests for the WhiteDNS config renderer (§3.4). TOML's canonical
 * form is the renderer's deterministic output — exact string compare. */
class WhitednsConfigRendererTest {

    private fun golden(name: String): String =
        requireNotNull(javaClass.getResource("/golden/$name")) { "missing golden $name" }.readText()

    private val cotten = WhitednsProfile(
        id = "w1", name = "W", engine = WhitednsEngine.COTTEN,
        domains = listOf("t.example.com", "cdn.example.org"), encryptionKey = "s3cret-key",
        encryptionMethod = 2, resolvers = listOf("8.8.8.8", "1.1.1.1:53"),
        tunnelMode = WhitednsMode.COTTENMAX, resolverTransport = "auto", balancingStrategy = 3,
    )

    @Test
    fun `cotten golden - full phase0 key set`() {
        val r = WhitednsConfigRenderer.render(cotten, listenPort = 18000)
        assertEquals(golden("whitedns-cotten.toml"), r.configToml)
        assertEquals(golden("whitedns-cotten-resolvers.txt"), r.resolversTxt)
    }

    @Test
    fun `master golden - master-family key set without cotten-only keys`() {
        val master = WhitednsProfile(
            id = "w2", name = "M", engine = WhitednsEngine.MASTER,
            domains = listOf("t.example.com"), encryptionKey = "key\"with\\escapes",
            encryptionMethod = 1, resolvers = listOf("9.9.9.9"),
        )
        val r = WhitednsConfigRenderer.render(master, listenPort = 18001)
        assertEquals(golden("whitedns-master.toml"), r.configToml)
        assertFalse(r.configToml.contains("TUNNEL_MODE"))
        assertFalse(r.configToml.contains("CONFIG_VERSION"))
    }

    @Test
    fun `rendering is deterministic and port comes from the caller`() {
        val a = WhitednsConfigRenderer.render(cotten, listenPort = 19000)
        val b = WhitednsConfigRenderer.render(cotten, listenPort = 19000)
        assertEquals(a, b)
        assertTrue(a.configToml.contains("LISTEN_PORT = 19000"))
    }

    @Test
    fun `resolver validation follows the v1 numeric rule`() {
        assertTrue(WhitednsResolvers.isNumericResolver("8.8.8.8"))
        assertTrue(WhitednsResolvers.isNumericResolver("1.1.1.1:53"))
        assertTrue(WhitednsResolvers.isNumericResolver("[2001:db8::53]:53"))
        assertTrue(WhitednsResolvers.isNumericResolver("10.0.0.0/8"))
        assertFalse(WhitednsResolvers.isNumericResolver("dns.google"))
        assertFalse(WhitednsResolvers.isNumericResolver("8.8.8.8:99999"))
        assertEquals(
            listOf("dns.google"),
            WhitednsResolvers.invalidResolvers("8.8.8.8, dns.google\n# comment\n9.9.9.9"),
        )
    }
}
