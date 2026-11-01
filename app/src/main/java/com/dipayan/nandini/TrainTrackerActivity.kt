package com.dipayan.nandini

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * ARUSHI Train Tracker.
 *
 * Railway data is displayed from official Indian Railways / CRIS enquiry pages.
 * No unofficial API key, PNR harvesting, or locally cached passenger data is used.
 */
class TrainTrackerActivity : Activity() {
    private val bg = Color.rgb(5, 7, 22)
    private val panel = Color.rgb(14, 18, 43)
    private val accent = Color.rgb(177, 105, 255)
    private val blue = Color.rgb(83, 190, 255)
    private val muted = Color.rgb(190, 190, 215)

    private lateinit var page: WebView
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var tabRow: LinearLayout

    private data class Tool(val title: String, val url: String, val hint: String)

    private val tools = listOf(
        Tool("LIVE TRAIN", "https://enquiry.indianrail.gov.in/ntes/", "Spot a train by number/name and view its reported running position, previous/next station, delay and expected timings."),
        Tool("BETWEEN STATIONS", "https://enquiry.indianrail.gov.in/ntes/", "Find trains between two stations. Use the official NTES journey search for the travel date and available services."),
        Tool("LIVE STATION", "https://enquiry.indianrail.gov.in/ntes/", "Check expected arrivals and departures at a station."),
        Tool("TRAIN SCHEDULE", "https://enquiry.indianrail.gov.in/ntes/", "View a train's route, halts and scheduled arrival/departure times."),
        Tool("PNR STATUS", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en", "Check the current reservation status on the official Indian Railways PNR enquiry page.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()
        selectTool(0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun card(color: Int, stroke: Int = accent) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    private fun actionButton(value: String, selected: Boolean = false, onTap: () -> Unit) =
        TextView(this).apply {
            text = value
            textSize = 10f
            letterSpacing = .025f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = card(if (selected) 0xFF382052.toInt() else panel, if (selected) blue else accent)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
        }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(8), dp(14), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(text("ARUSHI  /  RAIL", 20f, accent, true))
        titleStack.addView(text("NATIONAL TRAIN ENQUIRY • LIVE DATA", 9f, blue, true))
        top.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("CLOSE") { finish() })
        root.addView(top)

        root.addView(text("TRAIN TRACKER", 11f, Color.WHITE, true).apply {
            setPadding(dp(2), dp(13), dp(2), dp(6))
        })

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabScroll.addView(tabRow)
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(48)))

        pageStatus = text("Connecting to official Indian Railways enquiry…", 10f, muted)
        pageStatus.setPadding(dp(3), dp(7), dp(3), dp(7))
        root.addView(pageStatus)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        page = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val host = uri.host.orEmpty().lowercase()
                    val allowed = host == "indianrail.gov.in" ||
                        host.endsWith(".indianrail.gov.in") ||
                        host == "cris.org.in" || host.endsWith(".cris.org.in")
                    if (allowed) return false
                    Toast.makeText(this@TrainTrackerActivity, "External page blocked for your safety.", Toast.LENGTH_SHORT).show()
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progress.visibility = View.VISIBLE
                    pageStatus.text = "Loading official railway enquiry…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                    pageStatus.text = "Official railway enquiry page • verify the last-updated time shown in results"
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    progress.visibility = View.GONE
                    pageStatus.text = "Railway website could not load. Check your internet connection and tap the selected tab to retry."
                }
            }
        }
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(text(
            "LIVE INFORMATION IS PROVIDED BY INDIAN RAILWAYS / CRIS. PNR details are entered directly on the official railway page and are not saved by ARUSHI.",
            9f, muted
        ).apply { setPadding(dp(3), dp(8), dp(3), dp(3)) })

        setContentView(root)
        rebuildTabs(0)
    }

    private fun rebuildTabs(selected: Int) {
        tabRow.removeAllViews()
        tools.forEachIndexed { index, tool ->
            val button = actionButton(tool.title, index == selected) { selectTool(index) }
            tabRow.addView(button, LinearLayout.LayoutParams(-2, -1).apply {
                marginEnd = dp(7)
            })
        }
    }

    private fun selectTool(index: Int) {
        val tool = tools[index.coerceIn(tools.indices)]
        rebuildTabs(index)
        pageStatus.text = tool.hint
        page.loadUrl(tool.url)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::page.isInitialized && page.canGoBack()) page.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::page.isInitialized) {
            page.stopLoading()
            page.loadUrl("about:blank")
            page.destroy()
        }
        super.onDestroy()
    }
}
