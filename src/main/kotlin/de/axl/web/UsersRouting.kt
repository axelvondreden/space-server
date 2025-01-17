package de.axl.web

import de.axl.db.ExposedUser
import de.axl.db.UserDbService
import de.axl.getSessionUser
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.usersRoute(userDbService: UserDbService) {
    route("/users") {
        get {
            call.respond(HttpStatusCode.OK, userDbService.findAll())
        }

        get("/{username}") {
            val username = call.parameters["username"]
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val user = userDbService.findByUsername(username)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        put {
            val updatedUser = call.receive<ExposedUser>()
            val dbUser = userDbService.findByUsername(updatedUser.username)
            val sessionUser = getSessionUser(userDbService)
            if (sessionUser == null) {
                call.respond(HttpStatusCode.Unauthorized, "User not authenticated")
            } else if (dbUser == null) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
            } else if (!sessionUser.admin && sessionUser.username != dbUser.username) {
                call.respond(HttpStatusCode.Forbidden, "Not allowed to update other users")
            } else if (updatedUser.name.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Name must not be empty or blank")
            } else {
                userDbService.update(dbUser.copy(name = updatedUser.name))
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/password") {
            val changeRequest = call.receive<PasswordChangeRequest>()
            if (changeRequest.old.isBlank() || changeRequest.new.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Empty password")
            } else {
                val user = getSessionUser(userDbService)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
                } else {
                    if (userDbService.changePassword(user, changeRequest.old, changeRequest.new)) {
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
                userDbService.delete(username)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

data class PasswordChangeRequest(val old: String, val new: String)