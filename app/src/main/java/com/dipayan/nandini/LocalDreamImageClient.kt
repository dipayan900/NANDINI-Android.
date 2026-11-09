package com.dipayan.nandini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Image generation client for ARUSHI's bundled LocalDream runtime.
 * It starts and manages the native engine itself; no separate app is required.
 */
object LocalDreamImageClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.MINUTES)
        .callTimeout(8, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    fun checkHealth(): Boolean = EmbeddedLocalDreamRuntime.isHealthy()

    fun generate(context: Context, prompt: String, sourceImage: Bitmap? = null): Bitmap {
        require(prompt.isNotBlank()) { "Enter an image prompt first." }
        val modelId = LocalDreamModelStore.selectedModelId(context)
            ?: throw IllegalStateException("Open Model Manager and import/select a compatible SD 1.5 MNN model first.")

        EmbeddedLocalDreamRuntime.ensureRunning(context, modelId)

        val payload = JSONObject()
            .put("prompt", prompt.trim())
            .put("steps", 6)
            .put("width", 512)
            .put("height", 512)
            .put("output_format", "png")

        if (sourceImage != null) {
            val scaled = Bitmap.createScaledBitmap(sourceImage, 512, 512, true)
            val bytes = ByteArrayOutputStream().use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Could not encode the selected image.")
                }
                output.toByteArray()
            }
            if (scaled !== sourceImage && !scaled.isRecycled) scaled.recycle()
            payload.put("image", Base64.encodeToString(bytes, Base64.NO_WRAP))
            payload.put("denoise_strength", 0.6)
        }

        val request = Request.Builder()
            .url("${EmbeddedLocalDreamRuntime.BASE_URL}/generate")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(500).orEmpty()
                throw IllegalStateException(
                    "The embedded image engine returned HTTP ${response.code}. " +
                        detail.ifBlank { "Check the imported model package and try again." }
                )
            }

            val body = response.body
                ?: throw IllegalStateException("The embedded image engine returned an empty response.")
            val source = body.source()
            var currentEvent = ""
            while (true) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> currentEvent = line.substringAfter(':').trim()
                    line.startsWith("data:") -> {
                        val raw = line.substringAfter(':').trim()
                        if (raw.isBlank()) continue
                        val data = try { JSONObject(raw) } catch (_: Exception) { continue }
                        val type = data.optString("type", currentEvent)
                        when {
                            currentEvent == "error" || type == "error" -> {
                                throw IllegalStateException(
                                    data.optString("message").ifBlank { "Image generation failed." }
                                )
                            }
                            currentEvent == "complete" || type == "complete" -> {
                                val encoded = data.optString("image")
                                if (encoded.isBlank()) {
                                    throw IllegalStateException("Generation completed without an image.")
                                }
                                val imageBytes = try {
                                    Base64.decode(encoded, Base64.DEFAULT)
                                } catch (error: IllegalArgumentException) {
                                    throw IllegalStateException("The engine returned invalid image data.", error)
                                }
                                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    ?: throw IllegalStateException("The generated image could not be decoded.")
                            }
                        }
                    }
                }
            }
            throw IllegalStateException("The embedded image engine closed the generation stream unexpectedly.")
        }
    }
}
