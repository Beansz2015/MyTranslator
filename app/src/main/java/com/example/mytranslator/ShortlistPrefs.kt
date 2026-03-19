package com.example.mytranslator

import android.content.Context

object ShortlistPrefs {

    private const val PREFS_NAME = "translator_prefs"
    private const val KEY_SHORTLIST = "auto_shortlist_locales"

    // Default 8 locales if nothing has been saved yet
    private val DEFAULT_LOCALES = listOf(
        "en-US", "ms-MY", "zh-CN", "hi-IN", "th-TH", "bn-IN", "fil-PH", "ja-JP"
    )

    fun load(context: Context): List<LangOption> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SHORTLIST, null)
        val locales = saved?.split(",") ?: DEFAULT_LOCALES
        // Map back to LangOption objects, filtering out any that no longer exist
        return locales.mapNotNull { locale -> LANGUAGES.firstOrNull { it.locale == locale } }
            .ifEmpty { DEFAULT_LOCALES.mapNotNull { locale -> LANGUAGES.firstOrNull { it.locale == locale } } }
    }

    fun save(context: Context, shortlist: List<LangOption>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SHORTLIST, shortlist.joinToString(",") { it.locale }).apply()
    }
}
