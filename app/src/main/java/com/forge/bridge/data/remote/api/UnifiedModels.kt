package com.forge.bridge.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The unified, OpenAI-compatible request/response shape that every adapter
 * translates to and from. Clients of the bridge speak this shape regardless
 * of which upstream provider serves the request.
 */
@Serializable
data class UnifiedChatRequest(
    val model: String,
    val messages: List<UnifiedMessage>,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    val stream: Boolean = false,
    val provider: String? = null
)

@Serializable
data class UnifiedMessage(
    val role: String,
    val content: String
)

@Serializable
data class UnifiedChatResponse(
    val id: String,
    val model: String,
    val choices: List<UnifiedChoice>,
    val usage: UnifiedUsage? = null
)

@Serializable
data class UnifiedChoice(
    val index: Int = 0,
    val message: UnifiedMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UnifiedUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

/** Streaming SSE chunk shape (OpenAI-compatible). */
@Serializable
data class UnifiedStreamChunk(
    val id: String,
    val model: String,
    val choices: List<UnifiedStreamChoice>
)

@Serializable
data class UnifiedStreamChoice(
    val index: Int = 0,
    val delta: UnifiedDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UnifiedDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody
)

@Serializable
data class ErrorBody(
    val message: String,
    val type: String,
    val code: String? = null
)
