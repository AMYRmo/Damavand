package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.StatsRange
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlin.math.max

/**
 * Stats tab (spec §6): the live session, plus per-server usage by range.
 * Live 60 s download chart + two cards (best session ping, session volume).
 * Usage per server (3.2.29): daily buckets, Today / This week / This month.
 * Nothing older than 30 days is stored (RoutingStore prunes on every
 * load/record), so "This month" is the last 30 days, no more.
 */
@Composable
fun StatsScreen(vm: LiteViewModel, connection: ConnectionUiState, scrollState: ScrollState) {
    val chart by vm.chart.collectAsState()
    val bestPing by vm.bestSessionPing.collectAsState()
    val usageDays by vm.usageDays.collectAsState()
    val range by vm.statsRange.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    val connected = connection.state == ConnState.CONNECTED

    // 1.0.1.2 (#99): re-read the usage file while the tab is open —
    // the service records usage every 10 s, but nothing reloaded the
    // VM's snapshot, so the per-server rows froze at the values from
    // when the tab opened (and stayed stale after a disconnect too).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.refreshUsage()
        while (true) {
            kotlinx.coroutines.delay(10_000)
            vm.refreshUsage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tab_stats),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(16.dp))

        // Live chart card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_live_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                val latest = chart.lastOrNull()
                Text(
                    text = if (connected && latest != null) LiteFormat.formatSpeed(latest) else "—",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                DownloadChart(
                    samples = chart,
                    connected = connected,
                    modifier = Modifier.fillMaxSize()
                )
                if (!connected) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            fontSize = 13.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // X-axis labels: 60s ago / 30s ago / now.
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.stats_axis_60),
                    fontSize = 11.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.stats_axis_30),
                    fontSize = 11.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.stats_axis_now),
                    fontSize = 11.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Two stat cards, equal width.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                labelRes = R.string.stats_best_ping,
                value = if (bestPing > 0) LiteFormat.formatPing(bestPing) else "—",
                subRes = R.string.stats_best_ping_sub,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                labelRes = R.string.stats_session_volume,
                value = LiteFormat.formatVolume(connection.totalDown + connection.totalUp),
                subRes = R.string.stats_session_volume_sub,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(24.dp))

        // Range tabs (3.2.29): Today / This week / This month.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (r in StatsRange.entries) {
                StatsRangeChip(
                    labelRes = when (r) {
                        StatsRange.TODAY -> R.string.stats_today
                        StatsRange.WEEK -> R.string.stats_week
                        StatsRange.MONTH -> R.string.stats_month
                    },
                    selected = range == r,
                    onClick = { vm.selectStatsRange(r) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Per-server usage (3.1.0, PattNG): bytes per profile in the
        // selected range, summed over the daily buckets. Day keys are
        // ISO dates, so lexicographic compare is chronological.
        Text(
            text = stringResource(R.string.stats_usage_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(8.dp))
        val deletedLabel = stringResource(R.string.stats_usage_deleted)
        // 1.0.1.2 (#74): the total covers ALL rows in range, not just
        // the five shown — the old rangeTotal summed the displayed
        // rows only.
        val (usageRows, rangeTotal) = remember(usageDays, range, profiles, deletedLabel) {
            val cutoff = java.time.LocalDate.now()
                .minusDays((range.days - 1).toLong()).toString()
            val totals = HashMap<String, Long>()
            for ((day, perProfile) in usageDays) {
                if (day < cutoff) continue
                for ((id, bytes) in perProfile) {
                    totals[id] = (totals[id] ?: 0L) + bytes
                }
            }
            // 1.0.1.2 (#100): usage of deleted profiles used to vanish
            // from the rows AND the total. Keep it as one "deleted"
            // row so the total stays honest.
            val orphanBytes = totals.entries
                .filter { (id, _) -> profiles.none { it.id == id } }
                .sumOf { it.value }
            val rows = totals.entries
                .mapNotNull { (id, bytes) ->
                    profiles.firstOrNull { it.id == id }?.let { it to bytes }
                }
                .toMutableList()
            if (orphanBytes > 0) {
                rows += Profile(
                    id = "__deleted__",
                    link = "",
                    name = deletedLabel,
                    proto = "",
                    address = "",
                    port = 0,
                ) to orphanBytes
            }
            rows.sortedByDescending { it.second }.take(5) to totals.values.sum()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            if (usageRows.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_usage_empty),
                    fontSize = 13.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            } else {
                val maxBytes = usageRows.maxOf { it.second }.coerceAtLeast(1L)
                for ((profile, bytes) in usageRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = LiteFormat.formatVolume(bytes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(LiteColors.hairline, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((bytes.toFloat() / maxBytes).coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(LiteColors.accent, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.stats_usage_total,
                        LiteFormat.formatVolume(rangeTotal)
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Reset button (3.2.30): wipes ALL recorded usage, after a confirm.
        ResetUsageButton(onClick = { showResetConfirm = true })
        Spacer(Modifier.height(24.dp))
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.stats_reset_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.stats_reset_body),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearUsage()
                    showResetConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.stats_reset_confirm),
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontFamily = Vazirmatn
                    )
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    labelRes: Int,
    value: String,
    subRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 11.sp,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(subRes),
            fontSize = 11.sp,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn,
            maxLines = 2
        )
    }
}

/**
 * Range tab (3.2.29): same pill as the ServersScreen GroupChip —
 * accent background + white text when selected, surface + secondary
 * text otherwise.
 */
@Composable
private fun StatsRangeChip(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LiteColors.accent else LiteColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}

/**
 * Reset button (3.2.30): wipes ALL recorded usage. Same pill as the
 * Settings GeoPillButton, in the error color — it is a destructive
 * action, so the tap asks for confirmation first.
 */
@Composable
private fun ResetUsageButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.Transparent, CircleShape)
            .border(1.5.dp, LiteColors.error, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.stats_reset_usage),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.error,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}

/**
 * Live download chart (spec §6.2): white 2.5px smooth line, white 4%
 * area fill, red 6px last point, horizontal hairlines only, no vertical
 * gridlines, no Y-axis numbers. Samples are bytes/sec (up to 60).
 */
@Composable
private fun DownloadChart(
    samples: List<Long>,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Horizontal gridlines (white 6%).
        val gridColor = LiteColors.text.copy(alpha = 0.06f)
        for (f in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, h * f),
                end = Offset(w, h * f),
                strokeWidth = 1.dp.toPx()
            )
        }
        if (!connected || samples.isEmpty()) return@Canvas

        // Normalise to the peak sample (Mbps, so the scale is speed-based).
        val values = samples.map { it * 8.0 / 1_000_000.0 }
        val peak = max(1.0, values.max())
        val n = values.size
        fun point(i: Int): Offset {
            val x = if (n <= 1) 0f else (i.toFloat() / 59f) * w
            val y = (1f - (values[i] / peak).toFloat()) * h
            return Offset(x, y)
        }
        // Smooth line: Catmull-Rom → cubic Bezier.
        val linePath = Path()
        if (n == 1) {
            linePath.moveTo(0f, point(0).y)
            linePath.lineTo(w, point(0).y)
        } else {
            val pts = (0 until n).map { point(it) }
            linePath.moveTo(pts[0].x, pts[0].y)
            for (i in 0 until n - 1) {
                val p0 = pts[max(0, i - 1)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[minOf(n - 1, i + 2)]
                val c1 = Offset(
                    p1.x + (p2.x - p0.x) / 6f,
                    p1.y + (p2.y - p0.y) / 6f
                )
                val c2 = Offset(
                    p2.x - (p3.x - p1.x) / 6f,
                    p2.y - (p3.y - p1.y) / 6f
                )
                linePath.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
            }
        }
        // Area under the line: white 4% fill.
        val areaPath = Path().apply {
            addPath(linePath)
            if (n > 0) {
                lineTo(point(n - 1).x, h)
                lineTo(point(0).x, h)
                close()
            }
        }
        drawPath(areaPath, LiteColors.text.copy(alpha = 0.04f))
        // The line itself: text color 90%, 2.5px.
        drawPath(
            path = linePath,
            color = LiteColors.text.copy(alpha = 0.9f),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        // Last point: red dot 6px.
        val last = point(n - 1)
        drawCircle(
            color = LiteColors.accent,
            radius = 3.dp.toPx(),
            center = last
        )
    }
}
