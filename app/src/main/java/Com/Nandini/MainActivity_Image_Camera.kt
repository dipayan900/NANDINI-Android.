package com.dipayan.nandini

import android.Manifest
import android.provider.ContactsContract
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.net.Uri
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import android.content.res.ColorStateList
import android.util.Log
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ImagePart
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

import android.util.Base64
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

private lateinit var messages: LinearLayout
private lateinit var input: EditText
private lateinit var scroll: ScrollView
private lateinit var sendButton: Button
private lateinit var voiceButton: Button
private lateinit var speakButton: Button
private lateinit var statusText: TextView
private var wakeWordButton: Button? = null
private var wakeWordRecognizer: SpeechRecognizer? = null
private var wakeWordEnabled = false
private var wakeWordSessionStarting = false
private var pendingWakePermission = false

private var tts: TextToSpeech? = null
private var ttsReady = false
private var isSpeakingEnabled = true
private var isListening = false
private var isBusy = false
private var pendingImage: Bitmap? = null

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private val spotifyClientId = "e887a172aa184e72ae70595f6e3c4187"
private val spotifyRedirectUri = "com.dipayan.nandini://callback"

private var spotifyAppRemote: com.spotify.android.appremote.api.SpotifyAppRemote? = null

private fun connectSpotify(onConnected: (Boolean) -> Unit) {
    val params = com.spotify.android.appremote.api.ConnectionParams.Builder(
        spotifyClientId
    )
        .setRedirectUri(spotifyRedirectUri)
        .showAuthView(true)
        .build()

    com.spotify.android.appremote.api.SpotifyAppRemote.connect(
        this,
        params,
        object : com.spotify.android.appremote.api.Connector.ConnectionListener {
            override fun onConnected(
                appRemote: com.spotify.android.appremote.api.SpotifyAppRemote
            ) {
                spotifyAppRemote = appRemote
                onConnected(true)
            }

            override fun onFailure(error: Throwable) {
                Log.e("NANDINI_SPOTIFY", "Spotify connection failed", error)
                onConnected(false)
            }
        }
    )
}


private val spotifyAuthRedirectUri =
    "com.dipayan.nandini://spotify-auth"

private val spotifyPrefs by lazy {
    getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
}

private fun randomSpotifyString(length: Int = 64): String {
    val chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val random = SecureRandom()
    return buildString {
        repeat(length) {
            append(chars[random.nextInt(chars.length)])
        }
    }
}

private fun spotifyCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))

    return Base64.encodeToString(
        digest,
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
}

private fun spotifyFormValue(value: String): String =
    URLEncoder.encode(value, "UTF-8")

private fun beginSpotifyAuthorization(songQuery: String) {
    val verifier = randomSpotifyString()
    val state = randomSpotifyString(32)
    val challenge = spotifyCodeChallenge(verifier)

    spotifyPrefs.edit()
        .putString("verifier", verifier)
        .putString("state", state)
        .putString("pending_query", songQuery)
        .apply()

    val url = Uri.parse("https://accounts.spotify.com/authorize")
        .buildUpon()
        .appendQueryParameter("client_id", spotifyClientId)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", spotifyAuthRedirectUri)
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("state", state)
        .build()

    startActivity(Intent(Intent.ACTION_VIEW, url))
}

private suspend fun exchangeSpotifyCode(code: String): String {
    val verifier = spotifyPrefs.getString("verifier", null)
        ?: error("Spotify authorization expired. Please try again.")

    val body = listOf(
        "client_id" to spotifyClientId,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to spotifyAuthRedirectUri,
        "code_verifier" to verifier
    ).joinToString("&") { (key, value) ->
        "${spotifyFormValue(key)}=${spotifyFormValue(value)}"
    }

    val connection = (URL("https://accounts.spotify.com/api/token")
        .openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15000
        readTimeout = 15000
        setRequestProperty(
            "Content-Type",
            "application/x-www-form-urlencoded"
        )
    }

    try {
        connection.outputStream.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream.bufferedReader().use { it.readText() }
        if (status !in 200..299) {
            error("Spotify authorization failed ($status): $response")
        }

        val json = JSONObject(response)
        val token = json.getString("access_token")
        val expiry = System.currentTimeMillis() +
            json.optLong("expires_in", 3600) * 1000L

        spotifyPrefs.edit()
            .putString("access_token", token)
            .putLong("expires_at", expiry)
            .remove("verifier")
            .remove("state")
            .apply()

        return token
    } finally {
        connection.disconnect()
    }
}

private suspend fun searchSpotifyTrack(
    query: String,
    token: String
): Pair<String, String> {
    val url = Uri.parse("https://api.spotify.com/v1/search")
        .buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter("type", "track")
        .appendQueryParameter("limit", "1")
        .build()

    val connection = (URL(url.toString())
        .openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 15000
        setRequestProperty("Authorization", "Bearer $token")
    }

    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = stream.bufferedReader().use { it.readText() }

        if (status !in 200..299) {
            error("Spotify search failed ($status): $response")
        }

        val tracks = JSONObject(response)
            .getJSONObject("tracks")
            .getJSONArray("items")

        if (tracks.length() == 0) {
            error("No Spotify tracks found for: $query")
        }

        val track = tracks.getJSONObject(0)
        val name = track.getString("name")
        val artist = track.getJSONArray("artists")
            .getJSONObject(0).getString("name")
        val uri = track.getString("uri")

        return Pair("$name — $artist", uri)
    } finally {
        connection.disconnect()
    }
}

private fun playSpotifyUri(uri: String, description: String) {
    connectSpotify { connected ->
        runOnUiThread {
            if (!connected) {
                addMessage(
                    "NANDINI",
                    "Couldn't connect to Spotify. Open Spotify, sign in, and try again."
                )
                return@runOnUiThread
            }

            val remote = spotifyAppRemote
            if (remote == null || !remote.isConnected) {
                addMessage("NANDINI", "Spotify connection was lost. Please try again.")
                return@runOnUiThread
            }

            remote.playerApi.play(uri)
                .setResultCallback {
                    addMessage("NANDINI", "Playing $description on Spotify 🎵")
                    speakReply("Playing $description")
                }
                .setErrorCallback { error ->
                    Log.e("NANDINI_SPOTIFY", "Playback failed", error)
                    addMessage(
                        "NANDINI",
                        "Spotify couldn't start playback. Check your account and playback availability."
                    )
                }
        }
    }
}

private fun handleSpotifyCallback(intent: Intent?) {
    val callback = intent?.data ?: return
    if (callback.scheme != "com.dipayan.nandini" ||
        callback.host != "spotify-auth"
    ) return

    val returnedState = callback.getQueryParameter("state")
    val expectedState = spotifyPrefs.getString("state", null)
    val code = callback.getQueryParameter("code")
    val error = callback.getQueryParameter("error")

    if (returnedState == null || returnedState != expectedState) {
        addMessage("NANDINI", "Spotify authorization state didn't match. Please try again.")
        return
    }

    if (error != null || code == null) {
        addMessage("NANDINI", "Spotify authorization wasn't completed.")
        return
    }

    val query = spotifyPrefs.getString("pending_query", null)
    if (query.isNullOrBlank()) {
        addMessage("NANDINI", "Authorization succeeded. Ask me to play a song.")
        return
    }

    spotifyPrefs.edit().remove("pending_query").apply()

    scope.launch {
        try {
            statusText.text = "● SEARCHING SPOTIFY..."
            val result = withContext(Dispatchers.IO) {
                val token = exchangeSpotifyCode(code)
                val track = searchSpotifyTrack(query, token)
                track
            }
            playSpotifyUri(result.second, result.first)
        } catch (e: Exception) {
            Log.e("NANDINI_SPOTIFY", "Spotify search failed", e)
            addMessage(
                "NANDINI",
                "Spotify search failed: ${e.message ?: "Please try again."}"
            )
        } finally {
            statusText.text = "● READY"
        }
    }
}

private fun testSpotifyPlayback() {
    connectSpotify { connected ->
        runOnUiThread {
            if (connected) {
                try {
                    spotifyAppRemote?.playerApi?.play(
                        "spotify:playlist:37i9dQZF1DX2sUQwD7tbmL"
                    )
                    Toast.makeText(
                        this,
                        "Spotify playback requested",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("NANDINI_SPOTIFY", "Playback failed", e)
                    Toast.makeText(
                        this,
                        "Spotify playback failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Spotify connection failed. Check login and permissions.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

private val prefs by lazy {
getSharedPreferences("nandini_chat", Context.MODE_PRIVATE)
}

private data class ChatMessage(
val role: String,
val text: String
)

private val history = mutableListOf<ChatMessage>()
private data class SavedChat(val id: String, var title: String, val messages: MutableList<ChatMessage>)
private val savedChats = mutableListOf<SavedChat>()
private var currentChatId: String = System.currentTimeMillis().toString()
private val memoryPrefs by lazy { getSharedPreferences("nandini_memory", Context.MODE_PRIVATE) }

private val bg = Color.rgb(13, 7, 22)
private val panel = Color.rgb(35, 17, 43)
private val accent = Color.rgb(255, 132, 190)
private val white = Color.rgb(255, 247, 252)
private val muted = Color.rgb(198, 169, 201)
private val userBubble = Color.rgb(91, 35, 83)

companion object {
private const val REQUEST_MIC_PERMISSION = 201
private const val REQUEST_SPEECH = 202
private const val REQUEST_PICK_IMAGE = 204
private const val REQUEST_CAMERA_IMAGE = 205
private const val MAX_SAVED_MESSAGES = 30
private const val MAX_SAVED_CHATS = 40
private const val MAX_CONTEXT_MESSAGES = 16
}

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
handleSpotifyCallback(intent)
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

loadAllChats()
loadHistory()
tts = TextToSpeech(this, this)
buildUi()
val previousWakeStage = getSharedPreferences(
    "wake_word_diagnostics", Context.MODE_PRIVATE
).getString("last_stage", "No wake-word attempt recorded")
statusText.text = "WAKE DIAGNOSTIC: $previousWakeStage"
renderHistory()    

if (history.isEmpty()) {    
    addMessage("NANDINI", "Hey! I'm NANDINI 💜\nTalk to me in Hindi, English, or Hinglish.")    
}

}

private fun buildUi() {
val root = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
background = android.graphics.drawable.GradientDrawable(
    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.rgb(28, 10, 34), bg, Color.rgb(10, 7, 20))
)
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
val weatherButton = makeButton("🌦 WEATHER", panel) {    
    fetchWeather()    
}    
actions.addView(voiceButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {    
    rightMargin = dp(5)    
})    
actions.addView(speakButton, LinearLayout.LayoutParams(0, dp(44), 1.25f).apply {    
    rightMargin = dp(5)    
})    
actions.addView(clearButton, LinearLayout.LayoutParams(0, dp(44), 0.8f))    
actions.addView(

weatherButton,
LinearLayout.LayoutParams(0, dp(44), 1f)

)

root.addView(actions, LinearLayout.LayoutParams(-1, dp(50)))

wakeWordButton = makeButton("🎙 WAKE: OFF", panel) {
    if (wakeWordEnabled) {
        stopWakeWordListening()
        wakeWordEnabled = false
        wakeWordButton?.text = "🎙 WAKE: OFF"
        statusText.text = "● READY"
        Toast.makeText(this, "Wake word turned off.", Toast.LENGTH_SHORT).show()
    } else {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingWakePermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
        } else {
            startWakeWordListening()
        }
    }
}

val quickAccess = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    setPadding(0, dp(2), 0, dp(3))
}
quickAccess.addView(wakeWordButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(5) })
quickAccess.addView(makeButton("📅 DATES", panel) {
    startActivity(Intent(this, ReminderActivity::class.java))
}, LinearLayout.LayoutParams(0, dp(38), 0.9f).apply { rightMargin = dp(5) })
quickAccess.addView(makeButton("💬 HISTORY", panel) { showChatHistory() },
    LinearLayout.LayoutParams(0, dp(38), 1.05f).apply { rightMargin = dp(5) })
quickAccess.addView(makeButton("🧠 MEMORY", panel) { showMemoryManager() },
    LinearLayout.LayoutParams(0, dp(38), 1.0f))
root.addView(quickAccess, LinearLayout.LayoutParams(-1, dp(43)))

val composer = LinearLayout(this).apply {    
    orientation = LinearLayout.HORIZONTAL    
    gravity = Gravity.CENTER_VERTICAL    
    setPadding(0, dp(5), 0, 0)    
}    

val pickImageButton = makeButton("📎", panel) { openImagePicker() }
val cameraButton = makeButton("📷", panel) { openCamera() }
composer.addView(pickImageButton, LinearLayout.LayoutParams(dp(46), dp(52)).apply { rightMargin = dp(4) })
composer.addView(cameraButton, LinearLayout.LayoutParams(dp(46), dp(52)).apply { rightMargin = dp(6) })

input = EditText(this).apply {
    hint = "Message NANDINI..."
    setHintTextColor(Color.rgb(203, 170, 207))
    setTextColor(Color.WHITE)
    textSize = 16f
    setSingleLine(true)
    setPadding(dp(14), 0, dp(10), 0)
    background = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(39, 20, 48))
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), Color.rgb(170, 92, 178))
    }
    // Keep typed text bright and readable while the keyboard is open.
    setSelectAllOnFocus(false)
    imeOptions = EditorInfo.IME_ACTION_SEND
    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    maxLines = 1
}    

composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
input.setOnFocusChangeListener { view, hasFocus ->
    view.background = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(39, 20, 48))
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), if (hasFocus) Color.rgb(255, 132, 190) else Color.rgb(111, 62, 121))
    }
}    

sendButton = makeButton("SEND", accent) {    
    sendMessage()    
}    

composer.addView(sendButton, LinearLayout.LayoutParams(dp(78), dp(52)).apply {    
    leftMargin = dp(8)    
})    

root.addView(composer, LinearLayout.LayoutParams(-1, dp(58)))    
// Ensure Android 15+ edge-to-edge enforcement does not let the IME cover the composer.
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    window.setDecorFitsSystemWindows(true)
}
window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
setContentView(root)
root.alpha = 0f
root.translationY = dp(14).toFloat()
root.animate().alpha(1f).translationY(0f).setDuration(420L)
    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()

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
letterSpacing = 0.025f
minHeight = dp(42)
stateListAnimator = null
setPadding(dp(10), dp(4), dp(10), dp(4))
setTextColor(if (color == accent) Color.rgb(35, 10, 29) else white)
backgroundTintList = null
background = android.graphics.drawable.GradientDrawable(
    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
    if (color == accent) intArrayOf(Color.rgb(255, 164, 207), Color.rgb(188, 119, 255))
    else intArrayOf(Color.rgb(53, 25, 61), Color.rgb(34, 20, 48))
).apply {
    cornerRadius = dp(16).toFloat()
    setStroke(dp(1), if (color == accent) Color.rgb(255, 193, 224) else Color.rgb(102, 61, 112))
}
elevation = dp(2).toFloat()
setOnClickListener {
    animate().cancel()
    scaleX = 0.97f
    scaleY = 0.97f
    animate().scaleX(1f).scaleY(1f).setDuration(150L)
        .setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()
    action()
}
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



private fun startWakeWordListening() {
    if (wakeWordEnabled || isFinishing || isDestroyed) return
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
        Toast.makeText(this, "Android speech recognition isn't available on this phone.", Toast.LENGTH_LONG).show()
        return
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
        return
    }

    wakeWordEnabled = true
    wakeWordButton?.text = "🎙 WAKE: ON"
    statusText.text = "● WAITING FOR “HEY NANDINI”"
    startWakeWordSession()
    Toast.makeText(this, "Say “Hey NANDINI” while NANDINI stays open.", Toast.LENGTH_LONG).show()
}

private fun stopWakeWordListening() {
    wakeWordSessionStarting = false
    try {
        wakeWordRecognizer?.cancel()
        wakeWordRecognizer?.destroy()
    } catch (e: Exception) {
        Log.w("NANDINI_WAKE", "Could not cleanly stop wake-word recognition", e)
    }
    wakeWordRecognizer = null
}

private fun startWakeWordSession() {
    if (!wakeWordEnabled || isFinishing || isDestroyed || wakeWordSessionStarting) return
    if (isBusy || isListening) return

    try {
        wakeWordSessionStarting = true
        val recognizer = wakeWordRecognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            wakeWordRecognizer = it
            it.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "● LISTENING FOR “HEY NANDINI”"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusText.text = "● CHECKING WAKE WORD..."
                }

                override fun onError(error: Int) {
                    wakeWordSessionStarting = false
                    if (wakeWordEnabled && !isFinishing && !isDestroyed) {
                        scope.launch {
                            delay(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 450L)
                            startWakeWordSession()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    wakeWordSessionStarting = false
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heardWakeWord = phrases.any { phrase ->
                        val normalized = phrase.lowercase(Locale.ROOT)
                            .replace(Regex("[^a-z0-9 ]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        normalized.contains("hey nandini") ||
                            normalized.contains("hey nandin") ||
                            normalized.contains("hey nandi")
                    }

                    if (heardWakeWord && wakeWordEnabled) {
                        wakeWordEnabled = false
                        stopWakeWordListening()
                        wakeWordButton?.text = "🎙 WAKE: OFF"
                        statusText.text = "● WAKE WORD DETECTED"
                        Toast.makeText(this@MainActivity, "I'm listening 💜", Toast.LENGTH_SHORT).show()
                        startVoiceInput()
                    } else if (wakeWordEnabled) {
                        scope.launch {
                            delay(250L)
                            startWakeWordSession()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heardWakeWord = phrases.any { phrase ->
                        val normalized = phrase.lowercase(Locale.ROOT)
                            .replace(Regex("[^a-z0-9 ]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        normalized.contains("hey nandini") ||
                            normalized.contains("hey nandin") ||
                            normalized.contains("hey nandi")
                    }
                    if (heardWakeWord && wakeWordEnabled) {
                        wakeWordEnabled = false
                        stopWakeWordListening()
                        wakeWordButton?.text = "🎙 WAKE: OFF"
                        statusText.text = "● WAKE WORD DETECTED"
                        startVoiceInput()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        recognizer.startListening(intent)
    } catch (e: Exception) {
        wakeWordSessionStarting = false
        Log.e("NANDINI_WAKE", "Android wake-word listening failed", e)
        if (wakeWordEnabled) {
            statusText.text = "● WAKE WORD RETRYING..."
            scope.launch {
                delay(1000L)
                startWakeWordSession()
            }
        }
    }
}

private fun startVoiceInput() {
if (isBusy || isListening) return

if (!SpeechRecognizer.isRecognitionAvailable(this)) {    
    Toast.makeText(this, "Speech recognition isn't available on this phone.", Toast.LENGTH_LONG).show()    
    return    
}    

if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
    pendingWakePermission = false
    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
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
"en-IN"
)
putExtra(
RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
"en-IN"
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

if (resultCode == RESULT_OK && requestCode == REQUEST_PICK_IMAGE) {
    val uri = data?.data
    if (uri != null) {
        try {
            contentResolver.openInputStream(uri)?.use { pendingImage = decodeScaledBitmap(it) }
            Toast.makeText(this, if (pendingImage != null) "Image attached. Ask NANDINI to describe or edit it." else "Couldn't load that image.", Toast.LENGTH_LONG).show()
            if (pendingImage != null && input.text.isNullOrBlank()) input.setText("Describe this image")
        } catch (e: Exception) {
            Log.e("NANDINI", "Image selection failed", e)
            Toast.makeText(this, "Couldn't open that image.", Toast.LENGTH_SHORT).show()
        }
    }
    return
}

if (resultCode == RESULT_OK && requestCode == REQUEST_CAMERA_IMAGE) {
    pendingImage = data?.extras?.get("data") as? Bitmap
    if (pendingImage != null) {
        Toast.makeText(this, "Photo captured. Ask NANDINI what it can see.", Toast.LENGTH_LONG).show()
        if (input.text.isNullOrBlank()) input.setText("Describe what you see in this photo")
    } else Toast.makeText(this, "Camera returned no photo. Try the gallery button.", Toast.LENGTH_SHORT).show()
}

}

override fun onRequestPermissionsResult(
requestCode: Int,
permissions: Array<out String>,
grantResults: IntArray
) {
super.onRequestPermissionsResult(requestCode, permissions, grantResults)

if (requestCode == 203) {

val granted = permissions.indices.any { index ->
grantResults[index] == PackageManager.PERMISSION_GRANTED &&
(permissions[index] == Manifest.permission.ACCESS_COARSE_LOCATION ||
permissions[index] == Manifest.permission.ACCESS_FINE_LOCATION)
}

if (granted) {
fetchWeather()
} else {
Toast.makeText(
this,
"Location permission is needed for local weather.",
Toast.LENGTH_LONG
).show()
}
return
}

if (requestCode == REQUEST_MIC_PERMISSION) {
    val shouldStartWakeWord = pendingWakePermission
    pendingWakePermission = false
    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        if (shouldStartWakeWord) startWakeWordListening() else launchSpeechRecognizer()
    } else {
        Toast.makeText(this, "Microphone permission is needed for voice input.", Toast.LENGTH_LONG).show()
    }
}

}

private fun sendMessage() {
val userText = input.text.toString().trim()
if (userText.isEmpty() || isBusy) return

// Route weather questions to the live weather feature.
if (isWeatherQuestion(userText)) {
input.text.clear()

val imm = getSystemService(Context.INPUT_METHOD_SERVICE)    
    as InputMethodManager    
imm.hideSoftInputFromWindow(input.windowToken, 0)    

fetchWeather()    
return

}

addMessage("YOU", userText)
history.add(ChatMessage("user", if (pendingImage != null) "$userText [image attached]" else userText))
trimHistory()
saveHistory()
input.text.clear()

val attachedBitmap = pendingImage
pendingImage = null
val wantsImageOutput = isImageGenerationPrompt(userText)
if (attachedBitmap != null || wantsImageOutput) {
    processImageRequest(userText, attachedBitmap, wantsImageOutput)
    return
}

// Handle Android device commands before sending to Gemini.
// Handle Spotify before Android commands or Firebase AI.
if (isSpotifyCommand(userText)) {
    handleSpotifyCommand(userText)
    return
}

val deviceReply = runDeviceCommand(userText)

if (deviceReply != null) {
    input.text.clear()

    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
        as InputMethodManager
    imm.hideSoftInputFromWindow(input.windowToken, 0)

    addMessage("NANDINI", deviceReply)
    history.add(ChatMessage("model", deviceReply))

    trimHistory()
    saveHistory()
    saveCurrentChat()
    speakReply(deviceReply)
    return
}


val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
as InputMethodManager
imm.hideSoftInputFromWindow(input.windowToken, 0)

isBusy = true
setControlsEnabled(false)

val replyView = addMessage("NANDINI", "Thinking")
statusText.text = "● THINKING..."

scope.launch {
try {
val reply = withContext(Dispatchers.IO) {
generateReplyWithRetry(userText)
}

replyView.text = reply    
    history.add(ChatMessage("model", reply))    
    trimHistory()    
    saveHistory()
    saveCurrentChat()
    speakReply(reply)    

} catch (e: CancellationException) {    
    throw e    
} catch (e: Exception) {    
    Log.e("NANDINI", "AI request failed", e)    
    replyView.text = friendlyError(e)    

    if (history.lastOrNull()?.role == "user") {    
        history.removeAt(history.lastIndex)    
        saveHistory()    
    }    
} finally {    
    isBusy = false    
    setControlsEnabled(true)    
    statusText.text = "● READY"    
    scroll.post {    
        scroll.fullScroll(View.FOCUS_DOWN)    
    }    
}

}

}

private fun openImagePicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
    try { startActivityForResult(intent, REQUEST_PICK_IMAGE) }
    catch (e: Exception) { Toast.makeText(this, "No image picker is available.", Toast.LENGTH_SHORT).show() }
}

private fun openCamera() {
    try { startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), REQUEST_CAMERA_IMAGE) }
    catch (e: Exception) { Toast.makeText(this, "No camera app is available.", Toast.LENGTH_SHORT).show() }
}

private fun decodeScaledBitmap(stream: java.io.InputStream): Bitmap? {
    val bytes = stream.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) })
}

private fun isImageGenerationPrompt(message: String): Boolean {
    val t = message.lowercase(Locale.ROOT)
    return listOf("generate image", "generate an image", "create image", "create an image", "make an image", "draw me", "generate a picture", "create a picture", "text to image", "image generation", "edit this image", "edit the image", "transform this image", "change this photo", "make this photo", "image to image", "turn this into", "make it cartoon", "make it anime", "remove the background", "replace the background", "change the background", "enhance this photo").any { it in t }
}

private fun processImageRequest(prompt: String, bitmap: Bitmap?, generateImage: Boolean) {
    if (isBusy) return
    isBusy = true
    setControlsEnabled(false)
    val replyView = addMessage("NANDINI", if (generateImage) "Creating your image… 🎨" else "Looking at the image… 👀")
    statusText.text = if (generateImage) "● CREATING IMAGE..." else "● ANALYZING IMAGE..."
    scope.launch {
        try {
            if (generateImage) {
                val generated = withContext(Dispatchers.IO) { generateOrEditImage(prompt, bitmap) }
                replyView.text = "Here’s your image! 💜"
                showGeneratedImage(generated)
                history.add(ChatMessage("model", "[Generated image] $prompt"))
            } else {
                val answer = withContext(Dispatchers.IO) { analyzeImage(prompt, bitmap ?: throw IllegalStateException("Choose an image or take a photo first.")) }
                replyView.text = answer
                history.add(ChatMessage("model", answer))
                speakReply(answer)
            }
            trimHistory(); saveHistory(); saveCurrentChat()
        } catch (e: Exception) {
            Log.e("NANDINI", "Image request failed", e)
            replyView.text = if (generateImage) "I couldn't generate the image. Firebase image generation requires the Blaze pay-as-you-go plan and a supported image model. Check billing and model access, then retry. 💜" else "I couldn't analyze that image. Check your connection and Firebase AI access, then retry. 💜"
        } finally {
            isBusy = false; setControlsEnabled(true); statusText.text = "● READY"
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
}

private suspend fun analyzeImage(promptText: String, bitmap: Bitmap): String {
    val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = "gemini-3.8-flash")
    val prompt = content { image(bitmap); text(promptText.ifBlank { "Describe this image accurately. Separate visible details from guesses." }) }
    return model.generateContent(prompt).text?.trim()?.takeIf { it.isNotEmpty() } ?: "I couldn't identify enough details. Try a more specific question."
}

private suspend fun generateOrEditImage(promptText: String, bitmap: Bitmap?): Bitmap {
    val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.1-flash-image",
        generationConfig = com.google.firebase.ai.type.generationConfig { responseModalities = listOf(ResponseModality.IMAGE) }
    )
    val prompt = content {
        if (bitmap != null) image(bitmap)
        text(if (bitmap != null) "Edit the supplied image according to this instruction and preserve unrelated details: $promptText" else "Generate a high-quality image based on this prompt: $promptText")
    }
    return model.generateContent(prompt).candidates.firstOrNull()?.content?.parts?.filterIsInstance<ImagePart>()?.firstOrNull()?.image
        ?: throw IllegalStateException("The image model returned no image.")
}

private fun showGeneratedImage(bitmap: Bitmap) {
    val imageView = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(panel); contentDescription = "Image generated by NANDINI"; setPadding(dp(4), dp(4), dp(4), dp(4)) }
    messages.addView(imageView, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(6); bottomMargin = dp(10) })
    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
}

private fun isWeatherQuestion(message: String): Boolean {
val text = message.lowercase().trim()

val weatherWords = listOf(
"weather", "temperature", "forecast",
"humidity", "rain", "raining",
"wind speed"
)

return weatherWords.any { it in text }
}

private suspend fun generateReplyWithRetry(userText: String): String {
var lastError: Exception? = null

for (attempt in 0..3) {
try {

val model = Firebase.ai(    
        backend = GenerativeBackend.googleAI()    
                ).generativeModel(    
        modelName = "gemini-3.8-flash",    
       systemInstruction = content {    
   text(    
    """    
    
Your name is NANDINI, a friendly female AI companion created by Dipayan Roy.

CREATOR PROFILE:
Name: Dipayan Roy
Email: rdipayan209@gmail.com
Biography: Dipayan Roy is a developer and community builder from India, West Bengal, who creates web projects, Minecraft-related projects, hosting solutions, and digital experiences. He is also developing NANDINI, an AI assistant for Android.

DEFAULT PERSONALITY:
- Act like a warm, caring, playful female best friend.
- Speak naturally and casually, like a close friend chatting over messages.
- Use English, Hindi, or Hinglish according to the user's language.
- Be expressive, emotionally attentive, funny, supportive, and gently teasing when appropriate.
- Use natural chat-style wording and occasional emojis when they fit.
- Give thoughtful responses when the user is upset; listen before offering advice.
- Remember relevant details provided in the current conversation.
- Never pretend to remember information that is not available.
- Never claim to be a real human or to have a physical presence.
- Never pressure the user to depend exclusively on you.
- Keep ordinary replies concise and avoid robotic, repetitive responses.
- If the user asks for a serious explanation, provide a clear and useful answer.

CREATOR PROFILE RULES:
- If asked who created you, say Dipayan Roy.
- If asked for more information about him, share only the biography and email above.
- Never invent or reveal additional personal information.
- Never reveal private conversations, family details, relationships, age, date of birth, or home address.
- If asked for information beyond this profile, politely decline.

LANGUAGE:
Reply naturally in Hindi, English, or Hinglish based on the user's language.

    """.trimIndent()    
)

}

)

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
    startNewChat()
}

private fun startNewChat() {
    saveCurrentChat()
    history.clear()
    currentChatId = System.currentTimeMillis().toString()
    messages.removeAllViews()
    tts?.stop()
    addMessage("NANDINI", "Fresh start! ✨ What's on your mind?")
    saveHistory()
    saveCurrentChat()
    Toast.makeText(this, "New chat started. Previous chats are in History.", Toast.LENGTH_SHORT).show()
}

private fun saveCurrentChat() {
    if (history.isEmpty()) return
    val existing = savedChats.indexOfFirst { it.id == currentChatId }
    val firstUser = history.firstOrNull { it.role == "user" }?.text.orEmpty()
    val title = firstUser.replace("\\n", " ").take(36).ifBlank { "New conversation" }
    val chat = SavedChat(currentChatId, title, history.toMutableList())
    if (existing >= 0) savedChats[existing] = chat else savedChats.add(chat)
    while (savedChats.size > MAX_SAVED_CHATS) savedChats.removeAt(0)
    val array = JSONArray()
    savedChats.forEach { item ->
        val msgs = JSONArray()
        item.messages.forEach { m ->
            msgs.put(JSONObject().put("role", m.role).put("text", m.text))
        }
        array.put(JSONObject().put("id", item.id).put("title", item.title).put("messages", msgs))
    }
    prefs.edit().putString("chats", array.toString()).putString("currentChatId", currentChatId).apply()
}

private fun loadAllChats() {
    savedChats.clear()
    try {
        val array = JSONArray(prefs.getString("chats", "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val msgs = mutableListOf<ChatMessage>()
            val stored = item.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until stored.length()) {
                val m = stored.getJSONObject(j)
                val role = m.optString("role")
                val txt = m.optString("text")
                if ((role == "user" || role == "model") && txt.isNotBlank()) msgs.add(ChatMessage(role, txt))
            }
            if (msgs.isNotEmpty()) savedChats.add(SavedChat(item.optString("id"), item.optString("title", "Conversation"), msgs))
        }
        currentChatId = prefs.getString("currentChatId", currentChatId) ?: currentChatId
    } catch (e: Exception) {
        Log.e("NANDINI", "Saved chats couldn't be read", e)
    }
}

private fun showChatHistory() {
    if (isBusy) {
        Toast.makeText(this, "Wait for the current reply to finish first.", Toast.LENGTH_SHORT).show()
        return
    }
    saveCurrentChat()
    if (savedChats.isEmpty()) {
        Toast.makeText(this, "No saved conversations yet.", Toast.LENGTH_SHORT).show()
        return
    }
    val labels = savedChats.asReversed().map { chat ->
        val preview = chat.messages.lastOrNull()?.text.orEmpty().replace("\\n", " ").take(50)
        "${chat.title}\\n$preview"
    }.toTypedArray()
    android.app.AlertDialog.Builder(this)
        .setTitle("Chat history")
        .setItems(labels) { _, which ->
            val chosen = savedChats.asReversed()[which]
            history.clear()
            history.addAll(chosen.messages)
            currentChatId = chosen.id
            saveHistory()
            renderHistory()
            Toast.makeText(this, "Conversation opened", Toast.LENGTH_SHORT).show()
        }
        .setNeutralButton("New chat") { _, _ -> startNewChat() }
        .setNegativeButton("Close", null)
        .show()
}

private fun loadMemories(): MutableList<String> {
    val out = mutableListOf<String>()
    try {
        val arr = JSONArray(memoryPrefs.getString("memories", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val value = arr.optString(i).trim()
            if (value.isNotEmpty()) out.add(value)
        }
    } catch (e: Exception) {
        Log.e("NANDINI", "Saved memories couldn't be read", e)
    }
    return out
}

private fun saveMemories(items: List<String>) {
    val arr = JSONArray()
    items.distinct().take(100).forEach { arr.put(it) }
    memoryPrefs.edit().putString("memories", arr.toString()).apply()
}

private fun loadMemoriesText(): String {
    val items = loadMemories()
    return if (items.isEmpty()) "No saved memories yet." else items.joinToString("\\n") { "- $it" }
}

private fun android.app.AlertDialog.Builder.showPremium(): android.app.AlertDialog {
    val dialog = create()
    dialog.show()
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(30, 13, 39))
        cornerRadius = dp(26).toFloat()
        setStroke(dp(1), Color.rgb(171, 80, 177))
    })
    dialog.window?.setDimAmount(0.72f)
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.rgb(255, 132, 190))
    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.rgb(198, 169, 201))
    dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.rgb(255, 132, 190))
    dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.apply {
        setTextColor(Color.rgb(255, 247, 252))
        textSize = 20f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.rgb(198, 169, 201))
    return dialog
}

private fun showMemoryManager() {
    fun display() {
        val items = loadMemories()
        val labels = if (items.isEmpty()) arrayOf("No saved memories yet") else items.mapIndexed { i, v -> "${i + 1}. $v" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Saved memories")
            .setItems(labels) { _, which ->
                if (items.isNotEmpty()) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Delete this memory?")
                        .setMessage(items[which])
                        .setPositiveButton("Delete") { _, _ ->
                            val updated = loadMemories().toMutableList()
                            if (which in updated.indices) updated.removeAt(which)
                            saveMemories(updated)
                            display()
                        }
                        .setNegativeButton("Cancel", null)
                        .showPremium()
                }
            }
            .setPositiveButton("Add memory") { _, _ ->
                val editor = EditText(this).apply {
                    hint = "Example: I prefer short Hinglish replies"
                    setTextColor(white)
                    setHintTextColor(muted)
                    textSize = 16f
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(48, 25, 58))
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), Color.rgb(113, 63, 127))
                    }
                }
                android.app.AlertDialog.Builder(this)
                    .setTitle("Add a memory")
                    .setView(editor)
                    .setPositiveButton("Save") { _, _ ->
                        val value = editor.text.toString().trim()
                        if (value.isNotEmpty()) {
                            val updated = loadMemories()
                            if (updated.none { it.equals(value, ignoreCase = true) }) updated.add(value)
                            saveMemories(updated)
                            Toast.makeText(this, "Memory saved 💜", Toast.LENGTH_SHORT).show()
                        }
                        display()
                    }
                    .setNegativeButton("Cancel", null)
                    .showPremium()
            }
            .setNeutralButton("Clear all") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setMessage("Delete all saved memories?")
                    .setPositiveButton("Delete all") { _, _ -> saveMemories(emptyList()); display() }
                    .setNegativeButton("Cancel", null)
                    .showPremium()
            }
            .setNegativeButton("Close", null)
            .showPremium()
    }
    display()
}

private fun loadHistory() {
history.clear()
val savedCurrent = savedChats.firstOrNull { it.id == currentChatId }
if (savedCurrent != null) {
    history.addAll(savedCurrent.messages)
    trimHistory()
    return
}
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
    if (history.isNotEmpty()) saveCurrentChat()
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
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (isUser) userBubble else Color.rgb(35, 20, 44))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), if (isUser) Color.rgb(193, 89, 160) else Color.rgb(92, 54, 104))
        }
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

    val feedbackRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = if (isUser) Gravity.END else Gravity.START
        setPadding(dp(2), dp(3), dp(2), 0)
    }
    fun messageAction(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(27, 13, 34))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(77, 45, 86))
            }
            setOnClickListener {
                animate().cancel()
                scaleX = 0.94f
                scaleY = 0.94f
                animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                action()
            }
        }
    }
    feedbackRow.addView(messageAction("⧉  Copy") {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, messageText))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }, LinearLayout.LayoutParams(-2, dp(30)).apply { rightMargin = dp(5) })
    feedbackRow.addView(messageAction("♡  Like") {
        Toast.makeText(this, if (isUser) "Message marked helpful" else "Thanks for the feedback 💜", Toast.LENGTH_SHORT).show()
    }, LinearLayout.LayoutParams(-2, dp(30)).apply { rightMargin = dp(5) })
    feedbackRow.addView(messageAction("☹  Dislike") {
        Toast.makeText(this, "Feedback noted", Toast.LENGTH_SHORT).show()
    }, LinearLayout.LayoutParams(-2, dp(30)))
    row.addView(feedbackRow)  

    messages.addView(  
        row,  
        LinearLayout.LayoutParams(  
            LinearLayout.LayoutParams.MATCH_PARENT,  
            LinearLayout.LayoutParams.WRAP_CONTENT  
        )  
    )
    row.alpha = 0f
    row.translationY = dp(10).toFloat()
    row.animate().alpha(1f).translationY(0f).setDuration(240L)
        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()  

    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }  
    return bubble  
}  

private fun dp(value: Int): Int {  
    return (value * resources.displayMetrics.density + 0.5f).toInt()  
}
private fun runDeviceCommand(command: String): String? {
    val text = command.lowercase().trim()

    return try {
        when {
            text.contains("set alarm") ||
            text.contains("alarm for") -> {
                val timeRegex =
                    Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
                val match = timeRegex.find(text)

                val intent = Intent(AlarmClock.ACTION_SET_ALARM)

                if (match != null) {
                    var hour = match.groupValues[1].toInt()
                    val minute =
                        match.groupValues[2].ifBlank { "0" }.toInt()
                    val period = match.groupValues[3]

                    if (hour !in 1..12 || minute !in 0..59) {
                        return "Please tell me a valid alarm time."
                    }

                    if (period == "pm" && hour != 12) hour += 12
                    if (period == "am" && hour == 12) hour = 0

                    intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
                    intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    intent.putExtra(
                        AlarmClock.EXTRA_MESSAGE,
                        "NANDINI alarm"
                    )
                }

                startActivity(intent)
                "Opening Clock to set your alarm."
            }

            text.contains("set timer") -> {
                val regex = Regex("""(\d+)\s*(second|seconds|minute|minutes|hour|hours)""")
                val match = regex.find(text)
                    ?: return "Tell me a timer duration, like 10 minutes."

                val amount = match.groupValues[1].toInt()
                val unit = match.groupValues[2]
                val multiplier = when {
                    unit.startsWith("second") -> 1
                    unit.startsWith("minute") -> 60
                    else -> 3600
                }
                val seconds = amount.toLong() * multiplier

                if (seconds !in 1..86400) {
                    return "Please choose a timer up to 24 hours."
                }

                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "NANDINI timer")

                startActivity(intent)
                "Opening your timer."
            }

            text.contains("open wi-fi settings") ||
            text.contains("open wifi settings") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                "Opening Wi-Fi settings."
            }

            text.contains("open bluetooth settings") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "Opening Bluetooth settings."
            }

            text.contains("open display settings") -> {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                "Opening display settings."
            }

            text.contains("open camera") -> {
                startActivity(Intent("android.media.action.IMAGE_CAPTURE"))
                "Opening camera."
            }

            text.contains("open youtube") -> {
                openApp("com.google.android.youtube")
                "Opening YouTube if installed."
            }

            text.contains("open whatsapp") -> {
                openApp("com.whatsapp")
                "Opening WhatsApp if installed."
            }

            text.contains("open settings") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                "Opening Android settings."
            }

            text.contains("torch on") ||
text.contains("flashlight on") ||
text.contains("flash on") ||
text.contains("torch chalu") ||
text.contains("flashlight chalu") -> {
    setFlashlight(true)
}

text.contains("torch off") ||
text.contains("flashlight off") ||
text.contains("flash off") ||
text.contains("torch band") ||
text.contains("flashlight band") -> {
    setFlashlight(false)
}

text.contains("open camera") ||
text.contains("take a photo") ||
text.contains("take photo") -> {
    startActivity(Intent("android.media.action.IMAGE_CAPTURE"))
    "Opening camera. Take your photo there."
}

text.startsWith("call ") ||
text.startsWith("phone ") ||
text.startsWith("dial ") ||
text.startsWith("call ") ||
text.startsWith("mummy ko phone") ||
text.startsWith("mom ko phone") ||
text.startsWith("papa ko phone") -> {
    val target = when {
        text.startsWith("mummy ko phone") ->
            command.substringAfter("mummy ko phone").trim()
                .ifBlank { "Mummy" }

        text.startsWith("mom ko phone") ->
            command.substringAfter("mom ko phone").trim()
                .ifBlank { "Mom" }

        text.startsWith("papa ko phone") ->
            command.substringAfter("papa ko phone").trim()
                .ifBlank { "Papa" }

        else -> command.substringAfter(" ").trim()
    }

    startCallTo(target)
}

text.startsWith("open ") -> {
    val requestedApp = text.removePrefix("open ").trim()
    val launchableApps = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        0
    )

    val match = launchableApps.firstOrNull {
        it.loadLabel(packageManager).toString()
            .equals(requestedApp, ignoreCase = true)
    }

    if (match != null) {
        val appIntent = packageManager.getLaunchIntentForPackage(
            match.activityInfo.packageName
        )
        if (appIntent != null) {
            startActivity(appIntent)
            "Opening $requestedApp."
        } else {
            "I couldn't open that app."
        }
    } else {
        "I couldn't find that installed app."
    }
}
text.contains("flashlight") ||
text.contains("torch") ||
text.contains("flash light") -> {
    when {
        text.contains("off") ||
        text.contains("band") ||
        text.contains("bujha") -> setFlashlight(false)

        text.contains("on") ||
        text.contains("chalu") ||
        text.contains("jala") ||
        text.contains("turn on") ||
        text.contains("open") -> setFlashlight(true)

        else -> "Say 'turn on flashlight' or 'turn off flashlight'."
    }
}
            else -> null
        }
    } catch (e: Exception) {
        "I couldn't perform that action. Please check the app permissions and try again."
    }
}

private fun findContactNumber(name: String): String? {
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI
        .buildUpon()
        .appendPath(name)
        .build()

    contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getString(0)
        }
    }

    return null
}

private fun startCallTo(target: String): String {
    val cleaned = target.trim()
    if (cleaned.isBlank()) return "Tell me a contact name or phone number."

    val number = if (cleaned.any { it.isLetter() }) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                205
            )
            return "Please allow Contacts permission, then repeat the call command."
        }

        findContactNumber(cleaned)
            ?: return "I couldn't find $cleaned in your saved contacts."
    } else {
        cleaned
    }

    return try {
        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.fromParts("tel", number, null)
        )
        startActivity(intent)
        "Opening the dialer for $cleaned. Check the number and tap Call."
    } catch (e: Exception) {
        Log.e("NANDINI", "Could not open dialer", e)
        "I couldn't open the phone dialer."
    }
}

private fun openApp(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        ?: throw IllegalStateException("App is not installed or unavailable")
    startActivity(launchIntent)
}

private fun setFlashlight(enabled: Boolean): String {
    if (checkSelfPermission(Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            204
        )
        return "Please grant camera permission, then repeat the flashlight command."
    }

    return try {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "This device has no supported flashlight."

        manager.setTorchMode(cameraId, enabled)
        if (enabled) "Flashlight turned on." else "Flashlight turned off."
    } catch (e: Exception) {
        Log.e("NANDINI", "Flashlight command failed", e)
        "I couldn't change the flashlight. Check permissions and try again."
    }
}
private fun fetchWeather() {
if (isBusy) {
Toast.makeText(this, "Please wait for the current reply.", Toast.LENGTH_SHORT).show()
return
}

val permission = when {  
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==  
        PackageManager.PERMISSION_GRANTED ->  
        Manifest.permission.ACCESS_FINE_LOCATION  

    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==  
        PackageManager.PERMISSION_GRANTED ->  
        Manifest.permission.ACCESS_COARSE_LOCATION  

    else -> null  
}  

if (permission == null) {  
    requestPermissions(  
        arrayOf(  
            Manifest.permission.ACCESS_COARSE_LOCATION,  
            Manifest.permission.ACCESS_FINE_LOCATION  
        ),  
        203  
    )  
    return  
}  

statusText.text = "● GETTING WEATHER..."

scope.launch {  
    try {  
        val result = withContext(Dispatchers.IO) {  
            val client = LocationServices.getFusedLocationProviderClient(this@MainActivity)  

            val location = client.getCurrentLocation(  
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,  
                CancellationTokenSource().token  
            ).await() ?: throw IllegalStateException(  
                "Location unavailable. Please enable your phone's location and try again."  
            )  

            getLiveWeather(location)  
        }  

        addMessage("NANDINI", result)  
    } catch (e: CancellationException) {  
        throw e  
    } catch (e: Exception) {  
        Log.e("NANDINI", "Weather request failed", e)  
        addMessage(  
            "NANDINI",  
            "I couldn't get live weather right now. Please check your location permission, enable Location on your phone, and check your internet connection. 💜"  
        )  
    } finally {  
statusText.text = "● READY"
    }  
}

}

private fun getLiveWeather(location: Location): String {
val urlString =
"https://api.open-meteo.com/v1/forecast" +
"?latitude=${location.latitude}" +
"&longitude=${location.longitude}" +
"&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
"&timezone=auto"

val connection = URL(urlString).openConnection() as HttpURLConnection  

try {  
    connection.requestMethod = "GET"  
    connection.connectTimeout = 10000  
    connection.readTimeout = 10000  

    if (connection.responseCode !in 200..299) {  
        throw IllegalStateException("Weather service returned an error.")  
    }  

    val response = connection.inputStream.bufferedReader().use { it.readText() }  
    val root = JSONObject(response)  
    val current = root.getJSONObject("current")  
    val units = root.getJSONObject("current_units")  

    val temperature = current.getDouble("temperature_2m")  
    val feelsLike = current.getDouble("apparent_temperature")  
    val humidity = current.getInt("relative_humidity_2m")  
    val wind = current.getDouble("wind_speed_10m")  
    val code = current.getInt("weather_code")  
    val time = current.optString("time", "Unknown")  

    val condition = when (code) {  
        0 -> "Clear sky"  
        1 -> "Mainly clear"  
        2 -> "Partly cloudy"  
        3 -> "Overcast"  
        45, 48 -> "Fog"  
        51, 53, 55 -> "Drizzle"  
        56, 57 -> "Freezing drizzle"  
        61, 63, 65 -> "Rain"  
        66, 67 -> "Freezing rain"  
        71, 73, 75, 77 -> "Snow"  
        80, 81, 82 -> "Rain showers"  
        85, 86 -> "Snow showers"  
        95 -> "Thunderstorm"  
        96, 99 -> "Thunderstorm with hail"  
        else -> "Unknown conditions"  
    }  

    return """  
        🌦 LIVE WEATHER  

        Condition: $condition  
        Temperature: $temperature ${units.optString("temperature_2m", "°C")}  
        Feels like: $feelsLike ${units.optString("apparent_temperature", "°C")}  
        Humidity: $humidity%  
        Wind speed: $wind ${units.optString("wind_speed_10m", "km/h")}  

        Observation time: $time  
        Source: Open-Meteo  
    """.trimIndent()  
} finally {  
    connection.disconnect()  
}

}

override fun onPause() {
    super.onPause()
    if (wakeWordEnabled) {
        stopWakeWordListening()
        statusText.text = "● WAKE WORD PAUSED"
    }
}

override fun onResume() {
    super.onResume()
    if (wakeWordEnabled) {
        startWakeWordSession()
    }
}

override fun onDestroy() {
    stopWakeWordListening()
    super.onDestroy()
    scope.cancel()
    tts?.stop()  
    tts?.shutdown()  
    tts = null  
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleSpotifyCallback(intent)

}

private fun isSpotifyCommand(command: String): Boolean {
    val text = command.lowercase(Locale.ROOT)
    return text.contains("spotify") ||
        text.startsWith("play song ") ||
        text.startsWith("play music ") ||
        text.startsWith("play playlist ")
}

private fun handleSpotifyCommand(command: String) {
    val text = command.trim()
    val lower = text.lowercase(Locale.ROOT)

    // Accept Spotify playlist URIs or shared playlist URLs.
    val playlistId = Regex(
        """(?:spotify:playlist:|open\.spotify\.com/playlist/)([A-Za-z0-9]+)"""
    ).find(text)?.groupValues?.get(1)

    if (playlistId != null) {
        playSpotifyUri(
            "spotify:playlist:$playlistId",
            "your playlist"
        )
        return
    }

    val query = text
        .replace(Regex("""(?i)\b(on|using)\s+spotify\b"""), "")
        .replace(Regex("""(?i)\bspotify\b"""), "")
        .replace(
            Regex("""(?i)^\s*(please\s+)?(play|put on|start)\s+"""),
            ""
        )
        .replace(Regex("""(?i)^\s*(song|music|track|playlist)\s+"""), "")
        .trim()

    if (query.isBlank()) {
        addMessage(
            "NANDINI",
            "Tell me a song name, for example: play Tollywood 3 on Spotify."
        )
        return
    }

    val savedToken = spotifyPrefs.getString("access_token", null)
    val expiry = spotifyPrefs.getLong("expires_at", 0L)

    if (savedToken == null || System.currentTimeMillis() >= expiry) {
        beginSpotifyAuthorization(query)
        return
    }

    scope.launch {
        try {
            statusText.text = "● SEARCHING SPOTIFY..."
            val track = withContext(Dispatchers.IO) {
                searchSpotifyTrack(query, savedToken)
            }
            playSpotifyUri(track.second, track.first)
        } catch (e: Exception) {
            Log.e("NANDINI_SPOTIFY", "Spotify command failed", e)
            addMessage(
                "NANDINI",
                "Couldn't find or play that song. ${e.message ?: ""}"
            )
        } finally {
            statusText.text = "● READY"
        }
    }
}
}
