package com.hookway.jobcamera

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object AppStyle {
 val BLACK = 0xFF0D0D0D.toInt()
 val PANEL = 0xFF1A1A1A.toInt()
 val GOLD = 0xFFD4AF37.toInt()
 val WOOD = 0xFF8B5A2B.toInt()
 val TEXT = 0xFFF5F0E6.toInt()
 val MUTED = 0xFFB8AE98.toInt()

 fun dp(view: View, value: Int): Int =
 (value * view.resources.displayMetrics.density).toInt()

 private fun box(view: View, fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int): GradientDrawable =
 GradientDrawable().apply {
 setColor(fill)
 cornerRadius = radiusDp * view.resources.displayMetrics.density
 setStroke((strokeDp * view.resources.displayMetrics.density).toInt(), stroke)
 }

 fun primaryButton(b: Button) {
 b.background = box(b, GOLD, WOOD, 2, 12)
 b.setTextColor(BLACK)
 b.typeface = Typeface.DEFAULT_BOLD
 b.setPadding(dp(b, 16), dp(b, 12), dp(b, 16), dp(b, 12))
 }

 fun secondaryButton(b: Button) {
 b.background = box(b, BLACK, GOLD, 2, 12)
 b.setTextColor(GOLD)
 b.typeface = Typeface.DEFAULT_BOLD
 b.setPadding(dp(b, 16), dp(b, 12), dp(b, 16), dp(b, 12))
 }

 fun title(t: TextView) {
 t.setTextColor(GOLD)
 t.textSize = 26f
 t.typeface = Typeface.DEFAULT_BOLD
 t.letterSpacing = 0.08f
 }

 fun section(t: TextView) {
 t.setTextColor(GOLD)
 t.textSize = 18f
 t.typeface = Typeface.DEFAULT_BOLD
 }

 fun body(t: TextView) {
 t.setTextColor(TEXT)
 t.textSize = 16f
 }

 fun small(t: TextView) {
 t.setTextColor(MUTED)
 t.textSize = 14f
 }

 fun input(e: EditText) {
 e.setTextColor(TEXT)
 e.setHintTextColor(MUTED)
 e.background = box(e, PANEL, WOOD, 1, 10)
 e.setPadding(dp(e, 14), dp(e, 12), dp(e, 14), dp(e, 12))
 }

 fun checkbox(c: CheckBox) {
 c.setTextColor(TEXT)
 c.buttonTintList = ColorStateList.valueOf(GOLD)
 }

 fun card(l: LinearLayout) {
 l.background = box(l, PANEL, WOOD, 1, 14)
 l.setPadding(dp(l, 16), dp(l, 14), dp(l, 16), dp(l, 14))
 }
}
