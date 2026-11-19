package com.dipayan.nandini

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * App-owned allow-list of tools the model may propose.
 * The model never supplies executable code; it can only select these names and JSON arguments.
 */
internal object ArushiAgentTools {
    private const val NOTES_KEY = "agent.notes.v1"

    @Synchronized
    fun install(context: Context) {
        val installed = ArushiAgentRuntime.registeredTools().map { it.first }.toSet()
        if ("memory.note.save" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "memory.note.save",
                    description = "Save a user-approved note with title and content to encrypted local memory",
                    requiresApproval = true
                ) { app, args ->
                    val title = args.optString("title").trim().take(120)
                    val body = args.optString("content").trim().take(6000)
                    require(title.isNotBlank() && body.isNotBlank()) { "A note needs a title and content." }
                    val notes = readNotes(app)
                    val id = UUID.randomUUID().toString()
                    notes.put(JSONObject()
                        .put("id", id)
                        .put("title", title)
                        .put("content", body)
                        .put("createdAt", System.currentTimeMillis()))
                    while (notes.length() > 100) notes.remove(0)
                    ArushiSecureMemory.put(app, NOTES_KEY, notes.toString())
                    JSONObject().put("noteId", id).put("title", title)
                }
            )
        }
        if ("memory.note.search" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "memory.note.search",
                    description = "Search encrypted local notes using a query string",
                    requiresApproval = true
                ) { app, args ->
                    val query = args.optString("query").trim()
                    require(query.isNotBlank()) { "Enter a search query." }
                    val matches = JSONArray()
                    val notes = readNotes(app)
                    for (i in 0 until notes.length()) {
                        val note = notes.optJSONObject(i) ?: continue
                        val haystack = note.optString("title") + " " + note.optString("content")
                        if (haystack.contains(query, ignoreCase = true)) {
                            matches.put(JSONObject()
                                .put("title", note.optString("title"))
                                .put("content", note.optString("content").take(1000)))
                        }
                    }
                    JSONObject().put("matches", matches)
                }
            )
        }
        if ("reminder.create" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "reminder.create",
                    description = "Create a local Android reminder. Arguments: title, message, timeMillis, daily",
                    requiredPermissions = setOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    requiresApproval = true
                ) { app, args ->
                    val title = args.optString("title").trim().take(120)
                    val message = args.optString("message", title).trim().take(500)
                    val time = args.optLong("timeMillis")
                    val daily = args.optBoolean("daily", false)
                    require(title.isNotBlank()) { "Reminder title is required." }
                    require(time > System.currentTimeMillis() + 30_000L) { "Reminder time must be in the future." }
                    val id = UUID.randomUUID().toString()
                    val prefs = app.getSharedPreferences("nandini_reminders", Context.MODE_PRIVATE)
                    val all = try { JSONArray(prefs.getString("items", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
                    val item = JSONObject()
                        .put("id", id)
                        .put("title", title)
                        .put("message", message.ifBlank { title })
                        .put("time", time)
                        .put("daily", daily)
                        .put("birthday", false)
                    all.put(item)
                    check(prefs.edit().putString("items", all.toString()).commit()) { "Could not save reminder." }
                    ReminderScheduler.schedule(app, item)
                    JSONObject().put("reminderId", id).put("scheduledFor", time).put("daily", daily)
                }
            )
        }
        if ("github.file.read" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "github.file.read",
                    description = "Read a file from the GitHub repository and selected working branch configured in ARUSHI Developer Tools",
                    requiresApproval = true
                ) { app, args ->
                    val path = args.optString("path").trim()
                    require(path.isNotBlank()) { "Repository file path is required." }
                    val content = kotlinx.coroutines.runBlocking { ArushiGitHubClient.readFile(app, path) }
                    JSONObject().put("path", path).put("content", content)
                }
            )
        }
        if ("github.file.write" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "github.file.write",
                    description = "Create or update a file on the selected non-main GitHub working branch. Requires explicit approval.",
                    requiresApproval = true
                ) { app, args ->
                    val path = args.optString("path").trim()
                    val content = args.optString("content")
                    val message = args.optString("commitMessage").ifBlank { "ARUSHI approved repository update" }
                    val result = kotlinx.coroutines.runBlocking {
                        ArushiGitHubClient.writeFile(app, path, content, message)
                    }
                    JSONObject()
                        .put("contentUrl", result.optString("content").let { result.optJSONObject("content")?.optString("html_url").orEmpty() })
                        .put("commitSha", result.optJSONObject("commit")?.optString("sha").orEmpty())
                        .put("branch", ArushiGitHubClient.branch(app))
                }
            )
        }
        if ("github.workflow.dispatch" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "github.workflow.dispatch",
                    description = "Dispatch a GitHub Actions workflow on the selected non-main working branch. Requires explicit approval.",
                    requiresApproval = true
                ) { app, args ->
                    val workflow = args.optString("workflow").trim()
                    val inputs = args.optJSONObject("inputs") ?: JSONObject()
                    val result = kotlinx.coroutines.runBlocking {
                        ArushiGitHubClient.dispatchWorkflow(app, workflow, inputs)
                    }
                    JSONObject().put("success", true).put("workflow", workflow)
                        .put("branch", ArushiGitHubClient.branch(app))
                }
            )
        }
        if ("device.open_url" !in installed) {
            ArushiAgentRuntime.register(
                ArushiAgentRuntime.Tool(
                    name = "device.open_url",
                    description = "Open an HTTP or HTTPS web page after the user approves its exact URL",
                    requiresApproval = true
                ) { app, args ->
                    val raw = args.optString("url").trim()
                    val uri = Uri.parse(raw)
                    require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) {
                        "Only valid HTTP or HTTPS URLs can be opened."
                    }
                    app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    JSONObject().put("opened", uri.toString())
                }
            )
        }
    }

    suspend fun plan(context: Context, goal: String): String {
        install(context)
        require(goal.isNotBlank() && goal.length <= 4000) { "Describe the task in 1–4000 characters." }
        val allowed = ArushiAgentRuntime.registeredTools()
        val allowedText = allowed.joinToString("\n") { "- ${it.first}: ${it.second}" }
        val response = AiProviderRouter.primary(context).generate(
            AiProviderRequest(
                systemInstruction = "You are ARUSHI's task planner. Return ONLY valid JSON with keys title and steps. steps is an array of objects with keys tool and arguments. Choose only from this allow-list:\n$allowedText\nNever output code, shell commands, arbitrary functions, or unknown tool names. If the goal cannot be completed by these tools, return an empty steps array and explain briefly in title. For reminders, timeMillis must be a future Unix timestamp in milliseconds based on the supplied current time and timezone.",
                previousMessages = emptyList(),
                userText = "Current time: ${System.currentTimeMillis()}\nTimezone: ${java.util.TimeZone.getDefault().id}\nUser goal: $goal"
            )
        )
        val start = response.text.indexOf('{')
        val end = response.text.lastIndexOf('}')
        require(start >= 0 && end > start) { "ARUSHI could not create a structured plan. Try describing the task more clearly." }
        val json = JSONObject(response.text.substring(start, end + 1))
        val proposed = json.optJSONArray("steps") ?: JSONArray()
        require(proposed.length() <= 20) { "The plan contains too many steps." }
        val calls = mutableListOf<Pair<String, JSONObject>>()
        for (i in 0 until proposed.length()) {
            val step = proposed.optJSONObject(i) ?: continue
            val name = step.optString("tool")
            val args = step.optJSONObject("arguments") ?: JSONObject()
            require(allowed.any { it.first == name }) { "ARUSHI proposed an unregistered tool: $name" }
            calls += name to args
        }
        return ArushiAgentRuntime.createTask(
            context,
            json.optString("title").ifBlank { goal.take(100) },
            goal,
            calls
        )
    }

    private fun readNotes(context: Context): JSONArray =
        try { JSONArray(ArushiSecureMemory.get(context, NOTES_KEY) ?: "[]") }
        catch (_: Exception) { JSONArray() }
}
