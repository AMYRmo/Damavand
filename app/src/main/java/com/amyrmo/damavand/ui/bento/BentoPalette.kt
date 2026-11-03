/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * BentoPalette — the Bento colour system, ported from ZedSecure v3.1.8
 * (ui/bento/BentoPalette.kt) with two deliberate changes:
 *
 * 1. NO materialkolor. Zed generated its Accent/Pastel/Vivid/Mono palettes
 *    from an HCT seed at runtime; Damavand ships only the hand-written
 *    literal palettes below, looked up by name via [BentoPalettes.of].
 *
 * 2. RED REMAP (owner's no-green rule). Every hue in the 100°–190° band
 *    (lime / green / teal / aqua / mint) is rotated to the red band
 *    (350°–10°), preserving saturation and lightness:
 *      - the `lime` accent slot becomes Damavand red
 *        (dark palettes ≈ 0xFFFF5A4E family, light ≈ 0xFFD92B20 family —
 *        rendered bright, e.g. 0xFFFF6B5E, so the dark `onLime` text the
 *        design pairs with it stays legible);
 *      - `onLime` becomes dark red-brown 0xFF2A0500;
 *      - teal `cyan` tone containers (e.g. 0xFF103D3A) become warm
 *        red-brown (0xFF3D1210 family) and their aqua contents become rose;
 *      - semantic `good` / `goodOnStage` (ping-is-healthy greens such as
 *        0xFF7BE495 / 0xFF9BF0AE / 0xFF16803F) become warm rose
 *        (0xFFFF9E8A dark / 0xFFFFB59D on stage / 0xFFC93A22 light) — in
 *        this design "good" reads as warm rose, never green; `mid` stays
 *        amber and `bad` stays red, so ping colours remain distinguishable
 *        by lightness/hue-step (rose → amber → red).
 *      - lime-based families are converted wholesale: Citrus becomes a
 *        red palette (display name "Citrus Red"), Matcha and Pistachio
 *        (green families) become red variants, Ocean's mint accent
 *        becomes red; Sunset's amber accent is warmed toward red-orange.
 *    Violet, blue, pink and coral tones are kept as authored, except
 *    where a tone's container/content itself sat in the green band.
 */
package com.amyrmo.damavand.ui.bento

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class BentoTone(val container: Color, val content: Color)

@Immutable
data class BentoColors(
    val dark: Boolean,
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val stage: Color,
    val onStage: Color,
    val chipOnStage: Color,
    val goodOnStage: Color,
    /** Accent slot. Named `lime` as in ZedSecure; in Damavand it is RED. */
    val lime: Color,
    val onLime: Color,
    val blue: BentoTone,
    val violet: BentoTone,
    /** Named `cyan` as in ZedSecure; the tones are red-remapped (warm rose). */
    val cyan: BentoTone,
    val pink: BentoTone,
    val coral: BentoTone,
    /** Semantic "healthy" colour — warm rose in Damavand (see header). */
    val good: Color,
    val mid: Color,
    val bad: Color,
    val selected: Color,
    val dock: Color,
    val dockLine: Color,
    val danger: Color,
    val onDanger: Color,
    val colorful: Boolean = false,
) {
    val stageTone: BentoTone get() = BentoTone(stage, onStage)
    val limeTone: BentoTone get() = BentoTone(lime, onLime)
    val surfaceTone: BentoTone get() = BentoTone(surface, ink)

    fun tone(kind: BentoToneKind): BentoTone = when (kind) {
        BentoToneKind.Stage -> stageTone
        BentoToneKind.Lime -> limeTone
        BentoToneKind.Blue -> blue
        BentoToneKind.Violet -> violet
        BentoToneKind.Cyan -> cyan
        BentoToneKind.Pink -> pink
        BentoToneKind.Coral -> coral
        BentoToneKind.Surface -> surfaceTone
    }

    fun pingColor(ms: Int): Color = when {
        ms <= 0 -> bad
        ms < 300 -> good
        ms < 800 -> mid
        else -> bad
    }
}

enum class BentoToneKind { Stage, Lime, Blue, Violet, Cyan, Pink, Coral, Surface }

object BentoPalettes {
    val BrandDark = BentoColors(
        dark = true,
        ground = Color(0xFF0B0A10),
        surface = Color(0xFF18161F),
        surface2 = Color(0xFF24212D),
        line = Color(0xFF3A3646),
        ink = Color(0xFFEEEAF6),
        muted = Color(0xFFA6A0B8),
        stage = Color(0xFF311B6B),
        onStage = Color(0xFFEFE8FF),
        chipOnStage = Color(0x1FFFFFFF),
        goodOnStage = Color(0xFFFFB59D),
        lime = Color(0xFFFF5A4E),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF15294A), Color(0xFFCFE1FF)),
        violet = BentoTone(Color(0xFF2A2352), Color(0xFFDDD5FF)),
        cyan = BentoTone(Color(0xFF3D1210), Color(0xFFFFC9BE)),
        pink = BentoTone(Color(0xFF4A1C35), Color(0xFFFFD0E5)),
        coral = BentoTone(Color(0xFF4A251D), Color(0xFFFFD3C6)),
        good = Color(0xFFFF9E8A),
        mid = Color(0xFFF2C14E),
        bad = Color(0xFFFF8A80),
        selected = Color(0xFF2B1D57),
        dock = Color(0xFF1E1B27),
        dockLine = Color(0x24FFFFFF),
        danger = Color(0xFFFFB4AB),
        onDanger = Color(0xFF690005),
    )

    val BrandLight = BentoColors(
        dark = false,
        ground = Color(0xFFF6F6F0),
        surface = Color(0xFFEAEAE2),
        surface2 = Color(0xFFDCDCD2),
        line = Color(0xFFC5C5BA),
        ink = Color(0xFF16161C),
        muted = Color(0xFF55555F),
        stage = Color(0xFF2A1361),
        onStage = Color(0xFFF1EBFF),
        chipOnStage = Color(0x24FFFFFF),
        goodOnStage = Color(0xFFFFB59D),
        lime = Color(0xFFFF6B5E),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFD4E4FF), Color(0xFF123A70)),
        violet = BentoTone(Color(0xFFE3DBFF), Color(0xFF36268A)),
        cyan = BentoTone(Color(0xFFF6D7D0), Color(0xFF7A1F14)),
        pink = BentoTone(Color(0xFFFFD6E7), Color(0xFF6E1543)),
        coral = BentoTone(Color(0xFFFFDCCD), Color(0xFF6D2914)),
        good = Color(0xFFC93A22),
        mid = Color(0xFF8A5A00),
        bad = Color(0xFFB3261E),
        selected = Color(0xFFE3DBFF),
        dock = Color(0xFFFFFFFF),
        dockLine = Color(0x1A16161C),
        danger = Color(0xFFFFDAD6),
        onDanger = Color(0xFF410002),
    )

    /** Zed's lime-yellow "Citrus", converted wholesale to red. */
    val CitrusDark = BentoColors(
        dark = true,
        ground = Color(0xFF0D0807),
        surface = Color(0xFF1A100E),
        surface2 = Color(0xFF261916),
        line = Color(0xFF3D2A24),
        ink = Color(0xFFF5EDE6),
        muted = Color(0xFFB8A298),
        stage = Color(0xFF4A1408),
        onStage = Color(0xFFFFE8DC),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF5A3C),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF3B1E08), Color(0xFFFFD9A1)),
        violet = BentoTone(Color(0xFF3A1210), Color(0xFFFFC4B5)),
        cyan = BentoTone(Color(0xFF371510), Color(0xFFFFC9B8)),
        pink = BentoTone(Color(0xFF40280C), Color(0xFFFFD6A6)),
        coral = BentoTone(Color(0xFF43201A), Color(0xFFFFC8BC)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF4A1E12),
        dock = Color(0xFF261916),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    /** Zed's lime-yellow "Citrus", converted wholesale to red. */
    val CitrusLight = BentoColors(
        dark = false,
        ground = Color(0xFFFBF3EE),
        surface = Color(0xFFF3E4DC),
        surface2 = Color(0xFFE8D2C8),
        line = Color(0xFFCDB4A8),
        ink = Color(0xFF1A100C),
        muted = Color(0xFF5F4A42),
        stage = Color(0xFF4A1408),
        onStage = Color(0xFFFFE8DC),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF6B57),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFFFE3C2), Color(0xFF6A3410)),
        violet = BentoTone(Color(0xFFFFD9CF), Color(0xFF7A2014)),
        cyan = BentoTone(Color(0xFFFFDED2), Color(0xFF7A2812)),
        pink = BentoTone(Color(0xFFFFE0BE), Color(0xFF6A3A06)),
        coral = BentoTone(Color(0xFFFFD9D0), Color(0xFF6E2414)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5D5C8),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** Zed's green "Matcha", converted to a red variant. */
    val MatchaDark = BentoColors(
        dark = true,
        ground = Color(0xFF0D0A09),
        surface = Color(0xFF1A1514),
        surface2 = Color(0xFF271E1B),
        line = Color(0xFF40322E),
        ink = Color(0xFFF2E9E4),
        muted = Color(0xFFB3A29A),
        stage = Color(0xFF4A1610),
        onStage = Color(0xFFFFE2D8),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF7A5C),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF33201A), Color(0xFFF5D9CC)),
        violet = BentoTone(Color(0xFF341C14), Color(0xFFF5DCC8)),
        cyan = BentoTone(Color(0xFF3D1210), Color(0xFFFFC9BE)),
        pink = BentoTone(Color(0xFF3D2619), Color(0xFFF4CDB6)),
        coral = BentoTone(Color(0xFF36301E), Color(0xFFF2E6C2)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF452218),
        dock = Color(0xFF271E1B),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    /** Zed's green "Matcha", converted to a red variant. */
    val MatchaLight = BentoColors(
        dark = false,
        ground = Color(0xFFFAF4F1),
        surface = Color(0xFFF2E6E0),
        surface2 = Color(0xFFE8D8D0),
        line = Color(0xFFCDBBB2),
        ink = Color(0xFF1A1210),
        muted = Color(0xFF594A44),
        stage = Color(0xFF4A1610),
        onStage = Color(0xFFFFE2D8),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF7A66),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFF5DCD2), Color(0xFF6E2A18)),
        violet = BentoTone(Color(0xFFF5E3D0), Color(0xFF6A3414)),
        cyan = BentoTone(Color(0xFFF6D9D2), Color(0xFF7A1F14)),
        pink = BentoTone(Color(0xFFF7DCCB), Color(0xFF6A3415)),
        coral = BentoTone(Color(0xFFF3EAC9), Color(0xFF5A4A14)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5D9CE),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val NeonDark = BentoColors(
        dark = true,
        ground = Color(0xFF08070C),
        surface = Color(0xFF15131C),
        surface2 = Color(0xFF201D2A),
        line = Color(0xFF383346),
        ink = Color(0xFFF2EEFA),
        muted = Color(0xFFABA3BF),
        stage = Color(0xFF3D0B4F),
        onStage = Color(0xFFFBDDFF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF4D42),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF4A0E3B), Color(0xFFFFC1EA)),
        violet = BentoTone(Color(0xFF07344A), Color(0xFFA3EAFF)),
        cyan = BentoTone(Color(0xFF2B1657), Color(0xFFE1D0FF)),
        pink = BentoTone(Color(0xFF3A2408), Color(0xFFFFE9A0)),
        coral = BentoTone(Color(0xFF4A1810), Color(0xFFFFC5B5)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF4A1060),
        dock = Color(0xFF201D2A),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val NeonLight = BentoColors(
        dark = false,
        ground = Color(0xFFF8F6FB),
        surface = Color(0xFFECE8F2),
        surface2 = Color(0xFFDED8E8),
        line = Color(0xFFC5BDD3),
        ink = Color(0xFF15121C),
        muted = Color(0xFF554C66),
        stage = Color(0xFF3D0B4F),
        onStage = Color(0xFFFCE2FF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF6A55),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFFFD6F1), Color(0xFF6E0F55)),
        violet = BentoTone(Color(0xFFC8F1FF), Color(0xFF00475E)),
        cyan = BentoTone(Color(0xFFE7DBFF), Color(0xFF3D1F80)),
        pink = BentoTone(Color(0xFFFFEFC2), Color(0xFF6A4A00)),
        coral = BentoTone(Color(0xFFFFDCD2), Color(0xFF741D0A)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF1DDF7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** Zed's green "Pistachio", converted to a red variant. */
    val PistachioDark = BentoColors(
        dark = true,
        ground = Color(0xFF0B0C0A),
        surface = Color(0xFF171915),
        surface2 = Color(0xFF22251F),
        line = Color(0xFF393D34),
        ink = Color(0xFFEEF1E9),
        muted = Color(0xFFA8AE9F),
        stage = Color(0xFF2B2348),
        onStage = Color(0xFFE9E2FF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF8A75),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF34241C), Color(0xFFF5DFCE)),
        violet = BentoTone(Color(0xFF2C2547), Color(0xFFE2DAFF)),
        cyan = BentoTone(Color(0xFF3D1210), Color(0xFFFFC9BE)),
        pink = BentoTone(Color(0xFF43242E), Color(0xFFFFD6E1)),
        coral = BentoTone(Color(0xFF3A3420), Color(0xFFF7EBC4)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF352C57),
        dock = Color(0xFF22251F),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    /** Zed's green "Pistachio", converted to a red variant. */
    val PistachioLight = BentoColors(
        dark = false,
        ground = Color(0xFFF6F7F2),
        surface = Color(0xFFEAECE3),
        surface2 = Color(0xFFDDE0D4),
        line = Color(0xFFC4C8BA),
        ink = Color(0xFF151713),
        muted = Color(0xFF535849),
        stage = Color(0xFF2B2348),
        onStage = Color(0xFFEEE8FF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF8A75),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFF5E4D4), Color(0xFF6A3A1A)),
        violet = BentoTone(Color(0xFFE7E0FF), Color(0xFF3A2C7A)),
        cyan = BentoTone(Color(0xFFF6D9D2), Color(0xFF7A1F14)),
        pink = BentoTone(Color(0xFFFFDCE6), Color(0xFF6E2338)),
        coral = BentoTone(Color(0xFFF7EDCC), Color(0xFF5A4614)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFE3DDF7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** Zed's "Ocean": blue family kept; its mint accent becomes red. */
    val OceanDark = BentoColors(
        dark = true,
        ground = Color(0xFF070B10),
        surface = Color(0xFF121923),
        surface2 = Color(0xFF1C2431),
        line = Color(0xFF313D4F),
        ink = Color(0xFFE7EEF8),
        muted = Color(0xFF9FAABD),
        stage = Color(0xFF0E2F57),
        onStage = Color(0xFFD8E9FF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF6A5E),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF10304F), Color(0xFFC4E2FF)),
        violet = BentoTone(Color(0xFF1E2357), Color(0xFFD6D9FF)),
        cyan = BentoTone(Color(0xFF3D1210), Color(0xFFFFC9BE)),
        pink = BentoTone(Color(0xFF0A3340), Color(0xFFA6E8FF)),
        coral = BentoTone(Color(0xFF38301F), Color(0xFFF2E2BA)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF123A6A),
        dock = Color(0xFF1C2431),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    /** Zed's "Ocean": blue family kept; its mint accent becomes red. */
    val OceanLight = BentoColors(
        dark = false,
        ground = Color(0xFFF3F7FB),
        surface = Color(0xFFE4EBF3),
        surface2 = Color(0xFFD5DEE9),
        line = Color(0xFFB9C5D4),
        ink = Color(0xFF0F1620),
        muted = Color(0xFF4A5768),
        stage = Color(0xFF0E2F57),
        onStage = Color(0xFFDDEBFF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF7A66),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFD2E6FF), Color(0xFF0F3B6E)),
        violet = BentoTone(Color(0xFFDCDEFF), Color(0xFF262E80)),
        cyan = BentoTone(Color(0xFFF6D7D0), Color(0xFF7A1F14)),
        pink = BentoTone(Color(0xFFC6ECF7), Color(0xFF064559)),
        coral = BentoTone(Color(0xFFF3E8CC), Color(0xFF5A4614)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFD6E5F7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** Zed's "Sunset": amber accent warmed toward red-orange. */
    val SunsetDark = BentoColors(
        dark = true,
        ground = Color(0xFF0E0A09),
        surface = Color(0xFF1C1513),
        surface2 = Color(0xFF28201D),
        line = Color(0xFF43362F),
        ink = Color(0xFFF8EDE8),
        muted = Color(0xFFBBA79E),
        stage = Color(0xFF4A1F12),
        onStage = Color(0xFFFFE3D6),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF8A3D),
        onLime = Color(0xFF2E1800),
        blue = BentoTone(Color(0xFF4A2A16), Color(0xFFFFD8B8)),
        violet = BentoTone(Color(0xFF2E1A40), Color(0xFFE6D2FF)),
        cyan = BentoTone(Color(0xFF3F3110), Color(0xFFFFE7A3)),
        pink = BentoTone(Color(0xFF4A1A2A), Color(0xFFFFC6D9)),
        coral = BentoTone(Color(0xFF4A1F1A), Color(0xFFFFC4B8)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF5A2A18),
        dock = Color(0xFF28201D),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    /** Zed's "Sunset": amber accent warmed toward red-orange. */
    val SunsetLight = BentoColors(
        dark = false,
        ground = Color(0xFFFBF6F3),
        surface = Color(0xFFF1E8E3),
        surface2 = Color(0xFFE5D9D2),
        line = Color(0xFFCDBDB4),
        ink = Color(0xFF1C1310),
        muted = Color(0xFF64514A),
        stage = Color(0xFF4A1F12),
        onStage = Color(0xFFFFE6DA),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF7A3D),
        onLime = Color(0xFF2E1800),
        blue = BentoTone(Color(0xFFFFE2C9), Color(0xFF6A3A10)),
        violet = BentoTone(Color(0xFFEBDDFF), Color(0xFF4A2A70)),
        cyan = BentoTone(Color(0xFFFFEDB8), Color(0xFF5A4300)),
        pink = BentoTone(Color(0xFFFFD9E5), Color(0xFF6E1A38)),
        coral = BentoTone(Color(0xFFFFDAD0), Color(0xFF742214)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5DCD0),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val BerryDark = BentoColors(
        dark = true,
        ground = Color(0xFF0D080C),
        surface = Color(0xFF1B1219),
        surface2 = Color(0xFF271B24),
        line = Color(0xFF42303D),
        ink = Color(0xFFF7EAF2),
        muted = Color(0xFFB8A2B0),
        stage = Color(0xFF4A0E3A),
        onStage = Color(0xFFFFD9F2),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFF8A75),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFF4A1230), Color(0xFFFFC6E0)),
        violet = BentoTone(Color(0xFF341B4A), Color(0xFFEBD3FF)),
        cyan = BentoTone(Color(0xFF3A1214), Color(0xFFFFC4CE)),
        pink = BentoTone(Color(0xFF4A1C24), Color(0xFFFFCCD5)),
        coral = BentoTone(Color(0xFF46281A), Color(0xFFFFD9C2)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF5A1548),
        dock = Color(0xFF271B24),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val BerryLight = BentoColors(
        dark = false,
        ground = Color(0xFFFBF5F8),
        surface = Color(0xFFF1E6EC),
        surface2 = Color(0xFFE5D6DF),
        line = Color(0xFFCDB9C5),
        ink = Color(0xFF1C1018),
        muted = Color(0xFF634C5A),
        stage = Color(0xFF4A0E3A),
        onStage = Color(0xFFFFDDF3),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFF8A75),
        onLime = Color(0xFF2A0500),
        blue = BentoTone(Color(0xFFFFD7E9), Color(0xFF6E1240)),
        violet = BentoTone(Color(0xFFEEDDFF), Color(0xFF4F2A72)),
        cyan = BentoTone(Color(0xFFF6D7DC), Color(0xFF7A1F28)),
        pink = BentoTone(Color(0xFFFFD9E0), Color(0xFF6E1C2C)),
        coral = BentoTone(Color(0xFFFFE0CE), Color(0xFF6A3414)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5D8E9),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** Palette keys accepted by [of], in picker order. */
    val KEYS: List<String> = listOf(
        "Brand", "Citrus", "Matcha", "Neon", "Pistachio", "Ocean", "Sunset", "Berry",
    )

    /** Human-facing name for a palette key (Citrus is red in Damavand). */
    fun displayName(key: String): String = when (key.lowercase()) {
        "citrus" -> "Citrus Red"
        "matcha" -> "Matcha Red"
        "pistachio" -> "Pistachio Red"
        else -> key
    }

    /**
     * Name-based lookup (replaces Zed's HCT-driven `of(palette, dark, seed,
     * amoled)` — no materialkolor in Damavand). Unknown names fall back to
     * Brand. [amoled] flattens a dark palette onto pure black, as in Zed.
     */
    fun of(name: String, dark: Boolean, amoled: Boolean = false): BentoColors {
        val base = when (name.lowercase()) {
            "citrus", "citrus red" -> if (dark) CitrusDark else CitrusLight
            "matcha", "matcha red" -> if (dark) MatchaDark else MatchaLight
            "neon" -> if (dark) NeonDark else NeonLight
            "pistachio", "pistachio red" -> if (dark) PistachioDark else PistachioLight
            "ocean" -> if (dark) OceanDark else OceanLight
            "sunset" -> if (dark) SunsetDark else SunsetLight
            "berry" -> if (dark) BerryDark else BerryLight
            else -> if (dark) BrandDark else BrandLight
        }
        return if (amoled && dark) base.amoled() else base
    }

    private fun BentoColors.amoled(): BentoColors = copy(
        ground = Color.Black,
        surface = Color(0xFF111015),
        surface2 = Color(0xFF1B1A21),
        dock = Color(0xFF141319),
    )
}
