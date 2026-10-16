package com.dipayan.nandini

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import android.util.Log

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject

import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var speakButton: Button
    private lateinit var statusText: TextView

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isSpeakingEnabled = true
    private var isListening = false
    private var isBusy = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs by lazy {
        getSharedPreferences("nandini_chat", Context.MODE_PRIVATE)
    }

    private data class ChatMessage(
        val role: String,
        val text: String
    )

    private val history = mutableListOf<ChatMessage>()

    private val bg = Color.rgb(10, 12, 24)
    private val panel = Color.rgb(22, 25, 44)
    private val accent = Color.rgb(184, 142, 255)
    private val white = Color.rgb(245, 243, 255)
    private val muted = Color.rgb(174, 176, 202)
    private val userBubble = Color.rgb(56, 43, 87)

    companion object {
        private const val REQUEST_MIC_PERMISSION = 201
        private const val REQUEST_SPEECH = 202
        private const val MAX_SAVED_MESSAGES = 30
        private const val MAX_CONTEXT_MESSAGES = 16
    }

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
            Log.e("NANDINI", "App Check setup failed", e)
        }

        loadHistory()
        tts = TextToSpeech(this, this)
        buildUi()
        renderHistory()

        if (history.isEmpty()) {
            addMessage("NANDINI", "Hey! I'm NANDINI 💜\nTalk to me in Hindi, English, or Hinglish.")
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }

        root.addView(TextView(this).apply {
            text = "N A N D I N I"
            textSize = 22f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(40)))

        root.addView(TextView(this).apply {
            text = "YOUR PERSONAL AI COMPANION"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(-1, dp(22)))

        statusText = TextView(this).apply {
            text = "●  READY"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, dp(28)))

        scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
            isFillViewport = false
        }

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

        voiceButton = makeButton("🎙 VOICE", accent) {
            startVoiceInput()
        }

        speakButton = makeButton("🔊 SPEAK: ON", panel) {
            isSpeakingEnabled = !isSpeakingEnabled
            speakButton.text = if (isSpeakingEnabled) "🔊 SPEAK: ON" else "🔇 SPEAK: OFF"
            if (!isSpeakingEnabled) {
                tts?.stop()
            }
        }

        val clearButton = makeButton("CLEAR", panel) {
            clearChat()
        }

        actions.addView(voiceButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            rightMargin = dp(5)
        })
        actions.addView(speakButton, LinearLayout.LayoutParams(0, dp(44), 1.25f).apply {
            rightMargin = dp(5)
        })
        actions.addView(clearButton, LinearLayout.LayoutParams(0, dp(44), 0.8f))

        root.addView(actions, LinearLayout.LayoutParams(-1, dp(50)))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
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
            maxLines = 1
        }

        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))

        sendButton = makeButton("SEND", accent) {
            sendMessage()
        }

        composer.addView(sendButton, LinearLayout.LayoutParams(dp(78), dp(52)).apply {
            leftMargin = dp(8)
        })

        root.addView(composer, LinearLayout.LayoutParams(-1, dp(58)))
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

    private fun makeButton(
        label: String,
        color: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(if (color == accent) bg else white)
            backgroundTintList = ColorStateList.valueOf(color)
            setOnClickListener { action() }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            configureFemaleVoice()
        } else {
            ttsReady = false
            Log.e("NANDINI", "Text-to-Speech initialization failed")
        }
    }

    private fun configureFemaleVoice() {
        val engine = tts ?: return

        try {
            // Voice names differ between Android TTS engines.
            // This is best-effort; Android does not expose a universal gender flag.
            val available = engine.voices.orEmpty()
            val femaleVoice = available.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                !voice.isNetworkConnectionRequired &&
                    (name.contains("female") || name.contains("woman"))
            } ?: available.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                name.contains("female") || name.contains("woman")
            }

            if (femaleVoice != null) {
                engine.voice = femaleVoice
            }

            engine.setLanguage(Locale("hi", "IN"))
            engine.setSpeechRate(0.94f)
            engine.setPitch(1.08f)
        } catch (e: Exception) {
            Log.w("NANDINI", "Could not select preferred TTS voice", e)
        }
    }

    private fun speakReply(text: String) {
        if (!isSpeakingEnabled || !ttsReady || text.isBlank()) return

        try {
            val hasHindiScript = text.any { it in '\u0900'..'\u097F' }
            val locale = if (hasHindiScript) {
                Locale("hi", "IN")
            } else {
                Locale.US
            }

            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nandini_reply")
        } catch (e: Exception) {
            Log.e("NANDINI", "Speech output failed", e)
        }
    }

    private fun startVoiceInput() {
        if (isBusy || isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition isn't available on this phone.", Toast.LENGTH_LONG).show()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MIC_PERMISSION
            )
            return
        }

        launchSpeechRecognizer()
    }

    private fun launchSpeechRecognizer() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
                )
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in Hindi, English, or Hinglish")
            }

            isListening = true
            voiceButton.isEnabled = false
            statusText.text = "●  LISTENING..."
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (e: Exception) {
            isListening = false
            voiceButton.isEnabled = true
            statusText.text = "●  READY"
            Toast.makeText(this, "Couldn't start voice input.", Toast.LENGTH_SHORT).show()
            Log.e("NANDINI", "Voice input failed to launch", e)
        }
    }

    @Deprecated("Uses the compatible Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SPEECH) {
            isListening = false
            voiceButton.isEnabled = true
            statusText.text = "●  READY"

            if (resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                val spokenText = results?.firstOrNull()?.trim().orEmpty()

                if (spokenText.isNotEmpty()) {
                    input.setText(spokenText)
                    input.setSelection(spokenText.length)
                    sendMessage()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                launchSpeechRecognizer()
            } else {
                Toast.makeText(this, "Microphone permission is needed for voice input.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage() {
        val userText = input.text.toString().trim()
        if (userText.isEmpty() || isBusy) return

        addMessage("YOU", userText)
        history.add(ChatMessage("user", userText))
        trimHistory()
        saveHistory()
        input.text.clear()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        isBusy = true
        setControlsEnabled(false)

        val replyView = addMessage("NANDINI", "Thinking")
        statusText.text = "●  THINKING..."

        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    generateReplyWithRetry()
                }

                replyView.text = reply
                history.add(ChatMessage("model", reply))
                trimHistory()
                saveHistory()
                speakReply(reply)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NANDINI", "AI request failed", e)
                replyView.text = friendlyError(e)
                // Remove the unanswered user turn so it is not replayed as a completed exchange.
                if (history.lastOrNull()?.role == "user") {
                    history.removeAt(history.lastIndex)
                    saveHistory()
                }
            } finally {
                isBusy = false
                setControlsEnabled(true)
                statusText.text = "●  READY"
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private suspend fun generateReplyWithRetry(): String {
        var lastError: Exception? = null

        for (attempt in 0..3) {
            try {
                val model = Firebase.ai(
                    backend = GenerativeBackend.googleAI()
                ).generativeModel("gemini-3.8-flash")

                val recent = history.takeLast(MAX_CONTEXT_MESSAGES)
                val savedHistory = recent.dropLast(1).map { message ->

                    val role = message.role
                    val savedText = message.text
                    content(role = role) {
                        text(savedText)
                    }
                }

                val chat = model.startChat(history = savedHistory)
                val response = chat.sendMessage(
                    recent.lastOrNull { it.role == "user" }?.text
                        ?: throw IllegalStateException("No user message to send.")
                )

                return response.text
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "I couldn't generate a reply. Please try again."

            } catch (e: Exception) {
                lastError = e

                if (!isTemporaryServerError(e) || attempt == 3) {
                    throw e
                }

                delay(1000L * (1L shl attempt))
            }
        }

        throw lastError ?: IllegalStateException("AI request failed.")
    }

    private fun isTemporaryServerError(error: Exception): Boolean {
        val message = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        return message.contains("503") ||
            message.contains("502") ||
            message.contains("500") ||
            message.contains("high demand") ||
            message.contains("temporarily unavailable") ||
            message.contains("unavailable") ||
            message.contains("internal server error") ||
            message.contains("deadline exceeded") ||
            message.contains("timeout")
    }

    private fun friendlyError(error: Exception): String {
        val message = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        return when {
            message.contains("429") || message.contains("quota") ||
                message.contains("resource exhausted") ->
                "We've reached the current AI usage limit. Please try again later. 💜"

            message.contains("503") || message.contains("502") ||
                message.contains("high demand") || message.contains("unavailable") ||
                message.contains("timeout") ->
                "AI is busy right now 😅 Please try again in a little while. 💜"

            message.contains("403") || message.contains("app check") ->
                "I'm having trouble connecting securely. Please check the Firebase App Check setup. 💜"

            message.contains("network") || message.contains("connect") ->
                "I couldn't connect. Please check your internet and try again. 💜"

            else ->
                "Oops, I couldn't reply just now. Please try again. 💜"
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        input.isEnabled = enabled
        voiceButton.isEnabled = enabled && !isListening
    }

    private fun clearChat() {
        if (isBusy) {
            Toast.makeText(this, "Wait for the current reply to finish first.", Toast.LENGTH_SHORT).show()
            return
        }

        history.clear()
        saveHistory()
        messages.removeAllViews()
        tts?.stop()
        addMessage("NANDINI", "Fresh start! ✨ What's on your mind?")
        Toast.makeText(this, "Chat history cleared from this app.", Toast.LENGTH_SHORT).show()
    }

    private fun loadHistory() {
        history.clear()
        val raw = prefs.getString("history", "[]") ?: "[]"

        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val role = item.optString("role")
                val text = item.optString("text")

                if ((role == "user" || role == "model") && text.isNotBlank()) {
                    history.add(ChatMessage(role, text))
                }
            }
            trimHistory()
        } catch (e: Exception) {
            Log.e("NANDINI", "Saved history couldn't be read", e)
            history.clear()
        }
    }
         private fun saveHistory() {
        val array = JSONArray()

        history.forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role)
                    .put("text", message.text)
            )
        }

        prefs.edit()
            .putString("history", array.toString())
            .apply()
    }

    private fun trimHistory() {
        while (history.size > MAX_SAVED_MESSAGES) {
            history.removeAt(0)
        }
    }

    private fun renderHistory() {
        messages.removeAllViews()

        history.forEach { message ->
            addMessage(
                if (message.role == "user") "YOU" else "NANDINI",
                message.text
            )
        }

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessage(
        label: String,
        messageText: String
    ): TextView {
        val isUser = label == "YOU"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }

        val name = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(if (isUser) accent else muted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, dp(4), dp(3))
        }

        val bubble = TextView(this).apply {
            text = messageText
            textSize = 15f
            setTextColor(white)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            setBackgroundColor(if (isUser) userBubble else panel)
            setLineSpacing(dp(2).toFloat(), 1.0f)
            maxWidth = resources.displayMetrics.widthPixels * 4 / 5
        }

        row.addView(name)

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        messages.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
        

    
