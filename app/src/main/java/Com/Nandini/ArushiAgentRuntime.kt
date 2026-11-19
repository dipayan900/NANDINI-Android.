package com.dipayan.nandini

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Bounded, app-owned agent execution boundary.
 *
 * A model may propose a tool name and JSON arguments, but it cannot create tools or execute
 * arbitrary shell/code. Only tools registered by trusted app code can run. Every tool declares
 * its permission requirements and whether each invocation needs an explicit approval.
 */
internal object ArushiAgentRuntime {
    data class Tool(
        val name: String,
        val description: String,
        val requiredPermissions: Set<String> = emptySet(),
        val requiresApproval: Boolean = true,
        val handler: (Context, JSONObject) -> JSONObject
    )

    data class ToolResult(
        val success: Boolean,
        val message: String,
        val output: JSONObject = JSONObject()
    )

    data class Progress(
        val taskId: String,
        val stepId: String,
        val status: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private const val STORE_KEY = "agent.tasks.v1"
    private val tools = linkedMapOf<String, Tool>()

    @Synchronized
    fun register(tool: Tool) {
        require(tool.name.matches(Regex("[a-z][a-z0-9_.-]{1,63}"))) { "Invalid tool name" }
        require(tool.description.isNotBlank())
        tools[tool.name] = tool
    }

    @Synchronized
    fun registeredTools(): List<Pair<String, String>> =
        tools.values.map { it.name to it.description }

    fun createTask(context: Context, title: String, goal: String, proposedToolCalls: List<Pair<String, JSONObject>>): String {
        require(title.isNotBlank() && title.length <= 160) { "Task title is required" }
        require(goal.length <= 8000 && proposedToolCalls.size <= 30) { "Task is too large" }
        val taskId = UUID.randomUUID().toString()
        val steps = JSONArray()
        proposedToolCalls.forEachIndexed { index, call ->
            val tool = synchronized(this) { tools[call.first] }
                ?: throw IllegalArgumentException("Unregistered tool: ${call.first}")
            steps.put(JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("order", index)
                .put("tool", tool.name)
                .put("arguments", call.second)
                .put("status", if (tool.requiresApproval) "WAITING_APPROVAL" else "APPROVED")
                .put("approved", !tool.requiresApproval)
                .put("message", if (tool.requiresApproval) "Waiting for review" else "Pre-authorized by app policy"))
        }
        val tasks = readTasks(context)
        tasks.put(JSONObject()
            .put("id", taskId)
            .put("title", title)
            .put("goal", goal)
            .put("status", if (steps.length() == 0) "NEEDS_PLAN" else "AWAITING_APPROVAL")
            .put("createdAt", System.currentTimeMillis())
            .put("updatedAt", System.currentTimeMillis())
            .put("steps", steps)
            .put("audit", JSONArray().put(audit("TASK_CREATED", "Task created: $title"))))
        writeTasks(context, tasks)
        return taskId
    }

    fun approveStep(context: Context, taskId: String, stepId: String): Boolean =
        mutateStep(context, taskId, stepId) { step ->
            step.put("approved", true).put("status", "APPROVED")
                .put("message", "User approved this exact tool invocation")
        }

    fun rejectStep(context: Context, taskId: String, stepId: String): Boolean =
        mutateStep(context, taskId, stepId) { step ->
            step.put("approved", false).put("status", "REJECTED")
                .put("message", "User rejected this tool invocation")
        }

    fun cancelTask(context: Context, taskId: String): Boolean {
        val tasks = readTasks(context)
        val task = findTask(tasks, taskId) ?: return false
        task.put("status", "CANCELLED").put("updatedAt", System.currentTimeMillis())
        appendAudit(task, "TASK_CANCELLED", "Task cancelled by user")
        writeTasks(context, tasks)
        return true
    }

    /**
     * Runs only approved steps. Permission strings must be supplied by the Android integration
     * after checking the current user-granted permissions; this method never prompts for them.
     */
    fun executeApproved(
        context: Context,
        taskId: String,
        grantedPermissions: Set<String>,
        onProgress: (Progress) -> Unit = {}
    ): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        val initial = findTask(readTasks(context), taskId)
            ?: return listOf(ToolResult(false, "Task not found"))
        val steps = initial.optJSONArray("steps") ?: JSONArray()
        updateTaskStatus(context, taskId, "RUNNING", "Execution started")
        for (index in 0 until steps.length()) {
            val latestTask = findTask(readTasks(context), taskId)
            if (latestTask?.optString("status") == "CANCELLED") {
                onProgress(Progress(taskId, "", "CANCELLED", "Task cancelled by user"))
                break
            }
            val step = steps.optJSONObject(index) ?: continue
            val stepId = step.optString("id")
            val toolName = step.optString("tool")
            val tool = synchronized(this) { tools[toolName] }
            if (tool == null) {
                val result = ToolResult(false, "Tool is no longer registered: $toolName")
                recordStep(context, taskId, stepId, "FAILED", result.message)
                results += result
                onProgress(Progress(taskId, stepId, "FAILED", result.message))
                break
            }
            if (step.optString("status") == "REJECTED") continue
            if (!step.optBoolean("approved", false)) {
                val message = "Approval required before running $toolName"
                updateStep(context, taskId, stepId, "WAITING_APPROVAL", message)
                onProgress(Progress(taskId, stepId, "WAITING_APPROVAL", message))
                updateTaskStatus(context, taskId, "AWAITING_APPROVAL", message)
                break
            }
            val missing = tool.requiredPermissions - grantedPermissions
            if (missing.isNotEmpty()) {
                val message = "Missing user-granted permissions: ${missing.sorted().joinToString()}"
                recordStep(context, taskId, stepId, "BLOCKED", message)
                val result = ToolResult(false, message)
                results += result
                onProgress(Progress(taskId, stepId, "BLOCKED", message))
                updateTaskStatus(context, taskId, "BLOCKED", message)
                break
            }
            updateStep(context, taskId, stepId, "RUNNING", "Running approved tool")
            onProgress(Progress(taskId, stepId, "RUNNING", "Running $toolName"))
            val result = try {
                val args = step.optJSONObject("arguments") ?: JSONObject()
                ToolResult(true, "Completed", tool.handler(context.applicationContext, args))
            } catch (error: SecurityException) {
                ToolResult(false, "Permission denied: ${error.message ?: "restricted operation"}")
            } catch (error: Exception) {
                ToolResult(false, "Tool failed: ${error.message ?: error.javaClass.simpleName}")
            }
            results += result
            val status = if (result.success) "COMPLETED" else "FAILED"
            recordStep(context, taskId, stepId, status, result.message)
            onProgress(Progress(taskId, stepId, status, result.message))
            if (!result.success) {
                updateTaskStatus(context, taskId, "FAILED", result.message)
                break
            }
        }
        val finalTask = findTask(readTasks(context), taskId)
        val allDone = (finalTask?.optJSONArray("steps")?.let { arr ->
            (0 until arr.length()).all { arr.optJSONObject(it)?.optString("status") in setOf("COMPLETED", "REJECTED") }
        } ?: false)
        if (allDone) updateTaskStatus(context, taskId, "COMPLETED", "All approved steps finished")
        return results
    }

    fun taskSnapshot(context: Context): JSONArray = readTasks(context)

    private fun mutateStep(context: Context, taskId: String, stepId: String, mutate: (JSONObject) -> Unit): Boolean {
        val tasks = readTasks(context)
        val task = findTask(tasks, taskId) ?: return false
        val steps = task.optJSONArray("steps") ?: return false
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("id") == stepId) {
                mutate(step)
                task.put("updatedAt", System.currentTimeMillis())
                appendAudit(task, "STEP_REVIEWED", "Step $stepId: ${step.optString("status")}")
                writeTasks(context, tasks)
                return true
            }
        }
        return false
    }

    private fun updateStep(context: Context, taskId: String, stepId: String, status: String, message: String) {
        mutateStep(context, taskId, stepId) { it.put("status", status).put("message", message) }
    }

    private fun recordStep(context: Context, taskId: String, stepId: String, status: String, message: String) {
        mutateStep(context, taskId, stepId) { it.put("status", status).put("message", message) }
    }

    private fun updateTaskStatus(context: Context, taskId: String, status: String, message: String) {
        val tasks = readTasks(context)
        val task = findTask(tasks, taskId) ?: return
        task.put("status", status).put("updatedAt", System.currentTimeMillis())
        appendAudit(task, "TASK_$status", message)
        writeTasks(context, tasks)
    }

    private fun appendAudit(task: JSONObject, type: String, message: String) {
        val audit = task.optJSONArray("audit") ?: JSONArray()
        audit.put(audit(type, message))
        while (audit.length() > 500) audit.remove(0)
        task.put("audit", audit)
    }

    private fun audit(type: String, message: String) = JSONObject()
        .put("type", type)
        .put("message", message.take(1000))
        .put("timestamp", System.currentTimeMillis())

    private fun readTasks(context: Context): JSONArray =
        try { JSONArray(ArushiSecureMemory.get(context, STORE_KEY) ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun writeTasks(context: Context, tasks: JSONArray) {
        ArushiSecureMemory.put(context, STORE_KEY, tasks.toString())
    }

    private fun findTask(tasks: JSONArray, taskId: String): JSONObject? {
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            if (task.optString("id") == taskId) return task
        }
        return null
    }
}
