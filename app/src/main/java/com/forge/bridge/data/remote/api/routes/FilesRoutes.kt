package com.forge.bridge.data.remote.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class UploadedFile(
    val id: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val contentType: String?
)

@Serializable
data class FileListResponse(val files: List<UploadedFile>)

private val uploads = ConcurrentHashMap<String, UploadedFile>()
private val tempDir: File by lazy {
    File(System.getProperty("java.io.tmpdir"), "forge-bridge-uploads").apply { mkdirs() }
}

fun Routing.filesRoutes() {
    route("/files") {

        post("/upload") {
            val multipart = call.receiveMultipart()
            val saved = mutableListOf<UploadedFile>()

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val id = UUID.randomUUID().toString()
                    val filename = part.originalFileName ?: "file"
                    val target = File(tempDir, id)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L

                    target.outputStream().use { out ->
                        part.streamProvider().use { input ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                total += n
                            }
                        }
                    }

                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    val info = UploadedFile(
                        id = id,
                        filename = filename,
                        sizeBytes = total,
                        sha256 = sha,
                        contentType = part.contentType?.toString()
                    )
                    uploads[id] = info
                    saved += info
                }
                part.dispose()
            }

            if (saved.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "no file part in upload"))
            } else {
                call.respond(HttpStatusCode.OK, FileListResponse(saved))
            }
        }

        get {
            call.respond(HttpStatusCode.OK, FileListResponse(uploads.values.toList()))
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val info = uploads[id] ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, info)
        }
    }
}
