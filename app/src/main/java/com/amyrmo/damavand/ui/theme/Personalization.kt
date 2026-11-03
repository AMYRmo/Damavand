package com.amyrmo.damavand.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * Green→red remap: ZedSecure's green/teal accent slots and its green
 * "good ping" colour are rotated to the Damavand red family here (see the
 * mapping notes on [AccentPresets] and [goodPingColor]). Everything else is
 * a faithful port of ZedSecure's Personalization.kt, made self-contained:
 * the domain enums ZedSecure kept in `domain.model` are defined in this
 * file because v2 has no such enums, and the AppSettings-based converters
 * are dropped (v2's AppSettings differs) — profiles convert through
 * [ThemeProfile.toPersonalization] in ThemeProfiles.kt instead.
 */

// ---------------------------------------------------------------------------
// Domain enums (defined locally; v2 has no domain-model equivalents)
// ---------------------------------------------------------------------------

enum class CardCornerStyle { Rounded, Squircle, Sharp }

enum class ListDensity { Comfortable, Compact }

enum class TrafficTileSize { Normal, Large }

enum class TrafficCardStyle { Cards, Graph }

// ---------------------------------------------------------------------------
// Accent presets
// ---------------------------------------------------------------------------

data class AccentPreset(val name: String, val light: Color, val dark: Color)

/**
 * ZedSecure's 10 accent slots, same order (indices are persisted in
 * settings, so slots must not move). Green-family slots remapped to red:
 *  - slot 2 "Teal"   (0xFF00897E / 0xFF52DDCF) → "Scarlet"  (0xFFE5484D / 0xFFFFB3AB)
 *  - slot 3 "Forest" (0xFF2E7D32 / 0xFF9BD89B) → "Bordeaux" (0xFF8C2B2E / 0xFFF2B8B5)
 *  - slot 7 "Cyan"   (0xFF00838F / 0xFF4DD0E1) → "Ruby"     (0xFFD61C4E / 0xFFFFB0BE)
 * Scarlet's light value is the Damavand primary rather than Crimson's
 * 0xFFC0362C so slots 2 and 6 stay distinguishable. Iris / Ocean / Amber /
 * Rose / Crimson / Grape / Slate are unchanged (no green primaries).
 */
val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("Iris", Color(0xFF5646D6), Color(0xFFC7BFFF)),
    AccentPreset("Ocean", Color(0xFF1E63D0), Color(0xFFAAC7FF)),
    AccentPreset("Scarlet", Color(0xFFE5484D), Color(0xFFFFB3AB)),
    AccentPreset("Bordeaux", Color(0xFF8C2B2E), Color(0xFFF2B8B5)),
    AccentPreset("Amber", Color(0xFFB4690E), Color(0xFFFFB870)),
    AccentPreset("Rose", Color(0xFFC4326B), Color(0xFFFFB0C8)),
    AccentPreset("Crimson", Color(0xFFC0362C), Color(0xFFFFB4A9)),
    AccentPreset("Ruby", Color(0xFFD61C4E), Color(0xFFFFB0BE)),
    AccentPreset("Grape", Color(0xFF8E24AA), Color(0xFFE9B3FF)),
    AccentPreset("Slate", Color(0xFF4A5B7A), Color(0xFFB6C6EA)),
)

// ---------------------------------------------------------------------------
// Helpers ported from ZedSecure
// ---------------------------------------------------------------------------

/** Black-ish on light colours, white on dark colours (readable foreground). */
fun Color.readableOn(): Color =
    if (luminance() > 0.5f) Color(0xFF10131A) else Color.White

/** ZedSecure's AMOLED treatment: pure-black surfaces, dimmed containers. */
fun ColorScheme.amoled(dark: Boolean): ColorScheme {
    if (!dark) return this
    val black = Color(0xFF000000)
    val bright = Color(0xFF1A1A1A)
    val low = Color(0xFF0B0B0B)
    val container = Color(0xFF0F0F0F)
    val high = Color(0xFF161616)
    val highest = Color(0xFF1E1E1E)
    return copy(
        background = black,
        surface = black,
        surfaceBright = bright,
        surfaceDim = black,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        surfaceContainerLow = low,
        surfaceContainerLowest = black,
    )
}

/** Stored ARGB [Long] (0xAARRGGBB, as kept by [ThemeProfile]) → [Color]. */
fun Long.argbLongToColor(): Color = Color(this)

// ---------------------------------------------------------------------------
// Personalization state
// ---------------------------------------------------------------------------

data class Personalization(
    val downloadColor: Color? = null,
    val uploadColor: Color? = null,

    val downloadTileColor: Color? = null,
    val uploadTileColor: Color? = null,
    val connectColor: Color? = null,
    val connectedColor: Color? = null,
    val tapHintColor: Color? = null,

    val configCardColor: Color? = null,
    val configCardTextColor: Color? = null,
    val serverCardColor: Color? = null,
    val serverActiveColor: Color? = null,
    val cornerStyle: CardCornerStyle = CardCornerStyle.Rounded,
    val density: ListDensity = ListDensity.Comfortable,
    val showServerPing: Boolean = true,
    val showServerUsage: Boolean = true,
    val monospaceAddress: Boolean = false,

    val showTrafficTiles: Boolean = true,
    val trafficTilesAboveHero: Boolean = false,
    val trafficTileSize: TrafficTileSize = TrafficTileSize.Normal,
    val trafficCardStyle: TrafficCardStyle = TrafficCardStyle.Cards,
) {
    companion object {
        val Default = Personalization()
    }
}

// ---------------------------------------------------------------------------
// Ping colours (container-relative, as in ZedSecure)
// ---------------------------------------------------------------------------

/**
 * "Good" ping, remapped OFF green: ZedSecure returned ZedLime on dark
 * containers and 0xFF1B7A3C on light ones. Here good = the Damavand connect
 * red, lightened for dark containers (0xFFFF7A72) and deepened for light
 * containers (0xFFC0362C) so it stays legible on both.
 */
fun goodPingColor(container: Color): Color =
    if (container.luminance() > 0.5f) Color(0xFFC0362C) else Color(0xFFFF7A72)

fun fairPingColor(container: Color): Color =
    if (container.luminance() > 0.5f) Color(0xFF8A5A00) else Color(0xFFFFC46B)

fun pingColor(ping: Int, container: Color, error: Color = Color(0xFFD1453B)): Color = when {
    ping < 0 -> error
    ping < 200 -> goodPingColor(container)
    ping < 500 -> fairPingColor(container)
    else -> error
}

// ---------------------------------------------------------------------------
// Composition locals (red defaults)
// ---------------------------------------------------------------------------

val LocalPersonalization = staticCompositionLocalOf { Personalization.Default }

val LocalThemeProfile = staticCompositionLocalOf { DamavandDefaultProfile }
