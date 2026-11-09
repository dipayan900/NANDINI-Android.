package com.dipayan.nandini

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.concurrent.thread

/** Local, on-device image generation powered by the user's running LocalDream app. */
class ArushiImageStudioActivity : Activity() {
    private val bg = Color.rgb(7, 8, 23)
    private val panel = Color.rgb(19, 22, 48)
    private val accent = Color.rgb(184, 126, 255)
    private val cyan = Color.rgb(90, 211, 255)
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var generateButton: Button
    private lateinit var historyText: TextView
    private var sourceImage: Bitmap? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "✦  ARUSHI IMAGE STUDIO"; textSize = 23f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(TextView(this).apply {
            text = "LOCAL GENERATION  •  LOCAL IMAGE HISTORY"; textSize = 10f
            gravity = Gravity.CENTER; setTextColor(cyan)
        }, LinearLayout.LayoutParams(-1, dp(26)))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = text("Checking LocalDream connection…", 12f, cyan)
        content.addView(status, margin(-1, -2, 0, 8, 0, 8))
        prompt = EditText(this).apply {
            hint = "Describe the image you want to create…"
            setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); textSize = 15f
            minLines = 3; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel, accent)
        }
        content.addView(prompt, margin(-1, -2, 0, 4, 0, 10))
        content.addView(actionButton("CHOOSE IMAGE TO EDIT") { chooseImage() }, margin(-1, dp(46), 0, 0, 0, 8))
        content.addView(actionButton("REMOVE SELECTED IMAGE") {
            sourceImage = null
            status.text = "Text-to-image mode selected."
        }, margin(-1, dp(42), 0, 0, 0, 8))

        preview = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(panel); contentDescription = "ARUSHI generated image preview"
        }
        content.addView(preview, margin(-1, dp(280), 0, 6, 0, 10))

        generateButton = actionButton("GENERATE IMAGE") { generate() }
        content.addView(generateButton, margin(-1, dp(52), 0, 0, 0, 12))
        content.addView(actionButton("CHECK LOCALDREAM SERVER") { checkServer() }, margin(-1, dp(44), 0, 0, 0, 12))
        content.addView(text("RECENT LOCAL GENERATIONS", 12f, accent, true))
        historyText = text("", 11f, Color.LTGRAY)
        content.addView(historyText)
        root.addView(actionButton("← BACK") { finish() }, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        renderHistory()
        checkServer()
    }

    private fun generate() {
        if (busy) return
        val requestPrompt = prompt.text.toString().trim()
        if (requestPrompt.isBlank()) {
            prompt.error = "Enter a prompt first."
            return
        }
        busy = true
        generateButton.isEnabled = false
        status.text = "LocalDream is generating… keep its app running."
        thread(name = "arushi-localdream-generation") {
            try {
                val result = LocalDreamImageClient.generate(requestPrompt, sourceImage)
                val name = saveImage(result)
                runOnUiThread {
                    preview.setImageBitmap(result)
                    status.text = "✓ Generated and saved on this phone: $name"
                    addHistory(name)
                    Toast.makeText(this, "Image saved to ARUSHI local history.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Generation failed: ${e.message ?: "Unknown LocalDream error"}"
                }
            } finally {
                runOnUiThread {
                    busy = false
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun checkServer() {
        status.text = "Checking 127.0.0.1:8081…"
        thread(name = "arushi-localdream-health") {
            val online = runCatching { LocalDreamImageClient.checkHealth() }.getOrDefault(false)
            runOnUiThread {
                if (online) status.text = "✓ LocalDream connected • 127.0.0.1:8081"
                else status.text = "LocalDream offline. Open LocalDream and start its model/server."
            }
        }
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        startActivityForResult(intent, 4107)
    }

    @Deprecated("Deprecated by Android but retained for API 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4107 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            sourceImage = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            preview.setImageBitmap(sourceImage)
            status.text = if (sourceImage != null) "Source image selected. Editing requires img2img support in the loaded LocalDream backend." else "Could not read selected image."
        }
    }

    private fun saveImage(bitmap: Bitmap): String {
        val dir = File(filesDir, "generated_images")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot create local image folder.")
        val name = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.png"
        FileOutputStream(File(dir, name)).use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IllegalStateException("Could not save generated PNG.")
            it.flush()
        }
        return name
    }

    private fun addHistory(name: String) {
        val prefs = getSharedPreferences("arushi_image_studio", MODE_PRIVATE)
        val old = prefs.getString("history", "").orEmpty().split("\n").filter { it.isNotBlank() }
        prefs.edit().putString("history", (listOf(name) + old).distinct().take(20).joinToString("\n")).apply()
        renderHistory()
    }

    private fun renderHistory() {
        historyText.text = getSharedPreferences("arushi_image_studio", MODE_PRIVATE)
            .getString("history", "").orEmpty().split("\n").filter { it.isNotBlank() }
            .joinToString("\n") { "• $it" }.ifBlank { "No images saved yet." }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun margin(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }
    private fun rounded(color: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat(); setColor(color); setStroke(dp(1), stroke)
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(5), dp(7), dp(5), dp(7))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE)
        background = rounded(panel, cyan); setOnClickListener { action() }
    }
}
