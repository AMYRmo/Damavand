package com.amyrmo.damavand.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.model.Subscription
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.GlassCard
import com.amyrmo.damavand.ui.formatBytes
import com.amyrmo.damavand.ui.formatDate
import com.amyrmo.damavand.ui.formatDateTime
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.StateColors
import kotlinx.coroutines.launch

/**
 * Subscriptions (§5.2): health snapshots (usage bar, expiry, last
 * update — always labelled a snapshot, never implied live), refresh
 * through the SubscriptionService, URL/user-agent editing, add by URL,
 * and delete with an explicit member-handling choice.
 */
@Composable
fun SubscriptionsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var deleting by remember { mutableStateOf<Subscription?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                DamavandIcon(
                    kind = DamavandIconKind.Close,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.dest_subscriptions),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showAdd = true }) {
                DamavandIcon(
                    kind = DamavandIconKind.Plus,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = 8.dp,
                bottom = Dimens.DockClearance + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (subscriptions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sub_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(subscriptions, key = { it.id }) { sub ->
                SubscriptionCard(
                    subscription = sub,
                    memberCount = profiles.count { it.subscriptionId == sub.id },
                    onRefresh = { scope.launch { vm.refreshSubscription(sub) } },
                    onEdit = { editing = sub },
                    onDelete = { deleting = sub },
                )
            }
        }
    }

    if (showAdd) {
        SubscriptionEditDialog(
            initial = null,
            onSave = { url, name, _ ->
                scope.launch {
                    vm.addSubscription(url, name)
                    showAdd = false
                }
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { sub ->
        SubscriptionEditDialog(
            initial = sub,
            onSave = { url, name, userAgent ->
                vm.updateSubscription(
                    sub.copy(
                        url = url,
                        name = name.ifBlank { sub.name },
                        userAgent = userAgent.ifBlank { null },
                    ),
                )
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.sub_delete_title)) },
            text = { Text(sub.title ?: sub.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSubscription(sub, deleteMembers = true)
                        deleting = null
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.sub_delete_members,
                            profiles.count { it.subscriptionId == sub.id },
                        ),
                        color = StateColors.Error,
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            vm.deleteSubscription(sub, deleteMembers = false)
                            deleting = null
                        },
                    ) {
                        Text(stringResource(R.string.sub_keep_members))
                    }
                    TextButton(onClick = { deleting = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            },
        )
    }
}

/** One subscription's health snapshot card. */
@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    memberCount: Int,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val used = subscription.used
    val total = subscription.total
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subscription.title ?: subscription.name,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.sub_members, memberCount),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        if (used != null && total != null && total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
            )
            Text(
                text = stringResource(R.string.home_used_of, formatBytes(used), formatBytes(total)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        subscription.expireAt?.let { expireAt ->
            Text(
                text = stringResource(R.string.home_expires, formatDate(expireAt)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (subscription.lastUpdated > 0) {
                stringResource(R.string.sub_updated, formatDateTime(subscription.lastUpdated))
            } else {
                stringResource(R.string.sub_never_updated)
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.sub_snapshot),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh) {
                Text(stringResource(R.string.sub_refresh))
            }
            OutlinedButton(onClick = onEdit) {
                Text(stringResource(R.string.common_edit))
            }
            IconButton(onClick = onDelete) {
                DamavandIcon(DamavandIconKind.Trash, StateColors.Error, size = 20.dp)
            }
        }
    }
}

/** Add (initial == null) or edit a subscription's URL/name/user-agent. */
@Composable
private fun SubscriptionEditDialog(
    initial: Subscription?,
    onSave: (url: String, name: String, userAgent: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var userAgent by remember { mutableStateOf(initial?.userAgent ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.add_subscription else R.string.common_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.sub_url),
                    forceLtr = true,
                )
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.common_name),
                )
                FormTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = stringResource(R.string.sub_user_agent),
                    forceLtr = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(url.trim(), name.trim(), userAgent.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
