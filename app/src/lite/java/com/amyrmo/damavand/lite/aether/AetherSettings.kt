package com.amyrmo.damavand.lite.aether

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The settings of one Aether profile, as PattNG's ProfileItem holds them
 * (3.2.10). Null means the profile does not set it — the core's own
 * default, or PattNG's, applies. Stored as JSON on [Profile.aetherJson];
 * the editor writes it, [AetherArgs.buildArguments] reads it.
 *
 * Field names match PattNG's aether* members without the prefix, so the
 * argument builder reads like its PattNG original.
 */
@Serializable
data class AetherSettings(
    val protocol: String? = null,
    val transport: String? = null,
    /** The server name the MASQUE handshakes put in their ClientHello; null means www.cloudflare.com. */
    val masqueSni: String? = null,
    val scanMode: String? = null,
    val obfuscation: String? = null,
    /** The ClientHello fingerprint, an AetherFingerprint type; null means Chrome's. */
    val fingerprint: String? = null,
    val ipVersion: String? = null,
    val wiwOuter: String? = null,
    val wiwInner: String? = null,
    val fragment: Boolean? = null,
    val fragmentSize: String? = null,
    val fragmentDelay: String? = null,
    /** Whether the MASQUE handshake hides its server name with ECH; null means it does not. */
    val ech: Boolean? = null,
    /** The resolver the core asks for the ECH key; null means udp://1.1.1.1. */
    val echDns: String? = null,
    /** The domain whose HTTPS record gives the ECH key; null means cloudflare-ech.com. */
    val echDomain: String? = null,
    /** The resolvers names are looked up with inside the tunnel, comma-separated; null means the core's own. */
    val dns: String? = null,
    /** The exit rule the core holds the tunnel to: country codes to allow, or with a leading ! to refuse; null means any exit. */
    val exitLoc: String? = null,
    /** The name of the profile the core dials out through; null means freedom. */
    val exitNode: String? = null,
    /** Where Psiphon stands in the tunnel, an AetherPsiphon type; null means it is not used. */
    val psiphon: String? = null,
    val psiphonMode: String? = null,
    val psiphonCdnIps: String? = null,
    val psiphonCdnSni: String? = null,
    /** Which of the CDN edge lists built into Psiphon the fronting scan tries, comma separated; null means all. */
    val psiphonCdnSets: String? = null,
    val psiphonRegion: String? = null,
    /** Whether Psiphon starts from the server list the app bundles; null means yes. */
    val psiphonBundledList: Boolean? = null,
    /** Where Tor stands in the tunnel, an AetherTor type; null means it is not used. */
    val tor: String? = null,
    /** When Tor turns to bridges, an AetherTorBridges type; null means when Tor is blocked. */
    val torBridges: String? = null,
    /** The profile's own bridge lines, one per line as torrc writes them. */
    val torBridgeLines: String? = null,
    /** Where Tor's fetched bridges come from, an AetherTorRelays type; null means bridgedb and the public relays. */
    val torRelays: String? = null,
    /** The command line of this profile's core, written by hand in place of the one built from the settings; null follows the settings. */
    val command: String? = null,
    /** The WARP endpoint the core dials (a --peer), host + port; empty means the core scans for one. */
    val server: String? = null,
    val serverPort: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String?): AetherSettings = try {
            if (text.isNullOrBlank()) AetherSettings() else json.decodeFromString<AetherSettings>(text)
        } catch (_: Exception) {
            AetherSettings()
        }

        fun toJson(settings: AetherSettings): String = json.encodeToString(settings)
    }
}
