package com.amyrmo.damavand.platform

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the dashboard shows about the session's exit (§5.2 DashboardCard). */
data class GeoInfo(
    val ip: String,
    val country: String?,
    val city: String?,
    /** Round-trip of the lookup request itself, through the tunnel. */
    val pingMs: Int?,
)

/**
 * Exit-IP lookup through the session's advertised local SOCKS port
 * (§5.5: the dashboard's ping/IP are fetched through the session's port,
 * never around it). Plain HTTP over the SOCKS proxy so no DNS leaks
 * outside the tunnel: the proxy resolves the host.
 */
object GeoLookup {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(localSocksPort: Int): GeoInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localSocksPort))
            val conn = URL("http://ip-api.com/json/?fields=status,query,country,city")
                .openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            try {
                val started = System.nanoTime()
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                val pingMs = ((System.nanoTime() - started) / 1_000_000).toInt()
                val root = json.parseToJsonElement(body).jsonObject
                if (root["status"]?.jsonPrimitive?.contentOrNull != "success") return@runCatching null
                GeoInfo(
                    ip = root["query"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null,
                    country = root["country"]?.jsonPrimitive?.contentOrNull,
                    city = root["city"]?.jsonPrimitive?.contentOrNull,
                    pingMs = pingMs,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
