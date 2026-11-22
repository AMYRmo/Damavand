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
            // 1.0.1.18 (#87): the home design was the only setting
            // missing from backups — a restore silently reset Portal
            // back to Damavand.
            settings.put("home_design", store.homeDesign.first())
            settings.put("balancer_groups", JSONArray(store.balancerGroups.first().toList()))
            settings.put("double_column", store.doubleColumn.first())
            // 3.2.8: the Auto group + tuning were missing too.
            store.selectedGroup.first()?.let { settings.put("selected_group", it) }
            settings.put("auto_probe_url", store.autoProbeUrl.first())
            settings.put("auto_check_seconds", store.autoCheckSeconds.first())
            settings.put("auto_sweep_minutes", store.autoSweepMinutes.first())
            settings.put("auto_switch_margin", store.autoSwitchMargin.first())
            settings.put("auto_retry", store.autoRetry.first())
            // 3.2.9: dynamic color, local proxy, HEV tun.
            settings.put("dynamic_color", store.dynamicColor.first())
            settings.put("local_proxy", store.localProxy.first())
            settings.put("use_hev_tun", store.useHev.first())
            settings.put("root_mode", store.rootMode.first())
            settings.put("root_lan_share", store.rootLanShare.first())
            // 1.0.1.2 (#87): the selected profile, Auto picks, the
            // connection toggles and the Aether/IPv6/asset settings
            // were missing — a restore silently changed what the app
            // connects with and which profile boot/tile picks.
            store.selectedProfileId.first()?.let { settings.put("selected_profile_id", it) }
            store.lastWinnerLink.first()?.let { settings.put("last_winner_link", it) }
            settings.put("auto_last_pick", store.autoLastPickRawNow())
            settings.put("mux", store.mux.first())
            settings.put("sniffing", store.sniffing.first())
            settings.put("fragment", store.fragment.first())
            settings.put("routing_mode", store.routingMode.first())
            settings.put("perapp_mode", store.perAppMode.first())
            settings.put("ipv6_enabled", store.ipv6Enabled.first())
            settings.put("prefer_ipv6", store.preferIpv6.first())
            settings.put("aether_listen_port", store.aetherListenPort.first())
            settings.put("aether_keys_settings", store.aetherKeysSettings.first())
            settings.put("asset_geoip_url", store.assetGeoipUrl.first())
            settings.put("asset_geosite_url", store.assetGeositeUrl.first())
            settings.put("asset_psiphon_url", store.assetPsiphonUrl.first())
        }
        return JSONObject()
            .put("version", VERSION)
            .put("profiles", read("profiles.json"))
            .put("chains", read("chains.json"))
            .put("subscriptions", read("subscriptions.json"))
            .put("routing", readObj("routing.json"))
            .put("perapp", readObj("perapp.json"))
            .put("usage", readObj("usage.json"))
            // 1.0.1.2 (#87): the deleted-links blocklist — without it
            // a restore resurrects profiles the user deleted on the
            // next subscription refresh.
            .put("deleted_links", read("deleted-links.json"))
            // 1.0.1.2 (#87): the Aether work dir (identity + configs)
            // as base64, so Aether identities survive a restore.
            .put("aether_files", aetherFiles(File(filesDir, "aether")))
            .put("settings", settings)
            .toString(2)
    }

    /**
     * The Aether work dir as {relative path: base64}. Files over 1 MB
     * are skipped (downloaded Psiphon data is re-fetched on demand).
     */
    private fun aetherFiles(dir: File): JSONObject {
        val out = JSONObject()
        try {
            if (!dir.isDirectory) return out
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                try {
                    if (f.length() > 1_048_576L) return@forEach
                    val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                    out.put(rel, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /**
     * Type-check every known settings key; throws on a wrong type, so
     * importJson can validate the whole bundle before writing anything
     * (issue #88). Unknown keys are ignored (forward compatibility).
     */
    private fun validateSettings(settings: JSONObject) {
        val booleans = setOf(
            "kill_switch", "auto_connect", "battery_saver", "sub_auto_update",
            "socks_udp", "socks_share_lan", "socks_dynamic_port", "http_proxy",
            "fake_dns", "local_dns", "route_only", "mux_xudp_quic", "proxy_only",
            "show_speed_notif", "confirm_delete", "show_all_groups",
            "auto_start_boot", "sub_auto_test", "sub_auto_sort",
            "sub_auto_remove_invalid", "double_column", "auto_retry",
            "dynamic_color", "local_proxy", "use_hev_tun", "root_mode",
            "root_lan_share", "mux", "sniffing", "fragment",
            "ipv6_enabled", "prefer_ipv6",
        )
        val strings = setOf(
            "test_url", "dns_servers", "socks_port", "socks_user", "socks_pass",
            "mtu", "iface_addr", "core_loglevel", "domain_strategy",
            "mux_concurrency", "mux_xudp_concurrency", "domestic_dns",
            "dns_hosts", "real_ping_concurrency", "ip_api_url", "language",
            "hev_loglevel", "hev_rw_timeout", "theme_mode", "home_design", "selected_group",
            "auto_probe_url", "auto_check_seconds", "auto_sweep_minutes",
            "auto_switch_margin", "aether_listen_port", "aether_keys_settings",
            "asset_geoip_url", "asset_geosite_url", "asset_psiphon_url",
            "routing_mode", "perapp_mode", "selected_profile_id",
            "last_winner_link", "auto_last_pick",
        )
        for (key in booleans) {
            val v = settings.opt(key)
            if (v != null && v !== JSONObject.NULL && v !is Boolean) {
                throw IllegalArgumentException("setting $key is not a boolean")
            }
        }
        for (key in strings) {
            val v = settings.opt(key)
            if (v != null && v !== JSONObject.NULL && v !is String) {
                throw IllegalArgumentException("setting $key is not a string")
            }
        }
        val bg = settings.opt("balancer_groups")
        if (bg != null && bg !== JSONObject.NULL && bg !is JSONArray) {
            throw IllegalArgumentException("balancer_groups is not an array")
        }
        val picks = settings.opt("auto_last_pick")
        if (picks is String) JSONObject(picks) // throws when not an object
    }

    /**
     * Replace the app's data with the bundle. Returns false when the
     * text is not a Lite backup at all; a bundle that parses is
     * applied even if some section is missing.
     *
     * 1.0.1.2 (#88): the import is atomic — the whole bundle,
     * including every settings value, is validated BEFORE anything
     * is written, and the data files are staged to temp files and
     * swapped in only when all of them staged. A malformed bundle
     * used to report failure AFTER the live profiles were already
     * replaced, with no rollback.
     */
    fun importJson(context: Context, text: String): Boolean {
        return try {
            val bundle = JSONObject(text)
            if (bundle.optInt("version", 0) < 1) return false
            if (!bundle.has("profiles") && !bundle.has("subscriptions")) return false
            val settings = bundle.optJSONObject("settings")
            if (settings != null) validateSettings(settings)
            val filesDir = context.filesDir
            // Stage every file to a temp first; a failure here leaves
            // the live data untouched.
            val staged = mutableListOf<Pair<File, File>>()
            fun stage(name: String, value: Any?) {
                if (value == null) return
                val tmp = File(filesDir, "$name.tmp")
                tmp.writeText(value.toString())
                staged += tmp to File(filesDir, name)
            }
            try {
                stage("profiles.json", bundle.optJSONArray("profiles"))
                stage("chains.json", bundle.optJSONArray("chains"))
                stage("subscriptions.json", bundle.optJSONArray("subscriptions"))
                stage("routing.json", bundle.optJSONObject("routing"))
                stage("perapp.json", bundle.optJSONObject("perapp"))
                stage("usage.json", bundle.optJSONObject("usage"))
                stage("deleted-links.json", bundle.optJSONArray("deleted_links"))
            } catch (_: Exception) {
                staged.forEach { (tmp, _) -> runCatching { tmp.delete() } }
                return false
            }
            // All staged — swap them in. Each live file is backed up
            // first, so a failure mid-swap rolls back to the previous
            // generation instead of leaving mixed data (1.0.1.18, #88).
            val swapped = mutableListOf<Pair<File, File>>()
            try {
                staged.forEach { (tmp, target) ->
                    val backup = if (target.exists()) File(filesDir, target.name + ".bak") else null
                    if (backup != null) {
                        if (!target.renameTo(backup)) throw java.io.IOException("backup failed for ${target.name}")
                        swapped += backup to target
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                }
                swapped.forEach { (backup, _) -> runCatching { backup.delete() } }
            } catch (_: Exception) {
                swapped.forEach { (backup, target) ->
                    runCatching {
                        target.delete()
                        backup.renameTo(target)
                    }
                }
                staged.forEach { (tmp, _) -> runCatching { tmp.delete() } }
                return false
            }
            // 1.0.1.2 (#87): the Aether work dir. Paths are confined
            // to the aether dir — a crafted bundle must not write
            // outside it.
            bundle.optJSONObject("aether_files")?.let { obj ->
                val dir = File(filesDir, "aether")
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val rel = keys.next()
                    val b64 = obj.optString(rel)
                    if (b64.isEmpty()) continue
                    try {
                        val target = File(dir, rel)
                        if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) continue
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        // 1.0.1.18 (#88): export skips files over 1 MB —
                        // import must cap the same way, or a crafted
                        // bundle can write unbounded data here.
                        if (bytes.size > 1_048_576) continue
                        target.parentFile?.mkdirs()
                        // Stage + rename, like the data files above.
                        val tmp = File(target.parent, target.name + ".tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
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
                    // 1.0.1.18 (#87): only damavand/portal are valid.
                    if (settings.has("home_design")) {
                        settings.optString("home_design").takeIf {
                            it == HOME_DESIGN_DAMAVAND || it == HOME_DESIGN_PORTAL
                        }?.let { store.setHomeDesign(it) }
                    }
                    if (settings.has("balancer_groups")) {
                        val arr = settings.optJSONArray("balancer_groups")
                        val keys = (0 until (arr?.length() ?: 0))
                            .mapNotNull { arr?.optString(it) }.toSet()
                        store.setBalancerGroups(keys)
                    }
                    if (settings.has("double_column")) store.setDoubleColumn(settings.getBoolean("double_column"))
                    // 3.2.8 (Auto).
                    if (settings.has("selected_group")) store.setSelectedGroup(settings.optString("selected_group").takeIf { it.isNotEmpty() })
                    if (settings.has("auto_probe_url")) store.setAutoProbeUrl(settings.getString("auto_probe_url"))
                    if (settings.has("auto_check_seconds")) store.setAutoCheckSeconds(settings.getString("auto_check_seconds"))
                    if (settings.has("auto_sweep_minutes")) store.setAutoSweepMinutes(settings.getString("auto_sweep_minutes"))
                    if (settings.has("auto_switch_margin")) store.setAutoSwitchMargin(settings.getString("auto_switch_margin"))
                    if (settings.has("auto_retry")) store.setAutoRetry(settings.getBoolean("auto_retry"))
                    // 3.2.9.
                    if (settings.has("dynamic_color")) store.setDynamicColor(settings.getBoolean("dynamic_color"))
                    if (settings.has("local_proxy")) store.setLocalProxy(settings.getBoolean("local_proxy"))
                    if (settings.has("use_hev_tun")) store.setUseHev(settings.getBoolean("use_hev_tun"))
                    if (settings.has("root_mode")) store.setRootMode(settings.getBoolean("root_mode"))
                    if (settings.has("root_lan_share")) store.setRootLanShare(settings.getBoolean("root_lan_share"))
                    // 1.0.1.2 (#87).
                    if (settings.has("selected_profile_id")) store.setSelectedProfileId(settings.optString("selected_profile_id").takeIf { it.isNotEmpty() })
                    if (settings.has("last_winner_link")) store.setLastWinnerLink(settings.optString("last_winner_link").takeIf { it.isNotEmpty() })
                    if (settings.has("auto_last_pick")) {
                        try {
                            val picks = JSONObject(settings.optString("auto_last_pick") ?: "{}")
                            val keys = picks.keys()
                            while (keys.hasNext()) {
                                val id = keys.next()
                                store.setAutoLastPick(id, picks.optString(id))
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (settings.has("mux")) store.setMux(settings.getBoolean("mux"))
                    if (settings.has("sniffing")) store.setSniffing(settings.getBoolean("sniffing"))
                    if (settings.has("fragment")) store.setFragment(settings.getBoolean("fragment"))
                    if (settings.has("routing_mode")) store.setRoutingMode(settings.getString("routing_mode") ?: "bypass_lan")
                    if (settings.has("perapp_mode")) store.setPerAppMode(settings.getString("perapp_mode") ?: "bypass")
                    if (settings.has("ipv6_enabled")) store.setIpv6Enabled(settings.getBoolean("ipv6_enabled"))
                    if (settings.has("prefer_ipv6")) store.setPreferIpv6(settings.getBoolean("prefer_ipv6"))
                    if (settings.has("aether_listen_port")) store.setAetherListenPort(settings.getString("aether_listen_port"))
                    if (settings.has("aether_keys_settings")) store.setAetherKeysSettings(settings.getString("aether_keys_settings"))
                    if (settings.has("asset_geoip_url")) store.setAssetGeoipUrl(settings.getString("asset_geoip_url"))
                    if (settings.has("asset_geosite_url")) store.setAssetGeositeUrl(settings.getString("asset_geosite_url"))
                    if (settings.has("asset_psiphon_url")) store.setAssetPsiphonUrl(settings.getString("asset_psiphon_url"))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
