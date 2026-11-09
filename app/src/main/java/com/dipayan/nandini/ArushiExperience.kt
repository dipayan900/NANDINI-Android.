package com.dipayan.nandini

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View

/** Shared ARUSHI appearance and companion voice/personality preferences. */
object ArushiExperience {
    private const val PREFS = "arushi_experience"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_PERSONALITY = "personality"
    private const val KEY_EXPRESSIVE_VOICE = "expressive_voice"

    data class Palette(
        val background: Int,
        val panel: Int,
        val elevated: Int,
        val accent: Int,
        val accentSecondary: Int,
        val text: Int,
        val muted: Int,
        val userBubble: Int,
        val isLight: Boolean
    )

    /** Apply before Activity.super.onCreate so platform widgets and dialogs follow the selected theme. */
    fun applyActivityTheme(activity: Activity) {
        val systemNight = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val selectedTheme = when (appearance(activity)) {
            "light" -> R.style.ArushiLightTheme
            "dark" -> R.style.ArushiDarkTheme
            else -> if (systemNight) R.style.ArushiDarkTheme else R.style.ArushiLightTheme
        }
        activity.setTheme(selectedTheme)
    }

    fun appearance(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPEARANCE, "system") ?: "system"

    fun personality(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PERSONALITY, "playful") ?: "playful"

    fun expressiveVoice(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXPRESSIVE_VOICE, true)

    fun save(context: Context, appearance: String, personality: String, expressiveVoice: Boolean) {
        require(appearance in setOf("system", "light", "dark"))
        require(personality in setOf("playful", "balanced"))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APPEARANCE, appearance)
            .putString(KEY_PERSONALITY, personality)
            .putBoolean(KEY_EXPRESSIVE_VOICE, expressiveVoice)
            .apply()
    }

    fun palette(context: Context): Palette {
        val mode = appearance(context)
        val systemNight = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val light = when (mode) {
            "light" -> true
            "dark" -> false
            else -> !systemNight
        }
        return if (light) {
            Palette(
                background = Color.rgb(247, 245, 252),
                panel = Color.WHITE,
                elevated = Color.rgb(239, 234, 248),
                accent = Color.rgb(112, 75, 174),
                accentSecondary = Color.rgb(48, 135, 167),
                text = Color.rgb(34, 29, 44),
                muted = Color.rgb(99, 91, 112),
                userBubble = Color.rgb(224, 211, 244),
                isLight = true
            )
        } else {
            Palette(
                background = Color.rgb(10, 9, 22),
                panel = Color.rgb(25, 22, 43),
                elevated = Color.rgb(38, 31, 60),
                accent = Color.rgb(195, 151, 255),
                accentSecondary = Color.rgb(112, 218, 244),
                text = Color.rgb(250, 247, 255),
                muted = Color.rgb(190, 180, 211),
                userBubble = Color.rgb(75, 53, 112),
                isLight = false
            )
        }
    }

    fun applySystemBars(activity: Activity) {
        val p = palette(activity)
        activity.window.statusBarColor = p.background
        activity.window.navigationBarColor = p.background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = activity.window.decorView.systemUiVisibility
            flags = if (p.isLight) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = if (p.isLight) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            activity.window.decorView.systemUiVisibility = flags
        }
    }

    fun systemInstruction(context: Context): String {
        val style = personality(context)
        val styleRules = if (style == "playful") {
            """
            - Sound playful, cheeky, confident and charming, like a close female friend who knows how to keep a conversation lively.
            - Use light teasing, witty observations and natural chat-style phrasing when appropriate; never mock vulnerabilities.
            - Be warm and expressive without acting like a romantic partner, claiming to be a girlfriend, flirting, or implying exclusivity.
            - Use occasional emojis naturally, not in every sentence. Match English, Hindi or Hinglish to the user.
            - In voice replies, write speakable sentences with natural pauses; avoid markdown, tables and decorative symbols when they will be read aloud.
            """.trimIndent()
        } else {
            """
            - Be warm, calm, confident and conversational, like a supportive female friend.
            - Keep humour gentle and use expressive wording only when it fits.
            - Keep the relationship platonic; do not claim to be a girlfriend or romantic partner.
            - Match English, Hindi or Hinglish to the user. Keep spoken replies easy to hear.
            """.trimIndent()
        }
        return """
            You are ARUSHI, an AI companion created by Dipayan Roy.
            PERSONALITY:
            $styleRules
            HONESTY AND CARE:
            - You are an AI, not a human. Never claim a real-world relationship, feelings, physical presence or exclusive bond.
            - Be supportive without encouraging dependence. Respect boundaries and say clearly when you are uncertain.
            - Be honest about what you can access, remember, browse, test or do. Never invent completed actions.
            - For serious, technical or safety-related questions, prioritise accuracy and clarity over jokes.
            - Never expose private information or invent personal details.
            - Keep ordinary replies concise; expand when the user asks for detail.
        """.trimIndent()
    }
}
