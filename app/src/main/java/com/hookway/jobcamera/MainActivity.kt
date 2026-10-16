package com.hookway.jobcamera
import android.app.AlertDialog

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

data class Job(val title: String, val startMillis: Long) {
    fun folderName(): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date(startMillis))
        return "${title.replace(Regex("[\\\\/:*?\\\"<>|]"), "-")} - $day"
    }
    override fun toString(): String =
        "${SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(Date(startMillis))} — $title"
}

class MainActivity : Activity() {
    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var photoButton: Button
    private lateinit var videoButton: Button
    private lateinit var finishButton: Button
    private lateinit var uploadButton: Button
    private val jobs = mutableListOf<Job>()
    private var activeJob: Job? = null
    private var pendingOutput: File? = null
    private var pendingType = "image"
    private val photoRequest = 201
    private val videoRequest = 202
    private val permissionRequest = 203

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestCalendarPermission()
        refreshJobs()
        enqueueSync()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            gravity = Gravity.TOP
        }
        val title = TextView(this).apply {
            text = "HOOKWAY RESTORATION\nJOB CAMERA"
            textSize = 24f
            setTextColor(0xFF17324D.toInt())
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Choose a Calendar job"
            textSize = 17f
        })
        spinner = Spinner(this)
        root.addView(spinner)
        val refresh = Button(this).apply {
            text = "Refresh jobs from Calendar"
            setOnClickListener { refreshJobs() }
        }
        root.addView(refresh)
        photoButton = Button(this).apply {
            text = "TAKE PHOTO"
            textSize = 18f
            setOnClickListener { capture("image") }
        }
        root.addView(photoButton)
        videoButton = Button(this).apply {
            text = "RECORD VIDEO"
            textSize = 18f
            setOnClickListener { capture("video") }
        }
        root.addView(videoButton)
        uploadButton = Button(this).apply {
            text = "UPLOAD PHOTO / VIDEO"
            textSize = 18f
            setOnClickListener { pickMedia() }
        }
        root.addView(uploadButton)   
        finishButton = Button(this).apply {
            text = "BACK / FINISH THIS JOB"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Leave this job?")
                    .setItems(arrayOf("Pick another job", "Sign off this job", "Cancel")) { dialog, which ->
                        when (which) {
                            0 -> {
                                activeJob = null
                                spinner.setSelection(0)
                                status.text = "Choose a job from the list above."
                                updateButtons()
                            }
                            1 -> {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Sign off this job?")
                                    .setMessage("Are you sure you want to finish this job?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Sign off") { _, _ ->
                                        activeJob = null
                                        spinner.setSelection(0)
                                        status.text = "Job signed off. Select another job above."
                                        updateButtons()
                                    }
                                    .show()
                            }
                            else -> dialog.dismiss()
                        }
                    }
                    .show()
            }
        }
        root.addView(finishButton)
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 16, 0, 12)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Media is saved on this tablet first. Drive upload needs Google Drive setup; see README."
            textSize = 12f
            setTextColor(0xFF555555.toInt())
        })
        setContentView(ScrollView(this).apply { addView(root) })
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                activeJob = null
                updateButtons()
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    activeJob = null
                    status.text = "Choose a Calendar job to begin."
                    updateButtons()
                    return
                }
                val jobIndex = position - 1
                if (jobIndex in jobs.indices) {
                    activeJob = jobs[jobIndex]
                    status.text = "Selected: ${activeJob!!.folderName()}\nNew photos/videos will be saved for this job."
                    updateButtons()
                }
            }
        }
    }

    private fun updateButtons() {
        val enabled = activeJob != null
        photoButton.isEnabled = enabled
        videoButton.isEnabled = enabled
        uploadButton.isEnabled = enabled
    }

    private fun requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), permissionRequest)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshJobs()
    }

    private fun refreshJobs() {
        val cachedJobs = loadCachedJobs()
        jobs.clear()
        jobs.addAll(cachedJobs)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            showJobsInSpinner()
            activeJob = null
            updateButtons()
            status.text = if (jobs.isNotEmpty())
                "Calendar permission is not available. Showing ${jobs.size} previously saved events for offline use."
            else "Allow Calendar permission, then refresh. Google Calendar must be synced on this tablet."
            return
        }

        // Include all calendars visible through Android's Calendar provider, across a useful
        // window. The last successful non-empty result is cached for offline job selection.
        val now = System.currentTimeMillis()
        val start = now - TimeUnit.DAYS.toMillis(90)
        val end = now + TimeUnit.DAYS.toMillis(365)
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val rangeUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val freshJobs = mutableListOf<Job>()
        var querySucceeded = false
        try {
            contentResolver.query(
                rangeUri,
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                querySucceeded = true
                val titleCol = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                if (titleCol >= 0 && beginCol >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(titleCol)?.trim().orEmpty()
                        val eventStart = cursor.getLong(beginCol)
                        if (name.isNotBlank()) freshJobs.add(Job(name, eventStart))
                    }
                }
            }
        } catch (_: Exception) {
            querySucceeded = false
        }

        if (querySucceeded && freshJobs.isNotEmpty()) {
            jobs.clear()
            // Show upcoming appointments first, with the next appointment at the top.
            val distinctJobs = freshJobs.distinctBy { "${it.title}|${it.startMillis}" }
            val sortNow = System.currentTimeMillis()
            jobs.addAll(distinctJobs.sortedWith(
                compareBy<Job> { if (it.startMillis >= sortNow) 0 else 1 }
                    .thenBy { if (it.startMillis >= sortNow) it.startMillis else -it.startMillis }
            ))
            saveCachedJobs(jobs)
            showJobsInSpinner()
            activeJob = null
            updateButtons()
            status.text = "${jobs.size} Calendar events loaded from all synced calendars. The list is saved for offline use."
        } else {
            showJobsInSpinner()
            activeJob = null
            updateButtons()
            status.text = when {
                jobs.isNotEmpty() -> "Calendar could not provide fresh events. Showing ${jobs.size} previously saved events; they can be selected offline."
                querySucceeded -> "No Calendar events found in the last 90 days or next 365 days. Check that the correct Google account's calendars are synced."
                else -> "Calendar could not be read and no saved event list exists yet. Connect to Calendar, grant permission, then refresh."
            }
        }
    }

        private fun showJobsInSpinner() {
        val labels = listOf("Choose a job…") + jobs.map { it.toString() }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(0)
    }

    private fun saveCachedJobs(items: List<Job>) {
        val array = JSONArray()
        items.forEach { job ->
            array.put(JSONObject().put("title", job.title).put("startMillis", job.startMillis))
        }
        getSharedPreferences("calendar_cache", MODE_PRIVATE).edit()
            .putString("jobs", array.toString())
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedJobs(): List<Job> {
        val raw = getSharedPreferences("calendar_cache", MODE_PRIVATE).getString("jobs", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title", "").trim()
                val start = item.optLong("startMillis", 0L)
                if (title.isBlank() || start <= 0L) null else Job(title, start)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun pickMedia() {
        if (activeJob == null) {
            status.text = "Please select a Calendar job first."
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/*", "video/*")
            )
        }

        try {
            startActivityForResult(intent, 204)
        } catch (_: Exception) {
            status.text = "Could not open the photo and video picker."
        }
    }   
    private fun capture(type: String) {
        val job = activeJob ?: return
        val dir = File(filesDir, "jobs/${safe(job.title)}/${SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())}").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date())
        val ext = if (type == "image") ".jpg" else ".mp4"
        val file = File(dir, "${stamp}_${type}${ext}")
        pendingOutput = file
        pendingType = type
        val intent = if (type == "image") Intent("android.media.action.IMAGE_CAPTURE")
                    else Intent("android.media.action.VIDEO_CAPTURE")
        val outputUri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, outputUri)
        intent.clipData = android.content.ClipData.newRawUri("job-media", outputUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, if (type == "image") photoRequest else videoRequest)
        } catch (_: Exception) {
            file.delete()
            status.text = "Could not open the camera app on this tablet."
        }
    }

    @Deprecated("Camera intent result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 204) {
            handlePickedMedia(resultCode, data)
            return
        }
        val file = pendingOutput ?: return
        if (resultCode == RESULT_OK && file.exists() && file.length() > 0) {
            val prefs = getSharedPreferences("queue", MODE_PRIVATE)
            val key = "pending_files"
            val old = prefs.getString(key, "") ?: ""
            prefs.edit().putString(key, (old + file.absolutePath + "\n")).apply()
            status.text = "Saved offline: ${file.name}\nQueued on tablet. ${file.length() / 1024} KB."
            enqueueSync()
        } else {
            file.delete()
            status.text = "Capture cancelled or camera did not save the file."
        }
        pendingOutput = null
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("hookway-drive-upload", ExistingWorkPolicy.KEEP, request)
    }

    private fun handlePickedMedia(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data?.data == null) {
            status.text = "Media selection cancelled."
            return
        }
        val job = activeJob ?: run {
            status.text = "Please select a Calendar job first."
            return
        }
        var file: File? = null
        try {
            val uri = data.data!!
            val mime = contentResolver.getType(uri) ?: ""
            val ext = if (mime.startsWith("video/")) "mp4" else if (mime == "image/png") "png" else if (mime == "image/webp") "webp" else "jpg"
            val dir = File(filesDir, "jobs/${safe(job.title)}/${SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())}").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date())
            file = File(dir, "${stamp}_imported.$ext")
            val input = contentResolver.openInputStream(uri) ?: throw Exception("Cannot read selected file")
            input.use { source -> file!!.outputStream().use { output -> source.copyTo(output) } }
            if (file!!.length() == 0L) throw Exception("Selected file was empty")
            val prefs = getSharedPreferences("queue", MODE_PRIVATE)
            val old = prefs.getString("pending_files", "") ?: ""
            prefs.edit().putString("pending_files", old + file!!.absolutePath + "\n").apply()
            status.text = "Imported: ${file!!.name}\nSaved offline and queued for upload."
            enqueueSync()
        } catch (e: Exception) {
            file?.delete()
            status.text = "Import failed: ${e.message}"
        }
    }

    private fun safe(value: String) = value.replace(Regex("[\\\\/:*?\\\"<>|]"), "-")
}
