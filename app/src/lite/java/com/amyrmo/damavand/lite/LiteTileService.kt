package com.amyrmo.damavand.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.runBlocking

/**
 * Quick settings tile (3.2.6) — the control-center button, like
 * Damavand Test / PattNG / Windscribe have.
 *
 * Tap to toggle: connected → stop; disconnected → connect to the
 * selected profile (or the fastest tested ones, like the ring's
 * connect). When the VPN permission was never granted, the app
 * opens instead — it owns that flow. The tile state follows the
 * service: live broadcasts while the panel is open, and the last
 * state persisted by the service (see LiteVpnService.refreshTile)
 * when the panel opens later.
 */
class LiteTileService : TileService() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LiteVpnService.ACTION_STATUS) {
                updateTile(intent.getStringExtra("state"))
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val filter = IntentFilter(LiteVpnService.ACTION_STATUS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
        }
        // Last known state — the service persists it on every change,
        // so the tile is right even before the first broadcast.
        val prefs = getSharedPreferences("lite_tile", MODE_PRIVATE)
        val state = when {
            prefs.getBoolean("connected", false) -> LiteVpnService.STATE_CONNECTED
            prefs.getBoolean("connecting", false) -> LiteVpnService.STATE_CONNECTING
            else -> LiteVpnService.STATE_DISCONNECTED
        }
        updateTile(state)
    }

    override fun onStopListening() {
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (qsTile?.state == Tile.STATE_ACTIVE) {
            // Connected (or connecting) → stop the tunnel.
            try {
                startService(
                    Intent(this, LiteVpnService::class.java)
                        .setAction(LiteVpnService.ACTION_STOP)
                )
            } catch (_: Exception) {
            }
        } else {
            connect()
        }
    }

    private fun updateTile(state: String?) {
        try {
            val tile = qsTile ?: return
            tile.icon = Icon.createWithResource(this, R.drawable.ic_lite)
            tile.label = getString(R.string.app_name)
            tile.state = when (state) {
                LiteVpnService.STATE_CONNECTED -> Tile.STATE_ACTIVE
                LiteVpnService.STATE_CONNECTING -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            tile.updateTile()
        } catch (_: Exception) {
        }
    }

    private fun connect() {
        // No VPN permission yet → the app owns that flow.
        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }
        try {
            val store = SettingsStore(applicationContext)
            val repo = ProfileRepository(applicationContext)
            val profiles = repo.loadProfiles()
            val (selectedId, testUrl, killSwitch) = runBlocking {
                Triple(
                    store.selectedProfileIdNow(),
                    store.testUrlNow(),
                    store.killSwitchNow()
                )
            }
            val intent = Intent(this, LiteVpnService::class.java).apply {
                action = LiteVpnService.ACTION_START
                val selected = profiles.firstOrNull { it.id == selectedId }
                if (selected != null) {
                    putExtra(LiteVpnService.EXTRA_LINK, selected.link)
                } else {
                    val candidates = profiles.filter { it.lastPingMs > 0 }
                        .sortedBy { it.lastPingMs }.take(5).map { it.link }
                    if (candidates.isNotEmpty()) {
                        putStringArrayListExtra(
                            LiteVpnService.EXTRA_CANDIDATES, ArrayList(candidates)
                        )
                    } else {
                        profiles.firstOrNull()?.let {
                            putExtra(LiteVpnService.EXTRA_LINK, it.link)
                        } ?: run { openApp(); return }
                    }
                }
                putExtra(LiteVpnService.EXTRA_TEST_URL, testUrl)
                putExtra(LiteVpnService.EXTRA_KILL_SWITCH, killSwitch)
            }
            // QS tile taps are a user interaction, so starting the
            // foreground service from here is allowed; if it ever
            // fails, fall back to opening the app.
            startForegroundService(intent)
        } catch (_: Exception) {
            openApp()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("lite_action", "connect")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            startActivityAndCollapse(intent)
        } catch (_: Exception) {
        }
    }
}
