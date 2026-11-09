package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Full-screen host for ARUSHI's existing 3D companion renderer. */
class HologramStudioActivity : Activity() {
    private val palette get() = ArushiExperience.palette(this)
    private val bg get() = palette.background
    private val accent get() = palette.accent
    private val muted get() = palette.muted
    private lateinit var companion: Anime3DView
    private lateinit var modeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        companion = Anime3DView(this)
        root.addView(companion, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(10))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC10091D.toInt(), 0x0010091D)
            )
        }
        top.addView(TextView(this).apply {
            text = "A R U S H I"
            textSize = 20f
            letterSpacing = .16f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        })
        modeLabel = TextView(this).apply {
            text = "HOLOGRAPHIC STAGE  •  3D COMPANION"
            textSize = 9f
            letterSpacing = .08f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
        }
        top.addView(modeLabel)
        root.addView(top, FrameLayout.LayoutParams(-1, dp(78), Gravity.TOP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(18))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xDD090611.toInt(), 0x00090611)
            )
        }
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        modes.addView(control("3D STAGE") { setMode(false) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        modes.addView(control("PYRAMID ×4") { setMode(true) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        controls.addView(modes)

        val lower = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        lower.addView(control("AR ROOM AR") {
            try {
                startActivity(android.content.Intent(this@HologramStudioActivity, ArRoomActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this@HologramStudioActivity, "Couldn't open AR Room: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        lower.addView(control("CLOSE") { finish() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        controls.addView(lower)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        setMode(false)
    }

    private fun setMode(pyramid: Boolean) {
        companion.setHologramMode(if (pyramid) "pyramid" else "standard")
        modeLabel.text = if (pyramid) "PEPPER'S GHOST  •  FOUR-VIEW OUTPUT" else "HOLOGRAPHIC STAGE  •  3D COMPANION"
        if (pyramid) Toast.makeText(this, "Place a small clear reflective pyramid at the centre of the screen.", Toast.LENGTH_LONG).show()
    }

    private fun showArCoreInfo() {
        AlertDialog.Builder(this)
            .setTitle("AR room placement")
            .setMessage(
                "This build includes the on-screen 3D stage and four-view Pepper's Ghost output. " +
                "True ARCore placement needs a separate camera-session and world-anchor renderer, " +
                "plus confirmation that this exact phone model is ARCore-certified. " +
                "That integration is deliberately not simulated by placing a flat avatar over a camera image."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun control(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 10f
            letterSpacing = .04f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(0xCC25143B.toInt())
                setStroke(dp(1), 0xCC9C72D4.toInt())
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
