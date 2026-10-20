package com.dipayan.nandini

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object ReminderScheduler {
    private const val PREFS = "nandini_reminders"
    private const val ITEMS = "items"

    fun schedule(context: Context, item: JSONObject) {
        val id = item.optString("id")
        val title = item.optString("title", "NANDINI reminder")
        val message = item.optString("message", title)
        val daily = item.optBoolean("daily")
        val birthday = item.optBoolean("birthday")
        var triggerAt = item.optLong("time", System.currentTimeMillis() + 60_000L)

        if (birthday) {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, item.optInt("month"))
                set(Calendar.DAY_OF_MONTH, item.optInt("day"))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!next.timeInMillis.let { it > System.currentTimeMillis() }) next.add(Calendar.YEAR, 1)
            triggerAt = next.timeInMillis
        } else if (daily) {
            val base = Calendar.getInstance().apply { timeInMillis = triggerAt }
            val now = System.currentTimeMillis()
            while (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
            triggerAt = base.timeInMillis
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.dipayan.nandini.REMINDER"
            putExtra("id", id)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("daily", daily)
            putExtra("birthday", birthday)
        }
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: Exception) {
            Log.e("NANDINI_REMINDER", "Could not schedule reminder", e)
        }
    }

    fun cancel(context: Context, id: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = "com.dipayan.nandini.REMINDER" }
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    fun rescheduleAll(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val all = JSONArray(prefs.getString(ITEMS, "[]") ?: "[]")
            for (i in 0 until all.length()) {
                val item = all.optJSONObject(i) ?: continue
                schedule(context, item)
            }
        } catch (e: Exception) {
            Log.e("NANDINI_REMINDER", "Could not restore reminders", e)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            ReminderScheduler.rescheduleAll(context)
            return
        }
        val id = intent?.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "NANDINI reminder"
        val message = intent.getStringExtra("message") ?: title
        val daily = intent.getBooleanExtra("daily", false)
        val birthday = intent.getBooleanExtra("birthday", false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel("nandini_reminders") == null) {
            manager.createNotificationChannel(android.app.NotificationChannel("nandini_reminders", "NANDINI reminders", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        val openApp = PendingIntent.getActivity(
            context, 9901,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, "nandini_reminders")
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (birthday) "🎂 Happy Birthday!" else title)
            .setContentText(message)
            .setStyle(android.app.Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try { manager.notify(id.hashCode(), notification) } catch (e: Exception) { Log.e("NANDINI_REMINDER", "Notification failed", e) }

        if (daily || birthday) {
            // Store the next trigger by rescheduling from the saved record.
            try {
                val prefs = context.getSharedPreferences("nandini_reminders", Context.MODE_PRIVATE)
                val all = JSONArray(prefs.getString("items", "[]") ?: "[]")
                for (i in 0 until all.length()) {
                    val item = all.optJSONObject(i) ?: continue
                    if (item.optString("id") == id) {
                        if (birthday) {
                            val nextYear = Calendar.getInstance().apply {
                                set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR) + 1)
                                set(Calendar.MONTH, item.optInt("month"))
                                set(Calendar.DAY_OF_MONTH, item.optInt("day"))
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }
                            item.put("time", nextYear.timeInMillis)
                        } else {
                            item.put("time", item.optLong("time") + 24L * 60L * 60L * 1000L)
                        }
                        prefs.edit().putString("items", all.toString()).apply()
                        ReminderScheduler.schedule(context, item)
                        break
                    }
                }
            } catch (e: Exception) { Log.e("NANDINI_REMINDER", "Could not repeat reminder", e) }
        } else {
            try {
                val prefs = context.getSharedPreferences("nandini_reminders", Context.MODE_PRIVATE)
                val old = JSONArray(prefs.getString("items", "[]") ?: "[]")
                val kept = JSONArray()
                for (i in 0 until old.length()) if (old.optJSONObject(i)?.optString("id") != id) kept.put(old.getJSONObject(i))
                prefs.edit().putString("items", kept.toString()).apply()
            } catch (e: Exception) { Log.e("NANDINI_REMINDER", "Could not remove completed event", e) }
        }
    }
}
