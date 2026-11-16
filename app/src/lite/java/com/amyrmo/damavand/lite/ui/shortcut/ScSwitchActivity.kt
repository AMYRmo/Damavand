package com.amyrmo.damavand.lite.ui.shortcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect

/**
 * Shortcut: toggle the VPN (3.2.11, PattNG ui/shortcut/ScSwitchActivity).
 * Runs, stops a running service or starts a stopped one, and ends —
 * nothing is shown. The translucent theme keeps it invisible.
 */
class ScSwitchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                moveTaskToBack(true)
                if (TaskerLauncher.isRunning(this@ScSwitchActivity)) {
                    TaskerLauncher.stop(this@ScSwitchActivity)
                } else {
                    TaskerLauncher.start(this@ScSwitchActivity, null)
                }
                finish()
            }
        }
    }
}
