package com.dipayan.nandini

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Dedicated settings screen for LocalDream generation. */
class LocalDreamSettingsActivity : Activity() {
    private val bg = Color.rgb(8, 10, 27)
    private val panel = Color.rgb(22, 26, 54)
    private val accent = Color.rgb(182, 130, 255)
    private lateinit var negativePrompt: EditText
    private lateinit var steps: EditText
    private lateinit var width: EditText
    private lateinit var height: EditText
    private lateinit var cfg: EditText
    private lateinit var seed: EditText
    private lateinit var scheduler: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        root.addView(label("IMAGE GENERATION SETTINGS", 22f, accent, true))
        root.addView(label(
            "These values are saved on this phone and applied to the next LocalDream generation. " +
                "Higher sizes and step counts need more memory and time.", 13f, Color.LTGRAY
        ))

        val current = LocalDreamSettingsStore.load(this)
        negativePrompt = field(root, "Negative prompt", current.negativePrompt, multiline = true)
        steps = field(root, "Steps (1–30)", current.steps.toString(), numeric = true)
        width = field(root, "Width (256–768, multiple of 8)", current.width.toString(), numeric = true)
        height = field(root, "Height (256–768, multiple of 8)", current.height.toString(), numeric = true)
        cfg = field(root, "Guidance / CFG (1.0–15.0)", current.guidanceScale.toString(), decimal = true)
        seed = field(root, "Seed (leave blank for random)", current.seed?.toString().orEmpty(), numeric = true)
        scheduler = field(root, "Scheduler (dpm, euler, euler_a, lcm, etc.)", current.scheduler)

        root.addView(label(
            "Supported schedulers: dpm, dpm_karras, dpm_sde, dpm_sde_karras, " +
                "euler, euler_karras, euler_a, euler_a_karras, lcm", 11f, Color.LTGRAY
        ))
        root.addView(actionButton("SAVE SETTINGS") { save() }, params(-1, dp(52), 0, 16, 0, 8))
        root.addView(actionButton("RESTORE DEFAULTS") {
            negativePrompt.setText("blurry, low quality, distorted, extra fingers, malformed hands")
            steps.setText("8"); width.setText("512"); height.setText("512")
            cfg.setText("7.0"); seed.setText(""); scheduler.setText("dpm")
            Toast.makeText(this, "Defaults loaded. Tap SAVE SETTINGS to apply.", Toast.LENGTH_SHORT).show()
        }, params(-1, dp(48), 0, 0, 0, 8))
        root.addView(actionButton("BACK") { finish() }, params(-1, dp(48), 0, 0, 0, 0))
        setContentView(scroll)
    }

    private fun save() {
        try {
            val parsedSeed = seed.text.toString().trim().takeIf { it.isNotEmpty() }?.toLong()
            val settings = LocalDreamGenerationSettings(
                negativePrompt = negativePrompt.text.toString().trim(),
                steps = steps.text.toString().trim().toInt(),
                width = width.text.toString().trim().toInt(),
                height = height.text.toString().trim().toInt(),
                guidanceScale = cfg.text.toString().trim().toFloat(),
                seed = parsedSeed,
                scheduler = scheduler.text.toString().trim().lowercase()
            )
            LocalDreamSettingsStore.save(this, settings)
            Toast.makeText(this, "Image generation settings saved.", Toast.LENGTH_SHORT).show()
            finish()
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "Check the settings values.", Toast.LENGTH_LONG).show()
        }
    }

    private fun field(
        root: LinearLayout,
        title: String,
        value: String,
        numeric: Boolean = false,
        decimal: Boolean = false,
        multiline: Boolean = false
    ): EditText {
        root.addView(label(title, 13f, Color.WHITE, true), params(-1, -2, 0, 10, 0, 2))
        return EditText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
            inputType = when {
                numeric -> InputType.TYPE_CLASS_NUMBER
                decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            } else {
                setSingleLine(true)
            }
        }.also { root.addView(it, params(-1, if (multiline) dp(100) else dp(48), 0, 0, 0, 4)) }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(4), dp(7), dp(4), dp(7))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(82, 55, 135))
        setOnClickListener { action() }
    }

    private fun params(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
