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
    @Volatile private var hologramMode: String = "standard"

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
                pushEmotionToRenderer()
                pushHologramMode()
                setRendererVisible(isShown && windowVisibility == View.VISIBLE)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    pageReady = true
                    val message = "NANDINI renderer failed to load. Check your connection and reopen the companion."
                    val safeMessage = message.replace("'", "\\'")
                    view?.evaluateJavascript(
                        "window.__nandiniShowFallback && window.__nandiniShowFallback('$safeMessage')",
                        null
                    )
                    android.util.Log.w("NANDINI_3D", "Renderer page load failed: ${error?.description}")
                }
            }
        }

        // AndroidX's asset loader gives bundled HTML/model files a safe HTTPS origin.
        loadUrl("https://appassets.androidplatform.net/assets/companion.html")
    }

    /** Set renderer visibility while ARCore is acquiring or tracking a placed anchor. */
    fun setCompanionVisible(visible: Boolean) {
        post { setRendererVisible(visible) }
    }

    /** Position the existing VRM renderer at the ARCore-projected anchor. */
    fun setARPlacement(x: Float, y: Float, scale: Float, visible: Boolean) {
        val safeX = if (x.isFinite()) x.coerceIn(-3f, 3f) else 0f
        val safeY = if (y.isFinite()) y.coerceIn(-3f, 3f) else 0f
        val safeScale = if (scale.isFinite()) scale.coerceIn(0.25f, 3.5f) else 1f
        post {
            if (pageReady) {
                evaluateJavascript(
                    "window.setARPlacement && window.setARPlacement($safeX,$safeY,$safeScale,$visible)",
                    null
                )
            }
        }
    }

    /** Switch between the standard 3D stage and four-view projection output. */
    fun setHologramMode(value: String) {
        hologramMode = if (value.equals("pyramid", ignoreCase = true)) "pyramid" else "standard"
        post { pushHologramMode() }
    }

    private fun pushHologramMode() {
        if (!pageReady) return
        val mode = hologramMode
        evaluateJavascript("window.setHologramMode && window.setHologramMode('$mode')", null)
    }

    fun setSpeaking(value: Boolean) {
        setCompanionState(if (value) "speaking" else "idle")
    }

    @Volatile private var companionEmotion: String = "neutral"

    fun setCompanionState(value: String) {
        companionState = when (value.lowercase()) {
            "listening", "thinking", "speaking" -> value.lowercase()
            else -> "idle"
        }
        post { pushStateToRenderer() }
    }

    fun setCompanionEmotion(value: String) {
        companionEmotion = when (value.lowercase()) {
            "happy", "sad", "surprised", "serious", "calm" -> value.lowercase()
            else -> "neutral"
        }
        post { pushEmotionToRenderer() }
    }

    private fun pushStateToRenderer() {
        if (!pageReady) return
        val state = companionState
        evaluateJavascript(
            "window.setCompanionState && window.setCompanionState('$state')",
            null
        )
    }

    private fun pushEmotionToRenderer() {
        if (!pageReady) return
        val emotion = companionEmotion
        evaluateJavascript(
            "window.setCompanionEmotion && window.setCompanionEmotion('$emotion')",
            null
        )
    }

    private fun setRendererVisible(visible: Boolean) {
        if (!pageReady) return
        evaluateJavascript(
            "window.setCompanionVisible && window.setCompanionVisible($visible)",
            null
        )
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setRendererVisible(visibility == View.VISIBLE && isShown)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { setRendererVisible(isShown && windowVisibility == View.VISIBLE) }
    }

    override fun onDetachedFromWindow() {
        setRendererVisible(false)
        pageReady = false
        stopLoading()
        super.onDetachedFromWindow()
    }
}
