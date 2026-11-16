package com.amyrmo.damavand.lite.ui.shortcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect

/**
 * Shortcut: start the VPN (3.2.11, PattNG ui/shortcut/ScStartActivity).
 * Runs, starts the service when it is not running already, and ends —
 * nothing is shown. The translucent theme keeps it invisible.
 */
class ScStartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                moveTaskToBack(true)
                if (!TaskerLauncher.isRunning(this@ScStartActivity)) {
                    TaskerLauncher.start(this@ScStartActivity, null)
                }
                finish()
            }
        }
    }
}
