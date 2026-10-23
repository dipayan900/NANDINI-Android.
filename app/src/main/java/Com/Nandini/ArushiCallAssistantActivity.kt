package com.dipayan.nandini

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import android.telecom.Call
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * ARUSHI's user-visible Telecom controls. Live AI audio is intentionally not claimed here:
 * Android/OEMs may restrict cellular call uplink/downlink audio to privileged components.
 */
class ArushiCallAssistantActivity : Activity() {
    // Screen-local palette follows Android's current system light/dark setting.
    private var bg = Color.rgb(10, 12, 24)
    private var panel = Color.rgb(22, 25, 44)
    private var accent = Color.rgb(184, 142, 255)
    private var textPrimary = Color.WHITE
    private var textSecondary = Color.LTGRAY
    private var warning = Color.rgb(255, 205, 130)
    private lateinit var status: TextView
    private lateinit var callInfo: TextView
    private lateinit var answerButton: Button
    private lateinit var rejectButton: Button
    private lateinit var endButton: Button
    private lateinit var voiceStatus: TextView
    private lateinit var transcriptView: TextView
    private lateinit var voiceButton: Button
    private var dialNumber: String? = null
    private val requestAudioPermission = 7344

    companion object {
        const val EXTRA_LAUNCHED_FOR_CALL = "arushi_launched_for_call"
        private const val REQUEST_DIALER_ROLE = 7301
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemThemePalette()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = if (isLightTheme()) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
        if (intent?.action == Intent.ACTION_DIAL) {
            dialNumber = intent.data?.schemeSpecificPart
        }
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        ArushiCallSession.setListener { runOnUiThread { refreshCallUi() } }
        refreshStatus()
        refreshCallUi()
        refreshVoiceUi()
    }

    override fun onPause() {
        ArushiCallSession.setListener(null)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_DIAL) dialNumber = intent.data?.schemeSpecificPart
        refreshCallUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "ARUSHI"
            textSize = 14f
            letterSpacing = 0.28f
            setTextColor(accent)
        })
        root.addView(TextView(this).apply {
            text = "AI CALL ASSISTANT"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Android Telecom integration • User-controlled calling"
            textSize = 14f
            setTextColor(textSecondary)
        })
        root.addView(card(
            "PRIVACY FIRST",
            "ARUSHI must identify itself as an AI before conversation transcription or recording. This build does not secretly record calls. Live two-way SIM-call audio is not guaranteed by Android and depends on device/OEM support."
        ).also { it.layoutParams = marginParams(top = 14, bottom = 10) })

        status = makeText(15f, textPrimary).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(panel)
        }
        root.addView(status, marginParams(bottom = 10))
        root.addView(button("Request ARUSHI as default phone app") { requestDialerRole() })
        root.addView(button("Refresh Android call status") { refreshStatus(); refreshCallUi() })

        root.addView(TextView(this).apply {
            text = "CURRENT CALL CONTROLS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, dp(20), 0, dp(8))
        })
        callInfo = makeText(15f, textPrimary).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(panel)
        }
        root.addView(callInfo, marginParams(bottom = 8))
        answerButton = button("Answer call") { ArushiCallSession.answer() }
        rejectButton = button("Reject incoming call") { ArushiCallSession.reject() }
        endButton = button("End call") { ArushiCallSession.end() }
        root.addView(answerButton)
        root.addView(rejectButton)
        root.addView(endButton)

        root.addView(TextView(this).apply {
            text = "FREE AI CALL ASSISTANT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, dp(22), 0, dp(8))
        })
        root.addView(card(
            "HOW IT WORKS",
            "When enabled, ARUSHI can auto-answer, introduce itself as an AI, disclose that it may transcribe speech, ask for the caller's name, then request a message. It uses Android speech recognition and text-to-speech without a paid AI API. Android or vivo may block cellular call audio; this must be tested with a trusted caller. Speech recognition may use the device's configured recognition provider."
        ).also { it.layoutParams = marginParams(bottom = 10) })
        voiceButton = button("Enable free AI auto-answer") { toggleVoiceAssistant() }
        root.addView(voiceButton)
        voiceStatus = makeText(14f, textPrimary).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(panel)
        }
        root.addView(voiceStatus, marginParams(top = 6, bottom = 8))
        root.addView(TextView(this).apply {
            text = "LAST CALL TRANSCRIPT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, dp(12), 0, dp(8))
        })
        transcriptView = makeText(14f, textSecondary).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(panel)
        }
        root.addView(transcriptView)

        root.addView(TextView(this).apply {
            text = "WHAT THIS BUILD CAN DO: request the Android default-dialer role and expose Telecom call answer/reject/end controls when Android routes a call to this service. WHAT IT CANNOT PROMISE: bypass OS/OEM restrictions, hear both sides of cellular audio, automatically conduct an AI conversation, or generate an audio-based summary without a supported audio/transcription path."
            textSize = 13f
            setTextColor(warning)
            setPadding(0, dp(20), 0, dp(8))
        })
        setContentView(scroll)
        refreshStatus()
        refreshCallUi()
        refreshVoiceUi()
    }

    private fun toggleVoiceAssistant() {
        if (ArushiVoiceCallAssistant.isEnabled(this)) {
            ArushiVoiceCallAssistant.setEnabled(this, false)
            refreshVoiceUi()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), requestAudioPermission)
            return
        }
        ArushiVoiceCallAssistant.setEnabled(this, true)
        refreshVoiceUi()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudioPermission) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ArushiVoiceCallAssistant.setEnabled(this, true)
            } else {
                Toast.makeText(this, "Microphone permission is needed for the optional voice assistant.", Toast.LENGTH_LONG).show()
            }
            refreshVoiceUi()
        }
    }

    private fun refreshVoiceUi() {
        val enabled = ArushiVoiceCallAssistant.isEnabled(this)
        voiceButton.text = if (enabled) "Disable free AI auto-answer" else "Enable free AI auto-answer"
        voiceStatus.text = "Automation: ${if (enabled) "ENABLED" else "DISABLED"}\\n\\n${ArushiVoiceCallAssistant.status(this)}"
        transcriptView.text = ArushiVoiceCallAssistant.lastTranscript(this)
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                Toast.makeText(this, "Android does not offer the default dialer role on this build.", Toast.LENGTH_LONG).show()
                return
            }
            if (roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
                Toast.makeText(this, "ARUSHI is already the default phone app.", Toast.LENGTH_LONG).show()
                refreshStatus()
                return
            }
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
        } else {
            Toast.makeText(this, "This Android version needs its supported default-phone-app settings flow.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Role result callback retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DIALER_ROLE) {
            refreshStatus()
            Toast.makeText(this, if (resultCode == RESULT_OK) "Default phone-app role granted." else "Default phone-app role was not granted.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatus() {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            getSystemService(Context.ROLE_SERVICE) as RoleManager else null
        val available = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        val held = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        status.text = when {
            held -> "Default phone-app role: GRANTED\n\nAndroid has selected ARUSHI as the dialer. This does not unlock restricted cellular audio; test ordinary calls carefully and switch back to your previous phone app if anything behaves unexpectedly."
            available -> "Default phone-app role: AVAILABLE, NOT GRANTED\n\nTap the request button and approve Android's system prompt if you want ARUSHI to handle calls. You can decline and keep your current phone app."
            else -> "Default phone-app role: NOT AVAILABLE / VERSION UNSUPPORTED\n\nThis device/build has not exposed the supported role request."
        }
    }

    private fun refreshCallUi() {
        val call = ArushiCallSession.currentCall()
        if (call == null) {
            callInfo.text = "No call is currently routed to ARUSHI. After role approval, Android must route a test call to the InCallService before these controls can act."
            answerButton.isEnabled = false
            rejectButton.isEnabled = false
            endButton.isEnabled = false
            return
        }
        val stateText = when (call.state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "INCOMING — RINGING"
            Call.STATE_ACTIVE -> "CONNECTED"
            Call.STATE_HOLDING -> "ON HOLD"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT PHONE ACCOUNT"
            else -> "CALL STATE ${call.state}"
        }
        val handle = call.details?.handle
        val number = handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: "Caller ID unavailable"
        callInfo.text = "$stateText\n$number\n\nAI voice/audio status: not connected in this build."
        val ringing = call.state == Call.STATE_RINGING
        val live = call.state != Call.STATE_DISCONNECTED
        answerButton.isEnabled = ringing
        rejectButton.isEnabled = ringing
        endButton.isEnabled = live && !ringing
    }

    private fun isLightTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO

    private fun applySystemThemePalette() {
        if (isLightTheme()) {
            bg = Color.rgb(247, 245, 252)
            panel = Color.rgb(255, 255, 255)
            accent = Color.rgb(104, 69, 168)
            textPrimary = Color.rgb(28, 24, 38)
            textSecondary = Color.rgb(83, 77, 96)
            warning = Color.rgb(135, 79, 10)
        } else {
            bg = Color.rgb(10, 12, 24)
            panel = Color.rgb(22, 25, 44)
            accent = Color.rgb(184, 142, 255)
            textPrimary = Color.WHITE
            textSecondary = Color.LTGRAY
            warning = Color.rgb(255, 205, 130)
        }
    }

    private fun card(title: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
        addView(makeText(13f, accent).apply {
            text = title
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(makeText(14f, textSecondary).apply {
            text = body
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun makeText(size: Float, color: Int) = TextView(this).apply {
        textSize = size
        setTextColor(color)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(textPrimary)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(68, 53, 110)))
        setOnClickListener { action() }
        layoutParams = marginParams(top = 8, bottom = 4)
    }

    private fun marginParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
