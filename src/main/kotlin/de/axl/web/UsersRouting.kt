package de.axl.web

import de.axl.db.ExposedUser
import de.axl.db.UserService
import de.axl.getSessionUser
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

        put {
            val updatedUser = call.receive<ExposedUser>()
            val dbUser = userService.findByUsername(updatedUser.username)
            val sessionUser = getSessionUser(userService)
            if (sessionUser == null) {
                call.respond(HttpStatusCode.Unauthorized, "User not authenticated")
            } else if (dbUser == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
            } else if (!sessionUser.isAdmin && sessionUser.username != dbUser.username) {
                call.respond(HttpStatusCode.Forbidden, "Not allowed to update other users")
            } else {
                userService.update(dbUser.copy(name = updatedUser.name))
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/password") {
            val changeRequest = call.receive<PasswordChangeRequest>()
            if (changeRequest.old.isBlank() || changeRequest.new.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Empty password")
            } else {
                val user = getSessionUser(userService)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
                } else {
                    if (userService.changePassword(user, changeRequest.old, changeRequest.new)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Wrong password")
                    }
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

data class PasswordChangeRequest(val old: String, val new: String)

data class NameChangeRequest(val name: String)