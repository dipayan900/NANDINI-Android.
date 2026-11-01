package com.dipayan.nandini

import android.graphics.Color
import android.graphics.Typeface
import android.opengl.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ARCore tracks a real-world anchor. The same WebView/Three.js/three-vrm renderer
 * used by the companion screen is drawn transparently over the AR camera. This
 * preserves VRM facial expressions, blink, mouth motion and procedural animations.
 */
class ArRoomActivity : FragmentActivity() {
    private val bg = Color.rgb(7, 5, 14)
    private val accent = Color.rgb(210, 164, 255)
    private var companionView: Anime3DView? = null
    private var placedAnchor: Anchor? = null
    private var statusView: TextView? = null
    private var lastProjectionAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val fragmentContainer = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(fragmentContainer, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        val ar = ArFragment()
        supportFragmentManager.beginTransaction()
            .replace(fragmentContainer.id, ar, "arushi_ar_fragment")
            .commitNow()

        val renderer = Anime3DView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            companionView = this
            setCompanionVisible(false)
            setCompanionEmotion("neutral")
            setCompanionState("idle")
            setOnTouchListener { _, event ->
                ar.arSceneView.dispatchTouchEvent(MotionEvent.obtain(event))
                true
            }
        }
        root.addView(renderer, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildTopPanel(), FrameLayout.LayoutParams(-1, dp(108), Gravity.TOP))
        root.addView(buildBottomPanel(), FrameLayout.LayoutParams(-1, dp(116), Gravity.BOTTOM))

        ar.setOnTapArPlaneListener { hitResult, _, _ ->
            placedAnchor?.detach()
            placedAnchor = hitResult.createAnchor()
            statusView?.text = "ARUSHI placed • move around to view from another angle"
        }
        ar.arSceneView.scene.addOnUpdateListener {
            updateAnchoredCompanion(ar)
        }
    }

    private fun updateAnchoredCompanion(ar: ArFragment) {
        val anchor = placedAnchor ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastProjectionAt < 25L) return
        lastProjectionAt = now
        val frame = ar.arSceneView.arFrame ?: return
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING || anchor.trackingState != TrackingState.TRACKING) {
            companionView?.setCompanionVisible(false)
            statusView?.text = "Tracking… move your phone slowly"
            return
        }

        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val anchorMatrix = FloatArray(16)
        val viewAnchor = FloatArray(16)
        val clipMatrix = FloatArray(16)
        val clip = FloatArray(4)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        anchor.pose.toMatrix(anchorMatrix, 0)
        Matrix.multiplyMM(viewAnchor, 0, viewMatrix, 0, anchorMatrix, 0)
        Matrix.multiplyMM(clipMatrix, 0, projectionMatrix, 0, viewAnchor, 0)
        Matrix.multiplyMV(clip, 0, clipMatrix, 0, floatArrayOf(0f, 0f, 0f, 1f), 0)

        val w = clip[3]
        if (w <= 0f) {
            companionView?.setCompanionVisible(false)
            return
        }
        val ndcX = clip[0] / w
        val ndcY = clip[1] / w
        val screenX = (ndcX + 1f) * 0.5f
        val screenY = (1f - ndcY) * 0.5f
        val rootWidth = companionView?.width?.toFloat()?.takeIf { it > 0f } ?: return
        val rootHeight = companionView?.height?.toFloat()?.takeIf { it > 0f } ?: return

        val cameraPose = camera.pose
        val anchorPose = anchor.pose
        val dx = anchorPose.tx() - cameraPose.tx()
        val dy = anchorPose.ty() - cameraPose.ty()
        val dz = anchorPose.tz() - cameraPose.tz()
        val distance = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.35f)

        // Convert ARCore's projected ground anchor to the VRM renderer's camera plane.
        val aspect = rootWidth / rootHeight
        val tanHalfFov = tan(Math.toRadians(19.0)).toFloat()
        val xOffset = ndcX * 2.75f * tanHalfFov * aspect
        val baseAnchorNdcY = -0.76f / (2.75f * tanHalfFov)
        val yOffset = (ndcY - baseAnchorNdcY) * 2.75f * tanHalfFov
        val scale = (2.75f / distance).coerceIn(0.25f, 3.5f)
        val visible = screenX in -0.25f..1.25f && screenY in -0.25f..1.25f
        companionView?.setARPlacement(xOffset, yOffset, scale, visible)
        statusView?.text = "AR tracking active • ${"%.1f".format(distance)} m"
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
            text = "Scan slowly, then tap a detected floor or table to place ARUSHI."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, 0)
        })
    }

    private fun buildBottomPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(7), dp(10), dp(9))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xE610091D.toInt(), 0x0010091D)
        )
        statusView = TextView(this@ArRoomActivity).apply {
            text = "Scanning for a surface…"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFFE2D5F5.toInt())
        }
        addView(statusView, LinearLayout.LayoutParams(-1, dp(22)))
        val moods = LinearLayout(this@ArRoomActivity).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        listOf("NEUTRAL" to "neutral", "HAPPY" to "happy", "CALM" to "calm",
            "SAD" to "sad", "SURPRISED" to "surprised", "SERIOUS" to "serious").forEach { (label, emotion) ->
            moods.addView(TextView(this@ArRoomActivity).apply {
                text = label
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(6), dp(8), dp(6), dp(8))
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(9).toFloat()
                    setColor(0xCC351C50.toInt())
                    setStroke(dp(1), 0xCC9C72D4.toInt())
                }
                setOnClickListener {
                    companionView?.setCompanionEmotion(emotion)
                    statusView?.text = "Expression: $label • VRM facial morphs active"
                }
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        addView(moods, LinearLayout.LayoutParams(-1, dp(38)))
        addView(TextView(this@ArRoomActivity).apply {
            text = "CLOSE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(7), dp(18), dp(7))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xCC351C50.toInt())
                setStroke(dp(1), 0xCC9C72D4.toInt())
            }
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-2, dp(32)).apply { topMargin = dp(3) })
    }

    override fun onDestroy() {
        placedAnchor?.detach()
        companionView?.apply {
            setCompanionVisible(false)
            destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
