package com.amyrmo.damavand.ui.components.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.ui.theme.GlassTokens
import com.amyrmo.damavand.ui.theme.LocalGlassCapabilities
import com.amyrmo.damavand.ui.theme.glassBorderBrush
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * ============================================================
 *  THE ONLY FILE IN THE APP THAT IMPORTS HAZE.
 * ============================================================
 *
 * The version catalog pins Haze 2.1.0 marked VERIFY (the artifacts could
 * not be resolved from the build VM). Everything the app needs from Haze
 * is funnelled through [HazeGlassBackdrop], which implements the
 * [GlassBackdrop] seam from `FakeGlass.kt`:
 *
 *  - The 2.1.0 API was verified against the published sources on
 *    2026-11-03 (the 1.x-style hazeEffect/HazeStyle API is gone; 2.1 uses
 *    [rememberHazeState], [hazeSource] and `hazeBlur` with a
 *    [HazeBlurStyle] block). Any future API drift stays confined to
 *    THIS file.
 *  - If Haze had to be dropped entirely, replacing this class's body with
 *    the [NoGlassBackdrop] behaviour (one small edit) leaves every other
 *    file — GlassDock, the scaffold, all screens — compiling and looking
 *    identical on the fake-glass rung.
 *
 * The Haze path is only ever *used* when
 * [com.amyrmo.damavand.ui.theme.GlassCapabilities.realBlur] holds (API 31+,
 * cross-window blur available, no battery saver, no reduce-transparency);
 * the capability check happens in GlassDock, not here.
 *
 * Pill rim refraction (Haze Glass materials, API 33+) is a Phase-7 trial
 * per the rollout plan and is deliberately NOT wired here yet.
 */
class HazeGlassBackdrop(private val state: HazeState) : GlassBackdrop {

    @Composable
    override fun sourceModifier(): Modifier = Modifier.hazeSource(state)

    @Composable
    override fun BlurSurface(
        shape: Shape,
        isDark: Boolean,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit,
    ) {
        val capabilities = LocalGlassCapabilities.current
        if (!capabilities.realBlur) {
            // Same file, bottom rung: identical visuals to NoGlassBackdrop.
            FakeGlassSurface(shape = shape, isDark = isDark, modifier = modifier, content = content)
            return
        }
        // Live glass: backdrop blur at the spec's radius, tinted with the
        // surface colour at GlassTokens.TintAlpha, over the fake-glass
        // border + specular recipe so both rungs share one silhouette.
        // Haze 2.1 API (verified against the published 2.1.0 sources):
        // Modifier.hazeBlur(input, style) with a HazeBlurStyle block;
        // styles compare by identity, so the style is remembered.
        val tint = glassFallbackFill(isDark).copy(alpha = 1f)
        val style = remember(tint) {
            HazeBlurStyle {
                blurRadius(GlassTokens.BlurRadius)
                backgroundColor(tint)
                colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = GlassTokens.TintAlpha))))
                noiseFactor(0f)
            }
        }
        Box(
            modifier = modifier
                .clip(shape)
                .hazeBlur(
                    input = HazeInput.Sources(state),
                    style = style,
                )
                .border(width = 1.2.dp, brush = glassBorderBrush(), shape = shape),
        ) {
            SpecularStrip(shape = shape)
            content()
        }
    }
}

/**
 * Creates the app's single [HazeGlassBackdrop]. One backdrop source per
 * screen (spec §3.4): the scaffold owns it and hands the same instance to
 * the dock; content is captured once via [GlassBackdrop.sourceModifier].
 */
@Composable
fun rememberHazeGlassBackdrop(): HazeGlassBackdrop {
    val state = rememberHazeState()
    return remember(state) { HazeGlassBackdrop(state) }
}

/** The neutral background colour Haze blends from when a surface is empty. */
internal fun hazeBackground(isDark: Boolean): Color = glassFallbackFill(isDark)
