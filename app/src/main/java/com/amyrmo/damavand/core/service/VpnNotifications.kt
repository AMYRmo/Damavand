package com.amyrmo.damavand.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.amyrmo.damavand.MainActivity
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.ui.components.ConnectionStage

/**
 * The foreground-service notification (v1's channel/id conventions): it
 * mirrors the session's staged state (§5.5) — the same stage text the
 * in-app indicator shows — and carries a disconnect action. A dedicated
 * app icon replaces the framework placeholder in the UI phase.
 */
object VpnNotifications {

    const val CHANNEL_ID = "damavand_vpn"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Damavand connection status"
                setShowBadge(false)
            },
        )
    }

    /** Notification for a session state; null when no notification should show (Idle). */
    fun build(context: Context, state: SessionState): Notification? {
        val (title, text) = when (state) {
            SessionState.Idle -> return null
            is SessionState.Connecting -> {
                val stageText = when (state.stage.stage) {
                    ConnectionStage.Starting -> "Starting"
                    ConnectionStage.Authenticating -> "Authenticating"
                    ConnectionStage.Verifying -> "Verifying"
                    ConnectionStage.Retrying -> "Retrying (attempt ${state.stage.attempt})"
                    ConnectionStage.Connected -> "Connected"
                    ConnectionStage.Failed -> "Failed"
                }
                val detail = listOfNotNull(state.stage.hopLabel, state.stage.subStatus)
                    .joinToString(" · ")
                "Damavand — $stageText" to detail.ifBlank { state.targetName }
            }
            is SessionState.Connected -> "Damavand — Connected" to state.targetName
            is SessionState.Failed -> "Damavand — Failed" to state.failure.reason
        }
        val openIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            REQ_STOP,
            Intent(context, DamavandVpnService::class.java).setAction(DamavandVpnService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(state !is SessionState.Failed)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state is SessionState.Connecting || state is SessionState.Connected) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopIntent)
                }
            }
            .build()
    }

    private const val REQ_OPEN = 0
    private const val REQ_STOP = 1
}
