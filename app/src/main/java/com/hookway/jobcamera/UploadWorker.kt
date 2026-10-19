
package com.hookway.jobcamera

import android.content.Context
import androidx.work.BackoffPolicy
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
import java.util.concurrent.TimeUnit

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val prefs = app.getSharedPreferences("queue", Context.MODE_PRIVATE)
        val authPrefs = app.getSharedPreferences("drive_auth", Context.MODE_PRIVATE)

        fun readQueue(): List<String> =
            prefs.getString("pending_files", "").orEmpty()
                .lines().filter { it.isNotBlank() }.distinct()

        fun recordError(message: String) {
            prefs.edit().putString("last_upload_error", message).commit()
        }

        val token = authPrefs.getString("access_token", null)
        if (token.isNullOrBlank()) {
            recordError("Google Drive is not authorised. Tap CONNECT GOOGLE DRIVE / UPLOAD.")
            return@withContext Result.failure()
        }

        val queued = readQueue()
        if (queued.isEmpty()) {
            recordError("")
            return@withContext Result.success()
        }

        try {
            val drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                GoogleCredential().setAccessToken(token)
            ).setApplicationName("Hookway Job Camera").build()

            val jobsDir = File(app.filesDir, "jobs")
            val rootId = findOrCreateFolder(drive, "Hookway Restoration Jobs", "root")

            for (path in queued) {
                if (isStopped) return@withContext Result.retry()

                val file = File(path)

                if (!file.exists() || !file.isFile) {
                    removeFromQueue(prefs, path)
                    continue
                }

                try {
                    val relative = file.relativeTo(jobsDir)
                    val parts = relative.invariantSeparatorsPath.split("/")

                    require(parts.size >= 3) {
                        "File is not inside a job/date folder: ${file.name}"
                    }

                    val jobFolder = findOrCreateFolder(drive, parts[0], rootId)
                    val dateFolder = findOrCreateFolder(drive, parts[1], jobFolder)

                    // Use a stable marker so retries don't upload the same
                    // file twice if Drive accepted it but the response was lost.
                    val marker = file.name
                    if (!fileExists(drive, marker, dateFolder)) {
                        val metadata = DriveFile().apply {
                            name = file.name
                            parents = listOf(dateFolder)
                            appProperties = mapOf("hookwayLocalName" to marker)
                        }

                        val mime = URLConnection.guessContentTypeFromName(file.name)
                            ?: "application/octet-stream"

                        drive.files().create(metadata, FileContent(mime, file))
                            .setFields("id,name")
                            .execute()
                    }

                    removeFromQueue(prefs, path)
                    recordError("")
                } catch (e: Exception) {
                    recordError("${e.javaClass.simpleName}: ${e.message ?: "Upload failed"}")
                    return@withContext Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            recordError("${e.javaClass.simpleName}: ${e.message ?: "Drive connection failed"}")
            Result.retry()
        }
    }

    private fun removeFromQueue(
        prefs: android.content.SharedPreferences,
        path: String
    ) {
        synchronized(QUEUE_LOCK) {
            val remaining = prefs.getString("pending_files", "").orEmpty()
                .lines().filter { it.isNotBlank() && it != path }.distinct()

            check(prefs.edit()
                .putString("pending_files", remaining.joinToString("\n"))
                .commit()) { "Could not save upload queue" }
        }
    }

    private fun fileExists(drive: Drive, name: String, parentId: String): Boolean {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        return drive.files().list()
            .setQ("name = '$escaped' and trashed = false and '$parentId' in parents")
            .setSpaces("drive")
            .setPageSize(100)
            .setFields("files(id)")
            .execute().files.isNotEmpty()
    }

    private fun findOrCreateFolder(
        drive: Drive,
        name: String,
        parentId: String
    ): String {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val result = drive.files().list()
            .setQ(
                "name = '$escaped' and " +
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

        return drive.files().create(folder).setFields("id").execute().id
    }

    companion object {
        private val QUEUE_LOCK = Any()
    }
}
