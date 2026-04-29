package com.forge.bridge.data.remote.providers

import com.forge.bridge.data.remote.providers.anthropic.AnthropicAdapter
import com.forge.bridge.data.remote.providers.chatgpt.ChatGptProxyAdapter
import com.forge.bridge.data.remote.providers.claude.ClaudeProxyAdapter
import com.forge.bridge.data.remote.providers.gemini.GeminiAdapter
import com.forge.bridge.data.remote.providers.openai.OpenAiAdapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRegistry @Inject constructor(
    openAi: OpenAiAdapter,
    anthropic: AnthropicAdapter,
    gemini: GeminiAdapter,
    chatGpt: ChatGptProxyAdapter,
    claude: ClaudeProxyAdapter
) {
    private val adapters: Map<String, ProviderAdapter> = listOf(
        openAi, anthropic, gemini, chatGpt, claude
    ).associateBy { it.providerId }

    fun get(id: String): ProviderAdapter? = adapters[id]
    fun getAll(): List<ProviderAdapter> = adapters.values.toList()

    /** Filters down to providers that report being authenticated. */
    suspend fun getAvailable(): List<ProviderAdapter> =
        adapters.values.filter { runCatching { it.isAuthenticated() }.getOrDefault(false) }
}
