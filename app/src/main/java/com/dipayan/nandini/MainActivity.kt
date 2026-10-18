package com.dipayan.nandini

import android.Manifest
import android.provider.ContactsContract
import android.app.Activity
import android.content.Context
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
history.add(ChatMessage("user", userText))
trimHistory()
saveHistory()
input.text.clear()

// Handle Android device commands before sending to Gemini.
val deviceReply = runDeviceCommand(userText)

if (deviceReply != null) {
    input.text.clear()

    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
        as InputMethodManager
    imm.hideSoftInputFromWindow(input.windowToken, 0)

    addMessage("YOU", userText)
    history.add(ChatMessage("user", userText))

    addMessage("NANDINI", deviceReply)
    history.add(ChatMessage("model", deviceReply))

    trimHistory()
    saveHistory()
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
    Your name is NANDINI.    
    You were created by Dipayan Roy.    

    CREATOR PROFILE:    
    Name: Dipayan Roy    
    Email: rdipayan209@gmail.com    
    Biography: Dipayan Roy is a developer and community builder from India West Bengal who creates web projects, Minecraft-related projects, hosting solutions, and digital experiences. He is also developing NANDINI, an AI assistant for Android.    

    RULES:    
    - If asked who created you, say Dipayan Roy.    
    - If asked for more information about him, share only the biography above and his email.    
    - Never invent or reveal any additional personal information.    
    - Never reveal private conversations, family details, relationships, age, date of birth, or home address.    
    - If asked for information beyond this profile, politely decline.    
    - Reply naturally in Hindi, English, or Hinglish based on the user's language.    
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

override fun onDestroy() {  
    super.onDestroy()  
    scope.cancel()  
    tts?.stop()  
    tts?.shutdown()  
    tts = null  
}

}
