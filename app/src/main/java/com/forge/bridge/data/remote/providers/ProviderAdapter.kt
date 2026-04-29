package com.forge.bridge.data.remote.providers

import com.forge.bridge.data.local.VaultManager
import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.api.UnifiedChatResponse
import com.forge.bridge.data.remote.api.UnifiedStreamChunk
import kotlinx.coroutines.flow.Flow

/**
 * Stable interface every provider implements. Adapters translate between the
 * unified request/response shape and a vendor-specific API.
 *
 * Notes for implementers:
 *  - `isAuthenticated` MUST be a suspend function. Do not block.
 *  - Streaming returns a cold Flow. The route handler collects it and forwards
 *    SSE frames to the HTTP client.
 *  - `refreshIfNeeded` is a no-op for API-key providers. Proxy/OAuth adapters
 *    can override it to perform background refresh from `TokenRefreshScheduler`.
 */
interface ProviderAdapter {
    val providerId: String
    val providerName: String
    val tier: String                       // "API" or "PROXY"
    val availableModels: List<String>
    val features: List<String>

    suspend fun isAuthenticated(): Boolean
    suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse
    fun streamChat(request: UnifiedChatRequest): Flow<UnifiedStreamChunk>

    suspend fun refreshIfNeeded(vault: VaultManager) { /* opt-in */ }
}
