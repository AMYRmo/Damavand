@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.data.net.NetworkInfo
import com.amyrmo.damavand.domain.model.ConnectionFault
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.ui.bento.BentoServerPick
import com.amyrmo.damavand.ui.components.FlagBadge
import com.amyrmo.damavand.ui.connection.ConnectionUiState
import com.amyrmo.damavand.ui.format.formatElapsed
import org.jetbrains.compose.resources.stringResource

// Summit home, rebuilt spec-by-spec from the approved prototype:
// fixed dark tokens, header with wordmark + status pill, 288dp summit ring
// with contour lines and a 0-100 connecting percentage, always-visible stats
// row, server card with flag + core tag, and the fastest-server button.
// NOTE: no direct Res.string.* access in this file (see the 1.5.2 CI lesson);
// every label arrives as a String parameter resolved by HomeScreen.
@Composable
internal fun SummitHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    activeConfigName: String?,
    activeConfigDetail: String?,
    activeLocked: Boolean,
    lockedNote: String?,
    localProxyAddress: String?,
    reduceMotion: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    pingLabel: String,
    downloadLabel: String,
    uploadLabel: String,
    sessionLabel: String,
    subtitle: String,
    tapToConnectLabel: String,
    autoFastestLabel: String,
    stepKeysLabel: String,
    stepRouteLabel: String,
    stepFinishLabel: String,
    statusIdleLabel: String,
    trafficDirectLabel: String,
    connectedLabel: String,
    fastestLabel: String,
    fastestPick: BentoServerPick?,
    engineLabel: String?,
    countryCode: String?,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onShowLogs: () -> Unit,
    onStatusTap: () -> Unit,
    onPickServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = ui.state == ConnectionState.Connected
    val searching = ui.state == ConnectionState.Connecting ||
        ui.state == ConnectionState.Reconnecting
    val budget = LocalMotionBudget.current
    val animate = !reduceMotion && budget != MotionBudget.Paused
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    // Connecting percentage: 0 -> 100 in ~1.7s ease-out, then hold at 100
    // until the state changes (same as the prototype).
    val connectProgress = remember { Animatable(0f) }
    LaunchedEffect(searching) {
        if (searching) {
            connectProgress.snapTo(0f)
            if (animate) {
                connectProgress.animateTo(
                    100f,
                    tween(1700, easing = FastOutSlowInEasing),
                )
            } else {
                connectProgress.snapTo(100f)
            }
        } else {
            connectProgress.snapTo(0f)
        }
    }
    val stepLabel = when {
        connectProgress.value < 34f -> stepKeysLabel
        connectProgress.value < 67f -> stepRouteLabel
        else -> stepFinishLabel
    }

    val fault: ConnectionFault? = when {
        ui.state == ConnectionState.Error -> ConnectionFault.classify(ui.error)
        live -> info.fault
        else -> null
    }
    val placeLine = listOfNotNull(
        listOfNotNull(info.city, info.country).joinToString("، ").takeIf { it.isNotBlank() },
        activeConfigDetail,
        localProxyAddress,
    ).firstOrNull()

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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- Header: wordmark + subtitle + status pill ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SummitMountainLogo(Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "دماوند",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = SummitText,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = SummitTextMuted,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val dotColor = when {
                        live -> SummitRed
                        searching -> SummitAmber
                        ui.state == ConnectionState.Error -> SummitError
                        else -> SummitTextMuted
                    }
                    Surface(
                        onClick = onStatusTap,
                        shape = CircleShape,
                        color = SummitSurface,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Text(
                                stringResource(ui.state.labelRes()),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = SummitTextSecondary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Summit ring (288dp) ----
                SummitRing(
                    live = live,
                    searching = searching,
                    hasError = ui.state == ConnectionState.Error,
                    progress = connectProgress.value,
                    pingMs = info.pingMs,
                    connectedLabel = connectedLabel,
                    city = info.city,
                    tapToConnectLabel = tapToConnectLabel,
                    autoFastestLabel = autoFastestLabel,
                    stepLabel = stepLabel,
                    animate = animate,
                    onToggleConnection = onToggleConnection,
                )

                // ---- Status / server line under the ring ----
                Spacer(Modifier.height(16.dp))
                if (!live && !searching) {
                    Text(
                        statusIdleLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = SummitText,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        trafficDirectLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = SummitTextMuted,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        activeConfigName ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = SummitText,
                        textAlign = TextAlign.Center,
                    )
                    if (placeLine != null) {
                        Text(
                            placeLine,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SummitTextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // ---- Stats row: always visible ----
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummitStat(
                        value = info.pingMs?.let { "$it" } ?: "–",
                        unit = "ms",
                        label = pingLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummitStat(
                        value = formatMbps(ui.downloadBps),
                        unit = "Mbps",
                        label = downloadLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummitStat(
                        value = formatMbps(ui.uploadBps),
                        unit = "Mbps",
                        label = uploadLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummitStat(
                        value = formatSessionMb(ui.totalDownload + ui.totalUpload),
                        unit = "MB",
                        label = sessionLabel,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---- Current server card ----
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onBrowseConfigs,
                    shape = RoundedCornerShape(20.dp),
                    color = SummitSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SummitSurface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (countryCode != null) {
                                FlagBadge(
                                    countryCode = countryCode,
                                    size = 44.dp,
                                )
                            } else {
                                Text(
                                    activeConfigName?.firstOrNull()?.toString() ?: "؟",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = SummitTextSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                activeConfigName ?: "",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = SummitText,
                                maxLines = 1,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (placeLine != null) {
                                    Text(
                                        placeLine,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                        ),
                                        color = SummitTextSecondary,
                                        maxLines = 1,
                                    )
                                }
                                if (engineLabel != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            SummitHairlineStrong,
                                        ),
                                    ) {
                                        Text(
                                            engineLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                            ),
                                            color = SummitTextSecondary,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        if (info.pingMs != null) {
                            Surface(
                                shape = CircleShape,
                                color = SummitSurface2,
                            ) {
                                Text(
                                    "${info.pingMs} ms",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = SummitText,
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 6.dp,
                                    ),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        SummitChevron(Modifier.size(20.dp))
                    }
                }

                // ---- Fastest server button ----
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = {
                        val id = fastestPick?.id
                        if (id != null) onPickServer(id) else onBrowseConfigs()
                    },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        SummitHairlineStrong,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SummitBolt(Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            fastestLabel,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = SummitText,
                        )
                    }
                }

                if (fault != null) {
                    Spacer(Modifier.height(12.dp))
                    FaultBanner(
                        fault = fault,
                        connected = live,
                        onShowLogs = onShowLogs,
                    )
                }
                if (activeLocked && lockedNote != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = SummitTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SummitRing(
    live: Boolean,
    searching: Boolean,
    hasError: Boolean,
    progress: Float,
    pingMs: Int?,
    connectedLabel: String,
    city: String?,
    tapToConnectLabel: String,
    autoFastestLabel: String,
    stepLabel: String,
    animate: Boolean,
    onToggleConnection: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "summit-ring-press",
    )
    val haptics = LocalHapticFeedback.current
    val ringColor = if (hasError) SummitError else SummitRed

    Box(
        Modifier
            .size(288.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggleConnection,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Five contour lines behind the ring (prototype: white 3.5-7%).
            val contourRadii = listOf(141f, 126f, 110f, 94f, 78f)
            val contourAlphas = listOf(0.035f, 0.04f, 0.05f, 0.06f, 0.07f)
            contourRadii.forEachIndexed { i, r ->
                drawCircle(
                    color = Color.White.copy(alpha = contourAlphas[i]),
                    radius = r.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(1.dp.toPx()),
                )
            }
            // Glass button (260dp) + hairline.
            drawCircle(
                color = SummitSurface.copy(alpha = 0.55f),
                radius = 130.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = SummitHairlineStrong,
                radius = 130.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx()),
            )
            // Track (3dp, white 7%) + progress (3dp, Damavand Red), r = 124dp.
            val track = 124.dp.toPx()
            val trackRect = Offset(cx - track, cy - track)
            val trackSize = Size(track * 2f, track * 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = trackRect,
                size = trackSize,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
            val sweep = when {
                live -> 360f
                searching -> progress / 100f * 360f
                else -> 0f
            }
            if (sweep > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = trackRect,
                    size = trackSize,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                live -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            pingMs?.let { "$it" } ?: "–",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = SummitText,
                        )
                        Text(
                            " ms",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            color = SummitTextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        connectedLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = SummitText,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (city != null) {
                        Text(
                            city,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SummitTextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                searching -> {
                    Text(
                        "${progress.toInt()}%",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = SummitText,
                    )
                    Text(
                        stepLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = SummitTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    SummitPowerGlyph(Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tapToConnectLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = SummitText,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        autoFastestLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = SummitTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummitStat(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SummitSurface,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SummitText,
                )
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = SummitTextMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = SummitTextMuted,
            )
        }
    }
}

/** Mountain wordmark: a peak with a red summit dot. */
@Composable
private fun SummitMountainLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val peak = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.97f, h * 0.94f)
            lineTo(w * 0.03f, h * 0.94f)
            close()
        }
        drawPath(peak, SummitText)
        drawCircle(
            color = SummitRed,
            radius = w * 0.09f,
            center = Offset(w * 0.5f, h * 0.10f),
        )
    }
}

/** Power glyph (idle ring center), drawn like the prototype's 40px icon. */
@Composable
private fun SummitPowerGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.36f
        drawArc(
            color = SummitText.copy(alpha = 0.75f),
            startAngle = 300f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = SummitText.copy(alpha = 0.75f),
            start = Offset(cx, cy - r - 4.dp.toPx()),
            end = Offset(cx, cy - r * 0.25f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Lightning bolt for the fastest-server button. */
@Composable
private fun SummitBolt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val bolt = Path().apply {
            moveTo(13f * s, 2f * s)
            lineTo(4.5f * s, 13.5f * s)
            lineTo(11f * s, 13.5f * s)
            lineTo(9.5f * s, 22f * s)
            lineTo(19f * s, 10f * s)
            lineTo(12.5f * s, 10f * s)
            close()
        }
        drawPath(bolt, SummitText)
    }
}

/** Left-pointing chevron (RTL forward) for the server card. */
@Composable
private fun SummitChevron(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(
            color = SummitTextMuted,
            start = Offset(w * 0.68f, h * 0.22f),
            end = Offset(w * 0.32f, h * 0.5f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SummitTextMuted,
            start = Offset(w * 0.32f, h * 0.5f),
            end = Offset(w * 0.68f, h * 0.78f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

internal fun formatMbps(bytesPerSecond: Long): String {
    val mbps = bytesPerSecond * 8.0 / 1_000_000.0
    return when {
        mbps < 0.05 -> "0"
        mbps < 10 -> oneDecimal(mbps)
        else -> "${mbps.toInt()}"
    }
}

internal fun formatSessionMb(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return when {
        mb < 1 -> "0"
        mb < 10 -> oneDecimal(mb)
        else -> "${mb.toInt()}"
    }
}

/** Common-safe one-decimal formatting (no String.format in common code). */
internal fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toInt()
    return "${rounded / 10}.${rounded % 10}"
}
