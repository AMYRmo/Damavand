package com.amyrmo.damavand.ui.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.logic.ChainValidator
import com.amyrmo.damavand.logic.WhitednsResolvers
import com.amyrmo.damavand.model.WhitednsEngine
import com.amyrmo.damavand.model.WhitednsMode
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.platform.NetProbes
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.LiveLogPanel
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.accent
import kotlinx.coroutines.launch

private val TRANSPORTS = listOf("auto", "udp", "tcp", "dot", "doh", "doq")
private val METHODS = listOf(0, 1, 2, 3, 4, 5)

/**
 * The Whitedns (DNS tunnel) editor (§5.2): domains, shared key, the 0–5
 * encryption method, resolvers with inline IP-literal validation against
 * :logic's own rules, engine/tunnel/transport, and the persistent
 * "DNS tunnels are slow" note. Save is gated by
 * [ChainValidator.whitednsProblems] — the same function the chain
 * validator runs, so an invalid profile cannot be saved at all.
 *
 * The adapter exposes no resolver-test run, so the test button probes
 * each resolver itself with a real DNS query (NetProbes.dnsProbe) and
 * shows the measured answer time — never a fabricated number.
 */
@Composable
fun WhitednsEditorSheet(
    profile: WhitednsProfile,
    onSave: (WhitednsProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var engine by remember { mutableStateOf(profile.engine) }
    var domains by remember { mutableStateOf(profile.domains.joinToString("\n")) }
    var key by remember { mutableStateOf(profile.encryptionKey) }
    var method by remember { mutableStateOf(profile.encryptionMethod) }
    var resolvers by remember { mutableStateOf(profile.resolvers.joinToString("\n")) }
    var tunnelMode by remember { mutableStateOf(profile.tunnelMode) }
    var transport by remember { mutableStateOf(profile.resolverTransport) }
    var balancing by remember { mutableStateOf(profile.balancingStrategy.toString()) }
    var dns by remember { mutableStateOf(profile.dns) }
    var testResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val draft = profile.copy(
        name = name.trim(),
        engine = engine,
        domains = domains.lines().map { it.trim() }.filter { it.isNotEmpty() },
        encryptionKey = key,
        encryptionMethod = method,
        resolvers = WhitednsResolvers.splitResolvers(resolvers),
        tunnelMode = tunnelMode,
        resolverTransport = transport,
        balancingStrategy = balancing.toIntOrNull() ?: profile.balancingStrategy,
        dns = dns.trim(),
    )
    val problems = ChainValidator.whitednsProblems(draft)
    val invalidResolvers = WhitednsResolvers.invalidResolvers(resolvers)
    val error = when {
        name.isBlank() -> stringResource(R.string.editor_name_required)
        problems.isNotEmpty() -> problems.joinToString("\n")
        else -> null
    }

    EditorSheet(
        title = stringResource(R.string.editor_whitedns),
        accent = profile.kind.accent(),
        saveEnabled = name.isNotBlank() && problems.isEmpty(),
        error = error,
        onSave = { onSave(draft) },
        onDismiss = onDismiss,
    ) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
        )
        PickerRow(
            label = stringResource(R.string.wd_engine),
            value = engine,
            options = WhitednsEngine.entries,
            labelOf = { it.name.lowercase() },
            onPick = { engine = it },
        )
        FormTextField(
            value = domains,
            onValueChange = { domains = it },
            label = stringResource(R.string.wd_domains),
            singleLine = false,
            minLines = 2,
            forceLtr = true,
        )
        FormTextField(
            value = key,
            onValueChange = { key = it },
            label = stringResource(R.string.wd_key),
            forceLtr = true,
        )
        PickerRow(
            label = stringResource(R.string.wd_method),
            value = method,
            options = METHODS,
            labelOf = { if (it == 0) "0 · none" else it.toString() },
            onPick = { method = it },
        )
        FormTextField(
            value = resolvers,
            onValueChange = { resolvers = it },
            label = stringResource(R.string.wd_resolvers),
            singleLine = false,
            minLines = 3,
            forceLtr = true,
            isError = invalidResolvers.isNotEmpty(),
            supportingText = if (invalidResolvers.isNotEmpty()) {
                stringResource(R.string.whitedns_resolvers_invalid, invalidResolvers.joinToString(", "))
            } else {
                null
            },
        )
        OutlinedButton(
            onClick = {
                val entries = WhitednsResolvers.splitResolvers(resolvers)
                if (entries.isEmpty()) return@OutlinedButton
                testing = true
                scope.launch {
                    testResults = entries.map { entry ->
                        val ms = NetProbes.dnsProbe(entry)
                        if (ms != null) "$entry — $ms ms" else "$entry — ✕"
                    }
                    testing = false
                }
            },
            enabled = !testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.wd_test_resolvers))
        }
        if (testResults.isNotEmpty()) {
            Column {
                testResults.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (engine == WhitednsEngine.COTTEN) {
            PickerRow(
                label = stringResource(R.string.wd_tunnel_mode),
                value = tunnelMode,
                options = WhitednsMode.entries,
                labelOf = { it.name.lowercase() },
                onPick = { tunnelMode = it },
            )
        }
        PickerRow(
            label = stringResource(R.string.wd_transport),
            value = transport,
            options = TRANSPORTS,
            labelOf = { it },
            onPick = { transport = it },
        )
        FormTextField(
            value = balancing,
            onValueChange = { balancing = it.filter { c -> c.isDigit() } },
            label = stringResource(R.string.wd_balancing),
            forceLtr = true,
        )
        FormTextField(
            value = dns,
            onValueChange = { dns = it },
            label = stringResource(R.string.common_dns),
            forceLtr = true,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    DamavandShapeTokens.Card,
                )
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.whitedns_slow_note),
                style = MaterialTheme.typography.bodySmall,
                color = StateColors.Connecting,
            )
        }
        SectionHeader(title = stringResource(R.string.live_log))
        LiveLogPanel(filter = "whitedns", height = 140.dp)
    }
}
