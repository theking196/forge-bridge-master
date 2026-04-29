package com.forge.bridge.data.remote.providers.openai

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
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiAdapter @Inject constructor(
    private val client: HttpClient,
    private val vault: VaultManager,
    private val json: Json
) : ProviderAdapter {

    override val providerId = "openai-api"
    override val providerName = "OpenAI (API)"
    override val tier = "API"
    override val availableModels = listOf(
        "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1-preview", "o1-mini"
    )
    override val features = listOf("chat", "stream", "vision")

    private val baseUrl = "https://api.openai.com/v1"

    override suspend fun isAuthenticated(): Boolean = !vault.getApiKey(providerId).isNullOrBlank()

    private fun apiKeyOrThrow(): String =
        vault.getApiKey(providerId) ?: error("OpenAI API key not set")

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        val key = apiKeyOrThrow()
        val resp: OpenAiChatResponse = HttpRetry.withRetry {
            client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                headers { append(HttpHeaders.Authorization, "Bearer $key") }
                setBody(
                    OpenAiChatRequest(
                        model = request.model,
                        messages = request.messages.map { OpenAiMessage(it.role, it.content) },
                        temperature = request.temperature,
                        maxTokens = request.maxTokens?.takeIf { it > 0 },
                        stream = false
                    )
                )
            }.body()
        }
        return UnifiedChatResponse(
            id = resp.id,
            model = resp.model,
            choices = resp.choices.map {
                UnifiedChoice(
                    index = it.index,
                    message = UnifiedMessage(it.message.role, it.message.content),
                    finishReason = it.finishReason
                )
            },
            usage = resp.usage?.let {
                UnifiedUsage(it.promptTokens, it.completionTokens, it.totalTokens)
            }
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk> = flow {
        val key = apiKeyOrThrow()
        client.preparePost("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $key")
                append(HttpHeaders.Accept, "text/event-stream")
            }
            setBody(
                OpenAiChatRequest(
                    model = request.model,
                    messages = request.messages.map { OpenAiMessage(it.role, it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens?.takeIf { it > 0 },
                    stream = true
                )
            )
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]" || payload.isEmpty()) continue
                val chunk = runCatching { json.decodeFromString<OpenAiStreamChunk>(payload) }
                    .getOrNull() ?: continue
                emit(
                    UnifiedStreamChunk(
                        id = chunk.id,
                        model = chunk.model ?: request.model,
                        choices = chunk.choices.map {
                            UnifiedStreamChoice(
                                index = it.index,
                                delta = UnifiedDelta(it.delta.role, it.delta.content),
                                finishReason = it.finishReason
                            )
                        }
                    )
                )
            }
        }
    }
}
