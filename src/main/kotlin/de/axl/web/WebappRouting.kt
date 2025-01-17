package de.axl.web

import de.axl.db.UserDbService
import de.axl.getSessionUser
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import java.time.LocalDateTime

fun Route.webappRoute(userDbService: UserDbService) {
    staticResources("/styles", "styles")
    staticResources("/scripts", "scripts")

    get("/") {
        val user = getSessionUser(userDbService) ?: return@get
        LocalDateTime.of(2022, 1, 1, 0, 0)
        call.respond(ThymeleafContent("home.html", mapOf("user" to user)))
    }

    get("/imports") {
        val user = getSessionUser(userDbService) ?: return@get
        call.respond(ThymeleafContent("/imports/imports.html", mapOf("user" to user)))
    }

    get("/settings/profile") {
        val user = getSessionUser(userDbService) ?: return@get
        call.respond(ThymeleafContent("/settings/profile.html", mapOf("user" to user)))
    }
}