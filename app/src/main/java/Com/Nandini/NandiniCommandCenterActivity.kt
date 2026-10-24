package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext

/** Local-first command centre. Network/cloud AI is not required for notes and basic calculations. */
class NandiniCommandCenterActivity : Activity() {
    private val bg = Color.rgb(5, 7, 20)
    private val panel = Color.rgb(14, 18, 38)
    private val accent = Color.rgb(87, 223, 255)
    private val pink = Color.rgb(255, 135, 202)
    private val ink = Color.rgb(248, 243, 255)
    private val muted = Color.rgb(190, 198, 226)
    private val prefs by lazy { getSharedPreferences("nandini_command_center", Context.MODE_PRIVATE) }
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(20, 10, 38), bg, Color.rgb(5, 15, 24))
            )
        }
        root.addView(TextView(this).apply {
            text = "A R U S H I"; textSize = 24f; gravity = Gravity.CENTER
            letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(TextView(this).apply {
            text = "COMMAND NEXUS  •  LOCAL-FIRST SYSTEMS"; textSize = 10f
            gravity = Gravity.CENTER; letterSpacing = .07f; setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(10)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(cardButton("← RETURN TO STUDIO") { finish() }, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        render()
    }

    private fun render() {
        content.removeAllViews()
        section("LOCAL INTELLIGENCE", "Works without cloud AI. Your notes and command history remain in this app's private storage.")
        cardButton("⌘  OFFLINE CALCULATOR", "Basic arithmetic with parentheses, + − × ÷ and decimals.") { calculator() }
        cardButton("▤  QUICK NOTES", "Create, read, copy or delete local notes.") { notes() }
        cardButton("⏱  PROACTIVE REMINDER", "Schedule an Android alarm/reminder; Android may ask you to confirm.") { reminder() }
        cardButton("◈  FLOATING ASSISTANT", "Open overlay permission controls. The floating companion must be enabled by you.") { overlaySettings() }
        cardButton("▣  SCREEN UNDERSTANDING", "Open Accessibility settings to review and manually enable ARUSHI's screen helper.") { screenSettings() }
        cardButton("⌁  ANDROID ACTION CENTER", "Open supported system settings and app controls.") { actions() }
        cardButton("◉  LIVE DIAGNOSTICS", "Check battery, RAM availability, app permissions and network status.") { diagnostics() }
        section("PRIVACY BY DESIGN", "ARUSHI does not silently click through other apps. Screen-reading access is sensitive: enable it only if you understand and want that capability. Cloud AI and live web lookup still need internet.")
    }

    private fun section(title: String, subtitle: String) {
        content.addView(TextView(this).apply {
            text = title; textSize = 11f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(pink); setPadding(dp(3), dp(12), dp(3), dp(5))
        })
        content.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(muted); setPadding(dp(3), 0, dp(3), dp(10))
        })
    }

    private fun cardButton(title: String, subtitle: String? = null, action: () -> Unit): LinearLayout {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(13), dp(15), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat(); setColor(panel); setStroke(dp(1), Color.argb(195, 55, 196, 238))
            }
            isClickable = true; isFocusable = true
            elevation = dp(3).toFloat()
            setOnClickListener {
                animate().cancel()
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(80L).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                }.start()
                action()
            }
        }
        view.addView(TextView(this).apply { text = title; textSize = 14f; letterSpacing = .015f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        if (subtitle != null) view.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(muted); setPadding(0, dp(5), 0, 0)
        })
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        view.alpha = 0f
        view.translationY = dp(8).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(260L)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        return view
    }

    private fun calculator() {
        val input = EditText(this).apply {
            hint = "Example: (12 + 8) / 4"; setTextColor(ink); setHintTextColor(muted)
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("⌘ Offline Calculator").setMessage("Runs locally. Supports +, −, ×, ÷, parentheses and decimals.")
            .setView(input).setPositiveButton("CALCULATE") { _, _ ->
                val result = try { ExpressionParser(input.text.toString()).parse().stripTrailingZeros().toPlainString() }
                catch (_: Exception) { "Invalid expression. Use numbers and + - * / ( )." }
                AlertDialog.Builder(this).setTitle("RESULT").setMessage(result)
                    .setPositiveButton("COPY") { _, _ -> copy(result) }
                    .setNegativeButton("CLOSE", null).show()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun notes() {
        val arr = readNotes()
        val items = Array(arr.length()) { i -> arr.optJSONObject(i)?.optString("title", "Note") ?: "Note" }
        AlertDialog.Builder(this).setTitle("▤ Local Notes")
            .setItems(items) { _, index -> showNote(arr.optJSONObject(index)) }
            .setPositiveButton("＋ NEW NOTE") { _, _ -> addNote() }
            .setNeutralButton("CLOSE", null).show()
    }

    private fun addNote() {
        val title = EditText(this).apply { hint = "Note title"; setTextColor(ink); setHintTextColor(muted) }
        val body = EditText(this).apply { hint = "Write a note to keep on this device"; setTextColor(ink); setHintTextColor(muted); minLines = 3; gravity = Gravity.TOP }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(6), dp(16), dp(6)); addView(title); addView(body) }
        AlertDialog.Builder(this).setTitle("Save locally").setView(wrap).setPositiveButton("SAVE") { _, _ ->
            val t = title.text.toString().trim()
            val b = body.text.toString().trim()
            if (t.isNotEmpty() && b.isNotEmpty()) {
                val arr = readNotes()
                arr.put(JSONObject().put("title", t.take(100)).put("body", b.take(10000)).put("created", System.currentTimeMillis()))
                while (arr.length() > 100) arr.remove(0)
                prefs.edit().putString("notes", arr.toString()).apply()
                Toast.makeText(this, "Saved on this device.", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("CANCEL", null).show()
    }

    private fun showNote(note: JSONObject?) {
        if (note == null) return
        AlertDialog.Builder(this).setTitle(note.optString("title")).setMessage(note.optString("body"))
            .setPositiveButton("COPY") { _, _ -> copy(note.optString("body")) }
            .setNeutralButton("DELETE") { _, _ ->
                val arr = readNotes(); val rebuilt = JSONArray()
                for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optLong("created") != note.optLong("created")) rebuilt.put(arr.optJSONObject(i))
                prefs.edit().putString("notes", rebuilt.toString()).apply()
                Toast.makeText(this, "Note deleted.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("CLOSE", null).show()
    }

    private fun reminder() {
        val title = EditText(this).apply { hint = "Reminder title"; setTextColor(ink); setHintTextColor(muted) }
        val hour = EditText(this).apply { hint = "Hour (0–23)"; inputType = 2; setTextColor(ink); setHintTextColor(muted) }
        val minute = EditText(this).apply { hint = "Minute (0–59)"; inputType = 2; setTextColor(ink); setHintTextColor(muted) }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(4)); addView(title); addView(hour); addView(minute) }
        AlertDialog.Builder(this).setTitle("⏱ Set a reminder").setMessage("Creates an Android alarm for today. Choose a future time.")
            .setView(wrap).setPositiveButton("OPEN ALARM") { _, _ ->
                try {
                    val h = hour.text.toString().toInt(); val m = minute.text.toString().toInt()
                    require(h in 0..23 && m in 0..59)
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_MESSAGE, title.text.toString().ifBlank { "ARUSHI reminder" })
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Enter a valid hour and minute, and make sure an alarm app is available.", Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun overlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Floating permission is enabled")
                .setMessage("Use the existing Companion control in ARUSHI to start or stop the floating assistant. Turn off permission here any time.")
                .setPositiveButton("OVERLAY SETTINGS") { _, _ -> openOverlaySettings() }
                .setNegativeButton("CLOSE", null).show()
        } else AlertDialog.Builder(this).setTitle("Enable floating assistant?")
            .setMessage("Android requires you to grant 'Display over other apps' permission manually. This does not start the overlay by itself.")
            .setPositiveButton("OPEN SETTINGS") { _, _ -> openOverlaySettings() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun openOverlaySettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { openAppSettings() }
    }

    private fun screenSettings() {
        AlertDialog.Builder(this).setTitle("Screen Understanding")
            .setMessage("Android Accessibility access can expose on-screen text. Enable ARUSHI only if you want this capability; keep it off otherwise. You can disable it again in Accessibility settings.")
            .setPositiveButton("ACCESSIBILITY SETTINGS") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                catch (_: Exception) { openAppSettings() }
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun actions() {
        val choices = arrayOf("Wi-Fi / network settings", "Sound settings", "Display settings", "App permissions")
        AlertDialog.Builder(this).setTitle("⌁ Android Action Center").setItems(choices) { _, which ->
            try {
                val intent = when (which) {
                    0 -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    1 -> Intent(Settings.ACTION_SOUND_SETTINGS)
                    2 -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                }
                startActivity(intent)
            } catch (_: Exception) { Toast.makeText(this, "This settings screen isn't available on this device.", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun diagnostics() {
        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryText = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Unavailable"
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mem = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mem)
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val connected = if (android.os.Build.VERSION.SDK_INT >= 23) cm.activeNetwork?.let { cm.getNetworkCapabilities(it) != null } == true else true
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = runCatching {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        }.getOrDefault(false)
        val report = "BATTERY: $batteryText\nNETWORK: ${if (connected) "Connected" else "Offline"}\nAVAILABLE RAM: ${mem.availMem / (1024 * 1024)} MB\nOVERLAY PERMISSION: ${if (overlay) "Granted" else "Not granted"}\nACCESSIBILITY: ${if (accessibility) "At least one service enabled; check settings for ARUSHI specifically" else "Disabled"}\n\nDiagnostics are local estimates; they do not prove cloud AI availability."
        AlertDialog.Builder(this).setTitle("◉ System Diagnostics").setMessage(report)
            .setPositiveButton("APP SETTINGS") { _, _ -> openAppSettings() }
            .setNegativeButton("CLOSE", null).show()
    }

    private fun openAppSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { }
    }

    private fun readNotes() = try { JSONArray(prefs.getString("notes", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    private fun copy(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ARUSHI", value))
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class ExpressionParser(private val source: String) {
        private var pos = 0
        fun parse(): BigDecimal {
            val value = expression()
            whitespace()
            require(pos == source.length && source.isNotBlank()) { "Unexpected input" }
            return value
        }
        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                whitespace()
                value = when (peek()) {
                    '+' -> { pos++; value.add(term()) }
                    '-' -> { pos++; value.subtract(term()) }
                    else -> return value
                }
            }
        }
        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                whitespace()
                value = when (peek()) {
                    '*' -> { pos++; value.multiply(factor()) }
                    '/' -> { pos++; value.divide(factor(), MathContext.DECIMAL64) }
                    else -> return value
                }
            }
        }
        private fun factor(): BigDecimal {
            whitespace()
            if (peek() == '+') { pos++; return factor() }
            if (peek() == '-') { pos++; return factor().negate() }
            if (peek() == '(') {
                pos++; val value = expression(); whitespace()
                require(peek() == ')') { "Missing )" }; pos++; return value
            }
            val start = pos
            var dot = false
            while (pos < source.length && (source[pos].isDigit() || (source[pos] == '.' && !dot))) {
                if (source[pos] == '.') dot = true
                pos++
            }
            require(pos > start) { "Number expected" }
            return BigDecimal(source.substring(start, pos))
        }
        private fun peek(): Char? = source.getOrNull(pos)
        private fun whitespace() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
    }
}
