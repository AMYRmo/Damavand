package com.amyrmo.damavand.core.adapters

import android.content.Context
import com.amyrmo.damavand.core.AppLog as Log
import com.amyrmo.damavand.logic.XrayTraffic
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * v1's `XrayController`, ported from a singleton object to an INSTANCE
 * class (architecture §2.5): the chain collapse needs at most one *main*
 * Xray per session, but the singleton's "start stops the running loop" is
 * exactly what killed old Xray-outer+Xray-inner chains; instance-scoped
 * controllers remove the collision by construction.
 *
 * The one process-global piece — `Libv2ray.initCoreEnv` — stays guarded
 * by a companion [AtomicBoolean], so a second instance never re-runs it.
 * Each instance creates its own [CoreController] via
 * `Libv2ray.newCoreController`, with v1's stoppingOnPurpose /
 * onCoreShutdown guard kept per instance.
 *
 * Deviations from v1, per the v2 contract:
 *  - no `CoreCrashLog.install` (v2 has no CoreCrashLog);
 *  - traffic parsing delegates to `:logic` [XrayTraffic.trafficDeltaOf]
 *    (v1's inline copy, extracted and unit-tested there);
 *  - `warmUp`, `runtimeReport`, `cryptoAcceleration` and
 *    `measureDelayDetailed`/`CoreProbe` are dropped — nothing in v2
 *    consumes them.
 */
class XrayController(context: Context) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var controller: CoreController? = null

    /** Fired when the core shuts down without [stop] having been called. */
    var onCoreShutdown: (() -> Unit)? = null

    @Volatile
    private var stoppingOnPurpose = false

    @Volatile
    var lastError: String? = null
        private set

    private fun ready(): CoreController? {
        controller?.let { return it }
        return synchronized(this) {
            controller?.let { return@synchronized it }
            runCatching {
                initCoreEnvOnce(appContext)
                Libv2ray.newCoreController(object : CoreCallbackHandler {
                    override fun onEmitStatus(status: Long, message: String?): Long = 0
                    override fun startup(): Long = 0
                    override fun shutdown(): Long {
                        if (!stoppingOnPurpose) onCoreShutdown?.invoke()
                        return 0
                    }
                })
            }.getOrNull()?.also { controller = it }
        }
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
            Log.e(TAG, "startLoop failed", e)
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
            runCatching { c.stopLoop() }.onFailure { Log.e(TAG, "stopLoop failed", it) }
        } finally {
            stoppingOnPurpose = false
        }
    }

    /**
     * The traffic moved since the previous call — the core's counters are
     * per-query deltas (see `:logic` [XrayTraffic]); callers that want
     * session totals accumulate.
     */
    fun readTrafficDelta(): Pair<Long, Long> =
        XrayTraffic.trafficDeltaOf(runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull())

    fun measureDelay(url: String): Long =
        runCatching { ready()?.measureDelay(url) ?: -1L }.getOrDefault(-1L)

    companion object {
        private const val TAG = "XrayController"

        /** `Libv2ray.initCoreEnv` is process-global; run it exactly once. */
        private val envInitialized = AtomicBoolean(false)

        private fun initCoreEnvOnce(context: Context) {
            if (!envInitialized.compareAndSet(false, true)) return
            try {
                val assets = File(context.filesDir, "assets").apply { mkdirs() }.absolutePath
                Libv2ray.initCoreEnv(assets, "")
            } catch (e: Exception) {
                // Allow a later instance to retry a failed one-time init.
                envInitialized.set(false)
                throw e
            }
        }
    }
}
