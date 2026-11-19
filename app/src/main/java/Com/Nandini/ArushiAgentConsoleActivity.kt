package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import org.json.JSONArray
import org.json.JSONObject

/** User-facing plan, approval, execution and audit console for the bounded ARUSHI agent. */
class ArushiAgentConsoleActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val palette get() = ArushiExperience.palette(this)
    private val bg get() = palette.background
    private val panel get() = palette.panel
    private val accent get() = palette.accent
    private val ink get() = palette.text
    private val muted get() = palette.muted
    private lateinit var rootContent: LinearLayout
    private lateinit var status: TextView
    private lateinit var goalInput: EditText
    private var currentTaskId: String? = null
    private var pendingRunTaskId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
        ArushiAgentTools.install(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "ARUSHI  •  TASK EXECUTION"
            textSize = 20f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        goalInput = EditText(this).apply {
            hint = "Describe what you want ARUSHI to do…"
            setTextColor(ink)
            setHintTextColor(muted)
            minLines = 3
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(goalInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(actionButton("✦  PLAN WITH ARUSHI") { createPlan() })
        status = TextView(this).apply {
            text = "ARUSHI can currently save/search encrypted notes, create reminders and open approved web links. Each proposed action needs your approval."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        rootContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rootContent)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actionButton("← BACK TO COMMAND CENTER") { finish() })
        setContentView(root)
        renderLatestTask()
    }

    private fun createPlan() {
        val goal = goalInput.text.toString().trim()
        if (goal.isBlank()) {
            Toast.makeText(this, "Describe a task first.", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "ARUSHI is preparing a structured plan…"
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { ArushiAgentTools.plan(applicationContext, goal) }
                currentTaskId = id
                status.text = "Plan created. Review every proposed action below before running it."
                renderTask(id)
            } catch (error: Exception) {
                status.text = "Planning failed: ${error.message ?: "Unknown error"}"
            }
        }
    }

    private fun renderLatestTask() {
        val tasks = ArushiAgentRuntime.taskSnapshot(this)
        if (tasks.length() == 0) return
        val task = tasks.optJSONObject(tasks.length() - 1) ?: return
        currentTaskId = task.optString("id")
        renderTask(currentTaskId!!)
    }

    private fun renderTask(taskId: String) {
        rootContent.removeAllViews()
        val tasks = ArushiAgentRuntime.taskSnapshot(this)
        var task: JSONObject? = null
        for (i in 0 until tasks.length()) {
            val item = tasks.optJSONObject(i) ?: continue
            if (item.optString("id") == taskId) { task = item; break }
        }
        val selected = task ?: run {
            status.text = "Task not found."
            return
        }
        addLabel(selected.optString("title", "ARUSHI task"), 16f, accent, true)
        addLabel("Status: ${selected.optString("status")}\nGoal: ${selected.optString("goal")}", 12f, muted)
        val steps = selected.optJSONArray("steps") ?: JSONArray()
        if (steps.length() == 0) addLabel("No supported tool steps were generated. Try a goal involving a reminder, encrypted note, note search or opening a web page.", 13f, muted)
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            val stepId = step.optString("id")
            val toolName = step.optString("tool")
            val args = step.optJSONObject("arguments") ?: JSONObject()
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(panel)
            }
            block.addView(TextView(this).apply {
                text = "${i + 1}. $toolName\n${args.toString(2)}\n\n${step.optString("status")}: ${step.optString("message")}"
                textSize = 12f
                setTextColor(ink)
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val approve = Button(this).apply {
                text = "APPROVE"
                isEnabled = !step.optBoolean("approved") && step.optString("status") != "REJECTED"
                setOnClickListener {
                    ArushiAgentRuntime.approveStep(this@ArushiAgentConsoleActivity, taskId, stepId)
                    renderTask(taskId)
                }
            }
            val reject = Button(this).apply {
                text = "REJECT"
                isEnabled = step.optString("status") != "REJECTED" && step.optString("status") != "COMPLETED"
                setOnClickListener {
                    ArushiAgentRuntime.rejectStep(this@ArushiAgentConsoleActivity, taskId, stepId)
                    renderTask(taskId)
                }
            }
            actions.addView(approve, LinearLayout.LayoutParams(0, dp(44), 1f))
            actions.addView(reject, LinearLayout.LayoutParams(0, dp(44), 1f))
            block.addView(actions)
            rootContent.addView(block, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        rootContent.addView(actionButton("▶  RUN APPROVED STEPS") { runTask(taskId) })
        rootContent.addView(actionButton("◷  VIEW AUDIT HISTORY") { showAudit(selected) })
        rootContent.addView(actionButton("■  CANCEL TASK") {
            ArushiAgentRuntime.cancelTask(this, taskId)
            status.text = "Task cancelled."
            renderTask(taskId)
        })
    }

    private fun runTask(taskId: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            hasReminderStep(taskId)
        ) {
            pendingRunTaskId = taskId
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
            return
        }
        executeTask(taskId)
    }

    private fun hasReminderStep(taskId: String): Boolean {
        val tasks = ArushiAgentRuntime.taskSnapshot(this)
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            if (task.optString("id") != taskId) continue
            val steps = task.optJSONArray("steps") ?: return false
            for (j in 0 until steps.length()) if (steps.optJSONObject(j)?.optString("tool") == "reminder.create") return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 901) {
            val taskId = pendingRunTaskId ?: return
            pendingRunTaskId = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) executeTask(taskId)
            else status.text = "Notification permission was not granted. Reminder action remains blocked."
        }
    }

    private fun executeTask(taskId: String) {
        status.text = "Executing approved steps…"
        scope.launch {
            val granted = buildSet {
                if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            try {
                val results = withContext(Dispatchers.IO) {
                    ArushiAgentRuntime.executeApproved(applicationContext, taskId, granted) { progress ->
                        scope.launch(Dispatchers.Main) {
                            status.text = "${progress.status}: ${progress.message}"
                        }
                    }
                }
                status.text = results.lastOrNull()?.message ?: "No approved actions were ready to execute."
                renderTask(taskId)
            } catch (error: Exception) {
                status.text = "Execution failed: ${error.message ?: "Unknown error"}"
            }
        }
    }

    private fun showAudit(task: JSONObject) {
        val audit = task.optJSONArray("audit") ?: JSONArray()
        val lines = (0 until audit.length()).mapNotNull { audit.optJSONObject(it) }
            .joinToString("\n\n") {
                "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.optLong("timestamp")))}\n${it.optString("type")}: ${it.optString("message")}"
            }
        AlertDialog.Builder(this).setTitle("Task audit history")
            .setMessage(lines.ifBlank { "No audit events." })
            .setPositiveButton("CLOSE", null).show()
    }

    private fun addLabel(value: String, size: Float, color: Int, bold: Boolean = false) {
        rootContent.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(7), 0, dp(7))
        })
    }

    private fun actionButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
