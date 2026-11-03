package com.amyrmo.damavand.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.amyrmo.damavand.model.CoreKind

/**
 * Colour tokens for Damavand v2 (architecture §5.3; design-research §4.1,
 * adopted verbatim). Tokens are code and are reviewed like code: composables
 * outside this file never hard-code a palette value — they read
 * [MaterialTheme.colorScheme][androidx.compose.material3.MaterialTheme], the
 * brand/state objects below, or [CoreAccents].
 *
 * The base family is Narcic-Getway's "Night-Ice" blue, tokenised (Narcic
 * itself hard-codes these values inside composables — the mistake this file
 * exists to prevent).
 */

// ---------------------------------------------------------------------------
// Brand + state tokens (constant across light/dark/AMOLED)
// ---------------------------------------------------------------------------

object Brand {
    val Primary = Color(0xFF2E6BE6)
    val Violet = Color(0xFF3D7BFF)
    val Cyan = Color(0xFF4ED9E0)
    val Ice = Color(0xFF7DD8FF)
    val Deep = Color(0xFF0A1830)
}

object StateColors {
    val Connected = Color(0xFF27A468)
    val Connecting = Color(0xFFFF9500)
    val Error = Color(0xFFFF3B30)
}

object TrafficColors {
    val Down = Color(0xFF007AFF)
    val Up = Color(0xFF34C759)
}

/** Dock item colours for the unselected state (Narcic tab-bar values). */
object DockColors {
    val InactiveDark = Color(0xFF8E9BAE)
    val InactiveLight = Color(0xFF64748B)
}

/** Narcic `pingColor()` semantics, split by theme (design-research §4.1). */
fun pingColor(ms: Long, darkTheme: Boolean): Color = when {
    ms < 0 -> Color(0xFFD1453B) // unreachable / timed out
    ms < 150 -> if (darkTheme) Color(0xFF7DD8FF) else Color(0xFF1B7A3C)
    ms < 350 -> if (darkTheme) Color(0xFFFFC46B) else Color(0xFF8A5A00)
    else -> Color(0xFFD1453B)
}

// ---------------------------------------------------------------------------
// Gradients (design-research §4.1). Returned as colour stops; callers build
// the Brush so direction stays a layout decision.
// ---------------------------------------------------------------------------

object Gradients {
    val Connected: List<Color> = listOf(Brand.Violet, Brand.Cyan, Brand.Ice)

    val Connecting: List<Color> = listOf(Brand.Deep, Color(0xFF1E4A9E), Brand.Cyan)

    val Idle: List<Color> = listOf(Brand.Deep, Color(0xFF173B7C), Brand.Violet.copy(alpha = 0.75f))

    /**
     * 3–5 per-session variants of the connected gradient, rotated by session
     * id (Narcic pattern). All variants stay inside the brand family so the
     * connected state reads identically; only the stop order/weights move.
     */
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
// Per-core accents (architecture §5.3). One hue per core so server-card
// rails, chain chips and editor headers read at a glance.
// ---------------------------------------------------------------------------

object CoreAccents {
    val Xray: Color = Brand.Violet
    val Aether: Color = Brand.Cyan
    val Mihomo: Color = Color(0xFFFF8A5C)
    val Psiphon: Color = Color(0xFF7C6FF0)
    val Whitedns: Color = Color(0xFF20D8C0)
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
// Schemes (design-research §4.1, verbatim values)
// ---------------------------------------------------------------------------

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF1E4A9E),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF87D5DB),
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F54),
    onSecondaryContainer = Color(0xFFB4EBEF),
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
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF00696E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4EBEF),
    onSecondaryContainer = Color(0xFF00363A),
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
