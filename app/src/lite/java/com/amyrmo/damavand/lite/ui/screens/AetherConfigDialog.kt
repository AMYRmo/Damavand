package com.amyrmo.damavand.lite.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.amyrmo.damavand.lite.aether.AetherFingerprint
import com.amyrmo.damavand.lite.aether.AetherKeyKind
import com.amyrmo.damavand.lite.aether.AetherKeys
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
 * one in place. 3.2.11: Psiphon modes work — the client is packaged
 * again and the server list ships in the app (PsiphonServerList).
 */
@Composable
fun AetherAddDialog(vm: LiteViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    AetherConfigEditor(
        vm = vm,
        initialName = "",
        initial = AetherSettings(),
        profiles = profiles,
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
    val profiles by vm.profiles.collectAsState()
    AetherConfigEditor(
        vm = vm,
        initialName = profile.name,
        initial = initial,
        profiles = profiles,
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
    vm: LiteViewModel,
    initialName: String,
    initial: AetherSettings,
    profiles: List<Profile>,
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
    // 3.2.11 (PattNG ServerAetherKeysActivity): the WARP keys page opens
    // from the Aether editor, as in PattNG — a dialog, not a new screen,
    // so the Summit UI stays.
    var showKeys by remember { mutableStateOf(false) }

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
                // 3.2.11 (PattNG ExitNodeField): the exit node is picked
                // from the non-Aether profiles, not typed free-form, so
                // it can never name a profile that does not exist.
                AetherPickerRow(
                    labelRes = R.string.aether_exit_node,
                    options = profiles.filter { !it.isAether }.map { it.name },
                    selected = exitNode,
                    onSelect = { exitNode = it }
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
                TextButton(
                    onClick = { showKeys = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.aether_keys_get_keys),
                        fontSize = 14.sp,
                        color = LiteColors.accent,
                        fontFamily = Vazirmatn
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 3.2.13: save over the settings this editor opened, so
                // what it does not show — the two-hop endpoints, the
                // fragment/ECH/Psiphon/Tor details, the final mask and
                // dial mode an imported aether:// link carried — survives
                // an edit instead of being silently dropped.
                onSave(
                    name.trim().ifEmpty { "Aether" },
                    initial.copy(
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
    if (showKeys) {
        AetherKeysDialog(vm = vm, profiles = profiles, onDismiss = { showKeys = false })
    }
}

/**
 * The WARP keys page (3.2.11, PattNG ServerAetherKeysActivity): the keys
 * in use, the settings of the renewal run, the command it builds, and
 * the log of the run. A dialog, not a screen, so the Summit UI stays.
 * The settings are the page's alone — no profile's settings reach them.
 */
@Composable
private fun AetherKeysDialog(vm: LiteViewModel, profiles: List<Profile>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.aetherKeysSettings.collectAsState()
    val keys by vm.aetherKeys.collectAsState()
    val log by vm.aetherKeysLog.collectAsState()
    val renewing by vm.aetherKeysRenewing.collectAsState()
    val notice by vm.aetherKeysNotice.collectAsState()
    LaunchedEffect(Unit) { vm.loadAetherKeys() }
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearAetherKeysNotice()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.aether_keys_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (keys.isEmpty()) {
                    Text(
                        text = stringResource(R.string.aether_keys_no_keys),
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    keys.forEach { key ->
                        Text(
                            text = key.file,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn
                        )
                        Text(
                            text = key.identity?.deviceId ?: "—",
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                        Text(
                            text = "v4: " + (key.identity?.ipv4?.ifEmpty { "—" } ?: "—"),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                        Text(
                            text = "v6: " + (key.identity?.ipv6?.ifEmpty { "—" } ?: "—"),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                AetherCycleRow(
                    labelRes = R.string.aether_keys_kind,
                    options = listOf("all", "wg", "masque", "gool", "mim", "wg-over-masque"),
                    selected = settings.kind.type,
                    defaultLabel = "all",
                    onSelect = { vm.setAetherKeysSettings(settings.copy(kind = AetherKeyKind.fromString(it))) }
                )
                AetherField(
                    labelRes = R.string.aether_keys_enroll_address,
                    value = settings.enrollAddress,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(enrollAddress = it)) }
                )
                AetherField(
                    labelRes = R.string.aether_keys_api_sni,
                    value = settings.apiSni,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(apiSni = it)) }
                )
                AetherField(
                    labelRes = R.string.aether_keys_api_host,
                    value = settings.apiHost,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(apiHost = it)) }
                )
                AetherSwitchRow(
                    labelRes = R.string.aether_fragment,
                    checked = settings.fragment,
                    onChange = { vm.setAetherKeysSettings(settings.copy(fragment = it)) }
                )
                if (settings.fragment) {
                    AetherField(
                        labelRes = R.string.aether_keys_fragment_size,
                        value = settings.fragmentSize,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(fragmentSize = it)) }
                    )
                    AetherField(
                        labelRes = R.string.aether_keys_fragment_delay,
                        value = settings.fragmentDelay,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(fragmentDelay = it)) }
                    )
                }
                AetherSwitchRow(
                    labelRes = R.string.aether_ech,
                    checked = settings.ech,
                    onChange = { vm.setAetherKeysSettings(settings.copy(ech = it)) }
                )
                if (settings.ech) {
                    AetherField(
                        labelRes = R.string.aether_keys_ech_dns,
                        value = settings.echDns,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(echDns = it)) }
                    )
                    AetherField(
                        labelRes = R.string.aether_keys_ech_domain,
                        value = settings.echDomain,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(echDomain = it)) }
                    )
                }
                AetherCycleRow(
                    labelRes = R.string.aether_fingerprint,
                    options = listOf("chrome", "firefox", "semi-python", "go"),
                    selected = settings.fingerprint.type,
                    defaultLabel = "chrome",
                    onSelect = { vm.setAetherKeysSettings(settings.copy(fingerprint = AetherFingerprint.fromString(it))) }
                )
                AetherPickerRow(
                    labelRes = R.string.aether_exit_node,
                    options = profiles.filter { !it.isAether }.map { it.name },
                    selected = settings.exitNode,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(exitNode = it)) }
                )
                AetherField(
                    labelRes = R.string.aether_keys_final_mask,
                    value = settings.finalMask,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(finalMask = it)) }
                )
                AetherField(
                    labelRes = R.string.aether_keys_dial_mode,
                    value = settings.dialMode,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(dialMode = it)) }
                )
                AetherField(
                    labelRes = R.string.aether_command,
                    value = settings.command,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(command = it)) },
                    singleLine = false,
                    minLines = 3
                )
                Text(
                    text = stringResource(R.string.aether_command_hint),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.aether_keys_built_command),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
                Text(
                    text = AetherKeys.builtCommand(settings),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (log.isEmpty()) {
                    Text(
                        text = stringResource(R.string.aether_keys_log_empty),
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                } else {
                    log.takeLast(30).forEach { line ->
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (renewing) vm.cancelAetherKeysRenewal() else vm.renewAetherKeys() },
                enabled = true
            ) {
                Text(
                    text = stringResource(
                        if (renewing) R.string.sub_import_cancel else R.string.aether_keys_renew
                    ),
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

/** Dropdown picker row (3.2.11, PattNG ExitNodeField): label + the picked
 * value; tap opens a menu with "none" first, then the options. A saved
 * value that is no longer among the options is still shown as-is. */
@Composable
private fun AetherPickerRow(
    labelRes: Int,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = true }),
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
                text = selected.ifEmpty { stringResource(R.string.aether_default, "—") },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.accent,
                fontFamily = Vazirmatn
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.aether_default, "—"),
                        fontSize = 13.sp,
                        fontFamily = Vazirmatn,
                        color = LiteColors.text
                    )
                },
                onClick = { onSelect(""); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            fontFamily = Vazirmatn,
                            color = LiteColors.text
                        )
                    },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
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
