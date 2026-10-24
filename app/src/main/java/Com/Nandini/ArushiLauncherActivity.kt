package Com.Nandini

import android.app.Activity
import android.app.WallpaperManager
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
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ARUSHI's optional neon cyberpunk home screen. Android still owns protected system UI. */
class ArushiLauncherActivity : Activity() {
    private val bg = Color.rgb(5, 7, 22)
    private val panel = Color.rgb(14, 18, 43)
    private val purple = Color.rgb(177, 105, 255)
    private val blue = Color.rgb(83, 190, 255)
    private val pink = Color.rgb(255, 105, 205)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
    }

    private fun rounded(color: Int, stroke: Int = purple): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(18).toFloat(); setStroke(dp(1), stroke)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(dp(8), dp(7), dp(8), dp(7))
    }

    private fun button(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = rounded(panel, blue); setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { action() }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(22)); background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(bg, Color.rgb(19, 10, 48), bg)) }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("✦  ARUSHI  ✦", 30f, purple, true))
        root.addView(label("NEURAL HOME INTERFACE  /  LOVE • LIGHT • FUTURE", 10f, blue, true))
        val clock = label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 42f, Color.WHITE, true)
        root.addView(clock)
        root.addView(label(SimpleDateFormat("EEEE  •  dd MMMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()), 12f, blue))
        root.addView(label("♡  ALWAYS WITH YOU  ♡", 14f, pink, true))

        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = rounded(panel, purple); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        status.addView(label("● SYSTEM ONLINE", 11f, blue, true), LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(label("ANDROID 16", 11f, purple, true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(status, margin(-1, -2, 0, 0, 0, 16))

        root.addView(section("HOLOGRAPHIC APP GRID"))
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        val apps = listOf(
            Triple("✦", "ARUSHI AI", "ai"), Triple("☎", "PHONE", "phone"), Triple("✉", "MESSAGES", "sms"),
            Triple("◎", "CAMERA", "camera"), Triple("▧", "GALLERY", "gallery"), Triple("♫", "MUSIC", "music"),
            Triple("◈", "SETTINGS", "settings"), Triple("⌘", "BROWSER", "browser"), Triple("♡", "THEMES", "themes")
        )
        apps.forEach { (symbol, name, key) ->
            val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(panel, if (key == "ai") pink else purple); setPadding(dp(3), dp(8), dp(3), dp(8)) }
            tile.addView(label(symbol, 29f, if (key == "ai") pink else blue, true))
            tile.addView(label(name, 10f, Color.WHITE, true))
            tile.setOnClickListener { launch(key) }
            val p = GridLayout.LayoutParams().apply { width = 0; height = dp(94); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) }
            grid.addView(tile, p)
        }
        root.addView(grid)

        root.addView(section("CONTROL DECK"), margin(-1, -2, 0, 0, 0, 6))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("WI-FI SETTINGS") { openSettings(Settings.Panel.ACTION_WIFI) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(5), 0) })
        row1.addView(button("BLUETOOTH") { openSettings(Settings.Panel.ACTION_BLUETOOTH) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, 0, 0) })
        root.addView(row1, margin(-1, -2, 0, 0, 0, 8))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("DISPLAY") { safeStart(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(5), 0) })
        row2.addView(button("WALLPAPER") { openWallpaper() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), 0, 0, 0) })
        root.addView(row2, margin(-1, -2, 0, 0, 0, 8))

        root.addView(section("LAUNCHER CONFIGURATION"), margin(-1, -2, 0, 0, 0, 6))
        root.addView(label("Choose ARUSHI as your Home app to use this screen when you press Home. You can switch back to your vivo launcher in Android settings anytime.", 12f, Color.LTGRAY))
        root.addView(button("SET ARUSHI AS DEFAULT HOME") { requestHomeRole() }, margin(-1, -2, 0, 0, 0, 8))
        root.addView(button("OPEN ANDROID HOME SETTINGS") { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }, margin(-1, -2, 0, 0, 0, 8))
        root.addView(label("ARUSHI THEME ENGINE  •  PURPLE / BLUE HOLOGRAMS", 10f, purple, true))
        root.addView(label("This launcher changes the Home experience. Android controls protected lock-screen, notification-shade and system settings visuals.", 10f, Color.GRAY))
    }

    private fun section(text: String) = label("╋  $text  ╋", 12f, blue, true)
    private fun margin(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) = LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun launch(key: String) {
        val intent = when (key) {
            "ai" -> Intent().setClassName(packageName, "Com.Nandini.SplashActivity")
            "phone" -> Intent(Intent.ACTION_DIAL)
            "sms" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            "camera" -> Intent(MediaStoreIntent.CAMERA)
            "gallery" -> Intent(Intent.ACTION_VIEW).setType("image/*")
            "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            else -> { buildUi(); return }
        }
        safeStart(intent)
    }

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "That app or setting isn't available on this phone.", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings(action: String) {
        if (Build.VERSION.SDK_INT >= 29) safeStart(Intent(action)) else safeStart(Intent(Settings.ACTION_SETTINGS))
    }

    private fun openWallpaper() {
        try { startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)) }
        catch (_: Exception) { safeStart(Intent(Intent.ACTION_SET_WALLPAPER)) }
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

    private object MediaStoreIntent { const val CAMERA = "android.media.action.IMAGE_CAPTURE" }
}
