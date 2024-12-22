package de.axl.web

import de.axl.db.UserService
import de.axl.getUser
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*

fun Route.webappRoute(userService: UserService) {
    staticResources("/styles", "styles")

    get("/") {
        val user = getUser(userService) ?: return@get
        call.respond(ThymeleafContent("home.html", mapOf("user" to user)))
    }

    get("/settings/profile") {
        val user = getUser(userService) ?: return@get
        call.respond(ThymeleafContent("/settings/profile.html", mapOf("user" to user)))
    }
}