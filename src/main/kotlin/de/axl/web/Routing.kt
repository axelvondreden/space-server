package de.axl.web

import de.axl.apiRoute
import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.property
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting(userService: UserService, documentService: DocumentService, importService: ImportService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    routing {
        loginRoute(userService, log)

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            apiRoute {
                usersRoute(userService)
                documentsRoute(documentService)
                importsRoute(importService, fileManager)
            }

            webappRoute(userService, property("space.admin.user.username"))
        }
    }
}