package de.axl.web

import de.axl.apiRoute
import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.property
import de.axl.web.events.ImportStateEvent
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableSharedFlow

fun Application.configureRouting(userService: UserService, documentService: DocumentService, importService: ImportService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    val importFlow = MutableSharedFlow<ImportStateEvent>()

    routing {
        loginRoute(userService, log)

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            apiRoute {
                usersRoute(userService)
                documentsRoute(documentService, fileManager)
                importsRoute(importService, fileManager, importFlow)
            }

            webappRoute(userService)
        }
        authenticate("auth-basic") {
            loveRoute()
        }
    }
}