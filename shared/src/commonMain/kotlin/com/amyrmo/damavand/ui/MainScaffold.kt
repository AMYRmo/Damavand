package com.amyrmo.damavand.ui

import com.amyrmo.damavand.ui.platform.BackHandler
import com.amyrmo.damavand.ui.home.HomeFabCluster
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.domain.config.effectiveLocalProxyPort
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.auto_group_all
import com.amyrmo.damavand.shared.resources.auto_title_for
import com.amyrmo.damavand.shared.resources.tour_done
import com.amyrmo.damavand.shared.resources.tour_next
import com.amyrmo.damavand.shared.resources.tour_skip
import com.amyrmo.damavand.domain.model.AppLanguage
import com.amyrmo.damavand.domain.model.AppSettings
import com.amyrmo.damavand.ui.servers.ServersScreen
import com.amyrmo.damavand.ui.theme.toPersonalization
import com.amyrmo.damavand.ui.connection.ConnectionViewModel
import com.amyrmo.damavand.ui.home.HomeScreen
import com.amyrmo.damavand.ui.motion.transitionDecoration
import com.amyrmo.damavand.ui.navigation.TopDestination
import com.amyrmo.damavand.ui.settings.SettingsScreen
import com.amyrmo.damavand.ui.update.NudgeHost

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScaffold(
    settings: AppSettings,
    configRepository: ConfigRepository,
    onToggleConnection: () -> Unit,

    onActiveServerChanged: () -> Unit = {},
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,

    aiBridge: com.amyrmo.damavand.domain.ai.AiAppBridge? = null,
    appVersion: String = "",
) {
    val platform = com.amyrmo.damavand.ui.platform.LocalPlatform.current
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }

    var setupDone by rememberSaveable {
        mutableStateOf(settings.onboardingVersion >= com.amyrmo.damavand.ui.onboarding.ONBOARDING_VERSION)
    }
    var tourStep by rememberSaveable { mutableStateOf(-1) }

    var deepLinkOrigin by rememberSaveable { mutableStateOf<TopDestination?>(null) }

    var pendingSettingsPage by rememberSaveable {
        mutableStateOf(com.amyrmo.damavand.ui.settings.SettingsPage.Root)
    }
    val connectionVm: ConnectionViewModel = viewModel { ConnectionViewModel() }
    val homeVm: com.amyrmo.damavand.ui.home.HomeViewModel = viewModel { com.amyrmo.damavand.ui.home.HomeViewModel() }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val profiles by configRepository.profiles.collectAsStateWithLifecycle()
    val subscriptions by configRepository.subscriptions.collectAsStateWithLifecycle()
    val activeId by configRepository.activeId.collectAsStateWithLifecycle()

    val activeProfile = androidx.compose.runtime.remember(profiles, subscriptions, activeId) {
        activeId?.let { configRepository.profile(it) }
    }
    val autoSession by com.amyrmo.damavand.core.AutoSelect.session.collectAsStateWithLifecycle()
    val bento = settings.appDesign == com.amyrmo.damavand.domain.model.AppDesign.Bento

    val lockedNote = activeProfile?.takeIf { it.isLocked }?.note

    val mtuHint = androidx.compose.runtime.remember(activeProfile) {
        val link = (activeProfile?.source as? com.amyrmo.damavand.domain.config.ProfileSource.Link)?.link
        val parsed = link?.let {
            runCatching { com.amyrmo.damavand.domain.config.ConfigParser.parse(it) }.getOrNull()
        }
        val host = parsed?.address?.takeIf { it.isNotBlank() } ?: activeProfile?.address.orEmpty()
        com.amyrmo.damavand.data.net.MtuServerHint(
            host = host,
            overhead = parsed
                ?.let { com.amyrmo.damavand.data.net.MtuOverheads.ofServer(it) }
                ?: com.amyrmo.damavand.data.net.MtuOverheads.worstCase(),
        )
    }

    com.amyrmo.damavand.ui.servers.DeepLinkImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Servers },
    )

    NudgeHost(settings = settings, onUpdateSettings = onUpdateSettings)

    BackHandler(enabled = current != TopDestination.Home) { current = TopDestination.Home }

    BackHandler(enabled = tourStep >= 0) {
        tourStep = -1
        current = TopDestination.Home
    }

    if (!setupDone) {
        com.amyrmo.damavand.ui.onboarding.OnboardingFlow(
            settings = settings,
            onUpdate = onUpdateSettings,
            onLanguage = onLanguage,
            onStartTour = {
                onUpdateSettings {
                    it.copy(onboardingVersion = com.amyrmo.damavand.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
                current = TopDestination.Home
                tourStep = 0
            },
            onFinish = {
                onUpdateSettings {
                    it.copy(onboardingVersion = com.amyrmo.damavand.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            if (!bento) {
                com.amyrmo.damavand.ui.navigation.ZedNavBar(
                    style = settings.navBarStyle,
                    current = current,
                    onSelect = { current = it },
                )
            }
        },
    ) { innerPadding ->
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        val sign = com.amyrmo.damavand.ui.motion.layoutSign()
        val contentPadding = if (bento) {
            PaddingValues(
                start = innerPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                bottom = innerPadding.calculateBottomPadding() +
                    com.amyrmo.damavand.ui.bento.BentoDockHeight +
                    com.amyrmo.damavand.ui.bento.BentoDockMargin + 8.dp,
            )
        } else {
            innerPadding
        }
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                com.amyrmo.damavand.ui.motion.screenTransition(
                    style = settings.screenTransition,
                    forward = targetState.ordinal >= initialState.ordinal,
                    direction = sign,
                    reduceMotion = settings.reduceMotion,
                ).using(SizeTransform(clip = false))
            },
            label = "top-destination",
            modifier = Modifier.fillMaxSize(),
        ) { dest ->

            val decoration = transitionDecoration(
                style = settings.screenTransition,
                forward = dest.ordinal >= current.ordinal,
                direction = sign,
                reduceMotion = settings.reduceMotion,
            )
            Box(decoration) {
            when (dest) {
                TopDestination.Home -> {
                    val homeName = activeProfile?.let { p ->
                        if (p.isAutoSelect) {
                            stringResource(
                                Res.string.auto_title_for,
                                com.amyrmo.damavand.ui.servers.autoSelectGroupLabel(p),
                            )
                        } else p.name
                    }
                    val homeDetail = activeProfile?.takeIf { it.isAutoSelect }?.let { p ->
                        autoSession?.takeIf { it.profileId == p.id }?.let { session ->
                            com.amyrmo.damavand.ui.servers.autoSelectLiveLine(session) { id ->
                                profiles.firstOrNull { it.id == id }?.name
                            }
                        }
                    }
                    HomeScreen(
                    connectionVm = connectionVm,
                    homeVm = homeVm,
                    activeConfigName = homeName,
                    exitGeneration = autoSession?.takeIf { it.profileId == activeProfile?.id }?.exitGeneration ?: 0,
                    activeConfigDetail = homeDetail,
                    lockedNote = lockedNote,
                    localProxyAddress = if (settings.runMode == com.amyrmo.damavand.domain.model.RunMode.ProxyOnly) {
                        "127.0.0.1:${com.amyrmo.damavand.core.VpnManager.activeSocksPort ?: settings.effectiveLocalProxyPort}"
                    } else null,
                    activeLocked = activeProfile?.isLocked == true,
                    reduceMotion = settings.reduceMotion,

                    showConnectionInfo = activeProfile?.isDnsTunnel != true,
                    ipApiUrl = settings.ipApiUrl,
                    delayTestUrl = settings.delayTestUrl,

                    personalization = settings.toPersonalization(),
                    connectStyle = settings.connectButtonStyle,
                    design = settings.appDesign,
                    bentoActive = activeProfile?.let { p ->
                        com.amyrmo.damavand.ui.bento.BentoActiveServer(
                            name = homeName ?: p.name,
                            detail = homeDetail ?: p.transportLabel.removeSuffix(" · sing-box"),
                            country = if (settings.detectServerLocation) {
                                com.amyrmo.damavand.domain.config.ProfileCountry.of(p)
                            } else null,
                            engine = autoSession?.takeIf { p.isAutoSelect && it.profileId == p.id }
                                ?.selectedProfileId
                                ?.let { id -> profiles.firstOrNull { it.id == id } }
                                ?.let { bentoEngineLabel(it) }
                                ?: bentoEngineLabel(p),
                            locked = p.isLocked,
                            subscription = p.subscriptionId.ifBlank { p.autoSelectSettings()?.subscriptionId.orEmpty() }
                                .takeIf { it.isNotBlank() }
                                ?.let { id -> subscriptions.firstOrNull { it.id == id } },
                        )
                    },
                    bentoPicks = androidx.compose.runtime.remember(profiles, activeId, settings.detectServerLocation) {
                        profiles
                            .filter { !it.isLocked && it.id != activeId && (it.lastPingMs ?: 0) > 0 }
                            .sortedBy { it.lastPingMs }
                            .take(3)
                            .map {
                                com.amyrmo.damavand.ui.bento.BentoServerPick(
                                    id = it.id,
                                    name = it.name,
                                    detail = it.transportLabel.removeSuffix(" · sing-box"),
                                    country = if (settings.detectServerLocation) {
                                        com.amyrmo.damavand.domain.config.ProfileCountry.of(it)
                                    } else null,
                                    pingMs = it.lastPingMs,
                                )
                            }
                    },
                    onPickServer = { id ->
                        configRepository.setActive(id)
                        onActiveServerChanged()
                    },
                    onOpenSpeedTest = {
                        deepLinkOrigin = current
                        pendingSettingsPage = com.amyrmo.damavand.ui.settings.SettingsPage.SpeedTest
                        current = TopDestination.Settings
                    },
                    onOpenMap = {
                        deepLinkOrigin = current
                        pendingSettingsPage = com.amyrmo.damavand.ui.settings.SettingsPage.Map
                        current = TopDestination.Settings
                    },
                    contentPadding = contentPadding,
                    onToggleConnection = onToggleConnection,

                    onBrowseConfigs = { current = TopDestination.Servers },
                    )
                }
                TopDestination.Servers -> ServersScreen(
                    repository = configRepository,
                    contentPadding = contentPadding,
                    twoColumns = settings.doubleColumnDisplay,
                    showAllGroup = settings.groupAllDisplay,
                    confirmRemove = settings.confirmRemove,
                    realPingConcurrency = settings.realPingConcurrency,
                    delayTestUrl = settings.delayTestUrl,
                    autoTestAfterUpdate = settings.autoTestAfterUpdate,
                    autoRemoveInvalidAfterTest = settings.autoRemoveInvalidAfterTest,
                    autoSortAfterTest = settings.autoSortAfterTest,
                    personalization = settings.toPersonalization(),
                    reduceMotion = settings.reduceMotion,
                    onServerActivated = {
                        if (settings.homeAfterSelect) current = TopDestination.Home

                        onActiveServerChanged()
                    },
                )
                TopDestination.Settings -> SettingsScreen(
                    settings = settings,
                    contentPadding = contentPadding,
                    onUpdate = onUpdateSettings,
                    onLanguage = onLanguage,
                    initialPage = pendingSettingsPage,
                    onDeepLinkBack = deepLinkOrigin?.let { origin ->
                        {
                            current = origin
                            deepLinkOrigin = null
                        }
                    },
                    onInitialPageConsumed = {
                        pendingSettingsPage = com.amyrmo.damavand.ui.settings.SettingsPage.Root
                    },

                    serverTargets = profiles
                        .filter { it.source is com.amyrmo.damavand.domain.config.ProfileSource.Link }
                        .map { it.id to it.name },
                    groupTargets = listOf(
                        com.amyrmo.damavand.domain.config.AutoSelectIds.ALL to
                            stringResource(Res.string.auto_title_for, stringResource(Res.string.auto_group_all)),
                    ) + subscriptions.map { sub ->
                        com.amyrmo.damavand.domain.config.AutoSelectIds.of(sub.id) to stringResource(Res.string.auto_title_for, sub.name)
                    },
                    dnsTunnelActive = activeProfile?.isDnsBasedTunnel == true,

                    mtuHint = mtuHint,
                    ai = aiBridge?.let { bridge ->
                        com.amyrmo.damavand.ui.settings.AiSection(
                            settings = settings.ai,
                            onUpdate = { next -> onUpdateSettings { it.copy(ai = next) } },
                            bridge = bridge,
                            onOpenUrl = { url -> platform.openUri(url) },

                            languageName = languageEnglishName(settings.language),
                            languageNative = languageNativeName(settings.language),
                            platform = "Android",
                            appVersion = appVersion,
                        )
                    },
                )
            }
            }
        }

        val vpnStatus by com.amyrmo.damavand.core.VpnManager.status.collectAsStateWithLifecycle()
        val detailsOpen by homeVm.expanded.collectAsStateWithLifecycle()
        HomeFabCluster(
            visible = !bento && current == TopDestination.Home &&
                !(detailsOpen && settings.appDesign == com.amyrmo.damavand.domain.model.AppDesign.Pixel),
            showSpeedTest = vpnStatus.state == com.amyrmo.damavand.domain.model.ConnectionState.Connected &&
                activeProfile?.isDnsBasedTunnel != true,
            reduceMotion = settings.reduceMotion,
            atEnd = settings.speedFabAtEnd,
            yFraction = settings.speedFabY,
            onMove = { end, y -> onUpdateSettings { it.copy(speedFabAtEnd = end, speedFabY = y) } },
            onOpenMap = {
                deepLinkOrigin = current
                pendingSettingsPage = com.amyrmo.damavand.ui.settings.SettingsPage.Map
                current = TopDestination.Settings
            },
            onOpenSpeedTest = {
                deepLinkOrigin = current
                pendingSettingsPage = com.amyrmo.damavand.ui.settings.SettingsPage.SpeedTest
                current = TopDestination.Settings
            },
            modifier = Modifier.padding(innerPadding),
        )
        if (bento) {
            com.amyrmo.damavand.ui.bento.BentoDock(
                tabs = listOf(TopDestination.Home, TopDestination.Servers, TopDestination.Settings),
                current = current,
                onSelect = { current = it },
                visible = true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
      }
    }

    val tourSteps = com.amyrmo.damavand.ui.onboarding.rememberTourSteps()
    if (tourStep in tourSteps.indices) {
        val step = tourSteps[tourStep]

        val finish = {
            tourStep = -1
            current = TopDestination.Home
        }
        val advance = { if (tourStep >= tourSteps.lastIndex) finish() else tourStep += 1 }
        androidx.compose.runtime.LaunchedEffect(tourStep) {
            current = when (step.destination) {
                com.amyrmo.damavand.ui.onboarding.TourDestination.Home -> TopDestination.Home
                com.amyrmo.damavand.ui.onboarding.TourDestination.Servers -> TopDestination.Servers
                com.amyrmo.damavand.ui.onboarding.TourDestination.Settings -> TopDestination.Settings
            }
        }
        com.amyrmo.damavand.ui.onboarding.TourOverlay(
            step = step,
            index = tourStep,
            total = tourSteps.size,
            nextLabel = if (tourStep == tourSteps.lastIndex) {
                stringResource(Res.string.tour_done)
            } else {
                stringResource(Res.string.tour_next)
            },
            skipLabel = stringResource(Res.string.tour_skip),
            onNext = advance,
            onSkip = finish,

            onTargetMissing = advance,
        )
    }
    }
}

private fun languageEnglishName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "Persian"
    AppLanguage.Chinese -> "Chinese"
    AppLanguage.Russian -> "Russian"
    AppLanguage.English -> "English"

    AppLanguage.System -> "the language the user writes in"
}

private fun languageNativeName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "فارسی"
    AppLanguage.Chinese -> "中文"
    AppLanguage.Russian -> "Русский"
    AppLanguage.English -> "English"
    AppLanguage.System -> ""
}

private fun bentoEngineLabel(p: com.amyrmo.damavand.domain.config.VpnProfile): String =
    when (val src = p.source) {
        is com.amyrmo.damavand.domain.config.ProfileSource.SingBox,
        is com.amyrmo.damavand.domain.config.ProfileSource.SingBoxConfig -> "sing-box"
        is com.amyrmo.damavand.domain.config.ProfileSource.Link ->
            if (com.amyrmo.damavand.domain.config.SingBoxLinks.handles(src.link)) "sing-box" else "Xray"
        is com.amyrmo.damavand.domain.config.ProfileSource.Tor -> "Tor"
        is com.amyrmo.damavand.domain.config.ProfileSource.Psiphon -> "Psiphon"
        is com.amyrmo.damavand.domain.config.ProfileSource.Aether -> "Aether"
        is com.amyrmo.damavand.domain.config.ProfileSource.MasterDns -> "MasterDNS"
        is com.amyrmo.damavand.domain.config.ProfileSource.Ssh -> "SSH"
        is com.amyrmo.damavand.domain.config.ProfileSource.OpenConnect -> "OpenConnect"
        is com.amyrmo.damavand.domain.config.ProfileSource.Ikev2 -> "IKEv2"
        is com.amyrmo.damavand.domain.config.ProfileSource.SniSpoof -> "Xray · SNI"
        else -> "Xray"
    }
