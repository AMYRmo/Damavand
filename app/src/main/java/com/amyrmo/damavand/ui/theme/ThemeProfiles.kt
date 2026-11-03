package com.amyrmo.damavand.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * Green→red remap: every colour whose hue sat in the green/teal/lime band
 * (~100°–190°) is rotated into the red band (~350°–10°), preserving
 * saturation/lightness. ZedSecure's default "aurora" profile becomes
 * "damavand"; the lime/green profiles (citrus, neonlime, moss) become red
 * variants; profiles with no green primaries (ember, midnight, sakura,
 * mono, tokyo, solar, clay) are carried over verbatim. The full old→new
 * table is in the comments on each remapped profile.
 *
 * ZedSecure's `AppSettings.applyThemeProfile` is NOT ported (v2's
 * AppSettings differs); [ThemeProfile.toPersonalization] is the v2 bridge.
 */
data class ThemeProfile(
    val id: String,

    val name: String,

    val accentIndex: Int,
    val amoled: Boolean = false,
    val download: Long,
    val upload: Long,
    val downloadTile: Long,
    val uploadTile: Long,
    val connect: Long,
    val connected: Long,
    val tapHint: Long,
    val serverCard: Long,
    val serverActive: Long,
    val configCard: Long,
    val configCardText: Long,
) {
    val swatches: List<Color>
        get() = listOf(Color(connect), Color(download), Color(upload))
}

val ThemeProfiles: List<ThemeProfile> = listOf(
    // Was ZedSecure "aurora" (id "aurora", name "Aurora"):
    //   connect      0xFF12A594 → 0xFFE5484D (Damavand red)
    //   serverCard   0xFF11302E → 0xFF331114 (dark red, equal depth)
    //   serverActive 0xFF12A594 → 0xFFE5484D
    //   configCard   0xFF12332F → 0xFF361216 (dark red)
    //   configCardText 0xFFEAFFFB → 0xFFFFF0F0 (warm white)
    //   tapHint      0xFFA9E9E2 → 0xFFF5B8BC (light rose)
    //   download     0xFF4FE3D8 → 0xFFFF6B5E (teal → warm red)
    //   downloadTile 0xFF0D3B37 → 0xFF3B0D10 (dark red)
    //   connected    0xFF7C5CFF, upload 0xFFB49BFF, uploadTile 0xFF241A48
    //   unchanged (violet family, no green). accentIndex 2 now resolves to
    //   the "Scarlet" preset (the remapped "Teal" slot).
    ThemeProfile(
        id = "damavand",
        name = "Damavand",
        accentIndex = 2,
        download = 0xFFFF6B5E,
        upload = 0xFFB49BFF,
        downloadTile = 0xFF3B0D10,
        uploadTile = 0xFF241A48,
        connect = 0xFFE5484D,
        connected = 0xFF7C5CFF,
        tapHint = 0xFFF5B8BC,
        serverCard = 0xFF331114,
        serverActive = 0xFFE5484D,
        configCard = 0xFF361216,
        configCardText = 0xFFFFF0F0,
    ),
    // Ember: orange/red family, no green — verbatim from ZedSecure.
    ThemeProfile(
        id = "ember",
        name = "Ember",
        accentIndex = 4,
        download = 0xFFFFB870,
        upload = 0xFFFF8A65,
        downloadTile = 0xFF3D2409,
        uploadTile = 0xFF43200F,
        connect = 0xFFE07A1F,
        connected = 0xFFC0392B,
        tapHint = 0xFFFFD9AE,
        serverCard = 0xFF33200C,
        serverActive = 0xFFE07A1F,
        configCard = 0xFF3A2410,
        configCardText = 0xFFFFF1E2,
    ),
    // Midnight: blue family (hue ~220°, outside the remap band) — verbatim.
    ThemeProfile(
        id = "midnight",
        name = "Midnight",
        accentIndex = 1,

        amoled = true,
        download = 0xFF7FB2FF,
        upload = 0xFF9BC4FF,
        downloadTile = 0xFF0B1730,
        uploadTile = 0xFF101B36,
        connect = 0xFF2563EB,
        connected = 0xFF1E3A8A,
        tapHint = 0xFFA9C6F5,
        serverCard = 0xFF0C1526,
        serverActive = 0xFF2563EB,
        configCard = 0xFF0D1932,
        configCardText = 0xFFE6EEFF,
    ),
    // Sakura: pink family, no green — verbatim.
    ThemeProfile(
        id = "sakura",
        name = "Sakura",
        accentIndex = 5,
        download = 0xFFFFA8C5,
        upload = 0xFFFFC2D6,
        downloadTile = 0xFF3E1226,
        uploadTile = 0xFF45182C,
        connect = 0xFFD9407A,
        connected = 0xFF8E2751,
        tapHint = 0xFFFFCFE0,
        serverCard = 0xFF351023,
        serverActive = 0xFFD9407A,
        configCard = 0xFF3B1428,
        configCardText = 0xFFFFF0F5,
    ),
    // Was "Citrus" (lime-based). Lime→red rotation:
    //   download     0xFFC7F24E → 0xFFFF5A4E
    //   upload       0xFF8FD14F → 0xFFE5484D
    //   downloadTile 0xFF243007 → 0xFF30070A
    //   uploadTile   0xFF1E2A0A → 0xFF2A0A0C
    //   connect      0xFF9BC91F → 0xFFC91F2E
    //   connected    0xFF3F6212 → 0xFF7A1622
    //   tapHint      0xFFDDF59A → 0xFFF5B0B3
    //   serverCard   0xFF1E2708 → 0xFF27080B
    //   serverActive 0xFF9BC91F → 0xFFC91F2E
    //   configCard   0xFF232E09 → 0xFF2E0910
    //   configCardText 0xFFF2FFD6 → 0xFFFFF0F0
    // accentIndex 3 now resolves to "Bordeaux" (the remapped "Forest" slot).
    ThemeProfile(
        id = "citrus",
        name = "Citrus Red",
        accentIndex = 3,

        download = 0xFFFF5A4E,
        upload = 0xFFE5484D,
        downloadTile = 0xFF30070A,
        uploadTile = 0xFF2A0A0C,
        connect = 0xFFC91F2E,
        connected = 0xFF7A1622,
        tapHint = 0xFFF5B0B3,
        serverCard = 0xFF27080B,
        serverActive = 0xFFC91F2E,
        configCard = 0xFF2E0910,
        configCardText = 0xFFFFF0F0,
    ),
    // Mono: neutral greys, no green — verbatim.
    ThemeProfile(
        id = "mono",
        name = "Mono",
        accentIndex = 9,
        download = 0xFFE4E4E7,
        upload = 0xFFB4B4BB,
        downloadTile = 0xFF1C1C1E,
        uploadTile = 0xFF232326,
        connect = 0xFF3F3F46,
        connected = 0xFF71717A,
        tapHint = 0xFFC7C7CC,
        serverCard = 0xFF1A1A1C,
        serverActive = 0xFF3F3F46,
        configCard = 0xFF1D1D20,
        configCardText = 0xFFF2F2F7,
    ),
    // Was "Neon Lime". Neon lime→neon red rotation:
    //   download     0xFFD4FF3F → 0xFFFF3F4E
    //   upload       0xFF9EE84A → 0xFFFF6B5E
    //   downloadTile 0xFF0A0F00 → 0xFF0F0003
    //   uploadTile   0xFF0C1200 → 0xFF120003
    //   connect      0xFFC7F24E → 0xFFFF5A4E
    //   connected    0xFF4A7C0F → 0xFFC91F2E
    //   tapHint      0xFFDFFF9E → 0xFFFFB3AB
    //   serverCard   0xFF080B00 → 0xFF0B0002
    //   serverActive 0xFFC7F24E → 0xFFFF5A4E
    //   configCard   0xFF0B1000 → 0xFF100002
    //   configCardText 0xFFEBFFC2 → 0xFFFFE0DE
    ThemeProfile(
        id = "neonlime",
        name = "Neon Red",
        accentIndex = 3,

        amoled = true,
        download = 0xFFFF3F4E,
        upload = 0xFFFF6B5E,
        downloadTile = 0xFF0F0003,
        uploadTile = 0xFF120003,
        connect = 0xFFFF5A4E,
        connected = 0xFFC91F2E,
        tapHint = 0xFFFFB3AB,
        serverCard = 0xFF0B0002,
        serverActive = 0xFFFF5A4E,
        configCard = 0xFF100002,
        configCardText = 0xFFFFE0DE,
    ),
    // Tokyo: blue/violet family, no green — verbatim.
    ThemeProfile(
        id = "tokyo",
        name = "Tokyo",
        accentIndex = 1,

        amoled = true,
        download = 0xFF7AA2F7,
        upload = 0xFFBB9AF7,
        downloadTile = 0xFF0B0D17,
        uploadTile = 0xFF11101F,
        connect = 0xFF7AA2F7,
        connected = 0xFFBB9AF7,
        tapHint = 0xFF9AA5CE,
        serverCard = 0xFF090B14,
        serverActive = 0xFF7AA2F7,
        configCard = 0xFF0C0F1A,
        configCardText = 0xFFC0CAF5,
    ),
    // Solar: yellow/amber family (hue ~45°, outside the remap band) — verbatim.
    ThemeProfile(
        id = "solar",
        name = "Solar",
        accentIndex = 4,

        download = 0xFFFFD54F,
        upload = 0xFFFFB300,
        downloadTile = 0xFF3A2E05,
        uploadTile = 0xFF33280A,
        connect = 0xFFFFC107,
        connected = 0xFF8D6E00,
        tapHint = 0xFFFFE9A8,
        serverCard = 0xFF2E2506,
        serverActive = 0xFFFFC107,
        configCard = 0xFF352A07,
        configCardText = 0xFFFFF6DA,
    ),
    // Was "Moss" (green). Green→deep-red rotation:
    //   download     0xFF6EE7A8 → 0xFFFF9E8A
    //   upload       0xFF34D399 → 0xFFE5484D
    //   downloadTile 0xFF07231A → 0xFF23070A
    //   uploadTile   0xFF0A2A20 → 0xFF2A0A0E
    //   connect      0xFF10B981 → 0xFFC0362C
    //   connected    0xFF065F46 → 0xFF8E2323
    //   tapHint      0xFFA7F3D0 → 0xFFF5C6C0
    //   serverCard   0xFF061D16 → 0xFF1D0609
    //   serverActive 0xFF10B981 → 0xFFC0362C
    //   configCard   0xFF08251C → 0xFF25080C
    //   configCardText 0xFFE3FFF4 → 0xFFFFEDE8
    ThemeProfile(
        id = "moss",
        name = "Moss Red",
        accentIndex = 3,

        download = 0xFFFF9E8A,
        upload = 0xFFE5484D,
        downloadTile = 0xFF23070A,
        uploadTile = 0xFF2A0A0E,
        connect = 0xFFC0362C,
        connected = 0xFF8E2323,
        tapHint = 0xFFF5C6C0,
        serverCard = 0xFF1D0609,
        serverActive = 0xFFC0362C,
        configCard = 0xFF25080C,
        configCardText = 0xFFFFEDE8,
    ),
    // Clay: terracotta family, no green — verbatim.
    ThemeProfile(
        id = "clay",
        name = "Clay",
        accentIndex = 6,

        download = 0xFFE0A08A,
        upload = 0xFFD97757,
        downloadTile = 0xFF33201A,
        uploadTile = 0xFF3A231B,
        connect = 0xFFD97757,
        connected = 0xFF8C4A34,
        tapHint = 0xFFF0C8B6,
        serverCard = 0xFF2B1B15,
        serverActive = 0xFFD97757,
        configCard = 0xFF32201A,
        configCardText = 0xFFFFEDE3,
    ),
)

/** The default profile: "damavand" (ZedSecure's "aurora", remapped to red). */
val DamavandDefaultProfile: ThemeProfile = ThemeProfiles.first()

fun themeProfileOf(id: String?): ThemeProfile? =
    id?.let { wanted -> ThemeProfiles.firstOrNull { it.id == wanted } }

/**
 * The v2 bridge from a profile to [Personalization] (replaces ZedSecure's
 * AppSettings-based converters, which do not apply to v2's settings model).
 * Only the colour slots are carried; corner/density/traffic options keep
 * their [Personalization] defaults.
 */
fun ThemeProfile.toPersonalization(): Personalization = Personalization(
    downloadColor = Color(download),
    uploadColor = Color(upload),
    downloadTileColor = Color(downloadTile),
    uploadTileColor = Color(uploadTile),
    connectColor = Color(connect),
    connectedColor = Color(connected),
    tapHintColor = Color(tapHint),
    configCardColor = Color(configCard),
    configCardTextColor = Color(configCardText),
    serverCardColor = Color(serverCard),
    serverActiveColor = Color(serverActive),
)
