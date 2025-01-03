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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting(userService: UserService, documentService: DocumentService, importService: ImportService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    val importFlow = flow {
        var n = 0
        while (true) {
            emit("test $n")
            delay(1.seconds)
            n++
        }
    }.shareIn(GlobalScope, SharingStarted.Eagerly)

    routing {
        loginRoute(userService, log)

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            apiRoute {
                usersRoute(userService)
                documentsRoute(documentService)
                importsRoute(importService, fileManager, importFlow)
            }

            webappRoute(userService)
        }
        authenticate("auth-basic") {
            loveRoute()
        }
    }
}