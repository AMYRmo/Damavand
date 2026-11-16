package com.amyrmo.damavand.lite.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.BuildConfig
import com.amyrmo.damavand.lite.GeoDownloader
import com.amyrmo.damavand.lite.LiteTileService
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.ChevronDownIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Settings tab (spec §7; 3.1.0 PattNG): connection settings, the
 * subscription manager, routing + per-app proxy, battery, the core,
 * backup & restore, logs, and about. No save button, no reset row.
 */
@Composable
fun SettingsScreen(
    vm: LiteViewModel,
    scrollState: ScrollState,
    onNavigate: (SubScreen) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportGeo: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val killSwitch by vm.killSwitch.collectAsState(initial = false)
    val autoConnect by vm.autoConnect.collectAsState(initial = false)
    val batterySaver by vm.batterySaver.collectAsState(initial = true)
    val subAutoUpdate by vm.subAutoUpdate.collectAsState(initial = true)
    val geoState by vm.geoState.collectAsState()
    val updateCheck by vm.updateCheck.collectAsState()
    // 3.1.0 (PattNG): connection settings.
    val testUrl by vm.testUrl.collectAsState(initial = "https://www.google.com/generate_204")
    val mux by vm.mux.collectAsState(initial = false)
    val sniffing by vm.sniffing.collectAsState(initial = true)
    val fragment by vm.fragment.collectAsState(initial = false)
    val dnsServers by vm.dnsServers.collectAsState(initial = "8.8.8.8,1.1.1.1")
    val subscriptions by vm.subscriptions.collectAsState()
    // 3.2.0 (PattNG parity).
    val socksPort by vm.socksPort.collectAsState(initial = "1080")
    val socksUser by vm.socksUser.collectAsState(initial = "")
    val socksPass by vm.socksPass.collectAsState(initial = "")
    val socksUdp by vm.socksUdp.collectAsState(initial = true)
    val socksShareLan by vm.socksShareLan.collectAsState(initial = false)
    val socksDynamicPort by vm.socksDynamicPort.collectAsState(initial = false)
    val mtu by vm.mtu.collectAsState(initial = "1500")
    val ifaceAddr by vm.ifaceAddr.collectAsState(initial = "10.8.0.2")
    val httpProxy by vm.httpProxy.collectAsState(initial = false)
    val fakeDns by vm.fakeDns.collectAsState(initial = false)
    val localDns by vm.localDns.collectAsState(initial = true)
    val routeOnly by vm.routeOnly.collectAsState(initial = false)
    val coreLogLevel by vm.coreLogLevel.collectAsState(initial = "warning")
    val domainStrategy by vm.domainStrategy.collectAsState(initial = "IPIfNonMatch")
    val muxConcurrency by vm.muxConcurrency.collectAsState(initial = "8")
    val muxXudpConcurrency by vm.muxXudpConcurrency.collectAsState(initial = "8")
    val muxXudpQuic by vm.muxXudpQuic.collectAsState(initial = false)
    val domesticDns by vm.domesticDns.collectAsState(initial = "")
    val dnsHosts by vm.dnsHosts.collectAsState(initial = "")
    val realPingConcurrency by vm.realPingConcurrency.collectAsState(initial = "5")
    val ipApiUrl by vm.ipApiUrl.collectAsState(initial = "https://ipinfo.io/json")
    val proxyOnly by vm.proxyOnly.collectAsState(initial = false)
    val showSpeedNotif by vm.showSpeedNotif.collectAsState(initial = true)
    val confirmDelete by vm.confirmDelete.collectAsState(initial = true)
    val showAllGroups by vm.showAllGroups.collectAsState(initial = false)
    val language by vm.language.collectAsState(initial = "auto")
    val themeMode by vm.themeMode.collectAsState(initial = "system")
    val doubleColumn by vm.doubleColumn.collectAsState(initial = false)
    val autoStartBoot by vm.autoStartBoot.collectAsState(initial = false)
    val subAutoTest by vm.subAutoTest.collectAsState(initial = true)
    val subAutoSort by vm.subAutoSort.collectAsState(initial = false)
    val subAutoRemoveInvalid by vm.subAutoRemoveInvalid.collectAsState(initial = false)
    val hevLogLevel by vm.hevLogLevel.collectAsState(initial = "warning")
    val hevRwTimeout by vm.hevRwTimeout.collectAsState(initial = "0")
    var editField by remember { mutableStateOf<String?>(null) } // see the dialog below

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tab_settings),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(16.dp))

        // ---- 1. «اتصال» ----
        SettingsGroup(labelRes = R.string.settings_group_connection) {
            SwitchRow(
                titleRes = R.string.kill_switch,
                descRes = R.string.kill_switch_desc,
                checked = killSwitch,
                onCheckedChange = { vm.setKillSwitch(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.auto_connect,
                descRes = R.string.auto_connect_desc,
                checked = autoConnect,
                onCheckedChange = { vm.setAutoConnect(it) }
            )
            // 3.1.0 (PattNG): connection settings.
            GroupDivider()
            TextEditRow(
                titleRes = R.string.test_url_title,
                desc = testUrl,
                onClick = { editField = "test_url" }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.mux_title,
                descRes = R.string.mux_desc,
                checked = mux,
                onCheckedChange = { vm.setMux(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.sniffing_title,
                descRes = R.string.sniffing_desc,
                checked = sniffing,
                onCheckedChange = { vm.setSniffing(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.fragment_title,
                descRes = R.string.fragment_desc,
                checked = fragment,
                onCheckedChange = { vm.setFragment(it) }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.dns_title,
                desc = dnsServers,
                onClick = { editField = "dns" }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 1b. «VPN» (3.2.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_vpn) {
            SwitchRow(
                titleRes = R.string.proxy_only_title,
                descRes = R.string.proxy_only_desc,
                checked = proxyOnly,
                onCheckedChange = { vm.setProxyOnly(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.route_only_title,
                descRes = R.string.route_only_desc,
                checked = routeOnly,
                onCheckedChange = { vm.setRouteOnly(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.local_dns_title,
                descRes = R.string.local_dns_desc,
                checked = localDns,
                onCheckedChange = { vm.setLocalDns(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.fake_dns_title,
                descRes = R.string.fake_dns_desc,
                checked = fakeDns,
                onCheckedChange = { vm.setFakeDns(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.http_proxy_title,
                descRes = R.string.http_proxy_desc,
                checked = httpProxy,
                onCheckedChange = { vm.setHttpProxy(it) }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.mtu_title,
                desc = mtu,
                onClick = { editField = "mtu" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.iface_addr_title,
                desc = ifaceAddr,
                onClick = { editField = "iface_addr" }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.auto_start_boot_title,
                descRes = R.string.auto_start_boot_desc,
                checked = autoStartBoot,
                onCheckedChange = { vm.setAutoStartBoot(it) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 1c. «SOCKS» (3.2.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_socks) {
            TextEditRow(
                titleRes = R.string.socks_port_title,
                desc = socksPort,
                onClick = { editField = "socks_port" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.socks_user_title,
                desc = socksUser.ifEmpty { "—" },
                onClick = { editField = "socks_user" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.socks_pass_title,
                desc = if (socksPass.isEmpty()) "—" else "••••••••",
                onClick = { editField = "socks_pass" }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.socks_udp_title,
                descRes = R.string.socks_udp_desc,
                checked = socksUdp,
                onCheckedChange = { vm.setSocksUdp(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.socks_share_lan_title,
                descRes = R.string.socks_share_lan_desc,
                checked = socksShareLan,
                onCheckedChange = { vm.setSocksShareLan(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.socks_dynamic_port_title,
                descRes = R.string.socks_dynamic_port_desc,
                checked = socksDynamicPort,
                onCheckedChange = { vm.setSocksDynamicPort(it) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 2. «اشتراک‌ها» (3.1.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_subscriptions) {
            NavRow(
                titleRes = R.string.settings_subscriptions,
                descRes = R.string.settings_subscriptions_desc,
                trailing = LiteFormat.toPersian(subscriptions.size.toString()),
                onClick = { onNavigate(SubScreen.SUBSCRIPTIONS) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.sub_auto_test_title,
                descRes = R.string.sub_auto_test_desc,
                checked = subAutoTest,
                onCheckedChange = { vm.setSubAutoTest(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.sub_auto_sort_title,
                descRes = R.string.sub_auto_sort_desc,
                checked = subAutoSort,
                onCheckedChange = { vm.setSubAutoSort(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.sub_auto_remove_invalid_title,
                descRes = R.string.sub_auto_remove_invalid_desc,
                checked = subAutoRemoveInvalid,
                onCheckedChange = { vm.setSubAutoRemoveInvalid(it) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 3. «مسیریابی و برنامه‌ها» (3.1.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_routing_apps) {
            NavRow(
                titleRes = R.string.settings_routing,
                descRes = R.string.settings_routing_desc,
                onClick = { onNavigate(SubScreen.ROUTING) }
            )
            GroupDivider()
            NavRow(
                titleRes = R.string.settings_perapp,
                descRes = R.string.settings_perapp_desc,
                onClick = { onNavigate(SubScreen.PER_APP) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 2. «باتری و حجم» ----
        SettingsGroup(labelRes = R.string.settings_group_battery) {
            SwitchRow(
                titleRes = R.string.battery_saver,
                descRes = R.string.battery_saver_desc,
                checked = batterySaver,
                onCheckedChange = { vm.setBatterySaver(it) }
            )
            if (!batterySaver) {
                GroupDivider()
                Text(
                    text = stringResource(R.string.battery_saver_off_note),
                    fontSize = 12.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            GroupDivider()
            SwitchRow(
                titleRes = R.string.sub_auto_update,
                descRes = R.string.sub_auto_update_desc,
                checked = subAutoUpdate,
                onCheckedChange = { vm.setSubAutoUpdate(it) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 3. «هسته اتصال» ----
        SettingsGroup(labelRes = R.string.settings_group_core) {
            GeoRow(
                state = geoState,
                onDownload = { vm.downloadGeo() },
                onCancel = { vm.cancelGeoDownload() },
                onImportGeo = onImportGeo
            )
            // 3.2.0 (PattNG): core / mux / DNS details.
            GroupDivider()
            CycleRow(
                titleRes = R.string.core_loglevel_title,
                descRes = R.string.core_loglevel_desc,
                options = listOf("debug", "info", "warning", "error", "none"),
                current = coreLogLevel,
                onSelect = { vm.setCoreLogLevel(it) }
            )
            GroupDivider()
            CycleRow(
                titleRes = R.string.domain_strategy_title,
                descRes = R.string.domain_strategy_desc,
                options = listOf("IPIfNonMatch", "AsIs", "IPOnDemand"),
                current = domainStrategy,
                onSelect = { vm.setDomainStrategy(it) }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.domestic_dns_title,
                desc = domesticDns.ifEmpty { "—" },
                onClick = { editField = "domestic_dns" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.dns_hosts_title,
                desc = dnsHosts.ifEmpty { "—" },
                onClick = { editField = "dns_hosts" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.mux_concurrency_title,
                desc = muxConcurrency,
                onClick = { editField = "mux_concurrency" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.mux_xudp_concurrency_title,
                desc = muxXudpConcurrency,
                onClick = { editField = "mux_xudp_concurrency" }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.mux_xudp_quic_title,
                descRes = R.string.mux_xudp_quic_desc,
                checked = muxXudpQuic,
                onCheckedChange = { vm.setMuxXudpQuic(it) }
            )
            GroupDivider()
            CycleRow(
                titleRes = R.string.hev_loglevel_title,
                descRes = R.string.hev_loglevel_desc,
                options = listOf("debug", "info", "warning", "error", "none"),
                current = hevLogLevel,
                onSelect = { vm.setHevLogLevel(it) }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.hev_rw_timeout_title,
                desc = hevRwTimeout,
                onClick = { editField = "hev_rw_timeout" }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 6. «پشتیبان‌گیری» (3.1.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_backup) {
            NavRow(
                titleRes = R.string.settings_backup_export,
                descRes = R.string.settings_backup_export_desc,
                onClick = onExportBackup
            )
            GroupDivider()
            NavRow(
                titleRes = R.string.settings_backup_import,
                descRes = R.string.settings_backup_import_desc,
                onClick = onImportBackup
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 7. «لاگ‌ها» (3.1.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_logs) {
            NavRow(
                titleRes = R.string.settings_logs,
                descRes = R.string.settings_logs_desc,
                onClick = { onNavigate(SubScreen.LOGS) }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 7b. «متفرقه» (3.2.0, PattNG) ----
        SettingsGroup(labelRes = R.string.settings_group_misc) {
            TextEditRow(
                titleRes = R.string.real_ping_concurrency_title,
                desc = realPingConcurrency,
                onClick = { editField = "real_ping_concurrency" }
            )
            GroupDivider()
            TextEditRow(
                titleRes = R.string.ip_api_url_title,
                desc = ipApiUrl,
                onClick = { editField = "ip_api_url" }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.show_speed_notif_title,
                descRes = R.string.show_speed_notif_desc,
                checked = showSpeedNotif,
                onCheckedChange = { vm.setShowSpeedNotif(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.confirm_delete_title,
                descRes = R.string.confirm_delete_desc,
                checked = confirmDelete,
                onCheckedChange = { vm.setConfirmDelete(it) }
            )
            GroupDivider()
            SwitchRow(
                titleRes = R.string.show_all_groups_title,
                descRes = R.string.show_all_groups_desc,
                checked = showAllGroups,
                onCheckedChange = { vm.setShowAllGroups(it) }
            )
            GroupDivider()
            LanguageRow(
                current = language,
                onSelect = { next ->
                    vm.setLanguage(next)
                    // Let the DataStore write land, then restart the
                    // activity so the new locale applies.
                    scope.launch {
                        kotlinx.coroutines.delay(400)
                        (context as? android.app.Activity)?.recreate()
                    }
                }
            )
            GroupDivider()
            ThemeRow(
                current = themeMode,
                // 3.2.5: no restart — the theme is state, MainActivity
                // applies it live as soon as the setting changes.
                onSelect = { next -> vm.setThemeMode(next) }
            )
            GroupDivider()
            // 3.2.7 (PattNG): two-column server list. Off by default —
            // the Summit UI stays exactly as it is unless turned on.
            SwitchRow(
                titleRes = R.string.settings_double_column_title,
                descRes = R.string.settings_double_column_desc,
                checked = doubleColumn,
                onCheckedChange = { vm.setDoubleColumn(it) }
            )
            GroupDivider()
            // 3.2.6: add the quick settings tile (control-center
            // button) — Android 13+ prompts to add it in place.
            NavRow(
                titleRes = R.string.quick_settings_tile_title,
                descRes = R.string.quick_settings_tile_desc,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val sm = context.getSystemService(StatusBarManager::class.java)
                        sm?.requestAddTileService(
                            ComponentName(context, LiteTileService::class.java),
                            context.getString(R.string.app_name),
                            Icon.createWithResource(context, R.drawable.ic_lite),
                            context.mainExecutor
                        ) { result ->
                            val msgRes = when (result) {
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                                    R.string.quick_settings_tile_added
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                                    R.string.quick_settings_tile_already
                                else -> R.string.quick_settings_tile_not_added
                            }
                            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.quick_settings_tile_old_android),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            GroupDivider()
            NavRow(
                titleRes = R.string.system_vpn_settings_title,
                descRes = R.string.system_vpn_settings_desc,
                onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                    )
                }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 8. «درباره» ----
        SettingsGroup(labelRes = R.string.settings_group_about) {
            InfoRow(
                titleRes = R.string.about_version,
                desc = stringResource(R.string.about_version_code, LiteFormat.toPersian(BuildConfig.VERSION_CODE.toString())),
                trailing = LiteFormat.toPersian(BuildConfig.VERSION_NAME)
            )
            GroupDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_package),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                SelectionContainer {
                    Text(
                        text = BuildConfig.APPLICATION_ID,
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
            GroupDivider()
            InfoRow(
                titleRes = R.string.about_core,
                desc = null,
                trailing = "Xray"
            )
            GroupDivider()
            val context = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/AMYRmo/Damavand/tree/lite")
                            )
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_repo),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.about_repo_value),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
            GroupDivider()
            // 3.2.0 (PattNG): update check — tap to check; tap again
            // when an update is available to open the release page.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable {
                        val st = updateCheck
                        if (st is LiteViewModel.UpdateCheckState.Result && st.updateAvailable) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(st.url))
                            )
                        } else {
                            vm.checkForUpdate()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_check_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LiteColors.text,
                        fontFamily = Vazirmatn
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when (val st = updateCheck) {
                            is LiteViewModel.UpdateCheckState.Idle ->
                                stringResource(R.string.update_check_desc)
                            is LiteViewModel.UpdateCheckState.Checking ->
                                stringResource(R.string.update_check_checking)
                            is LiteViewModel.UpdateCheckState.Result ->
                                if (st.updateAvailable) {
                                    stringResource(
                                        R.string.update_check_available,
                                        LiteFormat.toPersian(st.tag.removePrefix("lite-v"))
                                    )
                                } else {
                                    stringResource(R.string.update_check_latest)
                                }
                            is LiteViewModel.UpdateCheckState.Failed ->
                                stringResource(R.string.update_check_failed)
                        },
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_copy),
            fontSize = 12.sp,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    // Text-field edit dialog (3.1.0): test URL / DNS servers.
    // 3.2.0: all the new text settings use the same dialog.
    val field = editField
    if (field != null) {
        val (titleRes, descRes, initial, onSave) = when (field) {
            "dns" -> Quadruple(
                R.string.dns_title, R.string.dns_desc, dnsServers,
                { v: String -> vm.setDnsServers(v) }
            )
            "socks_port" -> Quadruple(
                R.string.socks_port_title, R.string.socks_port_desc, socksPort,
                { v: String -> vm.setSocksPort(v) }
            )
            "socks_user" -> Quadruple(
                R.string.socks_user_title, R.string.socks_user_desc, socksUser,
                { v: String -> vm.setSocksUser(v) }
            )
            "socks_pass" -> Quadruple(
                R.string.socks_pass_title, R.string.socks_pass_desc, socksPass,
                { v: String -> vm.setSocksPass(v) }
            )
            "mtu" -> Quadruple(
                R.string.mtu_title, R.string.mtu_desc, mtu,
                { v: String -> vm.setMtu(v) }
            )
            "iface_addr" -> Quadruple(
                R.string.iface_addr_title, R.string.iface_addr_desc, ifaceAddr,
                { v: String -> vm.setIfaceAddr(v) }
            )
            "domestic_dns" -> Quadruple(
                R.string.domestic_dns_title, R.string.domestic_dns_desc, domesticDns,
                { v: String -> vm.setDomesticDns(v) }
            )
            "dns_hosts" -> Quadruple(
                R.string.dns_hosts_title, R.string.dns_hosts_desc, dnsHosts,
                { v: String -> vm.setDnsHosts(v) }
            )
            "mux_concurrency" -> Quadruple(
                R.string.mux_concurrency_title, R.string.mux_concurrency_desc, muxConcurrency,
                { v: String -> vm.setMuxConcurrency(v) }
            )
            "mux_xudp_concurrency" -> Quadruple(
                R.string.mux_xudp_concurrency_title, R.string.mux_xudp_concurrency_desc, muxXudpConcurrency,
                { v: String -> vm.setMuxXudpConcurrency(v) }
            )
            "real_ping_concurrency" -> Quadruple(
                R.string.real_ping_concurrency_title, R.string.real_ping_concurrency_desc, realPingConcurrency,
                { v: String -> vm.setRealPingConcurrency(v) }
            )
            "ip_api_url" -> Quadruple(
                R.string.ip_api_url_title, R.string.ip_api_url_desc, ipApiUrl,
                { v: String -> vm.setIpApiUrl(v) }
            )
            "hev_rw_timeout" -> Quadruple(
                R.string.hev_rw_timeout_title, R.string.hev_rw_timeout_desc, hevRwTimeout,
                { v: String -> vm.setHevRwTimeout(v) }
            )
            else -> Quadruple(
                R.string.test_url_title, R.string.test_url_desc, testUrl,
                { v: String -> vm.setTestUrl(v) }
            )
        }
        TextEditDialog(
            titleRes = titleRes,
            descRes = descRes,
            initial = initial,
            onDismiss = { editField = null },
            onSave = { v ->
                onSave(v)
                editField = null
            }
        )
    }
}

// A four-value holder for the text-field dialog config.
private data class Quadruple(
    val titleRes: Int,
    val descRes: Int,
    val initial: String,
    val onSave: (String) -> Unit
)

@Composable
private fun TextEditDialog(
    titleRes: Int,
    descRes: Int,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(descRes),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Vazirmatn,
                        fontSize = 14.sp,
                        color = LiteColors.text,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiteColors.hairlineStrong,
                        unfocusedBorderColor = LiteColors.hairline,
                        cursorColor = LiteColors.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onSave(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(
                    text = stringResource(R.string.server_edit_save),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/** A row that opens a sub-screen (3.1.0, PattNG). */
@Composable
private fun NavRow(
    titleRes: Int,
    descRes: Int,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.width(8.dp))
        }
        com.amyrmo.damavand.lite.ui.components.ChevronForwardIcon(
            size = 18.dp,
            color = LiteColors.textMuted
        )
    }
}

/** A row whose value is edited in a dialog (3.1.0, PattNG). */
@Composable
private fun TextEditRow(titleRes: Int, desc: String, onClick: () -> Unit) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 1,
                style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)
            )
        }
    }
}

// ---- Group chrome (§7) ----

/** A row that cycles through a fixed set of values on tap (3.2.0). */
@Composable
private fun LanguageRow(
    current: String,
    onSelect: (String) -> Unit
) {
    // 3.2.0: like CycleRow, but shows the localized label instead
    // of the raw value ("auto" / "fa" / "en").
    val options = listOf("auto", "fa", "en")
    val label = when (current) {
        "fa" -> stringResource(R.string.language_fa)
        "en" -> stringResource(R.string.language_en)
        else -> stringResource(R.string.language_auto)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = {
                val idx = options.indexOf(current).takeIf { it >= 0 } ?: 0
                onSelect(options[(idx + 1).mod(options.size)])
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.language_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.language_desc),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.accent,
            fontFamily = Vazirmatn
        )
    }
}

@Composable
private fun ThemeRow(
    current: String,
    onSelect: (String) -> Unit
) {
    // 3.2.5: like LanguageRow — tap cycles system → night → day.
    val options = listOf("system", "dark", "light")
    val label = when (current) {
        "dark" -> stringResource(R.string.theme_night)
        "light" -> stringResource(R.string.theme_day)
        else -> stringResource(R.string.theme_system)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = {
                val idx = options.indexOf(current).takeIf { it >= 0 } ?: 0
                onSelect(options[(idx + 1).mod(options.size)])
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.theme_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.theme_desc),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.accent,
            fontFamily = Vazirmatn
        )
    }
}

@Composable
private fun CycleRow(
    titleRes: Int,
    descRes: Int,
    options: List<String>,
    current: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = {
                val idx = options.indexOf(current)
                onSelect(options[(idx + 1).mod(options.size)])
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = current,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.accent,
            fontFamily = Vazirmatn,
            style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)
        )
    }
}

@Composable
private fun SettingsGroup(labelRes: Int, content: @Composable ColumnScope.() -> Unit) {
    // 3.2.4: every section starts closed, and a closed section
    // keeps the same glassy container as the open one — the header
    // sits inside the surface + hairline card, only the content
    // is hidden. (The clickable stays on the header row only, so
    // tapping a switch inside never collapses the section.)
    var collapsed by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { collapsed = !collapsed })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (collapsed) -90f else 0f
                }
            ) {
                ChevronDownIcon(size = 16.dp, color = LiteColors.textSecondary)
            }
        }
        if (!collapsed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(LiteColors.hairline)
    )
}

@Composable
private fun SwitchRow(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LiteColors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = LiteColors.text,
                uncheckedTrackColor = LiteColors.surface2,
                uncheckedBorderColor = LiteColors.hairlineStrong
            )
        )
    }
}

@Composable
private fun InfoRow(titleRes: Int, desc: String?, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = trailing,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn
        )
    }
}

@Composable
private fun InfoRow(titleRes: Int, descRes: Int, trailing: String) {
    InfoRow(
        titleRes = titleRes,
        desc = stringResource(descRes),
        trailing = trailing
    )
}

// ---- Geo download row (§7.4) — all five states ----

@Composable
private fun GeoRow(
    state: GeoDownloader.State,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onImportGeo: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.geo_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.geo_desc),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    maxLines = 2
                )
                when (state) {
                    is GeoDownloader.State.Ready -> {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.geo_version, LiteFormat.toPersian(state.version)),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                    is GeoDownloader.State.UpdateAvailable -> {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.geo_update_available),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                    is GeoDownloader.State.Error -> {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.reason,
                            fontSize = 12.sp,
                            color = LiteColors.error,
                            fontFamily = Vazirmatn
                        )
                    }
                    else -> Unit
                }
            }
            Spacer(Modifier.width(12.dp))
            when (state) {
                is GeoDownloader.State.Missing -> {
                    GeoPillButton(labelRes = R.string.geo_download, onClick = onDownload)
                }
                is GeoDownloader.State.Downloading -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = LiteFormat.formatPercent(state.percent),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.geo_cancel),
                            fontSize = 13.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn,
                            modifier = Modifier.clickable(onClick = onCancel)
                        )
                    }
                }
                is GeoDownloader.State.Ready -> {
                    Text(
                        text = stringResource(R.string.geo_ready),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LiteColors.green,
                        fontFamily = Vazirmatn
                    )
                }
                is GeoDownloader.State.UpdateAvailable -> {
                    GeoPillButton(labelRes = R.string.geo_update, onClick = onDownload)
                }
                is GeoDownloader.State.Error -> {
                    Column(horizontalAlignment = Alignment.End) {
                        if (state.wasReady) {
                            Text(
                                text = stringResource(R.string.geo_ready),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LiteColors.green,
                                fontFamily = Vazirmatn
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        GeoPillButton(labelRes = R.string.geo_retry, onClick = onDownload)
                    }
                }
            }
        }
        // Thin red progress hairline (2dp) while downloading.
        if (state is GeoDownloader.State.Downloading) {
            val fraction = (state.percent / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .height(2.dp)
                    .background(LiteColors.hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .background(LiteColors.accent)
                )
            }
        }
        // Custom geo import (3.2.0, PattNG): pick your own files.
        if (state !is GeoDownloader.State.Downloading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.geo_import_geoip),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.clickable { onImportGeo("geoip") }
                )
                Text(
                    text = stringResource(R.string.geo_import_geosite),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.clickable { onImportGeo("geosite") }
                )
            }
        }
    }
}

@Composable
private fun GeoPillButton(labelRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(Color.Transparent, CircleShape)
            .border(1.5.dp, LiteColors.hairlineStrong, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}
