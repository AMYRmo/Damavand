package com.amyrmo.damavand.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.ui.theme.GlassTokens
import com.amyrmo.damavand.ui.theme.LocalGlassCapabilities
import com.amyrmo.damavand.ui.theme.glassBorderBrush

/**
 * Fake glass — the Narcic recipe, and the foundation of the whole glass
 * system (architecture §5.3: "the opaque version is designed first, then
 * enhanced"). This file imports NOTHING from Haze: the fake-glass path must
 * compile and render identically if the Haze artifacts ever have to go.
 *
 * The recipe (from Narcic-Getway's iOS dock): a near-opaque fill, a 1.2 dp
 * vertical-gradient border (bright top edge → quiet middle → slight return
 * at the bottom), and a specular strip across the top. It costs nothing per
 * frame and is indistinguishable from blur at dock sizes.
 */

/**
 * The seam between the design system and any real-blur backend.
 *
 * Components (GlassDock, the scaffold) take a [GlassBackdrop] and never
 * touch a blur library directly. [NoGlassBackdrop] renders the fake-glass /
 * opaque rungs; `HazeGlass.kt` in this package provides the real-blur
 * implementation behind the same interface — it is the ONLY file in the
 * app that imports Haze.
 */
interface GlassBackdrop {
    /** Modifier the scrolling content behind glass surfaces must carry. */
    @Composable
    fun sourceModifier(): Modifier

    /**
     * Renders [content] on a glass surface of [shape]: real backdrop blur
     * when the backend + capabilities allow it, fake glass otherwise.
     * [isDark] selects the opaque fallback fill.
     */
    @Composable
    fun BlurSurface(
        shape: Shape,
        isDark: Boolean,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit,
    )
}

/** The no-blur backdrop: [BlurSurface] is exactly [FakeGlassSurface]. */
object NoGlassBackdrop : GlassBackdrop {
    @Composable
    override fun sourceModifier(): Modifier = Modifier

    @Composable
    override fun BlurSurface(
        shape: Shape,
        isDark: Boolean,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit,
    ) {
        FakeGlassSurface(shape = shape, isDark = isDark, modifier = modifier, content = content)
    }
}

/** Picks the opaque fallback fill for the current theme brightness. */
fun glassFallbackFill(isDark: Boolean): Color =
    if (isDark) GlassTokens.FallbackFillDark else GlassTokens.FallbackFillLight

/**
 * A surface rendered the fake-glass way. When the theme asks for live glass
 * tinting ([fill] == null) the surface uses its theme's opaque fallback
 * fill — the ladder's bottom rung — because fake glass has no backdrop to
 * tint over; translucency is only ever added by the real-blur backend.
 */
@Composable
fun FakeGlassSurface(
    shape: Shape,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    borderBrush: Brush = glassBorderBrush(),
    borderWidth: Dp = 1.2.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill ?: glassFallbackFill(isDark))
            .border(width = borderWidth, brush = borderBrush, shape = shape),
    ) {
        SpecularStrip(shape = shape)
        content()
    }
}

/**
 * The specular highlight: a soft white gradient band across the top edge,
 * fading out within the first ~20 dp. Static by design — spec/glow
 * animation is banned at MotionBudget.Paused and pointless otherwise.
 */
@Composable
fun BoxScope.SpecularStrip(
    shape: Shape,
    modifier: Modifier = Modifier,
    peakAlpha: Float = GlassTokens.SpecularAlpha,
    height: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = peakAlpha),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Convenience: is the current colour scheme dark (surface luminance test, Narcic-style). */
fun Color.isDarkSurface(): Boolean = luminance() < 0.5f

/** Reads the capability rung once for glass components. */
@Composable
fun glassRealBlurEnabled(): Boolean = LocalGlassCapabilities.current.realBlur
