@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.domain.config.AetherEndpoint
import com.amyrmo.damavand.domain.config.AetherFingerprint
import com.amyrmo.damavand.domain.config.AetherIpVersion
import com.amyrmo.damavand.domain.config.AetherObfuscation
import com.amyrmo.damavand.domain.config.AetherProfile
import com.amyrmo.damavand.domain.config.AetherProtocol
import com.amyrmo.damavand.domain.config.AetherPsiphon
import com.amyrmo.damavand.domain.config.AetherPsiphonCdnSet
import com.amyrmo.damavand.domain.config.AetherPsiphonMode
import com.amyrmo.damavand.domain.config.AetherScanMode
import com.amyrmo.damavand.domain.config.AetherTor
import com.amyrmo.damavand.domain.config.AetherTorBridges
import com.amyrmo.damavand.domain.config.AetherTorRelays
import com.amyrmo.damavand.domain.config.AetherTransport
import com.amyrmo.damavand.ui.components.PickerField

/**
 * The Aether config screen, field-for-field PattNG's ServerAetherActivity:
 * remarks, WARP keys, protocol, scan mode, obfuscation, IP version, DNS,
 * exit-node, MASQUE SNI/transport/fragment/ECH/fingerprint, hop endpoints,
 * Psiphon (mode, CDN, region), Tor (bridges, relays, lines), endpoint,
 * finalMask/dialMode and a live command preview.
 */
@Composable
fun AetherSheet(
    onDismiss: () -> Unit,
    onSave: (AetherProfile) -> Unit,
    initial: AetherProfile? = null,
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: AetherProtocol.WIREGUARD.type) }
    var transport by remember { mutableStateOf(initial?.transport ?: AetherTransport.HTTP3.type) }
    var scanMode by remember { mutableStateOf(initial?.scanMode ?: AetherScanMode.BALANCED.type) }
    var obfuscation by remember { mutableStateOf(initial?.obfuscation ?: AetherObfuscation.AUTO.type) }
    var ipVersion by remember { mutableStateOf(initial?.ipVersion ?: AetherIpVersion.V4.type) }
    var dns by remember { mutableStateOf(initial?.dns ?: "") }
    var exitLoc by remember { mutableStateOf(initial?.exitLoc ?: "") }
    var masqueSni by remember { mutableStateOf(initial?.masqueSni ?: "") }
    var fragment by remember { mutableStateOf(initial?.fragment ?: false) }
    var fragmentSize by remember { mutableStateOf(initial?.fragmentSize ?: "") }
    var fragmentDelay by remember { mutableStateOf(initial?.fragmentDelay ?: "") }
    var ech by remember { mutableStateOf(initial?.ech ?: false) }
    var echDns by remember { mutableStateOf(initial?.echDns ?: "") }
    var echDomain by remember { mutableStateOf(initial?.echDomain ?: "") }
    var fingerprint by remember { mutableStateOf(initial?.fingerprint ?: AetherFingerprint.CHROME.type) }
    var wiwOuter by remember { mutableStateOf(initial?.wiwOuter ?: "") }
    var wiwInner by remember { mutableStateOf(initial?.wiwInner ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var port by remember { mutableStateOf(initial?.port ?: "") }
    var psiphon by remember { mutableStateOf(initial?.psiphon ?: AetherPsiphon.OFF.type) }
    var psiphonMode by remember { mutableStateOf(initial?.psiphonMode ?: AetherPsiphonMode.AUTO.type) }
    var psiphonCdnIps by remember { mutableStateOf(initial?.psiphonCdnIps ?: "") }
    var psiphonCdnSni by remember { mutableStateOf(initial?.psiphonCdnSni ?: "") }
    var psiphonCdnSets by remember { mutableStateOf(initial?.psiphonCdnSets ?: "") }
    var psiphonRegion by remember { mutableStateOf(initial?.psiphonRegion ?: "") }
    var tor by remember { mutableStateOf(initial?.tor ?: AetherTor.OFF.type) }
    var torBridges by remember { mutableStateOf(initial?.torBridges ?: AetherTorBridges.AUTO.type) }
    var torRelays by remember { mutableStateOf(initial?.torRelays ?: AetherTorRelays.AUTO.type) }
    var torBridgeLines by remember { mutableStateOf(initial?.torBridgeLines ?: "") }
    var finalMask by remember { mutableStateOf(initial?.finalMask ?: "") }
    var dialMode by remember { mutableStateOf(initial?.dialMode ?: "") }
    var extraArgs by remember { mutableStateOf(initial?.extraArgs ?: "") }
    var rawCommand by remember { mutableStateOf("") }
    var showOther by remember { mutableStateOf(false) }

    fun applyCommand() {
        if (rawCommand.isBlank()) return
        val parsed = AetherProfile.fromCommandLine(rawCommand)
        remarks = parsed.remarks.ifBlank { remarks }
        protocol = parsed.protocol
        transport = parsed.transport
        scanMode = parsed.scanMode
        obfuscation = parsed.obfuscation
        ipVersion = parsed.ipVersion
        dns = parsed.dns
        exitLoc = parsed.exitLoc
        masqueSni = parsed.masqueSni
        fragment = parsed.fragment
        fragmentSize = parsed.fragmentSize
        fragmentDelay = parsed.fragmentDelay
        ech = parsed.ech
        echDns = parsed.echDns
        echDomain = parsed.echDomain
        fingerprint = parsed.fingerprint
        wiwOuter = parsed.wiwOuter
        wiwInner = parsed.wiwInner
        address = parsed.address
        port = parsed.port
        psiphon = parsed.psiphon
        psiphonMode = parsed.psiphonMode
        psiphonCdnIps = parsed.psiphonCdnIps
        psiphonCdnSni = parsed.psiphonCdnSni
        psiphonCdnSets = parsed.psiphonCdnSets
        psiphonRegion = parsed.psiphonRegion
        tor = parsed.tor
        torBridges = parsed.torBridges
        torRelays = parsed.torRelays
        torBridgeLines = parsed.torBridgeLines
        finalMask = parsed.finalMask
        dialMode = parsed.dialMode
        extraArgs = parsed.extraArgs
        rawCommand = ""
    }

    val protoEnum = AetherProtocol.fromString(protocol)
    val psiphonEnum = AetherPsiphon.fromString(psiphon)
    val torEnum = AetherTor.fromString(tor)

    val preview = remember(
        remarks, protocol, transport, scanMode, obfuscation, ipVersion, dns, exitLoc,
        masqueSni, fragment, fragmentSize, fragmentDelay, ech, echDns, echDomain,
        fingerprint, wiwOuter, wiwInner, address, port, psiphon, psiphonMode,
        psiphonCdnIps, psiphonCdnSni, psiphonCdnSets, psiphonRegion, tor, torBridges,
        torRelays, torBridgeLines, finalMask, dialMode, extraArgs,
    ) {
        AetherProfile(
            remarks = remarks, protocol = protocol, transport = transport,
            scanMode = scanMode, obfuscation = obfuscation, ipVersion = ipVersion,
            dns = dns, exitLoc = exitLoc, masqueSni = masqueSni, fragment = fragment,
            fragmentSize = fragmentSize, fragmentDelay = fragmentDelay, ech = ech,
            echDns = echDns, echDomain = echDomain, fingerprint = fingerprint,
            wiwOuter = wiwOuter, wiwInner = wiwInner, address = address, port = port,
            psiphon = psiphon, psiphonMode = psiphonMode, psiphonCdnIps = psiphonCdnIps,
            psiphonCdnSni = psiphonCdnSni, psiphonCdnSets = psiphonCdnSets,
            psiphonRegion = psiphonRegion, tor = tor, torBridges = torBridges,
            torRelays = torRelays, torBridgeLines = torBridgeLines,
            finalMask = finalMask, dialMode = dialMode, extraArgs = extraArgs,
        ).commandPreview(1819)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.aether_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            F(remarks, Res.string.aether_remarks) { remarks = it }
            PickerField(
                label = stringResource(Res.string.aether_protocol),
                options = listOf(
                    AetherProtocol.WIREGUARD.type to "WireGuard",
                    AetherProtocol.MASQUE.type to "MASQUE",
                    AetherProtocol.GOOL.type to "WARP-in-WARP",
                    AetherProtocol.MIM.type to "MASQUE-in-MASQUE",
                    AetherProtocol.WG_OVER_MASQUE.type to "WireGuard over MASQUE",
                ),
                selected = protocol,
                onSelect = { protocol = it },
            )
            if (protoEnum.overMasque) {
                PickerField(
                    label = stringResource(Res.string.aether_masque_transport),
                    options = listOf(
                        AetherTransport.HTTP3.type to "HTTP/3 (QUIC)",
                        AetherTransport.HTTP2.type to "HTTP/2 (TCP)",
                    ),
                    selected = transport,
                    onSelect = { transport = it },
                )
            }
            PickerField(
                label = stringResource(Res.string.aether_scan),
                options = listOf(
                    AetherScanMode.TURBO.type to "Turbo",
                    AetherScanMode.BALANCED.type to "Balanced",
                    AetherScanMode.THOROUGH.type to "Thorough",
                    AetherScanMode.VERIFIED.type to "Verified",
                    AetherScanMode.IRONCLAD.type to "Ironclad",
                ),
                selected = scanMode,
                onSelect = { scanMode = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_obfuscation),
                options = listOf(
                    AetherObfuscation.AUTO.type to stringResource(Res.string.aether_auto),
                    AetherObfuscation.OFF.type to stringResource(Res.string.aether_off),
                    AetherObfuscation.LIGHT.type to "Light",
                    AetherObfuscation.FIREWALL.type to "Firewall",
                    AetherObfuscation.BALANCED.type to "Balanced",
                    AetherObfuscation.GFW.type to "GFW",
                    AetherObfuscation.AGGRESSIVE.type to "Aggressive",
                ),
                selected = obfuscation,
                onSelect = { obfuscation = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_ip),
                options = listOf(
                    AetherIpVersion.V4.type to "IPv4",
                    AetherIpVersion.V6.type to "IPv6",
                    AetherIpVersion.DUAL.type to "IPv4 + IPv6",
                ),
                selected = ipVersion,
                onSelect = { ipVersion = it },
            )
            F(dns, Res.string.aether_dns, placeholder = "1.1.1.1") { dns = it }

            PickerField(
                label = stringResource(Res.string.aether_exit_node),
                options = listOf(
                    "" to "default",
                    "freedom" to "freedom",
                    "!IR" to "!IR",
                    "!IR,AZ,RU" to "!IR,AZ,RU",
                    "DE,SE" to "DE,SE",
                    "US" to "US",
                ),
                selected = exitLoc,
                onSelect = { exitLoc = it },
            )
            if (exitLoc.isNotBlank()) {
                F(finalMask, Res.string.aether_exit_mask, minLines = 2) { finalMask = it }
                F(dialMode, Res.string.aether_exit_dial) { dialMode = it }
            }

            if (protoEnum.overMasque) {
                Text(stringResource(Res.string.aether_masque_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                F(masqueSni, Res.string.aether_masque_sni) { masqueSni = it }
                if (transport == AetherTransport.HTTP2.type) {
                    PickerField(
                        label = stringResource(Res.string.aether_fragment),
                        options = listOf("off" to stringResource(Res.string.aether_off), "on" to stringResource(Res.string.action_on)),
                        selected = if (fragment) "on" else "off",
                        onSelect = { fragment = it == "on" },
                    )
                    if (fragment) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            F(fragmentSize, Res.string.aether_fragment_size, modifier = Modifier.weight(1f)) { fragmentSize = it }
                            F(fragmentDelay, Res.string.aether_fragment_delay, modifier = Modifier.weight(1f)) { fragmentDelay = it }
                        }
                    }
                }
                PickerField(
                    label = stringResource(Res.string.aether_ech),
                    options = listOf("off" to stringResource(Res.string.aether_off), "on" to stringResource(Res.string.action_on)),
                    selected = if (ech) "on" else "off",
                    onSelect = { ech = it == "on" },
                )
                if (ech) {
                    F(echDns, Res.string.aether_ech_dns) { echDns = it }
                    F(echDomain, Res.string.aether_ech_domain) { echDomain = it }
                }
                PickerField(
                    label = stringResource(Res.string.aether_fingerprint),
                    options = listOf(
                        AetherFingerprint.CHROME.type to "Chrome",
                        AetherFingerprint.CHROME_120.type to "Chrome 120",
                        AetherFingerprint.FIREFOX.type to "Firefox",
                        AetherFingerprint.SEMI_PYTHON.type to "Semi-Python",
                        AetherFingerprint.GO.type to "Go",
                    ),
                    selected = fingerprint,
                    onSelect = { fingerprint = it },
                )
            }

            if (protoEnum.twoHops) {
                Text(stringResource(Res.string.aether_hops_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                F(wiwOuter, Res.string.aether_outer, placeholder = "1.2.3.4:443 (blank = scan)") { wiwOuter = it }
                F(wiwInner, Res.string.aether_inner, placeholder = "5.6.7.8:443 (blank = scan)") { wiwInner = it }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    F(address, Res.string.manual_address, modifier = Modifier.weight(1f)) { address = it }
                    F(port, Res.string.ssh_port, numeric = true, modifier = Modifier.weight(1f)) { port = it }
                }
                OutlinedButton(
                    onClick = { address = ""; port = "" },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(stringResource(Res.string.aether_scan_endpoint)) }
            }

            PickerField(
                label = "Psiphon",
                options = listOf(
                    AetherPsiphon.OFF.type to stringResource(Res.string.aether_off),
                    AetherPsiphon.CHAIN.type to "WARP → Psiphon",
                    AetherPsiphon.REVERSE.type to "Psiphon → WARP",
                    AetherPsiphon.ONLY.type to "Psiphon only",
                ),
                selected = psiphon,
                onSelect = { psiphon = it },
            )
            if (psiphonEnum != AetherPsiphon.OFF) {
                PickerField(
                    label = stringResource(Res.string.aether_psiphon_mode),
                    options = listOf(
                        AetherPsiphonMode.AUTO.type to "Auto",
                        AetherPsiphonMode.CDN.type to "CDN",
                        AetherPsiphonMode.DIRECT.type to "Direct",
                    ),
                    selected = psiphonMode,
                    onSelect = { psiphonMode = it },
                )
                if (psiphonMode != AetherPsiphonMode.DIRECT.type) {
                    F(psiphonCdnIps, Res.string.aether_cdn_ips, placeholder = " IPs") { psiphonCdnIps = it }
                    F(psiphonCdnSni, Res.string.aether_cdn_sni) { psiphonCdnSni = it }
                    F(psiphonCdnSets, Res.string.aether_cdn_sets, placeholder = "cloudflare,fastly,...") { psiphonCdnSets = it }
                    OutlinedButton(
                        onClick = { psiphonCdnSets = AetherPsiphonCdnSet.entries.joinToString(",") { it.type } },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(stringResource(Res.string.aether_cdn_all)) }
                }
                F(psiphonRegion, Res.string.aether_psiphon_region, placeholder = "US") { psiphonRegion = it }
            }

            PickerField(
                label = "Tor",
                options = listOf(
                    AetherTor.OFF.type to stringResource(Res.string.aether_off),
                    AetherTor.CHAIN.type to "WARP → Tor",
                    AetherTor.REVERSE.type to "Tor → WARP",
                    AetherTor.ONLY.type to "Tor only",
                ),
                selected = tor,
                onSelect = { tor = it },
            )
            if (torEnum != AetherTor.OFF) {
                PickerField(
                    label = stringResource(Res.string.aether_tor_bridges),
                    options = listOf(
                        AetherTorBridges.AUTO.type to "Auto",
                        AetherTorBridges.FIRST.type to "Bridges first",
                        AetherTorBridges.NEVER.type to "Never",
                        AetherTorBridges.OWN.type to "Own bridges",
                    ),
                    selected = torBridges,
                    onSelect = { torBridges = it },
                )
                if (torBridges == AetherTorBridges.OWN.type) {
                    F(torBridgeLines, Res.string.aether_bridge_lines, minLines = 3) { torBridgeLines = it }
                }
                if (torBridges == AetherTorBridges.AUTO.type || torBridges == AetherTorBridges.FIRST.type) {
                    PickerField(
                        label = stringResource(Res.string.aether_tor_relays),
                        options = listOf(
                            AetherTorRelays.AUTO.type to "Auto",
                            AetherTorRelays.ONLY.type to "Relays only",
                            AetherTorRelays.OFF.type to "bridgedb only",
                        ),
                        selected = torRelays,
                        onSelect = { torRelays = it },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showOther = !showOther },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                ) { Text(stringResource(Res.string.aether_other)) }
            }
            if (showOther) {
                F(extraArgs, Res.string.aether_extra, minLines = 2) { extraArgs = it }
            }

            Text(stringResource(Res.string.aether_command_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(preview, style = MaterialTheme.typography.bodySmall)
            F(rawCommand, Res.string.aether_command_hint, minLines = 2) { rawCommand = it }
            OutlinedButton(
                onClick = { applyCommand() },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) { Text(stringResource(Res.string.aether_command_apply)) }

            Button(
                onClick = {
                    onSave(
                        AetherProfile(
                            remarks = remarks.trim(),
                            protocol = protocol,
                            transport = transport,
                            scanMode = scanMode,
                            obfuscation = obfuscation,
                            ipVersion = ipVersion,
                            dns = dns.trim(),
                            exitLoc = exitLoc.trim(),
                            masqueSni = masqueSni.trim(),
                            fragment = fragment,
                            fragmentSize = fragmentSize.trim(),
                            fragmentDelay = fragmentDelay.trim(),
                            ech = ech,
                            echDns = echDns.trim(),
                            echDomain = echDomain.trim(),
                            fingerprint = fingerprint,
                            wiwOuter = wiwOuter.trim(),
                            wiwInner = wiwInner.trim(),
                            address = address.trim(),
                            port = port.trim(),
                            psiphon = psiphon,
                            psiphonMode = psiphonMode,
                            psiphonCdnIps = psiphonCdnIps.trim(),
                            psiphonCdnSni = psiphonCdnSni.trim(),
                            psiphonCdnSets = psiphonCdnSets.trim(),
                            psiphonRegion = psiphonRegion.trim(),
                            tor = tor,
                            torBridges = torBridges,
                            torRelays = torRelays,
                            torBridgeLines = torBridgeLines.trim(),
                            finalMask = finalMask.trim(),
                            dialMode = dialMode.trim(),
                            extraArgs = extraArgs.trim(),
                        ),
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun F(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    minLines: Int = 1,
    numeric: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = modifier,
    )
}
