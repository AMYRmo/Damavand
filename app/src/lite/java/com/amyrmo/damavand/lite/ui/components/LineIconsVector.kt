package com.amyrmo.damavand.lite.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The line-icon set as framework [ImageVector]s (3.1.2, full redesign).
 *
 * The hand-rolled Canvas icons (LineIconCanvas + withTransform scale)
 * rendered off-center on-device in every release up to 3.1.1 — the
 * glyphs sat at the top-left of their buttons despite Box centering
 * and despite a by-construction full-size Canvas (CircleIconButton).
 * These vectors are rendered by the framework's own VectorPainter
 * through Material [Icon], so placement is the framework's job, not
 * ours: a path centered at (12, 12) in the 24-unit viewport renders
 * centered, period. The geometry below is a faithful 1:1 port of the
 * old Canvas paths (same 24×24 viewport, same 1.5 stroke).
 *
 * Colors are baked as black placeholders: call sites tint through
 * [androidx.compose.material3.Icon]'s `tint`, which replaces every
 * color in the vector.
 */

// ---- node helpers (24-unit space) ----

private fun circleNodes(cx: Float, cy: Float, r: Float): List<PathNode> = listOf(
    PathNode.MoveTo(cx - r, cy),
    PathNode.ArcTo(r, r, 0f, false, true, cx + r, cy),
    PathNode.ArcTo(r, r, 0f, false, true, cx - r, cy),
    PathNode.Close
)

private fun ellipseNodes(cx: Float, cy: Float, rx: Float, ry: Float): List<PathNode> = listOf(
    PathNode.MoveTo(cx - rx, cy),
    PathNode.ArcTo(rx, ry, 0f, false, true, cx + rx, cy),
    PathNode.ArcTo(rx, ry, 0f, false, true, cx - rx, cy),
    PathNode.Close
)

/** A circular arc: [startDeg] → [startDeg] + [sweepDeg], clockwise. */
private fun arcNodes(
    cx: Float,
    cy: Float,
    r: Float,
    startDeg: Float,
    sweepDeg: Float
): List<PathNode> {
    val a0 = Math.toRadians(startDeg.toDouble())
    val a1 = Math.toRadians((startDeg + sweepDeg).toDouble())
    val sx = (cx + r * cos(a0)).toFloat()
    val sy = (cy + r * sin(a0)).toFloat()
    val ex = (cx + r * cos(a1)).toFloat()
    val ey = (cy + r * sin(a1)).toFloat()
    return listOf(
        PathNode.MoveTo(sx, sy),
        PathNode.ArcTo(r, r, 0f, sweepDeg > 180f, sweepDeg > 0f, ex, ey)
    )
}

private fun roundRectNodes(x: Float, y: Float, w: Float, h: Float, r: Float): List<PathNode> =
    listOf(
        PathNode.MoveTo(x + r, y),
        PathNode.LineTo(x + w - r, y),
        PathNode.ArcTo(r, r, 0f, false, true, x + w, y + r),
        PathNode.LineTo(x + w, y + h - r),
        PathNode.ArcTo(r, r, 0f, false, true, x + w - r, y + h),
        PathNode.LineTo(x + r, y + h),
        PathNode.ArcTo(r, r, 0f, false, true, x, y + h - r),
        PathNode.LineTo(x, y + r),
        PathNode.ArcTo(r, r, 0f, false, true, x + r, y),
        PathNode.Close
    )

/** Rotate every node endpoint [degrees] about ([px], [py]). Arcs here
 *  are circular, so radii and flags survive the rotation unchanged. */
private fun rotateNodes(
    nodes: List<PathNode>,
    px: Float,
    py: Float,
    degrees: Float
): List<PathNode> {
    val rad = Math.toRadians(degrees.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    fun rot(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - px
        val dy = y - py
        return (px + dx * c - dy * s) to (py + dx * s + dy * c)
    }
    return nodes.map { node ->
        when (node) {
            is PathNode.MoveTo -> {
                val (x, y) = rot(node.x, node.y)
                PathNode.MoveTo(x, y)
            }
            is PathNode.LineTo -> {
                val (x, y) = rot(node.x, node.y)
                PathNode.LineTo(x, y)
            }
            is PathNode.ArcTo -> {
                val (x, y) = rot(node.arcStartX, node.arcStartY)
                PathNode.ArcTo(
                    node.horizontalEllipseRadius,
                    node.verticalEllipseRadius,
                    node.theta,
                    node.isMoreThanHalf,
                    node.isPositiveArc,
                    x,
                    y
                )
            }
            else -> node
        }
    }
}

// ---- builder helpers ----

private fun buildIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> Unit
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply(block).build()

private fun ImageVector.Builder.strokePath(nodes: List<PathNode>) = addPath(
    pathData = nodes,
    pathFillType = PathFillType.NonZero,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    strokeLineWidth = 1.5f,
    stroke = SolidColor(Color.Black)
)

private fun ImageVector.Builder.fillPath(nodes: List<PathNode>) = addPath(
    pathData = nodes,
    pathFillType = PathFillType.NonZero,
    fill = SolidColor(Color.Black)
)

// ---- the set (same geometry as the old Canvas icons) ----

/** Home: roof + walls. */
val HomeVector: ImageVector = buildIcon("home") {
    strokePath(
        listOf(
            PathNode.MoveTo(3f, 11f),
            PathNode.LineTo(12f, 3.5f),
            PathNode.LineTo(21f, 11f),
            PathNode.MoveTo(5.5f, 9.5f),
            PathNode.LineTo(5.5f, 20.5f),
            PathNode.LineTo(18.5f, 20.5f),
            PathNode.LineTo(18.5f, 9.5f)
        )
    )
}

/** Servers: three stacked units, each with a status dot. */
val ServersVector: ImageVector = buildIcon("servers") {
    for ((top, dotY) in listOf(3.5f to 6f, 9.75f to 12.25f, 16f to 18.5f)) {
        strokePath(roundRectNodes(4f, top, 16f, 5f, 2f))
        fillPath(circleNodes(7.5f, dotY, 1f))
    }
}

/** Stats: rising polyline. */
val StatsVector: ImageVector = buildIcon("stats") {
    strokePath(
        listOf(
            PathNode.MoveTo(3f, 19f),
            PathNode.LineTo(8f, 13f),
            PathNode.LineTo(12.5f, 15.5f),
            PathNode.LineTo(17f, 8f),
            PathNode.LineTo(21f, 5.5f)
        )
    )
}

/** Settings: three slider lines, each with a knob. */
val SettingsVector: ImageVector = buildIcon("settings") {
    for ((y, knobX) in listOf(6f to 9f, 12f to 15f, 18f to 7f)) {
        strokePath(
            listOf(
                PathNode.MoveTo(4f, y),
                PathNode.LineTo(knobX - 3f, y),
                PathNode.MoveTo(knobX + 3f, y),
                PathNode.LineTo(20f, y)
            )
        )
        strokePath(circleNodes(knobX, y, 2.2f))
    }
}

/** Power: ring arc + vertical line. */
val PowerVector: ImageVector = buildIcon("power") {
    strokePath(arcNodes(12f, 13f, 7f, 120f, 300f))
    strokePath(listOf(PathNode.MoveTo(12f, 2.5f), PathNode.LineTo(12f, 9.5f)))
}

/** Bolt: filled lightning. */
val BoltVector: ImageVector = buildIcon("bolt") {
    fillPath(
        listOf(
            PathNode.MoveTo(13f, 2f),
            PathNode.LineTo(5.5f, 13.5f),
            PathNode.LineTo(10.5f, 13.5f),
            PathNode.LineTo(9.5f, 22f),
            PathNode.LineTo(18.5f, 10f),
            PathNode.LineTo(13f, 10f),
            PathNode.Close
        )
    )
}

/** Info: circle with an "i" (dot + stem). */
val InfoVector: ImageVector = buildIcon("info") {
    strokePath(circleNodes(12f, 12f, 9f))
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 11f),
            PathNode.LineTo(12f, 17f)
        )
    )
    fillPath(circleNodes(12f, 7.5f, 1f))
}

/** Forward chevron (auto-mirrored in RTL: points left there). */
val ChevronForwardVector: ImageVector = buildIcon("chevron_forward", autoMirror = true) {
    strokePath(
        listOf(
            PathNode.MoveTo(9f, 5f),
            PathNode.LineTo(16f, 12f),
            PathNode.LineTo(9f, 19f)
        )
    )
}

/** Globe: circle + meridian + equator. */
val GlobeVector: ImageVector = buildIcon("globe") {
    strokePath(circleNodes(12f, 12f, 8.5f))
    strokePath(ellipseNodes(12f, 12f, 4.5f, 8.5f))
    strokePath(listOf(PathNode.MoveTo(3.5f, 12f), PathNode.LineTo(20.5f, 12f)))
}

/** Refresh: circular arrow (270° arc + arrowhead). */
val RefreshVector: ImageVector = buildIcon("refresh") {
    strokePath(arcNodes(12f, 12f, 8f, 40f, 270f))
    strokePath(
        listOf(
            PathNode.MoveTo(17.5f, 3f),
            PathNode.LineTo(21.5f, 3.5f),
            PathNode.LineTo(19f, 7f)
        )
    )
}

/** Plus: vertical + horizontal line. */
val PlusVector: ImageVector = buildIcon("plus") {
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 4.5f),
            PathNode.LineTo(12f, 19.5f),
            PathNode.MoveTo(4.5f, 12f),
            PathNode.LineTo(19.5f, 12f)
        )
    )
}

/** Trash: lid, body and inner lines. */
val TrashVector: ImageVector = buildIcon("trash") {
    strokePath(
        listOf(
            PathNode.MoveTo(4f, 7f),
            PathNode.LineTo(20f, 7f),
            PathNode.MoveTo(9f, 7f),
            PathNode.LineTo(9f, 6f),
            PathNode.LineTo(15f, 6f),
            PathNode.LineTo(15f, 7f),
            PathNode.MoveTo(6.5f, 7f),
            PathNode.LineTo(7.5f, 20f),
            PathNode.LineTo(16.5f, 20f),
            PathNode.LineTo(17.5f, 7f),
            PathNode.MoveTo(10.5f, 11f),
            PathNode.LineTo(10.5f, 16.5f),
            PathNode.MoveTo(13.5f, 11f),
            PathNode.LineTo(13.5f, 16.5f)
        )
    )
}

/** Chain link: two interlocked rounded links. */
val ChainVector: ImageVector = buildIcon("chain") {
    strokePath(rotateNodes(roundRectNodes(5f, 9f, 8f, 6f, 3f), 9f, 12f, -45f))
    strokePath(rotateNodes(roundRectNodes(11f, 9f, 8f, 6f, 3f), 15f, 12f, 45f))
}

/** Search: circle + handle. */
val SearchVector: ImageVector = buildIcon("search") {
    strokePath(circleNodes(10.5f, 10.5f, 6.5f))
    strokePath(listOf(PathNode.MoveTo(15.5f, 15.5f), PathNode.LineTo(20.5f, 20.5f)))
}

/** Vertical ellipsis: three dots. */
val DotsVerticalVector: ImageVector = buildIcon("dots_vertical") {
    fillPath(circleNodes(12f, 5f, 1.4f))
    fillPath(circleNodes(12f, 12f, 1.4f))
    fillPath(circleNodes(12f, 19f, 1.4f))
}

/** Share: up arrow out of a tray. */
val ShareVector: ImageVector = buildIcon("share") {
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 15f),
            PathNode.LineTo(12f, 3f),
            PathNode.MoveTo(7.5f, 7.5f),
            PathNode.LineTo(12f, 3f),
            PathNode.LineTo(16.5f, 7.5f),
            PathNode.MoveTo(5f, 12f),
            PathNode.LineTo(5f, 20f),
            PathNode.LineTo(19f, 20f),
            PathNode.LineTo(19f, 12f)
        )
    )
}

/** Pencil (edit). */
val EditVector: ImageVector = buildIcon("edit") {
    strokePath(
        listOf(
            PathNode.MoveTo(4f, 20f),
            PathNode.LineTo(5f, 16f),
            PathNode.LineTo(16f, 5f),
            PathNode.LineTo(19f, 8f),
            PathNode.LineTo(8f, 19f),
            PathNode.Close,
            PathNode.MoveTo(13.5f, 7.5f),
            PathNode.LineTo(16.5f, 10.5f)
        )
    )
}

/** Copy: two overlapping rectangles. */
val CopyVector: ImageVector = buildIcon("copy") {
    strokePath(roundRectNodes(9f, 9f, 11f, 11f, 2f))
    strokePath(
        listOf(
            PathNode.MoveTo(15f, 9f),
            PathNode.LineTo(15f, 5f),
            PathNode.LineTo(5f, 5f),
            PathNode.LineTo(5f, 15f),
            PathNode.LineTo(9f, 15f)
        )
    )
}

/** Download: down arrow into a tray. */
val DownloadVector: ImageVector = buildIcon("download") {
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 3f),
            PathNode.LineTo(12f, 15f),
            PathNode.MoveTo(7f, 10f),
            PathNode.LineTo(12f, 15f),
            PathNode.LineTo(17f, 10f),
            PathNode.MoveTo(4f, 20f),
            PathNode.LineTo(20f, 20f)
        )
    )
}

/** Upload: up arrow out of a tray. */
val UploadVector: ImageVector = buildIcon("upload") {
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 15f),
            PathNode.LineTo(12f, 3f),
            PathNode.MoveTo(7f, 8f),
            PathNode.LineTo(12f, 3f),
            PathNode.LineTo(17f, 8f),
            PathNode.MoveTo(4f, 20f),
            PathNode.LineTo(20f, 20f)
        )
    )
}

/** Check mark. */
val CheckVector: ImageVector = buildIcon("check") {
    strokePath(
        listOf(
            PathNode.MoveTo(4.5f, 12.5f),
            PathNode.LineTo(9.5f, 17.5f),
            PathNode.LineTo(19.5f, 6.5f)
        )
    )
}

/** Down chevron (collapsible groups). */
val ChevronDownVector: ImageVector = buildIcon("chevron_down") {
    strokePath(
        listOf(
            PathNode.MoveTo(6f, 9f),
            PathNode.LineTo(12f, 15f),
            PathNode.LineTo(18f, 9f)
        )
    )
}

/** Sort: three lines, long to short. */
val SortVector: ImageVector = buildIcon("sort") {
    strokePath(
        listOf(
            PathNode.MoveTo(4f, 6f),
            PathNode.LineTo(20f, 6f),
            PathNode.MoveTo(4f, 12f),
            PathNode.LineTo(16f, 12f),
            PathNode.MoveTo(4f, 18f),
            PathNode.LineTo(12f, 18f)
        )
    )
}

/** List (logs): three lines with leading dots. */
val ListVector: ImageVector = buildIcon("list") {
    for (y in listOf(6f, 12f, 18f)) {
        fillPath(circleNodes(5f, y, 1.2f))
        strokePath(listOf(PathNode.MoveTo(9f, y), PathNode.LineTo(20f, y)))
    }
}

/** Phone (per-app proxy): rounded handset outline. */
val PhoneVector: ImageVector = buildIcon("phone") {
    strokePath(roundRectNodes(7f, 2.5f, 10f, 19f, 2.5f))
    strokePath(listOf(PathNode.MoveTo(10.5f, 18.5f), PathNode.LineTo(13.5f, 18.5f)))
}

/** Play: a right-pointing triangle (3.2.0, balancer connect). */
val PlayVector: ImageVector = buildIcon("play") {
    fillPath(
        listOf(
            PathNode.MoveTo(8f, 5f),
            PathNode.LineTo(19f, 12f),
            PathNode.LineTo(8f, 19f),
            PathNode.Close
        )
    )
}

/** Balancer: a scale — beam, post, two pans (3.2.0, policy groups). */
val BalancerVector: ImageVector = buildIcon("balancer") {
    strokePath(
        listOf(
            PathNode.MoveTo(12f, 4f),
            PathNode.LineTo(12f, 20f),
            PathNode.MoveTo(4f, 7f),
            PathNode.LineTo(20f, 7f),
            PathNode.MoveTo(4f, 7f),
            PathNode.LineTo(7f, 13f),
            PathNode.MoveTo(20f, 7f),
            PathNode.LineTo(17f, 13f)
        )
    )
    strokePath(
        listOf(
            PathNode.MoveTo(4f, 15f),
            PathNode.LineTo(10f, 15f),
            PathNode.MoveTo(14f, 15f),
            PathNode.LineTo(20f, 15f)
        )
    )
}
