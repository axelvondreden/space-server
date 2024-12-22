package de.axl.web

import de.axl.db.UserService
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.thymeleaf.*

fun Route.webappRoute(userService: UserService, adminUsername: String) {
    staticResources("/styles", "styles")

    get("/") {
        val session = call.sessions.get<UserSession>() ?: return@get
        val user = userService.findByUsername(session.username) ?: return@get
        call.respond(ThymeleafContent("home.html", mapOf("user" to TemplateUser(user.username, user.name, user.name == adminUsername))))
    }
}

data class TemplateUser(val username: String, val name: String?, val isAdmin: Boolean)