package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.PsiphonMode
import com.amyrmo.damavand.model.PsiphonProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Psiphon config generation (§3.4): structure, modes, CDN fronting, and the chain splice. */
class PsiphonConfigBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun build(profile: PsiphonProfile) =
        json.parseToJsonElement(
            PsiphonConfigBuilder.build(profile, dataDirPath = "/data/psiphon", socksPort = 18080, httpPort = 18081),
        ).jsonObject

    @Test
    fun `base shape carries ports region and server list`() {
        val obj = build(PsiphonProfile(id = "1", name = "P", country = "ca"))
        assertEquals("18080", obj["LocalSocksProxyPort"]?.jsonPrimitive?.content)
        assertEquals("18081", obj["LocalHttpProxyPort"]?.jsonPrimitive?.content)
        assertEquals("CA", obj["EgressRegion"]?.jsonPrimitive?.content)
        assertTrue(obj.containsKey("RemoteServerListURLs"))
        assertTrue(obj.containsKey("RemoteServerListSignaturePublicKey"))
    }

    @Test
    fun `cdn mode limits to cdn protocols and disables tactics`() {
        val obj = build(PsiphonProfile(id = "2", name = "P", mode = PsiphonMode.CDN))
        val protocols = obj["LimitTunnelProtocols"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(protocols.all { it.contains("CDN") }, protocols.toString())
        assertEquals("true", obj["DisableTactics"]?.jsonPrimitive?.content)
    }

    @Test
    fun `direct mode has no quic-free pass but auto keeps full list`() {
        val direct = build(PsiphonProfile(id = "3", name = "P", mode = PsiphonMode.DIRECT))
        val protocols = direct["LimitTunnelProtocols"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("SSH" in protocols)
        assertEquals("true", direct["DisableTactics"]?.jsonPrimitive?.content)
    }

    @Test
    fun `cdn candidates become a scan spec`() {
        val obj = build(
            PsiphonProfile(
                id = "4", name = "P",
                cdnIps = listOf("104.16.0.1", "not-an-ip", "172.64.0.0/13"),
                cdnSni = "cdn.example.com, https://other.example.org/path",
            ),
        )
        val spec = obj["FrontedMeekCDNScanSpec"]!!.jsonObject
        val ips = spec["IPCandidates"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("104.16.0.1", "172.64.0.0/13"), ips)
        val snis = spec["SNIServerNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("cdn.example.com", "other.example.org"), snis)
        assertTrue(obj.containsKey("FrontedMeekDialOverrides"))
    }

    @Test
    fun `no candidates uses the built-in scan spec`() {
        val obj = build(PsiphonProfile(id = "5", name = "P"))
        assertEquals("true", obj["FrontedMeekCDNScanUseBuiltInSpec"]?.jsonPrimitive?.content)
    }

    @Test
    fun `upstream splice sets the url and strips quic`() {
        val base = PsiphonConfigBuilder.build(
            PsiphonProfile(id = "6", name = "P", mode = PsiphonMode.AUTO),
            dataDirPath = "/d", socksPort = 1, httpPort = 2,
        )
        val spliced = json.parseToJsonElement(
            PsiphonConfigBuilder.withUpstreamProxy(base, socksPort = 19000),
        ).jsonObject
        assertEquals("socks5://127.0.0.1:19000", spliced["UpstreamProxyURL"]?.jsonPrimitive?.content)
        val protocols = spliced["LimitTunnelProtocols"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertFalse(protocols.any { it.contains("QUIC") }, protocols.toString())
        assertTrue(protocols.isNotEmpty())
    }

    @Test
    fun `protocols lost to upstream are the quic ones`() {
        val lost = PsiphonConfigBuilder.protocolsLostToUpstreamProxy(
            PsiphonProfile(id = "7", name = "P", mode = PsiphonMode.CDN),
        )
        assertEquals(listOf("FRONTED-MEEK-CDN-QUIC-OSSH"), lost)
    }

    @Test
    fun `candidate helpers filter garbage`() {
        assertEquals(listOf("1.2.3.4"), PsiphonConfigBuilder.ipCandidates("1.2.3.4, example.com, 999.1.1.1"))
        assertEquals(listOf("a.example.com"), PsiphonConfigBuilder.sniCandidates("a.example.com localhost b"))
    }
}
