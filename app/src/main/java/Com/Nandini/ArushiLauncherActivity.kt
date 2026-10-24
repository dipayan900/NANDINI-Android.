package Com.Nandini

import android.app.Activity
import android.animation.ObjectAnimator
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Optional ARUSHI home launcher and local visual theme engine. Android retains protected system UI. */
class ArushiLauncherActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("arushi_theme_engine", MODE_PRIVATE) }
    private var themeIndex = 0
    private val themeNames = listOf("NEON CYBERPUNK", "LOVE HOLOGRAM", "ICE BLUE HUD")
    private val backgrounds = intArrayOf(Color.rgb(5, 7, 22), Color.rgb(19, 5, 28), Color.rgb(3, 15, 27))
    private val panels = intArrayOf(Color.rgb(14, 18, 43), Color.rgb(35, 12, 43), Color.rgb(8, 28, 43))
    private val purples = intArrayOf(Color.rgb(177, 105, 255), Color.rgb(255, 105, 205), Color.rgb(91, 220, 255))
    private val blues = intArrayOf(Color.rgb(83, 190, 255), Color.rgb(151, 139, 255), Color.rgb(113, 245, 255))
    private val pinks = intArrayOf(Color.rgb(255, 105, 205), Color.rgb(255, 125, 190), Color.rgb(148, 175, 255))
    private val bg get() = backgrounds[themeIndex]
    private val panel get() = panels[themeIndex]
    private val purple get() = purples[themeIndex]
    private val blue get() = blues[themeIndex]
    private val pink get() = pinks[themeIndex]
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeIndex = prefs.getInt("theme", 0).coerceIn(0, themeNames.lastIndex)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) buildUi()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun margin(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun rounded(color: Int, stroke: Int = purple) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(18).toFloat(); setStroke(dp(1), stroke)
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }

    private fun button(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = rounded(panel, blue); setPadding(dp(10), dp(12), dp(10), dp(12))
        isClickable = true; isFocusable = true
        setOnClickListener { action() }
    }

    private fun buildUi() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(bg, panel, bg))
        }
        scroll.addView(root)
        setContentView(scroll)

        val heroTitle = label("✦  ARUSHI  ✦", 31f, purple, true).apply {
            setShadowLayer(dp(14).toFloat(), 0f, 0f, blue)
            letterSpacing = 0.08f
        }
        root.addView(heroTitle)
        // A short, low-cost entrance glow: no repeating animation or background loop.
        ObjectAnimator.ofFloat(heroTitle, View.ALPHA, 0.72f, 1f).apply {
            duration = 760L
            start()
        }
        ObjectAnimator.ofFloat(heroTitle, View.SCALE_X, 0.97f, 1f).apply {
            duration = 620L
            start()
        }
        ObjectAnimator.ofFloat(heroTitle, View.SCALE_Y, 0.97f, 1f).apply {
            duration = 620L
            start()
        }
        root.addView(label("NEURAL HOME INTERFACE  /  LOVE • LIGHT • FUTURE", 10f, blue, true))
        root.addView(label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 42f, Color.WHITE, true))
        root.addView(label(SimpleDateFormat("EEEE  •  dd MMMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()), 12f, blue))
        root.addView(label("♡  ALWAYS WITH YOU  ♡", 14f, pink, true))

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(panel, purple); setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        status.addView(label("● SYSTEM ONLINE", 10f, blue, true), LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(label(themeNames[themeIndex], 10f, purple, true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(status, margin(-1, -2, 0, 0, 0, 12))

        root.addView(section("HOLOGRAPHIC APP GRID"))
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        val apps = listOf(
            Triple("✦", "ARUSHI AI", "ai"), Triple("☎", "PHONE", "phone"), Triple("✉", "MESSAGES", "sms"),
            Triple("◎", "CAMERA", "camera"), Triple("▧", "PHOTOS", "gallery"), Triple("♫", "MUSIC", "music"),
            Triple("◈", "SETTINGS", "settings"), Triple("⌘", "BROWSER", "browser"), Triple("▦", "APP DRAWER", "drawer")
        )
        apps.forEach { (symbol, name, key) ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = rounded(panel, if (key == "ai") pink else purple)
                setPadding(dp(3), dp(8), dp(3), dp(8)); isClickable = true; isFocusable = true
            }
            tile.addView(label(symbol, 27f, if (key == "ai") pink else blue, true))
            tile.addView(label(name, 9f, Color.WHITE, true))
            tile.setOnClickListener { if (key == "drawer") showAppDrawer() else launch(key) }
            tile.alpha = 0f
            tile.translationY = dp(8).toFloat()
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0; height = dp(88); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
            // Stagger-free entrance keeps the home screen smooth on mid-range phones.
            tile.animate().alpha(1f).translationY(0f).setDuration(260L).start()
        }
        root.addView(grid)

        root.addView(section("CONTROL DECK"), margin(-1, -2, 0, 8, 0, 6))
        addTwoButtons("WI-FI SETTINGS", { openSettings("android.settings.panel.action.WIFI") },
            "BLUETOOTH", { openSettings("android.settings.panel.action.BLUETOOTH") })
        addTwoButtons("DISPLAY SETTINGS", { safeStart(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            "SOUND SETTINGS", { safeStart(Intent(Settings.ACTION_SOUND_SETTINGS)) })
        addTwoButtons("BATTERY", { safeStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
            "ALL SETTINGS", { safeStart(Intent(Settings.ACTION_SETTINGS)) })

        root.addView(section("ARUSHI THEME ENGINE"), margin(-1, -2, 0, 12, 0, 6))
        root.addView(label("ACTIVE PRESET: ${themeNames[themeIndex]}\nChoose a visual preset. Your choice is saved on this phone.", 12f, Color.LTGRAY))
        addTwoButtons("NEON CYBERPUNK", { setThemePreset(0) }, "LOVE HOLOGRAM", { setThemePreset(1) })
        root.addView(button("ICE BLUE HUD") { setThemePreset(2) }, margin(-1, -2, 0, 6, 0, 6))
        root.addView(button("SET ARUSHI AS DEFAULT HOME") { requestHomeRole() }, margin(-1, -2, 0, 6, 0, 6))
        root.addView(button("OPEN ANDROID HOME SETTINGS") { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }, margin(-1, -2, 0, 6, 0, 6))
        root.addView(button("OPEN WALLPAPER SETTINGS") { safeStart(Intent(Intent.ACTION_SET_WALLPAPER)) }, margin(-1, -2, 0, 6, 0, 6))
        root.addView(label("ARUSHI CYBERPUNK HOME  •  LOCAL THEME ENGINE", 10f, purple, true))
        root.addView(label("This themes ARUSHI's launcher only. Android/vivo still controls the real lock screen, notification shade, system dialogs and protected settings. No root required.", 10f, Color.GRAY))
    }

    private fun section(text: String) = label("╋  $text  ╋", 12f, blue, true)

    private fun addTwoButtons(left: String, leftAction: () -> Unit, right: String, rightAction: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(left, leftAction), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(5), 0) })
        row.addView(button(right, rightAction), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, 0, 0) })
        root.addView(row, margin(-1, -2, 0, 0, 0, 8))
    }

    private fun setThemePreset(index: Int) {
        themeIndex = index.coerceIn(0, themeNames.lastIndex)
        prefs.edit().putInt("theme", themeIndex).apply()
        buildUi()
    }

    private fun launch(key: String) {
        val intent = when (key) {
            "ai" -> Intent().setClassName(packageName, "com.dipayan.nandini.SplashActivity")
            "phone" -> Intent(Intent.ACTION_DIAL)
            "sms" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
            "gallery" -> Intent(Intent.ACTION_VIEW).setType("image/*")
            "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            else -> return
        }
        safeStart(intent)
    }

    private fun showAppDrawer() {
        val apps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).distinctBy { it.activityInfo.packageName }.sortedBy {
            it.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
        }
        val old = root
        old.removeAllViews()
        old.addView(label("✦  ARUSHI APP MATRIX  ✦", 25f, purple, true))
        old.addView(label("Tap an app to launch it", 12f, blue))
        old.addView(button("← BACK TO ARUSHI HOME") { buildUi() }, margin(-1, -2, 0, 8, 0, 8))
        apps.forEach { info ->
            val title = info.loadLabel(packageManager).toString()
            root.addView(button("◈  $title") {
                val launchIntent = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)
                if (launchIntent != null) safeStart(launchIntent)
            }, margin(-1, -2, 0, 3, 0, 3))
        }
    }

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) }
        catch (_: Exception) { Toast.makeText(this, "That app or setting isn't available on this phone.", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings(action: String) {
        try { safeStart(Intent(action)) }
        catch (_: Exception) { safeStart(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 7301)
                return
            }
        }
        safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}
