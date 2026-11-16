package com.amyrmo.damavand.lite

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocketFactory

/**
 * Real per-profile ping: TCP connect, then a TLS handshake when the
 * profile uses TLS (SNI from the profile). Returns the round-trip time
 * in milliseconds, or -1 when the profile is unreachable / the handshake
 * fails. A failed TLS handshake is a failed test (not a TCP-only ping),
 * because a config that cannot complete TLS cannot carry a tunnel.
 */
object PingTester {

    private const val TIMEOUT_MS = 5000

    // 3.2.11 (PattNG PREF_PREFER_IPV6): when the profile's address is a
    // hostname, pick the resolved address of the preferred family.
    private fun resolveTarget(host: String, preferIpv6: Boolean): InetAddress {
        val all = runCatching { InetAddress.getAllByName(host) }.getOrNull()
            ?: return InetAddress.getByName(host)
        return all.firstOrNull { (it is Inet6Address) == preferIpv6 } ?: all.first()
    }

    fun ping(profile: Profile, preferIpv6: Boolean = false): Long {
        val parsed = XrayConfig.parse(profile.link) ?: return -1
        val t0 = System.currentTimeMillis()
        return try {
            val target = resolveTarget(parsed.addr, preferIpv6)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target, parsed.port), TIMEOUT_MS)
                if (parsed.tls == "tls") {
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val ssl = factory.createSocket(socket, parsed.addr, parsed.port, true) as javax.net.ssl.SSLSocket
                    try {
                        val sni = parsed.sni.ifEmpty { parsed.addr }
                        val params = ssl.sslParameters
                        params.serverNames = listOf(SNIHostName(sni))
                        params.endpointIdentificationAlgorithm = null
                        ssl.sslParameters = params
                        ssl.soTimeout = TIMEOUT_MS
                        ssl.startHandshake()
                    } finally {
                        runCatching { ssl.close() }
                    }
                }
            }
            System.currentTimeMillis() - t0
        } catch (_: Exception) {
            -1
        }
    }
}
