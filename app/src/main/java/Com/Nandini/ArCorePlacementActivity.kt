package com.dipayan.nandini

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * ARCore world placement for the existing transparent VRM WebGL companion.
 * ARCore owns camera tracking, plane detection and a persistent world anchor;
 * the companion is positioned over the live camera background at the anchor's
 * projected screen position. It is a camera-facing companion billboard, not
 * a depth-occluding volumetric hologram.
 */
class ArCorePlacementActivity : Activity(), GLSurfaceView.Renderer {
    private lateinit var glView: GLSurfaceView
    private lateinit var avatar: Anime3DView
    private lateinit var status: TextView
    private lateinit var renderer: CameraBackgroundRenderer
    @Volatile private var session: Session? = null
    private var installRequested = false
    private var lastUiUpdate = 0L
    private val requestCamera = 7412

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = false
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    renderer.queueTap(event.x, event.y)
                    true
                } else true
            }
        }
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))

        avatar = Anime3DView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        val avatarParams = FrameLayout.LayoutParams(dp(300), dp(460), Gravity.TOP or Gravity.START)
        root.addView(avatar, avatarParams)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9000000.toInt(), 0x00000000)
            )
        }
        header.addView(TextView(this).apply {
            text = "A R U S H I   •   ROOM AR"
            textSize = 16f
            letterSpacing = .10f
            gravity = Gravity.CENTER
            setTextColor(0xFFE5C5FF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        status = TextView(this).apply {
            text = "Move your phone slowly to find a horizontal surface."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
        }
        header.addView(status)
        root.addView(header, FrameLayout.LayoutParams(-1, dp(76), Gravity.TOP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(16))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xDD080611.toInt(), 0x00080611)
            )
        }
        controls.addView(control("RESET PLACEMENT") {
            renderer.queueReset()
            avatar.visibility = View.GONE
            status.text = "Tap a detected floor or table to place ARUSHI."
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        controls.addView(control("CLOSE") { finish() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        renderer = CameraBackgroundRenderer(this)
        glView.setRenderer(this)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCamera)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startArSession()
        }
    }

    private fun startArSession() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (session == null) {
                val created = Session(this)
                val config = Config(created)
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                created.configure(config)
                session = created
                renderer.session = created
            }
            session?.resume()
            glView.onResume()
            status.text = "Move slowly. When a surface is detected, tap it to place ARUSHI."
        } catch (e: Exception) {
            showError("ARCore could not start: ${e.message ?: "device or camera unavailable"}")
        }
    }

    override fun onPause() {
        glView.onPause()
        try { session?.pause() } catch (_: Exception) { }
        super.onPause()
    }

    override fun onDestroy() {
        try { session?.close() } catch (_: Exception) { }
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCamera) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startArSession()
            else showError("Camera permission is needed for room AR. You can still use 3D Stage and Pyramid mode.")
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            status.text = message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun control(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 10f
        letterSpacing = .04f
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xCC25143B.toInt())
            setStroke(dp(1), 0xCC9C72D4.toInt())
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(windowManager.defaultDisplay.rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val active = session ?: return
        try {
            active.setDisplayGeometry(windowManager.defaultDisplay.rotation, glView.width, glView.height)
            val frame = active.update()
            renderer.drawCamera(frame)
            processTap(frame)
            updateAnchor(frame)
        } catch (e: CameraNotAvailableException) {
            showError("Camera became unavailable. Close other camera apps and reopen Room AR.")
        } catch (e: Exception) {
            android.util.Log.w("ARUSHI_AR", "AR frame update failed", e)
        }
    }

    private fun processTap(frame: Frame) {
        val tap = renderer.consumeTap() ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        try {
            val hit = frame.hitTest(tap.first, tap.second).firstOrNull { result ->
                val plane = result.trackable
                plane is Plane && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    plane.trackingState == TrackingState.TRACKING && plane.isPoseInPolygon(result.hitPose)
            }
            if (hit != null) {
                renderer.setAnchor(hit.createAnchor())
                runOnUiThread { status.text = "ARUSHI placed. Move around to view the anchored companion." }
            } else {
                runOnUiThread { status.text = "No horizontal surface there yet. Scan slowly, then tap the floor or table." }
            }
        } catch (e: Exception) {
            android.util.Log.w("ARUSHI_AR", "Plane hit test failed", e)
        }
    }

    private fun updateAnchor(frame: Frame) {
        val anchor = renderer.anchor ?: return
        if (anchor.trackingState != TrackingState.TRACKING || frame.camera.trackingState != TrackingState.TRACKING) {
            runOnUiThread { avatar.visibility = View.INVISIBLE; status.text = "Tracking paused. Move the phone slowly to recover the surface." }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdate < 33L) return
        lastUiUpdate = now
        val view = FloatArray(16)
        val projection = FloatArray(16)
        val combined = FloatArray(16)
        val clip = FloatArray(4)
        frame.camera.getViewMatrix(view, 0)
        frame.camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
        Matrix.multiplyMM(combined, 0, projection, 0, view, 0)
        val pose = anchor.pose
        Matrix.multiplyMV(clip, 0, combined, 0, floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f), 0)
        if (clip[3] <= 0f) return
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        val screenX = (ndcX + 1f) * 0.5f * glView.width
        val screenY = (1f - ndcY) * 0.5f * glView.height
        if (screenX < -100 || screenX > glView.width + 100 || screenY < -100 || screenY > glView.height + 100) {
            runOnUiThread { avatar.visibility = View.INVISIBLE }
            return
        }
        val cameraPose = frame.camera.pose
        val dx = pose.tx() - cameraPose.tx()
        val dy = pose.ty() - cameraPose.ty()
        val dz = pose.tz() - cameraPose.tz()
        val distance = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.35f)
        val scale = (1.35f / distance).coerceIn(0.48f, 1.45f)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (avatar.width > 0 && avatar.height > 0) {
                avatar.pivotX = avatar.width / 2f
                avatar.pivotY = avatar.height.toFloat()
                avatar.x = screenX - avatar.width / 2f
                avatar.y = screenY - avatar.height.toFloat()
                avatar.scaleX = scale
                avatar.scaleY = scale
                avatar.visibility = View.VISIBLE
            }
        }
    }

    private class CameraBackgroundRenderer(private val activity: ArCorePlacementActivity) {
        @Volatile var session: Session? = null
        @Volatile var anchor: Anchor? = null
            private set
        private var textureId = 0
        private var program = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var textureHandle = 0
        private var hasTexture = false
        private val vertices = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val inputUv = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        private val outputUv = floatBuffer(FloatArray(8))
        @Volatile private var pendingTap: Pair<Float, Float>? = null
        @Volatile private var resetPending = false

        fun queueTap(x: Float, y: Float) { pendingTap = x to y }
        fun consumeTap(): Pair<Float, Float>? {
            val value = pendingTap
            pendingTap = null
            return value
        }
        fun queueReset() { resetPending = true }
        fun setAnchor(value: Anchor) {
            anchor?.detach()
            anchor = value
        }

        fun createOnGlThread() {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
            hasTexture = false
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }

        fun drawCamera(frame: Frame) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val active = session ?: return
            if (!hasTexture) {
                active.setCameraTextureName(textureId)
                hasTexture = true
            }
            if (resetPending) {
                anchor?.detach()
                anchor = null
                resetPending = false
                activity.runOnUiThread {
                    activity.avatar.visibility = View.GONE
                    activity.status.text = "Tap a detected horizontal surface to place ARUSHI."
                }
            }
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, inputUv,
                Coordinates2d.TEXTURE_NORMALIZED, outputUv
            )
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureHandle, 0)
            vertices.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
            outputUv.position(0)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, outputUv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }

        private fun createProgram(vertex: String, fragment: String): Int {
            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
            val result = GLES20.glCreateProgram()
            GLES20.glAttachShader(result, vs)
            GLES20.glAttachShader(result, fs)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) throw IllegalStateException("AR camera shader link failed: " + GLES20.glGetProgramInfoLog(result))
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return result
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) throw IllegalStateException("AR camera shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
            return shader
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """
            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                uniform samplerExternalOES uTexture;
                varying vec2 vTexCoord;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """
        }
    }
}
