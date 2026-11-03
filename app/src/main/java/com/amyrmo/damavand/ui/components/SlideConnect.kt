package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.ui.theme.Brand
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.Gradients
import com.amyrmo.damavand.ui.theme.StateColors
import kotlin.math.roundToInt

/**
 * The slide variant of the connect control (Appearance → Connect control,
 * §5.2): a thumb dragged across a capsule track; crossing 85 % of the
 * travel fires [onToggle]. Direction-aware: in RTL the thumb starts at
 * the right and travels left. When [state] is Connected the same gesture
 * disconnects. A disabled (Connecting) state ignores drags.
 */
@Composable
fun SlideConnect(
    state: ConnectPillState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: Int = 0,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    val thumbPx = with(density) { 64.dp.toPx() }
    val travel = (trackWidthPx - thumbPx).coerceAtLeast(0f)
    val progress = if (travel > 0f) (dragPx / travel).coerceIn(0f, 1f) else 0f

    val trackColors = when (state) {
        ConnectPillState.Connected -> Gradients.connectedForSession(sessionId)
        ConnectPillState.Failed -> listOf(StateColors.Error, StateColors.Error.copy(alpha = 0.8f))
        else -> listOf(Brand.Deep, Brand.Primary.copy(alpha = 0.75f))
    }
    val label = stringResource(
        when (state) {
            ConnectPillState.Connected -> R.string.home_slide_disconnect
            ConnectPillState.Connecting -> R.string.connecting
            ConnectPillState.Failed -> R.string.connection_failed
            ConnectPillState.Idle -> R.string.home_slide_connect
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PillHeight)
            .clip(DamavandShapeTokens.Capsule)
            .background(Brush.horizontalGradient(trackColors))
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 1f - progress * 0.7f),
            modifier = Modifier.align(Alignment.Center),
        )
        if (state != ConnectPillState.Connecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .offset {
                            val x = if (rtl) travel - dragPx else dragPx
                            IntOffset(x.roundToInt(), 0)
                        }
                        .clip(CircleShape)
                        .background(Color.White)
                        .pointerInput(state, rtl, travel) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (progress >= 0.85f) onToggle()
                                    dragPx = 0f
                                },
                                onDragCancel = { dragPx = 0f },
                            ) { _, dragAmount ->
                                val delta = if (rtl) -dragAmount else dragAmount
                                dragPx = (dragPx + delta).coerceIn(0f, travel)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DamavandIcon(
                        kind = DamavandIconKind.ChevronRight,
                        tint = Brand.Primary,
                        size = 26.dp,
                        modifier = Modifier.graphicsLayer { if (rtl) scaleX = -1f },
                    )
                }
            }
        }
    }
}
