package com.amyrmo.damavand.lite

import kotlinx.serialization.Serializable

/** One VPN profile (a parsed share link + its last test result). */
@Serializable
data class Profile(
    val id: String,
    val link: String,
    val name: String,
    val proto: String,
    val address: String,
    val port: Int,
    val country: String = "",
    val city: String = "",
    val countryCode: String = "",
    val core: String = "Xray",
    val lastPingMs: Long = -1,
    val lastTestedAt: Long = 0,
    val subscriptionUrl: String = "",
    // 3.2.10 (Aether): the Aether settings of an Aether profile, as
    // JSON (see AetherSettings); empty for every other profile. An
    // Aether profile's link is "aether://<uuid>", its address empty
    // and its port 0 — it has no server of its own to ping or test.
    val aetherJson: String = "",
) {
    /** True when this profile has a usable last test (ping > 0). */
    val hasPing: Boolean get() = lastPingMs > 0

    /** True when this profile runs on the Aether core. */
    val isAether: Boolean get() = proto == "aether" || aetherJson.isNotEmpty()

    /**
     * True when this profile runs on the standalone Psiphon core
     * (1.0.1.6): its link is "psiphon://<id>", it has no server of
     * its own to ping or test, and its exit country is [countryCode]
     * (empty = "Any country").
     */
    val isPsiphon: Boolean get() = proto == "psiphon"

    companion object {
        /** Stable id from the link itself (so re-imports dedupe). */
        fun idFor(link: String): String = link.hashCode().toUInt().toString(16)

        /**
         * Identity ignoring the #remark fragment (issue #91): the
         * fragment is only the display name — a panel rename must not
         * change the profile's identity in the deleted-links blocklist,
         * or a deleted server is resurrected under its new name on the
         * next refresh. Profile ids themselves stay fragment-sensitive:
         * changing idFor would orphan every stored profile, chain and
         * usage row.
         */
        fun idForStable(link: String): String = idFor(link.substringBefore('#'))

        /** A standalone Aether profile: a fresh uuid as its stable id and link. */
        fun aether(name: String, aetherJson: String): Profile {
            val id = java.util.UUID.randomUUID().toString()
            return Profile(
                id = id,
                link = "aether://$id",
                name = name,
                proto = "aether",
                address = "",
                port = 0,
                core = "Aether",
                aetherJson = aetherJson,
            )
        }
    }
}

/**
 * A proxy chain (PattNG PROXYCHAIN): an ordered list of profiles whose
 * traffic flows entry → … → exit. [memberIds] are in the order the user
 * picked them (entry first, exit last) — the order PattNG stores a
 * chain's members in; the core resolves them exit-first when building.
 */
@Serializable
data class Chain(
    val id: String,
    val name: String,
    val memberIds: List<String>,
)
