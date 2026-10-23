package com.hookway.jobcamera

import android.app.AlertDialog
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
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
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.UK)
            .format(Date(startMillis))
        return "${title.replace(Regex("[\\\\/:*?\\\"<>|]"), "-")} - $day"
    }

   
 override fun toString(): String =
 "${SimpleDateFormat("EEE d MMM yyyy, HH:mm", 
    Locale.UK).format(Date(startMillis))} - $title"
}

class MainActivity : Activity() {

    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var photoButton: Button
    private lateinit var videoButton: Button
    private lateinit var finishButton: Button
    private lateinit var uploadButton: Button

    private val jobs = mutableListOf<Job>()
    private val shownJobs = mutableListOf<Job>()
    private var filterText = ""
    private var filterMode = 0
    private var activeJob: Job? = null
    private var pendingOutput: File? = null
    private var pendingType = "image"
    private var photoLabel = "Before"

    private val settings by lazy {
        getSharedPreferences("company_settings", MODE_PRIVATE)
    }

    private fun businessName(): String =
        settings.getString("business_name", "").orEmpty().trim()

    private val photoRequest = 201
    private val videoRequest = 202
    private val permissionRequest = 203
    private val importPhotoRequest = 204
    private val importVideoRequest = 205

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (businessName().isBlank()) {
            showBusinessSetup()
            return
        }
        buildUi()
        requestCalendarPermission()
        refreshJobs()
        enqueueSync()
    }

    private fun showBusinessSetup() {
        val input = EditText(this).apply {
            hint = "Your business name"
            setSingleLine(true)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(TextView(this@MainActivity).apply {
                text = "Enter your business name to personalise uploaded filenames."
            })
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Set up your business")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(
                        this,
                        "Please enter your business name.",
                        Toast.LENGTH_LONG
                    ).show()
                    showBusinessSetup()
                } else {
                    settings.edit()
                        .putString("business_name", name)
                        .apply()
                    recreate()
                }
            }
            .show()
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
        
 val searchBox = EditText(this).apply {
 hint = "Search jobs by name or date"
 setSingleLine(true)
 addTextChangedListener(object : TextWatcher {
 override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
 override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
 override fun afterTextChanged(s: Editable?) {
 filterText = s?.toString().orEmpty()
 showJobsInSpinner()
 }
 })
 }
 root.addView(searchBox)

 val modeButton = Button(this).apply {
 text = "SHOWING: ALL JOBS"
 setOnClickListener {
 filterMode = (filterMode + 1) % 3
 text = when (filterMode) {
 1 -> "SHOWING: UPCOMING ONLY"
 2 -> "SHOWING: PAST ONLY"
 else -> "SHOWING: ALL JOBS"
 }
 showJobsInSpinner()
 }
 }
 root.addView(modeButton)

        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val beforeButton = Button(this).apply {
            text = "BEFORE PHOTO"
            setOnClickListener {
                photoLabel = "Before"
                capture("image")
            }
        }

        val afterButton = Button(this).apply {
            text = "AFTER PHOTO"
            setOnClickListener {
                photoLabel = "After"
                capture("image")
            }
        }

        labelRow.addView(beforeButton, LinearLayout.LayoutParams(0, -2, 1f))
        labelRow.addView(afterButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(labelRow)

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
            text = "IMPORT PHOTO / VIDEO"
            textSize = 18f
            setOnClickListener { pickMedia() }
        }
        root.addView(uploadButton)
 val settingsButton = Button(this).apply {
 text = "SETTINGS / CONNECT APPS"
 textSize = 18f
 setOnClickListener {
 startActivity(
 Intent(this@MainActivity, SettingsActivity::class.java)
 )
 }
 }
 root.addView(settingsButton)

        finishButton = Button(this).apply {
            text = "BACK / FINISH THIS JOB"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Leave this job?")
                    .setItems(
                        arrayOf(
                            "Pick another job",
                            "Sign off this job",
                            "Cancel"
                        )
                    ) { dialog, which ->
                        when (which) {
                            0 -> {
                                activeJob = null
                                saveActiveJob(null)
                                spinner.setSelection(0)
                                status.text =
                                    "Choose a job from the list above."
                                updateButtons()
                            }

                            1 -> {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Sign off this job?")
                                    .setMessage(
                                        "Are you sure you want to finish this job?"
                                    )
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Sign off") { _, _ ->
                                        activeJob = null
                                        saveActiveJob(null)
                                        spinner.setSelection(0)
                                        status.text =
                                            "Job signed off. Select another job above."
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

        setContentView(ScrollView(this).apply {
            addView(root)
        })

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    activeJob = null
                    updateButtons()
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 0) {
                        activeJob = null
                        status.text = "Choose a Calendar job to begin."
                        updateButtons()
                        return
                    }

                     val jobIndex = position - 1
                     if (jobIndex in shownJobs.indices) {
                        activeJob = shownJobs[jobIndex]
                        saveActiveJob(activeJob)
                        status.text =
                            "Selected: ${activeJob!!.folderName()}\n" +
                            "New photos/videos will be saved for this job."
                        updateButtons()
                    }
                }
            }
    }

    // Remembers the selected job until it is signed off or another is picked.
    private fun saveActiveJob(job: Job?) {
        val editor = getSharedPreferences("active_job", MODE_PRIVATE).edit()
        if (job == null) {
            editor.clear()
        } else {
            editor.putString("title", job.title)
            editor.putLong("startMillis", job.startMillis)
        }
        editor.apply()
    }

    private fun savedActiveJob(): Job? {
        val prefs = getSharedPreferences("active_job", MODE_PRIVATE)
        val title = prefs.getString("title", null)?.trim().orEmpty()
        val start = prefs.getLong("startMillis", 0L)
        return if (title.isBlank() || start <= 0L) null else Job(title, start)
    }

    private fun updateButtons() {
        val enabled = activeJob != null
        photoButton.isEnabled = enabled
        videoButton.isEnabled = enabled
        uploadButton.isEnabled = enabled
    }

    private fun requestCalendarPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR),
                permissionRequest
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == permissionRequest &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            refreshJobs()
        }
    }

    private fun refreshJobs() {
        val cachedJobs = loadCachedJobs()
        jobs.clear()
        jobs.addAll(cachedJobs)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showJobsInSpinner()
            activeJob = null
            updateButtons()
            status.text = if (jobs.isNotEmpty()) {
                "Calendar permission is not available. Showing " +
                "${jobs.size} previously saved events for offline use."
            } else {
                "Allow Calendar permission, then refresh. " +
                "Google Calendar must be synced on this tablet."
            }
            return
        }

        val now = System.currentTimeMillis()
        val start = now - TimeUnit.DAYS.toMillis(90)
        val end = now + TimeUnit.DAYS.toMillis(365)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )

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

                val titleCol =
                    cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginCol =
                    cursor.getColumnIndex(CalendarContract.Instances.BEGIN)

                if (titleCol >= 0 && beginCol >= 0) {
                    while (cursor.moveToNext()) {
                        val name =
                            cursor.getString(titleCol)?.trim().orEmpty()
                        val eventStart = cursor.getLong(beginCol)

                        if (name.isNotBlank()) {
                            freshJobs.add(Job(name, eventStart))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            querySucceeded = false
        }

        if (querySucceeded && freshJobs.isNotEmpty()) {
            jobs.clear()

            val distinctJobs =
                freshJobs.distinctBy { "${it.title}|${it.startMillis}" }
            val sortNow = System.currentTimeMillis()

            jobs.addAll(
                distinctJobs.sortedWith(
                    compareBy<Job> {
                        if (it.startMillis >= sortNow) 0 else 1
                    }.thenBy {
                        if (it.startMillis >= sortNow) {
                            it.startMillis
                        } else {
                            -it.startMillis
                        }
                    }
                )
            )

            saveCachedJobs(jobs)
            showJobsInSpinner()
            activeJob = null
            updateButtons()
            status.text =
                "${jobs.size} Calendar events loaded from all synced calendars. " +
                "The list is saved for offline use."
        } else {
            showJobsInSpinner()
            activeJob = null
            updateButtons()

            status.text = when {
                jobs.isNotEmpty() ->
                    "Calendar could not provide fresh events. Showing " +
                    "${jobs.size} previously saved events; they can be selected offline."

                querySucceeded ->
                    "No Calendar events found in the last 90 days or next 365 days. " +
                    "Check that the correct Google account's calendars are synced."

                else ->
                    "Calendar could not be read and no saved event list exists yet. " +
                    "Connect to Calendar, grant permission, then refresh."
            }
        }
    }

    private fun showJobsInSpinner() {
        val labels = listOf("Choose a job…") + jobs.map { it.toString() }
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        spinner.setSelection(0)

        // Put the remembered job back, if it is still in the list.
        val saved = savedActiveJob()
        if (saved != null) {
            val index = jobs.indexOf(saved)
            if (index >= 0) {
                spinner.setSelection(index + 1)
            }
        }
    }

    private fun saveCachedJobs(items: List<Job>) {
        val array = JSONArray()

        items.forEach { job ->
            array.put(
                JSONObject()
                    .put("title", job.title)
                    .put("startMillis", job.startMillis)
            )
        }

        getSharedPreferences("calendar_cache", MODE_PRIVATE)
            .edit()
            .putString("jobs", array.toString())
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedJobs(): List<Job> {
        val raw = getSharedPreferences(
            "calendar_cache",
            MODE_PRIVATE
        ).getString("jobs", null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)

            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index)
                    ?: return@mapNotNull null

                val title = item.optString("title", "").trim()
                val start = item.optLong("startMillis", 0L)

                if (title.isBlank() || start <= 0L) {
                    null
                } else {
                    Job(title, start)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Opens the Android photo/video picker for existing device media.
    private fun pickMedia() {
        if (activeJob == null) {
            status.text = "Please select a Calendar job first."
            return
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/*", "video/*")
            )
        }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent.createChooser(intent, "Select a photo or video"),
                importPhotoRequest
            )
        } catch (_: Exception) {
            status.text =
                "Could not open the device photo and video gallery."
        }
    }

    private fun capture(type: String) {
        val job = activeJob ?: return

        val dir = File(
            filesDir,
            "jobs/${safe(job.title)}/" +
            SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        ).apply {
            mkdirs()
        }

        val stamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date())
        val ext = if (type == "image") ".jpg" else ".mp4"
        val label = if (type == "image") photoLabel else "Video"
        val fileName = "${safe(businessName())}_${safe(job.title)}_${label}_${stamp}${ext}"
        val file = File(dir, fileName)

        pendingOutput = file
        pendingType = type

        val intent =
            if (type == "image") {
                Intent("android.media.action.IMAGE_CAPTURE")
            } else {
                Intent("android.media.action.VIDEO_CAPTURE")
            }

        val outputUri =
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

        intent.putExtra(
            android.provider.MediaStore.EXTRA_OUTPUT,
            outputUri
        )
        intent.clipData =
            android.content.ClipData.newRawUri("job-media", outputUri)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                intent,
                if (type == "image") photoRequest else videoRequest
            )
        } catch (_: Exception) {
            file.delete()
            status.text = "Could not open the camera app on this tablet."
        }
    }

    @Deprecated("Camera and media picker result")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == DriveAuth.REQUEST_CODE) {
            DriveAuth.handleResult(this, data)
            enqueueSync()
            return
        }

        if (
            requestCode == importPhotoRequest ||
            requestCode == importVideoRequest
        ) {
            handlePickedMedia(resultCode, data)
            return
        }

        val file = pendingOutput ?: return

        if (
            resultCode == RESULT_OK &&
            file.exists() &&
            file.length() > 0
        ) {
            val prefs = getSharedPreferences("queue", MODE_PRIVATE)
            val key = "pending_files"
            val old = prefs.getString(key, "") ?: ""

            prefs.edit()
                .putString(key, old + file.absolutePath + "\n")
                .apply()

            status.text =
                "Saved offline: ${file.name}\n" +
                "Queued on tablet. ${file.length() / 1024} KB."

            enqueueSync()
        } else {
            file.delete()
            status.text =
                "Capture cancelled or camera did not save the file."
        }

        pendingOutput = null
    }

    private fun enqueueSync() {
        val request =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "hookway-drive-upload",
            ExistingWorkPolicy.APPEND_OR_REPLACE, 
            request
        )
    }

    // Copies selected gallery media into the active job's local folder.
    private fun handlePickedMedia(
        resultCode: Int,
        data: Intent?
    ) {
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
            val uri: Uri = data.data!!
            val mime = contentResolver.getType(uri) ?: ""

            val ext = when {
                mime == "image/jpeg" -> "jpg"
                mime == "image/png" -> "png"
                mime == "image/webp" -> "webp"
                mime == "image/gif" -> "gif"
                mime == "image/heic" -> "heic"
                mime == "image/heif" -> "heif"
                mime == "video/mp4" -> "mp4"
                mime == "video/quicktime" -> "mov"
                mime.startsWith("video/") ->
                    mime.substringAfter("/").substringBefore(";")
                mime.startsWith("image/") ->
                    mime.substringAfter("/").substringBefore(";")
                else -> "bin"
            }

            val dir = File(
                filesDir,
                "jobs/${safe(job.title)}/" +
                SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            ).apply {
                mkdirs()
            }

            val stamp =
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.UK)
                    .format(Date())

            val label = if (ext in listOf("jpg", "jpeg", "png", "webp")) photoLabel else "Video"
            file = File(
                dir,
                "${safe(businessName())}_${safe(job.title)}_${label}_${stamp}.$ext"
            )

            val input = contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot read selected file")

            input.use { source ->
                file!!.outputStream().use { output ->
                    source.copyTo(output)
                }
            }

            if (file!!.length() == 0L) {
                throw Exception("Selected file was empty")
            }

            val prefs = getSharedPreferences("queue", MODE_PRIVATE)
            val old = prefs.getString("pending_files", "") ?: ""

            prefs.edit()
                .putString(
                    "pending_files",
                    old + file!!.absolutePath + "\n"
                )
                .apply()

            status.text =
                "Imported: ${file!!.name}\n" +
                "Saved offline and queued for upload."

            enqueueSync()
        } catch (e: Exception) {
            file?.delete()
            status.text = "Import failed: ${e.message}"
        }
    }

    private fun safe(value: String): String =
        value.replace(Regex("[\\\\/:*?\\\"<>|]"), "-")
}
