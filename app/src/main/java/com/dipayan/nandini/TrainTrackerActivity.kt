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
 * ARUSHI Rail: a themed native shell for official Indian Railways / CRIS enquiries.
 * Live running position, station boards, schedules and PNR are supplied by official
 * railway services. ARUSHI does not fabricate live locations or store PNR values.
 */
class TrainTrackerActivity : Activity() {
    private val palette get() = ArushiExperience.palette(this)
    private val bg get() = palette.background
    private val panel get() = palette.panel
    private val accent get() = palette.accent
    private val blue get() = palette.accentSecondary
    private val muted get() = palette.muted

    private lateinit var page: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var tabRow: LinearLayout
    private var selectedTool = 0

    private data class Tool(
        val title: String,
        val url: String,
        val description: String,
        val status: String
    )

    private val ntes = "https://enquiry.indianrail.gov.in/ntes/"
    private val tools = listOf(
        Tool(
            "SPOT TRAIN", ntes,
            "Enter a train number and journey date in NTES to view its current reported position, last reported station, delay and expected arrival / departure at upcoming stops.",
            "NTES • Spot Your Train"
        ),
        Tool(
            "LIVE STATION", ntes,
            "Choose a station and the 2, 4, 6 or 8 hour window to see expected arrivals, departures and platform information where published.",
            "NTES • Live Station board"
        ),
        Tool(
            "BETWEEN", "https://www.indianrail.gov.in/enquiry/TBIS/TrainBetweenImportantStations.jsp",
            "Search trains by journey date, source and destination. Review the returned services and their scheduled departure times.",
            "Indian Railways • Trains Between Stations"
        ),
        Tool(
            "SCHEDULE", ntes,
            "Open the official NTES train schedule option for route stoppages, scheduled arrival / departure, distance and day count.",
            "NTES • Train Schedule"
        ),
        Tool(
            "PNR STATUS", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en",
            "Enter the 10-digit PNR directly on the official Indian Railways page. ARUSHI does not collect or save it.",
            "Indian Railways • PNR enquiry"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
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
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bg, 0xFF10152E.toInt(), bg)
            )
            setPadding(dp(14), dp(9), dp(14), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleStack.addView(text("✦  ARUSHI RAIL", 21f, accent, true))
        titleStack.addView(text("INDIAN RAILWAYS • LIVE ENQUIRY", 9f, blue, true))
        top.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("↻ REFRESH") { if (::page.isInitialized) page.reload() })
        top.addView(actionButton("CLOSE") { finish() }, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(6)
        })
        root.addView(top)

        root.addView(text("TRAIN INTELLIGENCE", 11f, Color.WHITE, true).apply {
            setPadding(dp(2), dp(13), dp(2), dp(6))
        })

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabScroll.addView(tabRow)
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(47)))

        pageStatus = text("Connecting to official railway enquiry…", 10f, muted)
        pageStatus.setPadding(dp(3), dp(7), dp(3), dp(4))
        root.addView(pageStatus)

        loadingIndicator = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        root.addView(loadingIndicator, LinearLayout.LayoutParams(-1, dp(3)))

        page = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
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
                    val official = host == "indianrail.gov.in" ||
                        host.endsWith(".indianrail.gov.in") ||
                        host == "cris.org.in" || host.endsWith(".cris.org.in")
                    if (official) return false
                    Toast.makeText(this@TrainTrackerActivity, "This external page was blocked.", Toast.LENGTH_SHORT).show()
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    loadingIndicator.alpha = 1f
                    pageStatus.text = "Connecting to official railway service…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingIndicator.alpha = 0f
                    pageStatus.text = tools[selectedTool].status + " • Check the enquiry's update time"
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) showLoadError()
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    showLoadError()
                }
            }
        }
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(text(
            "LIVE DATA SOURCE: INDIAN RAILWAYS / CRIS. Search train number, station, journey date or PNR on the official enquiry page. PNR values are not stored by ARUSHI.",
            9f, muted
        ).apply { setPadding(dp(3), dp(8), dp(3), dp(3)) })

        setContentView(root)
        rebuildTabs(0)
    }

    private fun showLoadError() {
        loadingIndicator.alpha = 0f
        pageStatus.text = "Railway enquiry could not load. Check your internet connection and tap the selected tab to retry."
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
        selectedTool = index.coerceIn(tools.indices)
        val tool = tools[selectedTool]
        rebuildTabs(selectedTool)
        pageStatus.text = tool.description
        if (::page.isInitialized) page.loadUrl(tool.url)
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
