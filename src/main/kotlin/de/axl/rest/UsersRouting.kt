package de.axl.rest

import de.axl.db.ExposedUser
import de.axl.db.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.usersRoute(userService: UserService) {
    route("/users") {
        get {
            call.respond(HttpStatusCode.OK, userService.findAll())
        }

        get("/{username}") {
            val username = call.parameters["username"]
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val user = userService.findByUsername(username)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        put("/{username}") {
            val username = call.parameters["username"]
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val dbUser = userService.findByUsername(username)
                if (dbUser == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val user = call.receive<ExposedUser>()
                    userService.update(user)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        delete("/{username}") {
            val username = call.parameters["username"]
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                userService.delete(username)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}