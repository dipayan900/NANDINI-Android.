package com.dipayan.nandini

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One place to control ARUSHI's app-wide appearance and companion speaking style. */
class ArushiExperienceSettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private var selectedAppearance = "system"
    private var selectedPersonality = "playful"
    private var expressiveVoice = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedAppearance = ArushiExperience.appearance(this)
        val savedPersonality = ArushiExperience.personality(this)
        selectedAppearance = savedAppearance
        selectedPersonality = savedPersonality
        expressiveVoice = ArushiExperience.expressiveVoice(this)
        render()
    }

    private fun render() {
        val p = ArushiExperience.palette(this)
        ArushiExperience.applySystemBars(this)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
            setBackgroundColor(p.background)
        }
        scroll.addView(root)
        root.addView(label("ARUSHI EXPERIENCE", 24f, p.accent, true))
        root.addView(label("APP-WIDE APPEARANCE  •  TEXT PERSONALITY  •  VOICE", 10f, p.accentSecondary))
        root.addView(label("Appearance", 17f, p.text, true), params(-1, -2, 0, 18, 0, 4))
        root.addView(label("Choose how ARUSHI's screens should look. System follows your phone's light/dark setting.", 13f, p.muted))
        option("System default", "system", selectedAppearance == "system")
        option("Light", "light", selectedAppearance == "light")
        option("Dark", "dark", selectedAppearance == "dark")

        root.addView(label("Conversation personality", 17f, p.text, true), params(-1, -2, 0, 22, 0, 4))
        root.addView(label("The selected style affects both cloud and on-device AI instructions.", 13f, p.muted))
        optionPersonality("Playful companion", "playful", selectedPersonality == "playful",
            "Cheeky, witty, confident, charming and gently teasing when it fits — always respectful and platonic.")
        optionPersonality("Calm companion", "balanced", selectedPersonality == "balanced",
            "Warm, thoughtful, clear and supportive, with lighter humour.")

        root.addView(label("Voice replies", 17f, p.text, true), params(-1, -2, 0, 22, 0, 4))
        root.addView(label("ARUSHI uses your phone's installed text-to-speech voice. Voice availability and accent depend on the TTS engine.", 13f, p.muted))
        root.addView(action("Expressive speaking: " + if (expressiveVoice) "ON" else "OFF", p) {
            expressiveVoice = !expressiveVoice
            render()
        }, params(-1, dp(48), 0, 10, 0, 6))
        root.addView(label(
            "When enabled, ARUSHI uses a slightly more expressive speaking rate and pitch. The actual voice quality is controlled by Android's Text-to-Speech engine.",
            12f, p.muted
        ))

        root.addView(action("SAVE EXPERIENCE SETTINGS", p, true) {
            ArushiExperience.save(this, selectedAppearance, selectedPersonality, expressiveVoice)
            Toast.makeText(this, "ARUSHI experience settings saved.", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }, params(-1, dp(52), 0, 22, 0, 8))
        root.addView(action("BACK", p) { finish() }, params(-1, dp(48), 0, 0, 0, 0))
        setContentView(scroll)
    }

    private fun option(title: String, value: String, selected: Boolean) {
        val p = ArushiThemePalette()
        root.addView(action((if (selected) "●  " else "○  ") + title, p) {
            selectedAppearance = value
            render()
        }, params(-1, dp(46), 0, 5, 0, 0))
    }

    private fun optionPersonality(title: String, value: String, selected: Boolean, description: String) {
        val p = ArushiThemePalette()
        root.addView(action((if (selected) "●  " else "○  ") + title, p) {
            selectedPersonality = value
            render()
        }, params(-1, dp(46), 0, 5, 0, 0))
        root.addView(label(description, 12f, p.muted), params(-1, -2, dp(8), 0, dp(8), 5))
    }

    private fun ArushiThemePalette() = ArushiExperience.palette(this)

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(dp(5), dp(6), dp(5), dp(6))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun action(text: String, p: ArushiExperience.Palette, primary: Boolean = false, click: () -> Unit) =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else p.text)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(if (primary) p.accent else p.panel)
                setStroke(dp(1), p.accent)
            }
            setOnClickListener { click() }
        }

    private fun params(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
