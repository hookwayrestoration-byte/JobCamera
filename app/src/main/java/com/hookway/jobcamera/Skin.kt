package com.hookway.jobcamera

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

object Skin {

 private fun shape(v: View, fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int): GradientDrawable {
 val d = v.resources.displayMetrics.density
 return GradientDrawable().apply {
 setColor(fill)
 cornerRadius = radiusDp * d
 setStroke((strokeDp * d).toInt(), stroke)
 }
 }

 private fun buttonBackground(v: View, fill: Int, stroke: Int): StateListDrawable =
 StateListDrawable().apply {
 addState(
 intArrayOf(-android.R.attr.state_enabled),
 shape(v, 0xFF2A2A2A.toInt(), 0xFF4A3A28.toInt(), 2, 12)
 )
 addState(intArrayOf(), shape(v, fill, stroke, 2, 12))
 }

 private fun pad(v: View, h: Int, w: Int) {
 val d = v.resources.displayMetrics.density
 v.setPadding((h * d).toInt(), (w * d).toInt(), (h * d).toInt(), (w * d).toInt())
 }

 private fun styleButton(b: Button) {
 val t = b.text.toString().uppercase()
 val gold = t.contains("PHOTO") && !t.contains("IMPORT") || t.contains("VIDEO")
 val textColor = if (gold) AppStyle.BLACK else AppStyle.GOLD
 b.background = if (gold) {
 buttonBackground(b, AppStyle.GOLD, AppStyle.WOOD)
 } else {
 buttonBackground(b, AppStyle.BLACK, AppStyle.GOLD)
 }
 b.setTextColor(
 ColorStateList(
 arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
 intArrayOf(AppStyle.MUTED, textColor)
 )
 )
 b.typeface = Typeface.DEFAULT_BOLD
 pad(b, 16, 12)
 }

 private fun styleInput(e: EditText) {
 e.setTextColor(AppStyle.TEXT)
 e.setHintTextColor(AppStyle.MUTED)
 e.background = shape(e, AppStyle.PANEL, AppStyle.WOOD, 1, 10)
 pad(e, 14, 12)
 }

 private fun styleSpinner(s: Spinner) {
 s.background = shape(s, 0xFFF5F0E6.toInt(), AppStyle.WOOD, 2, 10)
 pad(s, 12, 10)
 }

 private fun styleText(t: TextView) {
 val sp = t.textSize / t.resources.displayMetrics.density
 when {
 sp >= 22f -> {
 t.setTextColor(AppStyle.GOLD)
 t.typeface = Typeface.DEFAULT_BOLD
 t.letterSpacing = 0.08f
 }
 sp <= 13f -> t.setTextColor(AppStyle.MUTED)
 else -> t.setTextColor(AppStyle.TEXT)
 }
 }

 fun restyle(view: View) {
 when (view) {
 is Button -> styleButton(view)
 is EditText -> styleInput(view)
 is Spinner -> styleSpinner(view)
 is TextView -> styleText(view)
 }
 if (view is ViewGroup && view !is Spinner) {
 for (i in 0 until view.childCount) restyle(view.getChildAt(i))
 }
 }
}
