package de.axl.web

import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*

fun Route.webappRoute() {
    staticResources("/styles", "styles")

    get("/") {
        call.respond(ThymeleafContent("home.html", emptyMap()))
    }
}