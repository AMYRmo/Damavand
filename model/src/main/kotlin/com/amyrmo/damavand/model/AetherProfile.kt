package com.amyrmo.damavand.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An Aether profile — the post-fix v1 profile, kept field-for-field as
 * audited (architecture §3.1): every field maps onto a flag of the
 * patterniha/Aether core (the vocabulary enums live in AetherSpec.kt in
 * this package), so links, forms and the core agree. In v1 these fields lived
 * on a settings object nested inside the profile; in v2 the profile IS the
 * settings (§3.2), which is also what a chain hop carries by value.
 *
 * Behaviour lives elsewhere on purpose: flag generation is
 * `:logic`'s `AetherFlags`, the share-link codec is [AetherLink] here in
 * `:model`, and verbatim-command handling is `:logic`'s `AetherCommands`.
 */
@Serializable
@SerialName("aether")
data class AetherProfile(
    override val id: String,
    override val name: String,
    override val subscriptionId: String? = null,
    /** AetherProtocol.type */
    val protocol: String = AetherProtocol.WIREGUARD.type,
    /** AetherTransport.type (MASQUE only) */
    val transport: String = AetherTransport.HTTP3.type,
    /** AetherScanMode.type */
    val scanMode: String = AetherScanMode.BALANCED.type,
    /** AetherObfuscation.type */
    val obfuscation: String = AetherObfuscation.AUTO.type,
    /** AetherIpVersion.type */
    val ipVersion: String = AetherIpVersion.V4.type,
    /** custom DNS of the tunnel (blank = core default) */
    val dns: String = "",
    /** exit-node location (blank = core default) */
    val exitLoc: String = "",
    /** MASQUE ClientHello SNI (blank = default) */
    val masqueSni: String = "",
    /** fragment the MASQUE-over-HTTP/2 ClientHello */
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    /** Encrypted Client Hello on the MASQUE handshakes */
    val ech: Boolean = false,
    val echDns: String = "",
    val echDomain: String = "",
    /** AetherFingerprint.type (over-MASQUE only) */
    val fingerprint: String = AetherFingerprint.CHROME.type,
    /** outer/inner hop endpoints of the two-hop protocols (blank = scan) */
    val wiwOuter: String = "",
    val wiwInner: String = "",
    /** single-endpoint protocols: host and port (blank = scan) */
    val address: String = "",
    val port: String = "",
    /** AetherPsiphon.type */
    val psiphon: String = AetherPsiphon.OFF.type,
    /** AetherPsiphonMode.type */
    val psiphonMode: String = AetherPsiphonMode.AUTO.type,
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: String = "",
    val psiphonRegion: String = "",
    /** false = Psiphon fetches a fresh server list before dialing */
    val psiphonBundledList: Boolean = true,
    /** AetherTor.type */
    val tor: String = AetherTor.OFF.type,
    /** AetherTorBridges.type */
    val torBridges: String = AetherTorBridges.AUTO.type,
    /** AetherTorRelays.type */
    val torRelays: String = AetherTorRelays.AUTO.type,
    /** one bridge line per line */
    val torBridgeLines: String = "",
    /** the exit-node's finalMask JSON and dialMode (newer cores) */
    val finalMask: String = "",
    val dialMode: String = "",
    /** extra raw flags appended verbatim (advanced) */
    val extraArgs: String = "",
    /** SOCKS5 upstream the core dials through (cross-chain carrier), socks5://127.0.0.1:port */
    val upstream: String = "",
    /**
     * A custom command line that runs verbatim instead of the field-derived
     * flags (Narcic Gateway's model). Blank = the settings generate the
     * command. The editor stores it only when it differs from what the
     * settings generate, so it can never drift stale against them.
     */
    val command: String = "",
) : Profile {
    override val kind: CoreKind get() = CoreKind.AETHER

    /** Whether the core should scan instead of dialing a named endpoint. */
    val scanning: Boolean
        get() = if (AetherProtocol.fromString(protocol).twoHops) {
            wiwOuter.isBlank() || wiwInner.isBlank()
        } else {
            address.isBlank()
        }
}
