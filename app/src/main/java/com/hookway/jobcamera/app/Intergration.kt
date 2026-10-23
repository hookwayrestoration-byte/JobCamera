package com.hookway.jobcamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface Integration {
 val id: String
 val title: String
 val description: String
 val requestCode: Int
 fun isConnected(context: Context): Boolean
 fun connect(activity: Activity)
 fun disconnect(context: Context)
 fun handleResult(activity: Activity, data: Intent?)
}

object Integrations {
 val all: List<Integration> = listOf(DriveIntegration)

 private fun prefs(context: Context) =
 context.getSharedPreferences("integrations", Context.MODE_PRIVATE)

 fun isEnabled(context: Context, id: String): Boolean =
 prefs(context).getBoolean("${id}_enabled", true)

 fun setEnabled(context: Context, id: String, enabled: Boolean) {
 prefs(context).edit().putBoolean("${id}_enabled", enabled).apply()
 }

 fun syncNow(context: Context) {
 val request = OneTimeWorkRequestBuilder<UploadWorker>()
 .setConstraints(
 Constraints.Builder()
 .setRequiredNetworkType(NetworkType.CONNECTED)
 .build()
 )
 .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
 .build()

 WorkManager.getInstance(context).enqueueUniqueWork(
 "hookway-drive-upload",
 ExistingWorkPolicy.APPEND_OR_REPLACE,
 request
 )
 }
}

object DriveIntegration : Integration {
 override val id = "drive"
 override val title = "Google Drive"
 override val description = "Saves every photo and video into a Drive folder for each job."
 override val requestCode = DriveAuth.REQUEST_CODE

 override fun isConnected(context: Context): Boolean =
 !context.getSharedPreferences("drive_auth", Context.MODE_PRIVATE)
 .getString("access_token", null).isNullOrBlank()

 override fun connect(activity: Activity) {
 DriveAuth.request(activity)
 }

 override fun disconnect(context: Context) {
 context.getSharedPreferences("drive_auth", Context.MODE_PRIVATE)
 .edit().remove("access_token").apply()
 }

 override fun handleResult(activity: Activity, data: Intent?) {
 DriveAuth.handleResult(activity, data)
 }
}
