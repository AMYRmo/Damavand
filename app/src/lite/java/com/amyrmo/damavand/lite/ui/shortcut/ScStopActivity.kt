package com.amyrmo.damavand.lite.ui.shortcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect

/**
 * Shortcut: stop the VPN (3.2.11, PattNG ui/shortcut/ScStopActivity).
 * Runs, stops the service when it is running, and ends — nothing is
 * shown. The translucent theme keeps it invisible.
 */
class ScStopActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                moveTaskToBack(true)
                if (TaskerLauncher.isRunning(this@ScStopActivity)) {
                    TaskerLauncher.stop(this@ScStopActivity)
                }
                finish()
            }
        }
    }
}
