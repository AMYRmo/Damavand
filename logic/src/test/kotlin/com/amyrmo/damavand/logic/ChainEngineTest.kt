package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.WhitednsEngine
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.model.XrayPayload
import com.amyrmo.damavand.model.XrayProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Validation rules V1–V9 (§4.7) and the planner (§4.5). */
class ChainEngineTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun parsed(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    // -- fixtures ---------------------------------------------------------------

    private fun xray(id: String, link: String) = XrayProfile(id = id, name = id, payload = XrayPayload.Link(link))

    private val vless = xray(
        "x1",
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@vl.example.com:443?encryption=none&security=tls&sni=vl.example.com&type=ws&host=cdn.example.com&path=%2Fws#VL",
    )
    private val trojan = xray("x2", "trojan://s3cret@tj.example.com:443?sni=tj.example.com#TJ")
    private val wg = xray(
        "wg1",
        "wireguard://yAnz5TF%2BlXXJte14tji3zlMNq%2Bhd2rYUIgJBgB3fBmk%3D@wg.example.com:51820" +
            "?publickey=TrMvSoPULSEHXZqi724FtoSsCgu8F%2B9esj%2F2oPhCY%3D&address=10.0.0.2%2F32#WG",
    )
    private val http = xray("h1", "http://user:pass@px.example.com:8080#H")
    private val psiphon = PsiphonProfile(id = "p1", name = "P")
    private val aether = AetherProfile(id = "a1", name = "A", address = "162.159.192.1", port = "2408")
    private val aetherPsiphonCarrier = aether.copy(id = "a2", psiphon = "chain")
    private val aetherCustomUpstream = AetherProfile(
        id = "ac1", name = "AC",
        command = "aether --bind 127.0.0.1:18099 --upstream socks5://127.0.0.1:9999 --peer 162.159.192.1:2408",
    )
    private val whitedns = WhitednsProfile(
        id = "w1", name = "W", engine = WhitednsEngine.COTTEN,
        domains = listOf("t.example.com"), encryptionKey = "key",
        encryptionMethod = 2, resolvers = listOf("8.8.8.8", "1.1.1.1:53"),
    )
    private val mihomo = MihomoProfile(
        id = "m1", name = "M",
        yaml = "proxies:\n  - name: n1\n    type: vless\n    server: vl.example.com\n    port: 443\n    uuid: u-1\n" +
            "proxy-groups:\n  - name: P\n    type: select\n    proxies:\n      - n1\nrules:\n  - MATCH,P",
    )

    private val all = listOf<Profile>(vless, trojan, wg, http, psiphon, aether, aetherPsiphonCarrier, aetherCustomUpstream, whitedns, mihomo)
        .associateBy { it.id }
    private val lookup = ProfileLookup { id -> all[id] }

    private fun chainOf(vararg ids: String) = ChainProfile(id = "c", name = "C", hopIds = ids.toList())

    private fun errorsOf(target: Profile): List<ChainError> =
        assertThrows<ChainException> { ChainPlanner.plan(target, "s", lookup = lookup) }.errors

    // -- V1 -----------------------------------------------------------------------

    @Test
    fun `V1 missing member is a typed error`() {
        val errors = errorsOf(chainOf("x1", "nope"))
        assertTrue(errors.any { it is ChainError.MissingMember && it.profileId == "nope" && it.position == 1 })
    }

    // -- V2 -----------------------------------------------------------------------

    @Test
    fun `V2 length bounds`() {
        assertTrue(ChainValidator.validateProfiles(listOf(vless)).errors.any { it is ChainError.TooShort })
        val six = listOf(vless, trojan, wg, http, psiphon, aether)
        assertTrue(ChainValidator.validateProfiles(six).errors.any { it is ChainError.TooLong })
    }

    @Test
    fun `V2 duplicate member`() {
        val result = ChainValidator.validateProfiles(listOf(vless, trojan, vless))
        assertTrue(result.errors.any { it is ChainError.DuplicateMember && it.profileId == "x1" })
    }

    @Test
    fun `V2 nested chain member`() {
        val inner = chainOf("x1", "x2")
        val result = ChainValidator.validateProfiles(listOf(vless, inner))
        assertTrue(result.errors.any { it is ChainError.NestedChain })
    }

    // -- V3 + V4 --------------------------------------------------------------------

    @Test
    fun `V3 whitedns is entry-only and V4 fires with it`() {
        val result = ChainValidator.validateProfiles(listOf(vless, whitedns))
        assertTrue(result.errors.any { it is ChainError.PositionNotAllowed && it.position == 1 })
        assertTrue(result.errors.any { it is ChainError.SpliceImpossible && it.position == 1 })
        // Planner level: Xray -> Whitedns is rejected outright.
        assertTrue(errorsOf(chainOf("x1", "w1")).any { it is ChainError.PositionNotAllowed })
    }

    // -- V5 ----------------------------------------------------------------------------

    @Test
    fun `V5 udp hop behind a tcp-only carrier`() {
        val viaHttp = ChainValidator.validateProfiles(listOf(http, wg))
        assertTrue(viaHttp.errors.any { it is ChainError.UdpNotCarried && it.needingProfileId == "wg1" && it.carrierProfileId == "h1" })
    }

    @Test
    fun `V5 udp exit behind a non-entry psiphon, but entry psiphon is fine`() {
        val mid = ChainValidator.validateProfiles(listOf(vless, psiphon, wg))
        assertTrue(mid.errors.any { it is ChainError.UdpNotCarried && it.carrierProfileId == "p1" })
        // v1 production proved entry Psiphon + WG exit works (build #75).
        val entry = ChainValidator.validateProfiles(listOf(psiphon, wg))
        assertTrue(entry.errors.none { it is ChainError.UdpNotCarried })
    }

    @Test
    fun `V6 aether with a psiphon carrier is tcp-only`() {
        val result = ChainValidator.validateProfiles(listOf(aetherPsiphonCarrier, wg))
        assertTrue(result.errors.any { it is ChainError.UdpNotCarried && it.carrierProfileId == "a2" })
        val clean = ChainValidator.validateProfiles(listOf(aether, wg))
        assertTrue(clean.errors.none { it is ChainError.UdpNotCarried })
    }

    // -- V7 ------------------------------------------------------------------------------

    @Test
    fun `V7 whitedns member validity`() {
        val badResolver = whitedns.copy(id = "w2", resolvers = listOf("dns.google"))
        val r1 = ChainValidator.validateProfiles(listOf(badResolver, vless))
        assertTrue(r1.errors.any { it is ChainError.WhitednsInvalid })
        val badMethod = whitedns.copy(id = "w3", encryptionMethod = 9)
        val r2 = ChainValidator.validateProfiles(listOf(badMethod, vless))
        assertTrue(r2.errors.any { it is ChainError.WhitednsInvalid })
        val noDomains = whitedns.copy(id = "w4", domains = emptyList())
        val r3 = ChainValidator.validateProfiles(listOf(noDomains, vless))
        assertTrue(r3.errors.any { it is ChainError.WhitednsInvalid })
    }

    // -- V8 ---------------------------------------------------------------------------------

    @Test
    fun `V8 custom command upstream only at the entry`() {
        val mid = ChainValidator.validateProfiles(listOf(vless, aetherCustomUpstream))
        assertTrue(mid.errors.any { it is ChainError.CustomCommandUpstream && it.position == 1 })
        val entry = ChainValidator.validateProfiles(listOf(aetherCustomUpstream, vless))
        assertTrue(entry.errors.none { it is ChainError.CustomCommandUpstream })
    }

    // -- V9 -----------------------------------------------------------------------------------

    @Test
    fun `V9 advisory warnings`() {
        val withDns = ChainValidator.validateProfiles(listOf(whitedns, aether, vless))
        assertTrue(withDns.warnings.any { it is ChainWarning.DnsTunnelSpeed })
        assertTrue(withDns.isValid)
        val long = ChainValidator.validateProfiles(listOf(whitedns, aether, mihomo, vless))
        assertTrue(long.warnings.any { it is ChainWarning.LatencyLength })
        val midPsiphon = ChainValidator.validateProfiles(listOf(vless, psiphon, trojan))
        assertTrue(midPsiphon.warnings.any { it is ChainWarning.TcpOnlySegment })
    }

    // -- planner: standalone ---------------------------------------------------------------------

    @Test
    fun `single xray profile - chain of one, no bridge, port 10808`() {
        val plan = ChainPlanner.plan(vless, "s1", lookup = lookup)
        assertNull(plan.bridge)
        assertEquals(1, plan.hops.size)
        assertEquals(10808, plan.hops[0].listenPort)
        val cfg = plan.hops[0].config as HopConfig.XrayJson
        val inbound = parsed(cfg.json).getValue("inbounds").jsonArray.single().jsonObject
        assertEquals(10808, inbound.getValue("port").jsonPrimitive.int)
    }

    @Test
    fun `single whitedns profile - bridge present, exit is the tunnel port`() {
        val plan = ChainPlanner.plan(whitedns, "s2", lookup = lookup)
        assertNotNull(plan.bridge)
        assertEquals(18190, plan.hops[0].listenPort)
        val files = plan.hops[0].config as HopConfig.WhitednsFiles
        assertTrue(files.configToml.contains("LISTEN_PORT = 18190"))
        val bridgeOut = parsed(plan.bridge!!.json!!).getValue("outbounds").jsonArray.first().jsonObject
        assertEquals(18190, bridgeOut.getValue("server_port").jsonPrimitive.int)
    }

    @Test
    fun `sealed payload and autoselect target are typed errors`() {
        val sealed = XrayProfile(id = "sx", name = "S", payload = XrayPayload.Sealed("AAAA"))
        assertTrue(errorsOf(sealed).any { it is ChainError.SealedPayload })
        val auto = AutoSelectProfile(id = "au", name = "AU", targetSubscriptionId = "sub1")
        assertTrue(errorsOf(auto).any { it is ChainError.UnplannableTarget })
    }

    // -- planner: the canonical chain -------------------------------------------------------------

    @Test
    fun `whitedns to aether to xray - the owner's example`() {
        val plan = ChainPlanner.plan(chainOf("w1", "a1", "x1"), "s3", lookup = lookup)
        assertEquals(3, plan.hops.size)
        assertEquals(listOf(18190, 18191, 18192), plan.hops.map { it.listenPort })
        assertEquals(PlannedUpstream.Fixed(SocksEndpoint(port = 18190)), plan.hops[1].upstream)
        assertEquals(PlannedUpstream.Fixed(SocksEndpoint(port = 18191)), plan.hops[2].upstream)

        val aetherCfg = plan.hops[1].config as HopConfig.AetherRuntime
        assertEquals("socks5://127.0.0.1:18190", aetherCfg.profile.upstream)
        assertTrue(aetherCfg.argv.containsAll(listOf("--upstream", "socks5://127.0.0.1:18190")))

        val xrayCfg = plan.hops[2].config as HopConfig.XrayJson
        val chainOut = parsed(xrayCfg.json).getValue("outbounds").jsonArray
            .map { it.jsonObject }
            .single { it["tag"]?.jsonPrimitive?.content == "chain-out" }
        val server = chainOut.getValue("settings").jsonObject
            .getValue("servers").jsonArray.single().jsonObject
        assertEquals(18191, server.getValue("port").jsonPrimitive.int)

        assertNotNull(plan.bridge)
        val bridgeOut = parsed(plan.bridge!!.json!!).getValue("outbounds").jsonArray.first().jsonObject
        assertEquals(18192, bridgeOut.getValue("server_port").jsonPrimitive.int)
        assertTrue(plan.warnings.any { it is ChainWarning.DnsTunnelSpeed })
    }

    @Test
    fun `xray xray collapse into one run`() {
        val plan = ChainPlanner.plan(chainOf("x1", "x2"), "s4", lookup = lookup)
        val start = plan.hops[0].config as HopConfig.XrayRunStart
        assertEquals(listOf(0, 1), start.memberIndices)
        assertTrue(plan.hops[1].config is HopConfig.XrayRunMember)
        assertEquals(plan.hops[0].listenPort, plan.hops[1].listenPort)
        val outbounds = parsed(start.json).getValue("outbounds").jsonArray.map { it.jsonObject }
        val exit = outbounds.single { it["tag"]?.jsonPrimitive?.content == "proxy" }
        val sockopt = exit.getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject
        assertEquals("proxy-h0", sockopt.getValue("dialerProxy").jsonPrimitive.content)
        val bridgeOut = parsed(plan.bridge!!.json!!).getValue("outbounds").jsonArray.first().jsonObject
        assertEquals(plan.hops[1].listenPort, bridgeOut.getValue("server_port").jsonPrimitive.int)
    }

    @Test
    fun `psiphon mid-chain - next hop splices at start, psiphon gets the upstream`() {
        val plan = ChainPlanner.plan(chainOf("x1", "p1", "x2"), "s5", lookup = lookup)
        assertEquals(PlannedUpstream.FromHop(1), plan.hops[2].upstream)
        val exitCfg = plan.hops[2].config as HopConfig.XrayJson
        assertTrue(exitCfg.carrierSplicePending)
        val psiphonCfg = plan.hops[1].config as HopConfig.PsiphonJson
        assertTrue(psiphonCfg.json.contains("18190"), "psiphon config carries the entry hop's port as its upstream")
        assertNull(plan.hops[1].listenPort, "psiphon ports are adopted at runtime")
    }

    @Test
    fun `psiphon exit - bridge json deferred to runtime`() {
        val plan = ChainPlanner.plan(chainOf("x1", "p1"), "s6", lookup = lookup)
        assertNotNull(plan.bridge)
        assertNull(plan.bridge!!.json)
        assertEquals(PlannedUpstream.FromHop(1), plan.bridge!!.exit)
    }
}
