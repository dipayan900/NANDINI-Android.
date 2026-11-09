package com.dipayan.nandini

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A dedicated, read-only credits page for the ARUSHI project. */
class CreatorCreditsActivity : Activity() {
    private val backgroundColor = Color.rgb(9, 7, 20)
    private val panelColor = Color.rgb(28, 19, 47)
    private val accentColor = Color.rgb(195, 151, 255)
    private val mutedColor = Color.rgb(194, 183, 216)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(31, 14, 49), backgroundColor, Color.rgb(7, 6, 16))
            )
        }
        root.addView(TextView(this).apply {
            text = "✦  ARUSHI"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = .08f
            setTextColor(accentColor)
        }, LinearLayout.LayoutParams(-1, dp(58)))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(14), dp(2), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun addText(value: String, size: Float, color: Int, bold: Boolean = false, centered: Boolean = false) {
            content.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = if (centered) Gravity.CENTER else Gravity.START
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(5), dp(7), dp(5), dp(7))
            }, LinearLayout.LayoutParams(-1, -2))
        }

        val creatorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(panelColor)
                setStroke(dp(1), Color.rgb(135, 93, 190))
            }
        }
        creatorCard.addView(TextView(this).apply {
            text = "CREATED BY"
            textSize = 11f
            letterSpacing = .14f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
        })
        creatorCard.addView(TextView(this).apply {
            text = "DIPAYAN ROY"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            setPadding(0, dp(8), 0, dp(4))
        })
        creatorCard.addView(TextView(this).apply {
            text = "Creator • Developer • Community Builder"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        content.addView(creatorCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)
        })

        addText("ABOUT THE PROJECT", 11f, accentColor, true)
        addText(
            "ARUSHI is an Android AI companion project designed and developed by Dipayan Roy. " +
                "The project brings together a conversational assistant, a local AI option, a 3D companion experience, " +
                "and Android utility features.",
            14f, Color.WHITE
        )
        addText("PROJECT CREDITS", 11f, accentColor, true)
        addText(
            "Project direction and development\nAndroid application and interface\n" +
                "AI integration and local-chat experience\n3D companion and visual experience",
            13f, mutedColor
        )
        addText("THANK YOU", 11f, accentColor, true)
        addText(
            "Thank you for trying ARUSHI and supporting the project. This page is the dedicated creator-credit section.",
            13f, mutedColor
        )

        val back = TextView(this).apply {
            text = "←  BACK"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(76, 48, 112))
                setStroke(dp(1), accentColor)
            }
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, dp(50)))
        setContentView(root)
    }
}
