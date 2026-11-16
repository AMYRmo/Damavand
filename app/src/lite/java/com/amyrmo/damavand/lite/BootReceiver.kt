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
 *
 * 3.2.16 (#50): the selection is resolved by StartIntent, so a
 * selected chain or Auto entry now reconnects as itself (BootReceiver
 * used to handle `chain:` but not `auto:`, so Auto + boot did
 * nothing). 3.2.16 (#51): when boot auto-start is OFF, the persisted
 * tile state is reset here — otherwise the tile still showed
 * "connected" after a reboot while no tunnel existed, and a tap
 * could never connect again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runBlocking {
            val store = SettingsStore(context)
            if (!store.autoStartBootNow()) {
                // No tunnel after this reboot: the tile/shortcuts must
                // not keep showing the pre-reboot "connected" state.
                try {
                    context.getSharedPreferences("lite_tile", Context.MODE_PRIVATE)
                        .edit().putBoolean("connected", false)
                        .putBoolean("connecting", false).apply()
                } catch (_: Exception) {
                }
                return@runBlocking
            }
            val selected = store.selectedProfileIdNow() ?: return@runBlocking
            val svc = StartIntent.build(context, selected) ?: return@runBlocking
            context.startForegroundService(svc)
        }
    }
}
