package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlin.math.max

/**
 * Stats tab (spec §6): the live session, nothing else.
 * Live 60 s download chart + two cards (best session ping, session volume).
 * No daily total — there is no real daily data.
 */
@Composable
fun StatsScreen(vm: LiteViewModel, connection: ConnectionUiState, scrollState: ScrollState) {
    val chart by vm.chart.collectAsState()
    val bestPing by vm.bestSessionPing.collectAsState()
    val connected = connection.state == ConnState.CONNECTED

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
        val gridColor = Color.White.copy(alpha = 0.06f)
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
        drawPath(areaPath, Color.White.copy(alpha = 0.04f))
        // The line itself: white 90%, 2.5px.
        drawPath(
            path = linePath,
            color = Color(0xFFF2F3F5).copy(alpha = 0.9f),
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
