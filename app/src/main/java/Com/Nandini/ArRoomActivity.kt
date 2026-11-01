package com.dipayan.nandini

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.ar.sceneform.ux.ArFragment

/**
 * Real camera-based AR room placement.
 *
 * ArFragment owns the ARCore session, camera background, plane detection and hit testing.
 * The bundled VRM is a GLB container; Sceneform's glTF loader renders its glTF mesh.
 */
class ArRoomActivity : FragmentActivity() {
    private val bg = Color.rgb(7, 5, 14)
    private val accent = Color.rgb(210, 164, 255)
    private var fragment: ArFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val fragmentContainer = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(fragmentContainer, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildTopPanel(), FrameLayout.LayoutParams(-1, dp(112), Gravity.TOP))
        root.addView(buildBottomPanel(), FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM))
        setContentView(root)

        if (savedInstanceState == null) {
            val ar = ArFragment()
            fragment = ar
            supportFragmentManager.beginTransaction()
                .replace(fragmentContainer.id, ar, "arushi_ar_fragment")
                .commitNow()
            configurePlacement(ar)
        } else {
            fragment = supportFragmentManager.findFragmentByTag("arushi_ar_fragment") as? ArFragment
            fragment?.let(::configurePlacement)
        }
    }

    private fun configurePlacement(ar: ArFragment) {
        runCatching {
            // Detect a real ARCore plane and create a transformable, anchored GLB model on tap.
            ar.setOnTapPlaneGlbModel("nandini-companion.vrm")
        }.onFailure {
            Toast.makeText(this, "AR setup could not start: ${it.localizedMessage ?: "unsupported device"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTopPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xE610091D.toInt(), 0x0010091D)
        )
        addView(TextView(this@ArRoomActivity).apply {
            text = "A R U S H I  •  REAL-WORLD AR"
            textSize = 16f
            letterSpacing = .07f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(accent)
        })
        addView(TextView(this@ArRoomActivity).apply {
            text = "Move your phone slowly to scan the room. Tap a detected floor or table to place ARUSHI."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, 0)
        })
    }

    private fun buildBottomPanel(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(12))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xE610091D.toInt(), 0x0010091D)
        )
        addView(TextView(this@ArRoomActivity).apply {
            text = "ARCore camera + plane tracking • Drag / pinch / rotate after placement"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFFE2D5F5.toInt())
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(TextView(this@ArRoomActivity).apply {
            text = "CLOSE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xCC351C50.toInt())
                setStroke(dp(1), 0xCC9C72D4.toInt())
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
