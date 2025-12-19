package com.mylibrary.ui.adapters
import android.content.Context
import com.mylibrary.R

object ThemeManager {
    const val THEME_CLAIR = 0
    const val THEME_POMME = 1
    const val THEME_ROSE = 2
    const val THEME_SOMBRE = 3

    fun applyTheme(themeId: Int): Int {
        return when (themeId) {
            THEME_POMME -> R.style.AppTheme_Pomme
            THEME_ROSE -> R.style.AppTheme_Rose
            THEME_SOMBRE -> R.style.AppTheme_Sombre
            else -> R.style.AppTheme
        }
    }

    fun saveTheme(context: Context, themeId: Int) {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putInt("selected_theme", themeId).apply()
    }

    fun getSavedTheme(context: Context): Int {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return prefs.getInt("selected_theme", THEME_CLAIR)
    }
}