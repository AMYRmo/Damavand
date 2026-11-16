package com.amyrmo.damavand.lite.ui.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.amyrmo.damavand.lite.AppLog

/**
 * The Tasker plugin's receiver (3.2.11, PattNG receiver/TaskerReceiver):
 * Tasker fires the setting, and this starts or stops the service for
 * the profile the setting names — the default guid means the selected
 * one, any other guid the profile with that id.
 */
class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val bundle = intent?.getBundleExtra(TaskerLauncher.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(TaskerLauncher.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(TaskerLauncher.TASKER_EXTRA_BUNDLE_GUID).orEmpty()

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else if (switch) {
                TaskerLauncher.start(
                    context,
                    if (guid == TaskerLauncher.TASKER_DEFAULT_GUID) null else guid
                )
            } else {
                TaskerLauncher.stop(context)
            }
        } catch (e: Exception) {
            AppLog.w("TaskerReceiver", "error processing Tasker broadcast: ${e.message}")
        }
    }
}
