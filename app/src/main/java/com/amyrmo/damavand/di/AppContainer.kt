package com.amyrmo.damavand.di

import android.content.Context
import com.amyrmo.damavand.BuildConfig
import com.amyrmo.damavand.core.CoreProbe
import com.amyrmo.damavand.core.LogcatTail
import com.amyrmo.damavand.core.VpnManager
import com.amyrmo.damavand.core.XrayController
import com.amyrmo.damavand.data.assets.GeoAssetsRepository
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.platform.AndroidKeyValueStore
import com.amyrmo.damavand.platform.AppInfo
import com.amyrmo.damavand.platform.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val configRepository: ConfigRepository = ConfigRepository(AndroidKeyValueStore(context, "zed_configs"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        AppInfo.versionName = BuildConfig.VERSION_NAME

        DeviceIdentity.id = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull().orEmpty()

        com.amyrmo.damavand.data.net.AndroidMtuProbe.install(context)

        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        VpnManager.deviceVpnProbe = { connectivity != null && anyVpnNetwork(connectivity) }
        CoreProbe.measureDelay = { url -> XrayController.measureDelay(url) }
        CoreProbe.measureDelayDetailed = { url -> XrayController.measureDelayDetailed(url) }
        CoreProbe.measureOutboundDelay = { cfg, url ->
            runCatching { Libv2ray.measureOutboundDelay(cfg, url) }.getOrDefault(-1L)
        }

        com.amyrmo.damavand.core.AutoSelect.readStatus = {
            runCatching { Libv2ray.autoSelectStatus() }.getOrNull()
        }
        com.amyrmo.damavand.core.AutoSelect.pinMember = { group, member ->
            runCatching { Libv2ray.autoSelectPin(group, member) }.getOrDefault(false)
        }
        com.amyrmo.damavand.core.AutoSelect.networkChanged = {
            runCatching { Libv2ray.autoSelectNetworkChanged() }
        }
        com.amyrmo.damavand.core.AutoSelect.supportsPinning = true
        TrafficAccountant(configRepository, scope)

        LogcatTail.register(scope)

        scope.launch {
            runCatching { GeoAssetsRepository(context).seedBundled(BuildConfig.VERSION_CODE.toLong()) }
            com.amyrmo.damavand.data.assets.GeoAutoFetch.start(scope, GeoAssetsRepository(context)) { settingsRepository.settings.value }
        }

        runCatching {
            com.amyrmo.damavand.domain.config.RoutingMigration
                .seedIfFresh(settingsRepository.settings.value)
                ?.let { seeded -> settingsRepository.update { seeded } }
        }

        scope.launch {
            combine(settingsRepository.settings, configRepository.subscriptions) { s, subs -> s to subs }
                .distinctUntilChangedBy { (s, subs) ->
                    Triple(
                        s.autoUpdateSubscriptions,
                        com.amyrmo.damavand.domain.config.SubscriptionSchedule
                            .periodHours(subs, s.subscriptionUpdateIntervalHours),
                        subs.any { it.enabled },
                    )
                }
                .collect { (s, subs) ->
                    runCatching {
                        com.amyrmo.damavand.core.SubscriptionUpdateScheduler.sync(context, s, subs)
                    }
                }
        }

        scope.launch {
            var counted = -1
            VpnManager.status.collect { s ->
                if (s.state == ConnectionState.Connected && s.sessionId != counted) {
                    counted = s.sessionId
                    settingsRepository.update { it.copy(successfulConnections = it.successfulConnections + 1) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun anyVpnNetwork(connectivity: android.net.ConnectivityManager): Boolean =
    connectivity.allNetworks.any { network ->
        connectivity.getNetworkCapabilities(network)
            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }

private class TrafficAccountant(
    private val repository: ConfigRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            var session = -1
            var lastDown = 0L
            var lastUp = 0L
            var activeId: String? = null

            VpnManager.status.collect { s ->
                when (s.state) {
                    ConnectionState.Connected -> {
                        if (s.sessionId != session) {
                            session = s.sessionId
                            lastDown = 0
                            lastUp = 0
                            activeId = repository.activeId.value
                        }

                        val autoMember = com.amyrmo.damavand.core.AutoSelect.session.value
                            ?.takeIf { it.profileId == activeId }?.selectedProfileId
                        val id = autoMember ?: activeId ?: return@collect
                        val dDown = (s.totalDownload - lastDown).coerceAtLeast(0)
                        val dUp = (s.totalUpload - lastUp).coerceAtLeast(0)
                        if (dDown > 0 || dUp > 0) {
                            repository.addUsage(id, dDown, dUp, persist = false)
                            lastDown = s.totalDownload
                            lastUp = s.totalUpload
                        }
                    }
                    ConnectionState.Idle, ConnectionState.Error -> {
                        activeId?.let { repository.addUsage(it, 0, 0, persist = true) }
                        activeId = null
                        session = -1
                        lastDown = 0
                        lastUp = 0
                    }
                    else -> Unit
                }
            }
        }
    }
}
