package com.amyrmo.damavand.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WhitednsLinkException(message: String) : Exception(message)

/**
 * The WhiteDNS profile-link codec (architecture §3.5, research report
 * §1.5): `<scheme>://base64(JSON)` where the JSON is the
 * `whitedns.profile` v1 schema —
 *
 * ```
 * {"schema":"whitedns.profile","version":1,"profile":{
 *    "name":…,
 *    "server":{"domain":…,"encryption_key":…,"encryption_method":0–5},
 *    "cottenSettings":{…}?,
 *    "resolvers":[…]}}
 * ```
 *
 * The scheme names the engine: `stormdns` → [WhitednsEngine.STORM],
 * `masterdns` → [WhitednsEngine.MASTER], `cottendns` →
 * [WhitednsEngine.COTTEN]. `soar://` is recognised and rejected with a
 * clear "not supported yet" error (§3.5) instead of a generic parse
 * failure.
 *
 * Mapping decisions (the schema predates the v2 model):
 * - The schema's server carries ONE `domain`; the v2 model carries a
 *   list. Import maps the single domain to a one-element list (or reads
 *   a `domains` array when a v2 export wrote one). Export writes
 *   `domain` = first entry, plus the full `domains` array only when
 *   there is more than one — so a canonical WhiteDNS link round-trips
 *   byte-for-byte at the JSON level.
 * - v2-only settings (tunnel mode, resolver transport, balancing
 *   strategy, DNS) ride inside `cottenSettings`, and the object is
 *   written only when at least one of them differs from the defaults —
 *   a default profile's export is exactly the upstream schema.
 */
@OptIn(ExperimentalEncodingApi::class)
object WhitednsLink {
    const val SCHEMA = "whitedns.profile"
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun schemeOf(link: String): String = link.trim().substringBefore("://", "").lowercase()

    fun handles(link: String): Boolean =
        schemeOf(link) in setOf("stormdns", "masterdns", "cottendns", "soar")

    fun engineForScheme(scheme: String): WhitednsEngine? = when (scheme) {
        "stormdns" -> WhitednsEngine.STORM
        "masterdns" -> WhitednsEngine.MASTER
        "cottendns" -> WhitednsEngine.COTTEN
        else -> null
    }

    fun schemeForEngine(engine: WhitednsEngine): String = when (engine) {
        WhitednsEngine.STORM -> "stormdns"
        WhitednsEngine.MASTER -> "masterdns"
        WhitednsEngine.COTTEN -> "cottendns"
    }

    fun parse(link: String, id: String, subscriptionId: String? = null): WhitednsProfile {
        val trimmed = link.trim()
        val scheme = schemeOf(trimmed)
        if (scheme == "soar") {
            throw WhitednsLinkException("soar:// WhiteDNS profiles are not supported yet")
        }
        val engine = engineForScheme(scheme)
            ?: throw WhitednsLinkException("Unsupported WhiteDNS link scheme: $scheme")
        val payload = trimmed.substringAfter("://")
        val text = runCatching { ConfigParser.decodeBase64Utf8(payload) }
            .getOrElse { throw WhitednsLinkException("WhiteDNS link payload is not base64") }
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw WhitednsLinkException("WhiteDNS link payload is not JSON") }
        if (root.str("schema") != SCHEMA) {
            throw WhitednsLinkException("Not a $SCHEMA link (schema=${root.str("schema")})")
        }
        val version = root.int("version")
        if (version != VERSION) {
            throw WhitednsLinkException("Unsupported $SCHEMA version: $version")
        }
        val profile = root["profile"]?.jsonObject
            ?: throw WhitednsLinkException("WhiteDNS link carries no profile object")
        val server = profile["server"]?.jsonObject
            ?: throw WhitednsLinkException("WhiteDNS link carries no server object")

        val domains = when {
            server["domains"] != null -> server.strings("domains")
            !server.str("domain").isNullOrBlank() -> listOf(server.str("domain")!!)
            else -> emptyList()
        }
        val cotten = profile["cottenSettings"]?.jsonObject
        return WhitednsProfile(
            id = id,
            name = profile.str("name")?.takeIf { it.isNotBlank() } ?: "Whitedns",
            subscriptionId = subscriptionId,
            engine = engine,
            domains = domains,
            encryptionKey = server.str("encryption_key").orEmpty(),
            encryptionMethod = server.int("encryption_method") ?: 0,
            resolvers = profile.strings("resolvers"),
            tunnelMode = when (cotten?.str("tunnel_mode")?.lowercase()) {
                "cottenmax" -> WhitednsMode.COTTENMAX
                else -> WhitednsMode.LEGACY
            },
            resolverTransport = cotten?.str("resolver_transport")?.takeIf { it.isNotBlank() } ?: "auto",
            balancingStrategy = cotten?.int("balancing_strategy") ?: 3,
            dns = cotten?.str("dns").orEmpty(),
        )
    }

    fun build(profile: WhitednsProfile): String {
        val root = buildJsonObject {
            put("schema", SCHEMA)
            put("version", VERSION)
            putJsonObject("profile") {
                put("name", profile.name)
                putJsonObject("server") {
                    profile.domains.firstOrNull()?.let { put("domain", it) }
                    if (profile.domains.size > 1) {
                        putJsonArray("domains") { profile.domains.forEach { add(JsonPrimitive(it)) } }
                    }
                    put("encryption_key", profile.encryptionKey)
                    put("encryption_method", profile.encryptionMethod)
                }
                putJsonArray("resolvers") { profile.resolvers.forEach { add(JsonPrimitive(it)) } }
                if (profile.tunnelMode != WhitednsMode.LEGACY ||
                    profile.resolverTransport != "auto" ||
                    profile.balancingStrategy != 3 ||
                    profile.dns.isNotBlank()
                ) {
                    putJsonObject("cottenSettings") {
                        put("tunnel_mode", if (profile.tunnelMode == WhitednsMode.COTTENMAX) "cottenmax" else "legacy")
                        put("resolver_transport", profile.resolverTransport)
                        put("balancing_strategy", profile.balancingStrategy)
                        if (profile.dns.isNotBlank()) put("dns", profile.dns)
                    }
                }
            }
        }
        val payload = json.encodeToString(JsonObject.serializer(), root)
        return "${schemeForEngine(profile.engine)}://" + Base64.encode(payload.encodeToByteArray())
    }

    /**
     * True when [entry] is a resolver literal the DNS engines accept:
     * an IPv4/IPv6 literal or CIDR, with an optional `:port`
     * (bracketed for IPv6) — hostnames are not resolvers (§4.7 rule V7).
     * Ported from v1's `MasterDnsProfile.isNumericResolver`.
     */
    fun isResolverLiteral(entry: String): Boolean {
        val text = entry.trim()
        if (text.isEmpty()) return false
        if (looksNumericHost(text)) return true
        val host: String
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close < 0) return false
            host = text.substring(1, close)
            val rest = text.substring(close + 1)
            if (rest.isNotEmpty() && !isPort(rest.removePrefix(":"))) return false
        } else {
            val sep = text.lastIndexOf(':')
            if (sep < 0) return false
            host = text.substring(0, sep)
            if (!isPort(text.substring(sep + 1))) return false
        }
        return looksNumericHost(host)
    }

    private fun isPort(s: String): Boolean = s.toIntOrNull()?.let { it in 1..65535 } == true

    private fun looksNumericHost(h: String): Boolean {
        val bare = h.substringBefore('/')
        val suffix = h.substringAfter('/', "")
        if (h.contains('/') && suffix.toIntOrNull()?.let { it in 0..128 } != true) return false
        if (bare.isEmpty()) return false
        if (bare.count { it == ':' } >= 2) {
            return bare.all { it.isDigit() || it == ':' || it == '.' || it in "abcdefABCDEF" }
        }
        if (bare.contains(':')) return false
        val parts = bare.split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all { it.isDigit() } && p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            .orEmpty()
}
