package br.com.rmfacilities.funcionarioapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppThemeManager {
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    private const val PREFS = "rm_funcionario_app"
    private const val KEY_THEME_MODE = "app_theme_mode"

    fun getMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, MODE_LIGHT) ?: MODE_LIGHT
    }

    fun setMode(context: Context, mode: String) {
        val safeMode = when (mode) {
            MODE_DARK -> MODE_DARK
            MODE_SYSTEM -> MODE_SYSTEM
            else -> MODE_LIGHT
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, safeMode).apply()
        applyMode(safeMode)
    }

    fun apply(context: Context) {
        applyMode(getMode(context))
    }

    private fun applyMode(mode: String) {
        val appCompatMode = when (mode) {
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != appCompatMode) {
            AppCompatDelegate.setDefaultNightMode(appCompatMode)
        }
    }
}
