/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * BentoConnectBar — the Bento connect controls, ported from ZedSecure
 * v3.1.8 (ui/bento/BentoConnectBar.kt; the Slide bar and the Button; the
 * Morph/Rail/Hold/Tile variants from BentoConnectVariants.kt are deferred).
 * Adaptations for Damavand v2:
 *  - Zed's ConnectionState/BentoPrefs become plain parameters: a
 *    [BentoConnectState] enum plus caller-supplied labels (the caller
 *    localises via v2's R); the accent is [BentoColors.lime], which in
 *    Damavand palettes is RED.
 *  - No material-icons in v2: the power glyph is drawn with Canvas.
 *  - DIRECTION-AWARE (Persian-crash audit): the slide knob is anchored
 *    with Alignment.CenterStart (start = right edge in RTL) and its
 *    physical offset is MIRRORED in RTL — travel is measured from the
 *    start edge toward the end edge, so in RTL the offset x is negated;
 *    the draggable uses reverseDirection in RTL and the wave track is
 *    canvas-mirrored, exactly so the knob, the filled track and the
 *    gesture all agree on which side is "on" in both directions.
 */
package com.amyrmo.damavand.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Plain connect-control state (v2's SessionState maps onto these four). */
enum class BentoConnectState { Idle, Connecting, Connected, Failed }

enum class BentoConnectVariant { Slide, Button }

private const val THRESHOLD = 0.72f

@Composable
fun BentoConnectControl(
    state: BentoConnectState,
    stateText: String,
    actionText: String,
    hintText: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BentoConnectVariant = BentoConnectVariant.Slide,
    animate: Boolean = true,
) {
    when (variant) {
        BentoConnectVariant.Slide ->
            BentoConnectBar(state, stateText, actionText, hintText, onToggle, animate, modifier)
        BentoConnectVariant.Button ->
            BentoConnectButton(state, stateText, actionText, onToggle, modifier)
    }
}

/** Power glyph drawn with Canvas (v2 carries no material-icons). */
@Composable
fun BentoPowerIcon(tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.10f
        val r = this.size.minDimension * 0.31f
        val cx = this.size.width / 2f
        val cy = this.size.height * 0.58f
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(cx, this.size.height * 0.10f),
            end = Offset(cx, cy - r * 0.45f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun BentoConnectBar(
    state: BentoConnectState,
    stateText: String,
    actionText: String,
    hintText: String,
    onToggle: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val busy = state == BentoConnectState.Connecting
    val engaged = busy || state == BentoConnectState.Connected
    val rest = if (engaged) 1f else 0f

    var position by remember { mutableFloatStateOf(rest) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float) {
        settle?.cancel()
        settle = scope.launch {
            animate(
                position,
                target,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            ) { v, _ -> position = v }
        }
    }

    LaunchedEffect(rest) { if (!dragging) settleTo(rest) }

    val dot by animateColorAsState(
        when (state) {
            BentoConnectState.Connected -> c.stage
            BentoConnectState.Failed -> c.bad
            BentoConnectState.Connecting -> c.stage.copy(alpha = 0.65f)
            BentoConnectState.Idle -> c.onLime.copy(alpha = 0.4f)
        },
        label = "connect-dot",
    )

    Column(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(BentoShapes.block())
            .background(c.lime)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = stateText
                onClick(actionText) {
                    onToggle()
                    true
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(
                stateText,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.onLime,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                hintText,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = c.onLime.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
            val density = LocalDensity.current
            val knob = 40.dp
            val range = with(density) { (maxWidth - knob).toPx().coerceAtLeast(1f) }
            val amp = with(density) { WAVE_AMP.toPx() }
            val wavelength = with(density) { waveLength(range, WAVE_LENGTH.toPx()) }
            val draggableState = rememberDraggableState { delta ->
                if (!busy) position = (position + delta / range).coerceIn(0f, 1f)
            }
            val phase = if (busy && animate) wavePhase() else null
            val tap = rememberUpdatedState {
                when {
                    busy -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                    else -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val nudge = if (engaged) rest - 0.14f else rest + 0.14f
                        settle?.cancel()
                        settle = scope.launch {
                            animate(position, nudge, animationSpec = tween(140)) { v, _ -> position = v }
                            animate(
                                position,
                                rest,
                                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                            ) { v, _ -> position = v }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap.value() }) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        enabled = !busy,
                        reverseDirection = rtl,
                        onDragStarted = {
                            settle?.cancel()
                            dragging = true
                        },
                        onDragStopped = {
                            dragging = false
                            val crossed = if (engaged) position < 1f - THRESHOLD else position > THRESHOLD
                            if (crossed) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                settleTo(1f - rest)
                                onToggle()
                            } else {
                                settleTo(rest)
                            }
                        },
                    ),
            ) {
                WaveTrack(
                    progress = { position },
                    color = c.onLime,
                    knob = with(density) { knob.toPx() },
                    amp = amp,
                    wavelength = wavelength,
                    rtl = rtl,
                    phase = phase,
                    connected = state == BentoConnectState.Connected,
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        // Direction-aware knob offset: CenterStart anchors
                        // at the start edge (right in RTL); travel toward
                        // the end edge is +x in LTR and −x in RTL.
                        .offset {
                            val travelled = (position * range).roundToInt()
                            IntOffset(if (rtl) -travelled else travelled, 0)
                        }
                        .graphicsLayer {
                            translationY = waveOffset(position * range, amp, wavelength)
                        }
                        .size(knob)
                        .clip(CircleShape)
                        .background(c.onLime),
                    contentAlignment = Alignment.Center,
                ) {
                    BentoPowerIcon(tint = c.lime, size = 20.dp)
                }
            }
        }
    }
}

private val WAVE_AMP = 4.5.dp
private val WAVE_LENGTH = 32.dp

internal fun waveLength(range: Float, preferred: Float): Float {
    val halves = (range / (preferred / 2f)).roundToInt().coerceAtLeast(1)
    return 2f * range / halves
}

internal fun waveOffset(distance: Float, amp: Float, wavelength: Float): Float =
    amp * sin(2f * PI.toFloat() * distance / wavelength)

@Composable
private fun wavePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "connect-wave")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "connect-wave-phase",
    )
}

@Composable
private fun WaveTrack(
    progress: () -> Float,
    color: Color,
    knob: Float,
    amp: Float,
    wavelength: Float,
    rtl: Boolean,
    phase: State<Float>?,
    connected: Boolean,
) {
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val width = size.width
        val mid = size.height / 2f
        val stroke = 4.5.dp.toPx()
        val start = knob / 2f
        val end = width - knob / 2f
        val knobCenter = start + p * (width - knob)
        fun build(from: Float, to: Float) {
            path.reset()
            var x = from
            path.moveTo(x, mid + waveOffset(x - start, amp, wavelength))
            while (x < to) {
                x = (x + 2f).coerceAtMost(to)
                path.lineTo(x, mid + waveOffset(x - start, amp, wavelength))
            }
        }
        // Mirrored in RTL so the filled portion always trails the knob
        // from the start edge, matching the knob's mirrored offset.
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(width / 2f, mid)) {
            if (knobCenter < end - 1f) {
                build(knobCenter, end)
                val dash = phase?.value
                drawPath(
                    path,
                    color.copy(alpha = if (dash != null) 0.9f else 0.3f),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = dash?.let {
                            PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()), -it * 19.dp.toPx())
                        },
                    ),
                )
            }
            if (knobCenter > start + 1f) {
                build(start, knobCenter)
                drawPath(
                    path,
                    color.copy(alpha = if (connected) 1f else 0.85f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
fun BentoConnectButton(
    state: BentoConnectState,
    stateText: String,
    actionText: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val haptics = LocalHapticFeedback.current
    val connected = state == BentoConnectState.Connected
    val busy = state == BentoConnectState.Connecting
    val bg by animateColorAsState(if (connected) c.stage else c.lime, label = "connect-btn-bg")
    val fg by animateColorAsState(if (connected) c.onStage else c.onLime, label = "connect-btn-fg")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.6f),
        label = "connect-btn-squash",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(BentoShapes.block())
            .background(bg)
            .semantics { stateDescription = stateText }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = actionText,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(fg),
            contentAlignment = Alignment.Center,
        ) {
            BentoPowerIcon(tint = bg, size = 24.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                actionText,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stateText,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        }
    }
}
