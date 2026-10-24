package com.dipayan.nandini

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.media.AudioManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

/** Internet-only WebRTC call UI, separate from cellular/SIM calling. */
class ArushiVoipActivity : Activity() {
    private var client: ArushiVoipClient? = null
    private lateinit var urlInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var turnUrlInput: EditText
    private lateinit var turnUserInput: EditText
    private lateinit var turnPasswordInput: EditText
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var transcript = StringBuilder()
    private var listening = false
    private val prefs by lazy { getSharedPreferences("arushi_voip", Context.MODE_PRIVATE) }
    private val accent = Color.rgb(184, 142, 255)
    private val bg = Color.rgb(7, 5, 16)
    private val panel = Color.rgb(22, 25, 44)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        root.addView(text("ARUSHI", 14f, accent))
        root.addView(text("INTERNET VOICE CALL", 26f, Color.WHITE, true))
        root.addView(text("Separate from SIM calls • WebRTC audio • Both participants need ARUSHI", 14f, Color.LTGRAY))
        root.addView(field("Signaling WebSocket URL (wss://your-host)", "signal_url", ""))
        urlInput = root.getChildAt(root.childCount - 1) as EditText
        root.addView(field("Room code (same on both phones)", "room", ""))
        roomInput = root.getChildAt(root.childCount - 1) as EditText
        root.addView(text("OPTIONAL TURN SERVER", 13f, accent, true))
        root.addView(field("TURN URL (turn:host:3478 or turns:host:5349)", "turn_url", ""))
        turnUrlInput = root.getChildAt(root.childCount - 1) as EditText
        root.addView(field("TURN username", "turn_user", ""))
        turnUserInput = root.getChildAt(root.childCount - 1) as EditText
        root.addView(field("TURN password", "turn_password", ""))
        turnPasswordInput = root.getChildAt(root.childCount - 1) as EditText
        root.addView(button("Connect / Join room") { connect() })
        root.addView(button("Send ARUSHI voice prompt to other caller") {
            val message = "Hello. I am ARUSHI, Dipayan's AI assistant. This is an internet call."
            client?.sendAiVoiceText(message) ?: toast("Connect to a room first.")
        })
        root.addView(button("Start caller transcript (speakerphone required)") { startRecognition() })
        root.addView(button("Stop transcript") { stopRecognition() })
        root.addView(button("End VoIP call") { endCall() })
        statusView = text("Not connected. Deploy the signaling server first.", 15f, Color.WHITE).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(panel)
        }
        root.addView(statusView)
        root.addView(text("CALL TRANSCRIPT", 13f, accent, true))
        transcriptView = text(prefs.getString("last_transcript", "No transcript yet.") ?: "No transcript yet.", 14f, Color.LTGRAY).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(panel)
        }
        root.addView(transcriptView)
        root.addView(text("Voice prompts are delivered as text to the other ARUSHI app, which speaks them locally with Android TTS. Live two-way microphone audio uses WebRTC. Speech recognition listens through this phone's microphone, so speakerphone acoustics affect transcript quality. Cellular SIM audio is not bridged.", 13f, Color.rgb(255, 205, 130)))
        setContentView(scroll)
    }

    private fun connect() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 7901)
            return
        }
        val url = urlInput.text.toString().trim()
        val room = roomInput.text.toString().trim()
        prefs.edit().putString("signal_url", url).putString("room", room)
            .putString("turn_url", turnUrlInput.text.toString().trim())
            .putString("turn_user", turnUserInput.text.toString().trim())
            .putString("turn_password", turnPasswordInput.text.toString().trim()).apply()
        client?.close(false)
        client = ArushiVoipClient(this, { runOnUiThread { statusView.text = it } }, { phrase ->
            runOnUiThread {
                tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, "arushi-remote-" + System.currentTimeMillis())
            }
        }).also {
            it.setTurnServer(turnUrlInput.text.toString(), turnUserInput.text.toString(), turnPasswordInput.text.toString())
            it.connect(url, room, turnUrlInput.text.toString(), turnUserInput.text.toString(), turnPasswordInput.text.toString())
        }
    }

    private fun startRecognition() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 7901); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "Transcript unavailable: no Android speech recognition provider installed."; return
        }
        listening = true
        transcript = StringBuilder()
        transcriptView.text = "Listening…"
        listenOnce()
    }

    private fun listenOnce() {
        if (!listening || isFinishing) return
        try { recognizer?.destroy() } catch (_: Exception) {}
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onError(error: Int) {
                if (listening) {
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        statusView.text = "Speech recognition error $error. Transcript may be incomplete."
                    }
                    window.decorView.postDelayed({ listenOnce() }, 500)
                }
            }
            override fun onResults(results: Bundle?) {
                if (!listening) return
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (words.isNotBlank()) {
                    transcript.append(words).append('\n')
                    prefs.edit().putString("last_transcript", transcript.toString().trim()).apply()
                    transcriptView.text = transcript.toString().trim()
                }
                window.decorView.postDelayed({ listenOnce() }, 250)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try { sr.startListening(intent) } catch (e: Exception) {
            statusView.text = "Could not start speech recognition: ${e.localizedMessage ?: "unknown error"}"
            listening = false
        }
    }

    private fun stopRecognition() {
        listening = false
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        prefs.edit().putString("last_transcript", transcript.toString().trim()).apply()
        statusView.text = "Transcript stopped and saved on this phone."
    }

    private fun endCall() {
        stopRecognition()
        client?.hangup()
        client = null
    }

    private fun field(hintText: String, key: String, default: String): EditText =
        EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(panel); setPadding(dp(12), dp(10), dp(12), dp(10))
            val value = prefs.getString(key, default).orEmpty()
            if (value.isNotBlank()) setText(value)
            if (key == "turn_password") inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(7), 0, dp(7))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setTextColor(accent); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7901 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) connect()
        else if (requestCode == 7901) statusView.text = "Microphone permission denied; VoIP audio cannot start."
    }

    override fun onDestroy() {
        stopRecognition()
        client?.close(false); client = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }
}
