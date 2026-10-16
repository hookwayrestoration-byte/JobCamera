package com.hookway.jobcamera

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

object DriveAuth {
    const val REQUEST_CODE = 6101

    fun request(activity: Activity) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope("https://www.googleapis.com/auth/drive"))
            )
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    activity.startIntentSenderForResult(
                        result.pendingIntent!!.intentSender,
                        REQUEST_CODE,
                        null,
                        0,
                        0,
                        0
                    )
                } else {
                    showResult(activity, result)
                }
            }
            .addOnFailureListener { error ->
                android.widget.Toast.makeText(
                    activity,
                    "Google Drive authorisation failed: ${error.localizedMessage}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
    }

    fun handleResult(activity: Activity, data: Intent?) {
        try {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            showResult(activity, result)
        } catch (error: Exception) {
            android.widget.Toast.makeText(
                activity,
                "Could not complete Google Drive authorisation: ${error.localizedMessage}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showResult(activity: Activity, result: AuthorizationResult) {
        val message = if (!result.accessToken.isNullOrBlank()) {
            "Google Drive access authorised. Next we'll test the connection."
        } else {
            "Google authorisation returned no access token. Please try again."
        }

        android.widget.Toast.makeText(
            activity,
            message,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
