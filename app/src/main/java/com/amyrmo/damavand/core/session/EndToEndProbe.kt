package com.amyrmo.damavand.core.session

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The end-to-end probe (architecture §4.3 step 4): an HTTP fetch through
 * the session's advertised local proxy port. Only a real answer from the
 * far side proves the whole path — hops, splices, bridge, TUN — carries
 * traffic; this is the check whose absence produced v1's "Connected, but
 * traffic is not getting through".
 *
 * The probe speaks SOCKS5 itself (every advertised port in v2 is a SOCKS
 * or mixed listener), CONNECTs to the probe host, then issues a plain
 * HTTP/1.1 request over the tunnel and reads the status line.
 */
object EndToEndProbe {

    private const val PROBE_HOST = "www.gstatic.com"
    private const val PROBE_PATH = "/generate_204"
    private const val PROBE_PORT = 80

    /**
     * True when a 204 (or any 2xx/3xx) comes back through
     * 127.0.0.1:[localPort]. Blocking; call on an IO dispatcher.
     * Retries [attempts] times with [perAttemptTimeoutMs] per attempt.
     */
    fun verify(localPort: Int, attempts: Int = 3, perAttemptTimeoutMs: Int = 10_000): Boolean {
        repeat(attempts) { attempt ->
            if (attemptOnce(localPort, perAttemptTimeoutMs)) return true
            if (attempt < attempts - 1) {
                try {
                    Thread.sleep(750)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
    }

    private fun attemptOnce(localPort: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", localPort), timeoutMs)
                socket.soTimeout = timeoutMs
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // SOCKS5 greeting: version 5, one method, no-auth.
                out.write(byteArrayOf(5, 1, 0))
                out.flush()
                val greeting = readExactly(input, 2)
                if (greeting[0].toInt() != 5 || greeting[1].toInt() != 0) return false

                // CONNECT to the probe host by domain name (ATYP=3).
                val hostBytes = PROBE_HOST.toByteArray(Charsets.US_ASCII)
                val request = ByteArray(7 + hostBytes.size)
                request[0] = 5 // VER
                request[1] = 1 // CONNECT
                request[2] = 0 // RSV
                request[3] = 3 // DOMAINNAME
                request[4] = hostBytes.size.toByte()
                hostBytes.copyInto(request, 5)
                request[5 + hostBytes.size] = (PROBE_PORT ushr 8).toByte()
                request[6 + hostBytes.size] = (PROBE_PORT and 0xFF).toByte()
                out.write(request)
                out.flush()
                val reply = readExactly(input, 4)
                if (reply[0].toInt() != 5 || reply[1].toInt() != 0) return false
                // Drain the bound address per ATYP.
                when (reply[3].toInt()) {
                    1 -> readExactly(input, 4 + 2)
                    3 -> {
                        val len = readExactly(input, 1)[0].toInt() and 0xFF
                        readExactly(input, len + 2)
                    }
                    4 -> readExactly(input, 16 + 2)
                    else -> return false
                }

                // Plain HTTP over the tunnel.
                out.write(
                    "GET $PROBE_PATH HTTP/1.1\r\nHost: $PROBE_HOST\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.US_ASCII),
                )
                out.flush()
                val statusLine = readLine(input) ?: return false
                val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: return false
                code in 200..399
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readExactly(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) throw java.io.EOFException("stream ended at $read/$count")
            read += n
        }
        return buffer
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b < 0) return if (builder.isEmpty()) null else builder.toString()
            if (prev == '\r'.code && b == '\n'.code) {
                builder.setLength(builder.length - 1)
                return builder.toString()
            }
            builder.append(b.toChar())
            prev = b
            if (builder.length > 1024) return builder.toString()
        }
    }
}
