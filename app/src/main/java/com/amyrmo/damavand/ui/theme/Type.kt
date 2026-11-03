package com.amyrmo.damavand.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.R

/**
 * Typography (architecture §5.3; design-research §4.2).
 *
 * Latin renders in Inter, Persian in Vazirmatn (decision D9 — Vazirmatn won
 * the Estedad tie-break in the architecture). Both fonts are bundled variable
 * fonts fetched from the google/fonts repository (res/font/inter.ttf,
 * res/font/vazirmatn.ttf), so the showcase Persian locale never depends on
 * device fonts.
 *
 * TODO(fonts): the bundled files are *variable* fonts registered here at four
 * static weights. Android selects the nearest declared instance, but exact
 * variation-axis rendering per weight should be verified on device at the
 * first CI build; if weights render flat, ship static instances instead.
 * The scale follows M3 (Expressive metrics) with the *Emphasized* headline
 * variants exposed separately in [DamavandTextStyles] — the beta
 * Typography(…Emphasized) constructor slots are deliberately not used
 * (conservative-API rule for the no-local-compile environment).
 *
 * Persian line heights are raised vs Latin (Narcic fa scale: body 14/24),
 * because Vazirmatn's ascenders/diacritics clip at Latin line heights.
 */
val InterFontFamily: FontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

val VazirmatnFontFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.SemiBold),
    Font(R.font.vazirmatn, FontWeight.Bold),
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/** Builds the M3 type scale for one script direction of the app. */
fun damavandTypography(persian: Boolean): Typography {
    val f = if (persian) VazirmatnFontFamily else InterFontFamily
    // Persian line-height bonus per style class (design-research §4.2).
    val lh = if (persian) 4 else 0
    return Typography(
        displayLarge = style(f, FontWeight.Normal, 57, 64 + lh),
        displayMedium = style(f, FontWeight.Normal, 45, 52 + lh),
        displaySmall = style(f, FontWeight.Normal, 36, 44 + lh),
        headlineLarge = style(f, FontWeight.Normal, 32, 40 + lh),
        headlineMedium = style(f, FontWeight.Normal, 28, 36 + lh),
        headlineSmall = style(f, FontWeight.Normal, 24, 32 + lh),
        titleLarge = style(f, FontWeight.Normal, 22, 28 + lh, 0.0),
        titleMedium = style(f, FontWeight.Medium, 16, 24 + lh, 0.15),
        titleSmall = style(f, FontWeight.Medium, 14, 20 + lh, 0.1),
        bodyLarge = style(f, FontWeight.Normal, 16, 24 + lh, 0.5),
        bodyMedium = style(f, FontWeight.Normal, 14, 20 + lh, 0.25),
        bodySmall = style(f, FontWeight.Normal, 12, 16 + lh, 0.4),
        labelLarge = style(f, FontWeight.Medium, 14, 20 + lh, 0.1),
        labelMedium = style(f, FontWeight.Medium, 12, 16 + lh, 0.5),
        labelSmall = style(f, FontWeight.Medium, 11, 16 + lh, 0.5),
    )
}

/**
 * Named one-off styles the design system calls for (design-research §4.2
 * anchors). Kept beside the scale so screens never invent sizes.
 */
object DamavandTextStyles {
    /** Connection-status headline on Home (displaySmall, emphasized). */
    fun statusHeadline(persian: Boolean): TextStyle =
        style(if (persian) VazirmatnFontFamily else InterFontFamily, FontWeight.SemiBold, 36, 44)

    /** Connect-pill label (titleLarge, emphasized). */
    fun pillLabel(persian: Boolean): TextStyle =
        style(if (persian) VazirmatnFontFamily else InterFontFamily, FontWeight.SemiBold, 22, 28)

    /** Dashboard readings (headlineSmall, emphasized). */
    fun dashboardValue(persian: Boolean): TextStyle =
        style(if (persian) VazirmatnFontFamily else InterFontFamily, FontWeight.SemiBold, 24, 32)

    /** Logs, IPs, commands, ports: always monospace, always Latin digits. */
    val Mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}
