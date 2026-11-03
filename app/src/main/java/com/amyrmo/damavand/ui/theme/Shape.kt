package com.amyrmo.damavand.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape + spacing tokens (architecture §5.3; design-research §4.3).
 *
 * Narcic's shape scale (rounded, not bubbly): 10 / 14 / 22 / 30 / 40 dp.
 * **The capsule is reserved for the control layer** — dock, connect pill,
 * chips. Content (cards, rows, sheets) never uses it; that reservation is
 * what makes the glass surfaces read as a separate layer.
 */
val DamavandShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

object DamavandShapeTokens {
    /** Cards: 16 dp (between small and medium, per the spec's card rule). */
    val Card: Shape = RoundedCornerShape(16.dp)

    /** Sheets: 20 dp top corners, square bottom. */
    val SheetTop: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** Control layer only: dock, connect pill, chips. */
    val Capsule: Shape = CircleShape
}

object Dimens {
    // 4 dp grid; screen horizontal padding 20 dp (sheets) – 22 dp (home).
    val ScreenPadding: Dp = 20.dp
    val HomePadding: Dp = 22.dp
    val CardPadding: Dp = 16.dp
    val SectionSpacing: Dp = 10.dp
    val RowVerticalPadding: Dp = 12.dp

    // GlassDock (design-research §3.4).
    val DockHeight: Dp = 64.dp
    val DockSideMargin: Dp = 16.dp
    val DockBottomMargin: Dp = 12.dp
    /** Content inset so scrolling lists clear the floating dock. */
    val DockClearance: Dp = DockHeight + DockBottomMargin + 16.dp

    // ConnectPill (design-research §3.4).
    val PillHeight: Dp = 72.dp
}
