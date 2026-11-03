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

/** Theme mode from the Appearance page (§5.2 Settings). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Language choice (§5.4): System follows the device, FA/EN force a locale. */
enum class AppLanguage { SYSTEM, FA, EN }

/** Connect-control style (§5.2 Appearance). PILL is the ConnectPill; SLIDE is the slide-to-connect variant. */
enum class ConnectStyle { PILL, SLIDE }

/**
 * The UI-facing settings slice (§5.2 Settings pages): appearance, language,
 * the connect-control style, and shell bookkeeping (the last profile the
 * user connected — the Home selection and the QS tile's target).
 */
data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    /** Forces the opaque rung of the glass ladder (§5.3). */
    val reduceTransparency: Boolean = false,
    /** Dock backdrop blur on/off (the fake-glass rung renders when off). */
    val glassBlur: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val connectStyle: ConnectStyle = ConnectStyle.PILL,
    /** Last profile connected (or chosen on Home); null before first use. */
    val lastProfileId: String? = null,
    /** The zero-config starter profiles have been seeded (§5.2). */
    val startersSeeded: Boolean = false,
)

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
 * Settings in DataStore Preferences (§3.3): the session-relevant
 * [AppSettings] slice plus the UI phase's [UiSettings] slice, in the same
 * store (one DataStore per file — the UI keys are separate and the two
 * update paths never touch each other's keys).
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

    val uiSettings: Flow<UiSettings> = context.settingsDataStore.data.map { it.toUiSettings() }

    suspend fun currentUi(): UiSettings = uiSettings.first()

    suspend fun updateUi(transform: (UiSettings) -> UiSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toUiSettings())
            prefs[UI_THEME_MODE] = next.themeMode.name
            prefs[UI_AMOLED] = next.amoled
            prefs[UI_REDUCE_TRANSPARENCY] = next.reduceTransparency
            prefs[UI_GLASS_BLUR] = next.glassBlur
            prefs[UI_LANGUAGE] = next.language.name
            prefs[UI_CONNECT_STYLE] = next.connectStyle.name
            next.lastProfileId?.let { prefs[UI_LAST_PROFILE] = it } ?: prefs.remove(UI_LAST_PROFILE)
            prefs[UI_STARTERS_SEEDED] = next.startersSeeded
        }
    }

    private fun Preferences.toUiSettings(): UiSettings = UiSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[UI_THEME_MODE].orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM),
        amoled = this[UI_AMOLED] ?: false,
        reduceTransparency = this[UI_REDUCE_TRANSPARENCY] ?: false,
        glassBlur = this[UI_GLASS_BLUR] ?: true,
        language = runCatching { AppLanguage.valueOf(this[UI_LANGUAGE].orEmpty()) }
            .getOrDefault(AppLanguage.SYSTEM),
        connectStyle = runCatching { ConnectStyle.valueOf(this[UI_CONNECT_STYLE].orEmpty()) }
            .getOrDefault(ConnectStyle.PILL),
        lastProfileId = this[UI_LAST_PROFILE],
        startersSeeded = this[UI_STARTERS_SEEDED] ?: false,
    )

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

        private val UI_THEME_MODE = stringPreferencesKey("ui_theme_mode")
        private val UI_AMOLED = booleanPreferencesKey("ui_amoled")
        private val UI_REDUCE_TRANSPARENCY = booleanPreferencesKey("ui_reduce_transparency")
        private val UI_GLASS_BLUR = booleanPreferencesKey("ui_glass_blur")
        private val UI_LANGUAGE = stringPreferencesKey("ui_language")
        private val UI_CONNECT_STYLE = stringPreferencesKey("ui_connect_style")
        private val UI_LAST_PROFILE = stringPreferencesKey("ui_last_profile")
        private val UI_STARTERS_SEEDED = booleanPreferencesKey("ui_starters_seeded")

        private const val MODE_ALL = "all"
        private const val MODE_INCLUDE = "include"
        private const val MODE_EXCLUDE = "exclude"
    }
}
