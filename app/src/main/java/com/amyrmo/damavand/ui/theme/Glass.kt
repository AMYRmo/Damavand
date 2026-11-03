package com.amyrmo.damavand.ui.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass tokens + the capability model behind the fallback ladder
 * (architecture §5.3; design-research §§3.4, 4.1).
 *
 * ONE glass style (Regular), at most two surfaces per screen, control layer
 * only. The opaque version is the design; blur is an enhancement layered on
 * top when [GlassCapabilities.realBlur] holds. The ladder:
 *
 * | Condition                                   | Behaviour              |
 * |---------------------------------------------|------------------------|
 * | API 33+                                     | dock blur + refraction |
 * | API 31–32                                   | dock blur only         |
 * | API 26–30 / blur off at runtime / saver on  | opaque fake glass      |
 * | "Reduce transparency" setting               | opaque fake glass      |
 *
 * [LocalGlassCapabilities] is provided by [DamavandTheme]; components read
 * it and never probe the platform themselves.
 */
object GlassTokens {
    /** Dock blur radius (spec: 16–20 dp). */
    val BlurRadius: Dp = 18.dp

    /** Backdrop capture scale for the blur (perf budget, spec §3.4). */
    const val CaptureScale: Float = 0.5f

    /** Tint alpha range for live glass over a blurred backdrop: 55–65 %. */
    const val TintAlpha: Float = 0.60f

    /** Opaque fallback fills (Narcic's near-opaque dock values). */
    val FallbackFillDark: Color = Color(0xEA0D1624)
    val FallbackFillLight: Color = Color(0xF2F6F8FC)

    /** Shadow: black @ 12 %. (Compose shadows take an elevation; components
     *  pair this colour with a 24 dp-class elevation on the dock.) */
    val ShadowColor: Color = Color(0x1F000000)

    /** Specular top strip peak alpha (fake glass highlight). */
    const val SpecularAlpha: Float = 0.16f

    /** Connect-pill white tint overlay: 12–16 %. */
    const val PillTintAlpha: Float = 0.14f
}

/**
 * The glass border: 1–1.2 dp vertical gradient, white 35 % → 8 % → 18 %
 * (bright top edge, quiet middle, slight return at the bottom — Narcic's
 * dock border recipe).
 */
fun glassBorderBrush(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color.White.copy(alpha = 0.35f),
        0.5f to Color.White.copy(alpha = 0.08f),
        1f to Color.White.copy(alpha = 0.18f),
    ),
)

/** Quieter border for near-opaque cards (GlassCard): same shape, less light. */
fun cardBorderBrush(onSurface: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to onSurface.copy(alpha = 0.16f),
        0.5f to onSurface.copy(alpha = 0.05f),
        1f to onSurface.copy(alpha = 0.09f),
    ),
)

data class GlassCapabilities(
    /** Real backdrop blur may be rendered (dock). */
    val realBlur: Boolean,
    /** Rim refraction may be trialled (connect pill, API 33+; Phase 7). */
    val refraction: Boolean,
    /** The user asked for reduced transparency — everything opaque. */
    val reduceTransparency: Boolean,
    /** The window manager reports cross-window blur available. */
    val crossWindowBlur: Boolean,
)

val LocalGlassCapabilities = staticCompositionLocalOf {
    GlassCapabilities(
        realBlur = false,
        refraction = false,
        reduceTransparency = false,
        crossWindowBlur = false,
    )
}

/**
 * Computes the capability set from platform signals. [reduceTransparency]
 * is the app's own Appearance setting (ships day one); battery saver and a
 * missing cross-window-blur also force the opaque rung.
 */
@Composable
fun rememberGlassCapabilities(reduceTransparency: Boolean): GlassCapabilities {
    val context = LocalContext.current
    return remember(reduceTransparency) {
        val sdk = Build.VERSION.SDK_INT
        val crossWindow = if (sdk >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
            }.getOrDefault(false)
        } else {
            false
        }
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
        val blur = sdk >= Build.VERSION_CODES.S && crossWindow && !powerSave && !reduceTransparency
        GlassCapabilities(
            realBlur = blur,
            refraction = blur && sdk >= Build.VERSION_CODES.TIRAMISU,
            reduceTransparency = reduceTransparency,
            crossWindowBlur = crossWindow,
        )
    }
}
