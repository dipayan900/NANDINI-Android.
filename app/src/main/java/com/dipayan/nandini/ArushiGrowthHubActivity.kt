package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Social account analytics, scheduled news briefings and a merchant-handoff shopping list. */
class ArushiGrowthHubActivity : Activity() {
    private val accent = Color.rgb(190, 126, 255)
    private val muted = Color.LTGRAY
    private lateinit var body: LinearLayout
    private lateinit var socialStatus: TextView
    private val http by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            setBackgroundColor(Color.rgb(12, 9, 24))
        }
        root.addView(TextView(this).apply {
            text = "ARUSHI  •  GROWTH HUB"; textSize = 23f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        })
        root.addView(TextView(this).apply {
            text = "CREATOR ANALYTICS  /  DAILY INTELLIGENCE  /  SMART SHOPPING"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(muted)
        })
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "← BACK"; setOnClickListener { finish() } })
        setContentView(root)

        section("SOCIAL ACCOUNTS & ANALYTICS",
            "Connect only accounts you own or are authorized to manage. ARUSHI uses official provider APIs; no password scraping.")
        socialStatus = label("Not connected in this app yet.")
        body.addView(socialStatus)
        button("CONNECT YOUTUBE") { startOAuth("youtube") }
        button("CONNECT INSTAGRAM PROFESSIONAL") { startOAuth("instagram") }
        button("REFRESH SOCIAL ANALYTICS") { loadAnalytics() }
        button("DISCONNECT SOCIAL ACCOUNTS") { disconnectSocial() }
        section("SCHEDULED NEWS BRIEFINGS",
            "Choose a topic and a daily delivery time. Android WorkManager runs when network and battery conditions allow.")
        button("SET NEWS TOPIC") { editNewsTopic() }
        button("SET DAILY BRIEFING TIME") { chooseBriefingTime() }
        button("ENABLE DAILY BRIEFING") { scheduleBriefing() }
        button("PAUSE DAILY BRIEFING") {
            WorkManager.getInstance(this).cancelUniqueWork(NewsBriefingWorker.WORK_NAME)
            Toast.makeText(this, "Daily briefing paused.", Toast.LENGTH_SHORT).show()
        }
        section("SHOPPING LIST & MERCHANT CHECKOUT",
            "Search general products, save items locally, then open the merchant's official HTTPS checkout. ARUSHI never stores payment details or buys automatically.")
        button("SEARCH PRODUCTS") { searchProducts() }
        button("ADD ITEM TO SHOPPING LIST") { addCartItem() }
        button("VIEW SHOPPING LIST") { showCart() }
    }

    private fun section(title: String, subtitle: String) {
        body.addView(TextView(this).apply {
            text = title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent); setPadding(0, dp(18), 0, dp(5))
        })
        body.addView(label(subtitle))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; textSize = 12f; setTextColor(muted); setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { action() }
        body.addView(this, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })
    }

    private fun startOAuth(provider: String) {
        Thread {
            try {
                val user = requireFirebaseUser()
                val token = com.google.android.gms.tasks.Tasks.await(user.getIdToken(true)).token ?: error("Firebase session unavailable")
                val project = com.google.firebase.FirebaseApp.getInstance().options.projectId
                    ?: error("Firebase project is not configured")
                val url = "https://us-central1-$project.cloudfunctions.net/socialApi?action=start&provider=$provider"
                val req = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
                val json = http.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("Social connection service: HTTP ${response.code} ${raw.take(250)}")
                    JSONObject(raw)
                }
                val authUrl = json.optString("authUrl")
                require(authUrl.startsWith("https://")) { "Provider did not return a secure authorization URL" }
                runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))) }
            } catch (e: Exception) {
                runOnUiThread {
                    socialStatus.text = "Connection setup required: ${e.localizedMessage ?: "service unavailable"}"
                    Toast.makeText(this, "Configure and deploy ARUSHI social API first.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun requireFirebaseUser(): com.google.firebase.auth.FirebaseUser {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser ?: com.google.android.gms.tasks.Tasks.await(auth.signInAnonymously()).user
            ?: error("ARUSHI could not create its Firebase session. Enable Anonymous sign-in in Firebase Authentication.")
    }

    private fun handleCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.dipayan.nandini" || uri.host != "social-auth") return
        val status = uri.getQueryParameter("status") ?: "unknown"
        val provider = uri.getQueryParameter("provider") ?: "social"
        socialStatus.text = if (status == "connected") "${provider.uppercase()} connected. Refresh analytics to load account insights."
            else "${provider.uppercase()} connection did not complete: ${uri.getQueryParameter("reason") ?: status}"
    }

    private fun loadAnalytics() {
        Thread {
            try {
                val user = requireFirebaseUser()
                val token = com.google.android.gms.tasks.Tasks.await(user.getIdToken(true)).token ?: error("Session unavailable")
                val project = com.google.firebase.FirebaseApp.getInstance().options.projectId ?: error("Firebase project missing")
                val url = "https://us-central1-$project.cloudfunctions.net/socialApi?action=analytics"
                val req = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
                val raw = http.newCall(req).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(300)}")
                    text
                }
                val data = JSONObject(raw)
                val result = StringBuilder()
                val accounts = data.optJSONArray("accounts") ?: JSONArray()
                for (i in 0 until accounts.length()) {
                    val item = accounts.optJSONObject(i) ?: continue
                    result.append(item.optString("provider").uppercase()).append(" • ")
                        .append(item.optString("name", "Connected account")).append('\n')
                    val metrics = item.optJSONObject("metrics") ?: JSONObject()
                    val keys = metrics.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result.append("  ").append(key).append(": ").append(metrics.opt(key)).append('\n')
                    }
                }
                runOnUiThread { socialStatus.text = if (result.isEmpty()) "No connected social accounts yet." else result.toString() }
            } catch (e: Exception) {
                runOnUiThread { socialStatus.text = "Analytics unavailable: ${e.localizedMessage ?: "network or provider error"}" }
            }
        }.start()
    }

    private fun disconnectSocial() {
        AlertDialog.Builder(this).setTitle("Disconnect social accounts?")
            .setMessage("This removes ARUSHI's stored provider tokens. You can also revoke access in the provider's own account settings.")
            .setNegativeButton("CANCEL", null).setPositiveButton("DISCONNECT") { _, _ ->
                Thread {
                    runCatching {
                        val user = requireFirebaseUser()
                        val token = com.google.android.gms.tasks.Tasks.await(user.getIdToken(true)).token ?: return@runCatching
                        val project = com.google.firebase.FirebaseApp.getInstance().options.projectId ?: return@runCatching
                        val req = Request.Builder()
                            .url("https://us-central1-$project.cloudfunctions.net/socialApi?action=disconnect")
                            .header("Authorization", "Bearer $token").post(okhttp3.FormBody.Builder().build()).build()
                        http.newCall(req).execute().close()
                    }
                    runOnUiThread { socialStatus.text = "ARUSHI social tokens cleared where configured." }
                }.start()
            }.show()
    }

    private fun editNewsTopic() {
        val input = EditText(this).apply {
            hint = "e.g. technology, science, Minecraft updates"
            setText(getSharedPreferences("arushi_news", MODE_PRIVATE).getString("topic", "technology"))
        }
        AlertDialog.Builder(this).setTitle("Daily briefing topic").setView(input)
            .setNegativeButton("CANCEL", null).setPositiveButton("SAVE") { _, _ ->
                val topic = input.text.toString().trim().take(100)
                if (topic.isNotBlank()) getSharedPreferences("arushi_news", MODE_PRIVATE).edit().putString("topic", topic).apply()
            }.show()
    }

    private fun chooseBriefingTime() {
        val prefs = getSharedPreferences("arushi_news", MODE_PRIVATE)
        TimePickerDialog(this, { _, hour, minute ->
            prefs.edit().putInt("hour", hour).putInt("minute", minute).apply()
            Toast.makeText(this, "Briefing time saved. Enable the daily briefing to apply it.", Toast.LENGTH_LONG).show()
        }, prefs.getInt("hour", 8), prefs.getInt("minute", 0), false).show()
    }

    private fun scheduleBriefing() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7302)
            Toast.makeText(this, "Allow notifications, then tap ENABLE DAILY BRIEFING again.", Toast.LENGTH_LONG).show()
            return
        }
        val prefs = getSharedPreferences("arushi_news", MODE_PRIVATE)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt("hour", 8))
            set(Calendar.MINUTE, prefs.getInt("minute", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
        val request = PeriodicWorkRequestBuilder<NewsBriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NewsBriefingWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
        Toast.makeText(this, "Daily briefing scheduled. Android may delay delivery to protect battery.", Toast.LENGTH_LONG).show()
    }

    private fun searchProducts() {
        val input = EditText(this).apply { hint = "Search general products" }
        AlertDialog.Builder(this).setTitle("Product search").setView(input)
            .setNegativeButton("CANCEL", null).setPositiveButton("SEARCH") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?tbm=shop&q=" + Uri.encode(q))))
            }.show()
    }

    private fun cart(): JSONArray = try {
        JSONArray(ArushiSecureMemory.get(this, "shopping.cart.v1") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    private fun saveCart(items: JSONArray) = ArushiSecureMemory.put(this, "shopping.cart.v1", items.toString())

    private fun addCartItem() {
        val title = EditText(this).apply { hint = "Product name" }
        val url = EditText(this).apply { hint = "Official merchant product/checkout HTTPS link" }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0); addView(title); addView(url) }
        AlertDialog.Builder(this).setTitle("Add shopping item").setView(wrap)
            .setNegativeButton("CANCEL", null).setPositiveButton("SAVE") { _, _ ->
                val name = title.text.toString().trim().take(120)
                val link = url.text.toString().trim()
                if (name.isBlank() || !link.startsWith("https://")) {
                    Toast.makeText(this, "Enter a name and a secure HTTPS merchant link.", Toast.LENGTH_LONG).show()
                } else {
                    val items = cart()
                    items.put(JSONObject().put("name", name).put("url", link))
                    saveCart(items)
                    Toast.makeText(this, "Saved in your encrypted shopping list.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun showCart() {
        val items = cart()
        if (items.length() == 0) {
            AlertDialog.Builder(this).setMessage("Your shopping list is empty.").setPositiveButton("OK", null).show()
            return
        }
        val names = Array(items.length()) { i -> items.optJSONObject(i)?.optString("name", "Item") ?: "Item" }
        AlertDialog.Builder(this).setTitle("Shopping list • ${items.length()} items")
            .setItems(names) { _, index ->
                val item = items.optJSONObject(index) ?: return@setItems
                AlertDialog.Builder(this).setTitle(item.optString("name"))
                    .setMessage("Open the merchant's official page? Payment and order confirmation happen outside ARUSHI. Ask a parent/guardian to approve purchases.")
                    .setNegativeButton("BACK", null)
                    .setNeutralButton("REMOVE") { _, _ -> items.remove(index); saveCart(items) }
                    .setPositiveButton("OPEN MERCHANT") { _, _ ->
                        val uri = Uri.parse(item.optString("url"))
                        if (uri.scheme == "https") startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }.show()
            }.setPositiveButton("CLOSE", null).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

class NewsBriefingWorker(context: android.content.Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("arushi_news", android.content.Context.MODE_PRIVATE)
            val topic = prefs.getString("topic", "technology")?.take(100) ?: "technology"
            val query = URLEncoder.encode(topic, "UTF-8")
            val url = URL("https://news.google.com/rss/search?q=$query&hl=en-IN&gl=IN&ceid=IN:en")
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 12000
                setRequestProperty("User-Agent", "ARUSHI-NewsBriefing/1.0")
            }
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            connection.inputStream.use { parser.setInput(it, "UTF-8") }
            val stories = mutableListOf<Pair<String, String>>()
            var event = parser.eventType
            var inItem = false
            var tag = ""
            var title = ""
            var link = ""
            while (event != XmlPullParser.END_DOCUMENT && stories.size < 5) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "item") { inItem = true; title = ""; link = "" }
                    }
                    XmlPullParser.TEXT -> if (inItem) {
                        if (tag == "title") title += parser.text
                        if (tag == "link") link += parser.text
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                        if (title.isNotBlank()) stories.add(title.trim() to link.trim())
                        inItem = false
                    }
                }
                event = parser.next()
            }
            connection.disconnect()
            if (stories.isEmpty()) return Result.retry()
            val manager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(android.app.NotificationChannel(
                    "arushi_news_briefings", "ARUSHI News Briefings", android.app.NotificationManager.IMPORTANCE_DEFAULT
                ))
            }
            val text = stories.mapIndexed { i, pair -> "${i + 1}. ${pair.first}" }.joinToString("\n")
            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "arushi_news_briefings")
                .setSmallIcon(applicationContext.applicationInfo.icon)
                .setContentTitle("Your ARUSHI briefing • $topic")
                .setContentText(stories.first().first.take(120))
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).build()
            androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(7301, notification)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object { const val WORK_NAME = "arushi_daily_news_briefing" }
}
