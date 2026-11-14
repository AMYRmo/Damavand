package com.amyrmo.damavand.lite.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Bottom bar (spec §3.3): exactly 4 tabs, min-height 52px + safe-area
 * bottom, solid background, top hairline. Active = text colour only.
 * Servers tab carries a static 5px red live dot while connected.
 */
@Composable
fun LiteBottomBar(selected: Int, isConnected: Boolean, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiteColors.bottomBar)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LiteColors.hairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
        ) {
            BottomTab(
                labelRes = R.string.tab_home,
                isSelected = selected == 0,
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f),
                icon = { color -> HomeLineIcon(size = 22.dp, color = color) }
            )
            BottomTab(
                labelRes = R.string.tab_servers,
                isSelected = selected == 1,
                onClick = { onSelect(1) },
                modifier = Modifier.weight(1f),
                showLiveDot = isConnected,
                icon = { color -> ServersLineIcon(size = 22.dp, color = color) }
            )
            BottomTab(
                labelRes = R.string.tab_stats,
                isSelected = selected == 2,
                onClick = { onSelect(2) },
                modifier = Modifier.weight(1f),
                icon = { color -> StatsLineIcon(size = 22.dp, color = color) }
            )
            BottomTab(
                labelRes = R.string.tab_settings,
                isSelected = selected == 3,
                onClick = { onSelect(3) },
                modifier = Modifier.weight(1f),
                icon = { color -> SettingsLineIcon(size = 22.dp, color = color) }
            )
        }
    }
}

@Composable
private fun BottomTab(
    labelRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLiveDot: Boolean = false,
    icon: @Composable (Color) -> Unit
) {
    val color = if (isSelected) LiteColors.text else LiteColors.textMuted
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.TopEnd) {
            icon(color)
            if (showLiveDot) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(LiteColors.accent, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ---- Line icons (24x24 viewport, 1.5 stroke, round caps/joins) ----
// Drawn with Canvas: the app has no material-icons dependency, and the
// spec wants one simple line set at 22px / 1.5 stroke.

/** Draws [draw] in a 24x24 coordinate space, scaled to [size]. */
@Composable
fun LineIconCanvas(
    size: Dp,
    color: Color,
    draw: DrawScope.() -> Unit
) {
    Canvas(modifier = Modifier.size(size)) {
        // The canvas is exactly `size` square; scale the 24-unit space to it.
        val s = this.size.width / 24f
        withTransform({ scale(s, s) }) {
            draw()
        }
    }
}

private fun DrawScope.strokePath(path: Path, color: Color, width: Float = 1.5f) {
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
fun HomeLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        strokePath(
            Path().apply {
                moveTo(3f, 11f)
                lineTo(12f, 3.5f)
                lineTo(21f, 11f)
                moveTo(5.5f, 9.5f)
                lineTo(5.5f, 20.5f)
                lineTo(18.5f, 20.5f)
                lineTo(18.5f, 9.5f)
            },
            color
        )
    }
}

@Composable
fun ServersLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        // Three stacked server units.
        for ((top, dotY) in listOf(3.5f to 6f, 9.75f to 12.25f, 16f to 18.5f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(4f, top),
                size = Size(16f, 5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(color = color, radius = 1f, center = Offset(7.5f, dotY))
        }
    }
}

@Composable
fun StatsLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        strokePath(
            Path().apply {
                moveTo(3f, 19f)
                lineTo(8f, 13f)
                lineTo(12.5f, 15.5f)
                lineTo(17f, 8f)
                lineTo(21f, 5.5f)
            },
            color
        )
    }
}

@Composable
fun SettingsLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        // Sliders: three horizontal lines with a knob each.
        val knobs = listOf(
            Triple(6f, 9f, 0),
            Triple(12f, 15f, 0),
            Triple(18f, 7f, 0)
        )
        for ((y, knobX, _) in knobs) {
            strokePath(
                Path().apply {
                    moveTo(4f, y)
                    lineTo(knobX - 3f, y)
                    moveTo(knobX + 3f, y)
                    lineTo(20f, y)
                },
                color
            )
            drawCircle(
                color = color,
                radius = 2.2f,
                center = Offset(knobX, y),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

/** Power symbol (ring content, disconnected state): arc + vertical line. */
@Composable
fun PowerLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        drawArc(
            color = color,
            startAngle = 120f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(5f, 6f),
            size = Size(14f, 14f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(12f, 2.5f),
            end = Offset(12f, 9.5f),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }
}

/** Bolt (fastest-server button): filled lightning. */
@Composable
fun BoltIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        drawPath(
            path = Path().apply {
                moveTo(13f, 2f)
                lineTo(5.5f, 13.5f)
                lineTo(10.5f, 13.5f)
                lineTo(9.5f, 22f)
                lineTo(18.5f, 10f)
                lineTo(13f, 10f)
                close()
            },
            color = color
        )
    }
}

/** Forward chevron (RTL-aware: points left in RTL, right in LTR). */
@Composable
fun ChevronForwardIcon(size: Dp, color: Color) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl
    LineIconCanvas(size = size, color = color) {
        strokePath(
            Path().apply {
                if (isRtl) {
                    moveTo(15f, 5f)
                    lineTo(8f, 12f)
                    lineTo(15f, 19f)
                } else {
                    moveTo(9f, 5f)
                    lineTo(16f, 12f)
                    lineTo(9f, 19f)
                }
            },
            color
        )
    }
}

/** Globe (flag fallback for unknown country): circle + meridian + equator. */
@Composable
fun GlobeLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        drawCircle(
            color = color,
            radius = 8.5f,
            center = Offset(12f, 12f),
            style = Stroke(width = 1.5f)
        )
        drawOval(
            color = color,
            topLeft = Offset(7.5f, 3.5f),
            size = Size(9f, 17f),
            style = Stroke(width = 1.5f)
        )
        drawLine(
            color = color,
            start = Offset(3.5f, 12f),
            end = Offset(20.5f, 12f),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }
}

/** Refresh (test-all button, optional icon): circular arrow. */
@Composable
fun RefreshLineIcon(size: Dp, color: Color) {
    LineIconCanvas(size = size, color = color) {
        drawArc(
            color = color,
            startAngle = 40f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(4f, 4f),
            size = Size(16f, 16f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
        // Arrowhead at the arc end (top, pointing right).
        drawPath(
            path = Path().apply {
                moveTo(17.5f, 3f)
                lineTo(21.5f, 3.5f)
                lineTo(19f, 7f)
            },
            color = color,
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
