package com.amyrmo.damavand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.amyrmo.damavand.ui.home.HomePlaceholder
import com.amyrmo.damavand.ui.theme.DamavandTheme

/**
 * Single-activity shell host (architecture §5.1). Phase 1 wires the design
 * system: [DamavandTheme] + the scaffold/dock showcase in
 * [HomePlaceholder]. The real screens replace the placeholder in the
 * screens phase; the language setting will drive `persian` + the locale
 * (for now it follows the device locale, §5.4).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val persian = resources.configuration.locales[0].language == "fa"
        setContent {
            DamavandTheme(persian = persian) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomePlaceholder()
                }
            }
        }
    }
}
