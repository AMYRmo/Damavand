package com.amyrmo.damavand.ui.bpb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.core.VpnManager
import com.amyrmo.damavand.data.net.CfAccountInfo
import com.amyrmo.damavand.data.net.CfAccountsRepository
import com.amyrmo.damavand.data.net.CloudflareApi
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.action_cancel
import com.amyrmo.damavand.shared.resources.cf_account_removed
import com.amyrmo.damavand.shared.resources.cf_accounts
import com.amyrmo.damavand.shared.resources.cf_add_account
import com.amyrmo.damavand.shared.resources.cf_apply_redeploy
import com.amyrmo.damavand.shared.resources.cf_clean_ips
import com.amyrmo.damavand.shared.resources.cf_custom_domain
import com.amyrmo.damavand.shared.resources.cf_detach_domain
import com.amyrmo.damavand.shared.resources.cf_domain_attached
import com.amyrmo.damavand.shared.resources.cf_domain_hint
import com.amyrmo.damavand.shared.resources.cf_refresh
import com.amyrmo.damavand.shared.resources.cf_remove_account
import com.amyrmo.damavand.shared.resources.cf_update_available
import com.amyrmo.damavand.shared.resources.cf_update_hint
import com.amyrmo.damavand.shared.resources.cf_remove_action
import com.amyrmo.damavand.shared.resources.cf_remove_anyway
import com.amyrmo.damavand.shared.resources.cf_remove_body
import com.amyrmo.damavand.shared.resources.cf_remove_failed
import com.amyrmo.damavand.shared.resources.cf_remove_title
import com.amyrmo.damavand.shared.resources.cf_usage_line_account
import com.amyrmo.damavand.shared.resources.cf_usage_line_worker
import com.amyrmo.damavand.shared.resources.cf_usage_title
import com.amyrmo.damavand.shared.resources.cf_connect
import com.amyrmo.damavand.shared.resources.cf_connect_action
import com.amyrmo.damavand.shared.resources.cf_connect_body
import com.amyrmo.damavand.shared.resources.cf_connect_title
import com.amyrmo.damavand.shared.resources.cf_connected_as
import com.amyrmo.damavand.shared.resources.cf_connection
import com.amyrmo.damavand.shared.resources.cf_create_token
import com.amyrmo.damavand.shared.resources.cf_env_hint
import com.amyrmo.damavand.shared.resources.cf_env_title
import com.amyrmo.damavand.shared.resources.cf_fallback_url
import com.amyrmo.damavand.shared.resources.cf_pending
import com.amyrmo.damavand.shared.resources.cf_ports
import com.amyrmo.damavand.shared.resources.cf_proxy_ips
import com.amyrmo.damavand.shared.resources.cf_deploy
import com.amyrmo.damavand.shared.resources.cf_deploy_failed
import com.amyrmo.damavand.shared.resources.cf_deploy_pages
import com.amyrmo.damavand.shared.resources.cf_deploy_worker
import com.amyrmo.damavand.shared.resources.cf_deployed
import com.amyrmo.damavand.shared.resources.cf_disconnect
import com.amyrmo.damavand.shared.resources.cf_disconnect_hint
import com.amyrmo.damavand.shared.resources.cf_not_connected
import com.amyrmo.damavand.shared.resources.cf_not_deployed
import com.amyrmo.damavand.shared.resources.cf_not_connected_hint
import com.amyrmo.damavand.shared.resources.cf_pages
import com.amyrmo.damavand.shared.resources.cf_pick_account
import com.amyrmo.damavand.shared.resources.cf_redeploy
import com.amyrmo.damavand.shared.resources.cf_section_cloudflare
import com.amyrmo.damavand.shared.resources.cf_token_hint
import com.amyrmo.damavand.shared.resources.cf_token_label
import com.amyrmo.damavand.shared.resources.cf_worker
import com.amyrmo.damavand.ui.platform.LocalPlatform
import com.amyrmo.damavand.ui.settings.SettingsEditRow
import com.amyrmo.damavand.ui.settings.SettingsGroup
import com.amyrmo.damavand.ui.settings.SettingsInfoRow
import com.amyrmo.damavand.ui.settings.SettingsMenuRow
import com.amyrmo.damavand.ui.settings.SettingsSegment
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The BPB tab's Cloudflare section: BPB-VPN's deployment suite, in-app. The
 * user pastes their own Cloudflare API token; the app deploys the bundled
 * BPB worker as a Worker and as a Pages project on that account, and the
 * built-in "bpb" profile resolves to the chosen deployment at connect time.
 */
@Composable
fun CloudflareSection(repository: CfAccountsRepository) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val account = state.selected()
    var showConnect by remember { mutableStateOf(false) }
    var deployBusy by remember { mutableStateOf(false) }
    var deployError by remember { mutableStateOf<String?>(null) }
    val deployedMsg = stringResource(Res.string.cf_deployed)
    val failedMsg = stringResource(Res.string.cf_deploy_failed)
    var usage by remember { mutableStateOf<com.amyrmo.damavand.data.net.CfUsage?>(null) }
    var usageError by remember { mutableStateOf<String?>(null) }
    var usageBusy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var removeBusy by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf<String?>(null) }
    val removedMsg = stringResource(Res.string.cf_account_removed)
    var domainBusy by remember { mutableStateOf(false) }
    var domainError by remember { mutableStateOf<String?>(null) }
    val domainAttachedMsg = stringResource(Res.string.cf_domain_attached)

    suspend fun loadUsage() {
        val acc = account ?: return
        usageBusy = true
        runCatching { repository.usage(acc.id, tunnelPort()) }
            .onSuccess { usage = it; usageError = null }
            .onFailure { usageError = it.message ?: it.toString() }
        usageBusy = false
    }

    val usageDeployment = account?.deployment(state.connectionType)
    androidx.compose.runtime.LaunchedEffect(account?.id, usageDeployment?.name) {
        usage = null
        usageError = null
        if (account != null && usageDeployment != null) loadUsage()
    }

    if (showConnect) {
        CfConnectDialog(
            repository = repository,
            onDismiss = { showConnect = false },
            onConnected = { showConnect = false },
        )
    }

    SettingsGroup(stringResource(Res.string.cf_section_cloudflare)) {
        if (account == null) {
            SettingsInfoRow(
                title = stringResource(Res.string.cf_not_connected),
                body = stringResource(Res.string.cf_not_connected_hint),
            )
            SettingsMenuRow(
                title = stringResource(Res.string.cf_connect),
            ) { showConnect = true }
        } else {
            SettingsInfoRow(
                title = stringResource(Res.string.cf_connected_as),
                body = listOf(account.label, account.name).filter { it.isNotBlank() }.distinct().joinToString(" · "),
            )
            if (state.accounts.size > 1) {
                SettingsSegment {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            stringResource(Res.string.cf_accounts),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                        state.accounts.forEach { acc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = acc.id == account.id) {
                                        repository.selectAccount(acc.id)
                                    }
                                    .padding(end = 16.dp),
                            ) {
                                RadioButton(
                                    selected = acc.id == account.id,
                                    onClick = { repository.selectAccount(acc.id) },
                                )
                                Text(acc.label)
                            }
                        }
                    }
                }
            }
            DeploymentStatusRow(Res.string.cf_worker, account.deployment(CloudflareApi.TYPE_WORKERS))
            DeploymentStatusRow(Res.string.cf_pages, account.deployment(CloudflareApi.TYPE_PAGES))

            fun deploy(types: List<String>) {
                if (deployBusy) return
                deployBusy = true
                deployError = null
                scope.launch {
                    val results = if (types.size == 2) {
                        repository.deployBoth(account.id, tunnelPort())
                    } else {
                        mapOf(types.first() to runCatching { repository.deployOne(account.id, types.first(), tunnelPort()) })
                    }
                    deployBusy = false
                    val failure = results.values.firstNotNullOfOrNull { it.exceptionOrNull() }
                    if (failure == null) {
                        platform.toast(deployedMsg)
                    } else {
                        deployError = "$failedMsg: ${failure.message ?: failure.toString()}"
                    }
                }
            }

            val workers = account.deployment(CloudflareApi.TYPE_WORKERS)
            val pages = account.deployment(CloudflareApi.TYPE_PAGES)
            val deployRow: Pair<org.jetbrains.compose.resources.StringResource, List<String>> = when {
                workers == null && pages == null ->
                    Res.string.cf_deploy to listOf(CloudflareApi.TYPE_WORKERS, CloudflareApi.TYPE_PAGES)
                workers == null -> Res.string.cf_deploy_worker to listOf(CloudflareApi.TYPE_WORKERS)
                pages == null -> Res.string.cf_deploy_pages to listOf(CloudflareApi.TYPE_PAGES)
                else -> Res.string.cf_redeploy to listOf(CloudflareApi.TYPE_WORKERS, CloudflareApi.TYPE_PAGES)
            }
            SettingsMenuRow(
                title = stringResource(deployRow.first),
                trailing = if (deployBusy) {
                    { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
            ) { deploy(deployRow.second) }

            deployError?.let { message ->
                SettingsSegment {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (account.updateAvailable) {
                SettingsMenuRow(
                    title = stringResource(Res.string.cf_update_available),
                    subtitle = stringResource(Res.string.cf_update_hint),
                    trailing = if (deployBusy) {
                        { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                ) { deploy(listOf(CloudflareApi.TYPE_WORKERS, CloudflareApi.TYPE_PAGES)) }
            }

            // Request usage (BPB's home card): this deployment + the account
            // total against the free plan's 100k/day cap, last 24 h.
            if (usageDeployment != null) {
                SettingsSegment {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(Res.string.cf_usage_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (usageBusy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { scope.launch { loadUsage() } }) {
                                    Text(stringResource(Res.string.cf_refresh))
                                }
                            }
                        }
                        usage?.let { u ->
                            Text(stringResource(Res.string.cf_usage_line_worker, formatCfThousands(u.worker)))
                            Text(
                                stringResource(
                                    Res.string.cf_usage_line_account,
                                    formatCfThousands(u.total),
                                    cfPercent(u.total, CloudflareApi.DAILY_REQUEST_LIMIT),
                                    formatCfThousands(CloudflareApi.DAILY_REQUEST_LIMIT),
                                ),
                            )
                        }
                        usageError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Connection switch — BPB's home-screen choice: which of the
            // account's two deployments the "bpb" profile resolves to.
            SettingsSegment {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        stringResource(Res.string.cf_connection),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    listOf(
                        CloudflareApi.TYPE_WORKERS to Res.string.cf_worker,
                        CloudflareApi.TYPE_PAGES to Res.string.cf_pages,
                    ).forEach { (type, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = state.connectionType == type) {
                                    repository.setConnectionType(type)
                                }
                                .padding(end = 16.dp),
                        ) {
                            RadioButton(
                                selected = state.connectionType == type,
                                onClick = { repository.setConnectionType(type) },
                            )
                            Text(stringResource(label))
                        }
                    }
                }
            }

            // Custom domain of the deployment the Connection switch selects
            // (BPB manages it per selected deployment).
            val selectedDeployment = account.deployment(state.connectionType)
            if (selectedDeployment != null) {
                if (selectedDeployment.customDomain == null) {
                    SettingsEditRow(
                        title = stringResource(Res.string.cf_custom_domain),
                        value = "",
                        placeholder = "vpn.example.com",
                        onValueChanged = { v ->
                            val host = v.trim()
                            if (host.isNotEmpty() && !domainBusy) {
                                domainBusy = true
                                domainError = null
                                scope.launch {
                                    runCatching {
                                        repository.attachDomain(account.id, state.connectionType, host, tunnelPort())
                                    }
                                        .onSuccess { platform.toast(domainAttachedMsg) }
                                        .onFailure { domainError = it.message ?: it.toString() }
                                    domainBusy = false
                                }
                            }
                        },
                    )
                } else {
                    SettingsInfoRow(
                        title = stringResource(Res.string.cf_custom_domain),
                        body = selectedDeployment.customDomain.orEmpty(),
                    )
                    SettingsMenuRow(
                        title = stringResource(Res.string.cf_detach_domain),
                        trailing = if (domainBusy) {
                            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    ) {
                        if (!domainBusy) {
                            domainBusy = true
                            domainError = null
                            scope.launch {
                                runCatching {
                                    repository.detachDomain(account.id, state.connectionType, tunnelPort())
                                }
                                    .onFailure { domainError = it.message ?: it.toString() }
                                domainBusy = false
                            }
                        }
                    }
                }
                SettingsSegment {
                    Text(
                        stringResource(Res.string.cf_domain_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                domainError?.let { message ->
                    SettingsSegment {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // VLESS knobs: proxy IPs + fallback URL are the worker's ENV
            // (server-side); clean IPs + ports are client-side.
            SettingsEditRow(
                title = stringResource(Res.string.cf_proxy_ips),
                value = account.proxyIps.joinToString(", "),
                onValueChanged = { v ->
                    repository.updateKnobs(account.id, parseCfList(v), account.fallbackUrl, account.cleanIps, account.ports)
                },
            )
            SettingsEditRow(
                title = stringResource(Res.string.cf_fallback_url),
                value = account.fallbackUrl,
                placeholder = "https://",
                onValueChanged = { v ->
                    repository.updateKnobs(account.id, account.proxyIps, v.trim(), account.cleanIps, account.ports)
                },
            )
            SettingsEditRow(
                title = stringResource(Res.string.cf_clean_ips),
                value = account.cleanIps.joinToString(", "),
                onValueChanged = { v ->
                    repository.updateKnobs(account.id, account.proxyIps, account.fallbackUrl, parseCfList(v), account.ports)
                },
            )
            SettingsEditRow(
                title = stringResource(Res.string.cf_ports),
                value = account.ports.joinToString(", "),
                onValueChanged = { v ->
                    repository.updateKnobs(account.id, account.proxyIps, account.fallbackUrl, account.cleanIps, parseCfPorts(v))
                },
            )
            SettingsInfoRow(
                title = stringResource(Res.string.cf_env_title),
                body = stringResource(Res.string.cf_env_hint),
            )
            if (account.deployments.any { it.pending }) {
                SettingsMenuRow(
                    title = stringResource(Res.string.cf_apply_redeploy),
                    subtitle = stringResource(Res.string.cf_pending),
                    trailing = if (deployBusy) {
                        { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                ) { deploy(listOf(CloudflareApi.TYPE_WORKERS, CloudflareApi.TYPE_PAGES)) }
            }

            SettingsMenuRow(
                title = stringResource(Res.string.cf_add_account),
            ) { showConnect = true }
            SettingsMenuRow(
                title = stringResource(Res.string.cf_remove_account),
            ) { confirmRemove = true }
            SettingsMenuRow(
                title = stringResource(Res.string.cf_disconnect),
                subtitle = stringResource(Res.string.cf_disconnect_hint),
            ) { repository.disconnect(account.id) }
        }
    }

    if (confirmRemove && account != null) {
        AlertDialog(
            onDismissRequest = { if (!removeBusy) confirmRemove = false },
            title = { Text(stringResource(Res.string.cf_remove_title)) },
            text = { Text(stringResource(Res.string.cf_remove_body)) },
            confirmButton = {
                TextButton(
                    enabled = !removeBusy,
                    onClick = {
                        removeBusy = true
                        scope.launch {
                            runCatching { repository.removeAccount(account.id, tunnelPort()) }
                                .onSuccess {
                                    removeBusy = false
                                    confirmRemove = false
                                    platform.toast(removedMsg)
                                }
                                .onFailure {
                                    removeBusy = false
                                    confirmRemove = false
                                    removeError = it.message ?: it.toString()
                                }
                        }
                    },
                ) { Text(stringResource(Res.string.cf_remove_action)) }
            },
            dismissButton = {
                TextButton(enabled = !removeBusy, onClick = { confirmRemove = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (removeError != null && account != null) {
        AlertDialog(
            onDismissRequest = { removeError = null },
            title = { Text(stringResource(Res.string.cf_remove_failed)) },
            text = { Text(removeError.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // BPB's fallback: forget the account locally even
                        // though the Cloudflare cleanup failed.
                        repository.disconnect(account.id)
                        removeError = null
                        platform.toast(removedMsg)
                    },
                ) { Text(stringResource(Res.string.cf_remove_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { removeError = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeploymentStatusRow(
    titleRes: org.jetbrains.compose.resources.StringResource,
    deployment: com.amyrmo.damavand.data.net.CfDeployment?,
) {
    SettingsInfoRow(
        title = stringResource(titleRes),
        body = deployment?.effectiveHost ?: stringResource(Res.string.cf_not_deployed),
    )
}

/** The live tunnel's SOCKS port when connected (BPB routes API calls through it). */
private fun tunnelPort(): Int? =
    VpnManager.activeSocksPort.takeIf { VpnManager.status.value.state == ConnectionState.Connected }

private fun parseCfList(text: String): List<String> =
    text.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private fun parseCfPorts(text: String): List<Int> =
    parseCfList(text).mapNotNull { it.toIntOrNull() }.filter { it in 1..65535 }.distinct()

private fun formatCfThousands(value: Long): String {
    val digits = value.toString()
    val sb = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

private fun cfPercent(part: Long, whole: Long): String {
    if (whole <= 0) return "0"
    val tenths = part * 1000 / whole
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun CfConnectDialog(
    repository: CfAccountsRepository,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Set when the token reaches several accounts: BPB's wizard picks one first.
    var picker by remember { mutableStateOf<Pair<String, List<CfAccountInfo>>?>(null) }

    picker?.let { (validToken, accounts) ->
        CfAccountPickerDialog(
            accounts = accounts,
            onDismiss = { picker = null },
            onPick = { info ->
                picker = null
                busy = true
                scope.launch {
                    runCatching { repository.addAccount(validToken, info, tunnelPort()) }
                        .onSuccess { busy = false; onConnected() }
                        .onFailure { busy = false; error = it.message ?: it.toString() }
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.cf_connect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.cf_connect_body))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(Res.string.cf_token_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = false,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.cf_token_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    enabled = !busy,
                    onClick = { platform.openUri(CloudflareApi.tokenCreationUrl()) },
                ) { Text(stringResource(Res.string.cf_create_token)) }
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && token.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { repository.validateToken(token, tunnelPort()) }
                            .onSuccess { (validToken, accounts) ->
                                if (accounts.size == 1) {
                                    runCatching { repository.addAccount(validToken, accounts.first(), tunnelPort()) }
                                        .onSuccess { busy = false; onConnected() }
                                        .onFailure { busy = false; error = it.message ?: it.toString() }
                                } else {
                                    busy = false
                                    picker = validToken to accounts
                                }
                            }
                            .onFailure { busy = false; error = it.message ?: it.toString() }
                    }
                },
            ) { Text(stringResource(Res.string.cf_connect_action)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun CfAccountPickerDialog(
    accounts: List<CfAccountInfo>,
    onDismiss: () -> Unit,
    onPick: (CfAccountInfo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cf_pick_account)) },
        text = {
            Column {
                accounts.forEach { info ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false) { onPick(info) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = false, onClick = { onPick(info) })
                        Text(info.name.ifBlank { info.id })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
