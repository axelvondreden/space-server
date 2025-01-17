package de.axl.web

import de.axl.apiRoute
import de.axl.db.UserDbService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.thymeleaf.*
import org.slf4j.Logger

fun Route.loginRoute(userDbService: UserDbService, logger: Logger) {
    apiRoute {
        post("/login") {
            handleLogin(userDbService, logger)
        }
    }
    post("/login") {
        handleLogin(userDbService, logger)
    }

    get("/login") {
        val error = call.request.queryParameters["error"]?.toString() ?: ""
        call.respond(ThymeleafContent("login.html", mapOf("error" to error)))
    }

    authenticate("auth-session") {
        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
    }
}

private suspend fun RoutingContext.handleLogin(userDbService: UserDbService, logger: Logger) {
    val params = call.receiveParameters()
    val username = params["username"]
    val password = params["password"]
    val fromUI = params["fromui"] == "1"
    logger.info("Login request from ${if (fromUI) "UI" else "API"} for $username")
    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        if (fromUI) {
            call.respondRedirect("/login?error=Missing username or password")
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing username or password")
        }
        return
    }
    val dbUser = userDbService.findByUsername(username)

    if (dbUser == null || !userDbService.testPassword(dbUser, password)) {
        logger.warn("Wrong credentials used! username: $username | password: $password")
        if (fromUI) {
            call.respondRedirect("/login?error=Wrong credentials")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
        }
    } else {
        call.sessions.set(UserSession(username))
        if (fromUI) {
            call.respondRedirect("/")
        } else {
            call.respond(HttpStatusCode.OK)
        }
    }
}