package de.axl.rest

import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.property
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting(userService: UserService, documentService: DocumentService, importService: ImportService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    routing {
        post("/login") {
            val params = call.receiveParameters()
            val username = params["username"]
            val password = params["password"]
            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing username or password")
                return@post
            }
            val dbUser = userService.findByUsername(username)

            if (dbUser == null || !userService.testPassword(dbUser, password)) {
                log.warn("Wrong credentials used! username: $username | password: $password")
                call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
            } else {
                call.sessions.set(UserSession(username))
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK)
        }

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-session") {
            get("/hello") {
                val session = call.sessions.get<UserSession>()
                val username = session!!.username
                call.respondText("Hello, $username!")
            }

            usersRoute(userService)
            documentsRoute(documentService)
            importsRoute(importService, fileManager)
        }
    }
}