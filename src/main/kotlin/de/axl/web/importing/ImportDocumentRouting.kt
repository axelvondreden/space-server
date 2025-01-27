package de.axl.web.importing

import de.axl.db.ExposedImportDocument
import de.axl.importing.ImportService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importDocumentRoute(importService: ImportService) {

    route("/doc") {
        get {
            call.respond(HttpStatusCode.OK, importService.findAllDocuments())
        }

        route("/{guid}") {
            val findByGuid: suspend (RoutingContext, String?) -> ExposedImportDocument? = { context, guid ->
                if (guid.isNullOrBlank()) {
                    context.call.respond(HttpStatusCode.NotFound)
                    null
                } else {
                    val doc = importService.findDocumentByGuid(guid)
                    if (doc == null) context.call.respond(HttpStatusCode.NotFound)
                    doc
                }
            }

            get {
                val doc = findByGuid(this, call.parameters["guid"])
                if (doc != null) {
                    call.respond(HttpStatusCode.OK, doc)
                }
            }

            put {
                val doc = findByGuid(this, call.parameters["guid"])
                if (doc != null) {
                    importService.updateDocument(call.receive<ExposedImportDocument>())
                    call.respond(HttpStatusCode.OK)
                }
            }

            delete {
                val guid = call.parameters["guid"]
                if (guid.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    importService.deleteDocument(guid)
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/pdf") {
                val guid = call.parameters["guid"]!!
                call.respondFile(importService.getPdfOriginal(guid))
            }
        }
    }
}