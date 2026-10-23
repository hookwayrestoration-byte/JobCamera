
package com.hookway.jobcamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarInfo(val id: Long, val name: String, val account: String)

object CalendarChoice {

 fun list(context: Context): List<CalendarInfo> {
 if (
 ContextCompat.checkSelfPermission(
 context,
 Manifest.permission.READ_CALENDAR
 ) != PackageManager.PERMISSION_GRANTED
 ) {
 return emptyList()
 }

 val found = mutableListOf<CalendarInfo>()

 try {
 context.contentResolver.query(
 CalendarContract.Calendars.CONTENT_URI,
 arrayOf(
 CalendarContract.Calendars._ID,
 CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
 CalendarContract.Calendars.ACCOUNT_NAME
 ),
 null,
 null,
 CalendarContract.Calendars.ACCOUNT_NAME + " ASC"
 )?.use { cursor ->
 while (cursor.moveToNext()) {
 found.add(
 CalendarInfo(
 cursor.getLong(0),
 cursor.getString(1).orEmpty(),
 cursor.getString(2).orEmpty()
 )
 )
 }
 }
 } catch (_: Exception) {
 }

 return found
 }

 // Returns null when nothing has been chosen yet, which means all calendars.
 fun selectedIds(context: Context): Set<Long>? {
 val prefs = context.getSharedPreferences("calendar_choice", Context.MODE_PRIVATE)
 val raw = prefs.getString("ids", null) ?: return null
 return raw.split(",")
 .mapNotNull { it.trim().toLongOrNull() }
 .toSet()
 }

 fun setSelected(context: Context, ids: Set<Long>) {
 context.getSharedPreferences("calendar_choice", Context.MODE_PRIVATE)
 .edit()
 .putString("ids", ids.joinToString(","))
 .apply()
 }
}
