package com.amyrmo.damavand.data.profiles

import com.amyrmo.damavand.model.AetherLink
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.ConfigParser
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.SingBoxLinks
import com.amyrmo.damavand.model.SingBoxProfile
import com.amyrmo.damavand.model.WhitednsLink
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.model.XrayPayload
import com.amyrmo.damavand.model.XrayProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The import cascade and the display/share derivations over the :model
 * hierarchy — the one place that turns pasted text into a [Profile] and a
 * [Profile] back into a share link (architecture §3.5).
 *
 * Cascade order matters: the per-core codecs claim their schemes first
 * (Whitedns share links, `aether://`, the sing-box-only schemes of
 * [SingBoxLinks]); the Xray family goes through [ConfigParser]; a bare
 * JSON document is a full sing-box config; Clash YAML is a Mihomo profile.
 */
object ProfileFactory {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** One share link → a profile, or null when nothing claims it. */
    fun fromLink(link: String, id: String, subscriptionId: String? = null): Profile? {
        val t = link.trim()
        if (t.isEmpty()) return null
        if (WhitednsLink.handles(t)) {
            return runCatching { WhitednsLink.parse(t, id, subscriptionId) }.getOrNull()
        }
        if (t.startsWith("aether://")) {
            return AetherLink.fromShareLink(t, id, subscriptionId)
        }
        if (SingBoxLinks.handles(t)) {
            val parsed = runCatching { SingBoxLinks.parse(t) }.getOrNull() ?: return null
            return SingBoxProfile(
                id = id,
                name = parsed.name,
                subscriptionId = subscriptionId,
                json = parsed.fragment,
                fragment = true,
                sourceLink = t,
            )
        }
        if (ConfigParser.isSupportedLink(t)) {
            val server = runCatching { ConfigParser.parse(t) }.getOrNull() ?: return null
            val name = server.remark.ifBlank { "${server.address}:${server.port}" }
            return XrayProfile(
                id = id,
                name = name,
                subscriptionId = subscriptionId,
                payload = XrayPayload.Link(t),
            )
        }
        return null
    }

    /** Pasted/imported text: a link, a full sing-box JSON config, or Clash YAML. */
    fun fromText(text: String, id: String, subscriptionId: String? = null): Profile? {
        val t = text.trim()
        if (t.isEmpty()) return null
        fromLink(t, id, subscriptionId)?.let { return it }
        if (MihomoProfile.looksLikeMihomo(t)) {
            return MihomoProfile(id = id, name = "", subscriptionId = subscriptionId, yaml = t)
        }
        if (t.startsWith("{") && runCatching { json.parseToJsonElement(t) as? JsonObject }.getOrNull() != null) {
            return SingBoxProfile(
                id = id,
                name = "",
                subscriptionId = subscriptionId,
                json = t,
                fragment = false,
            )
        }
        return null
    }

    /**
     * Stable identity of a profile's payload across subscription refreshes:
     * a refreshed member whose identity matches an existing one keeps the
     * existing id, so chains referencing it survive the refresh (§3.5).
     */
    fun identityKey(profile: Profile): String = when (profile) {
        is XrayProfile -> when (val p = profile.payload) {
            is XrayPayload.Link -> "link:${p.link}"
            is XrayPayload.RawJson -> "rawjson:${p.json}"
            is XrayPayload.Sealed -> "sealed:${p.zsx}"
        }
        is SingBoxProfile ->
            if (profile.sourceLink.isNotBlank()) "sblink:${profile.sourceLink}" else "sbjson:${profile.json}"
        is AetherProfile -> "aether:${AetherLink.toShareLink(profile)}"
        is WhitednsProfile -> "whitedns:${WhitednsLink.build(profile)}"
        is PsiphonProfile -> "psiphon:${profile.country}|${profile.mode}|${profile.cdnIps.joinToString(",")}|${profile.cdnSni}"
        is MihomoProfile ->
            if (profile.subUrl.isNotBlank()) "mihomo-sub:${profile.subUrl}" else "mihomo:${profile.yaml}"
        is ChainProfile -> "chain:${profile.hopIds.joinToString(",")}"
        is AutoSelectProfile -> "auto:${profile.targetSubscriptionId}"
    }

    /** The share link for a profile, or null when the variant has none. */
    fun shareLink(profile: Profile): String? = when (profile) {
        is XrayProfile -> (profile.payload as? XrayPayload.Link)?.link
        is SingBoxProfile -> when {
            profile.sourceLink.isNotBlank() -> profile.sourceLink
            profile.fragment -> firstOutbound(profile.json)?.let { SingBoxLinks.build(it, profile.name) }
            else -> null
        }
        is AetherProfile -> AetherLink.toShareLink(profile)
        is WhitednsProfile -> WhitednsLink.build(profile)
        else -> null
    }

    /** The profile's server endpoint (host, port) when it has a fixed one. */
    fun endpointOf(profile: Profile): Pair<String, Int>? = when (profile) {
        is XrayProfile -> when (val p = profile.payload) {
            is XrayPayload.Link ->
                runCatching { ConfigParser.parse(p.link) }.getOrNull()?.let { it.address to it.port }
            is XrayPayload.RawJson -> xrayJsonEndpoint(p.json)
            is XrayPayload.Sealed -> null
        }
        is SingBoxProfile -> firstOutbound(profile.json)?.let { ob ->
            val server = (ob["server"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val port = (ob["server_port"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
            if (server != null && port != null) server to port else null
        }
        is AetherProfile ->
            if (profile.address.isNotBlank()) {
                profile.address to (profile.port.toIntOrNull() ?: 443)
            } else {
                null
            }
        else -> null
    }

    /** Short protocol label for cards ("vless", "tuic", "wg", …). */
    fun protocolLabel(profile: Profile): String = when (profile) {
        is XrayProfile -> when (val p = profile.payload) {
            is XrayPayload.Link ->
                runCatching { ConfigParser.parse(p.link).protocol.id }.getOrDefault("xray")
            is XrayPayload.RawJson -> "json"
            is XrayPayload.Sealed -> "sealed"
        }
        is SingBoxProfile ->
            firstOutbound(profile.json)?.let {
                (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            } ?: "sing-box"
        is AetherProfile -> profile.protocol
        is MihomoProfile -> if (profile.subUrl.isNotBlank() && profile.yaml.isBlank()) "clash-sub" else "clash"
        is PsiphonProfile -> "psiphon"
        is WhitednsProfile -> profile.engine.name.lowercase()
        is ChainProfile -> "chain"
        is AutoSelectProfile -> "auto"
    }

    /** A blank profile of [kind] for the "new" editor flow (name filled in the sheet). */
    fun blank(kind: CoreKind, id: String): Profile = when (kind) {
        CoreKind.XRAY -> XrayProfile(id = id, name = "", payload = XrayPayload.Link(""))
        CoreKind.SINGBOX -> SingBoxProfile(id = id, name = "", json = "", fragment = false)
        CoreKind.PSIPHON -> PsiphonProfile(id = id, name = "")
        CoreKind.AETHER -> AetherProfile(id = id, name = "")
        CoreKind.MIHOMO -> MihomoProfile(id = id, name = "")
        CoreKind.WHITEDNS -> WhitednsProfile(
            id = id,
            name = "",
            domains = emptyList(),
            encryptionKey = "",
            encryptionMethod = 3,
            resolvers = emptyList(),
        )
        CoreKind.CHAIN -> ChainProfile(id = id, name = "", hopIds = emptyList())
        CoreKind.AUTO -> AutoSelectProfile(id = id, name = "", targetSubscriptionId = "")
    }

    private fun firstOutbound(text: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val outbounds = root["outbounds"] as? JsonArray
        return (outbounds?.firstOrNull() as? JsonObject) ?: root
    }

    private fun xrayJsonEndpoint(text: String): Pair<String, Int>? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val outbounds = root["outbounds"] as? JsonArray ?: return null
        for (ob in outbounds) {
            val settings = (ob as? JsonObject)?.get("settings") as? JsonObject ?: continue
            val vnext = (settings["vnext"] as? JsonArray)?.firstOrNull() as? JsonObject
            val servers = (settings["servers"] as? JsonArray)?.firstOrNull() as? JsonObject
            val node = vnext ?: servers ?: continue
            val address = node["address"]?.jsonPrimitive?.contentOrNull ?: continue
            val port = node["port"]?.jsonPrimitive?.intOrNull ?: continue
            return address to port
        }
        return null
    }
}
