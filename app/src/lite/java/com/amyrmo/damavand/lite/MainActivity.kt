package com.amyrmo.damavand.lite

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.runBlocking
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.LiteBottomBar
import com.amyrmo.damavand.lite.ui.components.LiteHeader
import com.amyrmo.damavand.lite.ui.screens.LogsScreen
import com.amyrmo.damavand.lite.ui.screens.PerAppScreen
import com.amyrmo.damavand.lite.ui.screens.RoutingScreen
import com.amyrmo.damavand.lite.ui.screens.ScannerScreen
import com.amyrmo.damavand.lite.ui.screens.ServersScreen
import com.amyrmo.damavand.lite.ui.screens.SettingsScreen
import com.amyrmo.damavand.lite.ui.screens.StatsScreen
import com.amyrmo.damavand.lite.ui.screens.SubScreen
import com.amyrmo.damavand.lite.ui.screens.SubscriptionsScreen
import com.amyrmo.damavand.lite.ui.screens.SummitHomeScreen
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import com.amyrmo.damavand.lite.ui.theme.isSystemDark
import com.amyrmo.damavand.lite.ui.theme.resolveDark

/**
 * Damavand Lite — single activity, four Summit tabs (Home / Servers /
 * Stats / Settings). No onboarding, no login, no design picker.
 * Android back from any tab goes to Home; back from Home exits.
 *
 * 2.0.0: only the VISIBLE tab is composed. The 1.1.x build composed all
 * four tabs at once (invisible ones at alpha 0), so a composition crash
 * in ANY tab killed the launch. Each tab's scroll state is hoisted here
 * so tabs still keep their scroll position across switches.
 */
class MainActivity : ComponentActivity() {

    // 3.2.0: apply the stored app language (auto/fa/en) before any
    // resource is loaded, so the right strings + layout direction win.
    override fun attachBaseContext(newBase: Context) {
        val stored = try {
            runBlocking { SettingsStore(newBase).languageNow() }
        } catch (_: Exception) {
            "auto"
        }
        // 3.2.5: apply the stored theme (system/dark/light) before any
        // frame, so a light theme never flashes dark on cold start.
        try {
            val themeMode = runBlocking { SettingsStore(newBase).themeModeNow() }
            LiteColors.applyDark(resolveDark(themeMode, isSystemDark(newBase)))
            // 3.2.9: dynamic color flag (the palette itself resolves
            // once Compose runs — until then the fixed tokens apply).
            val dynamicColor = runBlocking { SettingsStore(newBase).dynamicColorNow() }
            LiteColors.applyDynamic(
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        } catch (_: Exception) {
        }
        super.attachBaseContext(
            LocaleHelper.wrap(newBase, LocaleHelper.resolveLanguage(stored))
        )
    }

    private val vm: LiteViewModel by viewModels()

    private var pendingConnect = false
    private var pendingFastest = false

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingConnect) {
            pendingConnect = false
            vm.connect()
        } else if (pendingFastest) {
            pendingFastest = false
            vm.connectFastest()
        }
    }

    // 3.2.0 (PattNG): custom geo import file picker.
    private var pendingGeoKind = "geoip"

    private val importGeoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importGeoFile(uri, pendingGeoKind) { ok ->
                Toast.makeText(
                    this,
                    if (ok) getString(R.string.geo_import_ok) else getString(R.string.geo_import_fail),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(uri) { ok ->
                Toast.makeText(
                    this,
                    if (ok) getString(R.string.backup_export_ok) else getString(R.string.backup_export_fail),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importBackup(uri) { ok ->
                Toast.makeText(
                    this,
                    if (ok) getString(R.string.backup_import_ok) else getString(R.string.backup_import_fail),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 3.2.11 (PattNG ImportConfigLocal): import configs from a file.
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importFile(uri) { added ->
                Toast.makeText(
                    this,
                    if (added >= 0) getString(R.string.import_file_ok, added) else getString(R.string.import_file_fail),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2.0.0: if the first Compose frame throws, show a plain error
        // view instead of dying — and the crash handler in LiteApp has
        // already written the full stack trace to crash-<time>.txt.
        try {
            setContent {
                // 3.2.5: the theme follows the stored mode + the system,
                // live — changing the setting or the system theme
                // re-applies it, and the system bars follow the theme.
                val themeMode by vm.themeMode.collectAsState(initial = "system")
                val systemDark = isSystemInDarkTheme()
                val isDark = resolveDark(themeMode, systemDark)
                // 3.2.9 (PattNG dynamicColor): Android 12+ only — the
                // wallpaper palette, resolved in composition (the
                // dynamic*ColorScheme calls are @Composable).
                val dynamicColor by vm.dynamicColor.collectAsState(initial = false)
                val useDynamic = dynamicColor &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val dynamicScheme = if (useDynamic) {
                    if (isDark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                } else null
                SideEffect {
                    LiteColors.applyDark(isDark)
                    LiteColors.applyDynamic(useDynamic)
                    if (dynamicScheme != null) {
                        LiteColors.applyDynamicScheme(
                            accent = dynamicScheme.primary,
                            bg = dynamicScheme.background,
                            surface = dynamicScheme.surface,
                            surface2 = dynamicScheme.surfaceVariant,
                            text = dynamicScheme.onBackground,
                            textSecondary = dynamicScheme.onSurfaceVariant,
                        )
                    } else {
                        LiteColors.applyDynamicScheme(null, null, null, null, null, null)
                    }
                    window.statusBarColor = LiteColors.bg.toArgb()
                    window.navigationBarColor = LiteColors.bg.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !LiteColors.dark
                        isAppearanceLightNavigationBars = !LiteColors.dark
                    }
                }
                MaterialTheme(
                    colorScheme = if (LiteColors.dark) androidx.compose.material3.darkColorScheme(
                        background = LiteColors.bg,
                        surface = LiteColors.surface,
                        onBackground = LiteColors.text,
                        onSurface = LiteColors.text,
                        primary = LiteColors.accent,
                    ) else androidx.compose.material3.lightColorScheme(
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
                    LiteRoot(
                        vm = vm,
                        onConnectRequest = { requestConnect() },
                        onFastestRequest = { requestFastestConnect() },
                        onExportBackup = { exportBackupLauncher.launch("damavand-lite-backup.json") },
                        onImportBackup = { importBackupLauncher.launch(arrayOf("application/json")) },
                        onImportFile = { importFileLauncher.launch(arrayOf("*/*")) },
                        onImportGeo = { kind ->
                            pendingGeoKind = kind
                            importGeoLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
        } catch (t: Throwable) {
            val tv = android.widget.TextView(this)
            tv.text = "Damavand Lite failed to start.\n\n$t\n\nSee crash-*.txt in the app files folder."
            tv.setPadding(48, 48, 48, 48)
            setContentView(tv)
            return
        }
        // One auto-connect attempt on launch (spec §7.1) — unless a
        // shortcut/widget action already decided what to do (3.2.0).
        when (intent?.getStringExtra("lite_action")) {
            "connect" -> requestConnect()
            "disconnect" -> vm.disconnect()
            else -> runCatching { vm.autoConnectOnce() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 3.2.0: shortcut / widget action while the app is already open.
        when (intent.getStringExtra("lite_action")) {
            "connect" -> requestConnect()
            "disconnect" -> vm.disconnect()
        }
    }

    /** Connect, requesting the system VPN permission first if needed. */
    fun requestConnect() {
        // 3.2.10 (PattNG root mode): no VpnService at all — no consent
        // dialog to request.
        if (vm.isRootModeNow()) {
            vm.connect()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            vm.connect()
        } else {
            pendingConnect = true
            vpnPrepareLauncher.launch(prepare)
        }
    }

    /** Real fastest-connect (3.2.0), same VPN-permission dance. */
    fun requestFastestConnect() {
        if (vm.isRootModeNow()) {
            vm.connectFastest()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            vm.connectFastest()
        } else {
            pendingFastest = true
            vpnPrepareLauncher.launch(prepare)
        }
    }
}

@Composable
private fun LiteRoot(
    vm: LiteViewModel,
    onConnectRequest: () -> Unit,
    onFastestRequest: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFile: () -> Unit,
    onImportGeo: (String) -> Unit
) {
    // Saveable: a recreation (language change, theme, process death)
    // keeps the user on the same tab / sub-screen.
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 Home, 1 Servers, 2 Stats, 3 Settings
    // 3.1.0 (PattNG): a sub-screen opened from Settings hides the tabs.
    var subScreen by rememberSaveable { mutableStateOf<SubScreen?>(null) }
    val connection by vm.connection.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selectedId by vm.selectedId.collectAsState()

    // Scroll states hoisted here so each tab keeps its position while
    // only the visible tab is composed (see class doc).
    val homeScroll = rememberScrollState()
    val serversList = rememberLazyListState()
    val statsScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()

    // The chart only samples while the Stats tab is visible and connected.
    // (LaunchedEffect, not a call during composition.)
    LaunchedEffect(tab) { vm.setStatsVisible(tab == 2) }

    // Android back from any tab goes to Home; back from Home exits the app.
    // A sub-screen closes first.
    BackHandler(enabled = subScreen != null) { subScreen = null }
    BackHandler(enabled = subScreen == null && tab != 0) { tab = 0 }

    // 2.0.1: the app was Persian-only, but the phone's system locale
    // may be English (LTR) — without this, the whole UI mirrors.
    // 3.2.0: the direction now follows the APP language — RTL for
    // Persian, LTR for English (automatic fa/en selection).
    val appRtl = Locale.getDefault().language == "fa"
    CompositionLocalProvider(
        LocalLayoutDirection provides if (appRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
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
                // Only the selected tab is composed (2.0.0 launch safety).
                // A sub-screen replaces the tab content entirely.
                val sub = subScreen
                if (sub != null) {
                    when (sub) {
                        SubScreen.SUBSCRIPTIONS -> SubscriptionsScreen(vm = vm, onBack = { subScreen = null })
                        SubScreen.ROUTING -> RoutingScreen(vm = vm, onBack = { subScreen = null })
                        SubScreen.PER_APP -> PerAppScreen(vm = vm, onBack = { subScreen = null })
                        SubScreen.LOGS -> LogsScreen(vm = vm, onBack = { subScreen = null })
                        SubScreen.QR_SCAN -> ScannerScreen(vm = vm, onBack = { subScreen = null })
                    }
                } else {
                    TabContent(
                        tab = tab,
                        vm = vm,
                        connection = connection,
                        profiles = profiles,
                        selectedId = selectedId,
                        onConnectRequest = onConnectRequest,
                        onFastestRequest = onFastestRequest,
                        onGoToServers = { tab = 1 },
                        onGoToSettingsGeo = { tab = 3 },
                        onNavigate = { subScreen = it },
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        onImportFile = onImportFile,
                        onImportGeo = onImportGeo,
                        onOpenQrScan = { subScreen = SubScreen.QR_SCAN },
                        homeScroll = homeScroll,
                        serversList = serversList,
                        statsScroll = statsScroll,
                        settingsScroll = settingsScroll,
                    )
                }
            }
            if (subScreen == null) {
                LiteBottomBar(
                    selected = tab,
                    isConnected = connection.state == ConnState.CONNECTED,
                    onSelect = { tab = it },
                )
            }
        }
    }
    } // CompositionLocalProvider (app language direction)
}

@Composable
private fun TabContent(
    tab: Int,
    vm: LiteViewModel,
    connection: ConnectionUiState,
    profiles: List<Profile>,
    selectedId: String?,
    onConnectRequest: () -> Unit,
    onFastestRequest: () -> Unit,
    onGoToServers: () -> Unit,
    onGoToSettingsGeo: () -> Unit,
    onNavigate: (SubScreen) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFile: () -> Unit,
    onImportGeo: (String) -> Unit,
    onOpenQrScan: () -> Unit,
    homeScroll: androidx.compose.foundation.ScrollState,
    serversList: androidx.compose.foundation.lazy.LazyListState,
    statsScroll: androidx.compose.foundation.ScrollState,
    settingsScroll: androidx.compose.foundation.ScrollState,
) {
    when (tab) {
        0 -> SummitHomeScreen(
            vm = vm,
            connection = connection,
            profiles = profiles,
            selectedId = selectedId,
            onConnectRequest = onConnectRequest,
            onFastestRequest = onFastestRequest,
            onGoToServers = onGoToServers,
            onGoToSettingsGeo = onGoToSettingsGeo,
            scrollState = homeScroll,
        )
        1 -> ServersScreen(
            vm = vm,
            profiles = profiles,
            selectedId = selectedId,
            isConnected = connection.state == ConnState.CONNECTED,
            listState = serversList,
            onOpenQrScan = onOpenQrScan,
            onImportFile = onImportFile,
            onOpenSubscriptions = { onNavigate(SubScreen.SUBSCRIPTIONS) },
        )
        2 -> StatsScreen(vm = vm, connection = connection, scrollState = statsScroll)
        3 -> SettingsScreen(
            vm = vm,
            scrollState = settingsScroll,
            onNavigate = onNavigate,
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup,
            onImportGeo = onImportGeo,
        )
    }
}
