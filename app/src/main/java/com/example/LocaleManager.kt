package com.example

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LocaleManager {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANG = "key_language_tag"

    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_RU = "ru"
    const val LANG_UK = "uk"

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, langTag: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langTag).commit()
        applyLocale(context, langTag)
    }

    fun applyLocale(context: Context, langTag: String = getLanguage(context)) {
        val targetLocale = getLocaleForTag(langTag)
        Locale.setDefault(targetLocale)

        updateResourcesLocale(context, targetLocale)
        val appContext = context.applicationContext
        if (appContext != null && appContext !== context) {
            updateResourcesLocale(appContext, targetLocale)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateResourcesLocale(context: Context, locale: Locale) {
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }

    fun getLocaleForTag(langTag: String): Locale {
        return when (langTag) {
            LANG_EN -> Locale("en")
            LANG_RU -> Locale("ru")
            LANG_UK -> Locale("uk")
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    Resources.getSystem().configuration.locale ?: Locale.getDefault()
                }
            }
        }
    }

    fun wrapContext(context: Context): Context {
        val langTag = getLanguage(context)
        val locale = getLocaleForTag(langTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
