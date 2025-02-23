package de.axl.web.importing

import de.axl.importing.ImportService
import de.axl.serialization.api.ExposedImportPage
import de.axl.serialization.api.NewWord
import de.axl.serialization.api.Rectangle
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importPageRoute(importService: ImportService) {

    route("/page/{id}") {
        val findById: suspend (RoutingContext, Int?) -> ExposedImportPage? = { context, id ->
            if (id == null || id == 0) {
                context.call.respond(HttpStatusCode.NotFound)
                null
            } else {
                val page = importService.findPageById(id)
                if (page == null) context.call.respond(HttpStatusCode.NotFound)
                page
            }
        }

        get {
            findById(this, call.parameters["id"]?.toIntOrNull())?.let { call.respond(HttpStatusCode.OK, it) }
        }

        put {
            val id = call.parameters["id"]?.toIntOrNull()
            val page = call.receive<ExposedImportPage>()
            if (id != page.id) {
                call.respond(HttpStatusCode.BadRequest, "Id in path and body must be equal")
                return@put
            }
            if (findById(this, page.id) != null) {
                importService.updatePage(page)
                call.respond(HttpStatusCode.OK)
            }
        }

        delete {
            val id = call.parameters["id"]?.toIntOrNull() ?: 0
            if (id == 0) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.deletePage(id)
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/blocks") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                call.respond(HttpStatusCode.OK, importService.getFullBlocksForPage(page))
            }
        }

        get("/img") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                val type = call.request.queryParameters["type"]
                when (type) {
                    "original" -> call.respondFile(importService.getImageOriginal(page))
                    else -> call.respondFile(importService.getImageCleaned(page))
                }
            }
        }

        get("/thumb") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                val size = call.parameters["size"] ?: "512x512"
                call.respondFile(importService.getThumbnail(page, size))
            }
        }

        get("/text") {
            call.respondText(importService.findPageTextById(call.parameters["id"]?.toIntOrNull() ?: 0) ?: "")
        }

        post("/edit/clean") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.createCleanedImage(page)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/edit/thumbs") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.createThumbnails(page)
                call.respond(HttpStatusCode.OK)
            }
        }

        route("/ocr") {
            post("/full") {
                val page = findById(this, call.parameters["id"]?.toIntOrNull())
                if (page != null) {
                    importService.extractTextAndCreateDbObjects(page)
                    call.respond(HttpStatusCode.OK)
                }
            }

            post("/part") {
                val page = findById(this, call.parameters["id"]?.toIntOrNull())
                if (page != null) {
                    val rect = call.receive<Rectangle>()
                    val result = importService.extractText(page, rect)
                    call.respond(HttpStatusCode.OK, result)
                }
            }
        }

        post("/word") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                val newWord = call.receive<NewWord>()
                importService.addWordToPage(page, newWord)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}