package com.amyrmo.damavand.lite

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * App language (3.2.0): automatic Persian/English selection.
 * Stored choice is "auto" (system language), "fa" or "en".
 * "auto" resolves to fa only when the phone language is Persian;
 * anything else falls back to English.
 */
object LocaleHelper {

    /** The resolved language ("fa"/"en") the UI was last wrapped with. */
    @Volatile
    private var currentLanguage: String? = null

    /** [localized]'s context, kept per language. */
    @Volatile
    private var localizedCache: Pair<String, Context>? = null

    fun resolveLanguage(stored: String): String {
        if (stored == "fa" || stored == "en") return stored
        // "auto" follows the PHONE language. Not Locale.getDefault():
        // wrap() overwrites that with the last forced choice, so going
        // back to Auto kept the old language until the process died.
        val system = Resources.getSystem().configuration.locales[0]
        return if (system.language == "fa") "fa" else "en"
    }

    /** Wrap [base] with the resolved locale; also sets the default
     *  locale and the digit style used by [LiteFormat]. */
    fun wrap(base: Context, language: String): Context {
        currentLanguage = language
        val locale = Locale(language)
        Locale.setDefault(locale)
        LiteFormat.persianDigits = language == "fa"
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * The application context in the in-app language, for text resolved
     * outside the Activity (the ViewModel's status/error text, the VPN
     * service's status and notification text). Those used the plain
     * application context, so they followed the system language.
     */
    fun localized(base: Context): Context {
        val language = currentLanguage
            ?: resolveLanguage(storedLanguage(base)).also { currentLanguage = it }
        localizedCache?.let { (lang, ctx) -> if (lang == language) return ctx }
        val app = base.applicationContext ?: base
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale(language))
        return app.createConfigurationContext(config).also { localizedCache = language to it }
    }

    /** The stored choice, for a process started without the Activity (boot, tile). */
    private fun storedLanguage(context: Context): String = try {
        runBlocking { SettingsStore(context).languageNow() }
    } catch (_: Exception) {
        "auto"
    }
}
