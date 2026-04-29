package com.forge.bridge.data.remote.providers.chatgpt

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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse-proxy adapter for the chat.openai.com web session. Token is stored
 * in the vault and refreshed by hitting the /api/auth/session endpoint.
 *
 * Conversation state (id + parent_message_id) is held in-memory under a mutex
 * so concurrent /chat calls don't race the conversation pointer.
 */
@Singleton
class ChatGptProxyAdapter @Inject constructor(
    private val client: HttpClient,
    private val vault: VaultManager,
    private val json: Json
) : ProviderAdapter {

    override val providerId = "chatgpt-proxy"
    override val providerName = "ChatGPT (Proxy)"
    override val tier = "PROXY"
    override val availableModels = listOf("auto", "gpt-4o", "gpt-4", "gpt-3.5-turbo")
    override val features = listOf("chat", "stream")

    private val baseUrl = "https://chat.openai.com"

    private val conversationLock = Mutex()
    private var conversationId: String? = null
    private var parentMessageId: String? = null

    private val cachedAccessToken = AtomicReference<String?>(null)

    fun resetConversation() {
        conversationId = null
        parentMessageId = null
    }

    private suspend fun accessToken(): String {
        cachedAccessToken.get()?.let { return it }
        val session = vault.getToken(providerId, "SESSION") ?: error("ChatGPT session token not set")
        val resp: ChatGptSession = HttpRetry.withRetry {
            client.get("$baseUrl/api/auth/session") {
                headers {
                    append(HttpHeaders.Cookie, "__Secure-next-auth.session-token=$session")
                    append(HttpHeaders.UserAgent, BROWSER_UA)
                }
            }.body()
        }
        val token = resp.accessToken ?: error("No accessToken returned by ChatGPT session endpoint")
        cachedAccessToken.set(token)
        return token
    }

    override suspend fun isAuthenticated(): Boolean =
        !vault.getToken(providerId, "SESSION").isNullOrBlank()

    override suspend fun refreshIfNeeded(vault: VaultManager) {
        // Web access tokens last ~hours; clear cache so next call re-fetches.
        cachedAccessToken.set(null)
    }

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        // Non-streaming: drain the stream and concatenate.
        var full = ""
        var finish: String? = "stop"
        streamChat(request).collect { chunk ->
            chunk.choices.firstOrNull()?.delta?.content?.let { full += it }
            chunk.choices.firstOrNull()?.finishReason?.let { finish = it }
        }
        return UnifiedChatResponse(
            id = "chatgpt_${UUID.randomUUID()}",
            model = request.model,
            choices = listOf(
                UnifiedChoice(message = UnifiedMessage("assistant", full), finishReason = finish)
            )
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk> = flow {
        val token = accessToken()
        val streamId = "chatgpt_${UUID.randomUUID()}"
        val userMessage = request.messages.last { it.role == "user" }.content

        val (convId, parentId) = conversationLock.withLock {
            conversationId to (parentMessageId ?: UUID.randomUUID().toString())
        }

        val body = ChatGptConversationRequest(
            messages = listOf(
                ChatGptMessage(
                    id = UUID.randomUUID().toString(),
                    author = Author("user"),
                    content = Content(parts = listOf(userMessage))
                )
            ),
            conversation_id = convId,
            parent_message_id = parentId,
            model = if (request.model == "auto") "auto" else request.model
        )

        var lastText = ""
        client.preparePost("$baseUrl/backend-api/conversation") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append(HttpHeaders.Accept, "text/event-stream")
                append(HttpHeaders.UserAgent, BROWSER_UA)
            }
            setBody(body)
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]" || payload.isEmpty()) {
                    emit(
                        UnifiedStreamChunk(
                            id = streamId,
                            model = request.model,
                            choices = listOf(
                                UnifiedStreamChoice(
                                    delta = UnifiedDelta(content = ""),
                                    finishReason = "stop"
                                )
                            )
                        )
                    )
                    break
                }
                val event = runCatching { json.decodeFromString<ChatGptStreamEvent>(payload) }
                    .getOrNull() ?: continue

                event.conversation_id?.let { id ->
                    conversationLock.withLock { conversationId = id }
                }
                event.message?.id?.let { id ->
                    conversationLock.withLock { parentMessageId = id }
                }

                val parts = event.message?.content?.parts ?: continue
                val full = parts.joinToString("")
                if (full.length > lastText.length) {
                    val delta = full.substring(lastText.length)
                    lastText = full
                    emit(
                        UnifiedStreamChunk(
                            id = streamId,
                            model = request.model,
                            choices = listOf(
                                UnifiedStreamChoice(delta = UnifiedDelta(content = delta))
                            )
                        )
                    )
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
