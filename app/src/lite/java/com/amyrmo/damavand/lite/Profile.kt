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
) {
    /** True when this profile has a usable last test (ping > 0). */
    val hasPing: Boolean get() = lastPingMs > 0

    companion object {
        /** Stable id from the link itself (so re-imports dedupe). */
        fun idFor(link: String): String = link.hashCode().toUInt().toString(16)
    }
}
