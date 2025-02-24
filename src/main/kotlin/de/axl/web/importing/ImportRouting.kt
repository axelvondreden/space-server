package de.axl.web.importing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.io.readByteArray
import java.io.File

fun Route.importRoute(importService: ImportService, importFlow: MutableSharedFlow<ImportStateEvent>) {

    post("/upload") {
        var fileName = ""
        val multipartData = call.receiveMultipart(formFieldLimit = 200 * 1024 * 1024)
        var part = multipartData.readPart()
        while (part != null) {
            if (part is PartData.FileItem) {
                fileName = part.originalFileName as String
                val fileBytes = part.provider().readRemaining().readByteArray()
                File("${importService.dataPath}/upload/$fileName").writeBytes(fileBytes)
            }
            part.dispose()
            part = multipartData.readPart()
        }

        if (fileName.isNotBlank()) {
            call.respondText("File $fileName was uploaded!")
        } else {
            call.respond(HttpStatusCode.BadRequest, "No file found in request")
        }
    }

    post("/upload/collect") {
        call.respond(HttpStatusCode.OK)
        importService.handleUploads(importFlow)
    }

    route("/import") {
        importDocumentRoute(importService)
        importPageRoute(importService)
        importBlockRoute(importService)
        importLineRoute(importService)
        importWordRoute(importService)

        importInvoiceRoute(importService)

        webSocket("/events") {
            importFlow.collect { event ->
                send(jacksonObjectMapper().writeValueAsString(event))
            }
        }
    }
}