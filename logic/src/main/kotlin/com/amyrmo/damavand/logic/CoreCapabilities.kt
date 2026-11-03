package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.ConfigParser
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.ServerConfig
import com.amyrmo.damavand.model.SingBoxProfile
import com.amyrmo.damavand.model.WhitednsMode
import com.amyrmo.damavand.model.WhitednsEngine
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.model.XrayPayload
import com.amyrmo.damavand.model.XrayProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a profile can do as a chain hop (architecture §2.3). The planner and
 * the validator read capabilities from this table only — never from
 * per-source `when` lists (the cross-cutting rule the core audit drew from
 * v1's production bugs).
 *
 * [carriesUdp] is position-independent in this table; the one
 * position-dependent case (Psiphon carries UDP only as the first hop,
 * because only then is its "UDP" the device's own traffic — §4.3) is
 * exposed through [CoreCapabilities.carriesUdpAt].
 */
data class HopCapabilities(
    /** Presents a loopback SOCKS5 listener the next hop / bridge can dial. */
    val exposesSocks: Boolean,
    /** Can itself dial out through a SOCKS5 upstream (i.e. be a non-entry hop). */
    val canDialThroughSocks: Boolean,
    /** Carries UDP datagrams onward to the next hop. */
    val carriesUdp: Boolean,
    /** Last position this hop may occupy (null = any). */
    val maxPosition: Int?,
)

object CoreCapabilities {

    fun of(profile: Profile): HopCapabilities = when (profile) {
        is XrayProfile -> HopCapabilities(
            exposesSocks = true, canDialThroughSocks = true,
            carriesUdp = xrayCarriesUdp(profile), maxPosition = null,
        )
        is SingBoxProfile -> HopCapabilities(
            exposesSocks = true, canDialThroughSocks = true,
            carriesUdp = singBoxCarriesUdp(profile), maxPosition = null,
        )
        is AetherProfile -> HopCapabilities(
            exposesSocks = true, canDialThroughSocks = true,
            // With a Psiphon carrier active the hop's exit is Psiphon,
            // which is TCP-only (§4.7 V6).
            carriesUdp = AetherPsiphon.fromString(profile.psiphon) == AetherPsiphon.OFF,
            maxPosition = null,
        )
        is PsiphonProfile -> HopCapabilities(
            exposesSocks = true, canDialThroughSocks = true,
            carriesUdp = false, maxPosition = null,
        )
        is MihomoProfile -> HopCapabilities(
            exposesSocks = true, canDialThroughSocks = true,
            carriesUdp = true, maxPosition = null,
        )
        is WhitednsProfile -> HopCapabilities(
            exposesSocks = true,
            // The DNS engines have no upstream-proxy support at all
            // (whitedns-and-chain.md §2.4) — entry-only, forever.
            canDialThroughSocks = false,
            carriesUdp = !(profile.engine == WhitednsEngine.COTTEN && profile.tunnelMode == WhitednsMode.COTTENMAX),
            maxPosition = 0,
        )
        // Chains never nest and AutoSelect expands before planning (§4.2);
        // neither can appear as a hop.
        else -> HopCapabilities(false, false, false, null)
    }

    /** Position-aware UDP carriage: Psiphon carries UDP only at index 0. */
    fun carriesUdpAt(profile: Profile, position: Int): Boolean = when (profile) {
        is PsiphonProfile -> position == 0
        else -> of(profile).carriesUdp
    }

    /** Whether this hop's own traffic includes UDP the previous hop must carry (V5). */
    fun needsUdp(profile: Profile): Boolean = when (profile) {
        is XrayProfile -> when (val payload = profile.payload) {
            is XrayPayload.Link -> parseLink(payload.link)?.let { linkNeedsUdp(it) } ?: false
            is XrayPayload.RawJson -> rawJsonNeedsUdp(payload.json)
            is XrayPayload.Sealed -> false
        }
        is SingBoxProfile -> profile.fragment && fragmentType(profile.json) in UDP_FRAGMENT_TYPES
        is AetherProfile ->
            // All five Aether protocols are UDP transports; with Psiphon in
            // ONLY mode the session's exit is Psiphon itself (TCP).
            AetherPsiphon.fromString(profile.psiphon) != AetherPsiphon.ONLY
        is PsiphonProfile -> false
        is MihomoProfile -> profile.yaml.isNotBlank() && MihomoConfigEditor.needsUdp(profile.yaml)
        is WhitednsProfile -> false
        else -> false
    }

    // -- per-core derivations ------------------------------------------------------

    private val UDP_FRAGMENT_TYPES = setOf("tuic", "hysteria", "hysteria2", "wireguard", "naive")

    /** Fragment types that cannot carry UDP (v1's ChainHop.SingBox rule):
     * HTTP has no UDP; naive/ssh/tor/shadowtls ride TCP or lack UDP associate. */
    private val NON_UDP_FRAGMENT_TYPES = setOf("http", "naive", "ssh", "tor", "shadowtls")

    private fun parseLink(link: String): ServerConfig? = runCatching { ConfigParser.parse(link) }.getOrNull()

    private fun linkNeedsUdp(server: ServerConfig): Boolean =
        XrayChainWiring.ChainHop.Xray(server).needsUdp

    private fun xrayCarriesUdp(profile: XrayProfile): Boolean = when (val payload = profile.payload) {
        is XrayPayload.Link -> parseLink(payload.link)?.protocol?.carriesUdp ?: false
        is XrayPayload.RawJson -> true // user JSON: best effort, assumed capable
        is XrayPayload.Sealed -> false
    }

    private fun rawJsonNeedsUdp(json: String): Boolean {
        val root = SingBoxJson.parse(json) as? JsonObject ?: return false
        val outbounds = root["outbounds"] as? JsonArray ?: return false
        return outbounds.any {
            val p = ((it as? JsonObject)?.get("protocol") as? JsonPrimitive)?.content?.lowercase()
            p == "wireguard" || p == "hysteria"
        }
    }

    private fun fragmentType(json: String): String? =
        ((SingBoxJson.parse(json) as? JsonObject)?.get("type") as? JsonPrimitive)?.content?.lowercase()

    private fun singBoxCarriesUdp(profile: SingBoxProfile): Boolean {
        if (!profile.fragment) return true // full config: best effort
        val type = fragmentType(profile.json) ?: return false
        return type !in NON_UDP_FRAGMENT_TYPES
    }
}
