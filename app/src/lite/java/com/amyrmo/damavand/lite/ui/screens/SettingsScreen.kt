package com.amyrmo.damavand.lite.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.BuildConfig
import com.amyrmo.damavand.lite.GeoDownloader
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Settings tab (spec §7): a REAL Summit tab, not the classic
 * SettingsScreen. Exactly four groups, in this order:
 * «اتصال» / «باتری و حجم» / «هسته اتصال» / «درباره».
 * No save button, no reset row, no search.
 */
@Composable
fun SettingsScreen(vm: LiteViewModel) {
    val killSwitch by vm.killSwitch.collectAsState(initial = false)
    val autoConnect by vm.autoConnect.collectAsState(initial = false)
    val batterySaver by vm.batterySaver.collectAsState(initial = true)
    val subAutoUpdate by vm.subAutoUpdate.collectAsState(initial = true)
    val geoState by vm.geoState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            // Preferred core: info row (Lite ships Xray only — §7.3).
            InfoRow(
                titleRes = R.string.preferred_core,
                descRes = R.string.preferred_core_desc,
                trailing = "Xray"
            )
            GroupDivider()
            GeoRow(
                state = geoState,
                onDownload = { vm.downloadGeo() },
                onCancel = { vm.cancelGeoDownload() }
            )
        }
        Spacer(Modifier.height(24.dp))

        // ---- 4. «درباره» ----
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
}

// ---- Group chrome (§7) ----

@Composable
private fun SettingsGroup(labelRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp)),
            content = content
        )
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
    onCancel: () -> Unit
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
