package com.amyrmo.damavand.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService as AndroidVpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amyrmo.damavand.data.settings.AppLanguage
import com.amyrmo.damavand.data.settings.ThemeMode
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.chains.ChainsScreen
import com.amyrmo.damavand.ui.components.DamavandScaffold
import com.amyrmo.damavand.ui.components.TopDestination
import com.amyrmo.damavand.ui.components.glass.NoGlassBackdrop
import com.amyrmo.damavand.ui.components.glass.rememberHazeGlassBackdrop
import com.amyrmo.damavand.ui.home.HomeScreen
import com.amyrmo.damavand.ui.logs.LogsScreen
import com.amyrmo.damavand.ui.servers.ServersScreen
import com.amyrmo.damavand.ui.settings.SettingsScreen
import com.amyrmo.damavand.ui.subscriptions.SubscriptionsScreen
import com.amyrmo.damavand.ui.theme.DamavandTheme
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.LocalGlassCapabilities
import java.util.Locale

/** Full-screen pages hosted over the four destinations (§5.1: not dock destinations). */
private enum class Overlay { NONE, SUBSCRIPTIONS, LOGS }

/**
 * The app root: theme + locale from [UiSettings], the scaffold/dock shell,
 * the VPN-permission and notification-permission flows, and the snackbar
 * surface for view-model notes.
 *
 * Language (§5.4): FA/EN build a localized context provided as
 * [LocalContext] (stringResource resolves through it) and force the
 * layout direction; SYSTEM follows the device.
 */
@Composable
fun DamavandApp() {
    val activityContext = LocalContext.current
    val appContext = remember { activityContext.applicationContext }
    val vm: AppViewModel = viewModel(factory = AppViewModelFactory(appContext))
    val ui by vm.uiSettings.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val dark = when (ui.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val devicePersian = remember {
        activityContext.resources.configuration.locales[0].language == "fa"
    }
    val persian = when (ui.language) {
        AppLanguage.SYSTEM -> devicePersian
        AppLanguage.FA -> true
        AppLanguage.EN -> false
    }
    val localized = remember(ui.language) { localizedContext(activityContext, ui.language) }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        DamavandTheme(
            darkTheme = dark,
            amoled = ui.amoled,
            persian = persian,
            reduceTransparency = ui.reduceTransparency,
            profileId = ui.themeProfileId,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppShell(vm)
            }
        }
    }
}

private fun localizedContext(base: Context, language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) return base
    val locale = Locale(if (language == AppLanguage.FA) "fa" else "en")
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

@Composable
private fun AppShell(vm: AppViewModel) {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(TopDestination.Home) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    val ui by vm.uiSettings.collectAsStateWithLifecycle()
    val capabilities = LocalGlassCapabilities.current
    val hazeBackdrop = rememberHazeGlassBackdrop()
    val backdrop = if (ui.glassBlur && capabilities.realBlur) hazeBackdrop else NoGlassBackdrop

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.notes.collect { note ->
            snackbar.showSnackbar(context.getString(note.res, *note.args.toTypedArray()))
        }
    }

    // VPN permission: prepare() returns an intent when consent is missing;
    // the pending connect resumes from the activity result.
    var pendingConnect by remember { mutableStateOf<Profile?>(null) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConnect?.let { vm.connect(it) }
        }
        pendingConnect = null
    }
    val onConnect: (Profile) -> Unit = remember(vm) {
        { profile ->
            val consent = AndroidVpnService.prepare(context)
            if (consent != null) {
                pendingConnect = profile
                vpnPermissionLauncher.launch(consent)
            } else {
                vm.connect(profile)
            }
        }
    }

    // Notification permission (API 33+), asked once when the shell appears.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Overlay pages consume back before the scaffold's back-to-Home does.
    androidx.activity.compose.BackHandler(enabled = overlay != Overlay.NONE) {
        overlay = Overlay.NONE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DamavandScaffold(
            current = destination,
            onSelect = { destination = it },
            backdrop = backdrop,
        ) { dest ->
            when (dest) {
                TopDestination.Home -> HomeScreen(
                    vm = vm,
                    onConnect = onConnect,
                    onOpenServers = { destination = TopDestination.Servers },
                    onOpenSubscriptions = { overlay = Overlay.SUBSCRIPTIONS },
                )

                TopDestination.Servers -> ServersScreen(
                    vm = vm,
                    onConnect = onConnect,
                    onOpenSubscriptions = { overlay = Overlay.SUBSCRIPTIONS },
                )

                TopDestination.Chains -> ChainsScreen(vm = vm, onConnect = onConnect)
                TopDestination.Settings -> SettingsScreen(
                    vm = vm,
                    onOpenSubscriptions = { overlay = Overlay.SUBSCRIPTIONS },
                    onOpenLogs = { overlay = Overlay.LOGS },
                )
            }
        }
        when (overlay) {
            Overlay.SUBSCRIPTIONS -> SubscriptionsScreen(
                vm = vm,
                onBack = { overlay = Overlay.NONE },
            )

            Overlay.LOGS -> LogsScreen(onBack = { overlay = Overlay.NONE })
            Overlay.NONE -> Unit
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.DockClearance),
        )
    }
}
