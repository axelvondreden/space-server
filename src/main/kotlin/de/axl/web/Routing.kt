package de.axl.web

import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.property
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting(userService: UserService, documentService: DocumentService, importService: ImportService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    routing {
        loginRoute(userService, log)

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            route("/api/v1") {
                get("/hello") {
                    val session = call.sessions.get<UserSession>()
                    val username = session!!.username
                    call.respondText("Hello, $username!")
                }

                usersRoute(userService)
                documentsRoute(documentService)
                importsRoute(importService, fileManager)
            }

            webappRoute()
        }
    }
}