package com.amyrmo.damavand.lite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
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
        const val EXTRA_LINKS = "links"
        const val EXTRA_KILL_SWITCH = "kill_switch"
        /** 3.1.0: fastest-connect candidates, in preference order. */
        const val EXTRA_CANDIDATES = "candidates"
        // 3.2.0 (PattNG): balancer mode — the links are a policy group.
        const val EXTRA_BALANCER = "balancer"
        // 3.2.0: the display name for a balancer group (notification title).
        const val EXTRA_NAME = "name"
        /** 3.1.0: the URL a real tunnel test fetches. */
        const val EXTRA_TEST_URL = "test_url"

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
    private var currentLinks: List<String> = emptyList()
    private var currentCandidates: List<String> = emptyList()
    private var currentTestUrl: String = "https://www.google.com/generate_204"

    @Volatile private var location = ""
    @Volatile private var countryCode = ""
    @Volatile private var pingMs: Long = -1
    @Volatile private var downSpeed: Long = 0
    @Volatile private var upSpeed: Long = 0
    @Volatile private var totalDown: Long = 0
    @Volatile private var totalUp: Long = 0
    @Volatile private var lastDetail = ""
    @Volatile private var lastError: String? = null
    @Volatile private var connectedAt: Long = 0
    private var currentProfileName: String = ""
    // Displayed ping (3.2.0): the connected profile's tested ping, NOT the
    // live probe — the probe is a full HTTPS fetch through the tunnel
    // (page-load latency, ~10x the server ping) and is health-check only.
    @Volatile private var profilePingMs: Long = -1
    @Volatile private var probePingMs: Long = -1
    // Resolved per-connect ports (3.2.0): the configured SOCKS port, or a
    // random one when dynamic-port is on; the test port is always +100.
    @Volatile private var activeSocksPort: Int = SOCKS_PORT
    @Volatile private var activeTestPort: Int = TEST_PORT
    // Proxy-only mode (3.2.0): Xray + SOCKS, no tun, no hev bridge.
    @Volatile private var proxyOnlyMode: Boolean = false
    // Balancer mode (3.2.0, PattNG): the links are a policy group.
    @Volatile private var balancerMode: Boolean = false
    // Show live speeds in the notification (3.2.0 setting, cached per connect).
    @Volatile private var showSpeedNotif: Boolean = true
    // Display name passed by the caller (balancer group name).
    @Volatile private var displayName: String? = null
    // Live per-server usage (3.2.0): bytes already attributed to the exit
    // profile this session, so the Stats tab is never empty mid-session.
    @Volatile private var recordedUsage: Long = 0
    @Volatile private var usageProfileId: String? = null

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
                // A chain arrives as EXTRA_LINKS (entry first, exit last);
                // a single server arrives as EXTRA_LINK (2.0.2 path);
                // fastest-connect arrives as EXTRA_CANDIDATES (3.1.0).
                val links = intent.getStringArrayListExtra(EXTRA_LINKS)
                    ?: intent.getStringExtra(EXTRA_LINK)?.let { arrayListOf(it) }
                val candidates = intent.getStringArrayListExtra(EXTRA_CANDIDATES) ?: arrayListOf()
                val testUrl = intent.getStringExtra(EXTRA_TEST_URL)
                    ?.takeIf { it.isNotBlank() }
                    ?: "https://www.google.com/generate_204"
                // 3.2.0 (PattNG): balancer mode — a policy group.
                val balancer = intent.getBooleanExtra(EXTRA_BALANCER, false)
                displayName = intent.getStringExtra(EXTRA_NAME)
                if (!running) startTunnel(links ?: emptyList(), candidates, testUrl, balancer)
            }
            ACTION_STOP -> {
                intentionalStop = true
                stopTunnel()
            }
            ACTION_QUERY -> broadcastStatus()
        }
        return START_NOT_STICKY
    }

    // ---- notification (3.1.2: like the main Damavand app — the
    // server name as the title, live speeds as the text, the TOTAL
    // session volume as the short-critical chip, Xray as the
    // sub-text, a running chronometer, and a disconnect action) ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** Bytes/sec → "12.4 Mbps" / "1.2 Gbps" (Latin digits, like the main app). */
    private fun formatRateLatin(bytesPerSec: Long): String {
        val mbps = bytesPerSec * 8.0 / 1_000_000.0
        return if (mbps >= 1000) "%.1f Gbps".format(mbps / 1000)
        else "%.1f Mbps".format(mbps)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** The connected profile's tested ping (the exit hop for a chain). */
    private fun resolveProfilePing(links: List<String>): Long {
        return try {
            val repo = ProfileRepository(applicationContext)
            val profiles = repo.loadProfiles()
            val exitLink = links.lastOrNull() ?: return -1
            profiles.firstOrNull { it.link == exitLink }?.lastPingMs?.takeIf { it > 0 } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /** A balancer's displayed ping: the best tested ping of its members. */
    private fun resolveBalancerPing(links: List<String>): Long {
        return try {
            val repo = ProfileRepository(applicationContext)
            val profiles = repo.loadProfiles()
            links.mapNotNull { link ->
                profiles.firstOrNull { it.link == link }?.lastPingMs?.takeIf { it > 0 }
            }.minOrNull() ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /** The connected server's display name (the chain name for a chain). */
    private fun resolveProfileName(links: List<String>): String {
        return try {
            val repo = ProfileRepository(applicationContext)
            val profiles = repo.loadProfiles()
            if (links.size > 1) {
                val ids = links.mapNotNull { link -> profiles.firstOrNull { it.link == link }?.id }
                val chain = repo.loadChains().firstOrNull { it.memberIds == ids }
                if (chain != null) return chain.name
            }
            profiles.firstOrNull { it.link == links.firstOrNull() }?.name ?: getString(R.string.app_name)
        } catch (_: Exception) {
            getString(R.string.app_name)
        }
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, LiteVpnService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lite)
            .setColor(0xFFE5322D.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            // 3.2.0 (live notification, like main Damavand): promoted
            // ongoing + the total-volume chip. Needs core-ktx 1.17.0
            // (compileSdk 36) — both calls are absent in 1.13.1.
            .setRequestPromotedOngoing(true)
            .setContentIntent(openAppIntent())
        if (!connected) {
            return b
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_connecting))
                .setShowWhen(false)
                .addAction(0, getString(R.string.notif_disconnect), stopPending)
                .build()
        }
        val speeds = "↓ ${formatRateLatin(downSpeed)} · ↑ ${formatRateLatin(upSpeed)}"
        val totals = getString(R.string.notif_totals, formatBytes(totalDown), formatBytes(totalUp))
        // 3.2.0: with speeds off, the notification shows the totals only.
        val contentText = if (showSpeedNotif) speeds else totals
        val bigText = if (showSpeedNotif) "$speeds\n$totals" else totals
        return b
            .setContentTitle(currentProfileName.ifBlank { getString(R.string.app_name) })
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSubText("Xray")
            // 3.2.0: the total session volume as the short critical chip
            // (status-bar chip on Android 15+ with promoted ongoing).
            .setShortCriticalText(formatBytes(totalDown + totalUp))
            .setShowWhen(connectedAt > 0)
            .setWhen(if (connectedAt > 0) connectedAt else System.currentTimeMillis())
            .setUsesChronometer(connectedAt > 0)
            .addAction(0, getString(R.string.notif_disconnect), stopPending)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification())
    }

    // ---- broadcasts ----

    private fun broadcastStatus() {
        val state = when {
            !running -> STATE_DISCONNECTED
            connected -> STATE_CONNECTED
            else -> STATE_CONNECTING
        }
        refreshTile(state)
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

    // 3.2.6: the QS tile reads the last state from prefs (it cannot
    // start the service just to ask), and is woken on every change.
    @Volatile private var lastTileState: String? = null

    private fun refreshTile(state: String) {
        if (state == lastTileState) return
        lastTileState = state
        try {
            getSharedPreferences("lite_tile", MODE_PRIVATE).edit()
                .putBoolean("connected", state == STATE_CONNECTED)
                .putBoolean("connecting", state == STATE_CONNECTING)
                .apply()
        } catch (_: Exception) {
        }
        try {
            TileService.requestListeningState(
                this, ComponentName(this, LiteTileService::class.java)
            )
        } catch (_: Exception) {
        }
    }

    private fun broadcastLog(line: String) {
        AppLog.addLine(line)
        sendBroadcast(Intent(ACTION_LOG).putExtra("line", line).setPackage(packageName))
    }

    // ---- connect ----

    private fun startTunnel(
        links: List<String>,
        candidates: List<String> = emptyList(),
        testUrl: String = "https://www.google.com/generate_204",
        balancer: Boolean = false,
    ) {
        running = true
        connected = false
        intentionalStop = false
        balancerMode = balancer
        location = ""
        countryCode = ""
        pingMs = -1
        downSpeed = 0; upSpeed = 0; totalDown = 0; totalUp = 0
        lastDetail = ""
        lastError = null
        connectedAt = 0
        currentProfileName = ""
        profilePingMs = -1
        probePingMs = -1
        pingMs = -1
        currentLinks = links
        currentCandidates = candidates
        currentTestUrl = testUrl
        recordedUsage = 0
        usageProfileId = null
        startForeground(NOTIF_ID, notification())
        broadcastStatus()

        worker = Thread({
            try {
                runConnect(links, candidates, testUrl)
            } catch (e: Exception) {
                lastDetail = getString(R.string.conn_error_generic, e.message ?: "")
                lastError = e.message
                broadcastLog("ERROR: ${e.message}")
                broadcastStatus()
                stopTunnel()
            }
        }, "lite-connect").also { it.start() }
    }

    /**
     * Connect to [links] if given (one link, or a chain in entry-first
     * order), to the first candidate that passes a real tunnel test
     * when [candidates] are given (3.1.0 fastest-connect: a stale
     * fastest pick no longer kills the connect — the next candidate
     * is tried), otherwise to the fastest-tested profile from the
     * repository. A single real tunnel test validates the chosen
     * profile(s) before the tun is established.
     */
    private fun runConnect(
        links: List<String>,
        candidates: List<String> = emptyList(),
        testUrl: String = "https://www.google.com/generate_204",
    ) {
        // 3.2.0: resolve the per-connect ports before any test runs, so
        // the test port and the connect port always belong together.
        val settings0 = loadXraySettings()
        val (dynamicPort, proxyOnly) = try {
            kotlinx.coroutines.runBlocking {
                val s = SettingsStore(applicationContext)
                s.socksDynamicPortNow() to s.proxyOnlyNow()
            }
        } catch (_: Exception) {
            false to false
        }
        // 3.2.0: show live speeds in the notification only when enabled.
        showSpeedNotif = try {
            kotlinx.coroutines.runBlocking {
                SettingsStore(applicationContext).showSpeedNotifNow()
            }
        } catch (_: Exception) {
            true
        }
        activeSocksPort = if (dynamicPort) (20000..39900).random() else settings0.socksPort
        activeTestPort = (activeSocksPort + 100).coerceAtMost(65535)
        proxyOnlyMode = proxyOnly
        val repo = ProfileRepository(applicationContext)
        val chosen: List<String> = when {
            links.isNotEmpty() -> links
            candidates.isNotEmpty() -> {
                // Fastest-connect: try candidates in order until one
                // passes a real tunnel test, with live progress on
                // Home (3.2.0: "تست سرور 1 از 5…").
                var winner: List<String>? = null
                for ((index, candidate) in candidates.withIndex()) {
                    lastDetail = getString(
                        R.string.fastest_testing, index + 1, candidates.size,
                    )
                    broadcastStatus()
                    broadcastLog(lastDetail)
                    if (realTest(listOf(candidate), testUrl)) {
                        winner = listOf(candidate)
                        break
                    }
                }
                // One retry of the best candidate: a single transient
                // failure (flaky test URL) must not kill the connect.
                if (winner == null && candidates.isNotEmpty()) {
                    lastDetail = getString(R.string.fastest_retry)
                    broadcastStatus()
                    broadcastLog(lastDetail)
                    if (realTest(listOf(candidates[0]), testUrl)) {
                        winner = listOf(candidates[0])
                    }
                }
                winner ?: run {
                    lastDetail = getString(R.string.conn_no_server_responded)
                    broadcastStatus()
                    stopTunnel()
                    return
                }
            }
            else -> {
                val profiles = repo.loadProfiles()
                val ranked = profiles.sortedBy { it.lastPingMs.takeIf { p -> p > 0 } ?: Long.MAX_VALUE }
                ranked.firstOrNull()?.link?.let { listOf(it) } ?: emptyList()
            }
        }
        if (chosen.isEmpty()) {
            lastDetail = getString(R.string.conn_no_server)
            broadcastStatus()
            stopTunnel()
            return
        }
        currentLinks = chosen
        broadcastLog(getString(R.string.log_connecting_selected))
        if (links.isNotEmpty() && !realTest(chosen, testUrl)) {
            lastDetail = getString(R.string.conn_server_no_response)
            broadcastStatus()
            stopTunnel()
            return
        }
        connectWinner(chosen)
    }

    /** Start Xray with [links] on the resolved test port and fetch the test URL through it. */
    private fun realTest(links: List<String>, testUrl: String): Boolean {
        val geoReady = GeoDownloader(applicationContext).isReady()
        val settings = loadXraySettings()
        val cfg = XrayConfig.buildLinks(links, activeTestPort, geoReady, settings) ?: return false
        if (!XrayController.start(cfg)) return false
        try {
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 2000) {
                try {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", activeTestPort), 300) }
                    break
                } catch (_: Exception) {
                    Thread.sleep(200)
                }
            }
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", activeTestPort))
            val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
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

    /** Settings snapshot for one config build (defaults on any failure). */
    private fun loadXraySettings(): XrayConfig.XraySettings {
        return try {
            val s = kotlinx.coroutines.runBlocking {
                SettingsStore(applicationContext).xraySettingsNow(RoutingStore(applicationContext))
            }
            // 3.2.0: the core error log lives in the app files dir.
            s.copy(
                errorLogPath = java.io.File(
                    applicationContext.filesDir, "xray-error.log"
                ).absolutePath
            )
        } catch (_: Exception) {
            XrayConfig.XraySettings()
        }
    }

    private fun connectWinner(links: List<String>) {
        val geoReady = GeoDownloader(applicationContext).isReady()
        val settings = loadXraySettings()
        // 3.2.0 (PattNG): a balancer group builds one outbound per
        // profile + a leastPing balancer; anything else is a chain or a
        // single profile.
        val cfg = if (balancerMode) {
            XrayConfig.buildBalancer(links, activeSocksPort, geoReady, settings)
        } else {
            XrayConfig.buildLinks(links, activeSocksPort, geoReady, settings)
        } ?: run {
            lastDetail = getString(R.string.conn_config_failed)
            broadcastStatus(); stopTunnel(); return
        }
        val perApp = try {
            RoutingStore(applicationContext).loadPerApp()
        } catch (_: Exception) {
            PerAppConfig()
        }
        val dnsServers = settings.dnsServers.take(2).ifEmpty { listOf("8.8.8.8", "1.1.1.1") }
        val localDns = try {
            kotlinx.coroutines.runBlocking { SettingsStore(applicationContext).localDnsNow() }
        } catch (_: Exception) {
            true
        }
        // Proxy-only mode (3.2.0): no tun at all — Xray + SOCKS only,
        // like RakhshApp. The tun + hev bridge are skipped entirely.
        val descriptor = if (proxyOnlyMode) {
            null
        } else try {
            Builder().apply {
                setSession(getString(R.string.app_name))
                setMtu(settings.mtu)
                addAddress(settings.ifaceAddr, 30)
                addRoute("0.0.0.0", 0)
                // Local DNS off: the tun uses the system DNS.
                if (localDns) dnsServers.forEach { addDnsServer(it) }
                // The app itself (Xray core) must bypass the tunnel.
                runCatching { addDisallowedApplication(packageName) }
                // Per-app proxy (3.1.0, PattNG): bypass mode excludes
                // the listed apps; allow-only mode includes only them.
                when (perApp.mode) {
                    "allow_only" -> {
                        perApp.packages.forEach { pkg ->
                            runCatching { addAllowedApplication(pkg) }
                        }
                    }
                    else -> {
                        perApp.packages.forEach { pkg ->
                            runCatching { addDisallowedApplication(pkg) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
            }.establish()
        } catch (e: Exception) {
            AppLog.e("LiteVpnService", "establish failed", e)
            null
        }
        if (descriptor == null && !proxyOnlyMode) {
            lastDetail = getString(R.string.conn_vpn_iface_failed)
            broadcastStatus(); stopTunnel(); return
        }
        if (!proxyOnlyMode) {
            tun = descriptor
            if (!XrayController.start(cfg)) {
                lastDetail = getString(R.string.conn_core_failed, XrayController.lastError ?: "")
                broadcastStatus(); stopTunnel(); return
            }
            // 3.2.0: hev log level + rw timeout from settings.
            val (hevLog, hevTimeout) = try {
                kotlinx.coroutines.runBlocking {
                    val s = SettingsStore(applicationContext)
                    s.hevLogLevelNow() to s.hevRwTimeoutNow()
                }
            } catch (_: Exception) {
                "warning" to "0"
            }
            // descriptor is non-null here: the null check above returns
            // when !proxyOnlyMode; in proxy-only mode the fd is unused.
            if (!HevTunCore.start(
                    this, descriptor!!, activeSocksPort, settings.mtu,
                    settings.ifaceAddr, null, false, pipeline = true,
                    logLevel = hevLog, rwTimeout = hevTimeout
                )
            ) {
                lastDetail = getString(R.string.conn_tunnel_bridge_failed)
                broadcastStatus(); stopTunnel(); return
            }
        } else {
            if (!XrayController.start(cfg)) {
                lastDetail = getString(R.string.conn_core_failed, XrayController.lastError ?: "")
                broadcastStatus(); stopTunnel(); return
            }
        }
        connected = true
        connectedAt = System.currentTimeMillis()
        currentProfileName = displayName?.takeIf { it.isNotBlank() }
            ?: resolveProfileName(links)
        // A balancer's displayed ping is the best member ping (3.2.0).
        profilePingMs = if (balancerMode) {
            resolveBalancerPing(links)
        } else {
            resolveProfilePing(links)
        }
        // Live per-server usage (3.2.0): resolve the exit profile once.
        usageProfileId = try {
            val exitLink = links.lastOrNull()
            ProfileRepository(applicationContext).loadProfiles()
                .firstOrNull { it.link == exitLink }?.id
        } catch (_: Exception) {
            null
        }
        recordedUsage = 0
        pingMs = if (profilePingMs > 0) profilePingMs else -1
        lastDetail = ""
        updateNotification()
        broadcastStatus()
        fetchLocation()
        startPoller()
    }

    private fun fetchLocation() {
        Thread({
            try {
                val apiUrl = try {
                    kotlinx.coroutines.runBlocking {
                        SettingsStore(applicationContext).ipApiUrlNow()
                    }
                } catch (_: Exception) {
                    "https://ipinfo.io/json"
                }
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", activeSocksPort))
                val conn = URL(apiUrl).openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val j = JSONObject(body)
                val country = j.optString("country")
                val city = j.optString("city")
                countryCode = country
                location = listOf(city, country).filter { it.isNotBlank() }
                    .joinToString(if (LiteFormat.persianDigits) "، " else ", ")
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
            var probeFailStreak = 0
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            while (running && connected) {
                val screenOn = pm.isInteractive
                try {
                    Thread.sleep(if (screenOn) 1000 else 10_000)
                } catch (_: InterruptedException) {
                    break
                }
                if (!running || !connected) break
                // 3.1.1: a tick must never kill the poller silently —
                // a dead poller freezes the Stats tab at 0 while the
                // pill still says "connected" (3.1.0 on-device).
                try {
                    val (down, up) = XrayController.readTrafficDelta()
                    val now = System.currentTimeMillis()
                    val dt = max(1, now - lastTime) / 1000.0
                    downSpeed = ((down - lastDown) / dt).toLong().coerceAtLeast(0)
                    upSpeed = ((up - lastUp) / dt).toLong().coerceAtLeast(0)
                    totalDown += max(0, down - lastDown)
                    totalUp += max(0, up - lastUp)
                    lastDown = down; lastUp = up; lastTime = now
                    tick++
                    // Live per-server usage (3.2.0): attribute the session
                    // volume to the exit profile every 10 s, so the Stats
                    // tab shows usage mid-session — not only on disconnect.
                    if (tick % 10 == 0 && usageProfileId != null) {
                        val sessionBytes = totalDown + totalUp
                        val delta = sessionBytes - recordedUsage
                        if (delta > 0) {
                            try {
                                RoutingStore(applicationContext)
                                    .recordUsage(usageProfileId!!, delta)
                                recordedUsage = sessionBytes
                            } catch (_: Exception) {
                            }
                        }
                    }
                    // Stats diagnostics (3.2.0): the raw Xray counters,
                    // visible in the Logs screen, so a zero-stats report
                    // on-device can be traced to the exact counters.
                    if (tick % 30 == 0) {
                        AppLog.e("LiteVpnService", "raw stats: ${XrayController.rawStats()}")
                    }
                    if (tick % 10 == 0) {
                        // Health-check only (3.2.0): the probe is a full
                        // HTTPS fetch through the tunnel, so its ms must
                        // never become the displayed ping — it only fills
                        // in when the profile has no tested ping yet.
                        val probe = XrayController.measureDelay(currentTestUrl)
                        if (probe >= 0) {
                            probePingMs = probe
                            if (profilePingMs <= 0) pingMs = probe
                            probeFailStreak = 0
                        } else {
                            probeFailStreak++
                            AppLog.e(
                                "LiteVpnService",
                                "tunnel probe failed ($probeFailStreak/3)"
                            )
                            if (probeFailStreak >= 3) {
                                // Tunnel up but carrying nothing: drop
                                // it (reconnect once with kill-switch,
                                // else stop) instead of showing a fake
                                // "connected" with zero stats.
                                AppLog.e(
                                    "LiteVpnService",
                                    "probe failing 3x — dropping tunnel"
                                )
                                onTunnelDropped()
                                break
                            }
                        }
                    }
                    // Screen off: freeze the notification (health-check only).
                    if (pm.isInteractive) {
                        updateNotification()
                        broadcastStatus()
                    }
                } catch (e: Exception) {
                    AppLog.e("LiteVpnService", "poller tick failed", e)
                }
            }
        }, "lite-poll").also { it.start() }
    }

    // ---- tunnel drop / kill switch ----

    private fun onTunnelDropped() {
        if (!running || intentionalStop) return
        connected = false
        lastDetail = getString(R.string.conn_dropped_reconnecting)
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
                        val links = currentLinks
                        if (links.isNotEmpty()) {
                            connected = false
                            runConnect(links, currentCandidates, currentTestUrl)
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
        // Per-server usage (3.1.0): attribute the session volume to
        // the exit profile (the last link) before tearing down.
        // 3.2.0: only the remainder not yet recorded live (see poller).
        if (connected && totalDown + totalUp > recordedUsage && currentLinks.isNotEmpty()) {
            try {
                val exitLink = currentLinks.last()
                val profileId = usageProfileId
                    ?: ProfileRepository(applicationContext)
                        .loadProfiles().firstOrNull { it.link == exitLink }?.id
                if (profileId != null) {
                    RoutingStore(applicationContext)
                        .recordUsage(profileId, totalDown + totalUp - recordedUsage)
                }
            } catch (_: Exception) {
            }
        }
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
