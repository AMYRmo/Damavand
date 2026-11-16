package com.amyrmo.damavand.lite.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.amyrmo.damavand.lite.R

/** Damavand Lite — day & night tokens (3.2.5). Damavand Red stays the
 *  ONLY accent in both themes. [dark] is state, so every reader
 *  recomposes when the theme changes. */
object LiteColors {
    var dark by mutableStateOf(true)
        private set

    // The property setter would clash with this name on the JVM
    // (both would be setDark(Z)V), so the function is applyDark.
    fun applyDark(isDark: Boolean) { dark = isDark }

    // 3.2.9 (PattNG dynamicColor, opt-in): when on (Android 12+), the
    // system wallpaper palette replaces the fixed tokens below. The
    // dynamic values are state too, so the UI recomposes on change;
    // null means "use the fixed tokens" (dynamic off or unavailable).
    var dynamic by mutableStateOf(false)
        private set
    fun applyDynamic(on: Boolean) { dynamic = on }

    private var dynamicAccent: Color? by mutableStateOf(null)
    private var dynamicBg: Color? by mutableStateOf(null)
    private var dynamicSurface: Color? by mutableStateOf(null)
    private var dynamicSurface2: Color? by mutableStateOf(null)
    private var dynamicText: Color? by mutableStateOf(null)
    private var dynamicTextSecondary: Color? by mutableStateOf(null)

    fun applyDynamicScheme(
        accent: Color?, bg: Color?, surface: Color?,
        surface2: Color?, text: Color?, textSecondary: Color?,
    ) {
        dynamicAccent = accent; dynamicBg = bg; dynamicSurface = surface
        dynamicSurface2 = surface2; dynamicText = text
        dynamicTextSecondary = textSecondary
    }

    val bg: Color get() = if (dynamic && dynamicBg != null) dynamicBg!!
        else if (dark) Color(0xFF0E0F11) else Color(0xFFF2F3F5)
    val surface: Color get() = if (dynamic && dynamicSurface != null) dynamicSurface!!
        else if (dark) Color(0xFF1A1C1F) else Color(0xFFFFFFFF)
    val surface2: Color get() = if (dynamic && dynamicSurface2 != null) dynamicSurface2!!
        else if (dark) Color(0xFF212327) else Color(0xFFE9EAED)
    val hairline: Color get() = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val hairlineStrong: Color get() = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.14f)
    val text: Color get() = if (dynamic && dynamicText != null) dynamicText!!
        else if (dark) Color(0xFFF2F3F5) else Color(0xFF141518)
    val textSecondary: Color get() = if (dynamic && dynamicTextSecondary != null) dynamicTextSecondary!!
        else if (dark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val textMuted: Color get() = if (dark) Color(0xFF6B7280) else Color(0xFF8A8F98)
    // Damavand Red — the ONLY accent (unless dynamic color is on).
    val accent: Color get() = if (dynamic && dynamicAccent != null) dynamicAccent!!
        else Color(0xFFE5322D)
    val green: Color get() = if (dark) Color(0xFF34C77B) else Color(0xFF1E8E4F)
    val error: Color get() = if (dark) Color(0xFFE5484D) else Color(0xFFD92D20)
    val amber: Color get() = if (dark) Color(0xFFD8A13A) else Color(0xFFB7791F)
    val glass: Color get() = if (dark) Color(0x8C1A1C1F) else Color(0x8CFFFFFF)
    val bottomBar: Color get() = if (dark) Color(0xEB0E0F11) else Color(0xEBF2F3F5)
}

/** Resolve a stored theme mode ("system"/"dark"/"light") to dark or not. */
fun resolveDark(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

/** True when the given context is currently in night mode. */
fun isSystemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/** Vazirmatn, bundled (Google Fonts may fail on Iranian networks). */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Type scale (design spec §2.2): Display 44/800, Title 20/800, Section 16/700. */
object LiteType {
    const val DISPLAY_SIZE = 44
    const val TITLE_SIZE = 20
    const val SECTION_SIZE = 16
    const val ROW_TITLE_SIZE = 14.5f
    const val BODY_SIZE = 14
    const val LABEL_SIZE = 12
    const val CAPTION_SIZE = 11
}
