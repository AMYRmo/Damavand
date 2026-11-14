package com.amyrmo.damavand.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.ui.connection.ConnectionViewModel
import kotlinx.coroutines.delay

// Summit Stats tab (prototype): a live 60-second download-speed chart plus
// session totals and best ping. Real data only — no demo numbers.
// NOTE: no direct Res.string.* access in this file; labels arrive as params.
@Composable
internal fun SummitStatsScreen(
    connectionVm: ConnectionViewModel,
    homeVm: HomeViewModel,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    title: String,
    sessionTotalLabel: String,
    bestPingLabel: String,
    pingLabel: String,
    emptyNoteLabel: String,
    axis60Label: String,
    axis30Label: String,
    axisNowLabel: String,
    modifier: Modifier = Modifier,
) {
    val ui by connectionVm.ui.collectAsStateWithLifecycle()
    val info by homeVm.info.collectAsStateWithLifecycle()
    val live = ui.state == ConnectionState.Connected
    val scroll = rememberScrollState()

    // Last 60 seconds of download speed (Mbps), sampled once a second.
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val mbps = ui.downloadBps * 8f / 1_000_000f
            samples.add(mbps)
            while (samples.size > 60) samples.removeAt(0)
        }
    }
    var bestPing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(info.pingMs, live) {
        if (live) {
            info.pingMs?.let { ms ->
                bestPing = bestPing?.let { minOf(it, ms) } ?: ms
            }
        }
    }
    val hasData = samples.any { it > 0f }

    Box(
        modifier
            .fillMaxSize()
            .background(SummitBg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = SummitText,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )

                // ---- Live chart card ----
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SummitSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (!live && !hasData) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    emptyNoteLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                    ),
                                    color = SummitTextMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Canvas(
                                Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                            ) {
                                val w = size.width
                                val h = size.height
                                val max = (samples.maxOrNull() ?: 0f).coerceAtLeast(1f)
                                val points = samples.mapIndexed { i, v ->
                                    val x = if (samples.size <= 1) w
                                    else i.toFloat() / (samples.size - 1) * w
                                    val y = h - (v / max) * h * 0.85f - h * 0.05f
                                    Offset(x, y)
                                }
                                if (points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(points[0].x, points[0].y)
                                        points.drop(1).forEach { lineTo(it.x, it.y) }
                                    }
                                    drawPath(
                                        path,
                                        Color.White,
                                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                                    )
                                }
                                points.lastOrNull()?.let { last ->
                                    drawCircle(
                                        color = SummitRed,
                                        radius = 4.dp.toPx(),
                                        center = last,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                axis60Label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = SummitTextMuted,
                            )
                            Text(
                                axis30Label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = SummitTextMuted,
                            )
                            Text(
                                axisNowLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = SummitTextMuted,
                            )
                        }
                    }
                }

                // ---- Session total (wide card) ----
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SummitSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            sessionTotalLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SummitTextMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                formatSessionMb(ui.totalDownload + ui.totalUpload),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = SummitText,
                            )
                            Text(
                                " MB",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = SummitTextMuted,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                }

                // ---- Best ping + current ping ----
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SummitSurface,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                bestPingLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = SummitTextMuted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                bestPing?.let { "$it ms" } ?: "—",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = SummitText,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SummitSurface,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                pingLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = SummitTextMuted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                info.pingMs?.let { "$it ms" } ?: "—",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = SummitText,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
