package com.amyrmo.damavand.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.amyrmo.damavand.model.CoreKind

/**
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * Colour tokens for Damavand v2. The ENTIRE theme is remapped from
 * ZedSecure green to Damavand RED: any colour with hue ~100°–190°
 * (green/teal/cyan-green/lime) is rotated to ~350°–10° (red), preserving
 * saturation/lightness. Property/object names are kept from v2 so existing
 * call sites compile unchanged; where a name says "Cyan"/"Teal"/"Lime" the
 * VALUE is now the red-family replacement and the mapping is noted inline.
 *
 * Tokens are code: composables outside this file never hard-code a palette
 * value — they read [androidx.compose.material3.MaterialTheme.colorScheme],
 * the brand/state objects below, or [CoreAccents].
 */

// ---------------------------------------------------------------------------
// Brand + state tokens (constant across light/dark/AMOLED)
// ---------------------------------------------------------------------------

object Brand {
    /** Was Night-Ice blue 0xFF2E6BE6 / Zed connect teal 0xFF12A594 → Damavand red. */
    val Primary = Color(0xFFE5484D)

    /** ZedSecure connected violet, kept (non-green). Was 0xFF3D7BFF. */
    val Violet = Color(0xFF7C5CFF)

    /** Name kept for compatibility; value was cyan 0xFF4ED9E0 → warm red-orange. */
    val Cyan = Color(0xFFFF6B5E)

    /** Was ice blue 0xFF7DD8FF → light rose. */
    val Ice = Color(0xFFFFB3AB)

    /** Was deep blue 0xFF0A1830 → deep dark red. */
    val Deep = Color(0xFF30060A)
}

// ZedSecure colour constants (from ZedSecure Color.kt), remapped:
//   BrandIris  0xFF5646D6 → kept (violet)
//   BrandTeal  0xFF00897E → ZedTealAsRed 0xFFC0362C (teal → crimson red)
//   BrandRose  0xFFC4326B → kept (rose)
//   ZedLime    0xFFC7F24E → DamavandLime 0xFFFF5A4E (lime → red)
//   ZedOnLime  0xFF1A2200 → DamavandOnLime 0xFF2B0703 (dark green-black → dark red-black)
//   ZedHotPink 0xFFFF5FA2 → DamavandHotPink, kept (pink)
//   ZedViolet  0xFF7A5CFF → DamavandViolet, kept (violet)
//   ZedDeepViolet 0xFF2A1361 → DamavandDeepViolet, kept (violet)
//   ZedCyan    0xFF37E0D8 → DamavandWarm 0xFFFF8A5C (cyan → warm red-orange)
val BrandIris = Color(0xFF5646D6)
val ZedTealAsRed = Color(0xFFC0362C)
val BrandRose = Color(0xFFC4326B)
val DamavandLime = Color(0xFFFF5A4E)
val DamavandOnLime = Color(0xFF2B0703)
val DamavandHotPink = Color(0xFFFF5FA2)
val DamavandViolet = Color(0xFF7A5CFF)
val DamavandDeepViolet = Color(0xFF2A1361)
val DamavandWarm = Color(0xFFFF8A5C)

object StateColors {
    /** Was green 0xFF27A468 → Damavand red (connected reads red + violet gradient). */
    val Connected = Color(0xFFE5484D)
    val Connecting = Color(0xFFFF9500)
    val Error = Color(0xFFFF3B30)
}

object TrafficColors {
    /** Was blue 0xFF007AFF → red-family warm (matches profile download). */
    val Down = Color(0xFFFF6B5E)

    /** Was green 0xFF34C759 → Zed upload violet. */
    val Up = Color(0xFFB49BFF)
}

/** Dock item colours for the unselected state (Narcic tab-bar values). */
object DockColors {
    val InactiveDark = Color(0xFF8E9BAE)
    val InactiveLight = Color(0xFF64748B)
}

/**
 * Ping colours, split by theme. Remapped off green: the <150 ms branch was
 * green 0xFF1B7A3C (light) / ice 0xFF7DD8FF (dark) → deep red 0xFFC0362C
 * (light) / lightened connect red 0xFFFF7A72 (dark). Amber and error stay.
 */
fun pingColor(ms: Long, darkTheme: Boolean): Color = when {
    ms < 0 -> Color(0xFFD1453B) // unreachable / timed out
    ms < 150 -> if (darkTheme) Color(0xFFFF7A72) else Color(0xFFC0362C)
    ms < 350 -> if (darkTheme) Color(0xFFFFC46B) else Color(0xFF8A5A00)
    else -> Color(0xFFD1453B)
}

// ---------------------------------------------------------------------------
// Gradients. Returned as colour stops; callers build the Brush so direction
// stays a layout decision. With the remapped Brand values these are now
// violet → warm-red → rose stops; the two hard-coded blue mid-stops were
// rotated to dark reds (0xFF1E4A9E → 0xFF7A1622, 0xFF173B7C → 0xFF5C1218).
// ---------------------------------------------------------------------------

object Gradients {
    val Connected: List<Color> = listOf(Brand.Violet, Brand.Cyan, Brand.Ice)

    val Connecting: List<Color> = listOf(Brand.Deep, Color(0xFF7A1622), Brand.Cyan)

    val Idle: List<Color> = listOf(Brand.Deep, Color(0xFF5C1218), Brand.Violet.copy(alpha = 0.75f))

    val ConnectedVariants: List<List<Color>> = listOf(
        Connected,
        listOf(Brand.Cyan, Brand.Violet, Brand.Ice),
        listOf(Brand.Violet, Brand.Ice, Brand.Cyan),
        listOf(Brand.Ice, Brand.Cyan, Brand.Violet),
    )

    fun connectedForSession(sessionId: Int): List<Color> =
        ConnectedVariants[((sessionId % ConnectedVariants.size) + ConnectedVariants.size) % ConnectedVariants.size]
}

// ---------------------------------------------------------------------------
// Per-core accents. One hue per core so server-card rails, chain chips and
// editor headers read at a glance. Remap choices (no green/teal accents):
//   XRAY     violet 0xFF7C5CFF (Brand.Violet, kept)
//   AETHER   was cyan 0xFF4ED9E0 (hue ~183°, in remap band) → orange-red 0xFFFF8A5C
//   MIHOMO   was 0xFFFF8A5C → shifted to amber 0xFFFFB020 so it does not
//            collide with Aether's new accent
//   PSIPHON  was 0xFF7C6FF0 → light violet 0xFF9D7BFF (distinct from Xray)
//   WHITEDNS was teal 0xFF20D8C0 → Damavand red 0xFFE5484D
// ---------------------------------------------------------------------------

object CoreAccents {
    val Xray: Color = Brand.Violet
    val Aether: Color = Color(0xFFFF8A5C)
    val Mihomo: Color = Color(0xFFFFB020)
    val Psiphon: Color = Color(0xFF9D7BFF)
    val Whitedns: Color = Color(0xFFE5484D)
    val SingBox: Color = Color(0xFF8D9199) // = dark outline token
    val Chain: Color = Brand.Primary
    val Auto: Color = Brand.Ice
}

fun CoreKind.accent(): Color = when (this) {
    CoreKind.XRAY -> CoreAccents.Xray
    CoreKind.SINGBOX -> CoreAccents.SingBox
    CoreKind.PSIPHON -> CoreAccents.Psiphon
    CoreKind.AETHER -> CoreAccents.Aether
    CoreKind.MIHOMO -> CoreAccents.Mihomo
    CoreKind.WHITEDNS -> CoreAccents.Whitedns
    CoreKind.CHAIN -> CoreAccents.Chain
    CoreKind.AUTO -> CoreAccents.Auto
}

// ---------------------------------------------------------------------------
// Schemes — rebuilt around red primary. Surfaces keep v2's near-black
// blue-grey values; primary/secondary containers move off blue/teal to the
// red tonal family (dark primary is the M3 red tonal 0xFFFFB3AB; secondary
// was teal 0xFF87D5DB → rose tonal; tertiary orange stays).
// ---------------------------------------------------------------------------

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3AB),
    onPrimary = Color(0xFF601410),
    primaryContainer = Color(0xFF7A1C22),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF552400),
    tertiaryContainer = Color(0xFF7A3800),
    onTertiaryContainer = Color(0xFFFFDCC7),
    error = StateColors.Error,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B111E),
    onBackground = Color(0xFFE1E6F0),
    surface = Color(0xFF0B111E),
    onSurface = Color(0xFFE1E6F0),
    surfaceVariant = Color(0xFF222836),
    onSurfaceVariant = Color(0xFFB9C0CE),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF3A4152),
    surfaceContainerLowest = Color(0xFF060B14),
    surfaceContainerLow = Color(0xFF131926),
    surfaceContainer = Color(0xFF171D2B),
    surfaceContainerHigh = Color(0xFF222836),
    surfaceContainerHighest = Color(0xFF2D3342),
)

/** AMOLED variant of the dark scheme: pure-black surfaces, dim containers. */
val AmoledColorScheme: ColorScheme = DarkColorScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF181818),
)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF5F1410),
    secondary = Color(0xFF775652),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5DDD9),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFFB4551B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC7),
    onTertiaryContainer = Color(0xFF552400),
    error = StateColors.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF101623),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF101623),
    surfaceVariant = Color(0xFFE1E6EF),
    onSurfaceVariant = Color(0xFF4A5468),
    outline = Color(0xFF8A93A3),
    outlineVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFE9EDF4),
    surfaceContainerHigh = Color(0xFFE1E6EF),
    surfaceContainerHighest = Color(0xFFD8DEE9),
)
