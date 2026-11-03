package com.amyrmo.damavand.platform

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Honest, small network probes for the UI (§5.2: ping is a real delay
 * test, never a fabricated number).
 *
 * [tcpPing] times a plain TCP connect to a profile's server endpoint —
 * it measures reachability + RTT to the server, not through-tunnel delay,
 * and the UI labels it as such.
 *
 * [dnsProbe] sends a real minimal DNS query (root NS, id 0xD2A5) over
 * UDP to a resolver literal and times the response — the Whitedns
 * editor's resolver test, mirroring what the engine's scan checks first.
 */
object NetProbes {

    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 3_000): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    val started = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    ((System.nanoTime() - started) / 1_000_000).toInt()
                }
            }.getOrNull()
        }

    suspend fun dnsProbe(resolver: String, timeoutMs: Int = 2_500): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val (host, port) = parseResolver(resolver) ?: return@runCatching null
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs
                    val query = dnsQuery()
                    val address = InetAddress.getByName(host)
                    val started = System.nanoTime()
                    socket.send(DatagramPacket(query, query.size, address, port))
                    val buffer = ByteArray(512)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    // Any well-formed DNS response header (≥12 bytes, QR bit set).
                    if (response.length < 12 || (buffer[2].toInt() and 0x80) == 0) {
                        return@runCatching null
                    }
                    ((System.nanoTime() - started) / 1_000_000).toInt()
                }
            }.getOrNull()
        }

    /** `host`, `host:port`, `[v6]`, `[v6]:port` → host to port (default 53). */
    private fun parseResolver(entry: String): Pair<String, Int>? {
        val text = entry.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close < 0) return null
            val host = text.substring(1, close)
            val rest = text.substring(close + 1)
            val port = if (rest.isEmpty()) 53 else rest.removePrefix(":").toIntOrNull() ?: return null
            return host to port
        }
        val sep = text.lastIndexOf(':')
        if (sep < 0) return text to 53
        val host = text.substring(0, sep)
        val port = text.substring(sep + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun dnsQuery(): ByteArray {
        // Header: id, RD=1, QDCOUNT=1. Question: root label, NS(2), IN(1).
        val bytes = ByteArray(17)
        bytes[0] = 0xD2.toByte()
        bytes[1] = 0xA5.toByte()
        bytes[2] = 0x01
        bytes[5] = 0x01
        bytes[13] = 0x02
        bytes[15] = 0x01
        return bytes
    }
}
