package com.example.si13

import android.content.Context

class AuthRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAuthenticated(): Boolean {
        return preferences.getBoolean(KEY_IS_AUTHENTICATED, false)
    }

    companion object {
        private const val PREFERENCES_NAME = "auth_preferences"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
    }
}
