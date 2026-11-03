package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.LogBus
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.StateColors

/**
 * The shared live-log panel (§5.2): the session [LogBus] in a bounded,
 * solid (never glass) monospace box, auto-scrolling to the tail. Editors
 * pass a [filter] keyword (their core's name) to narrow the stream; the
 * Logs screen builds its richer filtering on the same bus.
 *
 * Priority colouring reads the AppLog line shape (`… E/tag: msg`): error
 * lines in the error colour, warnings in the connecting colour.
 */
@Composable
fun LiveLogPanel(
    modifier: Modifier = Modifier,
    filter: String? = null,
    height: androidx.compose.ui.unit.Dp = 180.dp,
) {
    val lines by LogBus.lines.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        LogBus.attach()
        onDispose { LogBus.detach() }
    }
    val visible = if (filter.isNullOrBlank()) {
        lines
    } else {
        lines.filter { it.contains(filter, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                DamavandShapeTokens.Card,
            )
            .padding(10.dp),
    ) {
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.live_log_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(visible) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = logLineColor(line),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** Priority colour for a log line (AppLog-stamped lines carry ` L/tag:`). */
@Composable
fun logLineColor(line: String): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        line.contains(" E/") || line.contains(" F/") -> StateColors.Error
        line.contains(" W/") -> StateColors.Connecting
        else -> scheme.onSurfaceVariant
    }
}
