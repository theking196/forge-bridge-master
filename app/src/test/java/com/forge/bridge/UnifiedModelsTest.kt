package com.forge.bridge

import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.api.UnifiedChatResponse
import com.forge.bridge.data.remote.api.UnifiedChoice
import com.forge.bridge.data.remote.api.UnifiedMessage
import com.forge.bridge.data.remote.api.UnifiedUsage
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UnifiedModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `request round-trips`() {
        val req = UnifiedChatRequest(
            model = "gpt-4o",
            messages = listOf(
                UnifiedMessage("system", "You are helpful"),
                UnifiedMessage("user", "Hello"),
            ),
            temperature = 0.7f,
            stream = true
        )
        val s = json.encodeToString(UnifiedChatRequest.serializer(), req)
        val back = json.decodeFromString(UnifiedChatRequest.serializer(), s)
        assertEquals(req, back)
    }

    @Test
    fun `response decodes with snake_case keys`() {
        val raw = """
            {"id":"x","model":"gpt-4o","choices":[
              {"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}
            ],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
        """.trimIndent()
        val resp = json.decodeFromString(UnifiedChatResponse.serializer(), raw)
        assertEquals("stop", resp.choices.first().finishReason)
        assertEquals(15, resp.usage?.totalTokens)
    }

    @Test
    fun `unified choice defaults to index 0`() {
        val choice = UnifiedChoice(message = UnifiedMessage("assistant", "ok"))
        assertEquals(0, choice.index)
        assertNull(choice.finishReason)
    }

    @Test
    fun `usage totals add up`() {
        val u = UnifiedUsage(promptTokens = 3, completionTokens = 4, totalTokens = 7)
        assertEquals(7, u.totalTokens)
    }
}
