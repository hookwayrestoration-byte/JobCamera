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

        fun readQueue(): MutableList<String> =
            prefs.getString("pending_files", "")
                .orEmpty()
                .lines()
                .filter { it.isNotBlank() }
                .distinct()
                .toMutableList()

        val queued = readQueue()
        if (queued.isEmpty()) return@withContext Result.success()

        val token = authPrefs.getString("access_token", null)
        if (token.isNullOrBlank()) return@withContext Result.retry()

        try {
            val credential = GoogleCredential().setAccessToken(token)

            val drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Hookway Job Camera")
                .build()

            val jobsDir = File(applicationContext.filesDir, "jobs")
            val rootId = findOrCreateFolder(
                drive, "Hookway Restoration Jobs", "root"
            )

            for (path in queued) {
                val localFile = File(path)

                // Remove files that no longer exist locally.
                if (!localFile.exists() || !localFile.isFile) {
                    removeFromQueue(prefs, path)
                    continue
                }

                try {
                    val relative = localFile.relativeTo(jobsDir)
                    val parts = relative.invariantSeparatorsPath.split("/")

                    require(parts.size >= 3) {
                        "File is not inside a job/date folder: $path"
                    }

                    val jobName = parts[0]
                    val dateName = parts[1]

                    val jobFolderId = findOrCreateFolder(
                        drive, jobName, rootId
                    )
                    val dateFolderId = findOrCreateFolder(
                        drive, dateName, jobFolderId
                    )

                    // Avoid creating duplicates if a previous upload
                    // succeeded but the app did not record completion.
                    if (!fileExists(
                            drive, localFile.name, dateFolderId
                        )) {
                        val metadata = DriveFile().apply {
                            name = localFile.name
                            parents = listOf(dateFolderId)
                        }

                        val mimeType =
                            URLConnection.guessContentTypeFromName(
                                localFile.name
                            ) ?: "application/octet-stream"

                        drive.files()
                            .create(
                                metadata,
                                FileContent(mimeType, localFile)
                            )
                            .setFields("id,name")
                            .execute()
                    }

                    // Remove each successful upload immediately.
                    // If a later file fails, completed files stay removed.
                    removeFromQueue(prefs, path)

                } catch (e: Exception) {
                    // Keep this file and all other pending files.
                    return@withContext Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun removeFromQueue(
        prefs: android.content.SharedPreferences,
        path: String
    ) {
        val remaining = prefs.getString("pending_files", "")
            .orEmpty()
            .lines()
            .filter { it.isNotBlank() && it != path }
            .distinct()

        check(
            prefs.edit()
                .putString("pending_files", remaining.joinToString("\n"))
                .commit()
        ) {
            "Could not save upload queue"
        }
    }

    private fun fileExists(
        drive: Drive,
        name: String,
        parentId: String
    ): Boolean {
        val escapedName = name
            .replace("\\", "\\\\")
            .replace("'", "\\'")

        return drive.files().list()
            .setQ(
                "name = '$escapedName' and " +
                "trashed = false and '$parentId' in parents"
            )
            .setSpaces("drive")
            .setPageSize(100)
            .setFields("files(id)")
            .execute()
            .files
            .isNotEmpty()
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

        result.files.firstOrNull()?.let { return it.id }

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
