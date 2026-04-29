package com.forge.bridge.data.remote.providers.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeOrganizationsResponse(
    val organizations: List<ClaudeOrganization> = emptyList()
)

@Serializable
data class ClaudeOrganization(
    val uuid: String,
    val name: String? = null
)

@Serializable
data class ClaudeChatRequest(
    val prompt: String,
    val parent_message_uuid: String? = null,
    val timezone: String = "UTC",
    val model: String,
    val attachments: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val rendering_mode: String = "messages"
)

@Serializable
data class ClaudeStreamEvent(
    val type: String,
    val completion: String? = null,
    val message: ClaudeMessage? = null,
    val delta: ClaudeDelta? = null,
    val index: Int? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class ClaudeMessage(
    val uuid: String? = null,
    val text: String? = null,
    val sender: String? = null
)

@Serializable
data class ClaudeDelta(val type: String? = null, val text: String? = null)

@Serializable
data class ClaudeNewConversation(
    val uuid: String,
    val name: String = "New chat",
    val message: String? = null
)
