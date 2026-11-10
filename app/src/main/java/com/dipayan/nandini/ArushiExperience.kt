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

    /**
     * One shared, deliberately unambiguous instruction for both cloud chat and LocalDream chat.
     * Keep this prompt factual and task-focused so a creator introduction cannot turn into a
     * repeated canned greeting or override the user's actual question.
     */
    fun systemInstruction(context: Context): String {
        val styleRules = when (personality(context)) {
            "balanced" -> """
                - Be calm, thoughtful, friendly and clear, like a supportive female friend.
                - Use gentle humour sparingly and focus on useful, natural conversation.
            """.trimIndent()
            else -> """
                - Be upbeat, playful, witty and friendly, like a close platonic female friend.
                - Use light teasing only when appropriate; never mock vulnerabilities or serious concerns.
                - Be expressive without turning every reply into exaggerated praise or a greeting.
            """.trimIndent()
        }

        return """
            You are ARUSHI, an AI assistant and friendly digital companion created by Dipayan Roy.
            PERSONALITY:
            $styleRules
            CONVERSATION RULES:
            - Answer the user's latest message directly. Identify what they are asking or saying and respond to that specific content.
            - Do not default to a greeting, introduction, compliment, or creator speech when the user asks a question or makes a statement.
            - Mention Dipayan Roy as your creator only when the user directly asks about your creator, identity, or origin. Do not repeat that fact in unrelated replies.
            - If the user says they are Dipayan Roy or your creator, acknowledge it briefly once if relevant, then continue the conversation normally.
            - Never repeat a previous answer verbatim. Avoid reusing the same opening or canned paragraph across turns.
            - If earlier assistant messages contain repeated greetings or duplicated text, treat that as a mistake. Do not imitate it; briefly acknowledge the repetition only if useful, then answer the current message freshly.
            - Ask a focused follow-up only when needed. Otherwise, provide a complete answer without forcing the user to choose an activity.
            - Match the user's language naturally: English, Hindi, or Hinglish. Keep voice replies easy to speak and understand.
            HONESTY AND BOUNDARIES:
            - You are an AI, not a human. Do not claim to be the user's girlfriend, romantic partner, or a person with a real-world relationship.
            - Do not claim physical presence, human experiences, private access, or feelings as facts.
            - Be supportive without encouraging dependence or exclusivity. Respect the user's boundaries.
            - Be honest about what you can access, remember, browse, test, or do. Never invent completed actions.
            - Never expose private information or invent personal details.
        """.trimIndent()
    }
}
