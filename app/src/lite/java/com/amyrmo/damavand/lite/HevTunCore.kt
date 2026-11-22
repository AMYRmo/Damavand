package com.amyrmo.damavand.lite

import android.content.Context
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import java.io.File

/** hev-socks5-tunnel bridge: forwards the VPN tun fd to Xray's local SOCKS port. */
object HevTunCore {
    private const val TAG = "HevTunCore"

    @Volatile
    private var running = false

    @Volatile
    private var stopping: Thread? = null

    fun start(
        context: Context,
        tun: ParcelFileDescriptor,
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,
        pipeline: Boolean = true,
        logLevel: String = DEFAULT_LOG_LEVEL,
        rwTimeout: String = DEFAULT_RW_TIMEOUT,
        // The core's SOCKS inbound requires auth when a user is set.
        socksUser: String = "",
        socksPass: String = "",
    ): Boolean {
        if (running) return false
        stopping?.let { previous ->
            val onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            previous.join(if (onMain) STOP_JOIN_MS else STOP_JOIN_WORKER_MS)
            if (previous.isAlive) {
                AppLog.e(TAG, "the previous hev is still stopping; not starting a second one")
                return false
            }
            stopping = null
        }
        return try {
            val fd = tun.fd
            val (tcpTimeout, udpTimeout) = parseRwTimeout(rwTimeout)
            val yaml = buildYaml(
                socksPort, mtu, ipv4, ipv6,
                pipeline = pipeline,
                logLevel = logLevel,
                tcpTimeoutSeconds = tcpTimeout,
                udpTimeoutSeconds = udpTimeout,
                socksUser = socksUser,
                socksPass = socksPass,
            )
            val file = File(context.filesDir, "hev-tunnel.yaml").apply { writeText(yaml) }
            TProxyService.TProxyStartService(file.absolutePath, fd)
            running = true
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "start failed", e)
            false
        }
    }

    private val STOP_JOIN_MS = 1_500L
    private val STOP_JOIN_WORKER_MS = 10_000L

    fun stop() {
        if (!running) return
        running = false
        val worker = Thread(
            { runCatching { TProxyService.TProxyStopService() }.onFailure { AppLog.e(TAG, "stop failed", it) } },
            "hev-stop",
        )
        stopping = worker
        worker.start()
        val onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        worker.join(if (onMain) STOP_JOIN_MS else STOP_JOIN_WORKER_MS)
        if (worker.isAlive) AppLog.w(TAG, "hev still stopping")
    }

    internal fun buildYaml(
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,
        pipeline: Boolean = true,
        logLevel: String = DEFAULT_LOG_LEVEL,
        tcpTimeoutSeconds: Int = 300,
        udpTimeoutSeconds: Int = 60,
        socksUser: String = "",
        socksPass: String = "",
    ): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  ipv4: $ipv4")
        // The tun captures IPv6 traffic (::/0) whenever it has an
        // IPv6 address, so HEV must be given that address too —
        // without it the IPv6 packets entering the tun go nowhere
        // (issue #82). The address is present exactly when the tun
        // carries IPv6; the "prefer IPv6" toggle stays a ping/routing
        // preference and no longer gates the bridge.
        if (ipv6 != null) appendLine("  ipv6: '$ipv6'")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: 127.0.0.1")
        appendLine("  udp: 'udp'")
        if (pipeline) appendLine("  pipeline: true")
        // Same rule as the Xray SOCKS inbound (XrayConfig.assemble): auth
        // is required whenever the user is non-empty.
        if (socksUser.isNotEmpty()) {
            appendLine("  username: ${yamlQuote(socksUser)}")
            appendLine("  password: ${yamlQuote(socksPass)}")
        }
        appendLine("misc:")
        appendLine("  tcp-buffer-size: 131072")
        appendLine("  udp-recv-buffer-size: 262144")
        appendLine("  tcp-read-write-timeout: ${tcpTimeoutSeconds * 1000}")
        appendLine("  udp-read-write-timeout: ${udpTimeoutSeconds * 1000}")
        appendLine("  log-level: ${logLevel.takeIf { it in LOG_LEVELS } ?: DEFAULT_LOG_LEVEL}")
    }

    /** YAML single-quoted scalar (a quote inside is doubled). */
    private fun yamlQuote(value: String): String = "'${value.replace("'", "''")}'"

    const val DEFAULT_LOG_LEVEL = "warn"
    const val DEFAULT_RW_TIMEOUT = "300,60"

    private val LOG_LEVELS = setOf("debug", "info", "warn", "error")

    internal fun parseRwTimeout(value: String): Pair<Int, Int> {
        val parts = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val tcp = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: 300
        val udp = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: 60
        return tcp to udp
    }
}
