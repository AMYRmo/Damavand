package com.amyrmo.damavand.lite.root

import com.amyrmo.damavand.lite.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects whether the device grants root (`su`) access.
 *
 * The result is cached after the first successful probe. Probing spawns `su` and
 * blocks, so [refresh] (a suspending call on [Dispatchers.IO]) should be used from UI
 * code; [isRootAvailable] may block and must not be called on the main thread the first time.
 * Ported from PattNG's RootManager (3.2.10); LogUtil became AppLog.
 */
object RootManager {

    private const val TAG = "RootManager"

    @Volatile
    private var cached: Boolean? = null

    /** Last known result without probing. Defaults to false when never probed. */
    fun cachedRoot(): Boolean = cached ?: false

    /**
     * Returns whether root is available, probing once if unknown.
     * May block while `su` is spawned; avoid calling on the main thread before a probe.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    /** Probes root off the main thread, updates the cache, and returns the result. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val result = probe()
        cached = result
        result
    }

    private fun probe(): Boolean {
        return try {
            val result = RootProcessRunner.run(listOf("su", "-c", "id -u"), 10000, 1024)
            if (result.code == -1) {
                AppLog.w(TAG, "RootManager: su probe timed out")
                return false
            }
            val isRoot = result.code == 0 && result.output.lineSequence().filter { it.isNotBlank() }.lastOrNull()?.trim() == "0"
            AppLog.i(TAG, "RootManager: root available = $isRoot")
            isRoot
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            AppLog.w(TAG, "RootManager: root probe failed")
            false
        }
    }
}
