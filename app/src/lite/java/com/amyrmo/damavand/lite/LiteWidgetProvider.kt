package com.amyrmo.damavand.lite

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget (3.2.0, PattNG): status + speeds, connect /
 * disconnect buttons. Connect opens the app (it owns the VPN
 * permission flow); disconnect stops the service directly. The widget
 * refreshes on every service status broadcast.
 */
class LiteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { updateWidget(context, manager, it, null) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == LiteVpnService.ACTION_STATUS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LiteWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it, intent) }
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        status: Intent?
    ) {
        val views = RemoteViews(context.packageName, R.layout.lite_widget)
        val state = status?.getStringExtra("state")
        // 1.0.1.2 (#104): onUpdate arrives with no status (e.g. the
        // widget was just added while the VPN is connected) — fall back
        // to the persisted tile state instead of showing "Disconnected".
        val tile = context.getSharedPreferences("lite_tile", Context.MODE_PRIVATE)
        val connected = state == LiteVpnService.STATE_CONNECTED ||
            (state == null && tile.getBoolean("connected", false))
        val connecting = state == LiteVpnService.STATE_CONNECTING ||
            (state == null && tile.getBoolean("connecting", false))
        views.setTextViewText(
            R.id.widget_status,
            when {
                connected -> context.getString(R.string.widget_connected)
                connecting -> context.getString(R.string.widget_connecting)
                else -> context.getString(R.string.widget_disconnected)
            }
        )
        val down = status?.getLongExtra("downSpeed", 0L) ?: 0L
        val up = status?.getLongExtra("upSpeed", 0L) ?: 0L
        views.setTextViewText(
            R.id.widget_speed,
            if (connected) {
                "↓ ${LiteVpnService.formatBytes(down)}/s  ↑ ${LiteVpnService.formatBytes(up)}/s"
            } else {
                status?.getStringExtra("detail") ?: context.getString(R.string.app_name)
            }
        )

        val connectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("lite_action", "connect")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_connect,
            PendingIntent.getActivity(
                context, 0, connectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val stopIntent = Intent(context, LiteVpnService::class.java).apply {
            action = LiteVpnService.ACTION_STOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_disconnect,
            PendingIntent.getService(
                context, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        manager.updateAppWidget(id, views)
    }
}
