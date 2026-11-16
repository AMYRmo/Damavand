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

    companion object {
        /** Stable id from the link itself (so re-imports dedupe). */
        fun idFor(link: String): String = link.hashCode().toUInt().toString(16)

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
