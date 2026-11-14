package com.amyrmo.damavand.lite

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lite application — warms up the Xray core in the background.
 *
 * 1.1.1: installs a global uncaught-exception handler FIRST, so that even
 * a crash during launch leaves a readable stack trace on the device
 * (internal filesDir + external files dir, readable from a file manager
 * or Termux at /sdcard/Android/data/com.amyrmo.damavand.lite/files/).
 * The handler chains to the previous (system) handler, so the normal
 * "keeps stopping" dialog still appears.
 */
class LiteApp : Application() {
    override fun onCreate() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val text = "thread=${thread.name}\n${sw}\n"
                runCatching { File(filesDir, "crash-$stamp.txt").writeText(text) }
                runCatching {
                    val ext = getExternalFilesDir(null)
                    if (ext != null) File(ext, "crash-$stamp.txt").writeText(text)
                }
                Log.e("LiteCrash", text)
            } catch (_: Throwable) {
                // Never let the reporter itself crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
        super.onCreate()
        XrayController.warmUp(this)
    }
}
