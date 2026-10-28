@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.domain.config.AetherProfile
import com.amyrmo.damavand.ui.components.PickerField

/**
 * The Aether config screen, architecture copied from PattNG's AETHER page:
 * remarks / Protocol / Scan mode / Obfuscation / exit-node (+ finalMask, dialMode)
 * / Psiphon (chain modes) / Tor (chain modes) / endpoint / IP version /
 * Other settings, plus a live "Aether command" preview.
 */
@Composable
fun AetherSheet(
    onDismiss: () -> Unit,
    onSave: (AetherProfile) -> Unit,
    initial: AetherProfile? = null,
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: "masque") }
    var scanMode by remember { mutableStateOf(initial?.scanMode ?: "balanced") }
    var noize by remember { mutableStateOf(initial?.noize ?: "") }
    var ipVersion by remember { mutableStateOf(initial?.ipVersion ?: "v4") }
    var psiphon by remember { mutableStateOf(initial?.psiphon ?: "off") }
    var psiphonMode by remember { mutableStateOf(initial?.psiphonMode ?: "auto") }
    var psiphonCdnSets by remember { mutableStateOf(initial?.psiphonCdnSets ?: "") }
    var psiphonRegion by remember { mutableStateOf(initial?.psiphonRegion ?: "") }
    var tor by remember { mutableStateOf(initial?.tor ?: "off") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var port by remember { mutableStateOf(initial?.port ?: "") }
    var masqueTransport by remember { mutableStateOf(initial?.masqueTransport ?: "") }
    var exitNode by remember { mutableStateOf(initial?.exitNode ?: "") }
    var exitMaskJson by remember { mutableStateOf(initial?.exitMaskJson ?: "") }
    var exitDialMode by remember { mutableStateOf(initial?.exitDialMode ?: "") }
    var extraArgs by remember { mutableStateOf(initial?.extraArgs ?: "") }
    var rawCommand by remember { mutableStateOf("") }
    var showOther by remember { mutableStateOf(false) }

    fun applyCommand() {
        if (rawCommand.isBlank()) return
        val parsed = AetherProfile.fromCommandLine(rawCommand)
        remarks = parsed.remarks.ifBlank { remarks }
        protocol = parsed.protocol
        scanMode = parsed.scanMode
        noize = parsed.noize
        ipVersion = parsed.ipVersion
        psiphon = parsed.psiphon
        psiphonMode = parsed.psiphonMode
        psiphonCdnSets = parsed.psiphonCdnSets
        psiphonRegion = parsed.psiphonRegion
        tor = parsed.tor
        address = parsed.address
        port = parsed.port
        masqueTransport = parsed.masqueTransport
        exitNode = parsed.exitNode
        exitMaskJson = parsed.exitMaskJson
        exitDialMode = parsed.exitDialMode
        extraArgs = parsed.extraArgs
        rawCommand = ""
    }

    val preview = remember(
        protocol, scanMode, noize, ipVersion, psiphon, psiphonMode, psiphonCdnSets,
        psiphonRegion, tor, address, port, masqueTransport, exitNode, exitMaskJson,
        exitDialMode, extraArgs,
    ) {
        AetherProfile(
            remarks = remarks, protocol = protocol, scanMode = scanMode, noize = noize,
            ipVersion = ipVersion, psiphon = psiphon, psiphonMode = psiphonMode,
            psiphonCdnSets = psiphonCdnSets, psiphonRegion = psiphonRegion, tor = tor,
            address = address, port = port, masqueTransport = masqueTransport,
            exitNode = exitNode, exitMaskJson = exitMaskJson, exitDialMode = exitDialMode,
            extraArgs = extraArgs,
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
                    "masque" to "MASQUE",
                    "wg" to "WireGuard",
                    "gool" to "WARP-in-WARP",
                    "mim" to "MASQUE-in-MASQUE",
                ),
                selected = protocol,
                onSelect = { protocol = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_scan),
                options = listOf(
                    "turbo" to "Turbo",
                    "balanced" to "Balanced",
                    "thorough" to "Thorough",
                    "verified" to "Verified",
                    "ironclad" to "Ironclad",
                ),
                selected = scanMode,
                onSelect = { scanMode = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_obfuscation),
                options = listOf(
                    "" to stringResource(Res.string.aether_auto),
                    "off" to stringResource(Res.string.aether_off),
                    "light" to "Light",
                    "firewall" to "Firewall",
                    "balanced" to "Balanced",
                    "gfw" to "GFW",
                    "aggressive" to "Aggressive",
                ),
                selected = noize,
                onSelect = { noize = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_exit_node),
                options = listOf(
                    "freedom" to "freedom (default)",
                    "!IR" to "!IR",
                    "!IR,AZ,RU" to "!IR,AZ,RU",
                    "DE,SE" to "DE,SE",
                    "US" to "US",
                ),
                selected = exitNode,
                onSelect = { exitNode = it },
            )
            if (exitNode.isNotBlank() && exitNode != "freedom") {
                F(exitMaskJson, Res.string.aether_exit_mask, minLines = 2) { exitMaskJson = it }
                F(exitDialMode, Res.string.aether_exit_dial) { exitDialMode = it }
            }
            PickerField(
                label = "Psiphon",
                options = listOf(
                    "off" to stringResource(Res.string.aether_off),
                    "chain" to "WARP → Psiphon",
                    "reverse" to "Psiphon → WARP",
                    "only" to "Psiphon only",
                ),
                selected = psiphon,
                onSelect = { psiphon = it },
            )
            if (psiphon != "off") {
                PickerField(
                    label = stringResource(Res.string.aether_psiphon_mode),
                    options = listOf(
                        "auto" to "Auto",
                        "cdn" to "CDN",
                        "direct" to "Direct",
                    ),
                    selected = psiphonMode,
                    onSelect = { psiphonMode = it },
                )
                F(psiphonCdnSets, Res.string.aether_cdn_sets, placeholder = "cloudflare,fastly,...") { psiphonCdnSets = it }
                F(psiphonRegion, Res.string.aether_psiphon_region, placeholder = "US") { psiphonRegion = it }
            }
            PickerField(
                label = "Tor",
                options = listOf(
                    "off" to stringResource(Res.string.aether_off),
                    "chain" to "WARP → Tor",
                    "reverse" to "Tor → WARP",
                    "only" to "Tor only",
                ),
                selected = tor,
                onSelect = { tor = it },
            )
            F(address, Res.string.manual_address, placeholder = "188.114.96.1") { address = it }
            F(port, Res.string.ssh_port, numeric = true, placeholder = "1701") { port = it }
            PickerField(
                label = stringResource(Res.string.aether_ip),
                options = listOf(
                    "v4" to "IPv4",
                    "v6" to "IPv6",
                    "both" to "IPv4 + IPv6",
                ),
                selected = ipVersion,
                onSelect = { ipVersion = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { address = ""; port = "" },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(Res.string.aether_scan_endpoint)) }
                OutlinedButton(
                    onClick = { showOther = !showOther },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(Res.string.aether_other)) }
            }

            if (showOther) {
                PickerField(
                    label = stringResource(Res.string.aether_masque_transport),
                    options = listOf(
                        "" to stringResource(Res.string.aether_auto),
                        "h2" to "HTTP/2 (TCP)",
                        "h3" to "HTTP/3 (QUIC)",
                    ),
                    selected = masqueTransport,
                    onSelect = { masqueTransport = it },
                )
                F(extraArgs, Res.string.aether_extra, minLines = 2) { extraArgs = it }
            }

            Text(stringResource(Res.string.aether_command_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(preview, style = MaterialTheme.typography.bodySmall)
            F(rawCommand, Res.string.aether_command_hint, minLines = 2) { rawCommand = it }
            OutlinedButton(
                onClick = { applyCommand() },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(Res.string.aether_command_apply)) }

            Button(
                onClick = {
                    onSave(
                        AetherProfile(
                            remarks = remarks.trim(),
                            protocol = protocol,
                            scanMode = scanMode,
                            noize = noize,
                            ipVersion = ipVersion,
                            psiphon = psiphon,
                            psiphonMode = psiphonMode,
                            psiphonCdnSets = psiphonCdnSets.trim(),
                            psiphonRegion = psiphonRegion.trim(),
                            tor = tor,
                            address = address.trim(),
                            port = port.trim(),
                            masqueTransport = masqueTransport,
                            exitNode = exitNode,
                            exitMaskJson = exitMaskJson.trim(),
                            exitDialMode = exitDialMode.trim(),
                            extraArgs = extraArgs.trim(),
                        ),
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun F(value: String, labelRes: StringResource, placeholder: String? = null, minLines: Int = 1, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
