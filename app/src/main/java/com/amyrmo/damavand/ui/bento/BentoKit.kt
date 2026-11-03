/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * BentoKit — the reusable Bento building blocks, ported from ZedSecure
 * v3.1.8 (ui/bento/BentoKit.kt) and trimmed to what BentoHome needs.
 * Adaptations for Damavand v2:
 *  - Zed's BentoPrefs (corners/density/title/motifs/playful) is gone; the
 *    shapes/metrics/type scale use the fixed "Round / Comfortable / Large"
 *    values from Zed's defaults.
 *  - No org.jetbrains.compose.resources, no coil, no graphics-shapes and
 *    no material-icons (none are v2 dependencies): cards carry no motif
 *    polygons, flags render as code badges, icons are caller-supplied
 *    composables.
 *  - The traffic/stat tiles from Zed's BentoHome (SpeedTile, FactTile and
 *    the sparkline) are folded in here instead of a separate tiles file.
 *  - All text arrives as plain parameters; localisation stays with the
 *    caller (androidx stringResource against v2's R).
 */
package com.amyrmo.damavand.ui.bento

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.ui.formatRate

/** Fixed shape scale (Zed "Round" corners). */
object BentoShapes {
    fun tile(prominent: Boolean): Shape = RoundedCornerShape(if (prominent) 36.dp else 30.dp)
    fun hero(): Shape = RoundedCornerShape(34.dp)
    fun card(): Shape = RoundedCornerShape(30.dp)
    fun block(): Shape = RoundedCornerShape(26.dp)
    fun small(): Shape = RoundedCornerShape(22.dp)
    fun chip(): Shape = RoundedCornerShape(14.dp)

    fun row(index: Int, count: Int): Shape {
        val outer = 22.dp
        val inner = 8.dp
        return when {
            count <= 1 -> RoundedCornerShape(outer)
            index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
            index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
            else -> RoundedCornerShape(inner)
        }
    }
}

/** Fixed metrics (Zed "Comfortable" density). */
object BentoMetrics {
    val gutter: Dp = 16.dp
    val gap: Dp = 8.dp
    val tileRow: Dp = 134.dp
    val rowMin: Dp = 62.dp
    val pad: Dp = 16.dp
}

object BentoType {
    @Composable
    fun display(): TextStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = 42.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (Bento.latin) (-42 * 0.033f).sp else 0.sp,
    )

    @Composable
    fun title(size: TextUnit, tight: Boolean = true): TextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = size,
        lineHeight = size * 1.18f,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (Bento.latin && tight) (-size.value * 0.022f).sp else 0.sp,
    )

    @Composable
    fun caption(): TextStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = if (Bento.latin) 0.9.sp else 0.sp,
    )

    @Composable
    fun number(size: TextUnit, weight: FontWeight = FontWeight.SemiBold): TextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = size,
            lineHeight = size * 1.08f,
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            letterSpacing = if (Bento.latin) (-size.value * 0.025f).sp else 0.sp,
        )

    @Composable
    fun body(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)

    @Composable
    fun rowTitle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun BentoCard(
    tone: BentoTone,
    modifier: Modifier = Modifier,
    shape: Shape = BentoShapes.card(),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.985f else 1f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "bento-card-press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(tone.container)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides tone.content) {
            Column(
                Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

@Composable
fun BentoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = BentoType.display(),
            color = Bento.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun BentoCaption(text: String, modifier: Modifier = Modifier, color: Color = Bento.colors.muted) {
    Text(
        text.uppercase(),
        style = BentoType.caption(),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun BentoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val c = Bento.colors
    val shape = BentoShapes.chip()
    val bg = if (selected) c.lime else Color.Transparent
    val fg = if (selected) c.onLime else c.ink
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(trailing, style = BentoType.number(13.sp), color = fg.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

@Composable
fun BentoPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    numeric: Boolean = false,
) {
    Row(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(container)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            text,
            style = if (numeric) {
                BentoType.number(13.sp)
            } else {
                MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            },
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Country badge without Zed's bundled flag SVGs / coil: the two-letter
 * code on a tinted tile (v2 ships no flag assets).
 */
@Composable
fun BentoFlag(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    corner: Dp = 13.dp,
    fallback: String? = null,
    container: Color = Bento.colors.surface2,
    content: Color = Bento.colors.ink,
) {
    val code = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(container)
            .border(1.dp, content.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (fallback ?: code ?: "··").take(3),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.Bold,
            ),
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun BentoIndex(index: Int, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    Text(
        "%02d".format(index),
        style = BentoType.number(12.sp, FontWeight.Medium),
        color = color.copy(alpha = color.alpha * 0.7f),
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Traffic / stat tiles (folded in from Zed's BentoHome tiles; a separate
// BentoTiles.kt layout engine is deferred — see the package report).
// ---------------------------------------------------------------------------

/**
 * Live rate tile with sparkline. [tone] for download is the palette's
 * `cyan` tone in Damavand — already red-remapped in [BentoPalettes], so
 * the tile renders warm rose, not Zed's teal.
 */
@Composable
fun BentoSpeedTile(
    label: String,
    bps: Long,
    history: List<Float>,
    tone: BentoTone,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val rate = formatRate(bps)
    val value = rate.substringBefore(' ')
    val unit = rate.substringAfter(' ', "")
    Column(
        modifier
            .height(122.dp)
            .clip(BentoShapes.block())
            .background(tone.container)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = tone.content,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value,
                style = BentoType.number(36.sp),
                color = tone.content.copy(alpha = if (live) 1f else 0.6f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = tone.content.copy(alpha = 0.8f),
                modifier = Modifier.alignByBaseline(),
            )
        }
        BentoSparkline(history, tone.content, Modifier.fillMaxWidth().height(22.dp))
    }
}

@Composable
fun BentoSparkline(values: List<Float>, color: Color, modifier: Modifier) {
    // Direction-aware: the series is drawn oldest→newest along +x and the
    // whole canvas is mirrored in RTL, so "latest" always sits at the
    // reading-end edge (same approach as ZedSecure's sparkline).
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val path = remember { Path() }
    val fill = remember { Path() }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull() ?: 0f
        val min = values.minOrNull() ?: 0f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = 3.dp.toPx()
        path.reset()
        fill.reset()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = if (max <= 0f) {
                size.height - pad
            } else {
                size.height - pad - (v - min) / span * (size.height - pad * 2)
            }
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
            drawPath(path, color.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BentoFactTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tone: BentoTone? = null,
) {
    val c = Bento.colors
    Column(
        modifier
            .height(70.dp)
            .clip(BentoShapes.small())
            .background(tone?.container ?: c.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = BentoType.body().copy(fontSize = 12.5.sp),
            color = tone?.content?.copy(alpha = 0.8f) ?: c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = BentoType.number(20.sp), color = tone?.content ?: c.ink, maxLines = 1)
    }
}

/** Small filled text button used on Bento cards (Zed's PillButton). */
@Composable
fun BentoPillButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outline: Color? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(container)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun BentoSectionSpacer() {
    Spacer(Modifier.height(8.dp))
}
