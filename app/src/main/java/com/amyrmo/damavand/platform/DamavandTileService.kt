package com.amyrmo.damavand.platform

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.amyrmo.damavand.MainActivity
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.service.SessionController
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.profiles.ProfileRepository
import com.amyrmo.damavand.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile (§5.2): tap toggles the last-used profile.
 *
 * Consent caveat: a tile cannot grant VPN consent — if
 * [VpnService.prepare] still returns an intent, tapping the tile opens
 * the app instead, where the in-app flow asks. A tile click can only
 * *start* a session when consent was already granted in-app.
 */
class DamavandTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (SessionController.isActive()) {
            SessionController.disconnect(applicationContext)
            scope.launch {
                delay(600)
                refreshTile()
            }
            return
        }
        scope.launch {
            val settings = SettingsRepository(applicationContext)
            val profileId = settings.currentUi().lastProfileId ?: return@launch openApp()
            val profiles = ProfileRepository(applicationContext)
            profiles.load()
            val profile = profiles.profile(profileId) ?: return@launch openApp()
            if (VpnService.prepare(applicationContext) != null) {
                openApp()
                return@launch
            }
            SessionController.connect(applicationContext, profile)
            delay(600)
            refreshTile()
        }
    }

    private fun openApp() {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_tile)
        tile.label = getString(R.string.app_name)
        val state = SessionController.state().value
        tile.state = when (state) {
            is SessionState.Connected, is SessionState.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (state) {
                is SessionState.Connected -> state.targetName
                is SessionState.Connecting -> getString(R.string.home_status_connecting)
                else -> getString(R.string.home_status_idle)
            }
        }
        tile.updateTile()
    }
}
