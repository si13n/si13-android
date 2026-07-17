package com.si13.app

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible

class GoogleSignInHandler(
    private val context: Context,
    private val activity: Activity,
    private val googleAuthClient: GoogleAuthClient = GoogleAuthClient()
) {
    suspend fun signIn(
        signInButton: View,
        errorText: TextView?,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit = {},
        extraDisabledView: View? = null
    ) {
        signInButton.isEnabled = false
        extraDisabledView?.isEnabled = false
        errorText?.isVisible = false

        when (val result = googleAuthClient.signIn(activity)) {
            is GoogleAuthResult.Success -> {
                AuthRepository(context).saveAuthenticatedUser(result.user)
                onSuccess()
            }

            GoogleAuthResult.Cancelled -> {
                signInButton.isEnabled = true
                extraDisabledView?.isEnabled = true
                onCancelled()
            }

            is GoogleAuthResult.Failure -> {
                errorText?.text = result.message
                errorText?.isVisible = true
                signInButton.isEnabled = true
                extraDisabledView?.isEnabled = true
            }
        }
    }
}
