package com.dipayan.nandini

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * ARUSHI Rail: a themed native shell for official Indian Railways / CRIS enquiry pages.
 * Live position, ETA/ETD, schedules, station boards and PNR are served by the railway sites.
 * ARUSHI does not scrape undocumented endpoints or store PNR values.
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
    private var selectedIndex = 0

    private data class Tool(val title: String, val url: String, val hint: String)

    private val ntesUrl = "https://enquiry.indianrail.gov.in/mntes/"
    private val tools = listOf(
        Tool("SPOT TRAIN", ntesUrl,
            "In NTES choose Spot Your Train, enter the train number and journey start date to see last reported station, running position and expected arrival/departure."),
        Tool("LIVE STATION", ntesUrl,
            "In NTES choose Live Station and enter a station code/name to view expected arrivals and departures for the available time window."),
        Tool("SCHEDULE", ntesUrl,
            "In NTES choose Train Schedule to view the train's route, stoppages and scheduled arrival/departure times."),
        Tool("TIME + ROUTE", "https://www.indianrail.gov.in/enquiry/TBIS/TrainBetweenImportantStations.jsp",
            "Enter journey date, source and destination on the official page. Compare the listed departure and arrival times. The official public page does not expose a separate time-only filter."),
        Tool("PNR STATUS", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en",
            "Enter your PNR directly on the official railway page. ARUSHI does not save or transmit it to any separate service.")
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
        titleStack.addView(text("INDIAN RAILWAYS • OFFICIAL ENQUIRY", 9f, blue, true))
        top.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("↻ REFRESH") { reloadPage() }, LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(6) })
        top.addView(actionButton("CLOSE") { finish() })
        root.addView(top)

        root.addView(text("TRAIN TRACKER", 11f, Color.WHITE, true).apply {
            setPadding(dp(2), dp(13), dp(2), dp(6))
        })

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
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
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                private fun isAllowed(uri: android.net.Uri): Boolean {
                    val host = uri.host.orEmpty().lowercase()
                    return host == "indianrail.gov.in" ||
                        host.endsWith(".indianrail.gov.in") ||
                        host == "cris.org.in" ||
                        host.endsWith(".cris.org.in") ||
                        host == "maps.google.com" ||
                        (host == "www.google.com" && uri.path.orEmpty().startsWith("/maps"))
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (isAllowed(uri)) return false
                    Toast.makeText(this@TrainTrackerActivity, "This external page was blocked.", Toast.LENGTH_SHORT).show()
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progress.visibility = View.VISIBLE
                    pageStatus.text = "Loading official railway enquiry…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                    pageStatus.text = tools[selectedIndex].hint
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        progress.visibility = View.GONE
                        pageStatus.text = "Railway enquiry could not load. Check your connection and tap REFRESH."
                    }
                }
            }
        }
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(text(
            "DATA SOURCE: INDIAN RAILWAYS / CRIS • RUNNING POSITION IS THE LAST RAILWAY-REPORTED UPDATE, NOT A PHONE GPS TRACK. PNR IS ENTERED ON THE OFFICIAL PAGE ONLY.",
            9f, muted
        ).apply { setPadding(dp(3), dp(8), dp(3), dp(3)) })

        setContentView(root)
        rebuildTabs()
    }

    private fun rebuildTabs() {
        tabRow.removeAllViews()
        tools.forEachIndexed { index, tool ->
            val button = actionButton(tool.title, index == selectedIndex) { selectTool(index) }
            tabRow.addView(button, LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(7) })
        }
    }

    private fun selectTool(index: Int) {
        selectedIndex = index.coerceIn(tools.indices)
        val tool = tools[selectedIndex]
        rebuildTabs()
        pageStatus.text = tool.hint
        if (selectedIndex == tools.lastIndex) {
            page.clearHistory()
            page.clearFormData()
        }
        page.loadUrl(tool.url)
    }

    private fun reloadPage() {
        if (::page.isInitialized) page.reload()
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
