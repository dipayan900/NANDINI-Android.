package com.dipayan.nandini

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
 * Same-device LocalDream client. LocalDream owns 127.0.0.1:8081;
 * ARUSHI is only an HTTP client and never starts/stops the server.
 */
object LocalDreamImageClient {
    private const val BASE_URL = "http://127.0.0.1:8081"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.MINUTES)
        .callTimeout(8, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    fun checkHealth(): Boolean {
        val request = Request.Builder().url("$BASE_URL/health").get().build()
        return healthClient.newCall(request).execute().use { it.isSuccessful }
    }

    /**
     * Waits for LocalDream's Server-Sent Events stream to emit its complete
     * event, then decodes the returned PNG/JPEG bytes into a Bitmap.
     * Supplying sourceImage requests img2img; that requires backend support.
     */
    fun generate(prompt: String, sourceImage: Bitmap? = null): Bitmap {
        require(prompt.isNotBlank()) { "Enter an image prompt first." }

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
            .url("$BASE_URL/generate")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(500).orEmpty()
                throw IllegalStateException("LocalDream returned HTTP ${response.code}. ${detail.ifBlank { "Check that its model and server are running." }}")
            }

            val body = response.body ?: throw IllegalStateException("LocalDream returned an empty response.")
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
                                throw IllegalStateException(data.optString("message").ifBlank { "LocalDream generation failed." })
                            }
                            currentEvent == "complete" || type == "complete" -> {
                                val encoded = data.optString("image")
                                if (encoded.isBlank()) throw IllegalStateException("LocalDream completed without returning an image.")
                                val imageBytes = try { Base64.decode(encoded, Base64.DEFAULT) }
                                    catch (e: IllegalArgumentException) { throw IllegalStateException("LocalDream returned invalid image data.", e) }
                                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    ?: throw IllegalStateException("The generated image could not be decoded.")
                            }
                        }
                    }
                }
            }
            throw IllegalStateException("LocalDream closed the generation stream before completion. Keep its app open and check that the model is loaded.")
        }
    }
}
