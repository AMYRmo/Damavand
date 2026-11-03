package com.amyrmo.damavand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The Damavand v2 theme root (architecture §5.3).
 *
 * Dark is the default-first scheme (Night-Ice); [amoled] swaps in the
 * pure-black variant; a full light scheme ships alongside. Beyond the M3
 * scheme this provides the two design-system composition locals every
 * component reads instead of probing the platform: [LocalMotionBudget]
 * and [LocalGlassCapabilities].
 *
 * @param persian selects the Vazirmatn type scale with raised line heights.
 * The caller derives it from the app's language setting (§5.4); layout
 * direction itself follows the composed locale as usual.
 * @param reduceTransparency the Appearance "Reduce transparency" setting —
 * forces the opaque rung of the glass fallback ladder.
 */
@Composable
fun DamavandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    persian: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && amoled -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val motionBudget = rememberMotionBudget()
    val glassCapabilities = rememberGlassCapabilities(reduceTransparency)

    CompositionLocalProvider(
        LocalMotionBudget provides motionBudget,
        LocalGlassCapabilities provides glassCapabilities,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = damavandTypography(persian),
            shapes = DamavandShapes,
            content = content,
        )
    }
}
