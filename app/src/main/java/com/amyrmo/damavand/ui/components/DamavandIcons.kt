package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design system's icon set, drawn with Canvas primitives.
 *
 * Why hand-drawn: the version catalog deliberately carries no
 * material-icons dependency (the extended set costs megabytes for a
 * handful of glyphs), and the four navigation glyphs + row glyphs the kit
 * needs are simple geometry. All icons draw in a 24 dp box and take their
 * tint from the caller, like Material icons.
 */
enum class DamavandIconKind {
    Home,
    Servers,
    Chains,
    Settings,
    MoreVert,
    Plus,
    Edit,
    Trash,
    Share,
    Refresh,
    ChevronRight,
    ArrowUp,
    ArrowDown,
    Close,
    Check,
    Copy,
}

@Composable
fun DamavandIcon(
    kind: DamavandIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f // scale factor, design grid = 24
        fun x(v: Float) = v * s
        when (kind) {
            DamavandIconKind.Home -> {
                val path = Path().apply {
                    moveTo(x(12f), x(3.5f))
                    lineTo(x(21f), x(11.5f))
                    lineTo(x(18.5f), x(11.5f))
                    lineTo(x(18.5f), x(20.5f))
                    lineTo(x(14f), x(20.5f))
                    lineTo(x(14f), x(15f))
                    lineTo(x(10f), x(15f))
                    lineTo(x(10f), x(20.5f))
                    lineTo(x(5.5f), x(20.5f))
                    lineTo(x(5.5f), x(11.5f))
                    lineTo(x(3f), x(11.5f))
                    close()
                }
                drawPath(path, tint)
            }

            DamavandIconKind.Servers -> {
                // Two stacked server units with status dots.
                for (top in listOf(4f, 13.5f)) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(x(3.5f), x(top)),
                        size = Size(x(17f), x(6.5f)),
                        cornerRadius = CornerRadius(x(2f), x(2f)),
                        style = Stroke(width = x(1.7f)),
                    )
                    drawCircle(
                        color = tint,
                        radius = x(1.3f),
                        center = Offset(x(7f), x(top + 3.25f)),
                    )
                    drawLine(
                        color = tint,
                        start = Offset(x(11f), x(top + 3.25f)),
                        end = Offset(x(17.5f), x(top + 3.25f)),
                        strokeWidth = x(1.5f),
                        cap = StrokeCap.Round,
                    )
                }
            }

            DamavandIconKind.Chains -> {
                // Two interlocking links.
                drawCircle(
                    color = tint,
                    radius = x(5.2f),
                    center = Offset(x(8.2f), x(8.2f)),
                    style = Stroke(width = x(2f)),
                )
                drawCircle(
                    color = tint,
                    radius = x(5.2f),
                    center = Offset(x(15.8f), x(15.8f)),
                    style = Stroke(width = x(2f)),
                )
                drawLine(
                    color = tint,
                    start = Offset(x(10.8f), x(13.2f)),
                    end = Offset(x(13.2f), x(10.8f)),
                    strokeWidth = x(2f),
                    cap = StrokeCap.Round,
                )
            }

            DamavandIconKind.MoreVert -> {
                for (cy in listOf(5.5f, 12f, 18.5f)) {
                    drawCircle(color = tint, radius = x(1.7f), center = Offset(x(12f), x(cy)))
                }
            }

            DamavandIconKind.Plus -> {
                drawLine(tint, Offset(x(12f), x(5f)), Offset(x(12f), x(19f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(5f), x(12f)), Offset(x(19f), x(12f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
            }

            DamavandIconKind.Edit -> {
                // Pencil: a rotated body with a tip.
                val path = Path().apply {
                    moveTo(x(4f), x(20f))
                    lineTo(x(5.6f), x(15f))
                    lineTo(x(16.2f), x(4.4f))
                    lineTo(x(19.6f), x(7.8f))
                    lineTo(x(9f), x(18.4f))
                    close()
                }
                drawPath(path, tint, style = Stroke(width = x(1.8f)))
                drawLine(tint, Offset(x(14.2f), x(6.4f)), Offset(x(17.6f), x(9.8f)), strokeWidth = x(1.8f), cap = StrokeCap.Round)
            }

            DamavandIconKind.Trash -> {
                drawLine(tint, Offset(x(4.5f), x(6.5f)), Offset(x(19.5f), x(6.5f)), strokeWidth = x(2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(9.5f), x(6.5f)), Offset(x(9.5f), x(4.2f)), strokeWidth = x(2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(14.5f), x(6.5f)), Offset(x(14.5f), x(4.2f)), strokeWidth = x(2f), cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(x(6.2f), x(6.5f))
                    lineTo(x(7.2f), x(19.5f))
                    lineTo(x(16.8f), x(19.5f))
                    lineTo(x(17.8f), x(6.5f))
                }
                drawPath(path, tint, style = Stroke(width = x(1.8f)))
                drawLine(tint, Offset(x(10f), x(10f)), Offset(x(10.2f), x(16.5f)), strokeWidth = x(1.6f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(14f), x(10f)), Offset(x(13.8f), x(16.5f)), strokeWidth = x(1.6f), cap = StrokeCap.Round)
            }

            DamavandIconKind.Share -> {
                // Three nodes joined by lines.
                val a = Offset(x(6f), x(12f))
                val b = Offset(x(17.5f), x(5.5f))
                val c = Offset(x(17.5f), x(18.5f))
                drawLine(tint, a, b, strokeWidth = x(1.7f), cap = StrokeCap.Round)
                drawLine(tint, a, c, strokeWidth = x(1.7f), cap = StrokeCap.Round)
                for (p in listOf(a, b, c)) {
                    drawCircle(color = tint, radius = x(2.4f), center = p)
                }
            }

            DamavandIconKind.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(x(4.5f), x(4.5f)),
                    size = Size(x(15f), x(15f)),
                    style = Stroke(width = x(2f), cap = StrokeCap.Round),
                )
                val path = Path().apply {
                    moveTo(x(18.6f), x(2.8f))
                    lineTo(x(19.4f), x(8.2f))
                    lineTo(x(14.2f), x(7.2f))
                    close()
                }
                drawPath(path, tint)
            }

            DamavandIconKind.ChevronRight -> {
                val path = Path().apply {
                    moveTo(x(9f), x(4.5f))
                    lineTo(x(16.5f), x(12f))
                    lineTo(x(9f), x(19.5f))
                }
                drawPath(path, tint, style = Stroke(width = x(2.2f), cap = StrokeCap.Round))
            }

            DamavandIconKind.ArrowUp -> {
                drawLine(tint, Offset(x(12f), x(20f)), Offset(x(12f), x(5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(5.5f), x(11f)), Offset(x(12f), x(4.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(18.5f), x(11f)), Offset(x(12f), x(4.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
            }

            DamavandIconKind.ArrowDown -> {
                drawLine(tint, Offset(x(12f), x(4f)), Offset(x(12f), x(19f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(5.5f), x(13f)), Offset(x(12f), x(19.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(18.5f), x(13f)), Offset(x(12f), x(19.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
            }

            DamavandIconKind.Close -> {
                drawLine(tint, Offset(x(5.5f), x(5.5f)), Offset(x(18.5f), x(18.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
                drawLine(tint, Offset(x(18.5f), x(5.5f)), Offset(x(5.5f), x(18.5f)), strokeWidth = x(2.2f), cap = StrokeCap.Round)
            }

            DamavandIconKind.Check -> {
                val path = Path().apply {
                    moveTo(x(4.5f), x(12.5f))
                    lineTo(x(9.8f), x(17.8f))
                    lineTo(x(19.5f), x(6.5f))
                }
                drawPath(path, tint, style = Stroke(width = x(2.4f), cap = StrokeCap.Round))
            }

            DamavandIconKind.Copy -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(x(8.5f), x(8.5f)),
                    size = Size(x(11.5f), x(11.5f)),
                    cornerRadius = CornerRadius(x(2f), x(2f)),
                    style = Stroke(width = x(1.8f)),
                )
                val path = Path().apply {
                    moveTo(x(15.5f), x(8.5f))
                    lineTo(x(15.5f), x(5.2f))
                    lineTo(x(5.2f), x(5.2f))
                    lineTo(x(5.2f), x(15.5f))
                    lineTo(x(8.5f), x(15.5f))
                }
                drawPath(path, tint, style = Stroke(width = x(1.8f)))
            }

            DamavandIconKind.Settings -> {
                // Gear: outer ring, hub, six spokes.
                val center = Offset(x(12f), x(12f))
                drawCircle(
                    color = tint,
                    radius = x(6.4f),
                    center = center,
                    style = Stroke(width = x(2.6f)),
                )
                drawCircle(color = tint, radius = x(2.1f), center = center)
                for (i in 0 until 6) {
                    val angle = Math.toRadians(i * 60.0)
                    val inner = x(7.6f)
                    val outer = x(9.6f)
                    drawLine(
                        color = tint,
                        start = Offset(
                            center.x + (inner * Math.cos(angle)).toFloat(),
                            center.y + (inner * Math.sin(angle)).toFloat(),
                        ),
                        end = Offset(
                            center.x + (outer * Math.cos(angle)).toFloat(),
                            center.y + (outer * Math.sin(angle)).toFloat(),
                        ),
                        strokeWidth = x(2.2f),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
