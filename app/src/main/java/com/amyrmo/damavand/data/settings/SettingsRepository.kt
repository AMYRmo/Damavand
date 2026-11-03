package com.amyrmo.damavand.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amyrmo.damavand.logic.BridgeConfigBuilder
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.logic.PlanSettings
import com.amyrmo.damavand.logic.RoutingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The app settings the session layer consumes (architecture §3.3). */
data class AppSettings(
    /** Fallback session DNS when the exit profile carries none (§4.6). */
    val sessionDns: String = "",
    val routing: RoutingMode = RoutingMode.GLOBAL,
    val perApp: PerAppSelection = PerAppSelection.All,
    val proxyOnly: Boolean = false,
    /** Bridge TUN device options (§2.2, carried into PlanSettings.device). */
    val device: BridgeConfigBuilder.DeviceOptions = BridgeConfigBuilder.DeviceOptions(),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings in DataStore Preferences (§3.3). Only the session-relevant
 * slice lives here for the runtime layer; theme/locale/glass options join
 * it in the UI phase.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    /** The planner input (§4.5 step 1) distilled from the stored settings. */
    suspend fun planSettings(): PlanSettings {
        val s = current()
        return PlanSettings(
            sessionDns = s.sessionDns,
            routing = s.routing,
            perApp = s.perApp,
            proxyOnly = s.proxyOnly,
            device = s.device,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toAppSettings())
            prefs[SESSION_DNS] = next.sessionDns
            prefs[ROUTING] = next.routing.name
            prefs[PROXY_ONLY] = next.proxyOnly
            when (val perApp = next.perApp) {
                PerAppSelection.All -> {
                    prefs[PER_APP_MODE] = MODE_ALL
                    prefs.remove(PER_APP_PACKAGES)
                }
                is PerAppSelection.Include -> {
                    prefs[PER_APP_MODE] = MODE_INCLUDE
                    prefs[PER_APP_PACKAGES] = perApp.packages
                }
                is PerAppSelection.Exclude -> {
                    prefs[PER_APP_MODE] = MODE_EXCLUDE
                    prefs[PER_APP_PACKAGES] = perApp.packages
                }
            }
            prefs[TUN_ADDRESS] = next.device.tunAddress
            prefs[TUN_MTU] = next.device.mtu
            prefs[TUN_STACK] = next.device.stack
            prefs[TUN_AUTO_ROUTE] = next.device.autoRoute
            prefs[TUN_STRICT_ROUTE] = next.device.strictRoute
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val packages = this[PER_APP_PACKAGES].orEmpty()
        val perApp = when (this[PER_APP_MODE]) {
            MODE_INCLUDE -> PerAppSelection.Include(packages)
            MODE_EXCLUDE -> PerAppSelection.Exclude(packages)
            else -> PerAppSelection.All
        }
        return AppSettings(
            sessionDns = this[SESSION_DNS].orEmpty(),
            routing = runCatching { RoutingMode.valueOf(this[ROUTING].orEmpty()) }
                .getOrDefault(RoutingMode.GLOBAL),
            perApp = perApp,
            proxyOnly = this[PROXY_ONLY] ?: false,
            device = BridgeConfigBuilder.DeviceOptions(
                tunAddress = this[TUN_ADDRESS]?.takeIf { it.isNotBlank() }
                    ?: BridgeConfigBuilder.DeviceOptions().tunAddress,
                mtu = this[TUN_MTU] ?: BridgeConfigBuilder.DeviceOptions().mtu,
                stack = this[TUN_STACK]?.takeIf { it.isNotBlank() }
                    ?: BridgeConfigBuilder.DeviceOptions().stack,
                autoRoute = this[TUN_AUTO_ROUTE] ?: true,
                strictRoute = this[TUN_STRICT_ROUTE] ?: false,
            ),
        )
    }

    companion object {
        private val SESSION_DNS = stringPreferencesKey("session_dns")
        private val ROUTING = stringPreferencesKey("routing")
        private val PROXY_ONLY = booleanPreferencesKey("proxy_only")
        private val PER_APP_MODE = stringPreferencesKey("per_app_mode")
        private val PER_APP_PACKAGES = stringSetPreferencesKey("per_app_packages")
        private val TUN_ADDRESS = stringPreferencesKey("tun_address")
        private val TUN_MTU = intPreferencesKey("tun_mtu")
        private val TUN_STACK = stringPreferencesKey("tun_stack")
        private val TUN_AUTO_ROUTE = booleanPreferencesKey("tun_auto_route")
        private val TUN_STRICT_ROUTE = booleanPreferencesKey("tun_strict_route")

        private const val MODE_ALL = "all"
        private const val MODE_INCLUDE = "include"
        private const val MODE_EXCLUDE = "exclude"
    }
}
