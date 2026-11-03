package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.Subscription
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.PingOutcome
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.ProfileRow
import com.amyrmo.damavand.ui.editors.ProfileEditorSheet
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.StateColors
import java.util.UUID

/**
 * Servers (§5.2): group tabs (Manual + one per subscription; chains live
 * in their own destination, never here), the Smart Auto row pinned at
 * the top of each subscription group, and server cards with ping chips,
 * expiry badges and the full overflow (connect/share/edit/ping/
 * duplicate/delete).
 *
 * Smart Auto connects a synthetic [AutoSelectProfile]; the planner
 * rejects AutoSelect targets by contract, so the ViewModel expands it
 * to the fastest measured member at connect time.
 */
@Composable
fun ServersScreen(
    vm: AppViewModel,
    onConnect: (Profile) -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProfileId.collectAsStateWithLifecycle()
    val pings by vm.pingResults.collectAsStateWithLifecycle()

    var groupKey by remember { mutableStateOf<String?>(null) } // null = Manual
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<Profile?>(null) }
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }

    val groups: List<Pair<String?, Subscription?>> = listOf(null to null) +
        subscriptions.map { it.id to it }
    val activeGroup = groups.firstOrNull { it.first == groupKey } ?: groups.first()
    val activeSub = activeGroup.second

    val members = profiles.filter { profile ->
        profile !is ChainProfile && profile !is AutoSelectProfile &&
            profile.subscriptionId == activeGroup.first
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = groups.indexOf(activeGroup).coerceAtLeast(0),
                edgePadding = 12.dp,
            ) {
                groups.forEach { (key, sub) ->
                    Tab(
                        selected = key == activeGroup.first,
                        onClick = { groupKey = key },
                        text = {
                            Text(
                                text = sub?.let { it.title ?: it.name }
                                    ?: stringResource(R.string.servers_manual),
                            )
                        },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    top = 12.dp,
                    bottom = Dimens.DockClearance + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeSub != null) {
                    item(key = "auto-${activeSub.id}") {
                        val auto = AutoSelectProfile(
                            id = "auto-${activeSub.id}",
                            name = stringResource(R.string.servers_smart_auto),
                            targetSubscriptionId = activeSub.id,
                        )
                        ProfileRow(
                            name = auto.name,
                            coreKind = CoreKind.AUTO,
                            subtitle = stringResource(
                                R.string.servers_smart_auto_sub,
                                members.size,
                            ),
                            onClick = { onConnect(auto) },
                            trailing = {
                                IconButton(onClick = { vm.pingProfiles(members) }) {
                                    DamavandIcon(
                                        kind = DamavandIconKind.Refresh,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        size = 20.dp,
                                    )
                                }
                            },
                        )
                    }
                }
                items(members, key = { it.id }) { profile ->
                    ServerCard(
                        profile = profile,
                        active = profile.id == selectedId,
                        ping = pings[profile.id] ?: PingOutcome.Unknown,
                        subscription = activeSub,
                        onSelect = { vm.selectProfile(profile.id) },
                        onConnect = { onConnect(profile) },
                        onShare = { sharing = profile },
                        onEdit = {
                            editing = profile
                            editingIsNew = false
                        },
                        onPing = { vm.pingProfile(profile) },
                        onDuplicate = { vm.duplicateProfile(profile) },
                        onDelete = { confirmDelete = profile },
                    )
                }
                if (members.isEmpty() && activeSub == null) {
                    item {
                        Text(
                            text = stringResource(R.string.servers_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                item {
                    TextButton(onClick = onOpenSubscriptions) {
                        Text(stringResource(R.string.dest_subscriptions))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = Dimens.DockClearance + 12.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            DamavandIcon(
                kind = DamavandIconKind.Plus,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    if (showAdd) {
        AddServerSheet(
            vm = vm,
            onDismiss = { showAdd = false },
            onBlank = { kind ->
                showAdd = false
                editing = ProfileFactory.blank(kind, UUID.randomUUID().toString())
                editingIsNew = true
            },
        )
    }
    editing?.let { profile ->
        ProfileEditorSheet(
            profile = profile,
            isNew = editingIsNew,
            onSave = { saved ->
                vm.upsertProfile(saved)
                vm.selectProfile(saved.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    sharing?.let { profile ->
        ShareDialog(profile = profile, onDismiss = { sharing = null })
    }
    confirmDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(profile.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeProfile(profile.id)
                        confirmDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = StateColors.Error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** One server card: the shared row kit + ping chip + overflow (§5.2). */
@Composable
private fun ServerCard(
    profile: Profile,
    active: Boolean,
    ping: PingOutcome,
    subscription: Subscription?,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onPing: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val endpoint = ProfileFactory.endpointOf(profile)
    val subtitle = buildString {
        append(ProfileFactory.protocolLabel(profile))
        if (endpoint != null) append(" · ${endpoint.first}:${endpoint.second}")
    }
    ProfileRow(
        name = profile.name,
        coreKind = profile.kind,
        subtitle = subtitle,
        active = active,
        onClick = onSelect,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PingChip(ping = ping)
                subscription?.expireAt?.let { expireAt ->
                    val daysLeft = ((expireAt - System.currentTimeMillis()) / 86_400_000L).toInt()
                    if (daysLeft in 0..7) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${daysLeft}d",
                            style = MaterialTheme.typography.labelSmall,
                            color = StateColors.Connecting,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        DamavandIcon(
                            kind = DamavandIconKind.MoreVert,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 20.dp,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_connect)) },
                            onClick = {
                                menuOpen = false
                                onConnect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_share)) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.servers_ping)) },
                            onClick = {
                                menuOpen = false
                                onPing()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_duplicate)) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** The measured-delay chip: a real number or nothing — never a guess. */
@Composable
fun PingChip(ping: PingOutcome) {
    when (ping) {
        is PingOutcome.Ms -> Text(
            text = "${ping.value} ms",
            style = MaterialTheme.typography.labelMedium,
            color = if (ping.value < 300) StateColors.Connected else StateColors.Connecting,
        )

        PingOutcome.Measuring -> Text(
            text = "…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PingOutcome.Unreachable -> Text(
            text = "✕",
            style = MaterialTheme.typography.labelMedium,
            color = StateColors.Error,
        )

        PingOutcome.Unknown, PingOutcome.NotApplicable -> Spacer(Modifier.height(0.dp))
    }
}
