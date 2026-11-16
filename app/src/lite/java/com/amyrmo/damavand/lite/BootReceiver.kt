package com.amyrmo.damavand.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Boot auto-start (3.2.0, PattNG): after a reboot, reconnect the last
 * selected profile/chain — only when the user enabled it in Settings.
 * The VPN permission persists across reboots once granted; if it was
 * never granted the service fails gracefully (no tun, no crash).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Nothing runs right after a boot: drop the tile state persisted
        // before the reboot, or the tile and shortcuts stay "connected".
        try {
            context.getSharedPreferences("lite_tile", Context.MODE_PRIVATE).edit()
                .putBoolean("connected", false)
                .putBoolean("connecting", false)
                .apply()
        } catch (_: Exception) {
        }
        runBlocking {
            val store = SettingsStore(context)
            if (!store.autoStartBootNow()) return@runBlocking
            val repo = ProfileRepository(context)
            val selected = store.selectedProfileIdNow() ?: return@runBlocking
            val links: List<String> = if (selected.startsWith("chain:")) {
                val chain = repo.loadChains()
                    .firstOrNull { "chain:${it.id}" == selected } ?: return@runBlocking
                chain.memberIds.mapNotNull { id ->
                    repo.loadProfiles().firstOrNull { it.id == id }?.link
                }
            } else {
                val profile = repo.loadProfiles()
                    .firstOrNull { it.id == selected } ?: return@runBlocking
                listOf(profile.link)
            }
            if (links.isEmpty()) return@runBlocking
            val svc = Intent(context, LiteVpnService::class.java).apply {
                action = LiteVpnService.ACTION_START
                if (links.size == 1) {
                    putExtra(LiteVpnService.EXTRA_LINK, links[0])
                } else {
                    putStringArrayListExtra(LiteVpnService.EXTRA_LINKS, ArrayList(links))
                }
                putExtra(LiteVpnService.EXTRA_TEST_URL, store.testUrlNow())
                putExtra(LiteVpnService.EXTRA_KILL_SWITCH, store.killSwitchNow())
            }
            context.startForegroundService(svc)
        }
    }
}
