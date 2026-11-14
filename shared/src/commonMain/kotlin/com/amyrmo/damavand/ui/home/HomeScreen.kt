@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.amyrmo.damavand.ui.home


import org.jetbrains.compose.resources.StringResource

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.amyrmo.damavand.ui.theme.MotionBudget
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.domain.model.ConnectButtonStyle
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.domain.model.AppDesign
import com.amyrmo.damavand.ui.connection.ConnectionViewModel
import com.amyrmo.damavand.ui.theme.Personalization
import com.amyrmo.damavand.ui.theme.ZedCyan
import com.amyrmo.damavand.ui.theme.ZedHotPink
import com.amyrmo.damavand.ui.theme.ZedLime
import com.amyrmo.damavand.ui.theme.ZedViolet
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(
    connectionVm: ConnectionViewModel,
    activeConfigName: String?,
    contentPadding: PaddingValues,

    activeConfigDetail: String? = null,

    exitGeneration: Int = 0,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onSecretUnlocked: () -> Unit = {},
    modifier: Modifier = Modifier,
    lockedNote: String? = null,
    localProxyAddress: String? = null,
    activeLocked: Boolean = false,
    reduceMotion: Boolean = false,

    showConnectionInfo: Boolean = true,

    ipApiUrl: String = "",

    delayTestUrl: String = "",

    personalization: Personalization = Personalization.Default,

    connectStyle: ConnectButtonStyle = ConnectButtonStyle.Pill,
    design: AppDesign = AppDesign.Pixel,
    bentoActive: com.amyrmo.damavand.ui.bento.BentoActiveServer? = null,
    bentoPicks: List<com.amyrmo.damavand.ui.bento.BentoServerPick> = emptyList(),
    onPickServer: (String) -> Unit = {},
    onOpenSpeedTest: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
    homeVm: HomeViewModel = viewModel { HomeViewModel() },
) {
    val ui by connectionVm.ui.collectAsStateWithLifecycle()
    val live = ui.state == ConnectionState.Connected
    val info by homeVm.info.collectAsStateWithLifecycle()
    val infoExpanded by homeVm.expanded.collectAsStateWithLifecycle()

    LaunchedEffect(live, ui.sessionId, exitGeneration, showConnectionInfo, ipApiUrl, delayTestUrl) {
        homeVm.preferredIpApiUrl = ipApiUrl.ifBlank { null }
        homeVm.preferredDelayUrl = delayTestUrl.ifBlank { null }
        if (live && showConnectionInfo) homeVm.refresh(settleDelayMs = 300) else homeVm.clear()
    }

    var burst by remember { mutableIntStateOf(0) }

    var laser by remember { mutableIntStateOf(0) }

    var statusTaps by remember { mutableIntStateOf(0) }
    var statusLastTap by remember { mutableLongStateOf(0L) }
    var showLogs by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier.fillMaxSize()) {
        if (design == AppDesign.Summit) {
            SummitHome(
                ui = ui,
                info = info,
                activeConfigName = activeConfigName,
                activeConfigDetail = activeConfigDetail,
                activeLocked = activeLocked,
                lockedNote = lockedNote,
                localProxyAddress = localProxyAddress,
                reduceMotion = reduceMotion,
                contentPadding = contentPadding,
                pingLabel = stringResource(Res.string.summit_ping),
                downloadLabel = stringResource(Res.string.home_download),
                uploadLabel = stringResource(Res.string.home_upload),
                sessionLabel = stringResource(Res.string.summit_session),
                subtitle = stringResource(Res.string.summit_subtitle),
                tapToConnectLabel = stringResource(Res.string.summit_tap_to_connect),
                autoFastestLabel = stringResource(Res.string.summit_auto_fastest),
                stepKeysLabel = stringResource(Res.string.summit_step_keys),
                stepRouteLabel = stringResource(Res.string.summit_step_route),
                stepFinishLabel = stringResource(Res.string.summit_step_finish),
                statusIdleLabel = stringResource(Res.string.summit_status_idle),
                trafficDirectLabel = stringResource(Res.string.summit_traffic_direct),
                connectedLabel = activeConfigName?.let {
                    "${stringResource(Res.string.state_connected)} — $it"
                } ?: stringResource(Res.string.state_connected),
                fastestLabel = stringResource(Res.string.summit_fastest_server),
                fastestPick = bentoPicks.firstOrNull(),
                engineLabel = bentoActive?.engine,
                countryCode = bentoActive?.country,
                onToggleConnection = onToggleConnection,
                onBrowseConfigs = onBrowseConfigs,
                onShowLogs = { showLogs = true },
                onPickServer = onPickServer,
                onStatusTap = {
                    val now = System.currentTimeMillis()
                    statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                    statusLastTap = now
                    if (statusTaps >= 3) {
                        statusTaps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        laser++
                    }
                },
            )
        } else if (design == AppDesign.Bento) {
            val budget = com.amyrmo.damavand.ui.theme.LocalMotionBudget.current
            com.amyrmo.damavand.ui.bento.BentoHome(
                ui = ui,
                info = info,
                active = bentoActive,
                picks = bentoPicks,
                lockedNote = lockedNote,
                localProxyAddress = localProxyAddress,
                showConnectionInfo = showConnectionInfo,
                animate = !reduceMotion && budget != com.amyrmo.damavand.ui.theme.MotionBudget.Paused,
                contentPadding = contentPadding,
                onToggleConnection = onToggleConnection,
                onBrowseConfigs = onBrowseConfigs,
                onPickServer = onPickServer,
                onOpenSpeedTest = onOpenSpeedTest,
                onOpenMap = onOpenMap,
                onRefreshInfo = { homeVm.refresh(force = true) },
                onSecretUnlocked = onSecretUnlocked,
                onShowLogs = { showLogs = true },
                onBurst = {
                    burst++
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onStatusTap = {
                    val now = System.currentTimeMillis()
                    statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                    statusLastTap = now
                    if (statusTaps >= 3) {
                        statusTaps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        laser++
                    }
                },
            )
        } else if (design == AppDesign.Pixel) {
            PixelHome(
                ui = ui,
                info = info,
                infoExpanded = infoExpanded,
                onOpenInfo = homeVm::openDetails,
                onCloseInfo = homeVm::closeDetails,
                onRefreshPing = homeVm::refreshPing,
                onRefreshPlace = homeVm::refreshPlace,
                onRefreshInfo = { homeVm.refresh(force = true) },
                activeConfigName = activeConfigName,
                activeConfigDetail = activeConfigDetail,
                activeLocked = activeLocked,
                lockedNote = lockedNote,
                localProxyAddress = localProxyAddress,
                showConnectionInfo = showConnectionInfo,
                reduceMotion = reduceMotion,
                contentPadding = contentPadding,
                onToggleConnection = onToggleConnection,
                onBrowseConfigs = onBrowseConfigs,
                onSecretUnlocked = onSecretUnlocked,
                onShowLogs = { showLogs = true },
                onBurst = {
                    burst++
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onStatusTap = {
                    val now = System.currentTimeMillis()
                    statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                    statusLastTap = now
                    if (statusTaps >= 3) {
                        statusTaps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        laser++
                    }
                },
            )
        } else {
            PortalHome(
                ui = ui,
                info = info,
                infoExpanded = infoExpanded,
                onToggleInfo = homeVm::toggleExpanded,
                onRefreshInfo = { homeVm.refresh(force = true) },
                activeConfigName = activeConfigName,
                activeConfigDetail = activeConfigDetail,
                activeLocked = activeLocked,
                lockedNote = lockedNote,
                localProxyAddress = localProxyAddress,
                showConnectionInfo = showConnectionInfo,
                reduceMotion = reduceMotion,
                contentPadding = contentPadding,
                onToggleConnection = onToggleConnection,
                onBrowseConfigs = onBrowseConfigs,
                onSecretUnlocked = onSecretUnlocked,
                onShowLogs = { showLogs = true },
                onBurst = {
                    burst++
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onStatusTap = {
                    val now = System.currentTimeMillis()
                    statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                    statusLastTap = now
                    if (statusTaps >= 3) {
                        statusTaps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        laser++
                    }
                },
            )
        }
        ParticleBurst(trigger = burst)
        com.amyrmo.damavand.ui.easteregg.LaserBounce(
            trigger = laser,
            color = MaterialTheme.colorScheme.primary,
            reduceMotion = reduceMotion,
        )
    }
    if (showLogs) LogSheet(onDismiss = { showLogs = false })
}

internal const val STAGE_ID = "home-stage"

private val STAGE_MIN = 148.dp

internal fun Modifier.scrollFade(state: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.canScrollForward || state.canScrollBackward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        val fade = length.toPx()
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private const val MAX_STAGE_PX = 16_384

@Composable
internal fun StageColumn(
    viewport: Dp,
    stageMin: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth

        val natural = Constraints(maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var stageIndex = -1
        var fixed = 0
        measurables.forEachIndexed { i, child ->
            if (child.layoutId == STAGE_ID) {
                stageIndex = i
            } else {
                placeables[i] = child.measure(natural).also { fixed += it.height }
            }
        }

        val viewportPx = if (viewport.isFinite) viewport.roundToPx().coerceIn(0, MAX_STAGE_PX) else 0
        val stagePx = if (stageIndex < 0) 0 else maxOf(stageMin.roundToPx(), viewportPx - fixed).coerceIn(0, MAX_STAGE_PX)
        if (stageIndex >= 0) {
            placeables[stageIndex] = measurables[stageIndex].measure(Constraints.fixed(width, stagePx))
        }
        layout(width, maxOf(viewportPx, fixed + stagePx)) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable == null) return@forEach
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

@Composable
internal fun FaultBanner(
    fault: com.amyrmo.damavand.core.ConnectionFault,
    connected: Boolean,
    onShowLogs: () -> Unit,
) {
    var expanded by remember(fault.raw) { mutableStateOf(false) }
    val known = fault.cause != com.amyrmo.damavand.core.ConnectionFault.Cause.Unknown
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(
                    if (connected) Res.string.fault_heading_health else Res.string.fault_heading_connect,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            if (known) {
                Text(
                    stringResource(faultTitle(fault.cause)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(faultHint(fault.cause)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (fault.raw.isNotBlank()) {
                Text(
                    fault.raw,
                    style = if (known) MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ) else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (known) 0.75f else 1f),

                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(Res.string.fault_open_logs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clickable(onClick = onShowLogs).padding(top = 2.dp),
            )
        }
    }
}

internal fun faultTitle(c: com.amyrmo.damavand.core.ConnectionFault.Cause) = when (c) {
    com.amyrmo.damavand.core.ConnectionFault.Cause.PortBusy -> Res.string.fault_port_busy
    com.amyrmo.damavand.core.ConnectionFault.Cause.Config -> Res.string.fault_config
    com.amyrmo.damavand.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout
    com.amyrmo.damavand.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed
    com.amyrmo.damavand.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused
    com.amyrmo.damavand.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset
    com.amyrmo.damavand.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable
    com.amyrmo.damavand.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns
    com.amyrmo.damavand.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls
    com.amyrmo.damavand.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate
    com.amyrmo.damavand.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality
    com.amyrmo.damavand.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth
    com.amyrmo.damavand.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport
    com.amyrmo.damavand.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

internal fun faultHint(c: com.amyrmo.damavand.core.ConnectionFault.Cause) = when (c) {
    com.amyrmo.damavand.core.ConnectionFault.Cause.PortBusy -> Res.string.fault_port_busy_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Config -> Res.string.fault_config_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport_hint
    com.amyrmo.damavand.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

private const val TAP_WINDOW_MS = 1_200L

internal fun rateFraction(bytesPerSecond: Long): Float {
    if (bytesPerSecond <= 0L) return 0f
    val kb = bytesPerSecond / 1024.0
    return (ln(1.0 + kb) / FULL_SCALE_LN).toFloat().coerceIn(0f, 1f)
}

private val FULL_SCALE_LN = ln(1.0 + 10 * 1024.0)

private class BurstParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float,
    val square: Boolean,
    var life: Float = 1f,
)

@Composable
internal fun ParticleBurst(trigger: Int) {
    val colors = listOf(ZedViolet, ZedHotPink, ZedLime, ZedCyan)
    val particles = remember { mutableStateListOf<BurstParticle>() }
    var tick by remember { mutableLongStateOf(0L) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        var guard = 0
        while (canvas == Size.Zero && guard < 5) {
            withInfiniteAnimationFrameNanos { }
            guard++
        }
        if (canvas == Size.Zero) return@LaunchedEffect
        val cx = canvas.width / 2f
        val cy = canvas.height * 0.42f
        repeat(56) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 8f + Random.nextFloat() * 20f
            particles += BurstParticle(
                x = cx,
                y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 8f,
                color = colors[Random.nextInt(colors.size)],
                radius = 7f + Random.nextFloat() * 11f,
                square = Random.nextBoolean(),
            )
        }
        var last = 0L
        while (particles.isNotEmpty()) {
            withInfiniteAnimationFrameNanos { now ->
                val step = if (last == 0L) 1f else ((now - last) / 16_666_666f).coerceIn(0f, 3f)
                last = now
                particles.forEach { p ->
                    p.vy += 0.9f * step
                    p.vx *= 1f - 0.02f * step
                    p.x += p.vx * step
                    p.y += p.vy * step
                    p.life -= 0.014f * step
                }
                particles.removeAll { it.life <= 0f || it.y > canvas.height + 60f }
                tick = now
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        tick
        particles.forEach { p ->
            val color = p.color.copy(alpha = p.life.coerceIn(0f, 1f))
            if (p.square) {
                drawRect(
                    color = color,
                    topLeft = Offset(p.x - p.radius / 2f, p.y - p.radius / 2f),
                    size = Size(p.radius, p.radius),
                )
            } else {
                drawCircle(color = color, radius = p.radius / 2f, center = Offset(p.x, p.y))
            }
        }
    }
}

internal fun ConnectionState.labelRes(): StringResource = when (this) {
    ConnectionState.Idle -> Res.string.state_idle
    ConnectionState.Connecting -> Res.string.state_connecting
    ConnectionState.Connected -> Res.string.state_connected
    ConnectionState.Reconnecting -> Res.string.state_reconnecting
    ConnectionState.Disconnecting -> Res.string.state_disconnecting
    ConnectionState.Error -> Res.string.state_error
}
