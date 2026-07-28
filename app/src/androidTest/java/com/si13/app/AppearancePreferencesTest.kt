package com.si13.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetAppearance() {
        AppearancePreferences.create(context).setMode(AppearanceMode.SYSTEM_DEFAULT)
    }

    @Test
    fun selectedAppearancePersistsAcrossPreferenceInstances() {
        AppearancePreferences.create(context).setMode(AppearanceMode.DARK)

        assertEquals(AppearanceMode.DARK, AppearancePreferences.create(context).mode)
    }

    @Test
    fun appearanceModesMapToAppCompatNightModes() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppearanceMode.SYSTEM_DEFAULT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppearanceMode.LIGHT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppearanceMode.DARK.nightMode)
    }
}
