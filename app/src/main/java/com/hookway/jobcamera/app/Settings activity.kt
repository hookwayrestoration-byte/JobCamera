package com.hookway.jobcamera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*

class SettingsActivity : Activity() {

 private lateinit var list: LinearLayout

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 list = LinearLayout(this).apply {
 orientation = LinearLayout.VERTICAL
 setPadding(28, 32, 28, 24)
 }
 setContentView(ScrollView(this).apply {
 setBackgroundColor(AppStyle.BLACK)
 isFillViewport = true
 addView(list)
 })
 }

 override fun onResume() {
 super.onResume()
 render()
 }

 private fun add(view: View, top: Int = 0, bottom: Int = 0) {
 list.addView(
 view,
 LinearLayout.LayoutParams(
 LinearLayout.LayoutParams.MATCH_PARENT,
 LinearLayout.LayoutParams.WRAP_CONTENT
 ).apply {
 setMargins(0, AppStyle.dp(list, top), 0, AppStyle.dp(list, bottom))
 }
 )
 }

 private fun tv(content: String, style: (TextView) -> Unit): TextView =
 TextView(this).apply {
 this.text = content
 style(this)
 }

 private fun btn(label: String, primary: Boolean = true, onClick: () -> Unit): Button =
 Button(this).apply {
 text = label
 if (primary) AppStyle.primaryButton(this) else AppStyle.secondaryButton(this)
 setOnClickListener { onClick() }
 }

 private fun render() {
 list.removeAllViews()

 add(tv("SETTINGS") { AppStyle.title(it) })
 add(View(this).apply { setBackgroundColor(AppStyle.WOOD) }, 4, 16)
 (list.getChildAt(1).layoutParams as LinearLayout.LayoutParams).height = AppStyle.dp(list, 4)

 val settings = getSharedPreferences("company_settings", MODE_PRIVATE)
 add(tv("Business name (used in file names)") { AppStyle.body(it) }, 8)
 val nameInput = EditText(this).apply {
 setText(settings.getString("business_name", "").orEmpty())
 setSingleLine(true)
 AppStyle.input(this)
 }
 add(nameInput, 6)
 add(btn("SAVE BUSINESS NAME") {
 val name = nameInput.text.toString().trim()
 if (name.isBlank()) {
 Toast.makeText(
 this@SettingsActivity,
 "Please enter your business name.",
 Toast.LENGTH_LONG
 ).show()
 } else {
 settings.edit().putString("business_name", name).apply()
 Toast.makeText(this@SettingsActivity, "Saved.", Toast.LENGTH_SHORT).show()
 }
 }, 10)

 add(tv("How far back to show past jobs (then press Refresh jobs)") { AppStyle.body(it) }, 32, 6)
 val options = listOf(
 "3 months" to 90,
 "1 year" to 365,
 "3 years" to 1095,
 "10 years" to 3650
 )
 val current = settings.getInt("history_days", 1095)
 val labels = options.map { it.first }
 add(Spinner(this).apply {
 adapter = object : ArrayAdapter<String>(
 this@SettingsActivity,
 android.R.layout.simple_spinner_dropdown_item,
 labels
 ) {
 override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
 val v = super.getView(position, convertView, parent) as TextView
 v.setTextColor(AppStyle.TEXT)
 return v
 }

 override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
 val v = super.getDropDownView(position, convertView, parent) as TextView
 v.setTextColor(AppStyle.TEXT)
 v.setBackgroundColor(AppStyle.PANEL)
 return v
 }
 }
 setSelection(options.indexOfFirst { it.second == current }.coerceAtLeast(0))
 onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
 override fun onNothingSelected(parent: AdapterView<*>?) {}
 override fun onItemSelected(
 parent: AdapterView<*>?, view: View?, position: Int, id: Long
 ) {
 settings.edit()
 .putInt("history_days", options[position].second)
 .apply()
 }
 }
 })

 add(tv("Connected apps") { AppStyle.section(it) }, 32, 8)
 Integrations.all.forEach { add(card(it), 8, 8) }

 val queuePrefs = getSharedPreferences("queue", MODE_PRIVATE)
 val waiting = queuePrefs.getString("pending_files", "").orEmpty().lines().count { it.isNotBlank() }
 val lastError = queuePrefs.getString("last_upload_error", "").orEmpty()
 add(
 tv(
 "Waiting to upload: $waiting\nLast upload problem: " +
 (if (lastError.isBlank()) "none" else lastError)
 ) { AppStyle.small(it) },
 8, 8
 )
 add(btn("TRY UPLOAD NOW") {
 Integrations.syncNow(this@SettingsActivity)
 Toast.makeText(
 this@SettingsActivity,
 "Upload started. Come back to Settings in a minute to see the result.",
 Toast.LENGTH_LONG
 ).show()
 }, 4)

 add(tv("Send to Zapier / Make (webhook)") { AppStyle.section(it) }, 32, 8)
 val webhookBox = EditText(this).apply {
 hint = "Paste your https:// webhook link"
 setSingleLine(true)
 setText(WebhookSettings.getUrl(this@SettingsActivity))
 AppStyle.input(this)
 }
 add(webhookBox)
 add(btn("SAVE WEBHOOK LINK") {
 val link = webhookBox.text.toString().trim()
 WebhookSettings.setUrl(this@SettingsActivity, link)
 val message = when {
 link.isBlank() -> "Webhook turned off."
 link.startsWith("https://") -> "Webhook link saved."
 else -> "Saved, but the link must start with https:// to be used."
 }
 Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
 }, 10)

 add(tv("Calendars to use for jobs") { AppStyle.section(it) }, 32, 8)
 val calendars = CalendarChoice.list(this)
 if (calendars.isEmpty()) {
 add(tv("No calendars found. Allow Calendar permission on the main screen first.") {
 AppStyle.small(it)
 })
 } else {
 val chosen = (
 CalendarChoice.selectedIds(this)
 ?: calendars.map { it.id }.toSet()
 ).toMutableSet()

 calendars.forEach { cal ->
 add(CheckBox(this).apply {
 text = "${cal.name} (${cal.account})"
 AppStyle.checkbox(this)
 isChecked = cal.id in chosen
 setOnCheckedChangeListener { _, checked ->
 if (checked) chosen.add(cal.id) else chosen.remove(cal.id)
 CalendarChoice.setSelected(this@SettingsActivity, chosen)
 }
 })
 }
 add(tv("After changing, go back and press Refresh jobs from Calendar.") {
 AppStyle.small(it)
 }, 8)
 }

 add(btn("BACK", primary = false) { finish() }, 24)
 }

 private fun card(item: Integration): View {
 val connected = item.isConnected(this)

 return LinearLayout(this).apply {
 orientation = LinearLayout.VERTICAL
 AppStyle.card(this)

 addView(tv(item.title) { AppStyle.section(it) })
 addView(tv(item.description) { AppStyle.body(it) })
 addView(tv(if (connected) "Status: Connected" else "Status: Not connected") {
 AppStyle.small(it)
 it.setPadding(0, 8, 0, 8)
 })

 if (connected) {
 addView(Switch(this@SettingsActivity).apply {
 text = "Send photos here"
 setTextColor(AppStyle.TEXT)
 isChecked = Integrations.isEnabled(this@SettingsActivity, item.id)
 setOnCheckedChangeListener { _, checked ->
 Integrations.setEnabled(this@SettingsActivity, item.id, checked)
 if (checked) Integrations.syncNow(this@SettingsActivity)
 }
 })
 addView(btn("DISCONNECT", primary = false) {
 item.disconnect(this@SettingsActivity)
 render()
 })
 } else {
 addView(btn("CONNECT ${item.title.uppercase()}") {
 item.connect(this@SettingsActivity)
 })
 }
 }
 }

 @Deprecated("Sign-in result")
 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
 super.onActivityResult(requestCode, resultCode, data)
 Integrations.all.firstOrNull { it.requestCode == requestCode }?.let {
 it.handleResult(this, data)
 Integrations.syncNow(this)
 render()
 }
 }
}
