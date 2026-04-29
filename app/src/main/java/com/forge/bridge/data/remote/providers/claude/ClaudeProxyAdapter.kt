package com.forge.bridge.data.remote.providers.claude

import com.forge.bridge.data.local.VaultManager
import com.forge.bridge.data.remote.api.HttpRetry
import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.api.UnifiedChatResponse
import com.forge.bridge.data.remote.api.UnifiedChoice
import com.forge.bridge.data.remote.api.UnifiedDelta
import com.forge.bridge.data.remote.api.UnifiedMessage
import com.forge.bridge.data.remote.api.UnifiedStreamChoice
import com.forge.bridge.data.remote.api.UnifiedStreamChunk
import com.forge.bridge.data.remote.providers.ProviderAdapter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse-proxy adapter for claude.ai. Auth is a `sessionKey` cookie captured
 * via the in-app WebView. Conversation state is held under a mutex.
 */
@Singleton
class ClaudeProxyAdapter @Inject constructor(
    private val client: HttpClient,
    private val vault: VaultManager,
    private val json: Json
) : ProviderAdapter {

    override val providerId = "claude-proxy"
    override val providerName = "Claude (Proxy)"
    override val tier = "PROXY"
    override val availableModels = listOf("claude-sonnet-4", "claude-opus-4", "claude-3-5-sonnet")
    override val features = listOf("chat", "stream")

    private val baseUrl = "https://claude.ai"

    private val convLock = Mutex()
    private var orgId: String? = null
    private var conversationId: String? = null

    fun storeOAuthToken(sessionKey: String) {
        vault.storeToken(providerId, "SESSION", sessionKey)
    }

    private fun sessionOrThrow(): String =
        vault.getToken(providerId, "SESSION") ?: error("Claude session key not set")

    override suspend fun isAuthenticated(): Boolean =
        !vault.getToken(providerId, "SESSION").isNullOrBlank()

    private suspend fun ensureOrg() {
        if (orgId != null) return
        val session = sessionOrThrow()
        val resp: List<ClaudeOrganization> = HttpRetry.withRetry {
            client.get("$baseUrl/api/organizations") {
                headers {
                    append(HttpHeaders.Cookie, "sessionKey=$session")
                    append(HttpHeaders.UserAgent, BROWSER_UA)
                    append(HttpHeaders.Accept, "application/json")
                }
            }.body()
        }
        orgId = resp.firstOrNull()?.uuid ?: error("No Claude organizations available")
    }

    private suspend fun ensureConversation() {
        if (conversationId != null) return
        ensureOrg()
        val session = sessionOrThrow()
        val convUuid = UUID.randomUUID().toString()
        val resp: ClaudeNewConversation = HttpRetry.withRetry {
            client.post("$baseUrl/api/organizations/$orgId/chat_conversations") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Cookie, "sessionKey=$session")
                    append(HttpHeaders.UserAgent, BROWSER_UA)
                }
                setBody(mapOf("uuid" to convUuid, "name" to "Forge Bridge"))
            }.body()
        }
        conversationId = resp.uuid
    }

    fun resetConversation() {
        conversationId = null
    }

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        var full = ""
        var finish: String? = "stop"
        streamChat(request).collect { chunk ->
            chunk.choices.firstOrNull()?.delta?.content?.let { full += it }
            chunk.choices.firstOrNull()?.finishReason?.let { finish = it }
        }
        return UnifiedChatResponse(
            id = "claude_${UUID.randomUUID()}",
            model = request.model,
            choices = listOf(
                UnifiedChoice(message = UnifiedMessage("assistant", full), finishReason = finish)
            )
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk> = flow {
        convLock.withLock { ensureConversation() }
        val session = sessionOrThrow()
        val streamId = "claude_${UUID.randomUUID()}"
        val userPrompt = request.messages.last { it.role == "user" }.content

        val body = ClaudeChatRequest(
            prompt = userPrompt,
            model = request.model,
            timezone = "UTC"
        )

        client.preparePost("$baseUrl/api/organizations/$orgId/chat_conversations/$conversationId/completion") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Cookie, "sessionKey=$session")
                append(HttpHeaders.UserAgent, BROWSER_UA)
                append(HttpHeaders.Accept, "text/event-stream")
            }
            setBody(body)
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val event = runCatching { json.decodeFromString<ClaudeStreamEvent>(payload) }
                    .getOrNull() ?: continue

                val text = event.completion ?: event.delta?.text
                if (!text.isNullOrEmpty()) {
                    emit(
                        UnifiedStreamChunk(
                            id = streamId,
                            model = request.model,
                            choices = listOf(
                                UnifiedStreamChoice(delta = UnifiedDelta(content = text))
                            )
                        )
                    )
                }
                if (event.type == "message_stop" || event.stopReason != null) {
                    emit(
                        UnifiedStreamChunk(
                            id = streamId,
                            model = request.model,
                            choices = listOf(
                                UnifiedStreamChoice(
                                    delta = UnifiedDelta(content = ""),
                                    finishReason = event.stopReason ?: "stop"
                                )
                            )
                        )
                    )
                    break
                }
            }
        }
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
