package com.amyrmo.damavand.ui.bpb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.domain.config.RoutingMigration
import com.amyrmo.damavand.domain.model.AppSettings
import com.amyrmo.damavand.domain.model.RulesetPresets
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.anti_sanction_dns
import com.amyrmo.damavand.shared.resources.anti_sanction_dns_hint
import com.amyrmo.damavand.shared.resources.assets_download
import com.amyrmo.damavand.shared.resources.assets_done
import com.amyrmo.damavand.shared.resources.assets_failed
import com.amyrmo.damavand.shared.resources.assets_missing
import com.amyrmo.damavand.shared.resources.assets_subtitle
import com.amyrmo.damavand.shared.resources.assets_title
import com.amyrmo.damavand.shared.resources.bpb_best_ping_note
import com.amyrmo.damavand.shared.resources.bpb_best_ping_title
import com.amyrmo.damavand.shared.resources.bpb_dns_bootstrap_note
import com.amyrmo.damavand.shared.resources.bpb_dns_bootstrap_title
import com.amyrmo.damavand.shared.resources.bpb_geo_verified_note
import com.amyrmo.damavand.shared.resources.bpb_geo_verified_title
import com.amyrmo.damavand.shared.resources.bpb_sanction_preset
import com.amyrmo.damavand.shared.resources.bpb_sanction_preset_summary
import com.amyrmo.damavand.shared.resources.bpb_section_dns
import com.amyrmo.damavand.shared.resources.bpb_section_global
import com.amyrmo.damavand.shared.resources.bpb_section_tor
import com.amyrmo.damavand.shared.resources.bpb_section_warp
import com.amyrmo.damavand.shared.resources.bpb_subtitle
import com.amyrmo.damavand.shared.resources.bpb_warp_masque_summary
import com.amyrmo.damavand.shared.resources.bpb_warp_wireguard_summary
import com.amyrmo.damavand.shared.resources.ic_bolt
import com.amyrmo.damavand.shared.resources.nav_bpb
import com.amyrmo.damavand.shared.resources.routing_preset_duplicate
import com.amyrmo.damavand.shared.resources.warp_mode_masque
import com.amyrmo.damavand.shared.resources.warp_mode_wireguard
import com.amyrmo.damavand.ui.platform.LocalPlatform
import com.amyrmo.damavand.ui.servers.WarpDialog
import com.amyrmo.damavand.ui.settings.BrowserDialerRow
import com.amyrmo.damavand.ui.settings.EchGroup
import com.amyrmo.damavand.ui.settings.FinalMaskGroup
import com.amyrmo.damavand.ui.settings.LanSharingRows
import com.amyrmo.damavand.ui.settings.LocalProxyEnableRow
import com.amyrmo.damavand.ui.settings.MasqueMaskGroup
import com.amyrmo.damavand.ui.settings.MoatFetchButton
import com.amyrmo.damavand.ui.settings.MoatFetchStatus
import com.amyrmo.damavand.ui.settings.SettingsEditRow
import com.amyrmo.damavand.ui.settings.SettingsGroup
import com.amyrmo.damavand.ui.settings.SettingsInfoRow
import com.amyrmo.damavand.ui.settings.SettingsMenuRow
import com.amyrmo.damavand.ui.settings.SettingsPageScaffold
import com.amyrmo.damavand.ui.settings.SettingsSegment
import com.amyrmo.damavand.ui.settings.rememberMoatFetchState
import com.amyrmo.damavand.ui.settings.rulesetUpdate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The BPB tab: every feature ported from BPB-VPN in one place, grouped the
 * way BPB-VPN's own settings page groups them (Warp / Global / VLESS-style
 * ECH + fragments / DNS / Tor). Every control binds to the SAME settings the
 * Fragment, Routing, Core and Tor pages edit — this screen owns no state of
 * its own beyond dialogs and progress.
 */
@Composable
fun BpbScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onWarpRegistered: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val duplicateMsg = stringResource(Res.string.routing_preset_duplicate)
    val assetsDoneMsg = stringResource(Res.string.assets_done)
    val assetsFailedMsg = stringResource(Res.string.assets_failed)

    // null = dialog closed; false = MASQUE preselected; true = WireGuard.
    var warpWireGuard by remember { mutableStateOf<Boolean?>(null) }
    warpWireGuard?.let { wireGuard ->
        WarpDialog(
            onDismiss = { warpWireGuard = null },
            onRegistered = { link ->
                warpWireGuard = null
                onWarpRegistered(link)
            },
            initialWireGuard = wireGuard,
        )
    }

    val moatFetch = rememberMoatFetchState()

    val geo = platform.geoAssets
    var geoAssets by remember { mutableStateOf(geo?.state().orEmpty()) }
    var geoBusy by remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = stringResource(Res.string.nav_bpb),
        subtitle = stringResource(Res.string.bpb_subtitle),
        contentPadding = contentPadding,
        modifier = modifier,
        titleIcon = Res.drawable.ic_bolt,
    ) {
        // Warp — BPB has separate Warp and Masque sections, each with its
        // account registration; Damavand's one dialog covers both.
        SettingsGroup(stringResource(Res.string.bpb_section_warp)) {
            SettingsMenuRow(
                title = stringResource(Res.string.warp_mode_masque),
                subtitle = stringResource(Res.string.bpb_warp_masque_summary),
            ) { warpWireGuard = false }
            SettingsMenuRow(
                title = stringResource(Res.string.warp_mode_wireguard),
                subtitle = stringResource(Res.string.bpb_warp_wireguard_summary),
            ) { warpWireGuard = true }
            // BPB's in-config best-ping balancer is behavior, not a switch:
            // an info row, honestly labelled as always-on.
            SettingsInfoRow(
                title = stringResource(Res.string.bpb_best_ping_title),
                body = stringResource(Res.string.bpb_best_ping_note),
            )
        }

        // Global — BPB keeps LAN sharing in its Global section; the Browser
        // Dialer is a global dialing behavior in the same spirit.
        SettingsGroup(stringResource(Res.string.bpb_section_global)) {
            LocalProxyEnableRow(settings, onUpdate)
            LanSharingRows(settings, onUpdate)
            BrowserDialerRow(settings, onUpdate)
        }

        // BPB's VLESS section order: ECH first, then TCP fragments. The
        // fragment groups below are the Fragment page's own groups.
        EchGroup(settings, onUpdate)
        FinalMaskGroup(settings, onUpdate)
        MasqueMaskGroup(settings, onUpdate)

        // DNS — BPB's anti-sanction DNS field (Global) and its sanction
        // routing rules (Routing), plus the built-in bootstrap pinning.
        SettingsGroup(stringResource(Res.string.bpb_section_dns)) {
            SettingsEditRow(
                title = stringResource(Res.string.anti_sanction_dns),
                value = settings.antiSanctionDns,
                placeholder = "178.22.122.100",
                onValueChanged = { v -> onUpdate { it.copy(antiSanctionDns = v) } },
            )
            SettingsSegment {
                Text(
                    stringResource(Res.string.anti_sanction_dns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            SettingsMenuRow(
                title = stringResource(Res.string.bpb_sanction_preset),
                subtitle = stringResource(Res.string.bpb_sanction_preset_summary),
            ) {
                // Same preset application as the Routing page's preset menu:
                // append only the rules that are not already present.
                val rules = RoutingMigration.effectiveRulesets(settings)
                val fresh = RulesetPresets.bypassSanctions().filterNot { candidate ->
                    rules.any { it.sameMatcher(candidate) }
                }
                if (fresh.isEmpty()) {
                    platform.toast(duplicateMsg)
                } else {
                    onUpdate(rulesetUpdate(settings) { it + fresh })
                }
            }
            SettingsInfoRow(
                title = stringResource(Res.string.bpb_dns_bootstrap_title),
                body = stringResource(Res.string.bpb_dns_bootstrap_note),
            )
        }

        // Tor — BPB's Tor section leads with "Get bridges" (Moat).
        SettingsGroup(stringResource(Res.string.bpb_section_tor)) {
            SettingsSegment {
                Column(Modifier.padding(16.dp)) {
                    MoatFetchButton(moatFetch, onUpdate)
                    MoatFetchStatus(moatFetch)
                }
            }
        }

        // Geo files — the Assets page's download (SHA-256 pinned since
        // cluster 2) plus per-file status.
        SettingsGroup(stringResource(Res.string.assets_title)) {
            if (geo != null) {
                geoAssets.forEach { asset ->
                    SettingsInfoRow(
                        title = asset.name,
                        body = if (asset.present) {
                            formatBytes(asset.sizeBytes)
                        } else {
                            stringResource(Res.string.assets_missing)
                        },
                    )
                }
                SettingsMenuRow(
                    title = stringResource(Res.string.assets_download),
                    subtitle = stringResource(Res.string.assets_subtitle),
                    trailing = if (geoBusy) {
                        { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                ) {
                    if (!geoBusy) {
                        scope.launch {
                            geoBusy = true
                            val ok = geo.downloadAll(settings.geoFilesSource)
                            geoAssets = geo.state()
                            geoBusy = false
                            platform.toast(if (ok) assetsDoneMsg else assetsFailedMsg)
                        }
                    }
                }
            }
            SettingsInfoRow(
                title = stringResource(Res.string.bpb_geo_verified_title),
                body = stringResource(Res.string.bpb_geo_verified_note),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[i]}"
}
