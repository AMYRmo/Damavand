package com.amyrmo.damavand.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The unified profile model (architecture §3.1). One sealed hierarchy; the
 * disk format IS this model (§3.3) — there is no separate storage shape.
 *
 * Every variant carries `@Serializable` and `@SerialName`, no exceptions.
 * The reflective registry test in this module's test source set enumerates
 * the sealed subclasses and round-trips each one, so a future variant that
 * forgets its annotations fails CI instead of silently losing user data
 * (the v1 Mihomo bug: the one variant without a serializer could not
 * persist at all).
 *
 * [kind] is a computed property, deliberately NOT a constructor property:
 * it is derived from the variant, so it is never serialised and can never
 * disagree with the payload actually stored.
 */
@Serializable
sealed interface Profile {
    /** UUID, stable across edits and subscription refreshes. */
    val id: String
    val name: String

    /** Null = manually added. */
    val subscriptionId: String?

    /** The engine kind, derived from the variant (see class doc). */
    val kind: CoreKind
}

/** How an Xray profile's configuration is stored (v1's Link/RawJson/Sealed). */
@Serializable
sealed interface XrayPayload {
    @Serializable
    @SerialName("link")
    data class Link(val link: String) : XrayPayload

    @Serializable
    @SerialName("json")
    data class RawJson(val json: String) : XrayPayload

    /** A base64 `zsx` sealed payload (v1 `ProfileSource.Sealed`). */
    @Serializable
    @SerialName("sealed")
    data class Sealed(val zsx: String) : XrayPayload
}

@Serializable
@SerialName("xray")
data class XrayProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    val payload: XrayPayload,
    /** Optional profile DNS (§4.6 precedence). */
    val dns: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.XRAY
}

/**
 * A sing-box profile: either a full config document
 * ([fragment] = false) or a single-outbound fragment ([fragment] = true).
 * In v2 both run on the sing-box adapter — v1's `SingBox` variant secretly
 * ran on Xray; that naming trap is gone (§3.2).
 */
@Serializable
@SerialName("singbox")
data class SingBoxProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    val json: String,
    val fragment: Boolean,
    /** The share link this fragment was imported from, when there is one. */
    val sourceLink: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.SINGBOX
}

/** Psiphon's operating mode (v1 stored this as a raw string). */
@Serializable
enum class PsiphonMode {
    @SerialName("auto")
    AUTO,

    @SerialName("cdn")
    CDN,

    @SerialName("direct")
    DIRECT,
}

@Serializable
@SerialName("psiphon")
data class PsiphonProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    /** Egress region code ("" = core default). */
    val country: String = "",
    val mode: PsiphonMode = PsiphonMode.AUTO,
    /** CDN fronting IP candidates / CIDRs for the meek scan spec. */
    val cdnIps: List<String> = emptyList(),
    /** CDN fronting SNI server names (space/comma separated, as in v1). */
    val cdnSni: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.PSIPHON
}

@Serializable
@SerialName("mihomo")
data class MihomoProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    /** The Clash YAML document, kept verbatim. */
    val yaml: String = "",
    /** Subscription URL; when set and [yaml] is blank the engine builds a
     *  provider-based config pulling proxies from this URL. */
    val subUrl: String = "",
    /** Override log-level: silent/error/warning/info/debug. */
    val logLevel: String = "info",
    val dns: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.MIHOMO

    companion object {
        /** True when the text looks like a Clash/mihomo YAML document. */
        fun looksLikeMihomo(text: String): Boolean {
            val t = text.trim()
            if (!t.startsWith("port:") && !t.startsWith("mixed-port:") && !t.startsWith("proxies:") &&
                !t.startsWith("proxy-groups:") && !t.startsWith("rules:") && !t.startsWith("---")
            ) {
                return false
            }
            return t.contains("proxies") || t.contains("proxy-groups") || t.contains("rules")
        }
    }
}

/** Which DNS-tunnel engine lineage a [WhitednsProfile] targets. */
@Serializable
enum class WhitednsEngine {
    @SerialName("cotten")
    COTTEN,

    @SerialName("storm")
    STORM,

    @SerialName("master")
    MASTER,
}

/** The tunnel mode written to `TUNNEL_MODE` (CottenDNS only). */
@Serializable
enum class WhitednsMode {
    @SerialName("legacy")
    LEGACY,

    @SerialName("cottenmax")
    COTTENMAX,
}

/**
 * A DNS-tunnel profile over the CottenDNS engine family (architecture
 * §3.1). Field names deliberately mirror the `client_config.toml` keys the
 * engine reads (see phase0-cottendns.md): [domains] → `DOMAINS`,
 * [encryptionKey] → `ENCRYPTION_KEY`, [encryptionMethod] →
 * `DATA_ENCRYPTION_METHOD` (0..5, must match the server),
 * [balancingStrategy] → `RESOLVER_BALANCING_STRATEGY`,
 * [tunnelMode] → `TUNNEL_MODE`, [resolverTransport] → `RESOLVER_TRANSPORT`.
 * The listener itself is NOT stored: `LISTEN_IP`/`LISTEN_PORT` are
 * allocated per session by the planner (§2.4 rule 2).
 */
@Serializable
@SerialName("whitedns")
data class WhitednsProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    val engine: WhitednsEngine = WhitednsEngine.COTTEN,
    /** Tunnel domains delegated to the tunnel server. */
    val domains: List<String>,
    val encryptionKey: String,
    /** 0..5, must match the server. */
    val encryptionMethod: Int,
    /** Resolver IP[:port] literals — validated as IPs (§4.7 rule V7). */
    val resolvers: List<String>,
    val tunnelMode: WhitednsMode = WhitednsMode.LEGACY,
    /** auto|udp|tcp|dot|doh|doh3|doq */
    val resolverTransport: String = "auto",
    val balancingStrategy: Int = 3,
    val dns: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.WHITEDNS
}

/**
 * A user-built chain: ordered references to standalone profiles, never
 * copies (§4.1). `hopIds[0]` is the entry hop, `hopIds.last()` the exit.
 */
@Serializable
@SerialName("chain")
data class ChainProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    val hopIds: List<String>,
) : Profile {
    override val kind: CoreKind get() = CoreKind.CHAIN
}

/** "Smart / fastest" over the members of one subscription (§3.2). */
@Serializable
@SerialName("auto")
data class AutoSelectProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    val targetSubscriptionId: String,
) : Profile {
    override val kind: CoreKind get() = CoreKind.AUTO
}

/**
 * Display/index data derived from a profile's payload (address, port,
 * transport label, last measured ping, byte counters). Deliberately NOT
 * part of the serialised model (§3.1): the stored model is config only, so
 * a stats write can never corrupt a config write.
 */
data class ProfileRuntimeInfo(
    val protocolLabel: String = "",
    val address: String = "",
    val port: Int = 0,
    val transportLabel: String = "",
    val countryCode: String? = null,
    val lastPingMs: Int? = null,
    val bytesDown: Long = 0,
    val bytesUp: Long = 0,
)
