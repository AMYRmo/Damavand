package com.amyrmo.damavand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * The Damavand v2 theme root. Dark is the default-first scheme;
 * [amoled] swaps in the pure-black variant; a full light scheme ships
 * alongside. Beyond the M3 scheme this provides the design-system
 * composition locals every component reads instead of probing the
 * platform: [LocalMotionBudget], [LocalGlassCapabilities],
 * [LocalPersonalization] and [LocalThemeProfile].
 *
 * Profile wiring (ZedSecure theme profiles, red-remapped — see
 * ThemeProfiles.kt): when [profileId] resolves via [themeProfileOf], the
 * profile drives [LocalPersonalization] (unless [personalization] is given
 * explicitly) and tints the M3 scheme — primary = profile connect colour,
 * secondary = profile upload, tertiary = profile download, each with a
 * [readableOn] foreground. A profile flagged `amoled` also applies the
 * [amoled] surface treatment in dark theme. With no profile, behaviour is
 * exactly the pre-profile theme (red default scheme, default locals).
 *
 * @param persian selects the Vazirmatn type scale with raised line heights.
 * @param reduceTransparency the Appearance "Reduce transparency" setting —
 * forces the opaque rung of the glass fallback ladder.
 */
@Composable
fun DamavandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    persian: Boolean = false,
    reduceTransparency: Boolean = false,
    profileId: String? = null,
    personalization: Personalization? = null,
    content: @Composable () -> Unit,
) {
    val profile = remember(profileId) { themeProfileOf(profileId) }
    val effectiveProfile = profile ?: DamavandDefaultProfile
    val effectivePersonalization = personalization
        ?: remember(profile) { profile?.toPersonalization() }
        ?: Personalization.Default

    val baseScheme = when {
        darkTheme && amoled -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (profile == null) {
        baseScheme
    } else {
        val connect = Color(profile.connect)
        val upload = Color(profile.upload)
        val download = Color(profile.download)
        val tinted = baseScheme.copy(
            primary = connect,
            onPrimary = connect.readableOn(),
            secondary = upload,
            onSecondary = upload.readableOn(),
            tertiary = download,
            onTertiary = download.readableOn(),
        )
        if (profile.amoled) tinted.amoled(darkTheme) else tinted
    }
    val motionBudget = rememberMotionBudget()
    val glassCapabilities = rememberGlassCapabilities(reduceTransparency)

    CompositionLocalProvider(
        LocalMotionBudget provides motionBudget,
        LocalGlassCapabilities provides glassCapabilities,
        LocalPersonalization provides effectivePersonalization,
        LocalThemeProfile provides effectiveProfile,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = damavandTypography(persian),
            shapes = DamavandShapes,
            content = content,
        )
    }
}
