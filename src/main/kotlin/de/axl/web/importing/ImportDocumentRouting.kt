package de.axl.web.importing

import de.axl.importing.ImportService
import de.axl.serialization.api.ExposedImportDocument
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importDocumentRoute(importService: ImportService) {

    route("/doc") {
        get {
            call.respond(HttpStatusCode.OK, importService.findAllDocuments())
        }

        route("/{id}") {
            val findById: suspend (RoutingContext, Int?) -> ExposedImportDocument? = { context, id ->
                if (id == null || id == 0) {
                    context.call.respond(HttpStatusCode.NotFound)
                    null
                } else {
                    val page = importService.findDocumentById(id)
                    if (page == null) context.call.respond(HttpStatusCode.NotFound)
                    page
                }
            }

            get {
                val doc = findById(this, call.parameters["id"]?.toIntOrNull())
                if (doc != null) {
                    call.respond(HttpStatusCode.OK, doc)
                }
            }

            put {
                val doc = findById(this, call.parameters["id"]?.toIntOrNull())
                if (doc != null) {
                    importService.updateDocument(call.receive<ExposedImportDocument>())
                    call.respond(HttpStatusCode.OK)
                }
            }

            delete {
                val id = call.parameters["id"]?.toIntOrNull() ?: 0
                if (id <= 0) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    importService.deleteDocument(id)
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/pdf") {
                val doc = importService.findDocumentById(call.parameters["id"]?.toIntOrNull() ?: 0)
                if (doc != null) {
                    call.respondFile(importService.getPdfOriginal(doc.guid))
                }
            }
        }
    }
}