package de.axl.web

import de.axl.db.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.thymeleaf.*
import org.slf4j.Logger

fun Route.loginRoute(userService: UserService, logger: Logger) {
    post("/login") {
        val params = call.receiveParameters()
        val username = params["username"]
        val password = params["password"]
        val fromUI = params["fromui"] == "1"
        logger.info("Login request from ${if (fromUI) "UI" else "API"} for $username")
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            if (fromUI) {
                call.respondRedirect("/app/login?error=Missing username or password")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Missing username or password")
            }
            return@post
        }
        val dbUser = userService.findByUsername(username)

        if (dbUser == null || !userService.testPassword(dbUser, password)) {
            logger.warn("Wrong credentials used! username: $username | password: $password")
            if (fromUI) {
                call.respondRedirect("/app/login?error=Wrong credentials")
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
            }
        } else {
            call.sessions.set(UserSession(username))
            if (fromUI) {
                call.respondRedirect("/app")
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    get("/app/login") {
        call.respond(ThymeleafContent("login.html", emptyMap()))
    }
}