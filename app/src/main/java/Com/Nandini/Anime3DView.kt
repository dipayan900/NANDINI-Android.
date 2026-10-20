package com.dipayan.nandini

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.View
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight procedural 3D anime-style companion. Uses low-poly shaded meshes,
 * transparent GL surface and sparse idle frames to keep battery/GPU use modest.
 */
class Anime3DView(context: Context, private val chatBackgroundMode: Boolean = false) : GLSurfaceView(context) {
    private val avatarRenderer = AvatarRenderer()
    private val idleRandom = Random()
    private val idleBlinkPulse = object : Runnable {
        override fun run() {
            requestRender()
            // Draw again after the eyelid has had time to close, then sleep again.
            postDelayed({ requestRender() }, 145L)
            postDelayed(this, 1900L + idleRandom.nextInt(3300).toLong())
        }
    }

    init {
        setEGLContextClientVersion(2)
        // Explicit alpha is essential: RGB-only EGL surfaces show as a black rectangle.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(!chatBackgroundMode)
        if (chatBackgroundMode) setZOrderMediaOverlay(true)
        setRenderer(avatarRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        setLayerType(View.LAYER_TYPE_NONE, null)
        postDelayed(idleBlinkPulse, 1600L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(idleBlinkPulse)
        super.onDetachedFromWindow()
    }

    fun setSpeaking(value: Boolean) { avatarRenderer.speaking = value; requestRender() }

    private class AvatarRenderer : Renderer {
        @Volatile var speaking = false
        private var blink = false
        private var nextBlink = System.currentTimeMillis() + 1500
        private var blinkUntil = 0L
        private var angle = 0f
        private var lastFrame = 0L
        private var program = 0
        private var posHandle = 0
        private var mvpHandle = 0
        private var colorHandle = 0
        private var lightHandle = 0
        private lateinit var projection: FloatArray
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val mvp = FloatArray(16)
        private val temp = FloatArray(16)
        private lateinit var sphere: Mesh
        private val random = Random()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            val vs = """
                uniform mat4 uMvp; attribute vec3 aPos; attribute vec3 aNormal;
                varying float vLight;
                void main(){ gl_Position=uMvp*vec4(aPos,1.0); vLight=0.42+0.58*max(dot(normalize(aNormal),normalize(vec3(-0.35,0.65,1.0))),0.0); }
            """.trimIndent()
            val fs = """
                precision mediump float; uniform vec4 uColor; varying float vLight;
                void main(){ gl_FragColor=vec4(uColor.rgb*vLight,uColor.a); }
            """.trimIndent()
            program = link(vs, fs)
            posHandle = GLES20.glGetAttribLocation(program, "aPos")
            lightHandle = GLES20.glGetAttribLocation(program, "aNormal")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            sphere = Mesh.makeSphere(14, 18)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            projection = FloatArray(16)
            val aspect = width.toFloat() / height.coerceAtLeast(1)
            Matrix.frustumM(projection, 0, -aspect * 0.72f, aspect * 0.72f, -1f, 1f, 2f, 18f)
            Matrix.setLookAtM(view, 0, 0f, 0f, 7.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val now = System.currentTimeMillis()
            if (now >= nextBlink && now >= blinkUntil) {
                blink = true; blinkUntil = now + 115
            } else if (blink && now >= blinkUntil) {
                blink = false; nextBlink = now + 1800 + random.nextInt(3400)
            }
            if (now - lastFrame > 1000) {
                angle = if (angle > 0f) -1.5f else 1.5f
                lastFrame = now
            }
            GLES20.glUseProgram(program)
            // Head-to-toe model centered in a transparent window.
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, 0f, 0f)
            Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
            // Hair back
            part(-0.0f, 0.66f, -0.04f, 0.50f, 0.62f, 0.29f, 0.19f, 0.07f, 0.32f)
            // legs
            part(-0.14f, -0.54f, 0f, 0.105f, 0.44f, 0.11f, 1f, 0.79f, 0.86f)
            part(0.14f, -0.54f, 0f, 0.105f, 0.44f, 0.11f, 1f, 0.79f, 0.86f)
            // shoes
            part(-0.15f, -0.79f, 0.03f, 0.15f, 0.075f, 0.19f, 0.16f, 0.06f, 0.27f)
            part(0.15f, -0.79f, 0.03f, 0.15f, 0.075f, 0.19f, 0.16f, 0.06f, 0.27f)
            // arms
            part(-0.32f, 0.04f, 0f, 0.09f, 0.39f, 0.10f, 1f, 0.79f, 0.86f, -12f)
            part(0.32f, 0.04f, 0f, 0.09f, 0.39f, 0.10f, 1f, 0.79f, 0.86f, 12f)
            // dress torso and flared skirt
            part(0f, 0.11f, 0.02f, 0.29f, 0.34f, 0.19f, 0.43f, 0.17f, 0.68f)
            part(0f, -0.19f, 0.02f, 0.42f, 0.24f, 0.22f, 0.48f, 0.20f, 0.76f)
            part(0f, -0.25f, 0.24f, 0.31f, 0.025f, 0.025f, 0.93f, 0.47f, 0.82f)
            // neck
            part(0f, 0.43f, 0f, 0.09f, 0.13f, 0.09f, 1f, 0.79f, 0.86f)
            // hair long side locks behind face
            part(-0.29f, 0.50f, -0.02f, 0.13f, 0.45f, 0.14f, 0.20f, 0.07f, 0.38f)
            part(0.29f, 0.50f, -0.02f, 0.13f, 0.45f, 0.14f, 0.20f, 0.07f, 0.38f)
            // face and hair cap
            part(0f, 0.73f, 0.13f, 0.31f, 0.34f, 0.22f, 1f, 0.84f, 0.88f)
            part(0f, 0.91f, 0.10f, 0.34f, 0.22f, 0.23f, 0.25f, 0.08f, 0.46f)
            // fringe
            part(-0.13f, 0.89f, 0.29f, 0.18f, 0.12f, 0.07f, 0.17f, 0.045f, 0.31f, -18f)
            part(0.11f, 0.89f, 0.29f, 0.17f, 0.12f, 0.07f, 0.17f, 0.045f, 0.31f, 15f)
            // eyes: flattened spheres when open; tiny curves when blinking
            if (!blink) {
                part(-0.13f, 0.73f, 0.337f, 0.052f, 0.074f, 0.025f, 0.22f, 0.10f, 0.52f)
                part(0.13f, 0.73f, 0.337f, 0.052f, 0.074f, 0.025f, 0.22f, 0.10f, 0.52f)
                part(-0.145f, 0.755f, 0.36f, 0.016f, 0.022f, 0.012f, 1f, 1f, 1f)
                part(0.115f, 0.755f, 0.36f, 0.016f, 0.022f, 0.012f, 1f, 1f, 1f)
            } else {
                part(-0.13f, 0.73f, 0.34f, 0.055f, 0.009f, 0.012f, 0.22f, 0.10f, 0.52f)
                part(0.13f, 0.73f, 0.34f, 0.055f, 0.009f, 0.012f, 0.22f, 0.10f, 0.52f)
            }
            // blush, mouth and hair accessories
            part(-0.21f, 0.65f, 0.333f, 0.06f, 0.025f, 0.015f, 1f, 0.36f, 0.62f)
            part(0.21f, 0.65f, 0.333f, 0.06f, 0.025f, 0.015f, 1f, 0.36f, 0.62f)
            part(0f, 0.625f, 0.35f, if (speaking) 0.035f else 0.025f, if (speaking) 0.04f else 0.012f, 0.012f, 0.56f, 0.12f, 0.32f)
            part(-0.32f, 0.92f, 0.13f, 0.05f, 0.07f, 0.035f, 0.95f, 0.42f, 0.82f)
            part(0.32f, 0.92f, 0.13f, 0.05f, 0.07f, 0.035f, 0.95f, 0.42f, 0.82f)
        }

        private fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, rotation: Float = 0f) {
            Matrix.setIdentityM(temp, 0)
            Matrix.translateM(temp, 0, x, y, z)
            if (rotation != 0f) Matrix.rotateM(temp, 0, rotation, 0f, 0f, 1f)
            Matrix.scaleM(temp, 0, sx, sy, sz)
            Matrix.multiplyMM(model, 0, model, 0, temp, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
            Matrix.multiplyMM(mvp, 0, mvp, 0, model, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniform4f(colorHandle, r, g, b, 1f)
            sphere.draw(posHandle, lightHandle)
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        }

        private fun link(vs: String, fs: String): Int {
            fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
                GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
            }
            val v = shader(GLES20.GL_VERTEX_SHADER, vs)
            val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f); GLES20.glLinkProgram(it)
            }
        }
    }

    private class Mesh(private val vertices: FloatBuffer, private val normals: FloatBuffer, private val indices: java.nio.ShortBuffer, private val count: Int) {
        fun draw(pos: Int, normal: Int) {
            vertices.position(0); normals.position(0); indices.position(0)
            GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 0, normals)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices)
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(normal)
        }
        companion object {
            fun makeSphere(stacks: Int, slices: Int): Mesh {
                val v = ArrayList<Float>(); val n = ArrayList<Float>(); val idx = ArrayList<Short>()
                for (i in 0..stacks) {
                    val phi = Math.PI * i / stacks
                    for (j in 0..slices) {
                        val theta = 2.0 * Math.PI * j / slices
                        val x = (sin(phi) * cos(theta)).toFloat()
                        val y = cos(phi).toFloat()
                        val z = (sin(phi) * sin(theta)).toFloat()
                        v.add(x); v.add(y); v.add(z); n.add(x); n.add(y); n.add(z)
                    }
                }
                for (i in 0 until stacks) for (j in 0 until slices) {
                    val a = (i * (slices + 1) + j).toShort()
                    val b = (a.toInt() + slices + 1).toShort()
                    idx.add(a); idx.add(b); idx.add((a + 1).toShort())
                    idx.add((a + 1).toShort()); idx.add(b); idx.add((b + 1).toShort())
                }
                fun fb(list: List<Float>) = ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { list.forEach { put(it) }; position(0) }
                val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { idx.forEach { put(it) }; position(0) }
                return Mesh(fb(v), fb(n), ib, idx.size)
            }
        }
    }
}
