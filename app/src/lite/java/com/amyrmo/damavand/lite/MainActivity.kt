package com.amyrmo.damavand.lite

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.LiteBottomBar
import com.amyrmo.damavand.lite.ui.components.LiteHeader
import com.amyrmo.damavand.lite.ui.screens.ServersScreen
import com.amyrmo.damavand.lite.ui.screens.SettingsScreen
import com.amyrmo.damavand.lite.ui.screens.StatsScreen
import com.amyrmo.damavand.lite.ui.screens.SummitHomeScreen
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Damavand Lite — single activity, four Summit tabs (Home / Servers /
 * Stats / Settings). No onboarding, no login, no design picker.
 * Android back from any tab goes to Home; back from Home exits.
 */
class MainActivity : ComponentActivity() {

    private val vm: LiteViewModel by viewModels()

    private var pendingConnect = false

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingConnect) {
            pendingConnect = false
            vm.connect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1.1.1: if the first Compose frame throws, show a plain error
        // view instead of dying — and the crash handler in LiteApp has
        // already written the full stack trace to crash-<time>.txt.
        try {
            setContent {
                MaterialTheme(
                    colorScheme = androidx.compose.material3.darkColorScheme(
                        background = LiteColors.bg,
                        surface = LiteColors.surface,
                        onBackground = LiteColors.text,
                        onSurface = LiteColors.text,
                        primary = LiteColors.accent,
                    ),
                    typography = androidx.compose.material3.Typography(
                        bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                        labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = Vazirmatn),
                    )
                ) {
                    LiteRoot(vm = vm, onConnectRequest = { requestConnect() })
                }
            }
        } catch (t: Throwable) {
            val tv = android.widget.TextView(this)
            tv.text = "Damavand Lite failed to start.\n\n$t\n\nSee crash-*.txt in the app files folder."
            tv.setPadding(48, 48, 48, 48)
            setContentView(tv)
            return
        }
        // One auto-connect attempt on launch (spec §7.1).
        runCatching { vm.autoConnectOnce() }
    }

    /** Connect, requesting the system VPN permission first if needed. */
    fun requestConnect() {
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            vm.connect()
        } else {
            pendingConnect = true
            vpnPrepareLauncher.launch(prepare)
        }
    }
}

@Composable
private fun LiteRoot(vm: LiteViewModel, onConnectRequest: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) } // 0 Home, 1 Servers, 2 Stats, 3 Settings
    val connection by vm.connection.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selectedId by vm.selectedId.collectAsState()

    // The chart only samples while the Stats tab is visible and connected.
    vm.setStatsVisible(tab == 2)

    // Android back from any tab goes to Home; back from Home exits the app.
    BackHandler(enabled = tab != 0) { tab = 0 }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LiteColors.bg,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LiteHeader(
                state = connection.state,
                onErrorPillClick = { tab = 0 },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(LiteColors.bg)
            ) {
                // All four tabs stay composed (each keeps its own scroll
                // position); only the selected one is visible, rendered
                // last so it is on top and receives touches.
                for (i in listOf(0, 1, 2, 3).filter { it != tab }) {
                    TabPage(visible = false) { TabContent(i, vm, connection, profiles, selectedId, onConnectRequest) { tab = 1 } { tab = 3 } }
                }
                TabPage(visible = true) { TabContent(tab, vm, connection, profiles, selectedId, onConnectRequest) { tab = 1 } { tab = 3 } }
            }
            LiteBottomBar(
                selected = tab,
                isConnected = connection.state == ConnState.CONNECTED,
                onSelect = { tab = it },
            )
        }
    }
}

@Composable
private fun TabPage(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (visible) 1f else 0f)
    ) {
        content()
    }
}

@Composable
private fun TabContent(
    tab: Int,
    vm: LiteViewModel,
    connection: ConnectionUiState,
    profiles: List<Profile>,
    selectedId: String?,
    onConnectRequest: () -> Unit,
    onGoToServers: () -> Unit,
    onGoToSettingsGeo: () -> Unit,
) {
    when (tab) {
        0 -> SummitHomeScreen(
            vm = vm,
            connection = connection,
            profiles = profiles,
            selectedId = selectedId,
            onConnectRequest = onConnectRequest,
            onGoToServers = onGoToServers,
            onGoToSettingsGeo = onGoToSettingsGeo,
        )
        1 -> ServersScreen(
            vm = vm,
            profiles = profiles,
            selectedId = selectedId,
            isConnected = connection.state == ConnState.CONNECTED,
        )
        2 -> StatsScreen(vm = vm, connection = connection)
        3 -> SettingsScreen(vm = vm)
    }
}
