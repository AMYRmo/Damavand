package com.amyrmo.damavand.lite.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
// 3.1.2: rendered as framework ImageVectors (see LineIconsVector.kt)
// through Material Icon. The hand-rolled Canvas versions rendered
// off-center on-device in every release up to 3.1.1; Icon + VectorPainter
// is the framework's own rendering path, so a glyph centered at (12, 12)
// in the viewport renders centered, in any parent.

/** Renders one of the line-icon vectors at [size], tinted [color]. */
@Composable
private fun LineIcon(vector: ImageVector, size: Dp, color: Color) {
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

/**
 * A round icon button (3.1.2): the glyph is a vector rendered by
 * Material [Icon], centered in the button by the Box. [flip] rotates
 * the glyph 180° (used for the RTL/LTR-aware back chevron).
 */
@Composable
fun CircleIconButton(
    buttonSize: Dp,
    glyphSize: Dp,
    filled: Boolean,
    onClick: () -> Unit,
    glyphColor: Color,
    icon: ImageVector,
    flip: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .background(
                if (filled) LiteColors.accent else Color.Transparent,
                CircleShape
            )
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, LiteColors.hairline, CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glyphColor,
            modifier = Modifier.size(glyphSize)
                .then(if (flip) Modifier.rotate(180f) else Modifier)
        )
    }
}

@Composable
fun HomeLineIcon(size: Dp, color: Color) {
    LineIcon(HomeVector, size, color)
}

@Composable
fun ServersLineIcon(size: Dp, color: Color) {
    LineIcon(ServersVector, size, color)
}

@Composable
fun StatsLineIcon(size: Dp, color: Color) {
    LineIcon(StatsVector, size, color)
}

@Composable
fun SettingsLineIcon(size: Dp, color: Color) {
    LineIcon(SettingsVector, size, color)
}

/** Power symbol (ring content, disconnected state): arc + vertical line. */
@Composable
fun PowerLineIcon(size: Dp, color: Color) {
    LineIcon(PowerVector, size, color)
}

/** Bolt (fastest-server button): filled lightning. */
@Composable
fun BoltIcon(size: Dp, color: Color) {
    LineIcon(BoltVector, size, color)
}

/** Forward chevron (RTL-aware via the vector's autoMirror). */
@Composable
fun ChevronForwardIcon(size: Dp, color: Color) {
    LineIcon(ChevronForwardVector, size, color)
}

/** Globe (flag fallback for unknown country): circle + meridian + equator. */
@Composable
fun GlobeLineIcon(size: Dp, color: Color) {
    LineIcon(GlobeVector, size, color)
}

/** Refresh (test-all button, optional icon): circular arrow. */
@Composable
fun RefreshLineIcon(size: Dp, color: Color) {
    LineIcon(RefreshVector, size, color)
}

/** Plus (add server, 3.0.0): a vertical and a horizontal line. */
@Composable
fun PlusLineIcon(size: Dp, color: Color) {
    LineIcon(PlusVector, size, color)
}

/** Trash (delete server, 3.0.0): lid, body and inner lines. */
@Composable
fun TrashLineIcon(size: Dp, color: Color) {
    LineIcon(TrashVector, size, color)
}

/** Chain link (chain rows, 3.0.0): two interlocked rounded links. */
@Composable
fun ChainLineIcon(size: Dp, color: Color) {
    LineIcon(ChainVector, size, color)
}
