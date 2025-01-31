package de.axl.web.importing

import de.axl.db.ExposedImportWord
import de.axl.importing.ImportService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importWordRoute(importService: ImportService) {

    route("/word/{id}") {
        val findById: suspend (RoutingContext, Int?) -> ExposedImportWord? = { context, id ->
            if (id == null || id == 0) {
                context.call.respond(HttpStatusCode.NotFound)
                null
            } else {
                val word = importService.findWordById(id)
                if (word == null) context.call.respond(HttpStatusCode.NotFound)
                word
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
                importService.deleteWord(id)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/text") {
            val id = call.parameters["id"]?.toIntOrNull() ?: 0
            val text = call.receiveText()
            importService.updateWordText(id, text)
            call.respond(HttpStatusCode.OK)
        }
    }
}