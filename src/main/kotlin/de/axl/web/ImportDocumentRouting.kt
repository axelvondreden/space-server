package de.axl.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.axl.db.ExposedImportDocument
import de.axl.files.FileManagerImport
import de.axl.importing.ImportService
import de.axl.importing.events.ImportStateEvent
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import java.io.File
import java.util.concurrent.atomic.AtomicReference

fun Route.importDocumentRoute(importService: ImportService, fileManager: FileManagerImport, importFlow: MutableSharedFlow<ImportStateEvent>) {

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
                    (5 downTo 1).forEach {
                        importFlow.emit(ImportStateEvent(importing = false, message = "Starting import in $it second(s)"))
                        delay(1_000)
                    }
                    importService.handleUploads(importFlow)
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

        get("/{guid}/pdf") {
            val guid = call.parameters["guid"]!!
            call.respondFile(fileManager.getPdfOriginal(guid))
        }

        get("/{guid}/img") {
            val guid = call.parameters["guid"]!!
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val type = call.request.queryParameters["type"]
            when (type) {
                "original" -> call.respondFile(fileManager.getImageOriginal(guid, page))
                "deskewed" -> call.respondFile(fileManager.getImageDeskewed(guid, page))
                "color" -> call.respondFile(fileManager.getImageColorAdjusted(guid, page))
                else -> call.respondFile(fileManager.getImage(guid, page))
            }
        }

        get("/{guid}/thumb/{page}/{size}") {
            val guid = call.parameters["guid"]!!
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val size = call.parameters["size"]!!
            call.respondFile(fileManager.getThumbnail(guid, page, size))
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
                    val import = call.receive<ExposedImportDocument>()
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

        post("/{guid}/edit/{page}/deskew") {
            val guid = call.parameters["guid"]
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val deskew = call.request.queryParameters["deskew"]?.toIntOrNull() ?: 40
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.update(importService.findByGuid(guid)!!.copy(deskew = deskew))
                fileManager.createDeskewedImage(guid, page, deskew)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{guid}/edit/{page}/color") {
            val guid = call.parameters["guid"]
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val fuzz = call.request.queryParameters["fuzz"]?.toIntOrNull() ?: 10
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.update(importService.findByGuid(guid)!!.copy(colorFuzz = fuzz))
                fileManager.createColorAdjustedImage(guid, page, fuzz)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{guid}/edit/{page}/crop") {
            val guid = call.parameters["guid"]
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val fuzz = call.request.queryParameters["fuzz"]?.toIntOrNull() ?: 10
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.update(importService.findByGuid(guid)!!.copy(cropFuzz = fuzz))
                fileManager.createCroppedImage(guid, page, fuzz)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{guid}/edit/{page}/thumbs") {
            val guid = call.parameters["guid"]
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                fileManager.createThumbnails(guid, page)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{guid}/edit/complete") {
            val guid = call.parameters["guid"]
            val deskew = call.request.queryParameters["deskew"]?.toIntOrNull() ?: 40
            val colorFuzz = call.request.queryParameters["colorFuzz"]?.toIntOrNull() ?: 10
            val cropFuzz = call.request.queryParameters["cropFuzz"]?.toIntOrNull() ?: 20
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.editImageComplete(guid, deskew, colorFuzz, cropFuzz)
                call.respond(HttpStatusCode.OK)
            }
        }

        webSocket("/events") {
            importFlow.collect { event ->
                send(jacksonObjectMapper().writeValueAsString(event))
            }
        }
    }
}