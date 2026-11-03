package com.amyrmo.damavand.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * Port of ZedSecure's `ZedGradients`, renamed [DamavandGradients] to avoid
 * clashing with the colour-stop [Gradients] object in Color.kt.
 *
 * Green→red remap of the gradient stops:
 *  - ZedLime 0xFFC7F24E → [DamavandLime] 0xFFFF5A4E (red)
 *  - ZedCyan 0xFF37E0D8 → [DamavandWarm] 0xFFFF8A5C (warm red-orange)
 *  - ZedViolet / ZedHotPink / ZedDeepViolet stops stay (violet/pink family),
 *    via [DamavandViolet] / [DamavandHotPink] / [DamavandDeepViolet].
 */
object DamavandGradients {
    val connected: Brush
        get() = Brush.linearGradient(
            colors = listOf(DamavandViolet, DamavandHotPink, DamavandLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    private val sessionPalettes: List<List<Color>> = listOf(
        listOf(DamavandViolet, DamavandHotPink, DamavandLime),
        listOf(DamavandDeepViolet, DamavandViolet, DamavandWarm),
        listOf(DamavandHotPink, DamavandViolet, DamavandWarm),
        listOf(DamavandViolet, DamavandWarm, DamavandLime),
        listOf(DamavandDeepViolet, DamavandHotPink, DamavandViolet),
    )

    fun forSession(sessionId: Int): Brush = Brush.linearGradient(
        colors = paletteFor(sessionId),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    private fun paletteFor(sessionId: Int): List<Color> =
        sessionPalettes[((sessionId % sessionPalettes.size) + sessionPalettes.size) % sessionPalettes.size]

    fun connectedStops(sessionId: Int): List<Color> =
        listOf(DamavandDeepViolet) + paletteFor(sessionId)

    val connectingStops: List<Color> = listOf(DamavandDeepViolet, DamavandViolet, DamavandHotPink)

    val idle: Brush
        get() = Brush.linearGradient(
            colors = listOf(DamavandDeepViolet, DamavandViolet, DamavandHotPink.copy(alpha = 0.7f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    val connecting: Brush
        get() = Brush.linearGradient(
            colors = listOf(DamavandViolet, DamavandWarm, DamavandLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
}
