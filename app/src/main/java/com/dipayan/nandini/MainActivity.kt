package com.dipayan.nandini

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val bg = Color.rgb(10, 12, 24)
    private val panel = Color.rgb(22, 25, 44)
    private val accent = Color.rgb(184, 142, 255)
    private val white = Color.rgb(245, 243, 255)
    private val muted = Color.rgb(174, 176, 202)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        addMessage("NANDINI", "Hey! I'm NANDINI 💜\nMy native Android foundation is running. My real AI connection is the next upgrade.")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "N A N D I N I"; textSize = 22f; setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(TextView(this).apply {
            text = "YOUR PERSONAL AI COMPANION"; textSize = 10f
            setTextColor(muted); gravity = Gravity.CENTER; letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(-1, dp(25)))
        root.addView(TextView(this).apply {
            text = "✦"; textSize = 42f; setTextColor(accent); gravity = Gravity.CENTER
            setBackgroundColor(panel)
        }, LinearLayout.LayoutParams(dp(86), dp(86)).apply {
            gravity = Gravity.CENTER; topMargin = dp(12); bottomMargin = dp(12)
        })
        scroll = ScrollView(this).apply { clipToPadding = false; setPadding(0, dp(4), 0, dp(8)) }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        input = EditText(this).apply {
            hint = "Message NANDINI..."; setHintTextColor(muted); setTextColor(white)
            textSize = 15f; setSingleLine(true); setPadding(dp(14), 0, dp(10), 0)
            setBackgroundColor(panel); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        composer.addView(Button(this).apply {
            text = "SEND"; setTextColor(bg); textSize = 12f; isAllCaps = false
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(dp(82), dp(52)).apply { leftMargin = dp(8) })
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(root)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        addMessage("YOU", text)
        input.text.clear()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        val lower = text.lowercase()
        val reply = when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hey there! 💜 I'm here. My real AI brain is not connected yet, but this native app is ready for the next step."
            lower.contains("who are you") -> "I'm NANDINI, your personal assistant project. Right now I'm running in demo mode."
            lower.contains("help") -> "Tell me what you want to build, and we'll work through it one step at a time."
            else -> "I received your message: \"$text\"\nI'm currently in demo mode. We'll connect a real AI model in the next phase."
        }
        messages.postDelayed({ addMessage("NANDINI", reply) }, 350)
    }

    private fun addMessage(sender: String, body: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(panel)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        card.addView(TextView(this).apply {
            text = sender; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (sender == "YOU") muted else accent); letterSpacing = 0.08f
        })
        card.addView(TextView(this).apply {
            text = body; textSize = 15f; setTextColor(white); setPadding(0, dp(5), 0, 0)
        })
        messages.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
