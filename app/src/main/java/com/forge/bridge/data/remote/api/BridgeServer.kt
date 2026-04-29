package com.forge.bridge.data.remote.api

import com.forge.bridge.data.remote.api.routes.filesRoutes
import com.forge.bridge.data.remote.api.routes.healthRoutes
import com.forge.bridge.data.remote.api.routes.providerRoutes
import com.forge.bridge.data.remote.api.routes.chatRoutes
import com.forge.bridge.data.remote.providers.ProviderRegistry
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeServer @Inject constructor(
    private val registry: ProviderRegistry,
    private val json: Json
) {
    private var server: NettyApplicationEngine? = null

    fun start(port: Int = 8745) {
        if (server != null) return
        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(ContentNegotiation) { json(json) }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader("Authorization")
                allowHeader("Content-Type")
                allowNonSimpleContentTypes = true
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            ErrorBody(
                                message = cause.message ?: "Internal server error",
                                type = cause::class.java.simpleName
                            )
                        )
                    )
                }
            }
            routing {
                healthRoutes()
                providerRoutes(registry)
                chatRoutes(registry, json)
                filesRoutes()
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1_000, 5_000)
        server = null
    }

    val isRunning: Boolean get() = server != null
}
