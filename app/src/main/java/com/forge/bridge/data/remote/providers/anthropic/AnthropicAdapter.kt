package com.forge.bridge.data.remote.providers.anthropic

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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicAdapter @Inject constructor(
    private val client: HttpClient,
    private val vault: VaultManager,
    private val json: Json
) : ProviderAdapter {

    override val providerId = "anthropic-api"
    override val providerName = "Anthropic (API)"
    override val tier = "API"
    override val availableModels = listOf(
        "claude-sonnet-4-20250514",
        "claude-opus-4-20250514",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022"
    )
    override val features = listOf("chat", "stream", "vision")

    private val baseUrl = "https://api.anthropic.com/v1"
    private val apiVersion = "2023-06-01"

    override suspend fun isAuthenticated(): Boolean = !vault.getApiKey(providerId).isNullOrBlank()

    private fun apiKeyOrThrow(): String =
        vault.getApiKey(providerId) ?: error("Anthropic API key not set")

    private fun splitSystemAndMessages(messages: List<UnifiedMessage>): Pair<String?, List<AnthropicMessage>> {
        val sys = messages.firstOrNull { it.role == "system" }?.content
        val rest = messages.filter { it.role != "system" }
            .map { AnthropicMessage(if (it.role == "assistant") "assistant" else "user", it.content) }
        return sys to rest
    }

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        val key = apiKeyOrThrow()
        val (sys, msgs) = splitSystemAndMessages(request.messages)
        val resp: AnthropicResponse = HttpRetry.withRetry {
            client.post("$baseUrl/messages") {
                contentType(ContentType.Application.Json)
                headers {
                    append("x-api-key", key)
                    append("anthropic-version", apiVersion)
                }
                setBody(
                    AnthropicRequest(
                        model = request.model,
                        messages = msgs,
                        system = sys,
                        maxTokens = request.maxTokens?.takeIf { it > 0 } ?: 4096,
                        temperature = request.temperature,
                        stream = false
                    )
                )
            }.body()
        }
        val text = resp.content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
        return UnifiedChatResponse(
            id = resp.id,
            model = resp.model,
            choices = listOf(
                UnifiedChoice(
                    index = 0,
                    message = UnifiedMessage("assistant", text),
                    finishReason = resp.stopReason
                )
            ),
            usage = resp.usage?.let {
                UnifiedUsage(it.inputTokens, it.outputTokens, it.inputTokens + it.outputTokens)
            }
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk> = flow {
        val key = apiKeyOrThrow()
        val (sys, msgs) = splitSystemAndMessages(request.messages)
        val streamId = "msg_${UUID.randomUUID()}"
        client.preparePost("$baseUrl/messages") {
            contentType(ContentType.Application.Json)
            headers {
                append("x-api-key", key)
                append("anthropic-version", apiVersion)
                append(HttpHeaders.Accept, "text/event-stream")
            }
            setBody(
                AnthropicRequest(
                    model = request.model,
                    messages = msgs,
                    system = sys,
                    maxTokens = request.maxTokens?.takeIf { it > 0 } ?: 4096,
                    temperature = request.temperature,
                    stream = true
                )
            )
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val event = runCatching { json.decodeFromString<AnthropicStreamEvent>(payload) }
                    .getOrNull() ?: continue
                when (event.type) {
                    "content_block_delta" -> {
                        val text = event.delta?.text ?: continue
                        emit(
                            UnifiedStreamChunk(
                                id = streamId,
                                model = request.model,
                                choices = listOf(
                                    UnifiedStreamChoice(
                                        index = 0,
                                        delta = UnifiedDelta(content = text)
                                    )
                                )
                            )
                        )
                    }
                    "message_delta" -> {
                        val stop = event.delta?.stopReason ?: continue
                        emit(
                            UnifiedStreamChunk(
                                id = streamId,
                                model = request.model,
                                choices = listOf(
                                    UnifiedStreamChoice(
                                        index = 0,
                                        delta = UnifiedDelta(content = ""),
                                        finishReason = stop
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
