package com.dipayan.nandini

import android.app.Application
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores uncaught Java/Kotlin crashes locally so device-only failures can be diagnosed. */
class ArushiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(filesDir, "arushi_last_crash.txt").writeText(
                    "ARUSHI crash report\nTime: $stamp\nThread: ${thread.name}\n\n${writer}"
                )
            } catch (_: Throwable) {
                // Never prevent Android's normal crash handling if report writing fails.
            }
            previous?.uncaughtException(thread, error) ?: run {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
