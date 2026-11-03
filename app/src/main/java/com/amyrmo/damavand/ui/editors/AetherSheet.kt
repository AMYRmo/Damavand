package com.amyrmo.damavand.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.core.adapters.AetherAdapter
import com.amyrmo.damavand.core.adapters.AetherKeysStatus
import com.amyrmo.damavand.logic.AetherCommands
import com.amyrmo.damavand.logic.AetherFlags
import com.amyrmo.damavand.model.AetherFingerprint
import com.amyrmo.damavand.model.AetherIpVersion
import com.amyrmo.damavand.model.AetherObfuscation
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherProtocol
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.AetherPsiphonCdnSet
import com.amyrmo.damavand.model.AetherPsiphonMode
import com.amyrmo.damavand.model.AetherScanMode
import com.amyrmo.damavand.model.AetherTor
import com.amyrmo.damavand.model.AetherTorBridges
import com.amyrmo.damavand.model.AetherTorRelays
import com.amyrmo.damavand.model.AetherTransport
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.LiveLogPanel
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.accent
import kotlinx.coroutines.launch

/** Port used only to render the command preview (the real one is plan-assigned). */
private const val PREVIEW_PORT = 18190

/**
 * The Aether editor (§5.2, Narcic layout): tunnel fields, the MASQUE
 * extras, Psiphon/Tor carriers, the command field, the identity-keys
 * section (the adapter's keys surface), and the live log.
 *
 * Command rule (carried from Narcic, architecture §3.1): the field shows
 * the live-generated command; the text is stored in
 * [AetherProfile.command] **only when it differs from the generated
 * preview** — otherwise the field-driven flags stay authoritative.
 * Save is gated by [AetherCommands.problemOf].
 *
 * The in-sheet endpoint scan is a labelled placeholder: the runtime's
 * Aether engine exposes no throwaway finder run to the UI yet (checked
 * against AetherAdapter/AetherEngine — only session starts and key
 * registration exist), so the row says so instead of faking a scan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AetherEditorSheet(
    profile: AetherProfile,
    isNew: Boolean,
    onSave: (AetherProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(profile) }
    var commandCustom by remember {
        mutableStateOf(profile.command.ifBlank { null })
    }
    val preview = remember(draft) {
        AetherFlags.commandPreview(draft.copy(command = ""), PREVIEW_PORT)
    }
    val commandText = commandCustom ?: preview
    val isCustomCommand = commandCustom != null && commandCustom != preview
    val savedCommand = if (isCustomCommand) commandCustom!! else ""
    val problem = AetherCommands.problemOf(draft.copy(command = savedCommand))
    val protocol = AetherProtocol.fromString(draft.protocol)

    val error = when {
        draft.name.isBlank() -> stringResource(R.string.editor_name_required)
        problem != null -> stringResource(
            when (problem) {
                AetherCommands.Problem.CARRIER_NEEDS_MASQUE -> R.string.aether_problem_carrier
                AetherCommands.Problem.TOR_PSIPHON_CONFLICT -> R.string.aether_problem_conflict
                AetherCommands.Problem.TOR_BRIDGES_MISSING -> R.string.aether_problem_bridges
            },
        )

        else -> null
    }

    EditorSheet(
        title = stringResource(R.string.editor_aether),
        accent = profile.kind.accent(),
        saveEnabled = draft.name.isNotBlank() && problem == null,
        error = error,
        onSave = { onSave(draft.copy(command = savedCommand)) },
        onDismiss = onDismiss,
    ) {
        FormTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = stringResource(R.string.common_name),
        )

        SectionHeader(title = stringResource(R.string.aether_tunnel))
        PickerRow(
            label = stringResource(R.string.aether_protocol),
            value = protocol,
            options = AetherProtocol.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(protocol = it.type) },
        )
        if (protocol.overMasque) {
            PickerRow(
                label = stringResource(R.string.aether_transport),
                value = AetherTransport.fromString(draft.transport),
                options = AetherTransport.entries,
                labelOf = { it.type },
                onPick = { draft = draft.copy(transport = it.type) },
            )
        }
        if (protocol.twoHops) {
            FormTextField(
                value = draft.wiwOuter,
                onValueChange = { draft = draft.copy(wiwOuter = it) },
                label = stringResource(R.string.aether_outer),
                forceLtr = true,
            )
            FormTextField(
                value = draft.wiwInner,
                onValueChange = { draft = draft.copy(wiwInner = it) },
                label = stringResource(R.string.aether_inner),
                forceLtr = true,
            )
        } else {
            FormTextField(
                value = draft.address,
                onValueChange = { draft = draft.copy(address = it) },
                label = stringResource(R.string.aether_address),
                forceLtr = true,
            )
            FormTextField(
                value = draft.port,
                onValueChange = { draft = draft.copy(port = it.filter { c -> c.isDigit() }) },
                label = stringResource(R.string.aether_port),
                forceLtr = true,
            )
        }
        if (draft.scanning) {
            Text(
                text = stringResource(R.string.aether_scan_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Placeholder by design: no finder-run API exists in the runtime yet.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.aether_scan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.aether_scan_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = StateColors.Connecting,
                )
            }
        }
        PickerRow(
            label = stringResource(R.string.aether_scan_mode),
            value = AetherScanMode.fromString(draft.scanMode),
            options = AetherScanMode.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(scanMode = it.type) },
        )
        PickerRow(
            label = stringResource(R.string.aether_obfuscation),
            value = AetherObfuscation.fromString(draft.obfuscation),
            options = AetherObfuscation.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(obfuscation = it.type) },
        )
        PickerRow(
            label = stringResource(R.string.aether_ip),
            value = AetherIpVersion.fromString(draft.ipVersion),
            options = AetherIpVersion.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(ipVersion = it.type) },
        )
        FormTextField(
            value = draft.exitLoc,
            onValueChange = { draft = draft.copy(exitLoc = it) },
            label = stringResource(R.string.aether_exit_loc),
            forceLtr = true,
        )
        FormTextField(
            value = draft.dns,
            onValueChange = { draft = draft.copy(dns = it) },
            label = stringResource(R.string.common_dns),
            forceLtr = true,
        )

        if (protocol.overMasque) {
            SectionHeader(title = stringResource(R.string.aether_masque))
            FormTextField(
                value = draft.masqueSni,
                onValueChange = { draft = draft.copy(masqueSni = it) },
                label = stringResource(R.string.aether_sni),
                forceLtr = true,
            )
            PickerRow(
                label = stringResource(R.string.aether_fingerprint),
                value = AetherFingerprint.fromString(draft.fingerprint),
                options = AetherFingerprint.entries,
                labelOf = { it.type },
                onPick = { draft = draft.copy(fingerprint = it.type) },
            )
            SwitchRow(
                label = stringResource(R.string.aether_fragment),
                checked = draft.fragment,
                onCheckedChange = { draft = draft.copy(fragment = it) },
            )
            if (draft.fragment) {
                FormTextField(
                    value = draft.fragmentSize,
                    onValueChange = { draft = draft.copy(fragmentSize = it) },
                    label = stringResource(R.string.aether_fragment_size),
                    forceLtr = true,
                )
                FormTextField(
                    value = draft.fragmentDelay,
                    onValueChange = { draft = draft.copy(fragmentDelay = it) },
                    label = stringResource(R.string.aether_fragment_delay),
                    forceLtr = true,
                )
            }
            SwitchRow(
                label = stringResource(R.string.aether_ech),
                checked = draft.ech,
                onCheckedChange = { draft = draft.copy(ech = it) },
            )
            if (draft.ech) {
                FormTextField(
                    value = draft.echDns,
                    onValueChange = { draft = draft.copy(echDns = it) },
                    label = stringResource(R.string.aether_ech_dns),
                    forceLtr = true,
                )
                FormTextField(
                    value = draft.echDomain,
                    onValueChange = { draft = draft.copy(echDomain = it) },
                    label = stringResource(R.string.aether_ech_domain),
                    forceLtr = true,
                )
            }
            FormTextField(
                value = draft.finalMask,
                onValueChange = { draft = draft.copy(finalMask = it) },
                label = stringResource(R.string.aether_final_mask),
                forceLtr = true,
            )
            FormTextField(
                value = draft.dialMode,
                onValueChange = { draft = draft.copy(dialMode = it) },
                label = stringResource(R.string.aether_dial_mode),
                forceLtr = true,
            )
        }

        SectionHeader(title = stringResource(R.string.aether_carriers))
        PickerRow(
            label = stringResource(R.string.aether_psiphon),
            value = AetherPsiphon.fromString(draft.psiphon),
            options = AetherPsiphon.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(psiphon = it.type) },
        )
        if (AetherPsiphon.fromString(draft.psiphon) != AetherPsiphon.OFF) {
            PickerRow(
                label = stringResource(R.string.aether_psiphon_mode),
                value = AetherPsiphonMode.fromString(draft.psiphonMode),
                options = AetherPsiphonMode.entries,
                labelOf = { it.type },
                onPick = { draft = draft.copy(psiphonMode = it.type) },
            )
            FormTextField(
                value = draft.psiphonRegion,
                onValueChange = { draft = draft.copy(psiphonRegion = it) },
                label = stringResource(R.string.aether_psiphon_region),
                forceLtr = true,
            )
            FormTextField(
                value = draft.psiphonCdnSni,
                onValueChange = { draft = draft.copy(psiphonCdnSni = it) },
                label = stringResource(R.string.aether_psiphon_sni),
                forceLtr = true,
            )
            FormTextField(
                value = draft.psiphonCdnIps,
                onValueChange = { draft = draft.copy(psiphonCdnIps = it) },
                label = stringResource(R.string.aether_psiphon_ips),
                singleLine = false,
                minLines = 2,
                forceLtr = true,
            )
            val selectedSets = AetherPsiphonCdnSet.parse(draft.psiphonCdnSets)
            Text(
                text = stringResource(R.string.aether_psiphon_sets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AetherPsiphonCdnSet.entries.forEach { set ->
                    FilterChip(
                        selected = set in selectedSets,
                        onClick = {
                            val next = if (set in selectedSets) {
                                selectedSets - set
                            } else {
                                selectedSets + set
                            }
                            draft = draft.copy(
                                psiphonCdnSets = AetherPsiphonCdnSet.join(next) ?: "",
                            )
                        },
                        label = { Text(set.type) },
                    )
                }
            }
            SwitchRow(
                label = stringResource(R.string.aether_psiphon_bundled),
                checked = draft.psiphonBundledList,
                onCheckedChange = { draft = draft.copy(psiphonBundledList = it) },
            )
        }
        PickerRow(
            label = stringResource(R.string.aether_tor),
            value = AetherTor.fromString(draft.tor),
            options = AetherTor.entries,
            labelOf = { it.type },
            onPick = { draft = draft.copy(tor = it.type) },
        )
        if (AetherTor.fromString(draft.tor) != AetherTor.OFF) {
            PickerRow(
                label = stringResource(R.string.aether_tor_bridges),
                value = AetherTorBridges.fromString(draft.torBridges),
                options = AetherTorBridges.entries,
                labelOf = { it.type },
                onPick = { draft = draft.copy(torBridges = it.type) },
            )
            PickerRow(
                label = stringResource(R.string.aether_tor_relays),
                value = AetherTorRelays.fromString(draft.torRelays),
                options = AetherTorRelays.entries,
                labelOf = { it.type },
                onPick = { draft = draft.copy(torRelays = it.type) },
            )
            FormTextField(
                value = draft.torBridgeLines,
                onValueChange = { draft = draft.copy(torBridgeLines = it) },
                label = stringResource(R.string.aether_tor_lines),
                singleLine = false,
                minLines = 2,
                forceLtr = true,
            )
        }

        SectionHeader(title = stringResource(R.string.aether_advanced))
        FormTextField(
            value = draft.upstream,
            onValueChange = { draft = draft.copy(upstream = it) },
            label = stringResource(R.string.aether_upstream),
            forceLtr = true,
        )
        FormTextField(
            value = draft.extraArgs,
            onValueChange = { draft = draft.copy(extraArgs = it) },
            label = stringResource(R.string.aether_extra_args),
            singleLine = false,
            minLines = 2,
            forceLtr = true,
        )

        SectionHeader(title = stringResource(R.string.aether_command))
        FormTextField(
            value = commandText,
            onValueChange = { commandCustom = it },
            label = stringResource(R.string.aether_command),
            singleLine = false,
            minLines = 3,
            forceLtr = true,
        )
        if (isCustomCommand) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.aether_command_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = StateColors.Connecting,
                )
                TextButton(onClick = { commandCustom = null }) {
                    Text(stringResource(R.string.aether_command_revert))
                }
            }
        }

        if (!isNew) {
            SectionHeader(title = stringResource(R.string.aether_keys))
            AetherKeysSection(profile = draft)
        }

        SectionHeader(title = stringResource(R.string.live_log))
        LiveLogPanel(filter = "aether", height = 150.dp)
        Spacer(Modifier.height(2.dp))
    }
}

/** Identity-keys block: status / renew / reset / export via [AetherAdapter]. */
@Composable
private fun AetherKeysSection(profile: AetherProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = remember { AetherAdapter(ProcessSupervisor()) }
    var status by remember { mutableStateOf<AetherKeysStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<Map<String, String>?>(null) }
    val clipboard = LocalClipboardManager.current

    fun refresh() {
        status = runCatching { adapter.keysStatus(context, profile) }.getOrNull()
    }
    LaunchedEffect(profile.id) { refresh() }

    val current = status
    Text(
        text = when {
            current == null -> "…"
            current.complete -> stringResource(R.string.aether_keys_complete)
            else -> stringResource(
                R.string.aether_keys_missing,
                current.missingFiles.joinToString(", "),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    runCatching { adapter.renewKeys(context, profile) }
                    refresh()
                    busy = false
                }
            },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.aether_keys_renew))
        }
        OutlinedButton(
            onClick = {
                runCatching { adapter.resetKeys(context, profile) }
                refresh()
            },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.aether_keys_reset))
        }
        OutlinedButton(
            onClick = {
                exported = runCatching { adapter.exportKeys(context, profile) }.getOrNull()
            },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.aether_keys_export))
        }
    }
    val shown = exported
    if (shown != null) {
        AlertDialog(
            onDismissRequest = { exported = null },
            title = { Text(stringResource(R.string.aether_keys_export)) },
            text = {
                Text(
                    text = shown.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                shown.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                            ),
                        )
                        exported = null
                    },
                ) {
                    Text(stringResource(R.string.common_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { exported = null }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}
