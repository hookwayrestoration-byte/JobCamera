package com.hookway.jobcamera

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Deliberately does not delete local files. Drive upload is not enabled until OAuth
 * credentials and the target Drive folder strategy are configured in README.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("queue", Context.MODE_PRIVATE)
        val queued = prefs.getString("pending_files", "").orEmpty().lines().filter { it.isNotBlank() }
        if (queued.isEmpty()) return Result.success()
        // Safe placeholder: retain all media until the real authenticated Drive uploader is configured.
        // This avoids falsely reporting uploads or risking loss of job evidence.
        return Result.success()
    }
}
