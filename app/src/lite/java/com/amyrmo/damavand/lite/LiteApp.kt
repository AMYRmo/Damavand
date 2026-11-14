package com.amyrmo.damavand.lite

import android.app.Application

/** Lite application — warms up the Xray core in the background. */
class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XrayController.warmUp(this)
    }
}
