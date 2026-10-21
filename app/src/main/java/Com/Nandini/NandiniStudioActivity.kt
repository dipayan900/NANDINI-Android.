package com.dipayan.nandini

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Additive local workspace; existing chat/history code is not changed. */
class NandiniStudioActivity : Activity() {
    private val bg = Color.rgb(10, 8, 22)
    private val panel = Color.rgb(29, 20, 48)
    private val accent = Color.rgb(190, 139, 255)
    private val white = Color.rgb(250, 247, 255)
    private val muted = Color.rgb(185, 174, 215)
    private val prefs by lazy { getSharedPreferences("nandini_studio_v1", Context.MODE_PRIVATE) }
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(31, 12, 45), bg, Color.rgb(7, 6, 17))
            )
        }
        root.addView(TextView(this).apply {
            text = "N A N D I N I"; textSize = 23f; gravity = Gravity.CENTER
            letterSpacing = .09f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(TextView(this).apply {
            text = "STUDIO • MEMORY / TASKS / SYSTEM / CREATOR"; textSize = 10f
            gravity = Gravity.CENTER; letterSpacing = .06f; setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(12)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeButton("← BACK TO NANDINI CHAT") { finish() }, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        renderHome()
    }

    private fun renderHome() {
        body.removeAllViews()
        addSection("YOUR WORKSPACE", "Choose a module. Studio data is stored locally on this device.")
        addFeature("🧠 MEMORY PALACE", "Save useful facts, preferences, project notes and goals. Review or delete them any time.") { showMemoryPalace() }
        addFeature("⚡ TASK ENGINE", "Turn a goal into an editable checklist and keep a local task log. This version plans; it does not silently execute actions.") { showTaskEngine() }
        addFeature("📱 SYSTEM INTELLIGENCE", "Inspect battery, memory and network availability, plus quick Android settings shortcuts.") { showSystemCentre() }
        addFeature("🛠 CREATOR MODE", "Open your project repository and copy prompts for build-error diagnosis or release preparation.") { showCreatorMode() }
        addFeature("🎭 ADAPTIVE MODES", "Choose a working mode to guide your next request in NANDINI chat.") { showModes() }
        addSection("PRIVACY NOTE", "Memory and task records stay in this app's private local preferences. Clearing app storage can remove them.")
    }

    private fun addSection(title: String, subtitle: String) {
        body.addView(TextView(this).apply {
            text = title; textSize = 11f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent); setPadding(dp(3), dp(12), dp(3), dp(4))
        })
        body.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(muted); setPadding(dp(3), 0, dp(3), dp(10))
        })
    }

    private fun addFeature(title: String, description: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat(); setColor(panel)
                setStroke(dp(1), Color.argb(150, 150, 105, 210))
            }
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }
        card.addView(TextView(this).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white) })
        card.addView(TextView(this).apply { text = description; textSize = 12f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun showMemoryPalace() {
        val entries = readArray("memory")
        val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        if (entries.length() == 0) view.addView(TextView(this).apply {
            text = "Your Memory Palace is empty. Add a project note, preference or goal."; setTextColor(muted)
        })
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = item.optString("text"); textSize = 14f; setTextColor(white); setPadding(0, dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(makeButton("DELETE") {
                val current = readArray("memory"); val rebuilt = JSONArray()
                for (j in 0 until current.length()) if (j != i) rebuilt.put(current.getJSONObject(j))
                writeArray("memory", rebuilt); showMemoryPalace()
            })
            view.addView(row)
        }
        AlertDialog.Builder(this).setTitle("🧠 Memory Palace").setView(view)
            .setPositiveButton("ADD MEMORY") { _, _ -> addMemoryDialog() }
            .setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
    }

    private fun addMemoryDialog() {
        val input = EditText(this).apply { hint = "Example: My app uses Kotlin and GitHub Actions"; setTextColor(white); setHintTextColor(muted) }
        AlertDialog.Builder(this).setTitle("Save a memory")
            .setMessage("Only save information you want NANDINI to retain on this device.")
            .setView(input).setPositiveButton("SAVE") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    val arr = readArray("memory")
                    arr.put(JSONObject().put("text", value).put("createdAt", System.currentTimeMillis()))
                    writeArray("memory", arr)
                    Toast.makeText(this, "Memory saved on this device.", Toast.LENGTH_SHORT).show()
                    showMemoryPalace()
                }
            }.setNegativeButton("CANCEL", null).show().also { styleDialog(it); it }
    }

    private fun showTaskEngine() {
        val input = EditText(this).apply {
            hint = "Describe a goal to plan..."
            setTextColor(white); setHintTextColor(muted)
        }
        val saved = readArray("tasks")
        val log = TextView(this).apply {
            setTextColor(muted); textSize = 12f
            val lines = (0 until saved.length()).mapNotNull { i ->
                saved.optJSONObject(i)?.let { task ->
                    val steps = task.optJSONArray("steps") ?: JSONArray()
                    var complete = 0
                    for (j in 0 until steps.length()) if (steps.optJSONObject(j)?.optBoolean("done", false) == true) complete++
                    task.optString("title") + "  •  " + complete + "/" + steps.length() + " steps"
                }
            }
            text = if (lines.isEmpty()) "No saved plans yet." else "RECENT PLANS\n" + lines.takeLast(8).joinToString("\n") { "• " + it }
            setPadding(0, dp(10), 0, 0)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(input)
            addView(TextView(this@NandiniStudioActivity).apply {
                text = "Plans and checkbox progress are saved locally. Review steps before real-world actions."
                setTextColor(muted); textSize = 12f
            })
            addView(log)
        }
        AlertDialog.Builder(this).setTitle("⚡ Task Engine").setView(wrap)
            .setPositiveButton("MAKE PLAN") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val taskId = System.currentTimeMillis().toString()
                    val steps = JSONArray()
                    listOf(
                        "Define the desired result and constraints",
                        "Inspect current files, settings and dependencies",
                        "Break the work into small, reversible steps",
                        "Implement one step and run the relevant checks",
                        "Review failures and fix the confirmed cause",
                        "Test the result and keep a rollback path"
                    ).forEach { steps.put(JSONObject().put("title", it).put("done", false)) }
                    val arr = readArray("tasks")
                    arr.put(JSONObject().put("id", taskId).put("title", title)
                        .put("createdAt", System.currentTimeMillis()).put("steps", steps))
                    while (arr.length() > 30) arr.remove(0)
                    writeArray("tasks", arr)
                    showPlan(taskId)
                }
            }.setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
    }

    private fun showPlan(taskId: String) {
        val tasks = readArray("tasks")
        var task: JSONObject? = null
        for (i in 0 until tasks.length()) {
            val candidate = tasks.optJSONObject(i)
            if (candidate?.optString("id") == taskId) { task = candidate; break }
        }
        val current = task ?: return
        val steps = current.optJSONArray("steps") ?: JSONArray()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        box.addView(TextView(this).apply {
            text = current.optString("title"); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(white)
        })
        val progress = TextView(this).apply { setTextColor(muted); textSize = 12f }
        fun refreshProgress() {
            var complete = 0
            for (i in 0 until steps.length()) if (steps.optJSONObject(i)?.optBoolean("done", false) == true) complete++
            progress.text = "Progress: " + complete + " / " + steps.length() + " complete"
        }
        box.addView(progress)
        refreshProgress()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            val checkbox = CheckBox(this).apply {
                text = step.optString("title"); textSize = 13f; setTextColor(white)
                isChecked = step.optBoolean("done", false)
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            }
            checkbox.setOnCheckedChangeListener { _, checked ->
                step.put("done", checked)
                writeArray("tasks", tasks)
                refreshProgress()
            }
            box.addView(checkbox)
        }
        AlertDialog.Builder(this).setTitle("Your saved task plan").setView(box)
            .setPositiveButton("DONE", null).show().also { styleDialog(it); it }
    }

    private fun showSystemCentre() {
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val battery = if (level >= 0 && scale > 0) (level * 100 / scale).toString() + "%" else "Unavailable"
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo(); manager.getMemoryInfo(mem)
        val ram = (mem.availMem / (1024 * 1024)).toString() + " MB available of " + (mem.totalMem / (1024 * 1024)) + " MB"
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile network connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
                else -> "Connected"
            }
        } else "Status unavailable on this Android version"
        val info = "BATTERY: " + battery + "\n\nNETWORK: " + network + "\n\nDEVICE MEMORY: " + ram +
            "\n\nThese are diagnostic readings, not a guarantee that AI services are reachable."
        AlertDialog.Builder(this).setTitle("📱 System Intelligence").setMessage(info)
            .setPositiveButton("NETWORK SETTINGS") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(this, "Settings couldn't be opened.", Toast.LENGTH_SHORT).show() }
            }.setNeutralButton("APP SETTINGS") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
            }.setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
    }

    private fun showCreatorMode() {
        val options = arrayOf(
            "Open NANDINI repository",
            "Check latest GitHub Actions build status",
            "Open latest GitHub Actions builds",
            "Open feature pull request",
            "Copy build-error diagnosis prompt",
            "Copy APK release checklist"
        )
        AlertDialog.Builder(this).setTitle("🛠 Creator Mode").setItems(options) { _, which ->
            when (which) {
                0 -> openUrl("https://github.com/dipayan900/NANDINI-Android.")
                1 -> showLatestBuildStatus()
                2 -> openUrl("https://github.com/dipayan900/NANDINI-Android./actions")
                3 -> openUrl("https://github.com/dipayan900/NANDINI-Android./pull/1")
                4 -> copyText("NANDINI Android/Kotlin build diagnosis:\n1. Read the first real compiler error, not only later cascading errors.\n2. Inspect the referenced file and surrounding code.\n3. Identify the confirmed root cause and smallest reversible fix.\n4. Preserve chat/history, Firebase AI, voice, camera, image generation, wake word and companion overlay.\n5. Run the GitHub Actions APK build and inspect its complete log if it fails.\n6. Never claim a fix is verified until the build succeeds.")
                4 -> copyText("NANDINI APK release checklist:\n1. Build on feature/myraa-style-avatar-safe.\n2. Confirm the latest GitHub Actions run succeeded and the artifact belongs to the latest commit.\n3. Install on the target Android phone.\n4. Test all six Studio features plus chat, saved history, voice, wake word, camera, image generation and companion overlay.\n5. Check logs for crashes and test offline/permission-denied cases.\n6. Keep the last known-good APK for rollback.")
            }
        }.setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
    }

    private fun showLatestBuildStatus() {
        Toast.makeText(this, "Checking the latest public GitHub Actions run…", Toast.LENGTH_SHORT).show()
        Thread {
            var message = "Couldn't read the latest build status. Open GitHub Actions to check manually."
            var runUrl = "https://github.com/dipayan900/NANDINI-Android./actions"
            try {
                val connection = (java.net.URL("https://api.github.com/repos/dipayan900/NANDINI-Android./actions/runs?per_page=1")
                    .openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NANDINI-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) error("GitHub returned HTTP " + connection.responseCode)
                    val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val runs = root.optJSONArray("workflow_runs") ?: JSONArray()
                    val run = runs.optJSONObject(0)
                    if (run != null) {
                        val title = run.optString("display_title", "NANDINI APK build")
                        val branch = run.optString("head_branch", "unknown")
                        val status = run.optString("status", "unknown")
                        val conclusion = run.optString("conclusion", "pending")
                        val sha = run.optString("head_sha", "").take(7)
                        runUrl = run.optString("html_url", runUrl)
                        message = "LATEST BUILD\n\n$title\nBranch: $branch\nStatus: $status\nResult: $conclusion\nCommit: $sha\n\nThis checks the build status only; it doesn't install or device-test the APK."
                    } else {
                        message = "No GitHub Actions runs were returned. Open Actions to inspect the repository."
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                message = "Build status check failed: " + (e.message ?: "network error") + "\n\nOpen GitHub Actions to inspect it manually."
            }
            runOnUiThread {
                AlertDialog.Builder(this).setTitle("🛠 Creator Build Monitor")
                    .setMessage(message)
                    .setPositiveButton("OPEN RUN") { _, _ -> openUrl(runUrl) }
                    .setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
            }
        }.start()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open the link on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showModes() {
        val modes = arrayOf("Companion", "Developer", "Researcher", "Vision", "Hands-free")
        val descriptions = arrayOf(
            "Natural conversation and supportive chat",
            "Prioritise code, build errors and project plans",
            "Look for evidence and separate facts from assumptions",
            "Work with an image or camera input only when I provide or activate it",
            "Prefer short voice-friendly replies and explicit listening controls"
        )
        val current = prefs.getString("mode", "Companion") ?: "Companion"
        AlertDialog.Builder(this).setTitle("🎭 Adaptive Mode • Current: " + current).setItems(modes) { _, which ->
            prefs.edit().putString("mode", modes[which]).apply()
            val text = "NANDINI mode: " + modes[which] + ". " + descriptions[which]
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("NANDINI mode prompt", text))
            Toast.makeText(this, modes[which] + " mode saved. It will guide new AI replies.", Toast.LENGTH_LONG).show()
        }.setNegativeButton("CLOSE", null).show().also { styleDialog(it); it }
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NANDINI Creator Mode", value))
        Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
    }

    private fun readArray(key: String): JSONArray = try { JSONArray(prefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    private fun writeArray(key: String, value: JSONArray) { prefs.edit().putString(key, value.toString()).apply() }
    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setTextColor(white)
        backgroundTintList = android.content.res.ColorStateList.valueOf(panel)
        setOnClickListener { action() }
    }
    private fun styleDialog(dialog: AlertDialog) {
        val bg = android.graphics.Color.rgb(25, 8, 35)
        val pink = android.graphics.Color.rgb(255, 126, 190)
        val lavender = android.graphics.Color.rgb(211, 164, 255)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), android.graphics.Color.rgb(123, 55, 150))
        })
        dialog.window?.setDimAmount(0.72f)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(pink)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(lavender)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(lavender)
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<android.widget.TextView>(titleId)?.setTextColor(pink)
            val messageId = resources.getIdentifier("message", "id", "android")
            dialog.findViewById<android.widget.TextView>(messageId)?.setTextColor(lavender)
            val listId = resources.getIdentifier("select_dialog_listview", "id", "android")
            dialog.findViewById<android.widget.ListView>(listId)?.let { list ->
                list.setBackgroundColor(bg)
                for (i in 0 until list.childCount) {
                    (list.getChildAt(i) as? android.widget.TextView)?.setTextColor(lavender)
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
