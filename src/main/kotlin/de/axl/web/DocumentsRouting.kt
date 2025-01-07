package de.axl.web

import de.axl.db.DocumentService
import de.axl.db.ExposedDocument
import de.axl.files.FileManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.documentsRoute(documentService: DocumentService, fileManager: FileManager) {
    route("/documents") {
        get {
            call.respond(HttpStatusCode.OK, documentService.findAll())
        }

        get("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val doc = documentService.findByGuid(guid)
                if (doc != null) {
                    call.respond(HttpStatusCode.OK, doc)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        get("/{guid}/pdf") {
            val guid = call.parameters["guid"]!!
            call.respondFile(fileManager.getPdf(guid))
        }

        get("/{guid}/img/{page}") {
            val guid = call.parameters["guid"]!!
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            call.respondFile(fileManager.getImage(guid, page))
        }

        get("/{guid}/thumb/{page}/{size}") {
            val guid = call.parameters["guid"]!!
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val size = call.parameters["size"]!!
            call.respondFile(fileManager.getThumb(guid, page, size))
        }

        put("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val dbDoc = documentService.findByGuid(guid)
                if (dbDoc == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val doc = call.receive<ExposedDocument>()
                    documentService.update(doc)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        delete("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                documentService.delete(guid)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}