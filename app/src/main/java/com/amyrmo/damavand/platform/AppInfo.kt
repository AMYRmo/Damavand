package com.amyrmo.damavand.platform

import android.content.Context

/** Small app-identity facts the cores ask for (v1's platform AppInfo). */
object AppInfo {
    fun versionName(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
}
