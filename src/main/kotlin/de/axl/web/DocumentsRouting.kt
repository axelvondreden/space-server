package de.axl.web

import de.axl.db.DocumentDbService
import de.axl.db.ExposedDocument
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.documentsRoute(documentDbService: DocumentDbService) {
    route("/documents") {
        get {
            call.respond(HttpStatusCode.OK, documentDbService.findAll())
        }

        get("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val doc = documentDbService.findByGuid(guid)
                if (doc != null) {
                    call.respond(HttpStatusCode.OK, doc)
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
                val dbDoc = documentDbService.findByGuid(guid)
                if (dbDoc == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val doc = call.receive<ExposedDocument>()
                    documentDbService.update(doc)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        delete("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                documentDbService.delete(guid)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}