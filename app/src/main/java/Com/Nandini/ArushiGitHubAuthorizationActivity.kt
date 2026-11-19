package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** GitHub Device Flow authorization and branch-scoped repository settings. */
class ArushiGitHubAuthorizationActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clientId: EditText
    private lateinit var repository: EditText
    private lateinit var branch: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
        val p = ArushiExperience.palette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(p.background)
        }
        root.addView(TextView(this).apply {
            text = "ARUSHI  •  GITHUB DEVELOPER TOOLS"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(p.accent)
        })
        root.addView(label("OAuth App client ID (public identifier, not a secret)", p))
        clientId = field("GitHub OAuth App client ID", p).apply { setText(ArushiGitHubClient.clientId(this@ArushiGitHubAuthorizationActivity)) }
        root.addView(clientId)
        root.addView(label("Repository owner/name", p))
        repository = field("owner/repository", p).apply {
            setText(ArushiGitHubClient.repository(this@ArushiGitHubAuthorizationActivity).ifBlank { "dipayan900/NANDINI-Android." })
        }
        root.addView(repository)
        root.addView(label("Dedicated working branch (main/master writes are blocked)", p))
        branch = field("feature/your-branch", p).apply {
            setText(ArushiGitHubClient.branch(this@ArushiGitHubAuthorizationActivity))
        }
        root.addView(branch)
        root.addView(action("SAVE REPOSITORY SETTINGS", p) { saveConfig() })
        root.addView(action("CONNECT GITHUB ACCOUNT", p) { connect() })
        root.addView(action("TEST REPOSITORY ACCESS", p) {
            scope.launch {
                try {
                    saveConfig()
                    val result = withContext(Dispatchers.IO) { ArushiGitHubClient.testRepository(this@ArushiGitHubAuthorizationActivity) }
                    status.text = "Connected: ${result.optString("full_name")} • default branch ${result.optString("default_branch")}"
                } catch (e: Exception) { status.text = e.message ?: "GitHub access test failed." }
            }
        })
        root.addView(action("DISCONNECT GITHUB", p) {
            ArushiGitHubClient.disconnect(this)
            status.text = "GitHub access token cleared from this device."
        })
        status = TextView(this).apply {
            text = if (ArushiGitHubClient.isConnected(this@ArushiGitHubAuthorizationActivity)) "GitHub token stored securely on this device." else "Not connected."
            textSize = 13f
            setTextColor(p.muted)
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "GitHub requires an OAuth App with Device Flow enabled. ARUSHI never asks for your GitHub password. Repository writes and workflow runs are restricted to the selected non-main branch and require a separate approval in the AI Task Execution screen."
            textSize = 12f
            setTextColor(p.muted)
        })
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(action("BACK") { finish() })
        setContentView(scroll)
    }

    private fun saveConfig() {
        ArushiGitHubClient.saveConfig(this, clientId.text.toString().trim(), repository.text.toString().trim(), branch.text.toString().trim())
        Toast.makeText(this, "GitHub settings saved.", Toast.LENGTH_SHORT).show()
    }

    private fun connect() {
        try { saveConfig() } catch (e: Exception) { status.text = e.message ?: "Invalid settings."; return }
        status.text = "Requesting GitHub device authorization…"
        scope.launch {
            try {
                val code = withContext(Dispatchers.IO) {
                    ArushiGitHubClient.requestDeviceCode(clientId.text.toString().trim())
                }
                AlertDialog.Builder(this@ArushiGitHubAuthorizationActivity)
                    .setTitle("Authorize ARUSHI on GitHub")
                    .setMessage("Open ${code.verificationUri}\n\nEnter this one-time code:\n${code.userCode}\n\nReturn here after approving.")
                    .setPositiveButton("OPEN GITHUB") { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(code.verificationUri)))
                        poll(code)
                    }
                    .setNeutralButton("COPY CODE") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitHub device code", code.userCode))
                        poll(code)
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            } catch (e: Exception) { status.text = e.message ?: "Could not start GitHub authorization." }
        }
    }

    private fun poll(code: ArushiGitHubClient.DeviceCode) {
        status.text = "Waiting for GitHub authorization…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ArushiGitHubClient.pollForToken(this@ArushiGitHubAuthorizationActivity, clientId.text.toString().trim(), code)
                }
                status.text = "GitHub connected. Repository access is ready."
            } catch (e: Exception) { status.text = e.message ?: "GitHub authorization failed." }
        }
    }

    private fun field(hintText: String, p: ArushiExperience.Palette) = EditText(this).apply {
        hint = hintText
        setTextColor(p.text)
        setHintTextColor(p.muted)
        setSingleLine(true)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun label(text: String, p: ArushiExperience.Palette) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(p.accentSecondary)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun action(text: String, p: ArushiExperience.Palette, click: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(p.text)
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
