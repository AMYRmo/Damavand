package com.amyrmo.damavand.ui.logs

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.LogBus
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.logLineColor
import com.amyrmo.damavand.ui.theme.Dimens

/**
 * The Logs screen (§5.2): the [LogBus] stream with a text search and
 * filter chips, priority colours, share-as-text and clear.
 *
 * Honest limitation: LogBus lines are plain strings — core output is
 * forwarded verbatim and carries no structured hop metadata — so the
 * per-core chips filter by content (the core's name appearing in the
 * line, e.g. in AppLog tags), not by a hop id. Lines that never mention
 * a core only appear under All/level filters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lines by LogBus.lines.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        LogBus.attach()
        onDispose { LogBus.detach() }
    }
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf(0) } // index into CHIP_KEYS; 0 = all

    val chipLabels = listOf(
        stringResource(R.string.logs_filter_all),
        stringResource(R.string.logs_filter_error),
        stringResource(R.string.logs_filter_warning),
        "aether", "mihomo", "psiphon", "whitedns", "xray",
    )
    val filtered = lines.filter { line ->
        val chipOk = when (chip) {
            0 -> true
            1 -> line.contains(" E/") || line.contains(" F/")
            2 -> line.contains(" W/")
            else -> line.contains(CHIP_KEYS[chip], ignoreCase = true)
        }
        chipOk && (query.isBlank() || line.contains(query, ignoreCase = true))
    }
    val listState = rememberLazyListState()
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(filtered.lastIndex)
    }

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
                text = stringResource(R.string.dest_logs),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, filtered.joinToString("\n"))
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
            ) {
                DamavandIcon(
                    kind = DamavandIconKind.Share,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { LogBus.clear() }) {
                DamavandIcon(
                    kind = DamavandIconKind.Trash,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        ) {
            FormTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.logs_search),
                forceLtr = true,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chipLabels.forEachIndexed { index, label ->
                    FilterChip(
                        selected = chip == index,
                        onClick = { chip = index },
                        label = { Text(label) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.DockClearance + 8.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    MaterialTheme.shapes.medium,
                )
                .padding(10.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(filtered) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = logLineColor(line),
                            maxLines = 4,
                        )
                    }
                }
            }
        }
    }
}

private val CHIP_KEYS = listOf("", "", "", "aether", "mihomo", "psiphon", "whitedns", "xray")
