package com.hookway.jobcamera

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object WebhookSettings {

 private fun prefs(context: Context) =
 context.getSharedPreferences("webhook", Context.MODE_PRIVATE)

 fun getUrl(context: Context): String =
 prefs(context).getString("url", "").orEmpty().trim()

 fun setUrl(context: Context, url: String) {
 prefs(context).edit().putString("url", url.trim()).apply()
 }

 fun isReady(context: Context): Boolean =
 getUrl(context).startsWith("https://")

 // Call this from a background thread only. Returns true when sent,
 // or when no link is set, so nothing is held up.
 fun send(context: Context, job: String, fileName: String, type: String): Boolean {
 if (!isReady(context)) return true

 return try {
 val business = context
 .getSharedPreferences("company_settings", Context.MODE_PRIVATE)
 .getString("business_name", "")
 .orEmpty()

 val body = JSONObject()
 .put("business", business)
 .put("job", job)
 .put("file", fileName)
 .put("type", type)
 .put("sentAt", System.currentTimeMillis())
 .toString()

 val connection = URL(getUrl(context)).openConnection() as HttpURLConnection
 connection.requestMethod = "POST"
 connection.connectTimeout = 15000
 connection.readTimeout = 15000
 connection.doOutput = true
 connection.setRequestProperty("Content-Type", "application/json")
 connection.outputStream.use { it.write(body.toByteArray()) }

 val ok = connection.responseCode in 200..299
 connection.disconnect()
 ok
 } catch (_: Exception) {
 false
 }
 }
}
