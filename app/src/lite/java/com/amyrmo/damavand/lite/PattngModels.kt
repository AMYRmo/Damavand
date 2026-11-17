package com.amyrmo.damavand.lite

import kotlinx.serialization.Serializable

/**
 * A subscription (3.1.0, PattNG): a URL with its own name, last
 * refresh time and auto-update flag. Profiles remember which
 * subscription they came from via [Profile.subscriptionUrl]; the
 * Servers tab groups them by it.
 */
@Serializable
data class Subscription(
    val url: String,
    val name: String = "",
    val lastUpdated: Long = 0,
    val autoUpdate: Boolean = true,
    // 3.2.0: usage from the subscription-userinfo response header
    // (upload/download/total bytes, expire epoch seconds; 0 = not
    // reported). Shown on the subscription card in Servers.
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    // 3.2.1: provider links from the same header
    // (profile-web-page-url / support-url); "" = not reported.
    val websiteUrl: String = "",
    val supportUrl: String = "",
    // 3.2.27 (ZedSecure Subscription.title): the panel's
    // `profile-title` response header, base64-decoded; the card
    // shows title ?: name. "" = not reported.
    val title: String = "",
) {
    val displayName: String
        get() = name.ifBlank {
            url.substringAfter("://").substringBefore("/").substringBefore("?")
                .ifBlank { url }
        }
    /** Bytes used in total (upload + download), per the panel. */
    val usedBytes: Long get() = upload + download
}

/**
 * Parsed `subscription-userinfo` header (3.2.0): the panel reports
 * `upload=…; download=…; total=…; expire=…` (bytes, epoch seconds).
 * 3.2.1: also `profile-web-page-url=…` and `support-url=…` when the
 * panel sends them. Not stored itself — its fields live on
 * [Subscription].
 */
data class SubUserInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val websiteUrl: String = "",
    val supportUrl: String = "",
    // 3.2.27 (ZedSecure): the panel's `profile-title` header,
    // decoded; "" = not reported.
    val title: String = "",
)

/**
 * One custom routing rule (3.1.0, PattNG): [pattern] is a domain
 * (example.com, *.example.com, geosite:…), an IP or a CIDR
 * (1.2.3.4, 10.0.0.0/8, geoip:…); [action] is one of
 * "proxy" / "direct" / "block".
 */
@Serializable
data class RoutingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val action: String,
    val enabled: Boolean = true,
)

@Serializable
data class RoutingConfig(
    val mode: String = "bypass_lan", // global | bypass_lan | custom
    val rules: List<RoutingRule> = emptyList(),
)

@Serializable
data class PerAppConfig(
    val mode: String = "bypass", // bypass | allow_only
    val packages: Set<String> = emptySet(),
)
