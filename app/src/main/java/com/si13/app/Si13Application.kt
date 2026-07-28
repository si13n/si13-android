package com.si13.app

import android.app.Application

class Si13Application : Application() {
    override fun onCreate() {
        // Apply before the first Activity is created to prevent a light-theme flash.
        AppearancePreferences.create(this).applyStoredMode()
        super.onCreate()
    }
}
