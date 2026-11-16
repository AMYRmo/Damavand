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
        runBlocking {
            val store = SettingsStore(context)
            if (!store.autoStartBootNow()) return@runBlocking
            // A selected Auto entry starts its group (the lookups below
            // only know chains and profiles, so boot did nothing).
            val selectedEntry = store.selectedProfileIdNow()
            if (AutoSelectIds.isAuto(selectedEntry)) {
                val extras = SelectedEntry.startExtras(context, selectedEntry) ?: return@runBlocking
                context.startForegroundService(
                    Intent(context, LiteVpnService::class.java)
                        .setAction(LiteVpnService.ACTION_START)
                        .putExtras(extras)
                        .putExtra(LiteVpnService.EXTRA_TEST_URL, store.testUrlNow())
                        .putExtra(LiteVpnService.EXTRA_KILL_SWITCH, store.killSwitchNow())
                )
                return@runBlocking
            }
            val repo = ProfileRepository(context)
            val selected = store.selectedProfileIdNow() ?: return@runBlocking
            var aether = false
            val links: List<String> = if (selected.startsWith("chain:")) {
                val chain = repo.loadChains()
                    .firstOrNull { "chain:${it.id}" == selected } ?: return@runBlocking
                chain.memberIds.mapNotNull { id ->
                    repo.loadProfiles().firstOrNull { it.id == id }?.link
                }
            } else {
                val profile = repo.loadProfiles()
                    .firstOrNull { it.id == selected } ?: return@runBlocking
                aether = profile.isAether
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
                // An Aether profile's link is not an Xray link: without
                // this flag the service tried XrayConfig on aether://<uuid>
                // and every boot auto-start failed with "config failed".
                if (aether) putExtra(LiteVpnService.EXTRA_AETHER, true)
            }
            context.startForegroundService(svc)
        }
    }
}
