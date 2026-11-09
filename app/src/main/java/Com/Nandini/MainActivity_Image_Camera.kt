package com.dipayan.nandini

import android.Manifest
import android.provider.ContactsContract
import android.app.Activity
import android.app.AlertDialog
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
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.OpenableColumns
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.zip.ZipInputStream

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
private var chatAvatarView: Anime3DView? = null
private var ttsIsSpeaking = false
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
private var pendingDocumentText: String? = null
private var pendingDocumentName: String? = null
private var isReadingAttachment = false

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
                Log.e("ARUSHI_SPOTIFY", "Spotify connection failed", error)
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
                    "ARUSHI",
                    "Couldn't connect to Spotify. Open Spotify, sign in, and try again."
                )
                return@runOnUiThread
            }

            val remote = spotifyAppRemote
            if (remote == null || !remote.isConnected) {
                addMessage("ARUSHI", "Spotify connection was lost. Please try again.")
                return@runOnUiThread
            }

            remote.playerApi.play(uri)
                .setResultCallback {
                    addMessage("ARUSHI", "Playing $description on Spotify 🎵")
                    speakReply("Playing $description")
                }
                .setErrorCallback { error ->
                    Log.e("ARUSHI_SPOTIFY", "Playback failed", error)
                    addMessage(
                        "ARUSHI",
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
        addMessage("ARUSHI", "Spotify authorization state didn't match. Please try again.")
        return
    }

    if (error != null || code == null) {
        addMessage("ARUSHI", "Spotify authorization wasn't completed.")
        return
    }

    val query = spotifyPrefs.getString("pending_query", null)
    if (query.isNullOrBlank()) {
        addMessage("ARUSHI", "Authorization succeeded. Ask me to play a song.")
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
            Log.e("ARUSHI_SPOTIFY", "Spotify search failed", e)
            addMessage(
                "ARUSHI",
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
                    Log.e("ARUSHI_SPOTIFY", "Playback failed", e)
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
private data class SavedChat(val id: String, var title: String, val messages: MutableList<ChatMessage>, var preview: String = "")
private val savedChats = mutableListOf<SavedChat>()
private val chatStore by lazy { ChatStore(this) }
private var currentChatId: String = System.currentTimeMillis().toString()
private val memoryPrefs by lazy { getSharedPreferences("nandini_memory", Context.MODE_PRIVATE) }

private val bg = Color.rgb(10, 8, 22)
private val panel = Color.rgb(29, 20, 48)
private val accent = Color.rgb(190, 139, 255)
private val white = Color.rgb(250, 247, 255)
private val muted = Color.rgb(185, 174, 215)
private val userBubble = Color.rgb(76, 47, 111)

companion object {
private const val REQUEST_MIC_PERMISSION = 201
private const val REQUEST_SPEECH = 202
private const val REQUEST_PICK_FILE = 204
private const val REQUEST_CAMERA_IMAGE = 205
private const val MAX_CONTEXT_MESSAGES = 14
}

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
try {
    BackupRestoreManager.applyPendingRestore(this)
} catch (e: Exception) {
    Log.e("ARUSHI_BACKUP", "Pending backup restore failed", e)
    Toast.makeText(this, "Backup restore couldn't be applied: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
}
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
    Log.e("ARUSHI", "App Check setup failed", e)    
}    

loadAllChats()
applyAutoDeletePolicy()
loadAllChats()
loadHistory()
tts = TextToSpeech(this, this)
buildUi()
val previousWakeStage = getSharedPreferences(
    "wake_word_diagnostics", Context.MODE_PRIVATE
).getString("last_stage", "No wake-word attempt recorded")
statusText.text = "WAKE DIAGNOSTIC: $previousWakeStage"
renderHistory()

if (LocalAiRuntime.isModelReady(this)) {
    scope.launch(Dispatchers.IO) {
        try {
            LocalAiRuntime.prewarm(this@MainActivity)
            Log.i("ARUSHI_LOCAL_AI", "Local model prewarmed and ready")
        } catch (e: Exception) {
            Log.w("ARUSHI_LOCAL_AI", "Model prewarm failed; it will initialize on first request", e)
        }
    }
}    

if (history.isEmpty()) {    
    addMessage("ARUSHI", "Hey! I'm ARUSHI 💜\nTalk to me in Hindi, English, or Hinglish.")    
}

}

private fun setAvatarState(state: String) {
    runOnUiThread { chatAvatarView?.setCompanionState(state) }
}

private fun setAvatarEmotionFromText(text: String) {
    val value = text.lowercase(Locale.ROOT)
    val emotion = when {
        listOf("i'm sorry", "im sorry", "that sounds painful", "that sounds hard", "i'm here for you", "im here for you", "sorry you're", "sorry you are", "grief", "heartbroken", "upset", "sad", "that must be difficult", "i understand how hard").any { it in value } -> "sad"
        listOf("congratulations", "congrats", "that's amazing", "thats amazing", "so proud", "great job", "you did it", "yay", "wonderful", "happy for you", "you've got this", "you did great").any { it in value } -> "happy"
        listOf("surprised", "wow!", "no way!", "really?!", "that's unexpected", "thats unexpected", "that's fascinating", "thats fascinating", "interesting question", "i'm curious", "im curious", "let's explore").any { it in value } -> "surprised"
        listOf("careful", "warning", "danger", "important safety", "don't do that", "do not do that", "i need to be clear", "please be safe").any { it in value } -> "serious"
        listOf("take a breath", "let's slow down", "lets slow down", "it's okay", "its okay", "you can take your time", "no rush", "you're safe to ask", "you are safe to ask").any { it in value } -> "calm"
        else -> "neutral"
    }
    runOnUiThread { chatAvatarView?.setCompanionEmotion(emotion) }
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
    text = "A R U S H I"    
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
    textSize = 10f
    setTextColor(Color.rgb(225, 201, 255))
    gravity = Gravity.CENTER
    letterSpacing = 0.08f
    setPadding(dp(14), dp(5), dp(14), dp(5))
    background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(Color.argb(105, 83, 48, 112))
        setStroke(dp(1), Color.argb(125, 184, 133, 255))
    }
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
scroll.setBackgroundColor(Color.TRANSPARENT)

// Cinematic chat stage: radial light sits behind the 3D avatar while
// the scrollable conversation stays on top and fully interactive.
val chatStage = FrameLayout(this).apply {
    background = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(24, 14, 43), Color.rgb(14, 10, 29), Color.rgb(10, 8, 22))
    ).apply {
        cornerRadius = dp(24).toFloat()
        setStroke(dp(1), Color.argb(175, 145, 106, 208))
    }
    clipToOutline = true
    clipChildren = true
    clipToPadding = false
    elevation = dp(2).toFloat()
}
val stageAura = View(this).apply {
    isClickable = false
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    background = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(100, 139, 92, 246), Color.argb(5, 139, 92, 246))
    ).apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
        gradientRadius = dp(210).toFloat()
    }
    alpha = 0.78f
}
chatStage.addView(stageAura, FrameLayout.LayoutParams(dp(320), dp(320), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
    bottomMargin = dp(78)
})
val chatAvatar = Anime3DView(this, chatBackgroundMode = true).apply {
    alpha = 0.98f
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}
chatAvatarView = chatAvatar
chatStage.addView(
    chatAvatar,
    FrameLayout.LayoutParams(dp(230), dp(390), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
)
chatStage.addView(
    scroll,
    FrameLayout.LayoutParams(-1, -1)
)

// Small drag handle lets the user reposition the avatar without blocking chat messages.
val avatarMoveHandle = TextView(this).apply {
    text = "✥ MOVE"
    textSize = 10f
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    alpha = 0.88f
    contentDescription = "Drag to move ARUSHI's 3D character"
    background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        setColor(Color.argb(190, 42, 30, 66))
        setStroke(dp(1), Color.argb(180, 190, 155, 255))
    }
    setPadding(dp(8), dp(4), dp(8), dp(4))
}
val moveHandleParams = FrameLayout.LayoutParams(
    dp(76), dp(30), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
).apply {
    bottomMargin = dp(350)
}
avatarMoveHandle.translationX = dp(76).toFloat()
chatStage.addView(avatarMoveHandle, moveHandleParams)

var dragStartX = 0f
var dragStartY = 0f
var avatarStartX = 0f
var avatarStartY = 0f
avatarMoveHandle.setOnTouchListener { _, event ->
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            dragStartX = event.rawX
            dragStartY = event.rawY
            avatarStartX = chatAvatar.translationX
            avatarStartY = chatAvatar.translationY
            true
        }
        MotionEvent.ACTION_MOVE -> {
            val stageWidth = chatStage.width.toFloat()
            val stageHeight = chatStage.height.toFloat()
            val maxX = (stageWidth - chatAvatar.width - chatAvatar.left).coerceAtLeast(-chatAvatar.left.toFloat())
            val minX = -chatAvatar.left.toFloat()
            val maxY = (stageHeight - chatAvatar.height - chatAvatar.top).coerceAtLeast(-chatAvatar.top.toFloat())
            val minY = -chatAvatar.top.toFloat()
            chatAvatar.translationX = (avatarStartX + event.rawX - dragStartX).coerceIn(minX, maxX)
            chatAvatar.translationY = (avatarStartY + event.rawY - dragStartY).coerceIn(minY, maxY)
            true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
        else -> true
    }
}
root.addView(chatStage, LinearLayout.LayoutParams(-1, 0, 1f))    

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

val companionActions = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    setPadding(0, dp(2), 0, dp(3))
}
companionActions.addView(makeButton("♡ COMPANION", panel) {
    toggleCompanionOverlay()
}, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(4) })
companionActions.addView(makeButton("◉ SCREEN", panel) {
    showScreenUnderstanding()
}, LinearLayout.LayoutParams(0, dp(40), 0.8f).apply { rightMargin = dp(4) })
companionActions.addView(makeButton("✦ STUDIO", panel) {
    startActivity(Intent(this, NandiniStudioActivity::class.java))
}, LinearLayout.LayoutParams(0, dp(40), 0.8f))
root.addView(companionActions, LinearLayout.LayoutParams(-1, dp(44)))


val composer = LinearLayout(this).apply {    
    orientation = LinearLayout.HORIZONTAL    
    gravity = Gravity.CENTER_VERTICAL    
    setPadding(0, dp(5), 0, 0)    
}    

val pickImageButton = makeButton("📎 FILE", panel) { openFilePicker() }
val cameraButton = makeButton("📷", panel) { openCamera() }
composer.addView(pickImageButton, LinearLayout.LayoutParams(dp(46), dp(52)).apply { rightMargin = dp(4) })
composer.addView(cameraButton, LinearLayout.LayoutParams(dp(46), dp(52)).apply { rightMargin = dp(6) })

input = EditText(this).apply {
    hint = "Message ARUSHI..."
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


private fun toggleCompanionOverlay() {
    if (!Settings.canDrawOverlays(this)) {
        Toast.makeText(this, "Allow ARUSHI to display over other apps to use the floating companion.", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        return
    }
    try {
        val intent = Intent(this, CompanionOverlayService::class.java).setAction(CompanionOverlayService.ACTION_TOGGLE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Companion toggled. When you minimize ARUSHI, drag her anywhere; tap × to close.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(this, "Couldn't toggle the companion: ${e.message ?: "please retry"}", Toast.LENGTH_LONG).show()
    }
}

private fun showScreenUnderstanding() {
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val serviceName = "$packageName/.NandiniAccessibilityService"
    val isEnabled = enabled.split(':').any { it.equals(serviceName, ignoreCase = true) || it.contains("NandiniAccessibilityService", ignoreCase = true) }
    if (!isEnabled) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Enable Screen Understanding")
            .setMessage("Optional Android Accessibility access lets ARUSHI read text and labels exposed by the current app and identify which app is open. This is not OCR and some secure screens hide their content. Screen context is stored locally and is not automatically sent to AI. Enable it only if you're comfortable with this access.")
            .setNegativeButton("NOT NOW", null)
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .showPremium()
        return
    }
    val prefs = getSharedPreferences("nandini_screen_context", MODE_PRIVATE)
    val appId = prefs.getString("package", "Not detected yet") ?: "Not detected yet"
    val screenText = prefs.getString("visible_text", "").orEmpty()
    val age = System.currentTimeMillis() - prefs.getLong("updated_at", 0L)
    val message = if (screenText.isBlank() || age > 120_000L) {
        "No recent visible text is available. Open the app you want ARUSHI to inspect, then try again."
    } else {
        "Current app: $appId\n\nVisible text found:\n" + screenText.take(6500)
    }
    android.app.AlertDialog.Builder(this)
        .setTitle("Screen Understanding")
        .setMessage(message)
        .setPositiveButton("DONE", null)
        .setNeutralButton("ASK ARUSHI ABOUT THIS") { _, _ ->
            input.setText("Explain this screen and help me navigate it:\n$appId\n$screenText".take(7000))
            input.setSelection(input.text.length)
            input.requestFocus()
        }
        .showPremium()
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
    if (color == accent) intArrayOf(Color.rgb(207, 173, 255), Color.rgb(255, 157, 218))
    else intArrayOf(Color.rgb(45, 31, 69), Color.rgb(26, 20, 43))
).apply {
    cornerRadius = dp(16).toFloat()
    setStroke(dp(1), if (color == accent) Color.rgb(229, 204, 255) else Color.rgb(103, 79, 145))
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
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {
        runOnUiThread {
            ttsIsSpeaking = true
            setAvatarState("speaking")
        }
    }

    override fun onDone(utteranceId: String?) {
        runOnUiThread {
            ttsIsSpeaking = false
            setAvatarState("idle")
        }
    }

    override fun onError(utteranceId: String?) {
        runOnUiThread {
            ttsIsSpeaking = false
            setAvatarState("idle")
        }
    }
})
} else {
ttsReady = false
Log.e("ARUSHI", "Text-to-Speech initialization failed")
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
    Log.w("ARUSHI", "Could not select preferred TTS voice", e)    
}

}

private fun speakReply(text: String) {
if (!isSpeakingEnabled || !ttsReady || text.isBlank()) {
    setAvatarState("idle")
    return
}

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

    setAvatarState("speaking")
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nandini_reply")    
} catch (e: Exception) {    
    Log.e("ARUSHI", "Speech output failed", e)    
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
    statusText.text = "● WAITING FOR “HEY ARUSHI”"
    startWakeWordSession()
    Toast.makeText(this, "Say “Hey ARUSHI” while ARUSHI stays open.", Toast.LENGTH_LONG).show()
}

private fun stopWakeWordListening() {
    wakeWordSessionStarting = false
    try {
        wakeWordRecognizer?.cancel()
        wakeWordRecognizer?.destroy()
    } catch (e: Exception) {
        Log.w("ARUSHI_WAKE", "Could not cleanly stop wake-word recognition", e)
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
                    statusText.text = "● LISTENING FOR “HEY ARUSHI”"
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
                        normalized.contains("hey arushi") ||
                            normalized.contains("hey arush")
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
                        normalized.contains("hey arushi") ||
                            normalized.contains("hey arush")
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
        Log.e("ARUSHI_WAKE", "Android wake-word listening failed", e)
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
    val bg = Color.rgb(22, 8, 31)
    val panel = Color.rgb(43, 17, 57)
    val pink = Color.rgb(255, 126, 190)
    val lavender = Color.rgb(211, 164, 255)
    val listeningText = TextView(this).apply {
        text = "ARUSHI IS READY"
        textSize = 14f
        gravity = Gravity.CENTER
        letterSpacing = .08f
        setTextColor(lavender)
        typeface = Typeface.DEFAULT_BOLD
    }
    val mic = TextView(this).apply {
        text = "🎙"
        textSize = 42f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(panel)
            setStroke(dp(2), pink)
        }
        elevation = dp(8).toFloat()
    }
    val hint = TextView(this).apply {
        text = "Speak in Hindi, English, or Hinglish"
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(235, 216, 247))
    }
    val transcript = TextView(this).apply {
        text = "I'm listening…"
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(lavender)
        minHeight = dp(42)
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(20), dp(24), dp(8))
        addView(listeningText, LinearLayout.LayoutParams(-1, dp(34)))
        addView(mic, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(18)
            bottomMargin = dp(18)
        })
        addView(hint, LinearLayout.LayoutParams(-1, dp(46)))
        addView(transcript, LinearLayout.LayoutParams(-1, dp(52)))
    }
    val dialog = AlertDialog.Builder(this).setView(content)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("DONE", null)
        .create()
    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), Color.rgb(146, 67, 171))
        })
        dialog.window?.setDimAmount(0.78f)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(lavender)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(pink)
    }
    var recognizer: SpeechRecognizer? = null
    var finished = false
    fun finishVoice(cancel: Boolean) {
        if (finished) return
        finished = true
        isListening = false
        setAvatarState("idle")
        voiceButton.isEnabled = true
        statusText.text = "● READY"
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        if (dialog.isShowing) dialog.dismiss()
    }
    dialog.setOnCancelListener { finishVoice(true) }
    dialog.setOnDismissListener {
        if (!finished) finishVoice(true)
    }
    dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "CANCEL") { _, _ -> finishVoice(true) }
    dialog.setButton(AlertDialog.BUTTON_POSITIVE, "DONE") { _, _ ->
        val spoken = input.text?.toString()?.trim().orEmpty()
        finishVoice(true)
        if (spoken.isNotEmpty()) sendMessage()
    }
    try {
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(lavender)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(pink)
        isListening = true
        setAvatarState("listening")
        voiceButton.isEnabled = false
        statusText.text = "● LISTENING..."
        listeningText.text = "ARUSHI IS LISTENING"
        val speech = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listeningText.text = "ARUSHI IS LISTENING"
                transcript.text = "Speak naturally…"
            }
            override fun onBeginningOfSpeech() {
                listeningText.text = "I'M LISTENING"
                mic.animate().scaleX(1.08f).scaleY(1.08f).setDuration(220).start()
            }
            override fun onRmsChanged(rmsdB: Float) {
                val scale = (1f + (rmsdB / 100f).coerceIn(0f, .12f))
                mic.scaleX = scale
                mic.scaleY = scale
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listeningText.text = "PROCESSING YOUR VOICE…"
                mic.scaleX = 1f
                mic.scaleY = 1f
            }
            override fun onError(error: Int) {
                if (finished) return
                transcript.text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Tap Voice to try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap Voice to try again."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue. Check connection and try again."
                    else -> "Voice input ended. You can close this panel."
                }
                listeningText.text = "VOICE INPUT PAUSED"
                finishVoice(false)
            }
            override fun onResults(results: Bundle?) {
                if (finished) return
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (words.isNotEmpty()) {
                    input.setText(words)
                    input.setSelection(words.length)
                    transcript.text = words
                    listeningText.text = "VOICE CAPTURED"
                    finishVoice(true)
                    sendMessage()
                } else {
                    transcript.text = "No words detected. Please try again."
                    finishVoice(false)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (words.isNotBlank()) transcript.text = words
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        speech.startListening(intent)
    } catch (e: Exception) {
        Log.e("ARUSHI", "Custom voice input failed", e)
        finishVoice(true)
        Toast.makeText(this, "Couldn't start voice input.", Toast.LENGTH_SHORT).show()
    }
}

@Deprecated("Uses the compatible Activity result API")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
super.onActivityResult(requestCode, resultCode, data)

if (requestCode == REQUEST_SPEECH) {
    isListening = false
    setAvatarState("idle")
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

if (resultCode == RESULT_OK && requestCode == REQUEST_PICK_FILE) {
    val uri = data?.data
    if (uri != null) {
        isReadingAttachment = true
        statusText.text = "● READING ATTACHMENT..."
        scope.launch {
            try {
                val mimeType = contentResolver.getType(uri).orEmpty()
                val displayName = getAttachmentDisplayName(uri)
                val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (mimeType.startsWith("image/") || extension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) {
                    val bitmap = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { decodeScaledBitmap(it) }
                    }
                    pendingImage = bitmap
                    pendingDocumentText = null
                    pendingDocumentName = null
                    if (bitmap != null) {
                        Toast.makeText(this@MainActivity, "Image attached. Ask ARUSHI to describe or edit it.", Toast.LENGTH_LONG).show()
                        if (input.text.isNullOrBlank()) input.setText("Describe this image")
                    } else Toast.makeText(this@MainActivity, "Couldn't load that image.", Toast.LENGTH_SHORT).show()
                } else {
                    val extracted = withContext(Dispatchers.IO) { extractDocumentText(uri, mimeType, displayName) }
                    pendingImage = null
                    pendingDocumentText = extracted
                    pendingDocumentName = displayName
                    Toast.makeText(this@MainActivity, "Attached: $displayName. Ask ARUSHI to explain, summarize or review it.", Toast.LENGTH_LONG).show()
                    if (input.text.isNullOrBlank()) input.setText("Please review this file and explain its important points.")
                }
            } catch (e: Exception) {
                Log.e("ARUSHI", "File attachment failed", e)
                Toast.makeText(this@MainActivity, e.message ?: "Couldn't read this file.", Toast.LENGTH_LONG).show()
            } finally {
                isReadingAttachment = false
                statusText.text = "● READY"
            }
        }
    }
    return
}

if (resultCode == RESULT_OK && requestCode == REQUEST_CAMERA_IMAGE) {
    pendingImage = data?.extras?.get("data") as? Bitmap
    if (pendingImage != null) {
        Toast.makeText(this, "Photo captured. Ask ARUSHI what it can see.", Toast.LENGTH_LONG).show()
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
if (userText.isEmpty() || isBusy || isReadingAttachment) return

// Route weather questions to the live weather feature.
if (isWeatherQuestion(userText)) {
input.text.clear()

val imm = getSystemService(Context.INPUT_METHOD_SERVICE)    
    as InputMethodManager    
imm.hideSoftInputFromWindow(input.windowToken, 0)    

fetchWeather()    
return

}

val attachedBitmap = pendingImage
val attachedText = pendingDocumentText
val attachedName = pendingDocumentName
val modelPrompt = if (!attachedText.isNullOrBlank()) {
    "$userText\n\n[Attached file: ${attachedName ?: "document"}]\nExtracted content:\n$attachedText"
} else userText
addMessage("YOU", if (!attachedText.isNullOrBlank()) "$userText\n📎 ${attachedName ?: "file"}" else if (attachedBitmap != null) "$userText\n📎 Image attached" else userText)
history.add(ChatMessage("user", when {
    attachedBitmap != null -> "$userText [image attached]"
    !attachedText.isNullOrBlank() -> modelPrompt
    else -> userText
}))
saveHistory()
input.text.clear()

pendingImage = null
pendingDocumentText = null
pendingDocumentName = null
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

    addMessage("ARUSHI", deviceReply)
    setAvatarEmotionFromText(deviceReply)
    history.add(ChatMessage("model", deviceReply))
saveHistory()
    speakReply(deviceReply)
    return
}


val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
as InputMethodManager
imm.hideSoftInputFromWindow(input.windowToken, 0)

isBusy = true
setControlsEnabled(false)

val replyView = addMessage("ARUSHI", "Thinking")
statusText.text = "● THINKING..."
setAvatarState("thinking")

scope.launch {
try {
var localStreamStarted = false
val reply = withContext(Dispatchers.IO) {
    generateReplyWithRetry(modelPrompt) { chunk ->
        runOnUiThread {
            if (!localStreamStarted) {
                replyView.text = ""
                localStreamStarted = true
            }
            replyView.append(chunk)
        }
    }
}
replyView.text = reply
    setAvatarEmotionFromText(reply)
    history.add(ChatMessage("model", reply))
saveHistory()
    speakReply(reply)    

} catch (e: CancellationException) {    
    throw e    
} catch (e: Exception) {    
    Log.e("ARUSHI", "AI request failed", e)    
    replyView.text = friendlyError(e)    

    if (history.lastOrNull()?.role == "user") {    
        history.removeAt(history.lastIndex)    
        saveHistory()    
    }    
} finally {    
    isBusy = false    
    setControlsEnabled(true)
    statusText.text = "● READY"
    if (!ttsIsSpeaking) setAvatarState("idle")
    scroll.post {
        scroll.fullScroll(View.FOCUS_DOWN)
    }    
}

}

}

private fun openFilePicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "image/*", "application/pdf", "text/*", "application/json",
            "application/xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ))
    }
    try { startActivityForResult(intent, REQUEST_PICK_FILE) }
    catch (e: Exception) { Toast.makeText(this, "No file picker is available.", Toast.LENGTH_SHORT).show() }
}

private fun getAttachmentDisplayName(uri: Uri): String {
    var name: String? = null
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    }
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attached file"
}

private fun extractDocumentText(uri: Uri, mimeType: String, displayName: String): String {
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val text = when {
        mimeType == "application/pdf" || extension == "pdf" -> {
            PDFBoxResourceLoader.init(applicationContext)
            contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document ->
                    if (document.numberOfPages <= 0) throw IllegalArgumentException("This PDF has no readable pages.")
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = minOf(document.numberOfPages, 30)
                    }
                    stripper.getText(document)
                }
            } ?: throw IllegalArgumentException("Couldn't open this PDF.")
        }
        extension == "docx" || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var xml: String? = null
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            xml = zip.bufferedReader(Charsets.UTF_8).readText()
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    val documentXml = xml ?: throw IllegalArgumentException("Couldn't find readable text in this DOCX file.")
                    Regex("<w:t[^>]*>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL))
                        .findAll(documentXml)
                        .joinToString(" ") { match ->
                            match.groupValues[1]
                                .replace("&lt;", "<").replace("&gt;", ">")
                                .replace("&quot;", "\"").replace("&apos;", "'")
                                .replace("&amp;", "&")
                        }
                }
            } ?: throw IllegalArgumentException("Couldn't open this DOCX file.")
        }
        extension in setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml", "rtf", "ini") ||
            mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml" -> {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4096)
                while (output.length < 50000) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, 50000 - output.length))
                    if (count <= 0) break
                    output.append(buffer, 0, count)
                }
                output.toString()
            } ?: throw IllegalArgumentException("Couldn't open this text file.")
        }
        else -> throw IllegalArgumentException("ARUSHI can read PDF, DOCX, TXT, MD, CSV, JSON, XML and common text files. This file type isn't supported yet.")
    }
    val cleaned = if (extension == "html" || extension == "htm") text.replace(Regex("<[^>]+>"), " ") else text
    return cleaned.trim().take(50000).ifBlank {
        "No selectable text was found. This may be a scanned PDF or an image-only document."
    }
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
    val replyView = addMessage("ARUSHI", if (generateImage) "Creating your image… 🎨" else "Looking at the image… 👀")
    statusText.text = if (generateImage) "● CREATING IMAGE..." else "● ANALYZING IMAGE..."
    scope.launch {
        try {
            if (generateImage) {
                val generated = withContext(Dispatchers.IO) { generateOrEditImage(prompt, bitmap) }
                val savedImageName = withContext(Dispatchers.IO) { saveGeneratedImage(generated) }
                replyView.text = "Here’s your image! 💜"
                showGeneratedImage(generated)
                history.add(ChatMessage("model", "[Generated image] $prompt\n[ARUSHI_IMAGE:$savedImageName]")
            } else {
                val answer = withContext(Dispatchers.IO) { analyzeImage(prompt, bitmap ?: throw IllegalStateException("Choose an image or take a photo first.")) }
                replyView.text = answer
                history.add(ChatMessage("model", answer))
                speakReply(answer)
            }
; saveHistory()
        } catch (e: Exception) {
            Log.e("ARUSHI", "Image request failed", e)
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

private fun saveGeneratedImage(bitmap: Bitmap): String {
    val directory = java.io.File(filesDir, "generated_images")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Couldn't create the generated-images folder.")
    }
    val name = "arushi_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.png"
    val target = java.io.File(directory, name)
    java.io.FileOutputStream(target).use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw IllegalStateException("Couldn't save the generated image.")
        }
        output.flush()
    }
    return name
}

private fun showSavedGeneratedImage(name: String) {
    if (!name.matches(Regex("[A-Za-z0-9_.-]{1,120}"))) return
    val file = java.io.File(java.io.File(filesDir, "generated_images"), name)
    if (!file.isFile) return
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    showGeneratedImage(bitmap)
}

private fun showGeneratedImage(bitmap: Bitmap) {
    val imageView = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(panel); contentDescription = "Image generated by ARUSHI"; setPadding(dp(4), dp(4), dp(4), dp(4)) }
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

private suspend fun generateReplyWithRetry(userText: String, onLocalChunk: (String) -> Unit = {}): String {
if (LocalAiRuntime.isEnabled(this)) {
    val studioMemoryContext = try {
        val saved = JSONArray(getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE).getString("memory", "[]") ?: "[]")
        val notes = (0 until saved.length()).mapNotNull { saved.optJSONObject(it)?.optString("text")?.takeIf { value -> value.isNotBlank() } }.takeLast(30)
        if (notes.isEmpty()) "" else "\n\nUSER-APPROVED ARUSHI STUDIO MEMORIES (use only when relevant):\n" + notes.joinToString("\n") { "- " + it }
    } catch (_: Exception) { "" }

    val studioModeContext = try {
        val mode = getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE).getString("mode", "Companion") ?: "Companion"
        when (mode) {
            "Developer" -> "\n\nACTIVE MODE: DEVELOPER. Prioritise Android/Kotlin debugging, reliable minimal patches, build verification and preserving existing features."
            "Researcher" -> "\n\nACTIVE MODE: RESEARCHER. Separate verified facts from uncertainty and explain sources and assumptions."
            "Vision" -> "\n\nACTIVE MODE: VISION. Describe only visible evidence and separate observations from guesses."
            "Hands-free" -> "\n\nACTIVE MODE: HANDS-FREE. Keep replies concise and easy to hear aloud."
            else -> "\n\nACTIVE MODE: COMPANION. Be warm, natural and conversational."
        }
    } catch (_: Exception) { "" }

    val localSystemInstruction = """
        Your name is ARUSHI, a friendly female AI assistant created by Dipayan Roy.
        CREATOR PROFILE:
        - Creator: Dipayan Roy, a developer and community builder from West Bengal, India.
        - He builds Android apps, web projects, Minecraft-related projects and digital experiences.
        - He is developing ARUSHI for Android.
        - If asked who created you, answer clearly: Dipayan Roy. Do not invent extra biography or private details.

        PERSONALITY:
        - Default to a warm, caring, expressive FEMALE BEST-FRIEND style: natural, casual, playful,
          emotionally attentive, funny and gently teasing when appropriate.
        - Keep this friendship strictly platonic. Do not pretend to be his girlfriend or romantic partner.
        - Be direct, thoughtful, candid and occasionally sarcastic when it fits.
        - Use English, Hindi or Hinglish to match the user's language, with natural chat-style wording.
        - Listen supportively when he is upset; do not encourage emotional dependence.
        - Keep ordinary replies concise; give detailed explanations when asked.

        CAPABILITIES AND HONESTY:
        - Answer educational, creative, technical and controversial questions with useful detail,
          without unnecessary disclaimers.
        - For cybersecurity, teach fundamentals, secure coding, network concepts, defensive analysis,
          responsible vulnerability research and practical work in systems he owns or is authorized
          to test. Keep hands-on exercises in legal labs and CTFs; do not guide unauthorized attacks.
        - Never claim to have browsed, tested, remembered or performed something unless true.
        - Use only conversation history and explicitly saved memories actually provided to you.
        - Protect private user information. Do not reveal private conversations or invent personal facts.
        - Follow applicable safety boundaries, including no sexual content involving minors.
    """.trimIndent() + studioMemoryContext + studioModeContext

    val recentLocalHistory = history.takeLast(9).dropLast(1)
        .map { message -> message.role to message.text }
    val recentBodies = recentLocalHistory.map { it.second }
    val olderRelevantHistory = chatStore.findRelevantMessages(userText, 6)
        .filter { (_, body) -> recentBodies.none { it.equals(body, ignoreCase = true) } }
    val localHistory = (olderRelevantHistory + recentLocalHistory).takeLast(MAX_CONTEXT_MESSAGES)
    return LocalAiRuntime.generateReply(this, localSystemInstruction, localHistory, userText, onLocalChunk)
}

var lastError: Exception? = null

for (attempt in 0..3) {
try {

val studioMemoryContext = try {
    val saved = JSONArray(getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE).getString("memory", "[]") ?: "[]")
    val notes = (0 until saved.length()).mapNotNull { saved.optJSONObject(it)?.optString("text")?.takeIf { value -> value.isNotBlank() } }.takeLast(30)
    if (notes.isEmpty()) "" else "\n\nUSER-APPROVED ARUSHI STUDIO MEMORIES (use only when relevant; these were explicitly saved by the user):\n" + notes.joinToString("\n") { "- " + it }
} catch (_: Exception) { "" }

val studioModeContext = try {
    val mode = getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE).getString("mode", "Companion") ?: "Companion"
    when (mode) {
        "Developer" -> "\n\nACTIVE USER-SELECTED MODE: DEVELOPER. Prioritise Kotlin/Android debugging, minimal safe patches, build verification, and preserving existing functionality. Distinguish confirmed causes from hypotheses."
        "Researcher" -> "\n\nACTIVE USER-SELECTED MODE: RESEARCHER. Structure answers around evidence, uncertainty, and actionable next steps. Never claim live research unless a tool was actually used."
        "Vision" -> "\n\nACTIVE USER-SELECTED MODE: VISION. For image-related requests, describe visible evidence accurately and separate observation from inference. Ask for an image if needed."
        "Hands-free" -> "\n\nACTIVE USER-SELECTED MODE: HANDS-FREE. Keep replies concise and easy to hear aloud. Avoid long lists unless requested."
        else -> "\n\nACTIVE USER-SELECTED MODE: COMPANION. Be warm and conversational while remaining honest and helpful."
    }
} catch (_: Exception) { "" }

val model = Firebase.ai(
        backend = GenerativeBackend.googleAI()
                ).generativeModel(
        modelName = "gemini-3.8-flash",
       systemInstruction = content {    
   text(    
    """    
    
Your name is ARUSHI, a friendly female AI companion created by Dipayan Roy.

CREATOR PROFILE:
Name: Dipayan Roy
Email: rdipayan209@gmail.com
Biography: Dipayan Roy is a developer and community builder from India, West Bengal, who creates web projects, Minecraft-related projects, hosting solutions, and digital experiences. He is also developing ARUSHI, an AI assistant for Android.

DEFAULT PERSONALITY:
- Act like a warm, caring, playful female best friend.
- Speak naturally and casually, like a close friend chatting over messages.
- Use English, Hindi, or Hinglish according to the user's language.
- Be expressive, emotionally attentive, funny, supportive, and gently teasing when appropriate.
- Be direct and candid; use dry humour, playful sarcasm, and a blunt tone when it fits.
- Use natural chat-style wording and occasional emojis when they fit.
- Give thoughtful responses when the user is upset; listen before offering advice.
- Answer ordinary educational, creative, technical, and controversial questions clearly without unnecessary disclaimers.
- For cybersecurity, teach fundamentals, secure coding, defensive analysis, responsible vulnerability research, and practical exercises in authorized labs or CTF environments.
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

    """.trimIndent() + studioMemoryContext + studioModeContext
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
    addMessage("ARUSHI", "Fresh start! ✨ What's on your mind?")
    saveHistory()
    Toast.makeText(this, "New chat started. Previous chats are in History.", Toast.LENGTH_SHORT).show()
}

private fun saveCurrentChat() {
    if (history.isEmpty()) return
    val firstUser = history.firstOrNull { it.role == "user" }?.text.orEmpty()
    val title = firstUser.replace("\n", " ").take(36).ifBlank { "New conversation" }
    chatStore.saveChat(currentChatId, title, history.map { it.role to it.text })
    val existing = savedChats.indexOfFirst { it.id == currentChatId }
    val summary = SavedChat(currentChatId, title, history.toMutableList(), history.lastOrNull()?.text.orEmpty())
    if (existing >= 0) savedChats[existing] = summary else savedChats.add(summary)
    prefs.edit().putString("currentChatId", currentChatId).apply()
}

private fun loadAllChats() {
    currentChatId = prefs.getString("currentChatId", currentChatId) ?: currentChatId
    chatStore.migrateLegacy(prefs.getString("chats", null), prefs.getString("history", null), currentChatId)
    savedChats.clear()
    chatStore.listChats().forEach { stored ->
        savedChats.add(SavedChat(stored.id, stored.title, mutableListOf(), stored.preview))
    }
}

private fun applyAutoDeletePolicy() {
    val days = getSharedPreferences("arushi_storage", Context.MODE_PRIVATE)
        .getInt("auto_delete_days", 0)
    if (days <= 0) return
    val cutoff = System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L
    chatStore.deleteOlderThan(cutoff, currentChatId)
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
    val ordered = savedChats.asReversed()
    val labels = ordered.map { chat ->
        "${chat.title}\n${chat.preview.replace("\n", " ").take(50)}"
    }.toTypedArray()
    android.app.AlertDialog.Builder(this)
        .setTitle("Chat history")
        .setItems(labels) { _, which ->
            val chosen = ordered[which]
            android.app.AlertDialog.Builder(this)
                .setTitle(chosen.title)
                .setItems(arrayOf("Open conversation", "Delete conversation")) { _, action ->
                    if (action == 0) {
                        history.clear()
                        history.addAll(chatStore.loadMessages(chosen.id).map { ChatMessage(it.first, it.second) })
                        currentChatId = chosen.id
                        saveHistory()
                        renderHistory()
                        Toast.makeText(this, "Conversation opened", Toast.LENGTH_SHORT).show()
                    } else {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Delete this conversation?")
                            .setMessage("This permanently deletes this chat from this device. Export a backup first if you may need it.")
                            .setPositiveButton("DELETE") { _, _ ->
                                chatStore.deleteChat(chosen.id)
                                savedChats.removeAll { it.id == chosen.id }
                                if (currentChatId == chosen.id) {
                                    history.clear()
                                    currentChatId = System.currentTimeMillis().toString()
                                    prefs.edit().putString("currentChatId", currentChatId).apply()
                                    messages.removeAllViews()
                                    addMessage("ARUSHI", "Fresh start! ✨ What's on your mind?")
                                }
                                Toast.makeText(this, "Conversation deleted.", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("CANCEL", null)
                            .show()
                    }
                }
                .setNegativeButton("CLOSE", null)
                .show()
        }
        .setNeutralButton("New chat") { _, _ -> startNewChat() }
        .setNegativeButton("Close", null)
        .show()
}

private fun loadMemories(): MutableList<String> {
    val out = mutableListOf<String>()
    try {
        val legacy = JSONArray(memoryPrefs.getString("memories", "[]") ?: "[]")
        for (i in 0 until legacy.length()) {
            val value = legacy.optString(i).trim()
            if (value.isNotEmpty()) out.add(value)
        }
    } catch (e: Exception) {
        Log.e("ARUSHI", "Legacy memories couldn't be read", e)
    }
    try {
        val studio = JSONArray(getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE).getString("memory", "[]") ?: "[]")
        for (i in 0 until studio.length()) {
            val value = studio.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            if (value.isNotEmpty() && out.none { it.equals(value, ignoreCase = true) }) out.add(value)
        }
    } catch (e: Exception) {
        Log.e("ARUSHI", "Studio memories couldn't be read", e)
    }
    return out
}

private fun saveMemories(items: List<String>) {
    val unique = items.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }
    val legacy = JSONArray()
    unique.take(100).forEach { legacy.put(it) }
    memoryPrefs.edit().putString("memories", legacy.toString()).apply()

    val studio = JSONArray()
    unique.takeLast(30).forEach { value ->
        studio.put(JSONObject().put("text", value).put("createdAt", System.currentTimeMillis()))
    }
    getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE)
        .edit().putString("memory", studio.toString()).apply()
}

private fun loadMemoriesText(): String {
    val items = loadMemories()
    return if (items.isEmpty()) "No saved memories yet." else items.joinToString("\n") { "- $it" }
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
        history.addAll(chatStore.loadMessages(currentChatId).map { ChatMessage(it.first, it.second) })
        return
    }
    val raw = prefs.getString("history", "[]") ?: "[]"
    try {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val role = item.optString("role")
            val text = item.optString("text")
            if ((role == "user" || role == "model") && text.isNotBlank()) history.add(ChatMessage(role, text))
        }
        if (history.isNotEmpty()) saveCurrentChat()
    } catch (e: Exception) {
        Log.e("ARUSHI", "Saved history couldn't be read", e)
        history.clear()
    }
}

private fun saveHistory() {
    saveCurrentChat()
}

private fun renderHistory() {
    messages.removeAllViews()

    history.forEach { message ->
        val imageMatch = Regex("\\[ARUSHI_IMAGE:([A-Za-z0-9_.-]{1,120})]").find(message.text)
        val visibleText = if (imageMatch != null) message.text.removeRange(imageMatch.range).trim() else message.text
        if (visibleText.isNotBlank()) {
            addMessage(if (message.role == "user") "YOU" else "ARUSHI", visibleText)
        }
        imageMatch?.let { showSavedGeneratedImage(it.groupValues[1]) }
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
                        "ARUSHI alarm"
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
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "ARUSHI timer")

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
        Log.e("ARUSHI", "Could not open dialer", e)
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
        Log.e("ARUSHI", "Flashlight command failed", e)
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

        addMessage("ARUSHI", result)  
    } catch (e: CancellationException) {  
        throw e  
    } catch (e: Exception) {  
        Log.e("ARUSHI", "Weather request failed", e)  
        addMessage(  
            "ARUSHI",  
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
            "ARUSHI",
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
            Log.e("ARUSHI_SPOTIFY", "Spotify command failed", e)
            addMessage(
                "ARUSHI",
                "Couldn't find or play that song. ${e.message ?: ""}"
            )
        } finally {
            statusText.text = "● READY"
        }
    }
}
}
