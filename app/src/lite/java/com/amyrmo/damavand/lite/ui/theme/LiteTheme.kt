package com.amyrmo.damavand.lite.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.amyrmo.damavand.lite.R

/** Damavand Lite — fixed dark-only tokens (design spec §2). No light mode, no dynamic colour. */
object LiteColors {
    val bg = Color(0xFF0E0F11)
    val surface = Color(0xFF1A1C1F)
    val surface2 = Color(0xFF212327)
    val hairline = Color.White.copy(alpha = 0.08f)
    val hairlineStrong = Color.White.copy(alpha = 0.14f)
    val text = Color(0xFFF2F3F5)
    val textSecondary = Color(0xFF9AA0A6)
    val textMuted = Color(0xFF6B7280)
    val accent = Color(0xFFE5322D) // Damavand Red — the ONLY accent.
    val green = Color(0xFF34C77B)
    val error = Color(0xFFE5484D)
    val amber = Color(0xFFD8A13A) // connecting state only.
    val glass = Color(0x8C1A1C1F) // rgba(26,28,31,0.55) — ring only.
    val bottomBar = Color(0xEB0E0F11) // rgba(14,15,17,0.92).
}

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
