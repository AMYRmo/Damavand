package com.amyrmo.damavand.lite

import android.content.Context
import androidx.datastore.core.DataStore
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
    suspend fun showSpeedNotifNow(): Boolean = showSpeedNotif.first()
    suspend fun confirmDeleteNow(): Boolean = confirmDelete.first()
    suspend fun showAllGroupsNow(): Boolean = showAllGroups.first()

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
        )
    }
}
