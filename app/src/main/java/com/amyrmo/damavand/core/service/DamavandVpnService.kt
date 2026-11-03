package com.amyrmo.damavand.core.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.amyrmo.damavand.core.LogBus
import com.amyrmo.damavand.core.session.SessionEnvironment
import com.amyrmo.damavand.core.session.SessionOrchestrator
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.core.session.TunRequest
import com.amyrmo.damavand.data.profiles.ProfileRepository
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.data.subscriptions.SubscriptionRepository
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.components.ConnectionStage
import com.amyrmo.damavand.ui.components.StageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The VPN service (architecture §2.1): three jobs only — own the Android
 * VpnService lifecycle and the foreground notification, hold the current
 * [SessionOrchestrator], and expose the state the UI observes (through
 * [SessionController]). All connection logic lives in the orchestrator.
 *
 * Start handoff: the intent carries only the session token; the target
 * profile travels in-process via [SessionController] (sealed kinds
 * end-to-end — no kind strings, no config blobs in intents, §2.4 rule 4).
 */
class DamavandVpnService : VpnService() {

    /** A start request handed over in-process by [SessionController]. */
    data class PendingStart(val token: String, val target: Profile)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var profiles: ProfileRepository? = null
    private var subscriptions: SubscriptionRepository? = null
    private var orchestrator: SessionOrchestrator? = null
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        VpnNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pending = SessionController.takePending(intent.getStringExtra(EXTRA_TOKEN))
                if (pending == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Foreground first: startForegroundService demands it promptly.
                val connecting = SessionState.Connecting(
                    sessionId = pending.token,
                    targetName = pending.target.name,
                    stage = StageInfo(ConnectionStage.Starting, hopLabel = pending.target.name),
                )
                startForegroundCompat(VpnNotifications.build(this, connecting))
                val orch = ensureOrchestrator()
                scope.launch {
                    profiles?.load()
                    subscriptions?.load()
                    orch.connect(pending.target)
                }
            }
            ACTION_STOP -> {
                orchestrator?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        orchestrator?.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        orchestrator?.disconnect()
        SessionController.attach(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureOrchestrator(): SessionOrchestrator {
        orchestrator?.let { return it }
        val profileRepo = ProfileRepository(applicationContext).also { profiles = it }
        val subscriptionRepo = SubscriptionRepository(applicationContext).also { subscriptions = it }
        val env = SessionEnvironment(
            appContext = applicationContext,
            vpnService = this,
            filesDir = filesDir,
            scope = scope,
            establishTun = { request -> establishXrayTun(request) },
        )
        val orch = SessionOrchestrator(
            env = env,
            profiles = profileRepo,
            settings = SettingsRepository(applicationContext),
            postNotification = { title, body -> postCoreNotification(title, body) },
        )
        orchestrator = orch
        SessionController.attach(orch)
        stateJob = scope.launch {
            orch.state.collect { state -> onStateChanged(state) }
        }
        return orch
    }

    private fun onStateChanged(state: SessionState) {
        when (state) {
            is SessionState.Connecting, is SessionState.Connected -> {
                VpnNotifications.build(this, state)?.let { notification ->
                    getSystemService(NotificationManager::class.java)
                        ?.notify(VpnNotifications.NOTIFICATION_ID, notification)
                }
            }
            is SessionState.Failed -> {
                LogBus.append("E/service: session failed: ${state.failure.message}")
                VpnNotifications.build(this, state)?.let { notification ->
                    getSystemService(NotificationManager::class.java)
                        ?.notify(VpnNotifications.NOTIFICATION_ID, notification)
                }
                // Terminal: detach the notification and let the service go.
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            SessionState.Idle -> Unit
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification?) {
        val built = notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                VpnNotifications.NOTIFICATION_ID,
                built,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(VpnNotifications.NOTIFICATION_ID, built)
        }
    }

    /** libbox-originated notifications (SingBoxPlatform sendNotification). */
    private fun postCoreNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, VpnNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(CORE_NOTIFICATION_ID, notification)
    }

    /**
     * The TUN for the standalone-Xray path (§2.2): v1's Option2 addressing
     * (router 10.1.0.1/30; the forwarder dials from 10.1.0.2), a default
     * route, and the plan's per-app selection. The core's own DNS stack
     * serves DNS; the interface DNS entry only anchors system queries.
     */
    private fun establishXrayTun(request: TunRequest): ParcelFileDescriptor? {
        if (prepare(this) != null) return null
        val builder = Builder()
            .setSession(request.sessionName.ifBlank { "Damavand" })
            .setMtu(request.mtu)
            .addAddress(TUN_ROUTER_ADDRESS, 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
        when (val perApp = request.perApp) {
            PerAppSelection.All -> runCatching { builder.addDisallowedApplication(packageName) }
            is PerAppSelection.Include -> {
                val allowed = perApp.packages.filter { it != packageName }
                if (allowed.isEmpty()) {
                    runCatching { builder.addDisallowedApplication(packageName) }
                } else {
                    allowed.forEach { pkg -> runCatching { builder.addAllowedApplication(pkg) } }
                }
            }
            is PerAppSelection.Exclude -> {
                (perApp.packages + packageName).forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        return runCatching { builder.establish() }.getOrNull()
    }

    companion object {
        const val ACTION_START = "com.amyrmo.damavand.action.START"
        const val ACTION_STOP = "com.amyrmo.damavand.action.STOP"
        const val EXTRA_TOKEN = "session_token"

        private const val CORE_NOTIFICATION_ID = 1001
        private const val TUN_ROUTER_ADDRESS = "10.1.0.1"
    }
}
