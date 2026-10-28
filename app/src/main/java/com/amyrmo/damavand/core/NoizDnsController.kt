package com.amyrmo.damavand.core

import android.os.Build
import com.amyrmo.damavand.core.AppLog as Log
import com.amyrmo.damavand.domain.config.DnsTunnelProfile
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * NoizDNS tunnel (SlipNet's noiz wire) via the gomobile dnstt.aar bindings:
 * Mobile.newClient(dnsAddr, domain, publicKey, listenAddr) with noizMode on.
 * Exposes a local SOCKS5 listener, same contract as DnsTunnelController.
 */
class NoizDnsController(
    private val settings: DnsTunnelProfile,
    private val listenPort: Int,
    private val maxListenPort: Int = listenPort + 9,
    private val listenHost: String = "127.0.0.1",
) {
    private var client: mobile.DnsttClient? = null
    @Volatile private var boundPort: Int = 0

    val isRunning: Boolean get() = runCatching { client?.isRunning }.getOrNull() == true

    @Volatile var lastError: String? = null

    @Synchronized
    fun start(): Int {
        lastError = null
        if (settings.domain.isBlank() || settings.publicKey.isBlank()) {
            lastError = "domain and public key are required"
            Log.e(TAG, lastError!!)
            return -1
        }
        val port = firstFreePort(listenPort)
        if (port < 0) {
            lastError = "no free local port near $listenPort"
            Log.e(TAG, lastError!!)
            return -1
        }
        val listenAddr = "$listenHost:$port"
        val dnsAddr = settings.dnsAddress()

        return try {
            val newClient = mobile.Mobile.newClient(dnsAddr, settings.domain, settings.publicKey, listenAddr)
            if (settings.noizMode) {
                newClient.setNoizMode(true)
                newClient.setDeviceManufacturer(Build.MANUFACTURER ?: "")
                if (settings.noizStealth) {
                    newClient.setStealthMode(true)
                }
            }
            newClient.setResolverMode(settings.resolverMode)
            newClient.setRRSpreadCount(settings.rrSpreadCount.toLong())
            newClient.start()
            Thread.sleep(150)
            if (!newClient.isRunning) {
                lastError = "noiz client did not start"
                Log.e(TAG, lastError!!)
                runCatching { newClient.stop() }
                return -1
            }
            client = newClient
            boundPort = port
            Log.i(TAG, "noizdns started on $listenAddr (resolver=$dnsAddr, domain=${settings.domain})")
            port
        } catch (e: Throwable) {
            lastError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            Log.e(TAG, "noizdns failed to start", e)
            stop()
            -1
        }
    }

    @Synchronized
    fun stop() {
        val c = client
        client = null
        boundPort = 0
        if (c != null) runCatching { c.stop() }
    }

    private fun firstFreePort(preferred: Int): Int {
        if (!isPortInUse(preferred)) return preferred
        for (p in (preferred + 1)..maxListenPort) if (!isPortInUse(p)) return p
        return -1
    }

    private fun isPortInUse(port: Int): Boolean = try {
        ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("127.0.0.1", port)); false }
    } catch (e: Exception) {
        true
    }

    private companion object {
        const val TAG = "NoizDns"
    }
}
