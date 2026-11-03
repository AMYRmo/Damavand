package com.amyrmo.damavand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.amyrmo.damavand.ui.DamavandApp

/**
 * Single-activity shell host (architecture §5.1). Everything — theme,
 * locale, navigation state, permissions — lives in [DamavandApp].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DamavandApp()
        }
    }
}
