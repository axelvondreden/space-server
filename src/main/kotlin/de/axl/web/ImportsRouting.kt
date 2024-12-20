package de.axl.web

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.files.FileManager
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.io.File

fun Route.importsRoute(importService: ImportService, fileManager: FileManager) {
    post("/upload") {
        var fileName = ""
        val multipartData = call.receiveMultipart()
        multipartData.forEachPart { part ->
            when (part) {
                /*is PartData.FormItem -> {
                    fileDescription = part.value
                }*/

                is PartData.FileItem -> {
                    fileName = part.originalFileName as String
                    val fileBytes = part.provider().readRemaining().readByteArray()
                    File("${fileManager.dataPath}/upload/$fileName").writeBytes(fileBytes)
                }

                else -> {}
            }
            part.dispose()
        }

        if (fileName.isNotBlank()) {
            call.respondText("File $fileName was uploaded!")
            fileManager.handleUpload(fileName)
        } else {
            call.respond(HttpStatusCode.BadRequest, "No file found in request")
        }
    }

    route("/imports") {
        get {
            call.respond(HttpStatusCode.OK, importService.findAll())
        }

        get("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val import = importService.findByGuid(guid)
                if (import != null) {
                    call.respond(HttpStatusCode.OK, import)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post {
            val import = call.receive<ExposedImport>()
            val guid = importService.create(import)
            call.respond(HttpStatusCode.Created, guid)
        }

        put("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val dbImport = importService.findByGuid(guid)
                if (dbImport == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val import = call.receive<ExposedImport>()
                    importService.update(import)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        delete("/{guid}") {
            val guid = call.parameters["guid"]
            if (guid.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                importService.delete(guid)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}