package com.dipayan.nandini

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Best-effort, no-paid-API call assistant. It uses Android TTS and SpeechRecognizer.
 * Android/OEM routing may prevent caller audio from reaching the recognizer or TTS from
 * reaching the caller; this class never claims that a SIM-call audio bridge is guaranteed.
 */
object ArushiVoiceCallAssistant {
    private const val TAG = "ARUSHI_VOICE_CALL"
    private const val PREFS = "arushi_call_assistant"
    private const val KEY_TRANSCRIPT = "last_transcript"
    private const val KEY_STATUS = "voice_status"
    private const val MAX_MESSAGE_MS = 18000L
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var transcript = StringBuilder()
    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var originalMode = AudioManager.MODE_NORMAL
    private var originalSpeaker = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_answer_enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_answer_enabled", enabled).apply()
        setStatus(context, if (enabled) "Auto-answer enabled. Test with a trusted caller first." else "Auto-answer disabled.")
    }

    fun lastTranscript(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRANSCRIPT, "No call message saved yet.") ?: "No call message saved yet."

    fun status(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, "Voice assistant idle.") ?: "Voice assistant idle."

    private fun setStatus(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATUS, value).apply()
        Log.i(TAG, value)
    }

    fun start(context: Context, callerNumber: String?) {
        val ctx = context.applicationContext
        main.post {
            if (running) return@post
            if (!isEnabled(ctx)) return@post
            if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                setStatus(ctx, "Voice assistant paused: microphone permission is required. Open ARUSHI Call Assistant and grant it.")
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                setStatus(ctx, "Voice assistant unavailable: Android speech recognition is not installed.")
                return@post
            }
            running = true
            appContext = ctx
            transcript = StringBuilder()
            audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            originalSpeaker = audioManager?.isSpeakerphoneOn ?: false
            try {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager?.isSpeakerphoneOn = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not select speaker route", e)
            }
            setStatus(ctx, "Call connected. Starting disclosed AI greeting.")
            var createdTts: TextToSpeech? = null
            createdTts = TextToSpeech(ctx) { result ->
                main.post {
                    val engine = createdTts
                    if (!running) { engine?.shutdown(); return@post }
                    if (result != TextToSpeech.SUCCESS || engine == null) {
                        finish("Text-to-speech is unavailable.")
                        return@post
                    }
                    tts = engine
                    engine.language = Locale.getDefault()
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    val numberText = callerNumber?.takeIf { it.isNotBlank() } ?: "unavailable"
                    speak("Hello. I am ARUSHI, Dipayan's AI assistant. I may transcribe your spoken message so Dipayan can review it later. Your caller ID is $numberText. Please tell me your name after the tone.") {
                        listen("name")
                    }
                }
            }
        }
    }

    private fun speak(text: String, after: () -> Unit) {
        val engine = tts
        if (!running || engine == null) { after(); return }
        val id = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { main.post { if (running) after() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { if (running) after() } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { if (running) after() } }
        })
        val params = Bundle()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) main.post { if (running) after() }
    }

    private fun listen(stage: String) {
        val ctx = appContext ?: return
        if (!running) return
        setStatus(ctx, if (stage == "name") "Listening for caller's name (device audio routing must be tested)." else "Listening for caller's message.")
        try { recognizer?.destroy() } catch (_: Exception) {}
        val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = sr
        var completed = false
        val timeout = Runnable {
            if (!completed && running) {
                completed = true
                try { sr.cancel(); sr.destroy() } catch (_: Exception) {}
                recognizer = null
                if (stage == "name") {
                    transcript.append("Caller name: not captured\n")
                    speak("Thank you. Please leave your message for Dipayan after the tone.") { listen("message") }
                } else {
                    transcript.append("Message: no speech captured or audio unavailable\n")
                    finishAndSave("No caller speech was captured. Check device call-audio routing.")
                }
            }
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onError(error: Int) {
                if (completed || !running) return
                completed = true
                main.removeCallbacks(timeout)
                try { sr.destroy() } catch (_: Exception) {}
                recognizer = null
                if (stage == "name") {
                    transcript.append("Caller name: not captured (speech error $error)\n")
                    speak("Thank you. Please leave your message for Dipayan after the tone.") { listen("message") }
                } else {
                    transcript.append("Message: not captured (speech error $error)\n")
                    finishAndSave("Speech recognition did not capture a message. The call audio path may be blocked.")
                }
            }
            override fun onResults(results: Bundle?) {
                if (completed || !running) return
                completed = true
                main.removeCallbacks(timeout)
                try { sr.destroy() } catch (_: Exception) {}
                recognizer = null
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (stage == "name") {
                    transcript.append("Caller name: ").append(phrase.ifBlank { "not provided" }).append('\n')
                    speak("Thank you. Please leave your message for Dipayan after the tone.") { listen("message") }
                } else {
                    transcript.append("Message: ").append(phrase.ifBlank { "not captured" }).append('\n')
                    speak("Thank you. Your message has been saved for Dipayan. Goodbye.") {
                        finishAndSave(if (phrase.isBlank()) "No message transcript captured." else "Message transcript saved.")
                    }
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        sr.startListening(intent)
        main.postDelayed(timeout, if (stage == "name") 10000L else MAX_MESSAGE_MS)
    }

    private fun finishAndSave(status: String) {
        val ctx = appContext ?: return
        val current = transcript.toString().trim().ifBlank { "No transcript captured." }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TRANSCRIPT, current)
            .putString(KEY_STATUS, status)
            .apply()
        finish(status)
    }

    private fun finish(status: String) {
        val ctx = appContext
        if (ctx != null) setStatus(ctx, status)
        running = false
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try {
            audioManager?.isSpeakerphoneOn = originalSpeaker
            audioManager?.mode = originalMode
        } catch (_: Exception) {}
        audioManager = null
        appContext = null
    }

    private fun engineSafeShutdown() {
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
    }

    fun stop() {
        main.post { if (running) finish("Voice assistant stopped.") }
    }
}
