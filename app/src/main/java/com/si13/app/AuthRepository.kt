package com.si13.app

import android.content.Context
import com.google.firebase.auth.FirebaseUser

class AuthRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAuthenticated(): Boolean {
        return preferences.getBoolean(KEY_IS_AUTHENTICATED, false)
    }

    fun getCurrentUser(): AuthUser? {
        if (!isAuthenticated()) {
            return null
        }

        return AuthUser(
            uid = preferences.getString(KEY_UID, null),
            displayName = preferences.getString(KEY_DISPLAY_NAME, null),
            email = preferences.getString(KEY_EMAIL, null),
            photoUrl = preferences.getString(KEY_PHOTO_URL, null)
        )
    }

    fun saveAuthenticatedUser(user: FirebaseUser) {
        saveAuthenticatedUser(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString()
        )
    }

    fun saveAuthenticatedUser(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        preferences.edit()
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .putString(KEY_UID, uid)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PHOTO_URL, photoUrl)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "auth_preferences"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
        private const val KEY_UID = "uid"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"
    }
}

data class AuthUser(
    val uid: String?,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)
