package com.amyrmo.damavand.lite

import android.content.Context
import android.content.res.Configuration
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import java.util.Locale

/**
 * App language (3.2.0): automatic Persian/English selection.
 * Stored choice is "auto" (system language), "fa" or "en".
 * "auto" resolves to fa only when the phone language is Persian;
 * anything else falls back to English.
 */
object LocaleHelper {

    fun resolveLanguage(stored: String): String {
        if (stored == "fa" || stored == "en") return stored
        return if (Locale.getDefault().language == "fa") "fa" else "en"
    }

    /** Wrap [base] with the resolved locale; also sets the default
     *  locale and the digit style used by [LiteFormat]. */
    fun wrap(base: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        LiteFormat.persianDigits = language == "fa"
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
