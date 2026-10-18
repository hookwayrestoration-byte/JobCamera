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

private fun showResult(
    activity: Activity,
    result: AuthorizationResult
) {
    val token = result.accessToken

    if (!token.isNullOrBlank()) {
        activity.getSharedPreferences(
            "drive_auth",
            Activity.MODE_PRIVATE
        ).edit()
            .putString("access_token", token)
            .apply()

        android.widget.Toast.makeText(
            activity,
            "Google Drive connected. Access token saved.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } else {
        android.widget.Toast.makeText(
            activity,
            "No access token received. Please reconnect Google Drive.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}}
