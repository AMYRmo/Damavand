package com.amyrmo.damavand.lite.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.aether.AetherSettings
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Aether profile editor (3.2.10, PattNG): the settings of one Aether
 * profile, field by field — protocol, transport, scan, obfuscation,
 * fingerprint, MASQUE SNI, DNS, exit location, exit node, Tor and
 * Psiphon, plus the raw command line that overrides them all. The
 * settings are stored as JSON on the profile (aetherJson); the
 * argument builder reads them at connect time. Null means the core's
 * own default — the cycle rows offer "" first for exactly that.
 *
 * [AetherAddDialog] adds a fresh profile, [AetherEditDialog] edits
 * one in place. Psiphon modes are refused at connect in this build
 * (the Psiphon client is not packaged — see the service), so the
 * dialog says so under the Psiphon row when one is picked.
 */
@Composable
fun AetherAddDialog(vm: LiteViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AetherConfigEditor(
        initialName = "",
        initial = AetherSettings(),
        titleRes = R.string.aether_add_title,
        confirmRes = R.string.manual_add,
        onSave = { name, settings ->
            vm.addAetherProfile(name, settings) { profile ->
                if (profile != null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.manual_added),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
fun AetherEditDialog(profile: Profile, vm: LiteViewModel, onDismiss: () -> Unit) {
    val initial = remember(profile.aetherJson) { AetherSettings.fromJson(profile.aetherJson) }
    AetherConfigEditor(
        initialName = profile.name,
        initial = initial,
        titleRes = R.string.aether_edit_title,
        confirmRes = R.string.server_edit_save,
        onSave = { name, settings ->
            vm.updateAetherProfile(profile.id, name, settings)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun AetherConfigEditor(
    initialName: String,
    initial: AetherSettings,
    titleRes: Int,
    confirmRes: Int,
    onSave: (String, AetherSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var protocol by remember { mutableStateOf(initial.protocol.orEmpty()) }
    var transport by remember { mutableStateOf(initial.transport.orEmpty()) }
    var scanMode by remember { mutableStateOf(initial.scanMode.orEmpty()) }
    var obfuscation by remember { mutableStateOf(initial.obfuscation.orEmpty()) }
    var fingerprint by remember { mutableStateOf(initial.fingerprint.orEmpty()) }
    var ipVersion by remember { mutableStateOf(initial.ipVersion.orEmpty()) }
    var masqueSni by remember { mutableStateOf(initial.masqueSni.orEmpty()) }
    var dns by remember { mutableStateOf(initial.dns.orEmpty()) }
    var exitLoc by remember { mutableStateOf(initial.exitLoc.orEmpty()) }
    var exitNode by remember { mutableStateOf(initial.exitNode.orEmpty()) }
    var server by remember { mutableStateOf(initial.server.orEmpty()) }
    var serverPort by remember { mutableStateOf(initial.serverPort.orEmpty()) }
    var fragment by remember { mutableStateOf(initial.fragment ?: false) }
    var ech by remember { mutableStateOf(initial.ech ?: false) }
    var tor by remember { mutableStateOf(initial.tor.orEmpty()) }
    var psiphon by remember { mutableStateOf(initial.psiphon.orEmpty()) }
    var command by remember { mutableStateOf(initial.command.orEmpty()) }

    AlertDialog(
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AetherField(
                    labelRes = R.string.manual_name,
                    value = name,
                    onValueChange = { name = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_protocol,
                    options = listOf("", "masque", "wg", "gool", "mim", "wg-over-masque"),
                    selected = protocol,
                    defaultLabel = "wg",
                    onSelect = { protocol = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_transport,
                    options = listOf("", "h3", "h2"),
                    selected = transport,
                    defaultLabel = "h3",
                    onSelect = { transport = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_scan,
                    options = listOf("", "turbo", "balanced", "thorough", "verified"),
                    selected = scanMode,
                    defaultLabel = "balanced",
                    onSelect = { scanMode = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_obfuscation,
                    options = listOf("", "auto", "off", "light", "firewall", "balanced", "gfw", "aggressive"),
                    selected = obfuscation,
                    defaultLabel = "auto",
                    onSelect = { obfuscation = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_fingerprint,
                    options = listOf("", "chrome", "firefox", "semi-python", "go"),
                    selected = fingerprint,
                    defaultLabel = "chrome",
                    onSelect = { fingerprint = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_ip_version,
                    options = listOf("", "v4", "v6", "both"),
                    selected = ipVersion,
                    defaultLabel = "v4",
                    onSelect = { ipVersion = it }
                )
                AetherField(
                    labelRes = R.string.aether_masque_sni,
                    value = masqueSni,
                    onValueChange = { masqueSni = it }
                )
                AetherField(
                    labelRes = R.string.aether_dns,
                    value = dns,
                    onValueChange = { dns = it }
                )
                AetherField(
                    labelRes = R.string.aether_exit_loc,
                    value = exitLoc,
                    onValueChange = { exitLoc = it }
                )
                AetherField(
                    labelRes = R.string.aether_exit_node,
                    value = exitNode,
                    onValueChange = { exitNode = it }
                )
                Text(
                    text = stringResource(R.string.aether_exit_node_hint),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AetherField(
                    labelRes = R.string.aether_server,
                    value = server,
                    onValueChange = { server = it }
                )
                AetherField(
                    labelRes = R.string.aether_server_port,
                    value = serverPort,
                    onValueChange = { serverPort = it }
                )
                AetherSwitchRow(
                    labelRes = R.string.aether_fragment,
                    checked = fragment,
                    onChange = { fragment = it }
                )
                AetherSwitchRow(
                    labelRes = R.string.aether_ech,
                    checked = ech,
                    onChange = { ech = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_tor,
                    options = listOf("", "off", "chain", "reverse", "only"),
                    selected = tor,
                    defaultLabel = "off",
                    onSelect = { tor = it }
                )
                AetherCycleRow(
                    labelRes = R.string.aether_psiphon,
                    options = listOf("", "off", "chain", "reverse", "only"),
                    selected = psiphon,
                    defaultLabel = "off",
                    onSelect = { psiphon = it }
                )
                if (psiphon.isNotEmpty() && psiphon != "off") {
                    Text(
                        text = stringResource(R.string.aether_psiphon_hint),
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                AetherField(
                    labelRes = R.string.aether_command,
                    value = command,
                    onValueChange = { command = it },
                    singleLine = false,
                    minLines = 3
                )
                Text(
                    text = stringResource(R.string.aether_command_hint),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    name.trim().ifEmpty { "Aether" },
                    AetherSettings(
                        protocol = protocol.ifEmpty { null },
                        transport = transport.ifEmpty { null },
                        masqueSni = masqueSni.ifEmpty { null },
                        scanMode = scanMode.ifEmpty { null },
                        obfuscation = obfuscation.ifEmpty { null },
                        fingerprint = fingerprint.ifEmpty { null },
                        ipVersion = ipVersion.ifEmpty { null },
                        fragment = fragment,
                        ech = ech,
                        dns = dns.ifEmpty { null },
                        exitLoc = exitLoc.ifEmpty { null },
                        exitNode = exitNode.ifEmpty { null },
                        psiphon = psiphon.ifEmpty { null },
                        tor = tor.ifEmpty { null },
                        command = command.ifEmpty { null },
                        server = server.ifEmpty { null },
                        serverPort = serverPort.ifEmpty { null },
                    )
                )
            }) {
                Text(
                    text = stringResource(confirmRes),
                    fontSize = 14.sp,
                    color = LiteColors.accent,
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

/** Tap-to-cycle row (like the manual editor's ManualCycleRow). */
@Composable
private fun AetherCycleRow(
    labelRes: Int,
    options: List<String>,
    selected: String,
    defaultLabel: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                val idx = options.indexOf(selected).takeIf { it >= 0 } ?: 0
                onSelect(options[(idx + 1).mod(options.size)])
            })
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = selected.ifEmpty { stringResource(R.string.aether_default, defaultLabel) },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.accent,
            fontFamily = Vazirmatn
        )
    }
}

/** Label + switch row. */
@Composable
private fun AetherSwitchRow(
    labelRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiteColors.accent,
                checkedTrackColor = LiteColors.accent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun AetherField(
    labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyle(
            fontFamily = Vazirmatn,
            fontSize = 14.sp,
            color = LiteColors.text,
            textDirection = TextDirection.Ltr
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LiteColors.hairlineStrong,
            unfocusedBorderColor = LiteColors.hairline,
            cursorColor = LiteColors.accent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}
