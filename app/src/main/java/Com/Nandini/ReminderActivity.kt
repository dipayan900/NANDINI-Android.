package com.dipayan.nandini

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ReminderActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("nandini_reminders", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(13, 7, 22)
    private val panel = Color.rgb(35, 17, 43)
    private val accent = Color.rgb(255, 132, 190)
    private val white = Color.rgb(255, 247, 252)
    private val muted = Color.rgb(198, 169, 201)
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        createNotificationChannel()
        ReminderScheduler.rescheduleAll(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(31, 10, 38), bg, Color.rgb(10, 7, 20))
            )
        }
        root.addView(TextView(this).apply {
            text = "♡  YOUR MOMENTS  ♡"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(accent)
            letterSpacing = 0.035f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(TextView(this).apply {
            text = "IMPORTANT DATES  ·  DAILY RITUALS  ·  BIRTHDAYS"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, dp(30)))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buttons.addView(makeButton("+ IMPORTANT DATE / EVENT") { chooseDateAndTime(false, false) })
        buttons.addView(makeButton("+ DAILY SCHEDULE / REMINDER") { chooseDateAndTime(true, false) })
        buttons.addView(makeButton("+ BIRTHDAY (YEARLY, 12 AM)") { chooseDateAndTime(false, true) })
        root.addView(buttons)
        root.addView(TextView(this).apply {
            text = "SAVED REMINDERS"
            textSize = 13f
            setTextColor(accent)
            setPadding(0, dp(12), 0, dp(6))
        })
        val scroller = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeButton("← BACK TO NANDINI") { finish() })
        setContentView(root)
        root.alpha = 0f
        root.translationY = dp(12).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(420L)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        refreshList()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 700)
        }
    }

    private fun chooseDateAndTime(daily: Boolean, birthday: Boolean) {
        fun premiumField(hintText: String) = EditText(this).apply {
            hint = hintText
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 16f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(48, 25, 58))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(113, 63, 127))
            }
        }
        val name = premiumField(if (birthday) "Person's name" else "Event / reminder title").apply { setSingleLine(true) }
        val note = premiumField(if (birthday) "Birthday wish (optional)" else "Notification message (optional)")
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
            addView(name, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(note, LinearLayout.LayoutParams(-1, -2))
        }
        val cal = Calendar.getInstance()
        val datePicker = DatePickerDialog(this, { _, year, month, day ->
            cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, month); cal.set(Calendar.DAY_OF_MONTH, day)
            val timeAction: (Int, Int) -> Unit = { hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, if (birthday) 0 else hour)
                cal.set(Calendar.MINUTE, if (birthday) 0 else minute)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val title = name.text.toString().trim()
                if (title.isBlank()) {
                    Toast.makeText(this, "Please enter a name or title.", Toast.LENGTH_SHORT).show()
                } else {
                    val id = UUID.randomUUID().toString()
                    val item = JSONObject()
                        .put("id", id).put("title", title)
                        .put("message", note.text.toString().trim().ifBlank {
                            if (birthday) "Happy birthday, $title! 🎂💜" else "It's time for $title."
                        })
                        .put("time", cal.timeInMillis)
                        .put("daily", daily).put("birthday", birthday)
                        .put("year", year).put("month", month).put("day", day)
                    val all = readAll()
                    all.put(item)
                    prefs.edit().putString("items", all.toString()).apply()
                    ReminderScheduler.schedule(this, item)
                    refreshList()
                    Toast.makeText(this, if (birthday) "Birthday saved. NANDINI will wish at midnight 💜" else "Reminder saved 💜", Toast.LENGTH_LONG).show()
                }
            }
            if (birthday) timeAction(0, 0) else TimePickerDialog(this, { _, hour, minute -> timeAction(hour, minute) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        datePicker.datePicker.minDate = if (birthday) 0L else System.currentTimeMillis() - 1000L
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (birthday) "♡  ADD BIRTHDAY" else if (daily) "✦  DAILY RITUAL" else "✦  IMPORTANT MOMENT")
            .setView(wrap)
            .setPositiveButton("CHOOSE DATE / TIME") { _, _ -> datePicker.show() }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    private fun readAll(): JSONArray = try { JSONArray(prefs.getString("items", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun refreshList() {
        list.removeAllViews()
        val all = readAll()
        if (all.length() == 0) {
            list.addView(TextView(this).apply { text = "No reminders yet. Add an event or birthday above."; setTextColor(muted); setPadding(0, dp(12), 0, dp(12)) })
            return
        }
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        for (i in 0 until all.length()) {
            val item = all.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val title = item.optString("title", "Reminder")
            val type = when {
                item.optBoolean("birthday") -> "🎂 Birthday • yearly at 12:00 AM"
                item.optBoolean("daily") -> "🔁 Daily reminder"
                else -> "📅 One-time event"
            }
            val timeText = if (item.optBoolean("birthday")) {
                String.format(Locale.getDefault(), "%02d %s", item.optInt("day"), listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec").getOrElse(item.optInt("month")) { "?" })
            } else formatter.format(Date(item.optLong("time")))
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(10))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(49, 24, 57), Color.rgb(29, 17, 41))
                ).apply {
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.rgb(105, 55, 112))
                }
                elevation = dp(2).toFloat()
            }
            card.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(white); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            card.addView(TextView(this).apply { text = "$type\n$timeText\n" + item.optString("message"); textSize = 12f; setTextColor(muted) })
            card.addView(makeButton("DELETE") {
                val updated = JSONArray()
                for (j in 0 until all.length()) if (all.optJSONObject(j)?.optString("id") != id) updated.put(all.getJSONObject(j))
                prefs.edit().putString("items", updated.toString()).apply()
                ReminderScheduler.cancel(this, id)
                refreshList()
            })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            card.alpha = 0f
            card.translationY = dp(8).toFloat()
            card.animate().alpha(1f).translationY(0f).setStartDelay((i * 45L).coerceAtMost(270L))
                .setDuration(260L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        letterSpacing = 0.02f
        minHeight = dp(46)
        stateListAnimator = null
        setPadding(dp(10), dp(4), dp(10), dp(4))
        val primary = label.startsWith("+")
        setTextColor(if (primary) Color.rgb(35, 10, 29) else white)
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(Color.rgb(255, 164, 207), Color.rgb(188, 119, 255))
            else intArrayOf(Color.rgb(53, 25, 61), Color.rgb(34, 20, 48))
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (primary) Color.rgb(255, 193, 224) else Color.rgb(102, 61, 112))
        }
        elevation = dp(2).toFloat()
        setOnClickListener {
            animate().cancel()
            scaleX = 0.97f
            scaleY = 0.97f
            animate().scaleX(1f).scaleY(1f).setDuration(150L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()
            action()
        }
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.rgb(30, 13, 39))
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.rgb(171, 80, 177))
        })
        dialog.window?.setDimAmount(0.72f)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.apply {
            setTextColor(white)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(muted)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("nandini_reminders", "NANDINI reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Important dates, daily schedules, and birthday wishes"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
