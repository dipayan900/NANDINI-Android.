package com.dipayan.nandini

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Optional on-device chat engine. The model is downloaded into app-private storage,
 * never bundled into the APK. CPU is used as the conservative default for compatibility.
 */
object LocalAiRuntime {
    const val PREFS = "arushi_local_ai"
    const val MODEL_NAME = "Qwen3-0.6B.litertlm"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
    private const val MODEL_SHA256 =
        "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"

    private val engineMutex = Mutex()
    @Volatile private var engine: Engine? = null

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "local_models"), MODEL_NAME)

    /** Cloud AI is the default. The small offline model is used only when explicitly selected. */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("use_local_model", false) &&
            prefs.getBoolean("enabled", false) && isModelReady(context)
    }

    fun setLocalModelSelected(context: Context, selected: Boolean) {
        check(!selected || isModelReady(context)) {
            "Download the on-device model before selecting offline AI."
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("use_local_model", selected)
            .apply()
    }

    fun isModelReady(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > 500L * 1024L * 1024L
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        if (isModelReady(context) && sha256(target).equals(MODEL_SHA256, true)) {
            return@withContext target
        }

        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${MODEL_NAME}.part")
        partial.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 25_000
            readTimeout = 90_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ARUSHI-Android")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Model download failed (HTTP $status).")
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            connection.inputStream.buffered().use { input ->
                FileOutputStream(partial).buffered().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }

            val actualHash = digest.digest().joinToString("") {
                "%02x".format(Locale.ROOT, it)
            }
            if (!actualHash.equals(MODEL_SHA256, true)) {
                throw IllegalStateException(
                    "Model integrity check failed. The partial download was discarded."
                )
            }

            if (target.exists() && !target.delete()) {
                throw IllegalStateException("Couldn't replace the old model file.")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("model_path", target.absolutePath)
                .putBoolean("enabled", true)
                .apply()
            target
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    suspend fun prewarm(context: Context) = withContext(Dispatchers.IO) {
        if (!isModelReady(context)) return@withContext
        engineMutex.withLock {
            if (engine == null) {
                val model = modelFile(context)
                engine = Engine(
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).also { it.initialize() }
            }
        }
    }

    suspend fun generateReply(
        context: Context,
        systemInstruction: String,
        previousMessages: List<Pair<String, String>>,
        userText: String,
        onChunk: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val model = modelFile(context)
            check(model.isFile && model.length() > 500L * 1024L * 1024L) {
                "The local model is not installed. Open Studio → Local AI Engine and download it first."
            }

            val activeEngine = engine ?: Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            ).also {
                it.initialize()
                engine = it
            }

            val initialMessages = previousMessages.takeLast(14).mapNotNull { (role, text) ->
                if (text.isBlank()) null
                else if (role == "user") Message.user(text)
                else Message.model(text)
            }

            val conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    initialMessages = initialMessages,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                    maxOutputToken = 1024
                )
            )

            try {
                val answer = StringBuilder()
                conversation.sendMessageAsync(userText).collect { chunk ->
                    answer.append(chunk.toString())
                    onChunk(chunk.toString())
                }
                answer.toString().trim().ifBlank {
                    "I couldn't produce a local reply. Try a shorter prompt."
                }
            } finally {
                conversation.close()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(Locale.ROOT, it)
        }
    }
}

/** Simple in-app model installer; no storage permission is needed. */
class LocalAiSetupActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button
    private val palette get() = ArushiExperience.palette(this)
    private val screenBackground get() = palette.background
    private val panel get() = palette.panel
    private val accent get() = palette.accent
    private val muted get() = palette.muted

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(screenBackground)
        }
        root.addView(TextView(this).apply {
            text = "ARUSHI • LOCAL AI"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(54)))

        root.addView(TextView(this).apply {
            text = "OFFLINE CHAT MODEL"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, dp(32)))

        root.addView(TextView(this).apply {
            text = "Qwen3 0.6B • Apache 2.0 • approximately 614 MB\n\nThe model downloads once and stays in ARUSHI's private storage. Chat can then run without Gemini or an internet connection. The APK does not contain the model. The first download needs Wi-Fi or mobile data and enough free storage.\n\nThis is a compact mobile model, so answer quality and speed will differ from larger cloud models."
            textSize = 14f
            setTextColor(muted)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackground(android.graphics.drawable.GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(18).toFloat()
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            text = if (LocalAiRuntime.isModelReady(this@LocalAiSetupActivity)) {
                "Model downloaded. Local chat is ready."
            } else {
                "Model not installed yet."
            }
            setPadding(0, dp(20), 0, dp(10))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = if (LocalAiRuntime.isModelReady(this@LocalAiSetupActivity)) 100 else 0
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply {
            topMargin = dp(8)
            bottomMargin = dp(18)
        })

        downloadButton = Button(this).apply {
            text = if (LocalAiRuntime.isModelReady(this@LocalAiSetupActivity)) {
                "RE-DOWNLOAD MODEL"
            } else {
                "DOWNLOAD LOCAL MODEL • 614 MB"
            }
            setOnClickListener { startModelDownload() }
        }
        root.addView(downloadButton, LinearLayout.LayoutParams(-1, dp(52)))

        root.addView(Button(this).apply {
            text = "EXPERIENCE & APPEARANCE"
            setOnClickListener { startActivity(Intent(this@LocalAiSetupActivity, ArushiExperienceSettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })

        root.addView(Button(this).apply {
            text = "BACK TO STUDIO"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        setContentView(root)
    }

    private fun startModelDownload() {
        downloadButton.isEnabled = false
        progress.progress = 0
        status.text = "Connecting to the model host…"
        activityScope.launch {
            try {
                LocalAiRuntime.downloadModel(this@LocalAiSetupActivity) { downloaded, total ->
                    runOnUiThread {
                        if (total > 0L) {
                            progress.progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            status.text = "Downloading model: ${formatMb(downloaded)} / ${formatMb(total)}"
                        } else {
                            status.text = "Downloaded ${formatMb(downloaded)}…"
                        }
                    }
                }
                progress.progress = 100
                status.text = "Verified and installed. Local chat is now enabled."
                downloadButton.text = "MODEL INSTALLED"
                Toast.makeText(this@LocalAiSetupActivity, "Local AI is ready.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                status.text = "Download failed: ${e.message ?: "Please try again."}"
                downloadButton.isEnabled = true
            }
        }
    }

    private fun formatMb(bytes: Long): String =
        String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
