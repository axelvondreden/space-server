package de.axl.web.importing

import de.axl.db.ExposedImportLine
import de.axl.importing.ImportService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importLineRoute(importService: ImportService) {

    route("/line/{id}") {
        val findById: suspend (RoutingContext, Int?) -> ExposedImportLine? = { context, id ->
            if (id == null || id == 0) {
                context.call.respond(HttpStatusCode.NotFound)
                null
            } else {
                val line = importService.findLineById(id)
                if (line == null) context.call.respond(HttpStatusCode.NotFound)
                line
            }
        }
        get {
            findById(this, call.parameters["id"]?.toIntOrNull())?.let { call.respond(HttpStatusCode.OK, it) }
        }

        put {
            val id = call.parameters["id"]?.toIntOrNull()
            val line = call.receive<ExposedImportLine>()
            if (id != line.id) {
                call.respond(HttpStatusCode.BadRequest, "Id in path and body must be equal")
                return@put
            }
            if (findById(this, line.id) != null) {
                importService.updateLine(line)
                call.respond(HttpStatusCode.OK)
            }
        }

        delete {
            val id = call.parameters["id"]?.toIntOrNull() ?: 0
            if (id == 0) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.deleteLine(id)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}