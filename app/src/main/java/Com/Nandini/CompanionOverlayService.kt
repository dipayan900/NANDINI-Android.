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
        container.setPadding(0, 0, 0, 0)
        // Fully transparent overlay: only the character and small close control are drawn.
        container.setBackgroundColor(Color.TRANSPARENT)

        val avatar = AnimeCompanionView()
        container.addView(avatar, FrameLayout.LayoutParams(dp(174), dp(300), Gravity.CENTER))
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
            dp(if (expanded) 190 else 174),
            dp(if (expanded) 320 else 300),
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
        private var sway = 0f
        private var blinkOpen = true
        private val face = RectF()
        private val hair = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tick: Runnable = object : Runnable {
            override fun run() {
                // Tiny idle motion and irregular, natural-looking blinks without a busy render loop.
                sway = if (sway > 0f) -0.8f else 0.8f
                blink = true
                invalidate()
                postDelayed({
                    blink = false
                    invalidate()
                    postDelayed(this, 1800L + (System.nanoTime() % 2600L))
                }, 115L)
            }
        }

        init {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            postDelayed(tick, 900L + (System.nanoTime() % 1700L))
        }

        fun setSpeaking(value: Boolean) {
            speaking = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w * .5f
            val unit = w / 174f
            canvas.save()
            canvas.translate(sway * unit, 0f)

            // Subtle glow is drawn directly on transparent pixels; no opaque panel behind the character.
            p.shader = android.graphics.RadialGradient(cx, h * .24f, w * .46f,
                intArrayOf(Color.argb(52, 180, 95, 255), Color.TRANSPARENT),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, h * .24f, w * .46f, p)
            p.shader = null

            // Long violet hair behind the body.
            p.color = Color.rgb(48, 21, 91)
            canvas.drawOval(w*.20f, h*.035f, w*.80f, h*.43f, p)
            val hairTail = android.graphics.Path().apply {
                moveTo(w*.24f,h*.22f); quadTo(w*.08f,h*.42f,w*.20f,h*.55f)
                quadTo(w*.31f,h*.44f,w*.34f,h*.31f); close()
                moveTo(w*.76f,h*.22f); quadTo(w*.92f,h*.42f,w*.80f,h*.55f)
                quadTo(w*.69f,h*.44f,w*.66f,h*.31f); close()
            }
            canvas.drawPath(hairTail,p)

            // Neck and a complete standing silhouette.
            p.color = Color.rgb(255, 211, 226)
            canvas.drawRoundRect(w*.455f,h*.255f,w*.545f,h*.335f,w*.025f,w*.025f,p)
            p.color = Color.rgb(255, 211, 226)
            val arms = android.graphics.Path().apply {
                moveTo(w*.34f,h*.34f); quadTo(w*.20f,h*.40f,w*.25f,h*.53f)
                lineTo(w*.30f,h*.52f); lineTo(w*.40f,h*.40f)
                moveTo(w*.66f,h*.34f); quadTo(w*.80f,h*.40f,w*.75f,h*.53f)
                lineTo(w*.70f,h*.52f); lineTo(w*.60f,h*.40f)
            }
            p.style = Paint.Style.FILL
            canvas.drawPath(arms,p)

            // Legs and shoes.
            p.color = Color.rgb(255, 211, 226)
            canvas.drawRoundRect(w*.405f,h*.67f,w*.485f,h*.91f,w*.035f,w*.035f,p)
            canvas.drawRoundRect(w*.515f,h*.67f,w*.595f,h*.91f,w*.035f,w*.035f,p)
            p.color = Color.rgb(62, 28, 100)
            canvas.drawRoundRect(w*.385f,h*.89f,w*.49f,h*.93f,w*.02f,w*.02f,p)
            canvas.drawRoundRect(w*.51f,h*.89f,w*.615f,h*.93f,w*.02f,w*.02f,p)

            // Short, modest purple dress with layered skirt and bright trim.
            p.color = Color.rgb(106, 45, 168)
            val dress = android.graphics.Path().apply {
                moveTo(w*.39f,h*.30f); lineTo(w*.61f,h*.30f)
                lineTo(w*.70f,h*.52f); quadTo(cx,h*.58f,w*.30f,h*.52f); close()
            }
            canvas.drawPath(dress,p)
            p.color = Color.rgb(205, 119, 255)
            p.strokeWidth = dp(1).toFloat()
            p.style = Paint.Style.STROKE
            canvas.drawLine(w*.31f,h*.52f,w*.69f,h*.52f,p)
            p.style = Paint.Style.FILL
            canvas.drawRoundRect(w*.455f,h*.32f,w*.545f,h*.345f,w*.01f,w*.01f,p)

            // Face, hair cap and bangs.
            face.set(w*.285f,h*.085f,w*.715f,h*.29f)
            p.color = Color.rgb(255, 220, 232)
            canvas.drawOval(face,p)
            p.color = Color.rgb(70, 27, 125)
            canvas.drawOval(w*.245f,h*.045f,w*.755f,h*.235f,p)
            p.color = Color.rgb(54, 19, 98)
            val fringe = android.graphics.Path().apply {
                moveTo(w*.25f,h*.18f); quadTo(w*.20f,h*.04f,w*.49f,h*.055f)
                quadTo(w*.80f,h*.035f,w*.75f,h*.18f)
                quadTo(w*.66f,h*.13f,w*.59f,h*.16f)
                quadTo(w*.48f,h*.09f,w*.40f,h*.17f)
                quadTo(w*.32f,h*.12f,w*.25f,h*.18f); close()
            }
            canvas.drawPath(fringe,p)

            // Random blink closes briefly; eye highlights and blush remain lightweight Canvas primitives.
            p.color = Color.rgb(71, 37, 151)
            if (!blink) {
                canvas.drawOval(w*.365f,h*.165f,w*.445f,h*.205f,p)
                canvas.drawOval(w*.555f,h*.165f,w*.635f,h*.205f,p)
                p.color = Color.WHITE
                canvas.drawCircle(w*.395f,h*.176f,w*.012f,p)
                canvas.drawCircle(w*.585f,h*.176f,w*.012f,p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2).toFloat()
                canvas.drawArc(w*.36f,h*.18f,w*.45f,h*.205f,5f,170f,false,p)
                canvas.drawArc(w*.55f,h*.18f,w*.64f,h*.205f,5f,170f,false,p)
                p.style = Paint.Style.FILL
            }
            p.color = Color.argb(105,255,105,160)
            canvas.drawOval(w*.30f,h*.205f,w*.38f,h*.225f,p)
            canvas.drawOval(w*.62f,h*.205f,w*.70f,h*.225f,p)
            p.color = Color.rgb(177,54,120)
            if (speaking) canvas.drawOval(w*.475f,h*.225f,w*.525f,h*.248f,p)
            else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(1).toFloat()
                canvas.drawArc(w*.46f,h*.22f,w*.54f,h*.245f,20f,140f,false,p)
                p.style = Paint.Style.FILL
            }

            // Small highlight ornaments.
            p.color = Color.rgb(255,190,245)
            canvas.drawCircle(w*.285f,h*.145f,w*.018f,p)
            canvas.drawCircle(w*.715f,h*.145f,w*.018f,p)
            canvas.restore()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(tick)
            super.onDetachedFromWindow()
        }
    }
}
