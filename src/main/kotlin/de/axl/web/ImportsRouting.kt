package de.axl.web

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.files.FileManager
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.utils.io.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import java.io.File
import java.util.concurrent.atomic.AtomicReference

fun Route.importsRoute(importService: ImportService, fileManager: FileManager, importFlow: MutableSharedFlow<String>) {

    val handleUploadsJob = AtomicReference<Job?>(null)

    post("/upload") {
        var fileName = ""
        val multipartData = call.receiveMultipart(formFieldLimit = 200 * 1024 * 1024)
        var part = multipartData.readPart()
        while (part != null) {
            if (part is PartData.FileItem) {
                fileName = part.originalFileName as String
                val fileBytes = part.provider().readRemaining().readByteArray()
                File("${fileManager.dataPath}/upload/$fileName").writeBytes(fileBytes)
            }
            part.dispose()
            part = multipartData.readPart()
        }

        if (fileName.isNotBlank()) {
            call.respondText("File $fileName was uploaded!")
            // Cancel if a scheduled job exists and schedule a new one
            handleUploadsJob.getAndSet(
                GlobalScope.launch {
                    (10 downTo 1).forEach {
                        importFlow.emit("Starting import in $it second(s)")
                        delay(1_000)
                    }
                    fileManager.handleUploads(importFlow)
                }
            )?.cancel() // Cancel previous unfinished job
        } else {
            call.respond(HttpStatusCode.BadRequest, "No file found in request")
        }
    }

    route("/imports") {
        get {
            call.respond(HttpStatusCode.OK, importService.findAll())
        }

        get("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val import = importService.findByGuid(guid)
                if (import != null) {
                    call.respond(HttpStatusCode.OK, import)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        put("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val dbImport = importService.findByGuid(guid)
                if (dbImport == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val import = call.receive<ExposedImport>()
                    importService.update(import)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        delete("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.delete(guid)
                call.respond(HttpStatusCode.OK)
            }
        }

        sse("/events") {
            call.response.cacheControl(CacheControl.NoCache(null))
            importFlow.collect { event ->
                send(ServerSentEvent(event))
            }
        }
    }
}