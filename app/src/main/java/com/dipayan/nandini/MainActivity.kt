package com.dipayan.nandini

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var sendButton: Button

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val bg = Color.rgb(10, 12, 24)
    private val panel = Color.rgb(22, 25, 44)
    private val accent = Color.rgb(184, 142, 255)
    private val white = Color.rgb(245, 243, 255)
    private val muted = Color.rgb(174, 176, 202)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        FirebaseApp.initializeApp(this)

        try {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
        } catch (e: Exception) {
            android.util.Log.e("NANDINI", "App Check setup failed", e)
        }

        buildUi()

        addMessage(
            "NANDINI",
            "Hey! I'm NANDINI 💜\nSend me a message to chat with Gemini AI."
        )
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "N A N D I N I"
            textSize = 22f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(44)))

        root.addView(TextView(this).apply {
            text = "YOUR PERSONAL AI COMPANION"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(-1, dp(25)))

        root.addView(TextView(this).apply {
            text = "✦"
            textSize = 42f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setBackgroundColor(panel)
        }, LinearLayout.LayoutParams(dp(86), dp(86)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
        }

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        input = EditText(this).apply {
            hint = "Message NANDINI..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(10), 0)
            setBackgroundColor(panel)
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        composer.addView(
            input,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        sendButton = Button(this).apply {
            text = "SEND"
            setTextColor(bg)
            textSize = 12f
            isAllCaps = false
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(accent)
            )
            setOnClickListener { sendMessage() }
        }

        composer.addView(
            sendButton,
            LinearLayout.LayoutParams(dp(82), dp(52)).apply {
                leftMargin = dp(8)
            }
        )

        root.addView(composer, LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(root)

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || !sendButton.isEnabled) return

        addMessage("YOU", text)
        input.text.clear()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        sendButton.isEnabled = false
        input.isEnabled = false

        val replyView = addMessage("NANDINI", "Thinking...")

        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    val model = Firebase.ai(
                        backend = GenerativeBackend.googleAI()
                    ).generativeModel("gemini-3.8-flash")

                    val response = model.generateContent(text)

                    response.text?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "No text returned. Please try again."
                }

                replyView.text = reply

            } catch (e: Exception) {
                android.util.Log.e("NANDINI", "Gemini request failed", e)

                replyView.text =
                    "Gemini request failed: " +
                    (e.message ?: "Unknown error") +
                    "\nPlease check Firebase configuration and internet."

            } finally {
                sendButton.isEnabled = true
                input.isEnabled = true
            }
        }
    }

    private fun addMessage(sender: String, body: String): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        card.addView(TextView(this).apply {
            text = sender
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (sender == "YOU") muted else accent)
            letterSpacing = 0.08f
        })

        val bodyView = TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(white)
            setPadding(0, dp(5), 0, 0)
        }

        card.addView(bodyView)

        messages.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bodyView
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
