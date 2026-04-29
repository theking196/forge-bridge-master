package com.forge.bridge.data.remote.api.routes

import com.forge.bridge.data.remote.providers.ProviderRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ProviderListResponse(val providers: List<ProviderEntry>)

@Serializable
data class ProviderEntry(
    val id: String,
    val name: String,
    val tier: String,
    val status: String,
    val models: List<String>,
    val features: List<String>
)

fun Routing.providerRoutes(registry: ProviderRegistry) {
    route("/providers") {
        // List ALL configured adapters (not just authenticated). Status reflects auth state.
        get {
            val adapters = registry.getAll()
            val entries = adapters.map { a ->
                val authed = runCatching { a.isAuthenticated() }.getOrDefault(false)
                ProviderEntry(
                    id = a.providerId,
                    name = a.providerName,
                    tier = a.tier,
                    status = if (authed) "connected" else "disconnected",
                    models = a.availableModels,
                    features = a.features
                )
            }
            call.respond(HttpStatusCode.OK, ProviderListResponse(entries))
        }

        // /providers/available — only those that report authenticated.
        get("/available") {
            val available = registry.getAvailable().map { a ->
                ProviderEntry(
                    id = a.providerId,
                    name = a.providerName,
                    tier = a.tier,
                    status = "connected",
                    models = a.availableModels,
                    features = a.features
                )
            }
            call.respond(HttpStatusCode.OK, ProviderListResponse(available))
        }

        get("/{id}/models") {
            val id = call.parameters["id"]!!
            val adapter = registry.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "unknown provider"))
            call.respond(HttpStatusCode.OK, mapOf("models" to adapter.availableModels))
        }
    }
}
