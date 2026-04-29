package com.forge.bridge.data.remote.providers.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Float? = null,
    val stream: Boolean = false
)

@Serializable
data class AnthropicMessage(val role: String, val content: String)

@Serializable
data class AnthropicResponse(
    val id: String,
    val model: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicContentBlock(val type: String, val text: String? = null)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

@Serializable
data class AnthropicStreamEvent(
    val type: String,
    val index: Int? = null,
    val delta: AnthropicStreamDelta? = null,
    @SerialName("content_block") val contentBlock: AnthropicContentBlock? = null,
    val message: AnthropicResponse? = null
)

@Serializable
data class AnthropicStreamDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)
