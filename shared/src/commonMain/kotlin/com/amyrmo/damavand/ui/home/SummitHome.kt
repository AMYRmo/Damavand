@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.core.ConnectionFault
import com.amyrmo.damavand.data.net.NetworkInfo
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.ui.connection.ConnectionUiState
import com.amyrmo.damavand.ui.format.formatBytes
import com.amyrmo.damavand.ui.format.formatElapsed
import com.amyrmo.damavand.ui.format.formatRate
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import com.amyrmo.damavand.ui.theme.MotionBudget
import org.jetbrains.compose.resources.stringResource

/**
 * Summit — the new Damavand home (Amir approved 2026-11-14).
 *
 * One screen, one action: the summit ring. The ring is the connect
 * control; Damavand red (the theme primary under the Damavand color
 * style) is the only accent. Everything else stays quiet stone
 * neutrals. Contour circles inside the ring are the signature detail.
 */
@Composable
internal fun SummitHome(
    ui: ConnectionUiState,
    info: NetworkInfo,
    activeConfigName: String?,
    activeConfigDetail: String?,
    activeLocked: Boolean,
    lockedNote: String?,
    localProxyAddress: String?,
    showConnectionInfo: Boolean,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onShowLogs: () -> Unit,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val live = ui.state == ConnectionState.Connected
    val searching = ui.state == ConnectionState.Connecting ||
        ui.state == ConnectionState.Reconnecting
    val budget = LocalMotionBudget.current
    val animate = !reduceMotion && budget != MotionBudget.Paused
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    val fault = when {
        ui.state == ConnectionState.Error ->
            ui.error?.takeIf { it.isNotBlank() }?.let { ConnectionFault.classify(it) }
        live -> info.fault
        else -> null
    }

    Box(modifier.fillMaxSize().background(cs.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(scroll)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Quiet header: state + elapsed. No greeting, no hero copy.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dotColor = when (ui.state) {
                    ConnectionState.Connected -> cs.primary
                    ConnectionState.Error -> cs.error
                    else -> cs.onSurfaceVariant.copy(alpha = 0.45f)
                }
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(dotColor),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(ui.state.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface,
                    modifier = Modifier.clickable(
                        onClickLabel = stringResource(Res.string.state_connected),
                        onClick = onStatusTap,
                    ),
                )
                Spacer(Modifier.weight(1f))
                if (live) {
                    Text(
                        formatElapsed(ui.elapsedSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            SummitRing(
                state = ui.state,
                pingMs = info.pingMs,
                showPing = showConnectionInfo,
                animate = animate,
                onTap = {
                    if (ui.state != ConnectionState.Disconnecting) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleConnection()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            // Server under the ring: name + place. Tapping opens servers.
            Column(
                Modifier.clickable(
                    onClickLabel = stringResource(Res.string.nav_servers),
                    role = Role.Button,
                    onClick = onBrowseConfigs,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    activeConfigName ?: ui.activeConfigName ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                val place = listOfNotNull(info.city, info.country)
                    .joinToString("، ").takeIf { it.isNotBlank() }
                val detail = place
                    ?: activeConfigDetail?.takeIf { it.isNotBlank() }
                    ?: localProxyAddress?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (activeLocked && lockedNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    lockedNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            AnimatedVisibility(visible = live && showConnectionInfo) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    SummitStats(
                        pingMs = info.pingMs,
                        downloadBps = ui.downloadBps,
                        uploadBps = ui.uploadBps,
                        sessionBytes = ui.totalDownload + ui.totalUpload,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Server card: the same server, tappable, with a live ping chip.
            Surface(
                color = cs.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                onClick = onBrowseConfigs,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (live) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            activeConfigName ?: ui.activeConfigName ?: "–",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(ui.state.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    if (showConnectionInfo && info.pingMs != null) {
                        Surface(
                            color = cs.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                "${info.pingMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            if (fault != null) {
                Box(Modifier.padding(top = 12.dp)) {
                    FaultBanner(fault = fault, connected = live, onShowLogs = onShowLogs)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummitRing(
    state: ConnectionState,
    pingMs: Int?,
    showPing: Boolean,
    animate: Boolean,
    onTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val searching = state == ConnectionState.Connecting ||
        state == ConnectionState.Reconnecting ||
        state == ConnectionState.Disconnecting
    val live = state == ConnectionState.Connected
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "summit-press",
    )

    // Indeterminate spin runs only while connecting, never when idle.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(searching && animate) {
        if (searching && animate) {
            while (true) {
                spin.animateTo(360f, tween(1400))
                spin.snapTo(0f)
            }
        } else {
            spin.snapTo(0f)
        }
    }

    val ringColor = when (state) {
        ConnectionState.Error -> cs.error
        else -> cs.primary
    }
    val ringSize = 252.dp
    Box(
        Modifier
            .size(ringSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(state.labelRes()),
                onClick = onTap,
            )
            .semantics { contentDescription = state.name },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val stroke = 10.dp.toPx()
            // Track.
            drawCircle(
                color = ringColor.copy(alpha = 0.12f),
                radius = r - stroke / 2f,
                center = center,
                style = Stroke(width = stroke),
            )
            // Contour signature: two thin inner circles, one dashed.
            drawCircle(
                color = cs.onSurface.copy(alpha = 0.10f),
                radius = r * 0.78f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = cs.onSurface.copy(alpha = 0.08f),
                radius = r * 0.58f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 8.dp.toPx()),
                        0f,
                    ),
                ),
            )
            // Progress.
            if (live) {
                drawCircle(
                    color = ringColor,
                    radius = r - stroke / 2f,
                    center = center,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else if (searching) {
                val start = if (animate) spin.value - 90f else -90f
                drawArc(
                    color = ringColor,
                    startAngle = start,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // Power glyph when idle or in error.
            if (!live && !searching) {
                val gr = r * 0.34f
                val glyphStroke = 7.dp.toPx()
                drawArc(
                    color = ringColor.copy(alpha = 0.75f),
                    startAngle = 300f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(center.x - gr, center.y - gr),
                    size = androidx.compose.ui.geometry.Size(gr * 2f, gr * 2f),
                    style = Stroke(width = glyphStroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = ringColor.copy(alpha = 0.75f),
                    start = Offset(center.x, center.y - gr * 0.1f),
                    end = Offset(center.x, center.y - gr * 0.95f),
                    strokeWidth = glyphStroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        // Live ping in the middle when connected.
        if (live && showPing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    pingMs?.toString() ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SummitStats(
    pingMs: Int?,
    downloadBps: Long,
    uploadBps: Long,
    sessionBytes: Long,
) {
    val cs = MaterialTheme.colorScheme
    val down = formatRate(downloadBps)
    val up = formatRate(uploadBps)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummitStat(
            value = pingMs?.let { "$it ms" } ?: "–",
            label = stringResource(Res.string.summit_ping),
            modifier = Modifier.weight(1f),
        )
        SummitStat(
            value = "${down.first} ${down.second}",
            label = stringResource(Res.string.home_download),
            modifier = Modifier.weight(1f),
        )
        SummitStat(
            value = "${up.first} ${up.second}",
            label = stringResource(Res.string.home_upload),
            modifier = Modifier.weight(1f),
        )
        SummitStat(
            value = formatBytes(sessionBytes),
            label = stringResource(Res.string.summit_session),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummitStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
