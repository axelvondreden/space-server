package de.axl.web.importing

import de.axl.importing.ImportService
import de.axl.serialization.api.ExposedImportInvoice
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importInvoiceRoute(importService: ImportService) {

    route("/invoice/{id}") {
        val findById: suspend (RoutingContext, Int?) -> ExposedImportInvoice? = { context, id ->
            if (id == null || id == 0) {
                context.call.respond(HttpStatusCode.NotFound)
                null
            } else {
                val invoice = importService.findInvoiceById(id)
                if (invoice == null) context.call.respond(HttpStatusCode.NotFound)
                invoice
            }
        }

        get {
            val invoice = findById(this, call.parameters["id"]?.toIntOrNull())
            if (invoice != null) {
                call.respond(HttpStatusCode.OK, invoice)
            }
        }

        put {
            val invoice = findById(this, call.parameters["id"]?.toIntOrNull())
            if (invoice != null) {
                importService.updateInvoice(call.receive<ExposedImportInvoice>())
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}