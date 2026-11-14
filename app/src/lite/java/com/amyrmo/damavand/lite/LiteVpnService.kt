package com.amyrmo.damavand.lite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import kotlin.math.max

/**
 * Damavand Lite — minimal VPN service (Xray core + hev tun bridge).
 *
 * Lite rules (see the design spec):
 * - No wake lock (wake lock off by default; the foreground service keeps
 *   the process alive, the CPU may sleep — that is the battery story).
 * - Quiet notification: IMPORTANCE_MIN + setOnlyAlertOnce, session total.
 * - Screen-off freeze: the poller keeps a slow health-check tick while the
 *   screen is off but does not repaint the notification.
 * - Kill switch: on an UNEXPECTED tunnel drop with the kill switch on, the
 *   tun stays established (traffic entering it is dropped) while the
 *   service reconnects once. An intentional disconnect closes the tun.
 * - One core per connection (Xray only).
 */
class LiteVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.amyrmo.damavand.lite.START"
        const val ACTION_STOP = "com.amyrmo.damavand.lite.STOP"
        const val ACTION_QUERY = "com.amyrmo.damavand.lite.QUERY"
        const val ACTION_STATUS = "com.amyrmo.damavand.lite.STATUS"
        const val ACTION_LOG = "com.amyrmo.damavand.lite.LOG"

        const val EXTRA_LINK = "link"
        const val EXTRA_KILL_SWITCH = "kill_switch"

        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_CONNECTING = "connecting"
        const val STATE_CONNECTED = "connected"

        private const val CHANNEL_ID = "lite"
        private const val NOTIF_ID = 1
        private const val SOCKS_PORT = 1080
        private const val TEST_PORT = 1180

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
            if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / 1024.0 / 1024.0)
            return "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }

    private var worker: Thread? = null
    private var poller: Thread? = null
    private var running = false
    private var connected = false
    private var killSwitchOn = false
    private var intentionalStop = false
    private var tun: ParcelFileDescriptor? = null
    private var currentLink: String? = null

    @Volatile private var location = ""
    @Volatile private var countryCode = ""
    @Volatile private var pingMs: Long = -1
    @Volatile private var downSpeed: Long = 0
    @Volatile private var upSpeed: Long = 0
    @Volatile private var totalDown: Long = 0
    @Volatile private var totalUp: Long = 0
    @Volatile private var lastDetail = ""
    @Volatile private var lastError: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        XrayController.onCoreShutdown = {
            if (connected) {
                broadcastLog("core stopped unexpectedly")
                onTunnelDropped()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                killSwitchOn = intent.getBooleanExtra(EXTRA_KILL_SWITCH, false)
                val link = intent.getStringExtra(EXTRA_LINK)
                if (!running) startTunnel(link)
            }
            ACTION_STOP -> {
                intentionalStop = true
                stopTunnel()
            }
            ACTION_QUERY -> broadcastStatus()
        }
        return START_NOT_STICKY
    }

    // ---- notification (quiet: IMPORTANCE_MIN + setOnlyAlertOnce) ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "دماوند لایت", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, LiteVpnService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("دماوند لایت")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lite)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, "قطع", stopPending)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val text = if (connected) "متصل — مجموع: ${formatBytes(totalDown + totalUp)}" else "در حال اتصال…"
        nm.notify(NOTIF_ID, notification(text))
    }

    // ---- broadcasts ----

    private fun broadcastStatus() {
        val state = when {
            !running -> STATE_DISCONNECTED
            connected -> STATE_CONNECTED
            else -> STATE_CONNECTING
        }
        sendBroadcast(
            Intent(ACTION_STATUS)
                .putExtra("state", state)
                .putExtra("detail", lastDetail)
                .putExtra("location", location)
                .putExtra("countryCode", countryCode)
                .putExtra("ping", pingMs)
                .putExtra("downSpeed", downSpeed)
                .putExtra("upSpeed", upSpeed)
                .putExtra("totalDown", totalDown)
                .putExtra("totalUp", totalUp)
                .setPackage(packageName)
        )
    }

    private fun broadcastLog(line: String) {
        sendBroadcast(Intent(ACTION_LOG).putExtra("line", line).setPackage(packageName))
    }

    // ---- connect ----

    private fun startTunnel(link: String?) {
        running = true
        connected = false
        intentionalStop = false
        location = ""
        countryCode = ""
        pingMs = -1
        downSpeed = 0; upSpeed = 0; totalDown = 0; totalUp = 0
        lastDetail = ""
        lastError = null
        currentLink = link
        startForeground(NOTIF_ID, notification("در حال اتصال…"))
        broadcastStatus()

        worker = Thread({
            try {
                runConnect(link)
            } catch (e: Exception) {
                lastDetail = "خطا: ${e.message}"
                lastError = e.message
                broadcastLog("ERROR: ${e.message}")
                broadcastStatus()
                stopTunnel()
            }
        }, "lite-connect").also { it.start() }
    }

    /**
     * Connect to [link] if given, otherwise to the fastest-tested profile
     * from the repository (falling back to the first reachable profile).
     * A single real tunnel test validates the chosen profile before the
     * tun is established.
     */
    private fun runConnect(link: String?) {
        val repo = ProfileRepository(applicationContext)
        val chosen: String? = when {
            !link.isNullOrBlank() -> link
            else -> {
                val profiles = repo.loadProfiles()
                val ranked = profiles.sortedBy { it.lastPingMs.takeIf { p -> p > 0 } ?: Long.MAX_VALUE }
                ranked.firstOrNull()?.link
            }
        }
        if (chosen.isNullOrBlank()) {
            lastDetail = "سروری برای اتصال نیست — اول یک سرور انتخاب کنید"
            broadcastStatus()
            stopTunnel()
            return
        }
        currentLink = chosen
        broadcastLog("وصل به سرور انتخاب‌شده…")
        if (!realTest(chosen)) {
            lastDetail = "سرور پاسخ نداد — سرور دیگری را امتحان کنید"
            broadcastStatus()
            stopTunnel()
            return
        }
        connectWinner(chosen)
    }

    /** Start Xray with [link] on TEST_PORT and fetch generate_204 through it. */
    private fun realTest(link: String): Boolean {
        val geoReady = GeoDownloader(applicationContext).isReady()
        val cfg = XrayConfig.build(link, TEST_PORT, geoReady) ?: return false
        if (!XrayController.start(cfg)) return false
        try {
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 3000) {
                try {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", TEST_PORT), 300) }
                    break
                } catch (_: Exception) {
                    Thread.sleep(200)
                }
            }
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TEST_PORT))
            val conn = URL("https://www.google.com/generate_204").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            return code == 204 || code == 200
        } catch (_: Exception) {
            return false
        } finally {
            XrayController.stop()
        }
    }

    private fun connectWinner(link: String) {
        val geoReady = GeoDownloader(applicationContext).isReady()
        val cfg = XrayConfig.build(link, SOCKS_PORT, geoReady) ?: run {
            lastDetail = "ساخت کانفیگ ناموفق بود"
            broadcastStatus(); stopTunnel(); return
        }
        val descriptor = try {
            Builder().apply {
                setSession("دماوند لایت")
                setMtu(1500)
                addAddress("10.8.0.2", 30)
                addRoute("0.0.0.0", 0)
                addDnsServer("8.8.8.8")
                addDnsServer("1.1.1.1")
                // The app itself (Xray core) must bypass the tunnel.
                runCatching { addDisallowedApplication(packageName) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
            }.establish()
        } catch (e: Exception) {
            AppLog.e("LiteVpnService", "establish failed", e)
            null
        }
        if (descriptor == null) {
            lastDetail = "ساخت اینترفیس VPN ناموفق بود"
            broadcastStatus(); stopTunnel(); return
        }
        tun = descriptor
        if (!XrayController.start(cfg)) {
            lastDetail = "شروع هسته ناموفق بود: ${XrayController.lastError ?: ""}"
            broadcastStatus(); stopTunnel(); return
        }
        if (!HevTunCore.start(this, descriptor, SOCKS_PORT, 1500, "10.8.0.2", null, false, pipeline = true)) {
            lastDetail = "شروع پل تونل ناموفق بود"
            broadcastStatus(); stopTunnel(); return
        }
        connected = true
        lastDetail = ""
        updateNotification()
        broadcastStatus()
        fetchLocation()
        startPoller()
    }

    private fun fetchLocation() {
        Thread({
            try {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
                val conn = URL("https://ipinfo.io/json").openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val j = JSONObject(body)
                val country = j.optString("country")
                val city = j.optString("city")
                countryCode = country
                location = listOf(city, country).filter { it.isNotBlank() }.joinToString("، ")
                broadcastStatus()
            } catch (_: Exception) {
            }
        }, "lite-loc").also { it.start() }
    }

    /**
     * Listener-driven speed loop with the screen-off freeze: while the
     * screen is on, tick every 1 s and repaint the notification; while the
     * screen is off, tick every 10 s for tunnel health-check only and do
     * not repaint the notification (it freezes). On screen-on the UI
     * re-reads the live values on resume.
     */
    private fun startPoller() {
        poller = Thread({
            var lastDown = 0L
            var lastUp = 0L
            var lastTime = System.currentTimeMillis()
            var tick = 0
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            while (running && connected) {
                val screenOn = pm.isInteractive
                try {
                    Thread.sleep(if (screenOn) 1000 else 10_000)
                } catch (_: InterruptedException) {
                    break
                }
                if (!running || !connected) break
                val (down, up) = XrayController.readTrafficDelta()
                val now = System.currentTimeMillis()
                val dt = max(1, now - lastTime) / 1000.0
                downSpeed = ((down - lastDown) / dt).toLong().coerceAtLeast(0)
                upSpeed = ((up - lastUp) / dt).toLong().coerceAtLeast(0)
                totalDown += max(0, down - lastDown)
                totalUp += max(0, up - lastUp)
                lastDown = down; lastUp = up; lastTime = now
                tick++
                if (tick % 10 == 0) {
                    pingMs = XrayController.measureDelay("https://www.google.com/generate_204")
                }
                // Screen off: freeze the notification (health-check only).
                if (pm.isInteractive) {
                    updateNotification()
                    broadcastStatus()
                }
            }
        }, "lite-poll").also { it.start() }
    }

    // ---- tunnel drop / kill switch ----

    private fun onTunnelDropped() {
        if (!running || intentionalStop) return
        connected = false
        lastDetail = "اتصال قطع شد — در حال اتصال دوباره…"
        broadcastStatus()
        if (killSwitchOn) {
            // Kill switch: keep the tun established so traffic entering it
            // is dropped (hev/Xray are stopped, nothing reads the tun),
            // and reconnect once. The tun is only closed on an intentional
            // disconnect (or when the service itself is destroyed).
            try { HevTunCore.stop() } catch (_: Exception) {}
            try { XrayController.stop() } catch (_: Exception) {}
            worker = Thread({
                try {
                    Thread.sleep(2000)
                    if (running && !intentionalStop) {
                        val link = currentLink
                        if (!link.isNullOrBlank()) {
                            connected = false
                            runConnect(link)
                        }
                    }
                } catch (_: Exception) {
                }
            }, "lite-reconnect").also { it.start() }
        } else {
            stopTunnel()
        }
    }

    // ---- stop ----

    private fun stopTunnel() {
        if (!running && tun == null) {
            stopSelf()
            return
        }
        running = false
        connected = false
        try { worker?.interrupt() } catch (_: Exception) {}
        try { poller?.interrupt() } catch (_: Exception) {}
        worker = null; poller = null
        try { HevTunCore.stop() } catch (_: Exception) {}
        try { XrayController.stop() } catch (_: Exception) {}
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastStatus()
        stopSelf()
    }

    override fun onDestroy() {
        try { HevTunCore.stop() } catch (_: Exception) {}
        try { XrayController.stop() } catch (_: Exception) {}
        try { tun?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
