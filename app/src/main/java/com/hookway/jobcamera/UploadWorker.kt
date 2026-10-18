
package com.hookway.jobcamera

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(
            "queue", Context.MODE_PRIVATE
        )
        val authPrefs = applicationContext.getSharedPreferences(
            "drive_auth", Context.MODE_PRIVATE
        )

        val queued = prefs.getString("pending_files", "")
            .orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .distinct()

        if (queued.isEmpty()) return@withContext Result.success()

        val token = authPrefs.getString("access_token", null)
        if (token.isNullOrBlank()) return@withContext Result.failure()

        try {
            val credential = GoogleCredential().setAccessToken(token)

            val drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Hookway Job Camera")
                .build()

            val rootId = findOrCreateFolder(drive, "Hookway Restoration Jobs", "root")
            val remaining = mutableListOf<String>()

            for (path in queued) {
                val localFile = File(path)

                if (!localFile.exists() || !localFile.isFile) {
                    continue
                }

                try {
                    val jobsDir = File(applicationContext.filesDir, "jobs")
                    val relative = localFile.relativeTo(jobsDir)
                    val parts = relative.invariantSeparatorsPath.split("/")

                    val jobName = parts.getOrNull(0) ?: "Unsorted Jobs"
                    val dateName = parts.getOrNull(1) ?: "Other"

                    val jobFolderId = findOrCreateFolder(
                        drive, jobName, rootId
                    )
                    val dateFolderId = findOrCreateFolder(
                        drive, dateName, jobFolderId
                    )

                    val metadata = DriveFile().apply {
                        name = localFile.name
                        parents = listOf(dateFolderId)
                    }

                    val mimeType =
                        URLConnection.guessContentTypeFromName(localFile.name)
                            ?: "application/octet-stream"

                    drive.files()
                        .create(metadata, FileContent(mimeType, localFile))
                        .setFields("id,name")
                        .execute()

                } catch (e: Exception) {
                    remaining.add(path)
                    throw e
                }
            }

            prefs.edit()
                .putString("pending_files", remaining.joinToString("\n"))
                .apply()

            Result.success()

        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun findOrCreateFolder(
        drive: Drive,
        name: String,
        parentId: String
    ): String {
        val escapedName = name
            .replace("\\", "\\\\")
            .replace("'", "\\'")

        val result = drive.files().list()
            .setQ(
                "name = '$escapedName' and " +
                "mimeType = 'application/vnd.google-apps.folder' and " +
                "trashed = false and '$parentId' in parents"
            )
            .setSpaces("drive")
            .setPageSize(100)
            .setFields("files(id,name)")
            .execute()

        val existing = result.files.firstOrNull()
        if (existing != null) return existing.id

        val folder = DriveFile().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }

        return drive.files()
            .create(folder)
            .setFields("id")
            .execute()
            .id
    }
}
