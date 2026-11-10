package com.dipayan.nandini

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Starts the bundled filtered LocalDream ARM64 engine as a child process owned
 * by ARUSHI. The loopback endpoint is private to this app; no second app/server
 * needs to be installed or started by the user.
 */
object EmbeddedLocalDreamRuntime {
    private const val TAG = "ARUSHI_LOCAL_ENGINE"
    const val PORT = 18081
    const val BASE_URL = "http://127.0.0.1:$PORT"
    private const val EXECUTABLE_NAME = "libstable_diffusion_core.so"

    @Volatile private var process: Process? = null
    @Volatile private var servingModel: String? = null

    @Synchronized
    fun ensureRunning(context: Context, modelId: String) {
        val app = context.applicationContext
        val model = LocalDreamModelStore.installedModels(app).firstOrNull { it.id == modelId }
            ?: throw IllegalStateException("Import a compatible SD 1.5 model in ARUSHI first.")

        if (process?.isAlive == true && servingModel == modelId && isHealthy()) return
        stopLocked()

        val executable = File(app.applicationInfo.nativeLibraryDir, EXECUTABLE_NAME)
        if (!executable.isFile) {
            throw IllegalStateException("The embedded image engine is missing from this ARUSHI build.")
        }
        executable.setExecutable(true, true)

        val logFile = File(app.filesDir, "localdream/engine.log")
        logFile.parentFile?.mkdirs()
        val command = listOf(
            executable.absolutePath,
            "--type", "sd15cpu",
            "--model_dir", model.directory.absolutePath,
            "--port", PORT.toString()
            // removed: "--safety_checker", safetyChecker.absolutePath
        )
        val nativeDir = app.applicationInfo.nativeLibraryDir
        val envVars = mapOf(
            "LD_LIBRARY_PATH" to "$nativeDir:/system/lib64:/vendor/lib64:/vendor/lib64/egl"
        )

        try {
            val builder = ProcessBuilder(command)
                .directory(app.filesDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            builder.environment().putAll(envVars)
            process = builder.start()
            servingModel = modelId

            val deadline = System.currentTimeMillis() + 120_000L
            while (System.currentTimeMillis() < deadline) {
                if (process?.isAlive != true) {
                    val detail = logFile.takeIf { it.exists() }?.readText()?.takeLast(1800).orEmpty()
                    stopLocked()
                    throw IllegalStateException("The embedded engine stopped while loading the model. $detail")
                }
                if (isHealthy()) return
                Thread.sleep(500L)
            }
            val detail = logFile.takeIf { it.exists() }?.readText()?.takeLast(1800).orEmpty()
            stopLocked()
            throw IllegalStateException("The image engine did not become ready in time. $detail")
        } catch (error: Exception) {
            stopLocked()
            if (error is IllegalStateException) throw error
            throw IllegalStateException("Could not start the embedded image engine: ${error.message}", error)
        }
    }

    @Synchronized
    fun stop() = stopLocked()

    fun isHealthy(): Boolean {
        return try {
            val connection = URL("$BASE_URL/health").openConnection() as HttpURLConnection
            connection.connectTimeout = 900
            connection.readTimeout = 900
            connection.requestMethod = "GET"
            try {
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun prepareSafetyChecker(context: Context): File {
        val directory = File(context.filesDir, "localdream")
        if (!directory.exists()) check(directory.mkdirs()) { "Could not create the engine data folder." }
        val destination = File(directory, "safety_checker.mnn")
        if (!destination.isFile || destination.length() == 0L) {
            context.assets.open("localdream/safety_checker.mnn").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(destination.isFile && destination.length() > 0L) {
            "The image safety checker is missing from this build."
        }
        return destination
    }

    @Synchronized
    private fun stopLocked() {
        val old = process
        process = null
        servingModel = null
        if (old != null) {
            try {
                old.destroy()
                if (!old.waitFor(1200L, TimeUnit.MILLISECONDS)) old.destroyForcibly()
            } catch (error: Exception) {
                Log.w(TAG, "Native engine shutdown failed", error)
                runCatching { old.destroyForcibly() }
            }
        }
    }
}
