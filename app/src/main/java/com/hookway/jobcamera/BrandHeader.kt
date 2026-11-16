package com.hookway.jobcamera

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object BrandHeader {
    fun create(context: Context): LinearLayout {
        fun dp(value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.hookway_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Hookway Restoration logo"
        }
        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(76), dp(82)).apply {
                marginEnd = dp(14)
            }
        )

        val title = TextView(context).apply {
            text = "HOOKWAY RESTORATION\nJOB CAMERA"
            textSize = 22f
            setTextColor(0xFFD4AF37.toInt())
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.05f
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        return header
    }
}
