package de.axl.web.importing

import de.axl.importing.ImportService
import de.axl.serialization.api.ExposedImportPage
import de.axl.serialization.api.OcrResult
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
                    "deskewed" -> call.respondFile(importService.getImageDeskewed(page))
                    "color" -> call.respondFile(importService.getImageColorAdjusted(page))
                    else -> call.respondFile(importService.getImage(page))
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

        post("/edit/deskew") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.createDeskewedImage(page, call.request.queryParameters["deskew"]?.toIntOrNull() ?: 40)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/edit/color") {
            var page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.createColorAdjustedImage(page, call.request.queryParameters["fuzz"]?.toIntOrNull() ?: 10)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/edit/crop") {
            var page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.createCroppedImage(page, call.request.queryParameters["fuzz"]?.toIntOrNull() ?: 10)
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

        post("/edit/ocr") {
            val page = findById(this, call.parameters["id"]?.toIntOrNull())
            if (page != null) {
                importService.extractTextAndCreateDbObjects(page)
                val text = importService.findPageTextById(page.id)
                call.respond(HttpStatusCode.OK, OcrResult(text ?: ""))
            }
        }
    }
}