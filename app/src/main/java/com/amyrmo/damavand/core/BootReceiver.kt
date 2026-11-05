package com.amyrmo.damavand.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.amyrmo.damavand.core.AppLog as Log
import androidx.core.content.ContextCompat
import com.amyrmo.damavand.DamavandApp
import com.amyrmo.damavand.domain.model.RunMode

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as DamavandApp).container
        val settings = container.settingsRepository.settings.value
        SubscriptionUpdateScheduler.sync(context, settings, container.configRepository.subscriptions.value)

        if (!settings.autoConnectOnBoot) return
        val active = container.configRepository.activeProfile() ?: return

        if (active.isIkev2) return

        if (settings.runMode != RunMode.ProxyOnly && VpnService.prepare(context) != null) {
            Log.i(TAG, "auto-connect skipped: VPN permission not granted")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DamavandVpnService::class.java)
                    .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_START_ACTIVE),
            )
        }.onFailure { Log.w(TAG, "auto-connect at boot failed to start the service", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
