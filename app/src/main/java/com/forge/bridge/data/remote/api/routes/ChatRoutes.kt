package com.forge.bridge.data.remote.api.routes

import com.forge.bridge.data.remote.api.ErrorBody
import com.forge.bridge.data.remote.api.ErrorResponse
import com.forge.bridge.data.remote.api.UnifiedChatRequest
import com.forge.bridge.data.remote.providers.ProviderRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

fun Routing.chatRoutes(registry: ProviderRegistry, json: Json) {
    route("/v1/chat/completions") {
        post {
            val request = call.receive<UnifiedChatRequest>()

            // Resolve provider: explicit field, then by model prefix, then first authed.
            val adapter = request.provider?.let { registry.get(it) }
                ?: registry.getAll().firstOrNull { request.model in it.availableModels }
                ?: registry.getAvailable().firstOrNull()

            if (adapter == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("No provider available for model '${request.model}'", "invalid_request_error"))
                )
                return@post
            }

            if (!adapter.isAuthenticated()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ErrorBody("Provider '${adapter.providerId}' is not authenticated", "unauthorized"))
                )
                return@post
            }

            if (request.stream) {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    try {
                        adapter.streamChat(request).collect { chunk ->
                            val payload = json.encodeToString(
                                com.forge.bridge.data.remote.api.UnifiedStreamChunk.serializer(),
                                chunk
                            )
                            write("data: $payload\n\n")
                            flush()
                        }
                        write("data: [DONE]\n\n")
                        flush()
                    } catch (t: Throwable) {
                        val err = json.encodeToString(
                            ErrorResponse.serializer(),
                            ErrorResponse(ErrorBody(t.message ?: "stream error", "stream_error"))
                        )
                        write("data: $err\n\n")
                        flush()
                    }
                }
            } else {
                val resp = adapter.chat(request)
                call.respond(HttpStatusCode.OK, resp)
            }
        }
    }
}
