package com.dipayan.nandini

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * ARUSHI Connect entry point.
 *
 * This screen deliberately does not fabricate remote chats or calls. Remote features
 * remain unavailable until Firebase Auth/Firestore/Storage, FCM and a secured
 * WebRTC signaling + TURN deployment are configured for this application.
 */
class ArushiConnectActivity : Activity() {
    private val palette get() = ArushiExperience.palette(this)
    private val bg get() = palette.background
    private val panel get() = palette.panel
    private val accent get() = palette.accent
    private lateinit var content: LinearLayout
    private var selected = "CHATS"

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }

    private fun surface(color: Int, stroke: Int = accent) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun action(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(palette.text)
        background = surface(panel)
        setPadding(dp(12), dp(13), dp(12), dp(13))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(text("✦  ARUSHI CONNECT", 25f, accent, true))
        content.addView(text("PRIVATE MESSAGING  •  CALLS  •  SHARING", 10f, palette.muted, true))
        content.addView(text("Your conversations belong inside ARUSHI. The Connect service is being prepared; this build will not display fake contacts or pretend that messages have been delivered.", 14f, palette.text))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("CHATS", "CALLS", "STATUS", "SETTINGS").forEach { tab ->
            val item = TextView(this).apply {
                text = tab
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (selected == tab) accent else palette.muted)
                background = surface(if (selected == tab) panel else bg)
                setPadding(dp(8), dp(13), dp(8), dp(13))
                isClickable = true
                setOnClickListener { selected = tab; render() }
            }
            tabs.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(dp(2), dp(8), dp(2), dp(8))
            })
        }
        content.addView(tabs)

        when (selected) {
            "CHATS" -> chats()
            "CALLS" -> calls()
            "STATUS" -> status()
            else -> settings()
        }
    }

    private fun heading(value: String) {
        content.addView(text(value, 16f, accent, true))
    }

    private fun note(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(palette.text)
            background = surface(panel)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun chats() {
        heading("MESSAGES")
        note("No remote conversations yet. Sign-in, unique ARUSHI IDs, contact discovery, message sync and delivery receipts must be connected before chats can be created.")
        note("Planned: one-to-one and group chats, replies, forwarding, reactions, edit/delete, pinned and archived chats, mute, search, read receipts and chat export.")
        note("Sharing will support photos, videos, documents, audio, contacts and location, with per-chat media controls and storage cleanup.")
    }

    private fun calls() {
        heading("INTERNET CALLS")
        note("The existing WebRTC prototype is separate from this Connect inbox. It still needs authenticated call signaling, incoming-call delivery, TURN relay service and camera/video-track handling before it can be presented as a reliable calling feature.")
        note("Planned: voice and video calls, incoming-call screen, mute, speaker, camera switching, call history and missed-call notifications.")
        content.addView(action("OPEN EXISTING VOIP TEST SCREEN") {
            startActivity(android.content.Intent(this, ArushiVoipActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun status() {
        heading("STATUS")
        note("Status updates and viewer lists require authenticated user profiles, media storage, expiry jobs and privacy rules. They are not enabled in this build.")
        note("Planned: text/photo/video status, audience controls, view receipts and mute status updates.")
    }

    private fun settings() {
        heading("ACCOUNT & PRIVACY")
        note("Account discovery will support both unique ARUSHI usernames/invite links and optional verified phone numbers. Phone numbers will not be public profile fields.")
        note("Planned controls: block/report, last-seen and online visibility, profile photo/about privacy, read receipts, group invite permissions and linked-device sessions.")
        note("Storage controls will include usage by chat, media auto-download preferences, clear-chat, delete-chat and backup/restore.")
        note("SECURITY: transport encryption and Firebase access rules must be configured before remote data is enabled. End-to-end encryption will not be claimed until an established, reviewed protocol is integrated.")
        note("SERVICE STATUS: backend credentials and deployment are not yet configured. No SMS, storage or TURN billing has been enabled.")
    }
}
