package com.forge.bridge.data.remote.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String, val uptimeMs: Long)

private val startedAt = System.currentTimeMillis()

fun Routing.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "ok",
                version = "1.0.0",
                uptimeMs = System.currentTimeMillis() - startedAt
            )
        )
    }
    get("/healthz") {
        call.respond(HttpStatusCode.OK, HealthResponse("ok", "1.0.0", System.currentTimeMillis() - startedAt))
    }
}
