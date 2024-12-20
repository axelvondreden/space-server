package de.axl.web

import de.axl.db.DocumentService
import de.axl.db.ExposedDocument
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.documentsRoute(documentService: DocumentService) {
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

        post {
            val guid = documentService.createEmpty()
            call.respond(HttpStatusCode.Created, guid)
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