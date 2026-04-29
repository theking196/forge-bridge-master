package com.forge.bridge.data.remote.providers.gemini

import com.forge.bridge.data.local.VaultManager
import com.forge.bridge.data.remote.api.HttpRetry
import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.api.UnifiedChatResponse
import com.forge.bridge.data.remote.api.UnifiedChoice
import com.forge.bridge.data.remote.api.UnifiedDelta
import com.forge.bridge.data.remote.api.UnifiedMessage
import com.forge.bridge.data.remote.api.UnifiedStreamChoice
import com.forge.bridge.data.remote.api.UnifiedStreamChunk
import com.forge.bridge.data.remote.api.UnifiedUsage
import com.forge.bridge.data.remote.providers.ProviderAdapter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAdapter @Inject constructor(
    private val client: HttpClient,
    private val vault: VaultManager,
    private val json: Json
) : ProviderAdapter {

    override val providerId = "gemini-api"
    override val providerName = "Gemini (API)"
    override val tier = "API"
    override val availableModels = listOf(
        "gemini-2.5-pro", "gemini-2.5-flash", "gemini-1.5-pro", "gemini-1.5-flash"
    )
    override val features = listOf("chat", "stream", "vision")

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    override suspend fun isAuthenticated(): Boolean = !vault.getApiKey(providerId).isNullOrBlank()

    private fun apiKeyOrThrow(): String =
        vault.getApiKey(providerId) ?: error("Gemini API key not set")

    /**
     * Gemini doesn't have a "system" role on the messages array — it has a
     * dedicated `system_instruction`. Map "assistant" to "model" since that's
     * what the Gemini API expects.
     */
    private fun toGemini(messages: List<UnifiedMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        val sys = messages.firstOrNull { it.role == "system" }?.let {
            GeminiContent(role = null, parts = listOf(GeminiPart(it.content)))
        }
        val rest = messages.filter { it.role != "system" }.map {
            val role = if (it.role == "assistant") "model" else "user"
            GeminiContent(role = role, parts = listOf(GeminiPart(it.content)))
        }
        return sys to rest
    }

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        val key = apiKeyOrThrow()
        val (sys, contents) = toGemini(request.messages)
        val resp: GeminiResponse = HttpRetry.withRetry {
            client.post("$baseUrl/${request.model}:generateContent?key=$key") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        contents = contents,
                        systemInstruction = sys,
                        generationConfig = GeminiGenerationConfig(
                            temperature = request.temperature,
                            maxOutputTokens = request.maxTokens?.takeIf { it > 0 },
                            topP = request.topP
                        )
                    )
                )
            }.body()
        }
        val candidate = resp.candidates.firstOrNull()
        val text = candidate?.content?.parts?.joinToString("") { it.text }.orEmpty()
        return UnifiedChatResponse(
            id = "gemini_${UUID.randomUUID()}",
            model = resp.modelVersion ?: request.model,
            choices = listOf(
                UnifiedChoice(
                    index = 0,
                    message = UnifiedMessage("assistant", text),
                    finishReason = candidate?.finishReason
                )
            ),
            usage = resp.usageMetadata?.let {
                UnifiedUsage(it.promptTokenCount, it.candidatesTokenCount, it.totalTokenCount)
            }
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk> = flow {
        val key = apiKeyOrThrow()
        val (sys, contents) = toGemini(request.messages)
        val streamId = "gemini_${UUID.randomUUID()}"
        client.preparePost("$baseUrl/${request.model}:streamGenerateContent?alt=sse&key=$key") {
            contentType(ContentType.Application.Json)
            setBody(
                GeminiRequest(
                    contents = contents,
                    systemInstruction = sys,
                    generationConfig = GeminiGenerationConfig(
                        temperature = request.temperature,
                        maxOutputTokens = request.maxTokens?.takeIf { it > 0 },
                        topP = request.topP
                    )
                )
            )
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val event = runCatching { json.decodeFromString<GeminiResponse>(payload) }
                    .getOrNull() ?: continue
                val candidate = event.candidates.firstOrNull() ?: continue
                val text = candidate.content?.parts?.joinToString("") { it.text }.orEmpty()
                if (text.isNotEmpty() || candidate.finishReason != null) {
                    emit(
                        UnifiedStreamChunk(
                            id = streamId,
                            model = event.modelVersion ?: request.model,
                            choices = listOf(
                                UnifiedStreamChoice(
                                    index = candidate.index,
                                    delta = UnifiedDelta(content = text),
                                    finishReason = candidate.finishReason
                                )
                            )
                        )
                    )
                }
            }
        }
    }
}
