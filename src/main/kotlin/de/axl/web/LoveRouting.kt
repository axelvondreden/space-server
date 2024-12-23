package de.axl.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import java.io.File

fun Route.loveRoute() {
    staticResources("/styles", "styles")

    get("/love") {
        val file = File("love.json")
        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val loveList = jacksonObjectMapper().readValue(file, LoveList::class.java).entries.sortedBy { rand(0, 10000) }
        call.respond(ThymeleafContent("love.html", mapOf("entries" to loveList, "size" to loveList.size)))
    }
}

fun rand(from: Int, to: Int) = (Math.random() * (to - from) + from).toInt()

data class LoveList(val entries: List<Love>)

data class Love(
    val text: String,
    val backgroundColor: String,
    val description: String? = null,
    val textColor: String? = null,
    val prefixIcon: String? = null,
    val suffixIcon: String? = null
)