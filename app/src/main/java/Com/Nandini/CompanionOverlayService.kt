package com.dipayan.nandini

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Lightweight, locally-rendered companion overlay. No screenshots or audio are captured here.
 * Users can drag the companion, tap to expand/collapse, and close it explicitly.
 */
class CompanionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var downAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) createOverlay()
        return START_STICKY
    }

    private fun createOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = FrameLayout(this)
        container.setPadding(dp(6), dp(6), dp(6), dp(6))
        container.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(238, 31, 12, 49), Color.argb(230, 65, 19, 82))
        ).apply {
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), Color.rgb(230, 115, 255))
        }

        val avatar = AnimeCompanionView()
        container.addView(avatar, FrameLayout.LayoutParams(dp(86), dp(106), Gravity.CENTER))
        val close = TextView(this).apply {
            text = "×"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(111, 42, 134))
                setStroke(dp(1), Color.rgb(255, 132, 190))
            }
        }
        container.addView(close, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END))
        close.setOnClickListener {
            removeOverlay()
            stopSelf()
        }

        val lp = WindowManager.LayoutParams(
            dp(if (expanded) 116 else 96),
            dp(if (expanded) 138 else 116),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(3) || abs(dy) > dp(3)) {
                        lp.x = (startX + dx).coerceAtLeast(0)
                        lp.y = (startY + dy).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(container, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downX) > dp(8) || abs(event.rawY - downY) > dp(8)
                    if (!moved && System.currentTimeMillis() - downAt < 500L) {
                        expanded = !expanded
                        avatar.setSpeaking(expanded)
                        container.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100)
                            .withEndAction { container.animate().scaleX(1f).scaleY(1f).setDuration(140).start() }.start()
                    }
                    true
                }
                else -> true
            }
        }
        params = lp
        root = container
        runCatching { wm.addView(container, lp) }.onFailure {
            root = null
            stopSelf()
        }
        container.alpha = 0f
        container.scaleX = 0.7f
        container.scaleY = 0.7f
        container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360)
            .setInterpolator(android.view.animation.OvershootInterpolator()).start()
    }

    private fun removeOverlay() {
        root?.let { view -> runCatching { wm.removeView(view) } }
        root = null
        params = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_CLOSE = "com.dipayan.nandini.COMPANION_CLOSE"
    }

    private inner class AnimeCompanionView : View(this@CompanionOverlayService) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var blink = false
        private var speaking = false
        private val face = RectF()
        private val hair = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tick = Runnable {
            blink = !blink
            invalidate()
            postDelayed(this@AnimeCompanionView.tick, if (blink) 140L else 2200L)
        }

        init {
            postDelayed(tick, 1200L)
        }

        fun setSpeaking(value: Boolean) {
            speaking = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w * .5f
            // soft aura
            p.shader = android.graphics.RadialGradient(cx, h * .47f, w * .52f,
                intArrayOf(Color.argb(110, 222, 92, 255), Color.argb(0, 122, 45, 255)),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, h * .48f, w * .5f, p)
            p.shader = null
            // flowing purple hair silhouette
            p.color = Color.rgb(54, 23, 101)
            canvas.drawOval(w*.12f, h*.08f, w*.88f, h*.72f, p)
            p.color = Color.rgb(115, 48, 173)
            canvas.drawOval(w*.20f, h*.09f, w*.80f, h*.66f, p)
            // face
            face.set(w*.28f, h*.19f, w*.72f, h*.62f)
            p.color = Color.rgb(255, 218, 231)
            canvas.drawOval(face, p)
            // bangs
            p.color = Color.rgb(47, 18, 88)
            val fringe = android.graphics.Path().apply {
                moveTo(w*.20f, h*.37f); quadTo(w*.18f,h*.10f,w*.49f,h*.13f)
                quadTo(w*.82f,h*.08f,w*.81f,h*.38f)
                quadTo(w*.68f,h*.26f,w*.60f,h*.30f)
                quadTo(w*.49f,h*.20f,w*.42f,h*.34f)
                quadTo(w*.31f,h*.25f,w*.20f,h*.37f); close()
            }
            canvas.drawPath(fringe, p)
            // eyes
            p.color = Color.rgb(95, 53, 193)
            if (!blink) {
                canvas.drawOval(w*.36f,h*.39f,w*.44f,h*.46f,p)
                canvas.drawOval(w*.56f,h*.39f,w*.64f,h*.46f,p)
                p.color = Color.WHITE
                canvas.drawCircle(w*.395f,h*.405f,w*.014f,p)
                canvas.drawCircle(w*.595f,h*.405f,w*.014f,p)
            } else {
                p.strokeWidth = dp(1.5).toFloat()
                p.style = Paint.Style.STROKE
                canvas.drawArc(w*.35f,h*.40f,w*.45f,h*.45f,5f,170f,false,p)
                canvas.drawArc(w*.55f,h*.40f,w*.65f,h*.45f,5f,170f,false,p)
                p.style = Paint.Style.FILL
            }
            // blush and mouth
            p.color = Color.argb(110, 255, 105, 160)
            canvas.drawOval(w*.29f,h*.47f,w*.39f,h*.51f,p)
            canvas.drawOval(w*.61f,h*.47f,w*.71f,h*.51f,p)
            p.color = Color.rgb(177, 54, 120)
            if (speaking) {
                canvas.drawOval(w*.45f,h*.50f,w*.55f,h*.56f,p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(1.5).toFloat()
                canvas.drawArc(w*.43f,h*.49f,w*.57f,h*.56f,20f,140f,false,p)
                p.style = Paint.Style.FILL
            }
            // collar and outfit
            p.color = Color.rgb(122, 54, 190)
            val body = android.graphics.Path().apply {
                moveTo(w*.25f,h*.65f); quadTo(cx,h*.55f,w*.75f,h*.65f)
                lineTo(w*.92f,h*.96f); lineTo(w*.08f,h*.96f); close()
            }
            canvas.drawPath(body,p)
            p.color = Color.rgb(255, 132, 190)
            canvas.drawCircle(cx,h*.69f,dp(3).toFloat(),p)
            // label
            p.color = Color.rgb(255, 225, 250)
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            p.textSize = dp(10).toFloat()
            canvas.drawText("NANDINI",cx,h*.99f,p)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(tick)
            super.onDetachedFromWindow()
        }
    }
}
