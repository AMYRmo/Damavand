package com.amyrmo.damavand.lite.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Global header (spec §3.1): mountain logo + «دماوند» + subtitle, status
 * pill at the end, hairline + mountain-contour signature at the bottom.
 * Same header on every tab.
 */
@Composable
fun LiteHeader(state: ConnState, onErrorPillClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiteColors.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lite),
                contentDescription = null,
                tint = LiteColors.text,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.lite_subtitle),
                    fontSize = 11.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn,
                    maxLines = 1
                )
            }
            StatusPill(state = state, onErrorPillClick = onErrorPillClick)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LiteColors.hairline)
        )
        MountainContourLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 20.dp),
            color = LiteColors.textMuted.copy(alpha = 0.4f)
        )
    }
}

/** Status pill (spec §3.2). Tappable only in the error state. */
@Composable
private fun StatusPill(state: ConnState, onErrorPillClick: () -> Unit) {
    val dotColor: Color
    @StringRes val labelRes: Int
    val labelColor: Color
    when (state) {
        ConnState.DISCONNECTED -> {
            dotColor = LiteColors.textMuted
            labelRes = R.string.status_disconnected
            labelColor = LiteColors.textSecondary
        }
        ConnState.CONNECTING -> {
            dotColor = LiteColors.amber
            labelRes = R.string.status_connecting
            labelColor = LiteColors.textSecondary
        }
        ConnState.CONNECTED -> {
            dotColor = LiteColors.accent
            labelRes = R.string.status_connected
            labelColor = LiteColors.text
        }
        ConnState.ERROR -> {
            dotColor = LiteColors.accent
            labelRes = R.string.status_error
            labelColor = LiteColors.text
        }
    }
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .background(LiteColors.surface, CircleShape)
            .border(1.dp, LiteColors.hairline, CircleShape)
            .then(
                if (state == ConnState.ERROR) Modifier.clickable(onClick = onErrorPillClick)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(labelRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}

/**
 * The mountain-contour signature (spec §0 / §3.1): one thin contour line.
 * The same polyline is reused behind the ring (scaled) and in the Servers
 * empty state. Points are fractions of the available width/height.
 */
@Composable
fun MountainContourLine(
    modifier: Modifier,
    color: Color,
    strokeWidth: Dp = 1.dp
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            contourPoints.forEachIndexed { i, (fx, fy) ->
                if (i == 0) moveTo(fx * w, fy * h) else lineTo(fx * w, fy * h)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** Contour ridgeline, as fractions (x, y) of the drawing area. */
val contourPoints: List<Pair<Float, Float>> = listOf(
    0.00f to 0.78f,
    0.08f to 0.60f,
    0.14f to 0.70f,
    0.22f to 0.38f,
    0.30f to 0.66f,
    0.38f to 0.48f,
    0.46f to 0.68f,
    0.54f to 0.30f,
    0.62f to 0.62f,
    0.70f to 0.42f,
    0.78f to 0.66f,
    0.86f to 0.52f,
    0.93f to 0.64f,
    1.00f to 0.58f
)
