package com.amyrmo.damavand.lite

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/**
 * One HTTP(S) fetch that works in BOTH network states (2.0.2).
 *
 * The phone is often on a VPN whose tunnel is up but does not carry the
 * app's traffic (or whose DNS breaks), while the direct mobile/WiFi path
 * is fine — or the other way round (direct path filtered, tunnel fine).
 * A plain HttpURLConnection only ever tries the default route, so the
 * subscription import and the geo download failed in exactly that state
 * (seen on-device 2026-11-15 with the VPN on: both the owner's own
 * subscription and a GitHub-hosted sub failed, although both answer
 * 200 from the server).
 *
 * So: try the default route first; on any failure, retry the same URL
 * on every available non-VPN network (mobile/WiFi underneath the VPN)
 * via [android.net.Network.openConnection], which bypasses the tunnel.
 * Returns a connected [HttpURLConnection] with a 2xx response, or null
 * when every route failed. Callers own the returned connection.
 */
object HttpFetch {

    /**
     * What a fetch ended as (1.0.1.2, #111): a non-2xx HTTP answer
     * (404/403 for a dead sub URL) is NOT a network failure, and the
     * caller needs to tell the user which one it was.
     */
    sealed interface Outcome {
        data class Ok(val connection: HttpURLConnection) : Outcome
        data class HttpError(val status: Int) : Outcome
        data object Failed : Outcome
    }

    fun openConnection(
        context: Context,
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        userAgent: String,
    ): HttpURLConnection? {
        return when (val outcome = openConnectionDetailed(context, url, connectTimeoutMs, readTimeoutMs, userAgent)) {
            is Outcome.Ok -> outcome.connection
            else -> null
        }
    }

    fun openConnectionDetailed(
        context: Context,
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        userAgent: String,
    ): Outcome {
        var lastStatus: Int? = null
        // Attempt 1: the default route (through the VPN when it is on).
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                configure(connectTimeoutMs, readTimeoutMs, userAgent)
            }
            if (conn.responseCode in 200..299) return Outcome.Ok(conn)
            lastStatus = conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
        }
        // Attempt 2: each non-VPN network, bypassing the tunnel.
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return lastStatus?.let { Outcome.HttpError(it) } ?: Outcome.Failed
        for (network in cm.allNetworks) {
            val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            try {
                val conn = (network.openConnection(URL(url)) as HttpURLConnection).apply {
                    configure(connectTimeoutMs, readTimeoutMs, userAgent)
                }
                if (conn.responseCode in 200..299) return Outcome.Ok(conn)
                lastStatus = conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
        return lastStatus?.let { Outcome.HttpError(it) } ?: Outcome.Failed
    }

    private fun HttpURLConnection.configure(
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        userAgent: String,
    ) {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        setRequestProperty("User-Agent", userAgent)
        instanceFollowRedirects = true
    }
}
