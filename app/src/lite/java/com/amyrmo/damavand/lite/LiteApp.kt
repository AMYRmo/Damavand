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
 * Lite application — deliberately does almost nothing at launch.
 *
 * 2.0.0 (full rewrite): the 1.1.x builds died on launch twice with a
 * black screen and no UI. The launch path now contains NO native-code
 * loading at all: the Xray core (libgojni) is loaded lazily on the first
 * connect / ping test, via XrayController.ready(). A broken native lib
 * can therefore never kill the launch — it can only fail a connect
 * attempt, with a readable error. The only work done here is installing
 * the global crash handler (which writes crash-<time>.txt) and handing
 * the application context to the controller for that lazy init.
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
        // Context only — no native code is touched here (see class doc).
        XrayController.setAppContext(this)
    }
}
