package com.dipayan.nandini

import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Transparent, local 3D companion renderer.
 *
 * The native chat remains responsible for AI, microphone, camera and TTS.
 * This WebView only renders the bundled CC0 VRM avatar and reacts to the
 * existing idle/listening/thinking/speaking state callbacks.
 */
class Anime3DView(
    context: Context,
    private val chatBackgroundMode: Boolean = false
) : WebView(context) {

    @Volatile private var companionState: String = "idle"
    @Volatile private var pageReady = false

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageReady = true
                pushStateToRenderer()
            }
        }

        // AndroidX's asset loader gives bundled HTML/model files a safe HTTPS origin.
        loadUrl("https://appassets.androidplatform.net/assets/companion.html")
    }

    fun setSpeaking(value: Boolean) {
        setCompanionState(if (value) "speaking" else "idle")
    }

    fun setCompanionState(value: String) {
        companionState = when (value.lowercase()) {
            "listening", "thinking", "speaking" -> value.lowercase()
            else -> "idle"
        }
        post { pushStateToRenderer() }
    }

    private fun pushStateToRenderer() {
        if (!pageReady) return
        val state = companionState
        evaluateJavascript(
            "window.setCompanionState && window.setCompanionState('$state')",
            null
        )
    }

    override fun onDetachedFromWindow() {
        pageReady = false
        stopLoading()
        super.onDetachedFromWindow()
    }
}
