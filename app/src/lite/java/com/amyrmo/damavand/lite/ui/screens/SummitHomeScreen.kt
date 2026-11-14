package com.amyrmo.damavand.lite.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.BoltIcon
import com.amyrmo.damavand.lite.ui.components.ChevronForwardIcon
import com.amyrmo.damavand.lite.ui.components.FlagBadge
import com.amyrmo.damavand.lite.ui.components.PowerLineIcon
import com.amyrmo.damavand.lite.ui.components.contourPoints
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import androidx.compose.ui.text.TextStyle

/**
 * Home tab (spec §4), fixed order: ring → caption → stats row →
 * server card → fastest button. The whole column scrolls on small phones.
 */
@Composable
fun SummitHomeScreen(
    vm: LiteViewModel,
    connection: ConnectionUiState,
    profiles: List<Profile>,
    selectedId: String?,
    onConnectRequest: () -> Unit,
    onGoToServers: () -> Unit,
    onGoToSettingsGeo: () -> Unit
) {
    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val fastest = vm.fastestProfile
    var showAddSub by remember { mutableStateOf(false) }

    // One entrance on load: short fade + rise (≤ 8px), no stagger.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(300), label = "home-enter-alpha"
    )
    val enterOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 8f,
        animationSpec = tween(300), label = "home-enter-offset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                alpha = enterAlpha
                translationY = enterOffset
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        SummitRing(
            vm = vm,
            connection = connection,
            profile = profile,
            onConnectRequest = onConnectRequest
        )
        Spacer(Modifier.height(16.dp))
        UnderRingCaption(connection = connection, profile = profile)
        Spacer(Modifier.height(24.dp))
        StatsRow(connection = connection)
        Spacer(Modifier.height(24.dp))
        ServerCard(
            profile = profile,
            isFastest = profile != null && fastest?.id == profile.id,
            onClick = onGoToServers,
            onAddSubscription = { showAddSub = true }
        )
        Spacer(Modifier.height(16.dp))
        FastestButton(
            connection = connection,
            onConnectRequest = onConnectRequest,
            onDisconnect = { vm.disconnect() }
        )
        if (connection.state == ConnState.ERROR) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = connection.detail.ifBlank { stringResource(R.string.err_generic) },
                fontSize = 12.sp,
                color = LiteColors.error,
                fontFamily = Vazirmatn,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAddSub) {
        AddSubscriptionDialog(
            vm = vm,
            onDismiss = { showAddSub = false }
        )
    }
}

// ---- Ring (§4.1) ----

@Composable
private fun SummitRing(
    vm: LiteViewModel,
    connection: ConnectionUiState,
    profile: Profile?,
    onConnectRequest: () -> Unit
) {
    val state = connection.state
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    // Connecting progress: timed 0 → 100% over ~1.7 s, ease-out.
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == ConnState.CONNECTING) {
            progressAnim.snapTo(0f)
            if (reduceMotion) progressAnim.snapTo(1f)
            else progressAnim.animateTo(
                1f,
                animationSpec = tween(1700, easing = FastOutSlowInEasing)
            )
        } else {
            progressAnim.snapTo(0f)
        }
    }
    val connectProgress = progressAnim.value

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120), label = "ring-press"
    )

    Box(
        modifier = Modifier.size(288.dp),
        contentAlignment = Alignment.Center
    ) {
        // Track, progress arc and the five contour lines behind the button.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val trackRadius = 124.dp.toPx()
            // Five contour ridgelines, same asset scaled, 30–40% opacity.
            val scales = listOf(0.52f to 0.30f, 0.64f to 0.32f, 0.76f to 0.35f, 0.88f to 0.38f, 1.0f to 0.40f)
            for ((s, a) in scales) {
                val path = Path().apply {
                    contourPoints.forEachIndexed { i, (fx, fy) ->
                        val x = cx + (fx * size.width - cx) * s
                        val y = cy + (fy * size.height - cy) * s
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = LiteColors.textMuted.copy(alpha = a),
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // Track: full circle, white 7%.
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = trackRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )
            // Progress arc: amber while connecting, red when connected.
            val sweep = when (state) {
                ConnState.CONNECTING -> connectProgress * 360f
                ConnState.CONNECTED -> 360f
                else -> 0f
            }
            if (sweep > 0f) {
                drawArc(
                    color = if (state == ConnState.CONNECTING) LiteColors.amber else LiteColors.accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - trackRadius, cy - trackRadius),
                    size = androidx.compose.ui.geometry.Size(trackRadius * 2f, trackRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Glass button (260dp), press scale 0.97.
        val ringClickable = state != ConnState.CONNECTING
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .background(LiteColors.glass, CircleShape)
                .border(1.dp, LiteColors.hairline, CircleShape)
                .then(
                    if (ringClickable) {
                        Modifier.pointerInput(state) {
                            detectTapGestures(
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (state == ConnState.CONNECTED) vm.disconnect()
                                    else onConnectRequest()
                                }
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                ConnState.DISCONNECTED, ConnState.ERROR -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        PowerLineIcon(size = 40.dp, color = LiteColors.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.ring_tap_to_connect),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ring_auto_fastest),
                            fontSize = 11.sp,
                            color = LiteColors.textMuted,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                ConnState.CONNECTING -> {
                    val percent = (connectProgress * 100).toInt().coerceIn(0, 100)
                    val stepRes = when {
                        percent <= 33 -> R.string.ring_step_keys
                        percent <= 66 -> R.string.ring_step_route
                        else -> R.string.ring_step_finish
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = LiteFormat.toPersian(percent.toString()),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            Text(
                                text = "٪",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(stepRes),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                }
                ConnState.CONNECTED -> {
                    val ping = connection.pingMs
                    val location = connection.location.ifNotBlank()
                        ?: profileLocation(profile)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (ping > 0) LiteFormat.toPersian(ping.toString()) else "—",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            if (ping > 0) {
                                Text(
                                    text = "ms",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LiteColors.textSecondary,
                                    fontFamily = Vazirmatn,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.status_connected) +
                                (profile?.let { " — ${it.name}" } ?: ""),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (location.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = location,
                                fontSize = 11.sp,
                                color = LiteColors.textMuted,
                                fontFamily = Vazirmatn,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun profileLocation(profile: Profile?): String {
    if (profile == null) return ""
    return listOf(profile.city, profile.country)
        .filter { it.isNotBlank() }
        .joinToString("، ")
}

// ---- Under-ring caption (§4.2) ----

@Composable
private fun UnderRingCaption(connection: ConnectionUiState, profile: Profile?) {
    val title: String
    val titleColor: Color
    val body: String
    when (connection.state) {
        ConnState.DISCONNECTED -> {
            title = stringResource(R.string.caption_idle_title)
            titleColor = LiteColors.text
            body = stringResource(R.string.caption_idle_body)
        }
        ConnState.CONNECTING -> {
            title = stringResource(R.string.caption_connecting_title)
            titleColor = LiteColors.text
            body = stringResource(R.string.caption_connecting_body)
        }
        ConnState.CONNECTED -> {
            title = stringResource(R.string.caption_connected_title)
            titleColor = LiteColors.text
            body = stringResource(R.string.caption_connected_body)
        }
        ConnState.ERROR -> {
            title = stringResource(R.string.caption_error_title)
            titleColor = LiteColors.error
            body = connection.detail.ifBlank { stringResource(R.string.err_generic) }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            fontFamily = Vazirmatn,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            fontSize = 12.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// ---- Stats row (§4.3) — always visible ----

@Composable
private fun StatsRow(connection: ConnectionUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(
            labelRes = R.string.stat_ping,
            value = if (connection.pingMs > 0) LiteFormat.toPersian(connection.pingMs.toString()) else "—",
            unit = if (connection.pingMs > 0) "ms" else null,
            valueMuted = connection.pingMs <= 0,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        val down = splitValueUnit(LiteFormat.formatSpeed(connection.downSpeed))
        StatColumn(
            labelRes = R.string.stat_download,
            value = down.first,
            unit = down.second,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        val up = splitValueUnit(LiteFormat.formatSpeed(connection.upSpeed))
        StatColumn(
            labelRes = R.string.stat_upload,
            value = up.first,
            unit = up.second,
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        val vol = splitValueUnit(LiteFormat.formatVolume(connection.totalDown + connection.totalUp))
        StatColumn(
            labelRes = R.string.stat_session,
            value = vol.first,
            unit = vol.second,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Splits a formatted "۱۲٫۴ مگابیت" string into value + unit. */
private fun splitValueUnit(formatted: String): Pair<String, String> {
    val idx = formatted.lastIndexOf(' ')
    return if (idx > 0) formatted.substring(0, idx) to formatted.substring(idx + 1)
    else formatted to ""
}

@Composable
private fun StatColumn(
    labelRes: Int,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueMuted: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 11.sp,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (valueMuted) LiteColors.textMuted else LiteColors.text,
                fontFamily = Vazirmatn,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1
            )
            if (!unit.isNullOrBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(bottom = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(LiteColors.hairline)
    )
}

// ---- Server card (§4.4) ----

@Composable
private fun ServerCard(
    profile: Profile?,
    isFastest: Boolean,
    onClick: () -> Unit,
    onAddSubscription: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (profile == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_empty_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_empty_body),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                    ChevronForwardIcon(size = 16.dp, color = LiteColors.textMuted)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlagBadge(
                        countryCode = profile.countryCode.ifBlank { null },
                        size = 44.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isFastest) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.badge_fastest),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LiteColors.accent,
                                    fontFamily = Vazirmatn
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val location = profileLocation(profile)
                            if (location.isNotBlank()) {
                                Text(
                                    text = location,
                                    fontSize = 12.sp,
                                    color = LiteColors.textSecondary,
                                    fontFamily = Vazirmatn,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            CoreTag(core = profile.core)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    PingChip(profile = profile)
                    Spacer(Modifier.width(8.dp))
                    ChevronForwardIcon(size = 16.dp, color = LiteColors.textMuted)
                }
            }
        }
        if (profile == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.servers_add_subscription),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(onClick = onAddSubscription)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

/** Core tag pill: surface-2 background, radius 10, 11sp/600 secondary. */
@Composable
fun CoreTag(core: String) {
    Box(
        modifier = Modifier
            .background(LiteColors.surface2, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = core,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}

/** Ping chip: green < 150, secondary 150–300, error > 300 or failed. */
@Composable
fun PingChip(profile: Profile) {
    val (text, color) = when {
        profile.lastTestedAt == 0L -> "—" to LiteColors.textMuted
        profile.lastPingMs < 0 -> stringResource(R.string.ping_failed) to LiteColors.error
        profile.lastPingMs < 150 -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.green
        profile.lastPingMs <= 300 -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.textSecondary
        else -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.error
    }
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        fontFamily = Vazirmatn,
        style = TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1
    )
}

// ---- Fastest button (§4.5) ----

@Composable
private fun FastestButton(
    connection: ConnectionUiState,
    onConnectRequest: () -> Unit,
    onDisconnect: () -> Unit
) {
    val state = connection.state
    val enabled = state != ConnState.CONNECTING
    val labelRes = when (state) {
        ConnState.CONNECTED -> R.string.btn_disconnect
        ConnState.CONNECTING -> R.string.btn_connecting
        else -> R.string.btn_fastest
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(120), label = "fastest-press"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .alpha(if (enabled) 1f else 0.5f)
            .background(Color.Transparent, CircleShape)
            .border(1.5.dp, LiteColors.hairlineStrong, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (state == ConnState.CONNECTED) onDisconnect()
                    else onConnectRequest()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BoltIcon(size = 20.dp, color = LiteColors.text)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(labelRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1
            )
        }
    }
}
