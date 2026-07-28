package com.si13.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

enum class AppearanceMode(
    val storageValue: String,
    val nightMode: Int,
    @StringRes val labelRes: Int
) {
    SYSTEM_DEFAULT(
        storageValue = "system_default",
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        labelRes = R.string.system_default
    ),
    LIGHT(
        storageValue = "light",
        nightMode = AppCompatDelegate.MODE_NIGHT_NO,
        labelRes = R.string.light
    ),
    DARK(
        storageValue = "dark",
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        labelRes = R.string.dark
    );

    companion object {
        fun fromStorageValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM_DEFAULT
    }
}

class AppearancePreferences private constructor(
    private val preferences: SharedPreferences
) {
    val mode: AppearanceMode
        get() = AppearanceMode.fromStorageValue(preferences.getString(KEY_MODE, null))

    fun applyStoredMode() {
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun setMode(mode: AppearanceMode) {
        // Commit before AppCompat recreates activities for the new night mode.
        preferences.edit().putString(KEY_MODE, mode.storageValue).commit()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    companion object {
        private const val PREFERENCES_NAME = "appearance_preferences"
        private const val KEY_MODE = "appearance_mode"

        fun create(context: Context): AppearancePreferences = AppearancePreferences(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    }
}
