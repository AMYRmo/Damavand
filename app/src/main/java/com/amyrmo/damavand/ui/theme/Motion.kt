package com.amyrmo.damavand.ui.theme

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The single motion gate for the whole app (architecture §5.3;
 * design-research §4.3 — Narcic's `MotionBudget`, adopted).
 *
 * Every animation in the design system consults [LocalMotionBudget]:
 * state-colour transitions run ~900 ms at [Full], halve at [Throttled]
 * (battery saver), and disappear at [Paused] (system animator scale 0 /
 * reduce-motion). There is no always-on decorative animation on Home —
 * Damavand's battery history bans it — and the glass specular/glow never
 * animates at [Paused].
 */
enum class MotionBudget {
    Full,
    Throttled,
    Paused,
    ;

    /** Scales a nominal duration: Full = as-is, Throttled = half, Paused = 0 (snap). */
    fun scaledDuration(fullMs: Int): Int = when (this) {
        Full -> fullMs
        Throttled -> fullMs / 2
        Paused -> 0
    }

    /** Press feedback scale; 1f (no movement) when paused. */
    fun pressScale(pressed: Boolean): Float = when {
        !pressed -> 1f
        this == Paused -> 1f
        else -> 0.97f
    }
}

object MotionDurations {
    /** State-colour transitions (connect pill, status) — ~900 ms per spec. */
    const val StateColorMs: Int = 900

    /** Standard content transitions (scaffold AnimatedContent, indicators). */
    const val StandardMs: Int = 300

    /** Dock indicator spring is duration-free; this is its fade companion. */
    const val DockFadeMs: Int = 200
}

val LocalMotionBudget = staticCompositionLocalOf { MotionBudget.Full }

/**
 * Derives the budget from system signals: animator duration scale 0 →
 * [MotionBudget.Paused]; power-save mode → [MotionBudget.Throttled];
 * otherwise [MotionBudget.Full]. Read once per composition root — the
 * signals change rarely and a stale budget is harmless.
 */
@Composable
fun rememberMotionBudget(): MotionBudget {
    val context = LocalContext.current
    return remember {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
        when {
            animatorScale == 0f -> MotionBudget.Paused
            powerSave -> MotionBudget.Throttled
            else -> MotionBudget.Full
        }
    }
}
