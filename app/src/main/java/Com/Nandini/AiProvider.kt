package com.dipayan.nandini

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content

/**
 * Stable boundary between ARUSHI's conversation engine and the model that answers it.
 * Providers can be added or replaced without rewriting chat history, UI, memory or tools.
 */
internal data class AiProviderRequest(
    val systemInstruction: String,
    val previousMessages: List<Pair<String, String>>,
    val userText: String
)

internal data class AiProviderResponse(
    val text: String,
    val searchWidgetHtml: String? = null,
    val sources: List<Pair<String, String>> = emptyList()
)

internal interface AiProvider {
    val id: String
    suspend fun generate(
        request: AiProviderRequest,
        onChunk: (String) -> Unit = {}
    ): AiProviderResponse
}

/**
 * Provider selection is deliberately separate from chat UI and app features.
 * Cloud reasoning remains the default; the compact on-device model is opt-in only.
 */
internal object AiProviderRouter {
    fun primary(context: Context): AiProvider =
        if (LocalAiRuntime.isEnabled(context)) LocalLiteRtProvider(context)
        else FirebaseGeminiProvider()
}

private class LocalLiteRtProvider(
    private val context: Context
) : AiProvider {
    override val id: String = "local_litert"

    override suspend fun generate(
        request: AiProviderRequest,
        onChunk: (String) -> Unit
    ): AiProviderResponse {
        val answer = LocalAiRuntime.generateReply(
            context,
            request.systemInstruction,
            request.previousMessages,
            request.userText,
            onChunk
        )
        return AiProviderResponse(text = answer)
    }
}

private class FirebaseGeminiProvider : AiProvider {
    override val id: String = "firebase_gemini"

    override suspend fun generate(
        request: AiProviderRequest,
        onChunk: (String) -> Unit
    ): AiProviderResponse {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content { text(request.systemInstruction) },
                tools = listOf(Tool.googleSearch())
            )

        val savedHistory = request.previousMessages
            .dropLast(1)
            .filter { it.second.isNotBlank() }
            .map { (role, body) ->
                content(role = role) { text(body) }
            }

        val chat = model.startChat(history = savedHistory)
        val answer = StringBuilder()
        var searchWidgetHtml: String? = null
        val sources = linkedMapOf<String, String>()

        chat.sendMessageStream(request.userText).collect { chunk ->
            val piece = chunk.text.orEmpty()
            if (piece.isNotEmpty()) {
                answer.append(piece)
                onChunk(piece)
            }

            val grounding = chunk.candidates.firstOrNull()?.groundingMetadata
            if (grounding != null) {
                searchWidgetHtml =
                    grounding.searchEntryPoint?.renderedContent ?: searchWidgetHtml
                grounding.groundingChunks.orEmpty().forEach { groundedChunk ->
                    val web = groundedChunk.web
                    val title = web?.title.orEmpty()
                    val uri = web?.uri.orEmpty()
                    if (uri.startsWith("https://") || uri.startsWith("http://")) {
                        sources[uri] = title.ifBlank { uri }
                    }
                }
            }
        }

        val finalText = answer.toString().trim()
        if (finalText.isBlank()) {
            throw IllegalStateException(
                "The AI service returned an empty response. Please try again."
            )
        }

        return AiProviderResponse(
            text = finalText,
            searchWidgetHtml = searchWidgetHtml,
            sources = sources.map { (uri, title) -> title to uri }
        )
    }
}
