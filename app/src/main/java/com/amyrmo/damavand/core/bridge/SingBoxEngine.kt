package com.amyrmo.damavand.core.bridge

import android.content.Context
import android.net.VpnService
import com.amyrmo.damavand.core.AppLog as Log
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.platform.AppInfo
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import libbox.CommandClient
import libbox.CommandClientHandler
import libbox.CommandClientOptions
import libbox.CommandServer
import libbox.CommandServerHandler
import libbox.ConnectionEvents
import libbox.Libbox
import libbox.LogIterator
import libbox.OutboundGroupItemIterator
import libbox.OutboundGroupIterator
import libbox.OverrideOptions
import libbox.SetupOptions
import libbox.StatusMessage
import libbox.StringIterator
import libbox.SystemProxyStatus

/**
 * v1's `SingBoxEngine`, ported to the bridge package with its
 * `PlatformInterface` implementation extracted into [SingBoxPlatform]
 * (architecture §2.5). Deltas from v1, all forced by the v2 shape:
 *
 *  - the constructor takes a [PerAppSelection] and a plain
 *    `postNotification: (String, String) -> Unit` instead of v1's
 *    `AppSettings` (settings → platform seam, §2.5) — the platform is
 *    constructed from them here;
 *  - libbox setup uses v2's `AppInfo.versionName(context)` (v1 read a
 *    `versionName` property from the KMP shared module; v2's
 *    platform AppInfo is a function of Context);
 *  - the TUN descriptor and the interface monitor live in the platform;
 *    [stop] tears them down through [SingBoxPlatform.shutdown], which is
 *    v1's stop behaviour split along the same seam.
 *
 * Instances are NOT singletons: a sing-box hop and the TUN bridge may run
 * two engines at once, each with its own CommandServer/CommandClient.
 * (The one process-global piece, `Libbox.setup`, stays setup-once via
 * the companion, as in v1.)
 */
class SingBoxEngine(
    private val service: VpnService,
    private val session: String,
    private val perApp: PerAppSelection,
    private val postNotification: (String, String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
) {
    private val active = AtomicBoolean(false)

    @Volatile private var server: CommandServer? = null
    @Volatile private var client: CommandClient? = null

    @Volatile var uplinkTotal: Long = 0; private set
    @Volatile var downlinkTotal: Long = 0; private set

    val isRunning: Boolean get() = active.get()

    private val platform = SingBoxPlatform(
        service = service,
        sessionName = session,
        perApp = perApp,
        postNotification = postNotification,
    )

    fun start(configJson: String) {
        setUp(service)
        val commandServer = CommandServer(serverHandler, platform)
        server = commandServer
        try {
            commandServer.start()
            commandServer.startOrReloadService(configJson, OverrideOptions())
        } catch (e: Exception) {
            stop()
            throw e
        }
        active.set(true)
        connectClient()
    }

    private fun connectClient() {
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            addCommand(Libbox.CommandLog)
            // 3 s, not 1 s (E2): the status callback only refreshes the
            // cumulative uplink/downlink totals the metrics loop diffs,
            // and that loop now repaints at a 3 s cadence — polling the
            // core every second for the whole session bought nothing.
            statusInterval = 3_000_000_000L
        }
        val commandClient = CommandClient(clientHandler, options)
        client = commandClient

        Thread({
            runCatching { commandClient.connect() }
                .onFailure { Log.w(TAG, "command client", it) }
        }, "sing-box-client").start()
    }

    fun stop() {
        val wasActive = active.getAndSet(false)
        runCatching { client?.disconnect() }
        client = null
        server?.let { s ->
            runCatching { s.closeService() }
            runCatching { s.close() }
        }
        server = null
        platform.shutdown()
        if (wasActive) Log.i(TAG, "stopped")
    }

    fun resetNetwork() {
        runCatching { server?.resetNetwork() }
    }

    private val serverHandler = object : CommandServerHandler {
        override fun serviceStop() {
            if (active.get()) onStopped(null)
        }

        override fun serviceReload() = Unit
        override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus()
        override fun setSystemProxyEnabled(enabled: Boolean) = Unit
        override fun triggerNativeCrash() = Unit
        override fun writeDebugMessage(message: String) { Log.d(TAG, message) }
        override fun connectSSHAgent(): Int = throw UnsupportedOperationException("no SSH agent")
    }

    private val clientHandler = object : CommandClientHandler {
        override fun connected() = Unit
        override fun disconnected(message: String?) {
            if (!message.isNullOrBlank()) Log.i(TAG, "command client: $message")
        }

        override fun clearLogs() = Unit
        override fun setDefaultLogLevel(level: Int) = Unit
        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit
        override fun updateClashMode(newMode: String?) = Unit
        override fun writeConnectionEvents(events: ConnectionEvents?) = Unit
        override fun writeGroups(groups: OutboundGroupIterator?) = Unit
        override fun writeOutbounds(outbounds: OutboundGroupItemIterator?) = Unit

        override fun writeLogs(messageList: LogIterator?) {
            val logs = messageList ?: return
            while (logs.hasNext()) {
                val entry = logs.next() ?: continue
                onLog("${levelChar(entry.level)}/sing-box: ${entry.message}")
            }
        }

        override fun writeStatus(message: StatusMessage?) {
            val status = message ?: return
            uplinkTotal = status.uplinkTotal
            downlinkTotal = status.downlinkTotal
        }
    }

    companion object {
        private const val TAG = "SingBoxEngine"

        private val setUpDone = AtomicBoolean(false)

        private fun setUp(context: Context) {
            if (!setUpDone.compareAndSet(false, true)) return
            val base = File(context.filesDir, "sing-box").apply { mkdirs() }
            Libbox.setup(SetupOptions().apply {
                basePath = base.absolutePath
                workingPath = File(base, "work").apply { mkdirs() }.absolutePath
                tempPath = File(context.cacheDir, "sing-box").apply { mkdirs() }.absolutePath

                fixAndroidStack = true
                logMaxLines = 3000L
                crashReportSource = "damavand"
                appVersion = AppInfo.versionName(context)
            })
        }

        private fun levelChar(level: Int): String = when (level) {
            0, 1, 2 -> "E"
            3 -> "W"
            4 -> "I"
            else -> "D"
        }
    }
}
