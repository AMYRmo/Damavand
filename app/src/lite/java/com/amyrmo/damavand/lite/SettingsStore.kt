package com.amyrmo.damavand.lite

import android.content.Context
import androidx.datastore.core.DataStore
import com.amyrmo.damavand.lite.aether.AetherKeysSettings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liteDataStore: DataStore<Preferences> by preferencesDataStore(name = "lite_settings")

/**
 * Lite settings (DataStore). Defaults follow the design spec:
 * kill switch OFF, auto-connect OFF, battery saver ON, subscription
 * auto-update ON, preferred core AUTO.
 */
class SettingsStore(private val context: Context) {

    companion object {
        val KEY_KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KEY_BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val KEY_SUB_AUTO_UPDATE = booleanPreferencesKey("sub_auto_update")
        val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val KEY_LAST_WINNER_LINK = stringPreferencesKey("last_winner_link")
        // 3.1.0 (PattNG): connection settings.
        val KEY_TEST_URL = stringPreferencesKey("test_url")
        val KEY_MUX = booleanPreferencesKey("mux")
        val KEY_SNIFFING = booleanPreferencesKey("sniffing")
        val KEY_FRAGMENT = booleanPreferencesKey("fragment")
        val KEY_DNS_SERVERS = stringPreferencesKey("dns_servers")
        val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        val KEY_PERAPP_MODE = stringPreferencesKey("perapp_mode")
        // 3.2.0 (PattNG parity): SOCKS / VPN / core / mux / DNS / misc.
        val KEY_SOCKS_PORT = stringPreferencesKey("socks_port")
        val KEY_SOCKS_USER = stringPreferencesKey("socks_user")
        val KEY_SOCKS_PASS = stringPreferencesKey("socks_pass")
        val KEY_SOCKS_UDP = booleanPreferencesKey("socks_udp")
        val KEY_SOCKS_SHARE_LAN = booleanPreferencesKey("socks_share_lan")
        val KEY_SOCKS_DYNAMIC_PORT = booleanPreferencesKey("socks_dynamic_port")
        val KEY_MTU = stringPreferencesKey("mtu")
        val KEY_IFACE_ADDR = stringPreferencesKey("iface_addr")
        val KEY_HTTP_PROXY = booleanPreferencesKey("http_proxy")
        val KEY_FAKE_DNS = booleanPreferencesKey("fake_dns")
        val KEY_LOCAL_DNS = booleanPreferencesKey("local_dns")
        val KEY_ROUTE_ONLY = booleanPreferencesKey("route_only")
        val KEY_CORE_LOGLEVEL = stringPreferencesKey("core_loglevel")
        val KEY_DOMAIN_STRATEGY = stringPreferencesKey("domain_strategy")
        val KEY_MUX_CONCURRENCY = stringPreferencesKey("mux_concurrency")
        val KEY_MUX_XUDP_CONCURRENCY = stringPreferencesKey("mux_xudp_concurrency")
        val KEY_MUX_XUDP_QUIC = booleanPreferencesKey("mux_xudp_quic")
        val KEY_DOMESTIC_DNS = stringPreferencesKey("domestic_dns")
        val KEY_DNS_HOSTS = stringPreferencesKey("dns_hosts")
        val KEY_REAL_PING_CONCURRENCY = stringPreferencesKey("real_ping_concurrency")
        val KEY_IP_API_URL = stringPreferencesKey("ip_api_url")
        val KEY_PROXY_ONLY = booleanPreferencesKey("proxy_only")
        val KEY_SHOW_SPEED_NOTIF = booleanPreferencesKey("show_speed_notif")
        val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val KEY_SHOW_ALL_GROUPS = booleanPreferencesKey("show_all_groups")
        val KEY_DOUBLE_COLUMN = booleanPreferencesKey("double_column")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val KEY_SUB_AUTO_TEST = booleanPreferencesKey("sub_auto_test")
        val KEY_SUB_AUTO_SORT = booleanPreferencesKey("sub_auto_sort")
        val KEY_SUB_AUTO_REMOVE_INVALID = booleanPreferencesKey("sub_auto_remove_invalid")
        val KEY_HEV_LOGLEVEL = stringPreferencesKey("hev_loglevel")
        val KEY_HEV_RW_TIMEOUT = stringPreferencesKey("hev_rw_timeout")
        val KEY_BALANCER_GROUPS = stringSetPreferencesKey("balancer_groups")
        // 3.2.8 (Auto, Damavand-faithful): the group selected in the
        // Servers tab (group key: "__all__" / "" / subscription URL),
        // shared with Home so fastest-connect tests that group's
        // configs; + the Auto tuning (Damavand's defaults) and the
        // last Auto pick per auto id (the "initial" member).
        val KEY_SELECTED_GROUP = stringPreferencesKey("selected_group")
        val KEY_AUTO_PROBE_URL = stringPreferencesKey("auto_probe_url")
        val KEY_AUTO_CHECK_SECONDS = stringPreferencesKey("auto_check_seconds")
        val KEY_AUTO_SWEEP_MINUTES = stringPreferencesKey("auto_sweep_minutes")
        val KEY_AUTO_SWITCH_MARGIN = stringPreferencesKey("auto_switch_margin")
        val KEY_AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val KEY_AUTO_LAST_PICK = stringPreferencesKey("auto_last_pick")
        // 3.2.9 (PattNG, the rest): dynamic color, local proxy, HEV tun.
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_LOCAL_PROXY = booleanPreferencesKey("local_proxy")
        val KEY_USE_HEV = booleanPreferencesKey("use_hev_tun")
        // 3.2.10 (PattNG root mode): system-wide routing via iptables + hev tun.
        val KEY_ROOT_MODE = booleanPreferencesKey("root_mode")
        val KEY_ROOT_LAN_SHARE = booleanPreferencesKey("root_lan_share")
        // 3.2.11 (PattNG full parity): IPv6 (PattNG PREF_IPV6_ENABLED,
        // default true) + prefer-IPv6 (default false) + the Aether
        // listen port (PattNG title_pref_aether_listen_port, 10819).
        val KEY_IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
        val KEY_PREFER_IPV6 = booleanPreferencesKey("prefer_ipv6")
        val KEY_AETHER_LISTEN_PORT = stringPreferencesKey("aether_listen_port")
        // 3.2.11 (PattNG full parity): the WARP keys page's settings,
        // one JSON string (PattNG keeps them in MMKV; Lite has none).
        val KEY_AETHER_KEYS_SETTINGS = stringPreferencesKey("aether_keys_settings")
        // 3.2.11 (PattNG full parity): the URL each asset file downloads
        // from (PattNG UserAssetUrlActivity); blank means the built-in one.
        val KEY_ASSET_GEOIP_URL = stringPreferencesKey("asset_geoip_url")
        val KEY_ASSET_GEOSITE_URL = stringPreferencesKey("asset_geosite_url")
        val KEY_ASSET_PSIPHON_URL = stringPreferencesKey("asset_psiphon_url")
    }

    val killSwitch: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_KILL_SWITCH] ?: false }
    val autoConnect: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_AUTO_CONNECT] ?: false }
    val batterySaver: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_BATTERY_SAVER] ?: true }
    val subAutoUpdate: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SUB_AUTO_UPDATE] ?: true }
    val selectedProfileId: Flow<String?> = context.liteDataStore.data.map { it[KEY_SELECTED_PROFILE_ID] }
    val lastWinnerLink: Flow<String?> = context.liteDataStore.data.map { it[KEY_LAST_WINNER_LINK] }
    // 3.1.0.
    val testUrl: Flow<String> = context.liteDataStore.data.map {
        it[KEY_TEST_URL] ?: "https://www.google.com/generate_204"
    }
    val mux: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_MUX] ?: false }
    val sniffing: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SNIFFING] ?: true }
    val fragment: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_FRAGMENT] ?: false }
    val dnsServers: Flow<String> = context.liteDataStore.data.map { it[KEY_DNS_SERVERS] ?: "8.8.8.8,1.1.1.1" }
    val routingMode: Flow<String> = context.liteDataStore.data.map { it[KEY_ROUTING_MODE] ?: "bypass_lan" }
    val perAppMode: Flow<String> = context.liteDataStore.data.map { it[KEY_PERAPP_MODE] ?: "bypass" }
    // 3.2.0.
    val socksPort: Flow<String> = context.liteDataStore.data.map { it[KEY_SOCKS_PORT] ?: "1080" }
    val socksUser: Flow<String> = context.liteDataStore.data.map { it[KEY_SOCKS_USER] ?: "" }
    val socksPass: Flow<String> = context.liteDataStore.data.map { it[KEY_SOCKS_PASS] ?: "" }
    val socksUdp: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SOCKS_UDP] ?: true }
    val socksShareLan: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SOCKS_SHARE_LAN] ?: false }
    val socksDynamicPort: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SOCKS_DYNAMIC_PORT] ?: false }
    val mtu: Flow<String> = context.liteDataStore.data.map { it[KEY_MTU] ?: "1500" }
    val ifaceAddr: Flow<String> = context.liteDataStore.data.map { it[KEY_IFACE_ADDR] ?: "10.8.0.2" }
    val httpProxy: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_HTTP_PROXY] ?: false }
    val fakeDns: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_FAKE_DNS] ?: false }
    val localDns: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_LOCAL_DNS] ?: true }
    val routeOnly: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_ROUTE_ONLY] ?: false }
    val coreLogLevel: Flow<String> = context.liteDataStore.data.map { it[KEY_CORE_LOGLEVEL] ?: "warning" }
    val domainStrategy: Flow<String> = context.liteDataStore.data.map { it[KEY_DOMAIN_STRATEGY] ?: "IPIfNonMatch" }
    val muxConcurrency: Flow<String> = context.liteDataStore.data.map { it[KEY_MUX_CONCURRENCY] ?: "8" }
    val muxXudpConcurrency: Flow<String> = context.liteDataStore.data.map { it[KEY_MUX_XUDP_CONCURRENCY] ?: "8" }
    val muxXudpQuic: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_MUX_XUDP_QUIC] ?: false }
    val domesticDns: Flow<String> = context.liteDataStore.data.map { it[KEY_DOMESTIC_DNS] ?: "" }
    val dnsHosts: Flow<String> = context.liteDataStore.data.map { it[KEY_DNS_HOSTS] ?: "" }
    val realPingConcurrency: Flow<String> = context.liteDataStore.data.map { it[KEY_REAL_PING_CONCURRENCY] ?: "5" }
    val ipApiUrl: Flow<String> = context.liteDataStore.data.map { it[KEY_IP_API_URL] ?: "https://ipinfo.io/json" }
    val proxyOnly: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_PROXY_ONLY] ?: false }
    val showSpeedNotif: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SHOW_SPEED_NOTIF] ?: true }
    val confirmDelete: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_CONFIRM_DELETE] ?: true }
    val showAllGroups: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SHOW_ALL_GROUPS] ?: false }
    // 3.2.7 (PattNG): two-column server list, off by default so the
    // Summit UI stays exactly as it is unless the user turns it on.
    val doubleColumn: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_DOUBLE_COLUMN] ?: false }

    // 3.2.0: app language — "auto" (system), "fa" or "en".
    val language: Flow<String> = context.liteDataStore.data.map { it[KEY_LANGUAGE] ?: "auto" }
    // 3.2.5: day/night theme — "system" (follow the device), "dark" or "light".
    val themeMode: Flow<String> = context.liteDataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val autoStartBoot: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_AUTO_START_BOOT] ?: false }
    val subAutoTest: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SUB_AUTO_TEST] ?: true }
    val subAutoSort: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SUB_AUTO_SORT] ?: false }
    val subAutoRemoveInvalid: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SUB_AUTO_REMOVE_INVALID] ?: false }
    val hevLogLevel: Flow<String> = context.liteDataStore.data.map { it[KEY_HEV_LOGLEVEL] ?: "warning" }
    val hevRwTimeout: Flow<String> = context.liteDataStore.data.map { it[KEY_HEV_RW_TIMEOUT] ?: "0" }
    // 3.2.0 (PattNG): groups flagged as leastPing balancers (keys are
    // subscription URLs; "" is the manual group).
    val balancerGroups: Flow<Set<String>> =
        context.liteDataStore.data.map { it[KEY_BALANCER_GROUPS] ?: emptySet() }

    // 3.2.8 (Auto): the selected group, shared by Servers + Home.
    val selectedGroup: Flow<String?> =
        context.liteDataStore.data.map { it[KEY_SELECTED_GROUP] }
    // 3.2.8 (Auto tuning — Damavand's defaults: check 30 s, sweep
    // 10 min, switch margin 30%, retry on).
    val autoProbeUrl: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AUTO_PROBE_URL] ?: "" }
    val autoCheckSeconds: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AUTO_CHECK_SECONDS] ?: "30" }
    val autoSweepMinutes: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AUTO_SWEEP_MINUTES] ?: "10" }
    val autoSwitchMargin: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AUTO_SWITCH_MARGIN] ?: "30" }
    val autoRetry: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_AUTO_RETRY] ?: true }

    // 3.2.9 (PattNG): dynamic color (off — Damavand Red stays the
    // default), local proxy (on — the SOCKS inbound other apps use),
    // HEV tunnel (on — Lite's current tun path; off is Xray's own
    // tun inbound reading the VPN fd).
    val dynamicColor: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }
    val localProxy: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_LOCAL_PROXY] ?: true }
    val useHev: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_USE_HEV] ?: true }
    // 3.2.10 (PattNG root mode): off by default — needs a rooted phone.
    val rootMode: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_ROOT_MODE] ?: false }
    // 3.2.11 (PattNG full parity): IPv6 on by default (PattNG's
    // DEFAULT_IPV6_ENABLED), prefer-IPv6 off, Aether listen port 10819.
    val ipv6Enabled: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_IPV6_ENABLED] ?: true }
    val preferIpv6: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_PREFER_IPV6] ?: false }
    val aetherListenPort: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AETHER_LISTEN_PORT] ?: "10819" }
    val aetherKeysSettings: Flow<String> =
        context.liteDataStore.data.map { it[KEY_AETHER_KEYS_SETTINGS] ?: "" }
    val assetGeoipUrl: Flow<String> =
        context.liteDataStore.data.map { it[KEY_ASSET_GEOIP_URL] ?: "" }
    val assetGeositeUrl: Flow<String> =
        context.liteDataStore.data.map { it[KEY_ASSET_GEOSITE_URL] ?: "" }
    val assetPsiphonUrl: Flow<String> =
        context.liteDataStore.data.map { it[KEY_ASSET_PSIPHON_URL] ?: "" }
    val rootLanShare: Flow<Boolean> =
        context.liteDataStore.data.map { it[KEY_ROOT_LAN_SHARE] ?: false }

    suspend fun setKillSwitch(v: Boolean) = context.liteDataStore.edit { it[KEY_KILL_SWITCH] = v }
    suspend fun setAutoConnect(v: Boolean) = context.liteDataStore.edit { it[KEY_AUTO_CONNECT] = v }
    suspend fun setBatterySaver(v: Boolean) = context.liteDataStore.edit { it[KEY_BATTERY_SAVER] = v }
    suspend fun setSubAutoUpdate(v: Boolean) = context.liteDataStore.edit { it[KEY_SUB_AUTO_UPDATE] = v }
    suspend fun setSelectedProfileId(v: String?) {
        context.liteDataStore.edit {
            if (v == null) it.remove(KEY_SELECTED_PROFILE_ID) else it[KEY_SELECTED_PROFILE_ID] = v
        }
    }
    suspend fun setLastWinnerLink(v: String?) {
        context.liteDataStore.edit {
            if (v == null) it.remove(KEY_LAST_WINNER_LINK) else it[KEY_LAST_WINNER_LINK] = v
        }
    }

    // 3.1.0 (PattNG): connection settings.
    suspend fun setTestUrl(v: String) = context.liteDataStore.edit { it[KEY_TEST_URL] = v }
    suspend fun setMux(v: Boolean) = context.liteDataStore.edit { it[KEY_MUX] = v }
    suspend fun setSniffing(v: Boolean) = context.liteDataStore.edit { it[KEY_SNIFFING] = v }
    suspend fun setFragment(v: Boolean) = context.liteDataStore.edit { it[KEY_FRAGMENT] = v }
    suspend fun setDnsServers(v: String) = context.liteDataStore.edit { it[KEY_DNS_SERVERS] = v }
    suspend fun setRoutingMode(v: String) = context.liteDataStore.edit { it[KEY_ROUTING_MODE] = v }
    suspend fun setPerAppMode(v: String) = context.liteDataStore.edit { it[KEY_PERAPP_MODE] = v }

    // 3.2.0 (PattNG parity).
    suspend fun setSocksPort(v: String) = context.liteDataStore.edit { it[KEY_SOCKS_PORT] = v }
    suspend fun setSocksUser(v: String) = context.liteDataStore.edit { it[KEY_SOCKS_USER] = v }
    suspend fun setSocksPass(v: String) = context.liteDataStore.edit { it[KEY_SOCKS_PASS] = v }
    suspend fun setSocksUdp(v: Boolean) = context.liteDataStore.edit { it[KEY_SOCKS_UDP] = v }
    suspend fun setSocksShareLan(v: Boolean) = context.liteDataStore.edit { it[KEY_SOCKS_SHARE_LAN] = v }
    suspend fun setSocksDynamicPort(v: Boolean) = context.liteDataStore.edit { it[KEY_SOCKS_DYNAMIC_PORT] = v }
    suspend fun setMtu(v: String) = context.liteDataStore.edit { it[KEY_MTU] = v }
    suspend fun setIfaceAddr(v: String) = context.liteDataStore.edit { it[KEY_IFACE_ADDR] = v }
    suspend fun setHttpProxy(v: Boolean) = context.liteDataStore.edit { it[KEY_HTTP_PROXY] = v }
    suspend fun setFakeDns(v: Boolean) = context.liteDataStore.edit { it[KEY_FAKE_DNS] = v }
    suspend fun setLocalDns(v: Boolean) = context.liteDataStore.edit { it[KEY_LOCAL_DNS] = v }
    suspend fun setRouteOnly(v: Boolean) = context.liteDataStore.edit { it[KEY_ROUTE_ONLY] = v }
    suspend fun setCoreLogLevel(v: String) = context.liteDataStore.edit { it[KEY_CORE_LOGLEVEL] = v }
    suspend fun setDomainStrategy(v: String) = context.liteDataStore.edit { it[KEY_DOMAIN_STRATEGY] = v }
    suspend fun setMuxConcurrency(v: String) = context.liteDataStore.edit { it[KEY_MUX_CONCURRENCY] = v }
    suspend fun setMuxXudpConcurrency(v: String) = context.liteDataStore.edit { it[KEY_MUX_XUDP_CONCURRENCY] = v }
    suspend fun setMuxXudpQuic(v: Boolean) = context.liteDataStore.edit { it[KEY_MUX_XUDP_QUIC] = v }
    suspend fun setDomesticDns(v: String) = context.liteDataStore.edit { it[KEY_DOMESTIC_DNS] = v }
    suspend fun setDnsHosts(v: String) = context.liteDataStore.edit { it[KEY_DNS_HOSTS] = v }
    suspend fun setRealPingConcurrency(v: String) = context.liteDataStore.edit { it[KEY_REAL_PING_CONCURRENCY] = v }
    suspend fun setIpApiUrl(v: String) = context.liteDataStore.edit { it[KEY_IP_API_URL] = v }
    suspend fun setProxyOnly(v: Boolean) = context.liteDataStore.edit { it[KEY_PROXY_ONLY] = v }
    suspend fun setShowSpeedNotif(v: Boolean) = context.liteDataStore.edit { it[KEY_SHOW_SPEED_NOTIF] = v }
    suspend fun setConfirmDelete(v: Boolean) = context.liteDataStore.edit { it[KEY_CONFIRM_DELETE] = v }
    suspend fun setShowAllGroups(v: Boolean) = context.liteDataStore.edit { it[KEY_SHOW_ALL_GROUPS] = v }
    suspend fun setDoubleColumn(v: Boolean) = context.liteDataStore.edit { it[KEY_DOUBLE_COLUMN] = v }

    suspend fun setLanguage(v: String) = context.liteDataStore.edit { it[KEY_LANGUAGE] = v }
    suspend fun setThemeMode(v: String) = context.liteDataStore.edit { it[KEY_THEME_MODE] = v }
    suspend fun setAutoStartBoot(v: Boolean) = context.liteDataStore.edit { it[KEY_AUTO_START_BOOT] = v }
    suspend fun setSubAutoTest(v: Boolean) = context.liteDataStore.edit { it[KEY_SUB_AUTO_TEST] = v }
    suspend fun setSubAutoSort(v: Boolean) = context.liteDataStore.edit { it[KEY_SUB_AUTO_SORT] = v }
    suspend fun setSubAutoRemoveInvalid(v: Boolean) = context.liteDataStore.edit { it[KEY_SUB_AUTO_REMOVE_INVALID] = v }
    suspend fun setHevLogLevel(v: String) = context.liteDataStore.edit { it[KEY_HEV_LOGLEVEL] = v }
    suspend fun setHevRwTimeout(v: String) = context.liteDataStore.edit { it[KEY_HEV_RW_TIMEOUT] = v }
    suspend fun setBalancerGroup(key: String, on: Boolean) {
        context.liteDataStore.edit {
            val cur = (it[KEY_BALANCER_GROUPS] ?: emptySet()).toMutableSet()
            if (on) cur.add(key) else cur.remove(key)
            it[KEY_BALANCER_GROUPS] = cur
        }
    }

    // 3.2.8 (Auto).
    suspend fun setSelectedGroup(v: String?) {
        context.liteDataStore.edit {
            if (v == null) it.remove(KEY_SELECTED_GROUP) else it[KEY_SELECTED_GROUP] = v
        }
    }

    suspend fun setAutoProbeUrl(v: String) = context.liteDataStore.edit { it[KEY_AUTO_PROBE_URL] = v }
    suspend fun setAutoCheckSeconds(v: String) = context.liteDataStore.edit { it[KEY_AUTO_CHECK_SECONDS] = v }
    suspend fun setAutoSweepMinutes(v: String) = context.liteDataStore.edit { it[KEY_AUTO_SWEEP_MINUTES] = v }
    suspend fun setAutoSwitchMargin(v: String) = context.liteDataStore.edit { it[KEY_AUTO_SWITCH_MARGIN] = v }
    suspend fun setAutoRetry(v: Boolean) = context.liteDataStore.edit { it[KEY_AUTO_RETRY] = v }
    // 3.2.9 (PattNG, the rest).
    suspend fun setDynamicColor(v: Boolean) = context.liteDataStore.edit { it[KEY_DYNAMIC_COLOR] = v }
    suspend fun setLocalProxy(v: Boolean) = context.liteDataStore.edit { it[KEY_LOCAL_PROXY] = v }
    suspend fun setUseHev(v: Boolean) = context.liteDataStore.edit { it[KEY_USE_HEV] = v }
    // 3.2.10 (PattNG root mode).
    suspend fun setRootMode(v: Boolean) = context.liteDataStore.edit { it[KEY_ROOT_MODE] = v }
    suspend fun setRootLanShare(v: Boolean) = context.liteDataStore.edit { it[KEY_ROOT_LAN_SHARE] = v }
    // 3.2.11 (PattNG full parity).
    suspend fun setIpv6Enabled(v: Boolean) = context.liteDataStore.edit { it[KEY_IPV6_ENABLED] = v }
    suspend fun setPreferIpv6(v: Boolean) = context.liteDataStore.edit { it[KEY_PREFER_IPV6] = v }
    suspend fun setAetherListenPort(v: String) = context.liteDataStore.edit { it[KEY_AETHER_LISTEN_PORT] = v }
    suspend fun setAetherKeysSettings(v: String) = context.liteDataStore.edit { it[KEY_AETHER_KEYS_SETTINGS] = v }
    suspend fun setAssetGeoipUrl(v: String) = context.liteDataStore.edit { it[KEY_ASSET_GEOIP_URL] = v }
    suspend fun setAssetGeositeUrl(v: String) = context.liteDataStore.edit { it[KEY_ASSET_GEOSITE_URL] = v }
    suspend fun setAssetPsiphonUrl(v: String) = context.liteDataStore.edit { it[KEY_ASSET_PSIPHON_URL] = v }
    suspend fun rootModeNow(): Boolean = rootMode.first()
    suspend fun rootLanShareNow(): Boolean = rootLanShare.first()
    suspend fun socksUserNow(): String = socksUser.first()
    suspend fun socksPassNow(): String = socksPass.first()

    /** Remember the Auto pick for one auto id (the "initial" member). */
    suspend fun setAutoLastPick(autoId: String, profileId: String) {
        context.liteDataStore.edit {
            val cur = try {
                org.json.JSONObject(it[KEY_AUTO_LAST_PICK] ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            cur.put(autoId, profileId)
            it[KEY_AUTO_LAST_PICK] = cur.toString()
        }
    }

    // 3.2.7: bulk restore for backups.
    suspend fun setBalancerGroups(keys: Set<String>) =
        context.liteDataStore.edit { it[KEY_BALANCER_GROUPS] = keys }

    suspend fun killSwitchNow(): Boolean = killSwitch.first()
    suspend fun autoConnectNow(): Boolean = autoConnect.first()
    suspend fun batterySaverNow(): Boolean = batterySaver.first()
    suspend fun subAutoUpdateNow(): Boolean = subAutoUpdate.first()
    suspend fun selectedProfileIdNow(): String? = selectedProfileId.first()
    suspend fun testUrlNow(): String = testUrl.first()
    suspend fun dnsServersNow(): String = dnsServers.first()
    // 3.2.0.
    suspend fun socksPortNow(): Int = socksPort.first().toIntOrNull()?.coerceIn(1, 65535) ?: 1080
    suspend fun mtuNow(): Int = mtu.first().toIntOrNull()?.coerceIn(576, 9000) ?: 1500
    suspend fun ifaceAddrNow(): String = ifaceAddr.first().trim().ifEmpty { "10.8.0.2" }
    suspend fun proxyOnlyNow(): Boolean = proxyOnly.first()
    suspend fun socksShareLanNow(): Boolean = socksShareLan.first()
    suspend fun socksDynamicPortNow(): Boolean = socksDynamicPort.first()
    suspend fun localDnsNow(): Boolean = localDns.first()
    suspend fun ipApiUrlNow(): String = ipApiUrl.first().trim().ifEmpty { "https://ipinfo.io/json" }
    suspend fun realPingConcurrencyNow(): Int =
        realPingConcurrency.first().toIntOrNull()?.coerceIn(1, 20) ?: 5
    suspend fun subAutoTestNow(): Boolean = subAutoTest.first()
    suspend fun subAutoSortNow(): Boolean = subAutoSort.first()
    suspend fun subAutoRemoveInvalidNow(): Boolean = subAutoRemoveInvalid.first()
    suspend fun autoStartBootNow(): Boolean = autoStartBoot.first()
    suspend fun hevLogLevelNow(): String = hevLogLevel.first()
    suspend fun hevRwTimeoutNow(): String = hevRwTimeout.first()
    // 3.2.9 (PattNG, the rest).
    suspend fun dynamicColorNow(): Boolean = dynamicColor.first()
    suspend fun localProxyNow(): Boolean = localProxy.first()
    suspend fun useHevNow(): Boolean = useHev.first()
    // 3.2.11 (PattNG full parity).
    suspend fun ipv6EnabledNow(): Boolean = ipv6Enabled.first()
    suspend fun preferIpv6Now(): Boolean = preferIpv6.first()
    suspend fun aetherListenPortNow(): Int =
        aetherListenPort.first().toIntOrNull()?.coerceIn(1024, 65535) ?: 10819
    suspend fun aetherKeysSettingsNow(): AetherKeysSettings =
        AetherKeysSettings.fromJson(aetherKeysSettings.first()) ?: AetherKeysSettings()
    suspend fun showSpeedNotifNow(): Boolean = showSpeedNotif.first()
    suspend fun confirmDeleteNow(): Boolean = confirmDelete.first()
    suspend fun showAllGroupsNow(): Boolean = showAllGroups.first()

    suspend fun selectedGroupNow(): String? = selectedGroup.first()

    suspend fun autoLastPickNow(autoId: String): String? {
        val raw = context.liteDataStore.data.first()[KEY_AUTO_LAST_PICK] ?: return null
        return try {
            org.json.JSONObject(raw).optString(autoId).takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** The Auto tuning snapshot for one connection build. */
    suspend fun autoTuningNow(): AutoSelectTuning = AutoSelectTuning(
        probeUrl = autoProbeUrl.first().trim(),
        activeIntervalSec = autoCheckSeconds.first().toIntOrNull()?.coerceIn(10, 600) ?: 30,
        sweepIntervalMin = autoSweepMinutes.first().toIntOrNull()?.coerceIn(1, 120) ?: 10,
        switchMarginPercent = autoSwitchMargin.first().toIntOrNull()?.coerceIn(5, 90) ?: 30,
        retry = autoRetry.first(),
    )

    suspend fun languageNow(): String = language.first()

    suspend fun themeModeNow(): String = themeMode.first()

    /** The full Xray settings snapshot for one connection build. */
    suspend fun xraySettingsNow(routingStore: RoutingStore): XrayConfig.XraySettings {
        val routing = routingStore.loadRouting()
        val hosts = dnsHosts.first().split(",").mapNotNull { entry ->
            val parts = entry.split("=").map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
        }.toMap()
        return XrayConfig.XraySettings(
            muxEnabled = mux.first(),
            sniffingEnabled = sniffing.first(),
            fragmentEnabled = fragment.first(),
            dnsServers = dnsServers.first().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("8.8.8.8", "1.1.1.1") },
            routingMode = routingMode.first(),
            customRules = routing.rules,
            socksPort = socksPortNow(),
            socksUser = socksUser.first().trim(),
            socksPass = socksPass.first(),
            socksUdp = socksUdp.first(),
            socksShareLan = socksShareLan.first(),
            httpProxyEnabled = httpProxy.first(),
            fakeDnsEnabled = fakeDns.first(),
            routeOnly = routeOnly.first(),
            coreLogLevel = coreLogLevel.first(),
            domainStrategy = domainStrategy.first(),
            muxConcurrency = muxConcurrency.first().toIntOrNull()?.coerceIn(1, 1024) ?: 8,
            muxXudpConcurrency = muxXudpConcurrency.first().toIntOrNull()?.coerceIn(1, 1024) ?: 8,
            muxXudpQuic = muxXudpQuic.first(),
            domesticDns = domesticDns.first().trim(),
            dnsHosts = hosts,
            mtu = mtuNow(),
            ifaceAddr = ifaceAddrNow(),
            // 3.2.11 (PattNG full parity): IPv6 gates the WG IPv6
            // addresses + the tun's IPv6 side (CoreOutboundBuilder).
            ipv6Enabled = ipv6EnabledNow(),
        )
    }
}
