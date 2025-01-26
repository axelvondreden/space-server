package de.axl.web

import de.axl.apiRoute
import de.axl.db.UserDbService
import de.axl.importing.ImportService
import de.axl.importing.events.ImportStateEvent
import de.axl.property
import de.axl.web.importing.importRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableSharedFlow

fun Application.configureRouting(userDbService: UserDbService, importService: ImportService) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    val importFlow = MutableSharedFlow<ImportStateEvent>()

    routing {
        loginRoute(userDbService, log)

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            apiRoute {
                usersRoute(userDbService)
                importRoute(importService, importFlow)
            }

            webappRoute(userDbService)
        }
        authenticate("auth-basic") {
            loveRoute()
        }
    }
}