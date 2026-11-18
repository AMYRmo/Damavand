package com.amyrmo.damavand.lite.psiphon

import android.util.Base64
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The config of the standalone Psiphon core (1.0.1.6): the JSON the
 * psiphon-tunnel-core console client takes with `-config`, ported
 * from Aether's `build_config` (aether/psiphon.rs) — the same config
 * Aether builds for the exact binary this app ships, in its Auto
 * shape: no in-proxy protocols, no tactics, the bundled server list
 * as the first entries, the remote list as the update.
 *
 * No Aether is involved: Lite spawns the console client itself
 * ([PsiphonEngine]) and bridges its SOCKS listener through the tun.
 * The client needs no server of its own — it dials Psiphon's public
 * servers from the bundled list, with [EgressRegion] pinning the
 * exit country when one is set ("Any country" = no region).
 */
object PsiphonConfig {

    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"

    private const val SERVER_LIST_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"

    private const val SERVER_LIST_SIGNATURE_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
            "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
            "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
            "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
            "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
            "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
            "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
            "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
            "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
            "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    /** The tunnel protocols the Auto shape offers (Aether's NON_INPROXY_PROTOCOLS). */
    private val PROTOCOLS = listOf(
        "SSH",
        "OSSH",
        "TLS-OSSH",
        "UNFRONTED-MEEK-OSSH",
        "UNFRONTED-MEEK-HTTPS-OSSH",
        "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "QUIC-OSSH",
        "SHADOWSOCKS-OSSH",
        "FRONTED-MEEK-OSSH",
        "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH",
        "FRONTED-MEEK-QUIC-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
    )

    /** Aether's Psiphon overlay, written into the config itself: resolvers of its own, since Android has no resolver config a Linux-built program could read. */
    private val DNS_RESOLVERS = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4")

    /**
     * The config JSON for a client whose data root is [dataDir],
     * listening for SOCKS on [socksPort] and leaving from [region]
     * (a 2-letter ISO code, or null for "Any country").
     */
    fun build(dataDir: File, socksPort: Int, region: String?): String {
        val code = region?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        val listUrl = Base64.encodeToString(SERVER_LIST_URL.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return buildJsonObject {
            put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            put("SponsorId", SPONSOR_ID)
            put("ClientVersion", "1")
            put("DataRootDirectory", dataDir.absolutePath)
            put("LocalSocksProxyPort", socksPort)
            put("DisableLocalHTTPProxy", true)
            put("EmitDiagnosticNotices", true)
            put("EmitDiagnosticNetworkParameters", true)
            put("EmitServerAlerts", true)
            put("EmitBytesTransferred", false)
            putJsonArray("RemoteServerListURLs") {
                add(buildJsonObject { put("URL", listUrl) })
            }
            put("RemoteServerListSignaturePublicKey", SERVER_LIST_SIGNATURE_KEY)
            put("InproxyTunnelProtocolPreferProbability", 0.0)
            put("InproxyTunnelProtocolSelectionProbability", 0.0)
            putJsonArray("LimitTunnelProtocols") {
                for (protocol in PROTOCOLS) add(JsonPrimitive(protocol))
            }
            putJsonArray("DNSResolverAlternateServers") {
                for (resolver in DNS_RESOLVERS) add(JsonPrimitive(resolver))
            }
            if (code != null) put("EgressRegion", code)
        }.toString()
    }
}
