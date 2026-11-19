package com.dipayan.nandini

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** GitHub OAuth Device Flow and branch-scoped REST client. Tokens are stored only in Android Keystore-encrypted memory. */
internal object ArushiGitHubClient {
    private const val PREFS = "arushi_github"
    private const val TOKEN_KEY = "github.oauth.access_token"
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    fun saveConfig(context: Context, clientId: String, repository: String, branch: String) {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{10,100}"))) { "Enter a valid GitHub OAuth App client ID." }
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Repository must be owner/repo." }
        require(branch.isNotBlank() && branch.length <= 120 && branch !in setOf("main", "master")) {
            "Use a dedicated feature/fix branch, not main or master."
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("client_id", clientId)
            .putString("repository", repository)
            .putString("branch", branch)
            .apply()
    }

    fun clientId(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("client_id", "").orEmpty()
    fun repository(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("repository", "").orEmpty()
    fun branch(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("branch", "feature/arushi-independent-ai-core").orEmpty()
    fun isConnected(context: Context) = !ArushiSecureMemory.get(context, TOKEN_KEY).isNullOrBlank()

    fun disconnect(context: Context) {
        ArushiSecureMemory.delete(context, TOKEN_KEY)
    }

    suspend fun requestDeviceCode(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("scope", "repo workflow read:user")
        val result = postJson("https://github.com/login/device/code", body, null)
        DeviceCode(
            deviceCode = result.getString("device_code"),
            userCode = result.getString("user_code"),
            verificationUri = result.getString("verification_uri"),
            intervalSeconds = result.optInt("interval", 5).coerceAtLeast(5),
            expiresInSeconds = result.optInt("expires_in", 900).coerceAtLeast(60)
        )
    }

    suspend fun pollForToken(context: Context, clientId: String, code: DeviceCode): String = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        var interval = code.intervalSeconds
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L)
            val result = postJson(
                "https://github.com/login/oauth/access_token",
                JSONObject()
                    .put("client_id", clientId)
                    .put("device_code", code.deviceCode)
                    .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                null
            )
            val token = result.optString("access_token")
            if (token.isNotBlank()) {
                ArushiSecureMemory.put(context, TOKEN_KEY, token)
                return@withContext token
            }
            when (result.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("GitHub authorization code expired. Start again.")
                "access_denied" -> error("GitHub authorization was denied.")
                else -> error(result.optString("error_description", "GitHub authorization failed."))
            }
        }
        error("GitHub authorization timed out. Start again.")
    }

    suspend fun testRepository(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val repo = repository(context)
        require(repo.contains("/")) { "Set a repository first." }
        api(context, "GET", "/repos/$repo", null)
    }

    suspend fun readFile(context: Context, path: String): String = withContext(Dispatchers.IO) {
        val repo = repository(context)
        val branch = branch(context)
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..")) { "Invalid repository path." }
        val encoded = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val result = api(context, "GET", "/repos/$repo/contents/$encoded?ref=${URLEncoder.encode(branch, "UTF-8")}", null)
        val encodedContent = result.optString("content").replace("\n", "")
        String(Base64.decode(encodedContent, Base64.DEFAULT), Charsets.UTF_8)
    }

    suspend fun writeFile(context: Context, path: String, content: String, message: String): JSONObject = withContext(Dispatchers.IO) {
        val repo = repository(context)
        val branch = branch(context)
        require(branch !in setOf("main", "master")) { "Writes to main/master are blocked." }
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..")) { "Invalid repository path." }
        require(content.length <= 500_000) { "File content is too large." }
        require(message.isNotBlank() && message.length <= 120) { "A concise commit message is required." }
        val encoded = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val current = runCatching {
            api(context, "GET", "/repos/$repo/contents/$encoded?ref=${URLEncoder.encode(branch, "UTF-8")}", null)
        }.getOrNull()
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        current?.optString("sha")?.takeIf { it.isNotBlank() }?.let { body.put("sha", it) }
        api(context, "PUT", "/repos/$repo/contents/$encoded", body)
    }

    suspend fun workflowRuns(context: Context): JSONArray = withContext(Dispatchers.IO) {
        val repo = repository(context)
        val branch = branch(context)
        val result = api(context, "GET", "/repos/$repo/actions/runs?branch=${URLEncoder.encode(branch, "UTF-8")}&per_page=10", null)
        result.optJSONArray("workflow_runs") ?: JSONArray()
    }

    suspend fun dispatchWorkflow(context: Context, workflow: String, inputs: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val repo = repository(context)
        val branch = branch(context)
        require(branch !in setOf("main", "master")) { "Workflow dispatch on main/master is blocked." }
        require(workflow.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid workflow filename or ID." }
        val body = JSONObject().put("ref", branch).put("inputs", inputs)
        api(context, "POST", "/repos/$repo/actions/workflows/$workflow/dispatches", body)
    }

    private fun api(context: Context, method: String, path: String, body: JSONObject?): JSONObject {
        val token = ArushiSecureMemory.get(context, TOKEN_KEY)
            ?: error("Connect GitHub in ARUSHI Developer Tools first.")
        val requestBody = body?.toString()?.toRequestBody(jsonType)
        val request = Request.Builder()
            .url("https://api.github.com$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ARUSHI-Android")
            .method(method, if (method == "GET" || method == "DELETE") null else requestBody)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("GitHub API ${response.code}: ${runCatching { JSONObject(raw).optString("message") }.getOrDefault(raw).take(500)}")
            return if (raw.isBlank()) JSONObject().put("success", true) else JSONObject(raw)
        }
    }

    private fun postJson(url: String, body: JSONObject, token: String?): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ARUSHI-Android")
            .post(body.toString().toRequestBody(jsonType))
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("GitHub authorization failed: ${response.code} ${raw.take(300)}")
            return JSONObject(raw)
        }
    }
}
