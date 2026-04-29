package com.forge.bridge.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.forge.bridge.IChatStreamCallback
import com.forge.bridge.IForgeBridge
import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.api.UnifiedMessage
import com.forge.bridge.data.remote.providers.ProviderRegistry
import com.forge.bridge.forge.ChatChunk
import com.forge.bridge.forge.ChatRequest
import com.forge.bridge.forge.ChatResponse
import com.forge.bridge.forge.ProviderInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * AIDL bridge for cross-app callers (Forge OS clients). Methods marshal across
 * processes; we run all real work on a service-scoped IO dispatcher.
 */
@AndroidEntryPoint
class ForgeInterfaceService : Service() {

    @Inject lateinit var registry: ProviderRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val binder = object : IForgeBridge.Stub() {

        override fun listProviders(): MutableList<ProviderInfo> = runBlocking {
            registry.getAvailable().map { a ->
                ProviderInfo(
                    id = a.providerId,
                    name = a.providerName,
                    tier = a.tier,
                    status = "connected",
                    models = a.availableModels,
                    features = a.features
                )
            }.toMutableList()
        }

        override fun chat(request: ChatRequest): ChatResponse {
            val unified = UnifiedChatRequest(
                model = request.model,
                messages = request.messages.map { UnifiedMessage(it.role, it.content) },
                temperature = request.temperature,
                maxTokens = request.maxTokens.takeIf { it > 0 },
                stream = false,
                provider = request.providerId
            )
            return runBlocking {
                val adapter = registry.get(request.providerId)
                    ?: error("Unknown provider: ${request.providerId}")
                val resp = adapter.chat(unified)
                val choice = resp.choices.firstOrNull()
                ChatResponse(
                    id = resp.id,
                    content = choice?.message?.content.orEmpty(),
                    finishReason = choice?.finishReason,
                    promptTokens = resp.usage?.promptTokens ?: 0,
                    completionTokens = resp.usage?.completionTokens ?: 0
                )
            }
        }

        override fun streamChat(request: ChatRequest, callback: IChatStreamCallback) {
            val unified = UnifiedChatRequest(
                model = request.model,
                messages = request.messages.map { UnifiedMessage(it.role, it.content) },
                temperature = request.temperature,
                maxTokens = request.maxTokens.takeIf { it > 0 },
                stream = true,
                provider = request.providerId
            )
            scope.launch {
                try {
                    val adapter = registry.get(request.providerId)
                        ?: error("Unknown provider: ${request.providerId}")
                    adapter.streamChat(unified).collect { chunk ->
                        val choice = chunk.choices.firstOrNull()
                        callback.onChunk(
                            ChatChunk(
                                delta = choice?.delta?.content.orEmpty(),
                                finishReason = choice?.finishReason
                            )
                        )
                    }
                    callback.onComplete()
                } catch (t: Throwable) {
                    runCatching { callback.onError(t.message ?: "stream error") }
                }
            }
        }

        override fun healthCheck(): String = "ok"
    }
}
