package com.si13.app

import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class GoogleAuthClient {
    suspend fun signIn(activity: Activity): GoogleAuthResult {
        val connectivity = AndroidConnectivityObserver(activity.applicationContext)
        if (!connectivity.isOnline()) {
            return GoogleAuthResult.Failure(activity.getString(R.string.google_sign_in_offline))
        }

        val webClientId = getWebClientId(activity)
            ?: return GoogleAuthResult.Failure(activity.getString(R.string.firebase_config_missing))

        val credentialManager = CredentialManager.create(activity)
        val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val credential = try {
            credentialManager.getCredential(
                context = activity,
                request = request
            ).credential
        } catch (exception: GetCredentialCancellationException) {
            return GoogleAuthResult.Cancelled
        } catch (exception: GetCredentialException) {
            return GoogleAuthResult.Failure(
                activity.getString(
                    if (connectivity.isOnline()) {
                        R.string.google_sign_in_failed
                    } else {
                        R.string.google_sign_in_offline
                    }
                )
            )
        }

        val idToken = try {
            getGoogleIdToken(credential)
        } catch (exception: GoogleIdTokenParsingException) {
            return GoogleAuthResult.Failure(
                activity.getString(R.string.google_sign_in_failed)
            )
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        return try {
            val result = FirebaseAuth.getInstance()
                .signInWithCredential(firebaseCredential)
                .await()
            val user = result.user
                ?: return GoogleAuthResult.Failure(
                    activity.getString(R.string.google_sign_in_failed)
                )
            GoogleAuthResult.Success(user)
        } catch (exception: Exception) {
            GoogleAuthResult.Failure(
                activity.getString(
                    if (connectivity.isOnline()) {
                        R.string.google_sign_in_failed
                    } else {
                        R.string.google_sign_in_offline
                    }
                )
            )
        }
    }

    private fun getGoogleIdToken(credential: Credential): String {
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

        throw GoogleIdTokenParsingException()
    }

    private fun getWebClientId(context: Context): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )

        if (resourceId == 0) {
            return null
        }

        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }
}

sealed class GoogleAuthResult {
    data class Success(val user: FirebaseUser) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Failure(val message: String) : GoogleAuthResult()
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}
