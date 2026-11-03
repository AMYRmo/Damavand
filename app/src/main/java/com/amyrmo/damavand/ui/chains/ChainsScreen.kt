package com.amyrmo.damavand.ui.chains

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.logic.ChainValidator
import com.amyrmo.damavand.logic.CoreCapabilities
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.GlassCard
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.accent
import java.util.UUID

/**
 * The Chains destination (§5.2): chain cards with ordered accent chips
 * and broken/warning badges from [ChainValidator], and the builder with
 * capability-filtered picking, reorder, live validation (Save disabled
 * on any error) and the traffic-preview sentence.
 */
@Composable
fun ChainsScreen(
    vm: AppViewModel,
    onConnect: (Profile) -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val byId = remember(profiles) { profiles.associateBy { it.id } }
    val chains = profiles.filterIsInstance<ChainProfile>()

    var editing by remember { mutableStateOf<ChainProfile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ChainProfile?>(null) }

    val current = editing
    if (current != null) {
        ChainBuilder(
            chain = current,
            allProfiles = profiles,
            onSave = { saved ->
                vm.upsertProfile(saved)
                vm.selectProfile(saved.id)
                editing = null
            },
            onBack = { editing = null },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = 16.dp,
                bottom = Dimens.DockClearance + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chains.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chains_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(chains, key = { it.id }) { chain ->
                ChainCard(
                    chain = chain,
                    byId = byId,
                    onConnect = { onConnect(chain) },
                    onEdit = {
                        editing = chain
                        editingIsNew = false
                    },
                    onDuplicate = { vm.duplicateProfile(chain) },
                    onDelete = { confirmDelete = chain },
                )
            }
        }
        FloatingActionButton(
            onClick = {
                editing = ChainProfile(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    hopIds = emptyList(),
                )
                editingIsNew = true
            },
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

    confirmDelete?.let { chain ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(chain.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeProfile(chain.id)
                        confirmDelete = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = StateColors.Error)
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

/** A chain card: name, ordered per-hop accent chips, validation badges, actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainCard(
    chain: ChainProfile,
    byId: Map<String, Profile>,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val resolved = chain.hopIds.mapNotNull { byId[it] }
    val hasMissing = resolved.size != chain.hopIds.size
    val validation = if (hasMissing) null else ChainValidator.validateProfiles(resolved)
    val broken = hasMissing || (validation?.errors?.isNotEmpty() == true)
    val warned = validation?.warnings?.isNotEmpty() == true

    GlassCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chain.name,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (broken) {
                Badge(text = stringResource(R.string.chain_broken), color = StateColors.Error)
            } else if (warned) {
                Badge(text = stringResource(R.string.chain_warning), color = StateColors.Connecting)
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chain.hopIds.forEachIndexed { index, hopId ->
                val member = byId[hopId]
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(scheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(member?.kind?.accent() ?: StateColors.Error),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = member?.name ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface,
                    )
                    if (index < chain.hopIds.lastIndex) {
                        Text(
                            text = " →",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect, enabled = !broken) {
                Text(stringResource(R.string.common_connect))
            }
            OutlinedButton(onClick = onEdit) {
                Text(stringResource(R.string.common_edit))
            }
            OutlinedButton(onClick = onDuplicate) {
                Text(stringResource(R.string.common_duplicate))
            }
            IconButton(onClick = onDelete) {
                DamavandIcon(
                    kind = DamavandIconKind.Trash,
                    tint = StateColors.Error,
                    size = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * The chain builder (§5.2): name, ordered hop rows with reorder and
 * remove, a capability-filtered picker, live [ChainValidator] messages,
 * the traffic-preview sentence and the simple-mode hint. Save stays
 * disabled while any error exists.
 */
@Composable
private fun ChainBuilder(
    chain: ChainProfile,
    allProfiles: List<Profile>,
    onSave: (ChainProfile) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val byId = remember(allProfiles) { allProfiles.associateBy { it.id } }
    var name by remember(chain.id) { mutableStateOf(chain.name) }
    var hopIds by remember(chain.id) { mutableStateOf(chain.hopIds) }
    var pickerOpen by remember { mutableStateOf(false) }

    val resolved = hopIds.mapNotNull { byId[it] }
    val hasMissing = resolved.size != hopIds.size
    val validation = if (hasMissing) null else ChainValidator.validateProfiles(resolved)
    val errors = validation?.errors.orEmpty()
    val warnings = validation?.warnings.orEmpty()
    val nameOf: (String) -> String = { id -> byId[id]?.name ?: id.take(8) }
    val saveEnabled = name.isNotBlank() && !hasMissing && errors.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = 12.dp, bottom = Dimens.DockClearance + 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                DamavandIcon(
                    kind = DamavandIconKind.Close,
                    tint = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.chain_builder),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSave(chain.copy(name = name.trim(), hopIds = hopIds)) },
                enabled = saveEnabled,
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
        Spacer(Modifier.height(10.dp))
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
        )
        Spacer(Modifier.height(14.dp))
        SectionHeader(title = stringResource(R.string.chain_hops))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hopIds.size, key = { hopIds[it] }) { index ->
                val member = byId[hopIds[index]]
                val positionLabel = when {
                    hopIds.size == 1 || index == 0 -> stringResource(R.string.chain_entry)
                    index == hopIds.lastIndex -> stringResource(R.string.chain_exit)
                    else -> stringResource(R.string.chain_hop_n, index + 1)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(member?.kind?.accent() ?: StateColors.Error),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member?.name ?: "?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = positionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                hopIds = hopIds.toMutableList().also {
                                    val item = it.removeAt(index)
                                    it.add(index - 1, item)
                                }
                            }
                        },
                        enabled = index > 0,
                    ) {
                        DamavandIcon(DamavandIconKind.ArrowUp, scheme.onSurfaceVariant, size = 18.dp)
                    }
                    IconButton(
                        onClick = {
                            if (index < hopIds.lastIndex) {
                                hopIds = hopIds.toMutableList().also {
                                    val item = it.removeAt(index)
                                    it.add(index + 1, item)
                                }
                            }
                        },
                        enabled = index < hopIds.lastIndex,
                    ) {
                        DamavandIcon(DamavandIconKind.ArrowDown, scheme.onSurfaceVariant, size = 18.dp)
                    }
                    IconButton(
                        onClick = { hopIds = hopIds - hopIds[index] },
                    ) {
                        DamavandIcon(DamavandIconKind.Trash, StateColors.Error, size = 18.dp)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { pickerOpen = true },
                    enabled = hopIds.size < ChainValidator.MAX_HOPS,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.chain_add_hop))
                }
            }
            if (hasMissing) {
                item {
                    Text(
                        text = stringResource(R.string.chain_broken),
                        style = MaterialTheme.typography.bodySmall,
                        color = StateColors.Error,
                    )
                }
            }
            errors.forEach { error ->
                item(key = "err-$error") {
                    Text(
                        text = chainErrorText(error, nameOf),
                        style = MaterialTheme.typography.bodySmall,
                        color = StateColors.Error,
                    )
                }
            }
            warnings.forEach { warning ->
                item(key = "warn-$warning") {
                    Text(
                        text = chainWarningText(warning, nameOf),
                        style = MaterialTheme.typography.bodySmall,
                        color = StateColors.Connecting,
                    )
                }
            }
            if (resolved.size >= 2) {
                item {
                    Text(
                        text = stringResource(
                            R.string.chain_preview,
                            resolved.joinToString(" → ") { it.name },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.chain_simple_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickerOpen) {
        val position = hopIds.size
        val candidates = allProfiles.filter { candidate ->
            candidate !is ChainProfile && candidate !is AutoSelectProfile &&
                candidate.id !in hopIds &&
                run {
                    val caps = CoreCapabilities.of(candidate)
                    (position == 0 || caps.canDialThroughSocks) &&
                        (caps.maxPosition == null || position <= caps.maxPosition!!)
                }
        }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.chain_add_hop)) },
            text = {
                LazyColumn {
                    items(candidates, key = { it.id }) { candidate ->
                        TextButton(
                            onClick = {
                                hopIds = hopIds + candidate.id
                                pickerOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = candidate.name,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = candidate.kind.name.lowercase(),
                                color = candidate.kind.accent(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (candidates.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.chains_empty),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
