package com.amyrmo.damavand.lite.ui.shortcut

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.amyrmo.damavand.lite.LiteVpnService
import com.amyrmo.damavand.lite.MainActivity
import com.amyrmo.damavand.lite.ProfileRepository
import com.amyrmo.damavand.lite.SelectedEntry
import com.amyrmo.damavand.lite.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Tasker + shortcuts (3.2.11, PattNG ui/shortcut): the Tasker plugin
 * (TaskerActivity + TaskerReceiver) and the start/stop/switch shortcut
 * activities all go through here.
 *
 * Lite adaptations:
 * - PattNG's LauncherManager starts the selected server from MMKV;
 *   Lite starts it the way the quick-settings tile does
 *   (LiteTileService.connect): the selected profile, else the five
 *   fastest tested ones, else the first profile.
 * - No :daemon process: Lite's service runs in the main process, so
 *   the shortcut activities run there too (PattNG runs them in :daemon).
 * - Aether profiles get EXTRA_AETHER, the way the ViewModel starts them.
 * - Root mode skips the VPN-permission check, the way MainActivity does.
 * - Whether the service runs is read from the tile state the service
 *   persists ("lite_tile"), the same state the tile itself shows.
 */
object TaskerLauncher {

    const val TASKER_EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val TASKER_EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val TASKER_EXTRA_BUNDLE_SWITCH = "tasker_extra_bundle_switch"
    const val TASKER_EXTRA_BUNDLE_GUID = "tasker_extra_bundle_guid"
    const val TASKER_DEFAULT_GUID = "Default"

    fun isRunning(context: Context): Boolean {
        val prefs = context.getSharedPreferences("lite_tile", Context.MODE_PRIVATE)
        return prefs.getBoolean("connected", false) || prefs.getBoolean("connecting", false)
    }

    /** Starts the profile [guid] names, or the selected one for a null or default guid; false when nothing starts. */
    fun start(context: Context, guid: String?): Boolean {
        val store = SettingsStore(context)
        val repo = ProfileRepository(context)
        val (rootMode, testUrl, killSwitch) = runBlocking {
            Triple(store.rootModeNow(), store.testUrlNow(), store.killSwitchNow())
        }
        // No VPN permission yet → the app owns that flow (root mode needs none).
        if (!rootMode && VpnService.prepare(context) != null) {
            openApp(context)
            return false
        }
        val profiles = repo.loadProfiles()
        val intent = Intent(context, LiteVpnService::class.java).apply {
            action = LiteVpnService.ACTION_START
            if (guid != null && guid != TASKER_DEFAULT_GUID) {
                val profile = profiles.firstOrNull { it.id == guid } ?: return false
                putExtra(LiteVpnService.EXTRA_LINK, profile.link)
                if (profile.isAether) putExtra(LiteVpnService.EXTRA_AETHER, true)
            } else {
                val selectedId = runBlocking { store.selectedProfileIdNow() }
                val selected = profiles.firstOrNull { it.id == selectedId }
                if (SelectedEntry.isGroup(selectedId)) {
                    // A selected chain or Auto entry starts as in the app.
                    val extras = SelectedEntry.startExtras(context, selectedId)
                        ?: run { openApp(context); return false }
                    putExtras(extras)
                } else if (selected != null) {
                    putExtra(LiteVpnService.EXTRA_LINK, selected.link)
                    if (selected.isAether) putExtra(LiteVpnService.EXTRA_AETHER, true)
                } else {
                    val candidates = profiles.filter { it.lastPingMs > 0 }
                        .sortedBy { it.lastPingMs }.take(5).map { it.link }
                    if (candidates.isNotEmpty()) {
                        putStringArrayListExtra(LiteVpnService.EXTRA_CANDIDATES, ArrayList(candidates))
                    } else {
                        profiles.firstOrNull()?.let {
                            putExtra(LiteVpnService.EXTRA_LINK, it.link)
                            if (it.isAether) putExtra(LiteVpnService.EXTRA_AETHER, true)
                        } ?: run { openApp(context); return false }
                    }
                }
            }
            putExtra(LiteVpnService.EXTRA_TEST_URL, testUrl)
            putExtra(LiteVpnService.EXTRA_KILL_SWITCH, killSwitch)
        }
        return try {
            context.startForegroundService(intent)
            true
        } catch (_: Exception) {
            openApp(context)
            false
        }
    }

    fun stop(context: Context) {
        try {
            context.startService(
                Intent(context, LiteVpnService::class.java).setAction(LiteVpnService.ACTION_STOP)
            )
        } catch (_: Exception) {
        }
    }

    private fun openApp(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("lite_action", "connect")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
