package com.hookway.jobcamera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {

 private lateinit var list: LinearLayout

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 list = LinearLayout(this).apply {
 orientation = LinearLayout.VERTICAL
 setPadding(28, 32, 28, 24)
 }
 setContentView(ScrollView(this).apply { addView(list) })
 }

 override fun onResume() {
 super.onResume()
 render()
 }

 private fun render() {
 list.removeAllViews()

 list.addView(TextView(this).apply {
 text = "SETTINGS"
 textSize = 24f
 setTextColor(0xFF17324D.toInt())
 setPadding(0, 0, 0, 16)
 })

 val settings = getSharedPreferences("company_settings", MODE_PRIVATE)
 list.addView(TextView(this).apply {
 text = "Business name (used in file names)"
 textSize = 16f
 })
 val nameInput = EditText(this).apply {
 setText(settings.getString("business_name", "").orEmpty())
 setSingleLine(true)
 }
 list.addView(nameInput)
 list.addView(Button(this).apply {
 text = "SAVE BUSINESS NAME"
 setOnClickListener {
 val name = nameInput.text.toString().trim()
 if (name.isBlank()) {
 Toast.makeText(
 this@SettingsActivity,
 "Please enter your business name.",
 Toast.LENGTH_LONG
 ).show()
 } else {
 settings.edit().putString("business_name", name).apply()
 Toast.makeText(
 this@SettingsActivity,
 "Saved.",
 Toast.LENGTH_SHORT
 ).show()
 }
 }
 })

 list.addView(TextView(this).apply {
 text = "How far back to show past jobs (then press Refresh jobs)"
 textSize = 16f
 setPadding(0, 32, 0, 0)
 })
 val options = listOf(
 "3 months" to 90,
 "1 year" to 365,
 "3 years" to 1095,
 "10 years" to 3650
 )
 val current = settings.getInt("history_days", 1095)
 list.addView(Spinner(this).apply {
 adapter = ArrayAdapter(
 this@SettingsActivity,
 android.R.layout.simple_spinner_dropdown_item,
 options.map { it.first }
 )
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

 list.addView(TextView(this).apply {
 text = "Connected apps"
 textSize = 18f
 setPadding(0, 32, 0, 8)
 })
 Integrations.all.forEach { list.addView(card(it)) }

 list.addView(TextView(this).apply {
 text = "Send to Zapier / Make (webhook)"
 textSize = 18f
 setPadding(0, 32, 0, 8)
 })
 val webhookBox = EditText(this).apply {
 hint = "Paste your https:// webhook link"
 setSingleLine(true)
 setText(WebhookSettings.getUrl(this@SettingsActivity))
 }
 list.addView(webhookBox)
 list.addView(Button(this).apply {
 text = "SAVE WEBHOOK LINK"
 setOnClickListener {
 val link = webhookBox.text.toString().trim()
 WebhookSettings.setUrl(this@SettingsActivity, link)
 val message = when {
 link.isBlank() -> "Webhook turned off."
 link.startsWith("https://") -> "Webhook link saved."
 else -> "Saved, but the link must start with https:// to be used."
 }
 Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
 }
 })

 list.addView(TextView(this).apply {
 text = "Calendars to use for jobs"
 textSize = 18f
 setPadding(0, 32, 0, 8)
 })
 val calendars = CalendarChoice.list(this)
 if (calendars.isEmpty()) {
 list.addView(TextView(this).apply {
 text = "No calendars found. Allow Calendar permission on the main screen first."
 textSize = 14f
 })
 } else {
 val chosen = (
 CalendarChoice.selectedIds(this)
 ?: calendars.map { it.id }.toSet()
 ).toMutableSet()

 calendars.forEach { cal ->
 list.addView(CheckBox(this).apply {
 text = "${cal.name} (${cal.account})"
 isChecked = cal.id in chosen
 setOnCheckedChangeListener { _, checked ->
 if (checked) chosen.add(cal.id) else chosen.remove(cal.id)
 CalendarChoice.setSelected(this@SettingsActivity, chosen)
 }
 })
 }
 list.addView(TextView(this).apply {
 text = "After changing, go back and press Refresh jobs from Calendar."
 textSize = 14f
 })
 }

 list.addView(Button(this).apply {
 text = "BACK"
 setOnClickListener { finish() }
 })
 }

 private fun card(item: Integration): View {
 val connected = item.isConnected(this)

 return LinearLayout(this).apply {
 orientation = LinearLayout.VERTICAL
 setPadding(0, 16, 0, 24)

 addView(TextView(this@SettingsActivity).apply {
 text = item.title
 textSize = 18f
 })
 addView(TextView(this@SettingsActivity).apply {
 text = item.description
 textSize = 14f
 })
 addView(TextView(this@SettingsActivity).apply {
 text = if (connected) "Status: Connected" else "Status: Not connected"
 textSize = 14f
 setPadding(0, 8, 0, 8)
 })

 if (connected) {
 addView(Switch(this@SettingsActivity).apply {
 text = "Send photos here"
 isChecked = Integrations.isEnabled(this@SettingsActivity, item.id)
 setOnCheckedChangeListener { _, checked ->
 Integrations.setEnabled(this@SettingsActivity, item.id, checked)
 if (checked) Integrations.syncNow(this@SettingsActivity)
 }
 })
 addView(Button(this@SettingsActivity).apply {
 text = "DISCONNECT"
 setOnClickListener {
 item.disconnect(this@SettingsActivity)
 render()
 }
 })
 } else {
 addView(Button(this@SettingsActivity).apply {
 text = "CONNECT ${item.title.uppercase()}"
 setOnClickListener { item.connect(this@SettingsActivity) }
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
