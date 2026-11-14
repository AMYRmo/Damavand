package com.amyrmo.damavand.lite

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/** Thin wrapper over libv2ray (zedcore.aar): start/stop one Xray core. */
object XrayController {
    private const val TAG = "XrayController"

    private var controller: CoreController? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    var onCoreShutdown: (() -> Unit)? = null

    @Volatile
    private var stoppingOnPurpose = false

    @Volatile
    var lastError: String? = null
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val assets = File(context.filesDir, "assets").apply { mkdirs() }.absolutePath
        Libv2ray.initCoreEnv(assets, "")
        controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun onEmitStatus(status: Long, message: String?): Long = 0
            override fun startup(): Long = 0
            override fun shutdown(): Long {
                if (!stoppingOnPurpose) onCoreShutdown?.invoke()
                return 0
            }
        })
        initialized = true
    }

    /**
     * Stores the application context for the lazy init below. This touches
     * NO native code: loading libgojni happens only on the first connect
     * or test, so a broken native lib can never kill the app launch.
     */
    fun setAppContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun ready(): CoreController? {
        if (!initialized) appContext?.let { runCatching { init(it) } }
        return controller
    }

    val isRunning: Boolean
        get() = controller?.isRunning ?: false

    fun start(configJson: String): Boolean {
        val c = ready() ?: return false
        if (c.isRunning) stopQuietly(c)
        lastError = null
        return try {
            c.startLoop(configJson, 0)
            c.isRunning
        } catch (e: Exception) {
            AppLog.e(TAG, "startLoop failed", e)
            lastError = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            false
        }
    }

    fun stop() {
        controller?.takeIf { runCatching { it.isRunning }.getOrDefault(false) }?.let { stopQuietly(it) }
    }

    @Synchronized
    private fun stopQuietly(c: CoreController) {
        stoppingOnPurpose = true
        try {
            runCatching { c.stopLoop() }.onFailure { AppLog.e(TAG, "stopLoop failed", it) }
        } finally {
            stoppingOnPurpose = false
        }
    }

    /** (down, up) bytes since the last call, for the "proxy" outbound. */
    fun readTrafficDelta(): Pair<Long, Long> {
        val raw = runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull() ?: return 0L to 0L
        var down = 0L
        var up = 0L
        var busiestDown = 0L
        var busiestUp = 0L
        for (entry in raw.split(';')) {
            if (entry.isEmpty()) continue
            val parts = entry.split(',')
            if (parts.size != 3) continue
            val value = parts[2].toLongOrNull() ?: continue
            if (parts[0] in setOf("direct", "block", "blocked", "dns-out", "dns_out", "api", "fragment")) continue
            if (parts[0] == "proxy") {
                when (parts[1]) {
                    "downlink" -> down += value
                    "uplink" -> up += value
                }
            }
            if (parts[1] == "downlink") busiestDown = maxOf(busiestDown, value)
            if (parts[1] == "uplink") busiestUp = maxOf(busiestUp, value)
        }
        return if (down == 0L && up == 0L) busiestDown to busiestUp else down to up
    }

    fun measureDelay(url: String): Long =
        runCatching { ready()?.measureDelay(url) ?: -1L }.getOrDefault(-1L)
}
