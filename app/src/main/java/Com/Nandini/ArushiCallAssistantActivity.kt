package Com.Nandini

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * ARUSHI Call Assistant setup and compatibility screen.
 *
 * Android does not expose cellular-call audio to ordinary apps merely by granting
 * microphone permission. This screen reports that limitation instead of pretending
 * the AI can hear both sides or auto-answer calls.
 */
class ArushiCallAssistantActivity : Activity() {
    private val bg = Color.rgb(10, 12, 24)
    private val panel = Color.rgb(22, 25, 44)
    private val accent = Color.rgb(184, 142, 255)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
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
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Automatic call answering • Caller handover • Call summary"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        })
        root.addView(card(
            "PRIVACY FIRST",
            "When live AI call handling is supported, ARUSHI must identify itself as an AI assistant and tell the caller before any transcription or recording. Do not secretly record calls."
        ).also { it.layoutParams = marginParams(top = 14, bottom = 10) })

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(panel)
        }
        root.addView(status, marginParams(bottom = 10))
        refreshStatus()

        root.addView(button("Check Android call-assistant support") { refreshStatus() })
        root.addView(button("Check default phone-app role") {
            val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(Context.ROLE_SERVICE) as RoleManager else null
            val message = if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                "Android supports a default-dialer role, but ARUSHI is not yet a complete dialer. Cellular audio access is still unverified."
            } else {
                "This Android build does not report the default dialer role as available."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        })
        root.addView(TextView(this).apply {
            text = "IMPORTANT: A successful APK build cannot prove that vivo's dialer exposes both sides of SIM-call audio. This setup screen does not claim to auto-answer or conduct live AI conversations. That requires a supported call-audio path and a real-device test."
            textSize = 13f
            setTextColor(Color.rgb(255, 205, 130))
            setPadding(0, dp(18), 0, dp(8))
        })
        setContentView(scroll)
    }

    private fun refreshStatus() {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(Context.ROLE_SERVICE) as RoleManager else null
        val supported = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        status.text = if (supported) {
            "Android dialer role: AVAILABLE\n\nARUSHI is not confirmed as the default phone app. A complete, user-approved calling interface is required. Cellular call audio access still needs separate device testing."
        } else {
            "Android dialer role: NOT REPORTED AVAILABLE\n\nAutomatic SIM-call handling cannot be enabled through the standard dialer role on this device/build."
        }
    }

    private fun card(title: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
        addView(TextView(this@ArushiCallAssistantActivity).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        })
        addView(TextView(this@ArushiCallAssistantActivity).apply {
            text = body
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
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
