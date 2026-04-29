package com.forge.bridge.data.remote.providers.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class OpenAiMessage(val role: String, val content: String)

@Serializable
data class OpenAiChatResponse(
    val id: String,
    val model: String,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class OpenAiStreamChunk(
    val id: String,
    val model: String? = null,
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
data class OpenAiStreamChoice(
    val index: Int = 0,
    val delta: OpenAiDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)
