
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
import java.security.MessageDigest

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val prefs = app.getSharedPreferences("queue", Context.MODE_PRIVATE)
        val authPrefs = app.getSharedPreferences("drive_auth", Context.MODE_PRIVATE)

        fun recordError(message: String) {
            prefs.edit().putString("last_upload_error", message).apply()
        }

        fun readQueue(): List<String> =
            prefs.getString("pending_files", "").orEmpty()
                .lines()
                .filter { it.isNotBlank() }
                .distinct()

        val queued = readQueue()
        if (queued.isEmpty()) {
            recordError("")
            return@withContext Result.success()
        }

 if (!Integrations.isEnabled(app, "drive")) {
 return@withContext Result.success()
 }

        val token: String? = try {
 val freshRequest = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
 .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")))
 .build()
            val freshResult = com.google.android.gms.tasks.Tasks.await(
 com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(app).authorize(freshRequest)
 )
 val freshToken = freshResult.accessToken
 if (!freshToken.isNullOrBlank()) {
 authPrefs.edit().putString("access_token", freshToken).apply()
 freshToken
 } else {
 authPrefs.getString("access_token", null)
 }
 } catch (e: Exception) {
 authPrefs.getString("access_token", null)
 }
        if (token.isNullOrBlank()) {
            recordError("Google Drive is not authorised. Reconnect Drive.")
            return@withContext Result.failure()
        }

        try {
            val drive = Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                GoogleCredential().setAccessToken(token)
            ).setApplicationName("Hookway Job Camera").build()

            val jobsDir = File(app.filesDir, "jobs")
            val rootId = findOrCreateFolder(
                drive, "Hookway Restoration Jobs", "root"
            )

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

                    val jobFolderId = findOrCreateFolder(
                        drive, parts[0], rootId
                    )
                    val dateFolderId = findOrCreateFolder(
                        drive, parts[1], jobFolderId
                    )

                    val hash = sha256(file)

                    if (!fileExistsByHash(drive, hash, dateFolderId)) {
                        val metadata = DriveFile().apply {
                            name = file.name
                            parents = listOf(dateFolderId)
                            appProperties = mapOf("hookwaySha256" to hash)
                        }

                        val mime = URLConnection.guessContentTypeFromName(
                            file.name
                        ) ?: "application/octet-stream"

                        drive.files().create(
                            metadata,
                            FileContent(mime, file)
                        ).setFields("id,name").execute()
                    }

 // Keep local file; remove from queue only after success.
 removeFromQueue(prefs, path)
 recordError("")

 WebhookSettings.send(
 app,
 parts[0],
 file.name,
 if (file.extension.lowercase() in listOf("mp4", "3gp", "mov")) "video" else "image"
 )


                } catch (e: Exception) {
                    recordError(
                        "${e.javaClass.simpleName}: " +
                            (e.message ?: "File upload failed")
                    )
                    return@withContext Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            recordError(
                "${e.javaClass.simpleName}: " +
                    (e.message ?: "Google Drive upload failed")
            )
            Result.retry()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private fun removeFromQueue(
        prefs: android.content.SharedPreferences,
        path: String
    ) {
        synchronized(QUEUE_LOCK) {
            val remaining = prefs.getString("pending_files", "").orEmpty()
                .lines()
                .filter { it.isNotBlank() && it != path }
                .distinct()

            check(
                prefs.edit()
                    .putString("pending_files", remaining.joinToString("\n"))
                    .commit()
            ) { "Could not save the upload queue" }
        }
    }

    private fun fileExistsByHash(
        drive: Drive,
        hash: String,
        parentId: String
    ): Boolean {
        val escapedHash = hash.replace("'", "\\'")
        val query =
            "appProperties has { key='hookwaySha256' and value='$escapedHash' } " +
                "and trashed = false and '$parentId' in parents"

        return drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setPageSize(100)
            .setFields("files(id)")
            .execute()
            .files.isNotEmpty()
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

        return drive.files().create(folder)
            .setFields("id")
            .execute()
            .id
    }

    companion object {
        private val QUEUE_LOCK = Any()
    }
}
