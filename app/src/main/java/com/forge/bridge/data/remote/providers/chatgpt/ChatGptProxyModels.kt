package com.forge.bridge.data.remote.providers.chatgpt

import kotlinx.serialization.Serializable

@Serializable
data class ChatGptConversationRequest(
    val action: String = "next",
    val messages: List<ChatGptMessage>? = null,
    val conversation_id: String? = null,
    val parent_message_id: String? = null,
    val model: String = "auto",
    val timezone_offset_min: Int = -480,
    val history_and_training_disabled: Boolean = false,
    val conversation_mode: ConversationMode = ConversationMode(),
    val supports_buffering: Boolean = true
)

@Serializable
data class ChatGptMessage(
    val id: String,
    val author: Author,
    val content: Content,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class Author(val role: String)

@Serializable
data class Content(val content_type: String = "text", val parts: List<String>)

@Serializable
data class ConversationMode(val kind: String = "primary_assistant")

@Serializable
data class ChatGptStreamEvent(
    val message: ChatGptStreamMessage? = null,
    val conversation_id: String? = null,
    val error: String? = null
)

@Serializable
data class ChatGptStreamMessage(
    val id: String? = null,
    val author: Author? = null,
    val content: ChatGptStreamContent? = null,
    val end_turn: Boolean? = null,
    val status: String? = null
)

@Serializable
data class ChatGptStreamContent(
    val content_type: String? = null,
    val parts: List<String>? = null
)

@Serializable
data class ChatGptSession(
    val accessToken: String? = null,
    val expires: String? = null
)
