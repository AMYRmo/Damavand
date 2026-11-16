package com.amyrmo.damavand.lite

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup & restore (3.1.0, PattNG): one JSON bundle holding the
 * profiles, chains, subscriptions, routing, per-app list and the
 * connection settings. Export writes it to a user-chosen file;
 * import replaces the app's data with it. Settings are applied
 * through the DataStore; a missing key keeps its current value.
 */
object BackupManager {

    private const val VERSION = 1

    /** Build the bundle JSON from the app's current data. */
    fun exportJson(context: Context): String {
        val filesDir = context.filesDir
        fun read(name: String): JSONArray {
            return try {
                val f = File(filesDir, name)
                if (!f.exists()) JSONArray() else JSONArray(f.readText())
            } catch (_: Exception) {
                JSONArray()
            }
        }
        fun readObj(name: String): JSONObject {
            return try {
                val f = File(filesDir, name)
                if (!f.exists()) JSONObject() else JSONObject(f.readText())
            } catch (_: Exception) {
                JSONObject()
            }
        }
        val settings = JSONObject()
        runBlocking {
            val store = SettingsStore(context)
            settings.put("kill_switch", store.killSwitchNow())
            settings.put("auto_connect", store.autoConnectNow())
            settings.put("battery_saver", store.batterySaverNow())
            settings.put("sub_auto_update", store.subAutoUpdateNow())
            settings.put("test_url", store.testUrlNow())
            settings.put("dns_servers", store.dnsServersNow())
            // 3.2.0: every new setting, so a restore is complete.
            settings.put("socks_port", store.socksPort.first())
            settings.put("socks_user", store.socksUser.first())
            settings.put("socks_pass", store.socksPass.first())
            settings.put("socks_udp", store.socksUdp.first())
            settings.put("socks_share_lan", store.socksShareLan.first())
            settings.put("socks_dynamic_port", store.socksDynamicPort.first())
            settings.put("mtu", store.mtu.first())
            settings.put("iface_addr", store.ifaceAddr.first())
            settings.put("http_proxy", store.httpProxy.first())
            settings.put("fake_dns", store.fakeDns.first())
            settings.put("local_dns", store.localDns.first())
            settings.put("route_only", store.routeOnly.first())
            settings.put("core_loglevel", store.coreLogLevel.first())
            settings.put("domain_strategy", store.domainStrategy.first())
            settings.put("mux_concurrency", store.muxConcurrency.first())
            settings.put("mux_xudp_concurrency", store.muxXudpConcurrency.first())
            settings.put("mux_xudp_quic", store.muxXudpQuic.first())
            settings.put("domestic_dns", store.domesticDns.first())
            settings.put("dns_hosts", store.dnsHosts.first())
            settings.put("real_ping_concurrency", store.realPingConcurrency.first())
            settings.put("ip_api_url", store.ipApiUrl.first())
            settings.put("proxy_only", store.proxyOnly.first())
            settings.put("show_speed_notif", store.showSpeedNotif.first())
            settings.put("confirm_delete", store.confirmDelete.first())
            settings.put("show_all_groups", store.showAllGroups.first())
            settings.put("language", store.language.first())
            settings.put("auto_start_boot", store.autoStartBoot.first())
            settings.put("sub_auto_test", store.subAutoTest.first())
            settings.put("sub_auto_sort", store.subAutoSort.first())
            settings.put("sub_auto_remove_invalid", store.subAutoRemoveInvalid.first())
            settings.put("hev_loglevel", store.hevLogLevel.first())
            settings.put("hev_rw_timeout", store.hevRwTimeout.first())
            // 3.2.7: the theme + the balancer groups were missing —
            // a restore used to lose both.
            settings.put("theme_mode", store.themeMode.first())
            settings.put("balancer_groups", JSONArray(store.balancerGroups.first().toList()))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("profiles", read("profiles.json"))
            .put("chains", read("chains.json"))
            .put("subscriptions", read("subscriptions.json"))
            .put("routing", readObj("routing.json"))
            .put("perapp", readObj("perapp.json"))
            .put("usage", readObj("usage.json"))
            .put("settings", settings)
            .toString(2)
    }

    /**
     * Replace the app's data with the bundle. Returns false when the
     * text is not a Lite backup at all; a bundle that parses is
     * applied even if some section is missing.
     */
    fun importJson(context: Context, text: String): Boolean {
        return try {
            val bundle = JSONObject(text)
            if (bundle.optInt("version", 0) < 1) return false
            if (!bundle.has("profiles") && !bundle.has("subscriptions")) return false
            val filesDir = context.filesDir
            fun write(name: String, value: Any?) {
                if (value == null) return
                try {
                    File(filesDir, name).writeText(value.toString())
                } catch (_: Exception) {
                }
            }
            write("profiles.json", bundle.optJSONArray("profiles"))
            write("chains.json", bundle.optJSONArray("chains"))
            write("subscriptions.json", bundle.optJSONArray("subscriptions"))
            write("routing.json", bundle.optJSONObject("routing"))
            write("perapp.json", bundle.optJSONObject("perapp"))
            write("usage.json", bundle.optJSONObject("usage"))
            val settings = bundle.optJSONObject("settings")
            if (settings != null) {
                runBlocking {
                    val store = SettingsStore(context)
                    if (settings.has("kill_switch")) store.setKillSwitch(settings.getBoolean("kill_switch"))
                    if (settings.has("auto_connect")) store.setAutoConnect(settings.getBoolean("auto_connect"))
                    if (settings.has("battery_saver")) store.setBatterySaver(settings.getBoolean("battery_saver"))
                    if (settings.has("sub_auto_update")) store.setSubAutoUpdate(settings.getBoolean("sub_auto_update"))
                    if (settings.has("test_url")) store.setTestUrl(settings.getString("test_url"))
                    if (settings.has("dns_servers")) store.setDnsServers(settings.getString("dns_servers"))
                    // 3.2.0.
                    if (settings.has("socks_port")) store.setSocksPort(settings.getString("socks_port"))
                    if (settings.has("socks_user")) store.setSocksUser(settings.getString("socks_user"))
                    if (settings.has("socks_pass")) store.setSocksPass(settings.getString("socks_pass"))
                    if (settings.has("socks_udp")) store.setSocksUdp(settings.getBoolean("socks_udp"))
                    if (settings.has("socks_share_lan")) store.setSocksShareLan(settings.getBoolean("socks_share_lan"))
                    if (settings.has("socks_dynamic_port")) store.setSocksDynamicPort(settings.getBoolean("socks_dynamic_port"))
                    if (settings.has("mtu")) store.setMtu(settings.getString("mtu"))
                    if (settings.has("iface_addr")) store.setIfaceAddr(settings.getString("iface_addr"))
                    if (settings.has("http_proxy")) store.setHttpProxy(settings.getBoolean("http_proxy"))
                    if (settings.has("fake_dns")) store.setFakeDns(settings.getBoolean("fake_dns"))
                    if (settings.has("local_dns")) store.setLocalDns(settings.getBoolean("local_dns"))
                    if (settings.has("route_only")) store.setRouteOnly(settings.getBoolean("route_only"))
                    if (settings.has("core_loglevel")) store.setCoreLogLevel(settings.getString("core_loglevel"))
                    if (settings.has("domain_strategy")) store.setDomainStrategy(settings.getString("domain_strategy"))
                    if (settings.has("mux_concurrency")) store.setMuxConcurrency(settings.getString("mux_concurrency"))
                    if (settings.has("mux_xudp_concurrency")) store.setMuxXudpConcurrency(settings.getString("mux_xudp_concurrency"))
                    if (settings.has("mux_xudp_quic")) store.setMuxXudpQuic(settings.getBoolean("mux_xudp_quic"))
                    if (settings.has("domestic_dns")) store.setDomesticDns(settings.getString("domestic_dns"))
                    if (settings.has("dns_hosts")) store.setDnsHosts(settings.getString("dns_hosts"))
                    if (settings.has("real_ping_concurrency")) store.setRealPingConcurrency(settings.getString("real_ping_concurrency"))
                    if (settings.has("ip_api_url")) store.setIpApiUrl(settings.getString("ip_api_url"))
                    if (settings.has("proxy_only")) store.setProxyOnly(settings.getBoolean("proxy_only"))
                    if (settings.has("show_speed_notif")) store.setShowSpeedNotif(settings.getBoolean("show_speed_notif"))
                    if (settings.has("confirm_delete")) store.setConfirmDelete(settings.getBoolean("confirm_delete"))
                    if (settings.has("show_all_groups")) store.setShowAllGroups(settings.getBoolean("show_all_groups"))
                    if (settings.has("language")) store.setLanguage(settings.getString("language") ?: "auto")
                    if (settings.has("auto_start_boot")) store.setAutoStartBoot(settings.getBoolean("auto_start_boot"))
                    if (settings.has("sub_auto_test")) store.setSubAutoTest(settings.getBoolean("sub_auto_test"))
                    if (settings.has("sub_auto_sort")) store.setSubAutoSort(settings.getBoolean("sub_auto_sort"))
                    if (settings.has("sub_auto_remove_invalid")) store.setSubAutoRemoveInvalid(settings.getBoolean("sub_auto_remove_invalid"))
                    if (settings.has("hev_loglevel")) store.setHevLogLevel(settings.getString("hev_loglevel"))
                    if (settings.has("hev_rw_timeout")) store.setHevRwTimeout(settings.getString("hev_rw_timeout"))
                    // 3.2.7.
                    if (settings.has("theme_mode")) store.setThemeMode(settings.getString("theme_mode") ?: "system")
                    if (settings.has("balancer_groups")) {
                        val arr = settings.optJSONArray("balancer_groups")
                        val keys = (0 until (arr?.length() ?: 0))
                            .mapNotNull { arr?.optString(it) }.toSet()
                        store.setBalancerGroups(keys)
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
