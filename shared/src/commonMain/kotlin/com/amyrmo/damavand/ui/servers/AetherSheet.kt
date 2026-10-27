@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.domain.config.AetherProfile
import com.amyrmo.damavand.ui.components.PickerField

/**
 * PattNG-style adjustable Aether config: protocol, scan, obfuscation, exit node,
 * Psiphon, Tor, endpoint, IP version + raw command import.
 */
@Composable
fun AetherSheet(
    onDismiss: () -> Unit,
    onSave: (AetherProfile) -> Unit,
    initial: AetherProfile? = null,
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: "wg") }
    var scanMode by remember { mutableStateOf(initial?.scanMode ?: "balanced") }
    var obfuscation by remember { mutableStateOf(initial?.obfuscation ?: "automatic") }
    var exitNode by remember { mutableStateOf(initial?.exitNode ?: "freedom") }
    var exitMaskJson by remember { mutableStateOf(initial?.exitMaskJson ?: "") }
    var exitDialMode by remember { mutableStateOf(initial?.exitDialMode ?: "") }
    var psiphon by remember { mutableStateOf(initial?.psiphon ?: "off") }
    var psiphonRegion by remember { mutableStateOf(initial?.psiphonRegion ?: "") }
    var tor by remember { mutableStateOf(initial?.tor ?: "off") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var port by remember { mutableStateOf(initial?.port ?: "") }
    var ipVersion by remember { mutableStateOf(initial?.ipVersion ?: "v4") }
    var extraArgs by remember { mutableStateOf(initial?.extraArgs ?: "") }
    var rawCommand by remember { mutableStateOf("") }

    fun applyCommand() {
        if (rawCommand.isBlank()) return
        val parsed = AetherProfile.fromCommandLine(rawCommand)
        remarks = parsed.remarks.ifBlank { remarks }
        protocol = parsed.protocol
        scanMode = parsed.scanMode
        obfuscation = parsed.obfuscation
        exitNode = parsed.exitNode
        exitMaskJson = parsed.exitMaskJson
        exitDialMode = parsed.exitDialMode
        psiphon = parsed.psiphon
        psiphonRegion = parsed.psiphonRegion
        tor = parsed.tor
        address = parsed.address
        port = parsed.port
        ipVersion = parsed.ipVersion
        extraArgs = parsed.extraArgs
        rawCommand = ""
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

            F(remarks, Res.string.manual_remark) { remarks = it }
            PickerField(
                label = stringResource(Res.string.aether_protocol),
                options = listOf(
                    "wg" to "WireGuard",
                    "masque" to "MASQUE",
                    "mim" to "MIM",
                    "gool" to "gool",
                    "tls" to "TLS",
                    "quic" to "QUIC",
                    "hybrid" to "Hybrid",
                ),
                selected = protocol,
                onSelect = { protocol = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_scan),
                options = listOf(
                    "off" to stringResource(Res.string.aether_off),
                    "balanced" to stringResource(Res.string.aether_scan_balanced),
                    "thorough" to stringResource(Res.string.aether_scan_thorough),
                    "turbo" to "Turbo",
                    "perf" to stringResource(Res.string.aether_scan_perf),
                ),
                selected = scanMode,
                onSelect = { scanMode = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_obfuscation),
                options = listOf(
                    "automatic" to stringResource(Res.string.aether_auto),
                    "none" to stringResource(Res.string.aether_none),
                    "ironclad" to "Ironclad",
                    "noize" to "Noize",
                    "http" to "HTTP",
                ),
                selected = obfuscation,
                onSelect = { obfuscation = it },
            )
            PickerField(
                label = stringResource(Res.string.aether_exit_node),
                options = listOf(
                    "freedom" to "freedom (default)",
                    "warp" to "WARP",
                ),
                selected = exitNode,
                onSelect = { exitNode = it },
            )
            if (exitMaskJson.isNotBlank() || exitNode != "freedom") {
                F(exitMaskJson, Res.string.aether_exit_mask, minLines = 3) { exitMaskJson = it }
                F(exitDialMode, Res.string.aether_exit_dial) { exitDialMode = it }
            }
            PickerField(
                label = "Psiphon",
                options = listOf(
                    "off" to stringResource(Res.string.aether_off),
                    "on" to stringResource(Res.string.aether_on),
                    "cdn" to "CDN",
                    "frontend" to "Fronted",
                    "only" to "Psiphon only",
                ),
                selected = psiphon,
                onSelect = { psiphon = it },
            )
            if (psiphon !in setOf("off", "")) {
                F(psiphonRegion, Res.string.aether_psiphon_region, placeholder = "US") { psiphonRegion = it }
            }
            PickerField(
                label = "Tor",
                options = listOf(
                    "off" to stringResource(Res.string.aether_off),
                    "on" to stringResource(Res.string.aether_on),
                    "bridges-only" to stringResource(Res.string.aether_tor_only),
                ),
                selected = tor,
                onSelect = { tor = it },
            )
            F(address, Res.string.manual_address, placeholder = "engage.cloudflareclient.com") { address = it }
            F(port, Res.string.ssh_port, numeric = true, placeholder = "2408") { port = it }
            PickerField(
                label = stringResource(Res.string.aether_ip),
                options = listOf(
                    "v4" to "IPv4",
                    "v6" to "IPv6",
                    "dual" to "Dual",
                ),
                selected = ipVersion,
                onSelect = { ipVersion = it },
            )
            F(extraArgs, Res.string.aether_extra, minLines = 2) { extraArgs = it }

            Text(stringResource(Res.string.aether_command_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                            obfuscation = obfuscation,
                            exitNode = exitNode,
                            exitMaskJson = exitMaskJson.trim(),
                            exitDialMode = exitDialMode.trim(),
                            psiphon = psiphon,
                            psiphonRegion = psiphonRegion.trim(),
                            tor = tor,
                            address = address.trim(),
                            port = port.trim(),
                            ipVersion = ipVersion,
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
