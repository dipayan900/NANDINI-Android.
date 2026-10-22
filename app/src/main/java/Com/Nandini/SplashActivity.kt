package com.dipayan.nandini

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0
    private lateinit var bar: ProgressBar
    private lateinit var percent: TextView
    private val tick = object : Runnable {
        override fun run() {
            progress = (progress + 8).coerceAtMost(100)
            bar.progress = progress
            percent.text = "$progress%"
            if (progress < 100) handler.postDelayed(this, 35L)
            else handler.postDelayed({
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 4, 24)
        window.navigationBarColor = Color.rgb(9, 4, 24)
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(36, 10, 70), Color.rgb(10, 5, 28), Color.rgb(5, 3, 18))
            )
        }
        val glow = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(110, 155, 67, 255), Color.TRANSPARENT)
            )
        }
        root.addView(glow, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        
        content.addView(TextView(this).apply {
            text = "✦"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 173, 255))
            setShadowLayer(24f, 0f, 0f, Color.MAGENTA)
        }, LinearLayout.LayoutParams(-1, 70))
        content.addView(TextView(this).apply {
            text = "N A N D I N I"
            textSize = 30f
            gravity = Gravity.CENTER
            letterSpacing = .10f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(18f, 0f, 0f, Color.rgb(163, 70, 255))
        }, LinearLayout.LayoutParams(-1, 56))
        content.addView(TextView(this).apply {
            text = "YOUR PERSONAL AI COMPANION"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = .15f
            setTextColor(Color.rgb(218, 194, 255))
        }, LinearLayout.LayoutParams(-1, 34))
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(204, 145, 255))
        }
        content.addView(spinner, LinearLayout.LayoutParams(38, 38).apply {
            gravity = Gravity.CENTER
            topMargin = 28
            bottomMargin = 14
        })
        percent = TextView(this).apply {
            text = "0%"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(percent, LinearLayout.LayoutParams(-1, dp(32)))
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(194, 111, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(90, 235, 205, 255))
        }
        content.addView(bar, LinearLayout.LayoutParams(-1, 7).apply {
            leftMargin = 14
            rightMargin = 14
        })
        content.addView(TextView(this).apply {
            text = "Preparing your universe..."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 201, 245))
        }, LinearLayout.LayoutParams(-1, 42))
        content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val centered = FrameLayout(this).apply {
            // Reserve space for the bottom credit so small screens never clip or overlap the logo.
            setPadding(0, 0, 0, dp(42))
            clipChildren = false
            clipToPadding = false
            addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        root.addView(centered, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(this).apply {
            text = "CREATED WITH ♡ BY DIPAYAN"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = .10f
            setTextColor(Color.rgb(192, 145, 246))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }, FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM))
        setContentView(root)
        handler.postDelayed(tick, 40L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
