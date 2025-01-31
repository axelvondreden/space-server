package de.axl.web.importing

import de.axl.db.ExposedImportBlock
import de.axl.importing.ImportService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importBlockRoute(importService: ImportService) {

    route("/block/{id}") {
        val findById: suspend (RoutingContext, Int?) -> ExposedImportBlock? = { context, id ->
            if (id == null || id == 0) {
                context.call.respond(HttpStatusCode.NotFound)
                null
            } else {
                val block = importService.findBlockById(id)
                if (block == null) context.call.respond(HttpStatusCode.NotFound)
                block
            }
        }
        get {
            findById(this, call.parameters["id"]?.toIntOrNull())?.let { call.respond(HttpStatusCode.OK, it) }
        }

        delete {
            val id = call.parameters["id"]?.toIntOrNull() ?: 0
            if (id == 0) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.deleteBlock(id)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}